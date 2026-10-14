package hgds.IloveGroup.server

import hgds.IloveGroup.DEFAULT_RENDER_DISTANCE
import hgds.IloveGroup.GameInfo
import hgds.IloveGroup.net.BlockChangeEntry
import hgds.IloveGroup.net.ChatKind
import hgds.IloveGroup.net.ClientConnection
import hgds.IloveGroup.net.HgdsNet
import hgds.IloveGroup.net.HgdsServer
import hgds.IloveGroup.net.NetworkPlayer
import hgds.IloveGroup.net.NetworkPosition
import hgds.IloveGroup.net.Packet
import hgds.IloveGroup.util.AppDirs
import hgds.IloveGroup.world.BlockRegistry
import hgds.IloveGroup.world.TerrainGenerator
import hgds.IloveGroup.world.World
import hgds.IloveGroup.world.save.LevelData
import hgds.IloveGroup.world.save.WorldConfig
import hgds.IloveGroup.world.save.WorldStorage
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/** Настройки выделенного сервера (берутся из аргументов командной строки). */
data class ServerOptions(
    val port: Int = HgdsNet.DEFAULT_PORT,
    val worldName: String = "world",
    /** null — сид из сохранённого мира, при создании — случайный. */
    val seed: Long? = null,
    val flat: Boolean = false,
    val maxPlayers: Int = 8,
    val renderDistance: Int = DEFAULT_RENDER_DISTANCE,
    val motd: String = "Сервер ${GameInfo.NAME} на hgdsNET",
    val ticksPerSecond: Int = 20,
    val savesDir: Path = AppDirs.serverSavesDir,
    val logsDir: Path = AppDirs.serverLogsDir
)

/** Результат команды консоли: строки ответа и флаг «сервер нужно остановить». */
data class CommandResult(val lines: List<String> = emptyList(), val shutdown: Boolean = false)

/**
 * Игровая логика выделенного сервера: мир, игроки, чат, изменения блоков,
 * сохранения и команды консоли. Мир защищён монитором [worldLock],
 * так как к нему обращаются потоки клиентов, поток тиков и поток консоли.
 */
class GameServer(val options: ServerOptions) : HgdsServer.Listener {

    /** Журнал сервера (консоль + файлы). */
    val log = ServerLog(options.logsDir)

    /** Реестр блоков: те же blocks.json, что и у клиента. */
    private val registry = BlockRegistry.load()

    private val worldLock = Any()
    private val world: World
    private var server: HgdsServer

    @Volatile
    private var level: LevelData

    /** Папка мира на диске. */
    private lateinit var worldDir: Path

    private val running = AtomicBoolean(false)
    private var tickThread: Thread? = null
    private var startedAtMs = 0L
    private var lastSaveMs = 0L

    @Volatile
    private var dirty = false

    /** Время последнего сообщения чата от игрока (защита от флуда). */
    private val chatCooldownMs = ConcurrentHashMap<Int, Long>()

    /** Сколько тиков в секунду крутит сервер (команда tps). */
    @Volatile
    var ticksPerSecond: Int = options.ticksPerSecond.coerceIn(1, 60)

    /** Описание сервера для списка серверов (команда motd). */
    var motd: String = options.motd
        set(value) {
            field = value
            server.motd = value
        }

    init {
        level = loadOrCreateWorld()
        val generator = TerrainGenerator(
            level.seed,
            registry.index("grass_block"),
            registry.index("dirt_block"),
            registry.index("stone_block"),
            level.flat,
            registry.index("oak_log"),
            registry.index("oak_leaves")
        )
        world = World(generator)
        world.loadEdits(level.edits)
        server = HgdsServer(options.port, motd, options.maxPlayers, this)
        server.log = { line -> log.info(line) }
    }

    /** Порт, на котором сервер реально принимает подключения (0 в настройках — выбранный ОС). */
    val boundPort: Int get() = server.boundPort

    /** Текущий блок мира (для консольных команд и проверок). */
    fun blockAt(x: Int, y: Int, z: Int): Int = synchronized(worldLock) { world.getBlock(x, y, z) }

    /** Сид текущего мира. */
    val worldSeed: Long get() = world.terrainSeed

    /** Имя мира. */
    val worldName: String get() = level.name

    /** Находит мир по имени в папке сервера или создаёт новый. */
    private fun loadOrCreateWorld(): LevelData {
        val existing = WorldStorage.list(options.savesDir)
            .firstOrNull { it.data.name.equals(options.worldName, ignoreCase = true) }
        if (existing != null) {
            worldDir = existing.dir
            log.info("Загружен мир «${existing.data.name}» (сид ${existing.data.seed}): ${existing.dir}")
            return existing.data
        }
        val seed = options.seed ?: Random.nextLong()
        val created = WorldStorage.create(
            options.savesDir,
            WorldConfig(options.worldName, seed, options.flat, options.renderDistance)
        )
        worldDir = created.dir
        log.info("Создан мир «${created.data.name}» (сид ${created.data.seed}): ${created.dir}")
        return created.data
    }

    /** Точка появления: нулевые координаты, на 2 блока выше рельефа. */
    private fun spawnPosition(): NetworkPosition {
        val x = 0.5f
        val z = 0.5f
        val y = world.surfaceHeight(x.toInt(), z.toInt()) + 2f
        return NetworkPosition(x, y, z, 0f, 0f)
    }

    /** Все изменения блоков мира — для отправки новому игроку. */
    private fun snapshotChanges(): List<BlockChangeEntry> {
        val result = ArrayList<BlockChangeEntry>()
        for ((key, blockIndex) in world.exportEdits()) {
            val parts = key.split(',')
            if (parts.size != 3) continue
            val x = parts[0].trim().toIntOrNull() ?: continue
            val y = parts[1].trim().toIntOrNull() ?: continue
            val z = parts[2].trim().toIntOrNull() ?: continue
            result.add(BlockChangeEntry(x, y, z, blockIndex))
        }
        return result
    }

    /** name блока по индексу — для журнала и команды setblock. */
    private fun blockName(blockIndex: Int): String = when (blockIndex) {
        BlockRegistry.AIR -> "air"
        else -> registry.byIndex(blockIndex)?.id ?: "block#$blockIndex"
    }

    /** Запускает приём игроков и игровой цикл сервера. */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        startedAtMs = System.currentTimeMillis()
        lastSaveMs = startedAtMs
        log.info("======== ${GameInfo.NAME} Server ========")
        log.info("Протокол ${HgdsNet.MAGIC} v${HgdsNet.VERSION}, порт ${options.port}")
        log.info("Мир «${level.name}», сид ${world.terrainSeed}, ${if (level.flat) "суперплоский" else "обычный"}")
        log.info("Изменений блоков в мире: ${level.edits.size}, игроков до ${options.maxPlayers}")
        log.info("Подключение: ${connectAddress()}")
        log.info("Журнал: ${log.serverLogFile}")
        log.info("Команда help — список команд консоли")
        log.info("====================================")
        server.start()
        log.info("Сервер слушает порт ${server.boundPort}")
        tickThread = Thread({ tickLoop() }, "hgdsNET-tick").apply {
            isDaemon = true
            start()
        }
    }

    private fun connectAddress(): String {
        val host = try {
            java.net.InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            "127.0.0.1"
        }
        return "$host:${options.port} (такой же адрес в списке серверов игры)"
    }

    /** Игровой цикл сервера: рассылка позиций, автосохранение. */
    private fun tickLoop() {
        while (running.get()) {
            val started = System.nanoTime()
            val players = server.players()
            if (players.size > 1) {
                val snapshot = ArrayList<NetworkPlayer>(players.size)
                for (player in players) snapshot.add(NetworkPlayer(player.id, player.name, player.position))
                server.broadcast(Packet.PlayerUpdate(snapshot))
            }
            autosave(System.currentTimeMillis())
            val tickMillis = 1000L / ticksPerSecond
            val spent = (System.nanoTime() - started) / 1_000_000L
            val sleep = (tickMillis - spent).coerceAtLeast(1L)
            try {
                Thread.sleep(sleep)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    /** Автосохранение мира раз в AUTOSAVE_INTERVAL_MS, если были изменения. */
    private fun autosave(now: Long) {
        if (!dirty) return
        if (now - lastSaveMs < AUTOSAVE_INTERVAL_MS) return
        save("автосохранение")
    }

    /** Сохраняет мир на диск. Возвращает число сохранённых изменений блоков. */
    fun save(reason: String): Int {
        synchronized(worldLock) {
            level = WorldStorage.save(worldDir, level, null, world)
            dirty = false
            lastSaveMs = System.currentTimeMillis()
            log.info("Мир сохранён ($reason): изменений ${level.edits.size}")
            return level.edits.size
        }
    }

    /** Останавливает сервер: отключает игроков, сохраняет мир. */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        log.info("Остановка сервера…")
        server.stop()
        tickThread?.join(2000L)
        if (dirty) save("при остановке")
        log.info("Сервер остановлен. Изменений блоков за сеанс: ${log.blockChanges}")
    }

    fun isRunning(): Boolean = running.get()

    /** Имена игроков на сервере. */
    fun playerNames(): List<String> = server.players().map { it.name }

    /** Шлёт системное сообщение всем игрокам и в журнал. */
    fun broadcastSystem(text: String) {
        server.broadcast(Packet.Chat(SERVER_SENDER, text, ChatKind.SYSTEM))
        log.chat("", text)
    }

    // ------------- Обработчики пакетов hgdsNET -------------

    override fun onLogin(client: ClientConnection, requestedName: String): Packet.LoginResult {
        if (server.findByName(requestedName) != null) {
            log.warn("Вход отклонён: имя «$requestedName» уже занято (${client.remote})")
            return rejected("Игрок с именем «$requestedName» уже на сервере")
        }
        if (server.playerCount() >= options.maxPlayers) {
            log.warn("Вход отклонён: сервер заполнен (${client.remote})")
            return rejected("На сервере нет места (максимум ${options.maxPlayers})")
        }
        val changes: List<BlockChangeEntry>
        val others: List<NetworkPlayer>
        val spawn: NetworkPosition
        synchronized(worldLock) {
            changes = snapshotChanges()
            others = server.players()
                .filter { it.id != client.id }
                .map { NetworkPlayer(it.id, it.name, it.position) }
            spawn = spawnPosition()
        }
        log.info("Игрок $requestedName подключается с ${client.remote}")
        return Packet.LoginResult(
            true,
            client.id,
            "Добро пожаловать в мир «${level.name}»",
            world.terrainSeed,
            level.flat,
            level.renderDistance,
            spawn,
            changes,
            others
        )
    }

    private fun rejected(message: String): Packet.LoginResult = Packet.LoginResult(
        false, -1, message, 0L, false, 0,
        NetworkPosition(0f, 0f, 0f, 0f, 0f), emptyList(), emptyList()
    )

    override fun onJoin(client: ClientConnection) {
        server.broadcast(
            Packet.PlayerJoin(NetworkPlayer(client.id, client.name, client.position)),
            client.id
        )
        val text = "${client.name} присоединился к игре"
        server.broadcast(Packet.Chat("", text, ChatKind.JOIN_LEAVE))
        log.chat("", text)
        log.info("Игрок ${client.name} вошёл (${server.playerCount()}/${options.maxPlayers} на сервере)")
    }

    override fun onPacket(client: ClientConnection, packet: Packet) {
        when (packet) {
            is Packet.PlayerPosition -> client.position = packet.position
            is Packet.BlockChange -> applyBlockChange(client, packet.change)
            is Packet.Chat -> handleChat(client, packet)
            else -> Unit // прочие пакеты сервер от клиента не принимает
        }
    }

    override fun onDisconnect(client: ClientConnection) {
        server.broadcast(Packet.PlayerLeave(client.id))
        val text = "${client.name} покинул игру"
        server.broadcast(Packet.Chat("", text, ChatKind.JOIN_LEAVE))
        log.chat("", text)
        log.info("Игрок ${client.name} отключился (${server.playerCount()}/${options.maxPlayers} на сервере)")
    }

    /** Проверяет и применяет изменение блока, затем рассылает его остальным. */
    private fun applyBlockChange(client: ClientConnection, change: BlockChangeEntry) {
        if (change.y < 0 || change.y >= World.WORLD_HEIGHT) return
        val known = change.blockIndex == BlockRegistry.AIR || registry.byIndex(change.blockIndex) != null
        if (!known || change.blockIndex < 0) return
        val distance = maxOf(
            kotlin.math.abs(change.x + 0.5f - client.position.x),
            kotlin.math.abs(change.y + 0.5f - client.position.y),
            kotlin.math.abs(change.z + 0.5f - client.position.z)
        )
        if (distance > MAX_BLOCK_REACH) {
            client.send(Packet.Chat(SERVER_SENDER, "Слишком далеко, чтобы менять блок", ChatKind.SYSTEM))
            return
        }
        synchronized(worldLock) {
            if (world.getBlock(change.x, change.y, change.z) == change.blockIndex) return
            world.setBlockByPlayer(change.x, change.y, change.z, change.blockIndex)
            dirty = true
        }
        server.broadcast(Packet.BlockChange(change))
        log.blockChange(
            "${client.name}: (${change.x},${change.y},${change.z}) → ${blockName(change.blockIndex)}"
        )
    }

    /** Чат игрока: защита от флуда, рассылка и запись в историю чата. */
    private fun handleChat(client: ClientConnection, packet: Packet.Chat) {
        val text = packet.text.replace('\n', ' ').replace('\r', ' ').trim()
        if (text.isEmpty()) return
        val now = System.currentTimeMillis()
        val previous = chatCooldownMs[client.id] ?: 0L
        if (now - previous < CHAT_COOLDOWN_MS) return
        chatCooldownMs[client.id] = now
        val clipped = if (text.length > MAX_CHAT_LENGTH) text.substring(0, MAX_CHAT_LENGTH) else text
        server.broadcast(Packet.Chat(client.name, clipped, ChatKind.CHAT))
        log.chat(client.name, clipped)
    }

    // ---------------- Команды консоли ----------------

    /** Сколько секунд сервер запущен. */
    fun uptimeSeconds(): Long =
        if (startedAtMs == 0L) 0L else (System.currentTimeMillis() - startedAtMs) / 1000L

    /** Выполняет строку, введённую в консоль сервера. */
    fun execute(line: String): CommandResult {
        val input = line.trim()
        if (input.isEmpty()) return CommandResult()
        val parts = input.split(' ', limit = 2)
        val command = parts[0].lowercase()
        val rest = if (parts.size > 1) parts[1].trim() else ""
        return when (command) {
            "help", "?" -> CommandResult(help())

            "list", "players" -> {
                val names = playerNames()
                CommandResult(
                    listOf(
                        if (names.isEmpty()) "На сервере никого нет (${options.maxPlayers} мест)"
                        else "Игроки (${names.size}/${options.maxPlayers}): ${names.joinToString(", ")}"
                    )
                )
            }

            "say" -> {
                if (rest.isEmpty()) CommandResult(listOf("Использование: say <текст>"))
                else {
                    broadcastSystem(rest)
                    CommandResult()
                }
            }

            "kick" -> kick(rest)
            "seed" -> CommandResult(listOf("Сид мира: ${world.terrainSeed}"))
            "status" -> CommandResult(status())
            "tps" -> setTps(rest)
            "save" -> CommandResult(listOf("Изменений блоков сохранено: ${save("из консоли")}"))
            "setblock" -> setBlock(rest)

            "motd" -> {
                if (rest.isEmpty()) CommandResult(listOf("Описание сервера: $motd"))
                else {
                    motd = rest
                    CommandResult(listOf("Описание сервера изменено на «$rest»"))
                }
            }

            "log" -> {
                val count = rest.toIntOrNull()?.coerceIn(1, ServerLog.MAX_RECENT) ?: 10
                CommandResult(log.tail(count))
            }

            "version" -> CommandResult(
                listOf("${GameInfo.DISPLAY_NAME} · протокол ${HgdsNet.MAGIC} v${HgdsNet.VERSION} · мир «${level.name}»")
            )

            "stop", "exit", "quit" -> {
                log.info("Команда остановки из консоли")
                stop()
                CommandResult(listOf("Сервер остановлен"), shutdown = true)
            }

            else -> CommandResult(listOf("Неизвестная команда «$command» — введите help"))
        }
    }

    private fun help(): List<String> = listOf(
        "help — этот список",
        "list — игроки на сервере",
        "say <текст> — системное сообщение всем игрокам",
        "kick <игрок> [причина] — отключить игрока",
        "status — состояние сервера",
        "seed — сид мира",
        "tps [1..60] — тиков в секунду (сейчас $ticksPerSecond)",
        "save — сохранить мир сейчас",
        "motd <текст> — описание сервера в списке серверов",
        "setblock <x> <y> <z> <блок> — поставить блок (air — воздух)",
        "log [n] — последние n строк журнала",
        "version — версия игры и протокола",
        "stop — остановить сервер"
    )

    private fun status(): List<String> {
        val names = playerNames()
        return listOf(
            "Мир: «${level.name}», сид ${world.terrainSeed}, ${if (level.flat) "суперплоский" else "обычный"}",
            "Изменений блоков: ${level.edits.size} (за сеанс: ${log.blockChanges})",
            "Игроков: ${names.size}/${options.maxPlayers}" + if (names.isEmpty()) "" else " — ${names.joinToString(", ")}",
            "Порт: ${options.port}, тиков: $ticksPerSecond/с, аптайм: ${uptimeSeconds()} с",
            "Описание: $motd"
        )
    }

    private fun kick(rest: String): CommandResult {
        if (rest.isEmpty()) return CommandResult(listOf("Использование: kick <игрок> [причина]"))
        val parts = rest.split(' ', limit = 2)
        val target = server.findByName(parts[0])
            ?: return CommandResult(listOf("Игрок «${parts[0]}» не найден"))
        val reason = if (parts.size > 1) parts[1] else "Отключён администратором"
        log.info("Игрок ${target.name} отключён: $reason")
        server.kick(target, reason)
        return CommandResult(listOf("${target.name} отключён: $reason"))
    }

    private fun setTps(rest: String): CommandResult {
        if (rest.isEmpty()) return CommandResult(listOf("Тиков в секунду: $ticksPerSecond"))
        val value = rest.toIntOrNull() ?: return CommandResult(listOf("Нужно целое число от 1 до 60"))
        ticksPerSecond = value.coerceIn(1, 60)
        return CommandResult(listOf("Тиков в секунду: $ticksPerSecond"))
    }

    /** Индекс блока по имени («air» — воздух); null, если такого блока нет. */
    private fun blockIndexById(id: String): Int? {
        if (id.equals("air", ignoreCase = true)) return BlockRegistry.AIR
        val definition = registry.all().firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: return null
        return registry.index(definition.id)
    }

    /** Команда setblock: меняет блок и рассылает изменение игрокам. */
    private fun setBlock(rest: String): CommandResult {
        val parts = rest.split(' ')
        if (parts.size < 4) {
            return CommandResult(listOf("Использование: setblock <x> <y> <z> <имя блока|air>"))
        }
        val x = parts[0].toIntOrNull() ?: return CommandResult(listOf("Координата X должна быть целым числом"))
        val y = parts[1].toIntOrNull() ?: return CommandResult(listOf("Координата Y должна быть целым числом"))
        val z = parts[2].toIntOrNull() ?: return CommandResult(listOf("Координата Z должна быть целым числом"))
        if (y < 0 || y >= World.WORLD_HEIGHT) {
            return CommandResult(listOf("Y вне мира (0..${World.WORLD_HEIGHT - 1})"))
        }
        val index = blockIndexById(parts[3])
            ?: return CommandResult(
                listOf("Нет блока «${parts[3]}». Доступны: " + registry.all().joinToString(", ") { it.id })
            )
        synchronized(worldLock) {
            world.ensureColumn(x.floorDiv(World.CHUNK_SIZE_X), z.floorDiv(World.CHUNK_SIZE_Z))
            world.setBlockByPlayer(x, y, z, index)
            dirty = true
        }
        server.broadcast(Packet.BlockChange(BlockChangeEntry(x, y, z, index)))
        log.blockChange("консоль: ($x,$y,$z) → ${blockName(index)}")
        return CommandResult(listOf("Блок ($x,$y,$z) → ${blockName(index)}"))
    }

    private companion object {
        /** Имя отправителя системных сообщений. */
        const val SERVER_SENDER = "Сервер"

        /** Период автосохранения мира, мс. */
        const val AUTOSAVE_INTERVAL_MS = 60_000L

        /** Максимальное расстояние до блока, который разрешено менять игроку. */
        const val MAX_BLOCK_REACH = 8f

        /** Минимальная пауза между сообщениями чата одного игрока, мс. */
        const val CHAT_COOLDOWN_MS = 400L

        /** Максимальная длина сообщения чата. */
        const val MAX_CHAT_LENGTH = 200
    }
}




