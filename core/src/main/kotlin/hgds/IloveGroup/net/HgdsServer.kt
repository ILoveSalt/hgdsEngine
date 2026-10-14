package hgds.IloveGroup.net

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Одно соединение клиента на стороне сервера: сокет + отдельный поток отправки,
 * чтобы блокирующая запись в TCP не тормозила игровой цикл сервера.
 */
class ClientConnection internal constructor(
    val id: Int,
    val socket: Socket,
    private val output: DataOutputStream,
    val remote: String
) {
    /** Имя игрока (присваивается после успешного входа). */
    @Volatile
    var name: String = ""

    @Volatile
    var loggedIn: Boolean = false

    /** Последняя позиция, полученная от клиента. */
    @Volatile
    var position: NetworkPosition = NetworkPosition(0f, 0f, 0f, 0f, 0f)

    /** Время последнего пакета (для контроля «мёртвых» соединений). */
    @Volatile
    var lastPacketMs: Long = System.currentTimeMillis()

    @Volatile
    var closed: Boolean = false
        private set

    private val outQueue = LinkedBlockingQueue<Packet>()
    private val writerThread = Thread({ writerLoop() }, "hgdsNET-write-$id")

    internal fun startWriting() {
        writerThread.isDaemon = true
        writerThread.start()
    }

    /** Ставит пакет в очередь отправки. */
    fun send(packet: Packet) {
        if (!closed) outQueue.add(packet)
    }

    private fun writerLoop() {
        try {
            while (!closed) {
                HgdsNet.writePacket(output, outQueue.take())
            }
        } catch (e: IOException) {
            // Соединение закрыто — читатель завершит обработку
        } catch (e: Exception) {
            System.err.println("[hgdsNET] ошибка отправки клиенту #$id: ${e.message}")
        }
    }

    fun close() {
        if (closed) return
        closed = true
        try {
            socket.close()
        } catch (e: IOException) {
            // сокет уже закрыт
        }
    }

    override fun toString(): String = "#$id $name ($remote)"
}

/**
 * Сервер hgdsNET: принимает соединения, проверяет рукопожатие,
 * читает пакеты клиентов и рассылает пакеты всем игрокам.
 *
 * Игровая логика (мир, чат, сохранения) — в [listener].
 */
class HgdsServer(
    val port: Int,
    motd: String,
    val maxPlayers: Int,
    private val listener: Listener
) {

    /** Обработчик игровой логики сервера. */
    interface Listener {
        /**
         * Проверка имени и выдача данных мира.
         * Обязан вернуть [Packet.LoginResult] с accepted=false, если вход запрещён.
         */
        fun onLogin(client: ClientConnection, requestedName: String): Packet.LoginResult

        /** Пакет от уже вошедшего игрока. */
        fun onPacket(client: ClientConnection, packet: Packet)

        /** Игрок успешно вошёл и получил данные мира — можно рассылать остальным. */
        fun onJoin(client: ClientConnection)

        /** Игрок отключился (соединение закрыто или кикнут). */
        fun onDisconnect(client: ClientConnection)
    }

    /** Описание сервера для списка серверов (можно менять из консоли). */
    @Volatile
    var motd: String = motd

    /** Журнал сервера: консоль + файл. */
    var log: (String) -> Unit = { println(it) }

    private val serverSocket = ServerSocket()

    /** Порт, на котором сервер реально принимает соединения (при port = 0 — назначенный системой). */
    val boundPort: Int get() = if (serverSocket.isBound) serverSocket.localPort else port
    private val clients = ConcurrentHashMap<Int, ClientConnection>()
    private var nextId = AtomicInteger()

    @Volatile
    private var running = false
    private var acceptThread: Thread? = null

    /** Запускает приём соединений. */
    fun start() {
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(port))
        running = true
        acceptThread = Thread({ acceptLoop() }, "hgdsNET-accept").apply {
            isDaemon = true
            start()
        }
    }

    private fun acceptLoop() {
        while (running) {
            val socket = try {
                serverSocket.accept()
            } catch (e: IOException) {
                break // сокет закрыт — сервер останавливается
            }
            Thread({ serve(socket) }, "hgdsNET-conn").apply {
                isDaemon = true
                start()
            }
        }
    }

    /** Обслуживание одного соединения: рукопожатие, затем чтение пакетов. */
    private fun serve(socket: Socket) {
        var connection: ClientConnection? = null
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = HgdsNet.HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val client = ClientConnection(
                nextId.incrementAndGet(), socket, output, describe(socket)
            )
            connection = client

            val first = HgdsNet.readPacket(input)
            if (first is Packet.ServerPing) {
                HgdsNet.writePacket(
                    output,
                    Packet.ServerPong(motd, playerCount(), maxPlayers, HgdsNet.VERSION, first.time)
                )
                client.close()
                return
            }

            val refusal = when {
                first !is Packet.LoginRequest ->
                    Packet.Kick("Ожидалось рукопожатие ${HgdsNet.MAGIC}")

                first.magic != HgdsNet.MAGIC ->
                    Packet.Kick("Это не сервер ${HgdsNet.MAGIC}")

                first.protocolVersion != HgdsNet.VERSION ->
                    Packet.Kick(
                        "Протокол v${first.protocolVersion} не совпадает с серверным v${HgdsNet.VERSION}"
                    )

                !isValidName(first.name) ->
                    Packet.Kick("Имя «${first.name}» недопустимо")

                else -> null
            }
            if (refusal != null) {
                HgdsNet.writePacket(output, refusal)
                client.close()
                return
            }

            client.name = (first as Packet.LoginRequest).name
            val result = listener.onLogin(client, client.name)
            if (!result.accepted) {
                HgdsNet.writePacket(output, result)
                client.close()
                return
            }

            socket.soTimeout = HgdsNet.READ_TIMEOUT_MS
            client.startWriting()
            client.position = result.spawn
            client.loggedIn = true
            clients[client.id] = client
            client.send(result)
            listener.onJoin(client)
            log("Игрок ${client.name} вошёл в мир (${playerCount()}/$maxPlayers)")
            readLoop(input, client)
        } catch (e: IOException) {
            // обрыв соединения или таймаут чтения — штатная ситуация
        } catch (e: Exception) {
            System.err.println("[hgdsNET] ошибка соединения: ${e.message}")
        } finally {
            val client = connection
            if (client != null) {
                val wasLogged = clients.remove(client.id) != null
                client.close()
                if (wasLogged) listener.onDisconnect(client)
            }
        }
    }

    /** Читает пакеты, пока соединение живо. PING/PONG обрабатывает сам транспорт. */
    private fun readLoop(input: DataInputStream, client: ClientConnection) {
        while (running && !client.closed) {
            val packet = HgdsNet.readPacket(input)
            client.lastPacketMs = System.currentTimeMillis()
            if (packet is Packet.Ping) {
                client.send(Packet.Pong(packet.time))
            } else {
                listener.onPacket(client, packet)
            }
        }
    }

    /** Игроки на сервере, в порядке входа. */
    fun players(): List<ClientConnection> = clients.values.sortedBy { it.id }

    fun playerCount(): Int = clients.size

    fun findByName(name: String): ClientConnection? =
        clients.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Рассылает пакет всем игрокам, кроме [exceptId] (-1 — всем). */
    fun broadcast(packet: Packet, exceptId: Int = -1) {
        for (client in clients.values) {
            if (client.id != exceptId) client.send(packet)
        }
    }

    /** Отключает игрока с причиной. */
    fun kick(client: ClientConnection, reason: String) {
        client.send(Packet.Kick(reason))
        client.close()
    }

    fun isRunning(): Boolean = running

    /** Останавливает сервер: рассылает KICK и закрывает сокет. */
    fun stop() {
        if (!running) return
        running = false
        for (client in clients.values) {
            client.send(Packet.Kick("Сервер остановлен"))
        }
        try {
            serverSocket.close()
        } catch (e: IOException) {
            // сокет уже закрыт
        }
        for (client in clients.values) client.close()
        clients.clear()
        acceptThread?.join(1000L)
    }

    private fun describe(socket: Socket): String {
        val address = socket.remoteSocketAddress as? InetSocketAddress ?: return socket.remoteSocketAddress.toString()
        return "${address.address?.hostAddress ?: address.hostString}:${address.port}"
    }

    /** Имя игрока: 1..16 символов, буквы (любой раскладки), цифры, «_» и «-». */
    private fun isValidName(name: String): Boolean {
        if (name.length < MIN_NAME_LENGTH || name.length > MAX_NAME_LENGTH) return false
        return name.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    companion object {
        const val MIN_NAME_LENGTH = 1
        const val MAX_NAME_LENGTH = 16
    }
}

