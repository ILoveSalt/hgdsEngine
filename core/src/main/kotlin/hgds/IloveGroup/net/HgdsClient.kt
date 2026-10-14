package hgds.IloveGroup.net

import hgds.IloveGroup.net.HgdsNet.readPacket
import hgds.IloveGroup.net.HgdsNet.writePacket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue

/**
 * Клиент hgdsNET: соединение с сервером, отдельный поток чтения и очередь
 * исходящих пакетов.
 *
 * Принятые пакеты складываются в [inbox] и обрабатываются игровым циклом
 * через [poll] — вся логика (в том числе OpenGL) остаётся в главном потоке,
 * а сетевые потоки ничего, кроме очередей, не трогают.
 */
class HgdsClient {

    /** Идентификатор игрока на сервере после успешного входа. */
    @Volatile
    var playerId: Int = -1
        private set

    @Volatile
    var connected: Boolean = false
        private set

    /** Последняя измеренная задержка до сервера, мс. */
    @Volatile
    var latencyMs: Int = 0
        private set

    /** Причина, по которой сервер отключил клиента (Kick). */
    @Volatile
    var kickReason: String? = null
        private set

    /** true, когда соединение оборвалось — нужно выйти в меню. */
    @Volatile
    var disconnected: Boolean = false
        private set

    private val inbox = LinkedBlockingQueue<Packet>()
    private val outbox = LinkedBlockingQueue<Packet>()
    private var socket: Socket? = null
    private var readerThread: Thread? = null
    private var writerThread: Thread? = null

    @Volatile
    private var lastPositionSendMs = 0L

    @Volatile
    private var lastPingMs = 0L

    /**
     * Подключается и выполняет рукопожатие. Вызов блокирующий — из игрового цикла
     * при входе на сервер.
     *
     * @throws IOException если сервер недоступен или отказал во входа
     */
    fun connect(
        host: String,
        port: Int = HgdsNet.DEFAULT_PORT,
        name: String,
        timeoutMs: Int = 5000
    ): Packet.LoginResult {
        val (hostName, hostPort) = parseAddress(host, port)
        val newSocket = Socket()
        newSocket.connect(InetSocketAddress(hostName, hostPort), timeoutMs)
        newSocket.tcpNoDelay = true
        newSocket.soTimeout = HgdsNet.READ_TIMEOUT_MS
        val streamOutput = DataOutputStream(BufferedOutputStream(newSocket.getOutputStream()))
        val streamInput = DataInputStream(BufferedInputStream(newSocket.getInputStream()))
        socket = newSocket

        writePacket(streamOutput, Packet.LoginRequest(name))
        val answer = readPacket(streamInput)
        if (answer !is Packet.LoginResult) throw IOException("Сервер ответил не на рукопожатие")
        if (!answer.accepted) {
            newSocket.close()
            throw IOException(answer.message.ifEmpty { "Сервер отказал во входа" })
        }
        playerId = answer.playerId
        connected = true
        disconnected = false
        readerThread = Thread({ readLoop(streamInput) }, "hgdsNET-client-read").apply {
            isDaemon = true
            start()
        }
        writerThread = Thread({ writeLoop(streamOutput) }, "hgdsNET-client-write").apply {
            isDaemon = true
            start()
        }
        return answer
    }

    private fun readLoop(streamInput: DataInputStream) {
        try {
            while (connected) {
                val packet = readPacket(streamInput)
                if (packet is Packet.Pong) {
                    latencyMs = (System.currentTimeMillis() - packet.time).toInt()
                    continue
                }
                if (packet is Packet.Kick) kickReason = packet.reason
                inbox.add(packet)
            }
        } catch (e: IOException) {
            // соединение оборвалось — игровой цикл увидит disconnected == true
        } finally {
            connected = false
            disconnected = true
        }
    }

    private fun writeLoop(streamOutput: DataOutputStream) {
        try {
            while (connected) {
                writePacket(streamOutput, outbox.take())
            }
        } catch (e: IOException) {
            connected = false
            disconnected = true
        } catch (e: InterruptedException) {
            // соединение закрывается
        }
    }

    /** Ставит пакет в очередь отправки. */
    fun send(packet: Packet) {
        if (connected) outbox.add(packet)
    }

    /** Отдаёт накопленные серверные пакеты (не более [maxCount] за кадр). */
    fun poll(maxCount: Int = 64): List<Packet> {
        val packets = ArrayList<Packet>()
        while (packets.size < maxCount) {
            val packet = inbox.poll() ?: break
            packets.add(packet)
        }
        return packets
    }

    /** Отправляет позицию не чаще 20 раз в секунду. */
    fun sendPosition(position: NetworkPosition) {
        val now = System.currentTimeMillis()
        if (now - lastPositionSendMs < HgdsNet.POSITION_BROADCAST_INTERVAL_MS) return
        lastPositionSendMs = now
        send(Packet.PlayerPosition(position))
    }

    /** Периодический пинг для замера задержки (вызывать из игрового цикла). */
    fun keepAlive(intervalMs: Long = 2000L) {
        val now = System.currentTimeMillis()
        if (now - lastPingMs < intervalMs) return
        lastPingMs = now
        send(Packet.Ping(now))
    }

    fun close() {
        connected = false
        try {
            socket?.close()
        } catch (e: IOException) {
            // сокет уже закрыт
        }
        socket = null
        outbox.clear()
        inbox.clear()
    }

    companion object {

        /** Разбирает адрес сервера: «host», «host:port» или «:port». */
        fun parseAddress(address: String, defaultPort: Int = HgdsNet.DEFAULT_PORT): Pair<String, Int> {
            val trimmed = address.trim()
            val separator = trimmed.lastIndexOf(':')
            if (separator < 0) return trimmed to defaultPort
            val host = trimmed.substring(0, separator)
            val port = trimmed.substring(separator + 1).toIntOrNull() ?: defaultPort
            return (if (host.isEmpty()) "127.0.0.1" else host) to port
        }

        /**
         * Быстрый опрос сервера из списка (без входа в мир).
         * null — сервер недоступен или не ответил.
         */
        fun ping(address: String, timeoutMs: Int = 1500): Packet.ServerPong? {
            val (host, port) = parseAddress(address)
            val socket = Socket()
            return try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val started = System.currentTimeMillis()
                writePacket(output, Packet.ServerPing(started))
                val answer = readPacket(input)
                if (answer is Packet.ServerPong) answer else null
            } catch (e: SocketTimeoutException) {
                null
            } catch (e: IOException) {
                null
            } finally {
                try {
                    socket.close()
                } catch (e: IOException) {
                    // сокет уже закрыт
                }
            }
        }
    }
}

