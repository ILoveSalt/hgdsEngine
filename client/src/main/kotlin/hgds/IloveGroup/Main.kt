package hgds.IloveGroup

import hgds.IloveGroup.engine.Shader
import hgds.IloveGroup.engine.Window
import hgds.IloveGroup.entity.Villager
import hgds.IloveGroup.net.BlockChangeEntry
import hgds.IloveGroup.net.ChatKind
import hgds.IloveGroup.net.HgdsClient
import hgds.IloveGroup.net.MultiplayerSession
import hgds.IloveGroup.net.Packet
import hgds.IloveGroup.player.Player
import hgds.IloveGroup.render.GuiRenderer
import hgds.IloveGroup.render.HudRenderer
import hgds.IloveGroup.render.LineRenderer
import hgds.IloveGroup.render.TextureAtlas
import hgds.IloveGroup.render.TextureAtlasData
import hgds.IloveGroup.render.font.BitmapFont
import hgds.IloveGroup.render.font.FontRenderer
import hgds.IloveGroup.render.font.TrueTypeFont
import hgds.IloveGroup.ui.screens.OptionsScreen
import hgds.IloveGroup.ui.ChatHud
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.ui.UiTextures
import hgds.IloveGroup.ui.screens.ConnectingScreen
import hgds.IloveGroup.ui.screens.CreateWorldScreen
import hgds.IloveGroup.ui.screens.MultiplayerScreen
import hgds.IloveGroup.ui.screens.PauseScreen
import hgds.IloveGroup.ui.screens.SingleplayerScreen
import hgds.IloveGroup.ui.screens.TitleScreen
import hgds.IloveGroup.util.AppDirs
import hgds.IloveGroup.util.GameOptions
import hgds.IloveGroup.util.Lang
import hgds.IloveGroup.util.Resources
import hgds.IloveGroup.world.BlockRegistry
import hgds.IloveGroup.world.ChunkColumn
import hgds.IloveGroup.world.Raycaster
import hgds.IloveGroup.world.Section
import hgds.IloveGroup.world.TerrainGenerator
import hgds.IloveGroup.world.World
import hgds.IloveGroup.world.save.LevelData
import hgds.IloveGroup.world.save.WorldConfig
import hgds.IloveGroup.world.save.WorldStorage
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW.GLFW_KEY_0
import org.lwjgl.glfw.GLFW.GLFW_KEY_1
import org.lwjgl.glfw.GLFW.GLFW_KEY_2
import org.lwjgl.glfw.GLFW.GLFW_KEY_3
import org.lwjgl.glfw.GLFW.GLFW_KEY_E
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_D
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_KEY_F
import org.lwjgl.glfw.GLFW.GLFW_KEY_F3
import org.lwjgl.glfw.GLFW.GLFW_KEY_F5
import org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL
import org.lwjgl.glfw.GLFW.GLFW_KEY_I
import org.lwjgl.glfw.GLFW.GLFW_KEY_J
import org.lwjgl.glfw.GLFW.GLFW_KEY_K
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT
import org.lwjgl.glfw.GLFW.GLFW_KEY_L
import org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS
import org.lwjgl.glfw.GLFW.GLFW_KEY_O
import org.lwjgl.glfw.GLFW.GLFW_KEY_U
import org.lwjgl.glfw.GLFW.GLFW_KEY_G
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_S
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_T
import org.lwjgl.glfw.GLFW.GLFW_KEY_W
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_2
import org.lwjgl.glfw.GLFW.glfwGetTime
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glClearColor
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glLineWidth
import hgds.IloveGroup.render.CelestialRenderer
import hgds.IloveGroup.render.ChunkMeshFactory
import hgds.IloveGroup.render.PlayerRenderer
import hgds.IloveGroup.render.PlayerSkin
import hgds.IloveGroup.render.SkyTextures
import hgds.IloveGroup.world.DayNightCycle
import java.nio.file.Path
import kotlin.concurrent.thread

/** Состояния игры: экраны меню или игровой мир. */
enum class GameState {
    TITLE,
    SINGLEPLAYER,
    CREATE_WORLD,
    MULTIPLAYER,
    CONNECTING,
    PLAYING
}

/**
 * Подключение к серверу в фоновом потоке: connect и рукопожатие блокируют,
 * а игровой цикл всё это время должен рисовать экран ожидания.
 * Результат ([login] или [error]) читает игровой цикл, когда [finished] = true.
 */
class ConnectJob(val address: String, val nick: String) {
    val client = HgdsClient()

    @Volatile
    var login: Packet.LoginResult? = null
        private set

    @Volatile
    var error: String? = null
        private set

    @Volatile
    var finished = false
        private set

    @Volatile
    var cancelled = false

    init {
        thread(isDaemon = true, name = "hgds-connect") {
            try {
                val (host, port) = HgdsClient.parseAddress(address)
                login = client.connect(host, port, nick)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            // Если игрок отменил подключение или что-то сломалось — соединение не нужно
            if (cancelled || error != null) client.close()
            finished = true
        }
    }

    fun cancel() {
        cancelled = true
    }
}

fun main() {
    AppDirs.init()
    Resources.exportDefaults() // выгрузка ресурсов jar в <папка игры>/assets
    Lang.init(AppDirs.langsDir)
    GameOptions.load(AppDirs.gameDir)

    var window: Window? = null
    var blockShader: Shader? = null
    var lineShader: Shader? = null
    var atlas: TextureAtlas? = null
    var lineRenderer: LineRenderer? = null
    var guiRenderer: GuiRenderer? = null
    var uiTextures: UiTextures? = null
    var fontRenderer: FontRenderer? = null
    var skyRenderer: SkyTextures? = null
    var celestialRenderer: CelestialRenderer? = null
    var playerRenderer: PlayerRenderer? = null
    var villagerRenderer: PlayerRenderer? = null
    var world: World? = null

    try {
        val win = Window(GameInfo.WINDOW_TITLE, 1280, 720).also { it.init() }
        window = win

        // Применяем настройки окна сразу после запуска
        win.setVsync(GameOptions.vsync)
        GameOptions.detectGpus()
        if (GameOptions.resolutionW != 0 && GameOptions.resolutionH != 0 && !GameOptions.fullscreen) {
            win.setWindowSize(GameOptions.resolutionW, GameOptions.resolutionH)
        }
        if (GameOptions.fullscreen) win.setFullscreen(true)

        glEnable(GL_DEPTH_TEST)
        glClearColor(0.53f, 0.74f, 0.92f, 1f) // небо
        glLineWidth(2f)

        val registry = BlockRegistry.load()
        val atlasData = TextureAtlasData.load()
        val atl = TextureAtlas(atlasData)
        atlas = atl

        val blockProg = Shader("/assets/shaders/basic.vert", "/assets/shaders/basic.frag")
        blockShader = blockProg
        blockProg.use()
        blockProg.setInt("uTexture", 0)
        atl.bind(0)

        val lineProg = Shader("/assets/shaders/line.vert", "/assets/shaders/line.frag")
        lineShader = lineProg
        val lines = LineRenderer()
        lineRenderer = lines

        // Интерфейс: рендерер, текстуры (стиль Minecraft) и шрифт с кириллицей
        val gui = GuiRenderer()
        guiRenderer = gui
        val textures = UiTextures()
        uiTextures = textures
        // Небо: текстуры солнца и луны для цикла дня и ночи
        val sky = SkyTextures()
        skyRenderer = sky
        val celestial = CelestialRenderer()
        celestialRenderer = celestial
        // Модель игрока со скином (blem.png) и анимацией ходьбы
        val players = PlayerRenderer()
        playerRenderer = players
        // Атлас блоков — для отрисовки предмета в руке от первого лица
        players.atlTexture = atl
        // Жители: та же геометрия модели, но своя текстура
        val villagerModel = PlayerRenderer(PlayerSkin.VILLAGER_RESOURCE)
        villagerRenderer = villagerModel
        // Шрифт «Minecraft Rus» из resources/minecraft.ttf — растеризуется в атлас
        // при запуске. Если TTF недоступен, откатываемся на растровый шрифт Minecraft.
        val font = try {
            TrueTypeFont.load()
        } catch (e: Exception) {
            System.err.println("[hgdsEngine] TTF-шрифт не загружен (${e.message}), используется растровый")
            BitmapFont.load()
        }
        fontRenderer = font
        val ui = UiContext(win, gui, font, textures)
        val hud = HudRenderer()

        // ----- Состояние игры -----
        var state = GameState.TITLE
        var paused = false
        var screen: Screen? = null

        var gameWorld: World? = null
        var player: Player? = null

        /** Живые жители текущего мира (создаются при входе в мир). */
        val villagers = ArrayList<Villager>()

        var worldDir: Path? = null
        var levelData: LevelData? = null
        var renderDistance = DEFAULT_RENDER_DISTANCE
        var showChunkBorders = false
        var showDebugInfo = false
        // F5: 0 — от первого лица, 1 — третье лицо (за спиной), 2 — третье лицо (спереди, в лицо)
        var cameraMode = 0
        var inventoryOpen = false // E: инвентарь поверх игры
        var wasF3Down = false
        var savedToast = 0f

        // Цикл дня и ночи: время суток, счётчик дней, цвет неба и освещение
        var dayCycle = DayNightCycle()
        val skyColorBuf = FloatArray(3)
        val sunDirBuf = FloatArray(3)

        // Счётчик FPS: кадры копятся одну секунду
        var fps = 0
        var fpsFrames = 0
        var fpsTimer = 0f
        var spawnPos = Vector3f(0f, 80f, 0f)
        var lastHit: hgds.IloveGroup.world.RayHit? = null

        // ----- Сетевая игра -----
        /** Имя игрока, используемое при входе на сервер. */
        var nick = "Steve"

        /** Активный сеанс игры на сервере (null — одиночная игра). */
        var session: MultiplayerSession? = null

        /** Чат текущего сервера (сообщения и строка ввода). */
        val chat = ChatHud()

        /** Причина, по которой пришлось покинуть сервер (показывается в меню). */
        var disconnectNotice: String? = null

        /** Идёт подключение к серверу (фоновый поток рукопожатия). */
        var connectJob: ConnectJob? = null

        /** Матрица проекции для подписей игроков поверх мира. */
        val hudViewProj = Matrix4f()
        val hudClip = Vector4f()

        // Инвентарь: 41 слот — 0..9 хотбар, 10 доп. рука, 11..40 основной инвентарь.
        // Заполняется первыми блоками из blocks.json.
        val hotbar: Array<String?> = Array(41) { i ->
            registry.all().elementAtOrNull(i)?.id
        }
        var selectedSlot = 0
        var dragSlot = -1 // слот, из которого перетаскивается предмет в открытом инвентаре

        // Смещения чанков вокруг игрока (перестраиваются при входе в мир)
        var offsets = ArrayList<IntArray>()

        val viewMatrix = Matrix4f()
        val projMatrix = Matrix4f()
        /** Отдельная проекция прохода руки: MC рисует руку с фиксированным FOV 70. */
        val handProjMatrix = Matrix4f()
        val identityMatrix = Matrix4f()

        fun rebuildOffsets() {
            val list = ArrayList<IntArray>()
            // +1 кольцо: чтобы у секций на краю дальности прорисовки
            // соседи были сгенерированы и меш строился без фризов
            val r = renderDistance + 1
            for (dx in -r..r) {
                for (dz in -r..r) {
                    list.add(intArrayOf(dx, dz))
                }
            }
            list.sortBy { it[0] * it[0] + it[1] * it[1] }
            offsets = list
        }

        /** Сгенерированы ли 4 соседние колонки (нужно для мешинга без фризов). */
        fun neighborsGenerated(w: World, column: ChunkColumn): Boolean {
            val cx = column.cx
            val cz = column.cz
            for ((nx, nz) in listOf(cx - 1 to cz, cx + 1 to cz, cx to cz - 1, cx to cz + 1)) {
                val c = w.columnAt(nx, nz) ?: continue
                if (!c.generated) return false
            }
            return true
        }

        fun disposeWorldMeshes() {
            gameWorld?.allColumns()?.forEach { column ->
                column.sections.forEach { section ->
                    section?.mesh?.dispose()
                    section?.mesh = null
                }
            }
        }

        fun spawnPlayer(w: World, saved: LevelData.PlayerData?): Player {
            if (saved != null) return Player(Vector3f(saved.x, saved.y, saved.z), saved.yaw, saved.pitch)
            // Спавн на нулевых координатах (центр блока 0,0)
            val spawnX = 0.5f
            val spawnZ = 0.5f
            w.ensureColumn(0, 0)
            val spawnY = w.surfaceHeight(spawnX.toInt(), spawnZ.toInt()) + 1f
            // yaw 90° — восток (+X): солнце восходит там (DayNightCycle идёт с +X на -X),
            // поэтому новый игрок сразу смотрит в сторону света
            return Player(Vector3f(spawnX, spawnY, spawnZ), 90f, 0f)
        }

        /**
         * Спавнит жителей мира. Первый живой NPC появляется на (0; 68; 10)
         * и живёт в радиусе 200 блоков от точки спавна.
         */
        fun spawnVillagers(w: World) {
            villagers.clear()
            val vx = 0.5f
            val vz = 10.5f
            w.ensureColumn(0, 0)
            // Целевая точка (0; 68; 10): если рельеф выше — ставим на поверхность,
            // чтобы житель не заспавнился внутри блоков
            val vy = maxOf(w.surfaceHeight(0, 10) + 1f, 68f)
            villagers.add(Villager(vx, vy, vz))
            println("[hgdsEngine] Житель заспавнен: 0 ${vy.toInt()} 10 (радиус жизни 200 блоков)")
        }

        fun enterWorld(dir: Path, data: LevelData, savedPlayer: LevelData.PlayerData?, edits: Map<String, Int>) {
            disposeWorldMeshes()
            worldDir = dir
            levelData = data
            val terr = TerrainGenerator(
                data.seed,
                registry.index("grass_block"),
                registry.index("dirt_block"),
                registry.index("stone_block"),
                data.flat,
                registry.index("oak_log"),
                registry.index("oak_leaves")
            )
            val w = World(terr)
            w.loadEdits(edits)
            gameWorld = w
            world = w
            renderDistance = data.renderDistance.coerceIn(2, MAX_RENDER_DISTANCE)
            rebuildOffsets()
            val p = spawnPlayer(w, savedPlayer)
            player = p
            spawnPos.set(p.position)
            // Живой мир: населяем деревню жителем
            spawnVillagers(w)
            // Восстанавливаем время суток и счётчик дней из сохранения
            dayCycle = DayNightCycle(data.timeOfDay, if (data.day < 1) 1 else data.day)
            println("[hgdsEngine] Мир «${data.name}» (сид ${data.seed}, " +
                (if (data.flat) "суперплоский" else "обычный") + ", дальность $renderDistance)")
            screen = null
            paused = false
            state = GameState.PLAYING
            win.captureCursor()
        }

        fun createWorld(config: WorldConfig) {
            val loaded = WorldStorage.create(AppDirs.savesDir, config)
            enterWorld(loaded.dir, loaded.data, null, emptyMap())
        }

        fun saveCurrent(showToast: Boolean) {
            val w = gameWorld ?: return
            val dir = worldDir ?: return
            val data = levelData ?: return
            levelData = WorldStorage.save(dir, data, player, w)
            if (showToast) savedToast = 2.5f
        }

        // Показ экранов — через vars, так как экраны ссылаются друг на друга
        // (заголовок → список миров → настройки мира и обратно)
        var showTitleScreen: () -> Unit = { }
        var showSingleplayerScreen: () -> Unit = { }
        var showCreateWorldScreen: () -> Unit = { }
        var showMultiplayerScreen: (String) -> Unit = { }
        /** Экран настроек; реализация присваивается ниже (после resumeGame/pauseGame). */
        var openOptions: (Boolean) -> Unit = { }

        showCreateWorldScreen = {
            win.releaseCursor()
            state = GameState.CREATE_WORLD
            screen = CreateWorldScreen(ui, { config -> createWorld(config) }, { showSingleplayerScreen() }).also { it.init() }
        }

        showSingleplayerScreen = {
            win.releaseCursor()
            state = GameState.SINGLEPLAYER
            screen = SingleplayerScreen(
                ui,
                { summary -> enterWorld(summary.dir, summary.data, summary.data.player, summary.data.edits) },
                { showCreateWorldScreen() },
                { showTitleScreen() }
            ).also { it.init() }
        }

        showTitleScreen = {
            win.releaseCursor()
            state = GameState.TITLE
            paused = false
            screen = TitleScreen(
                ui,
                { showSingleplayerScreen() },
                { showMultiplayerScreen(disconnectNotice.orEmpty()) },
                { openOptions(false) },
                { win.requestClose() }
            ).also { it.init() }
        }

        /** Экран списка серверов; [notice] — причина предыдущего выхода (или ""). */
        showMultiplayerScreen = { notice ->
            connectJob?.cancel()
            connectJob = null
            disconnectNotice = notice.ifEmpty { null }
            state = GameState.MULTIPLAYER
            paused = false
            win.releaseCursor()
            screen = MultiplayerScreen(
                ui,
                nick,
                notice,
                { address, name ->
                    // Рукопожатие блокирует, поэтому оно стартует в фоне (ConnectJob)
                    nick = name
                    connectJob = ConnectJob(address, name)
                    state = GameState.CONNECTING
                    screen = ConnectingScreen(ui, address) { connectJob?.cancel() }.also { it.init() }
                },
                { showTitleScreen() }
            ).also { it.init() }
        }

        /** Мир из ответов сервера: сид + изменения блоков + спавн + другие игроки. */
        fun enterServerWorld(job: ConnectJob) {
            val result = job.login ?: return
            disposeWorldMeshes()
            val terr = TerrainGenerator(
                result.seed,
                registry.index("grass_block"),
                registry.index("dirt_block"),
                registry.index("stone_block"),
                result.flat,
                registry.index("oak_log"),
                registry.index("oak_leaves")
            )
            val w = World(terr)
            w.loadEdits(result.changes.associate { "${it.x},${it.y},${it.z}" to it.blockIndex })
            gameWorld = w
            world = w
            worldDir = null
            levelData = null
            renderDistance = result.renderDistance.coerceIn(2, MAX_RENDER_DISTANCE)
            rebuildOffsets()
            val spawn = result.spawn
            val p = Player(Vector3f(spawn.x, spawn.y, spawn.z), spawn.yaw, spawn.pitch)
            player = p
            spawnPos.set(p.position)
            // Живой мир: жители есть и на сервере (обновляются локально)
            spawnVillagers(w)
            // На сервере время суток начинается заново с утра (сервер пока не рассылает время)
            dayCycle = DayNightCycle()

            val newSession = MultiplayerSession(job.client, nick, job.address, chat)
            newSession.message = result.message
            for (other in result.players) {
                if (other.id == job.client.playerId) continue
                newSession.remotePlayers[other.id] = other
                chat.add("", Lang.format("chat.joined", other.name), ChatKind.JOIN_LEAVE)
            }
            session = newSession
            chat.add("", result.message, ChatKind.SYSTEM)
            connectJob = null
            screen = null
            paused = false
            state = GameState.PLAYING
            win.captureCursor()
            println("[hgdsNET] Вход на сервер ${job.address} игроком $nick")
        }

        /** Выход с сервера: закрываем соединение и возвращаемся к списку серверов. */
        fun leaveServer(reason: String) {
            session?.close()
            session = null
            chat.close()
            connectJob = null
            disposeWorldMeshes()
            villagers.clear()
            gameWorld = null
            world = null
            player = null
            worldDir = null
            levelData = null
            lastHit = null
            showMultiplayerScreen(reason)
        }

        /** Завершает подключение, когда фоновый поток закончил рукопожатие. */
        fun checkConnectResult() {
            val job = connectJob ?: return
            if (state != GameState.CONNECTING) return
            if (job.cancelled) {
                showMultiplayerScreen(Lang.get("multiplayer.cancelled"))
                return
            }
            if (!job.finished) return
            val error = job.error
            when {
                error != null -> showMultiplayerScreen(Lang.format("multiplayer.connect_failed", error))
                else -> enterServerWorld(job)
            }
        }

        fun exitToTitle() {
            if (session != null) leaveServer("") else saveCurrent(false)
            disposeWorldMeshes()
            villagers.clear()
            gameWorld = null
            world = null
            player = null
            worldDir = null
            levelData = null
            lastHit = null
            showTitleScreen()
        }

        fun resumeGame() {
            paused = false
            screen = null
            win.captureCursor()
        }

        fun pauseGame() {
            paused = true
            lastHit = null
            inventoryOpen = false
            win.releaseCursor()
            val net = session
            if (net != null) {
                // В сети сохранение недоступно (мир общий), а «Выход» ведёт на список серверов
                screen = PauseScreen(
                    ui,
                    { resumeGame() },
                    { },
                    { openOptions(true) },
                    { leaveServer(Lang.get("chat.left")) },
                    multiplayer = true
                ).also { it.init() }
            } else {
                screen = PauseScreen(
                    ui,
                    { resumeGame() },
                    { saveCurrent(true) },
                    { openOptions(true) },
                    { exitToTitle() }
                ).also { it.init() }
            }
        }

        /**
         * Экран настроек. [fromPause] — true, если открыты из паузы в мире:
         * по «Готово» возвращаемся в меню паузы, иначе — в главное меню.
         */
        openOptions = { fromPause ->
            win.releaseCursor()
            val optionsScreen = OptionsScreen(
                ui,
                onDone = {
                    if (fromPause) {
                        // Возврат в меню паузы (мир остаётся загруженным и на паузе)
                        val net = session
                        if (net != null) {
                            screen = PauseScreen(
                                ui, { resumeGame() }, { }, { openOptions(true) },
                                { leaveServer(Lang.get("chat.left")) }, multiplayer = true
                            ).also { it.init() }
                        } else {
                            screen = PauseScreen(
                                ui, { resumeGame() }, { saveCurrent(true) }, { openOptions(true) },
                                { exitToTitle() }
                            ).also { it.init() }
                        }
                    } else {
                        showTitleScreen()
                    }
                },
                onChanged = {
                    // Дальность прорисовки применяется к загруженному миру сразу
                    if (gameWorld != null) {
                        renderDistance = GameOptions.renderDistance.coerceIn(2, MAX_RENDER_DISTANCE)
                        rebuildOffsets()
                    }
                },
                darkBackground = fromPause
            ).also { opts ->
                // Навигация по разделам настроек: показать под-экран и вернуться обратно
                // sub.init() обязателен: экран создаётся заново, без него widgets пусты
                // и кнопки появляются только после пересборки при изменении размера окна
                opts.showSub = { sub -> sub.init(); screen = sub }
                opts.onSubClosed = { screen = opts }
                opts.init()
            }
            screen = optionsScreen
        }

        // ----- Обновление мира: ввод, физика, генерация, взаимодействие -----
        fun updateGame(dt: Float) {
            val w = gameWorld ?: return
            val p = player ?: return

            // Время суток идёт только в активной игре (не в паузе — updateGame не вызывается)
            dayCycle.tick(dt)
            // Синхронизируем время с данными уровня: иначе в level.json попадает
            // устаревшее значение и после перезахода день сбрасывается
            levelData?.let {
                it.timeOfDay = dayCycle.timeOfDay
                it.day = dayCycle.day
            }

            // Открытый чат: движение и обзор не реагируют на клавиши, чтобы можно было печатать
            val typing = session != null && chat.isOpen

            // Выбор блока 1..9 и 0 (по нажатию; 0 — десятый слот)
            if (!typing && !inventoryOpen) {
                for (k in 0 until HudRenderer.SLOTS - 1) {
                    if (win.isKeyClicked(GLFW_KEY_1 + k)) selectedSlot = k
                }
                if (win.isKeyClicked(GLFW_KEY_0)) selectedSlot = HudRenderer.SLOTS - 1
            }

            // F — переложить предмет из выбранного слота хотбара в доп. руку (и обратно),
            // работает и при закрытом, и при открытом инвентаре (как в Minecraft).
            // Во время перетаскивания предмета мышью обмен не выполняется.
            if (win.isKeyClicked(GLFW_KEY_F) && !typing && dragSlot < 0) {
                val s = selectedSlot.coerceIn(0, HudRenderer.OFFHAND_SLOT - 1)
                val tmp = hotbar[s]
                hotbar[s] = hotbar[HudRenderer.OFFHAND_SLOT]
                hotbar[HudRenderer.OFFHAND_SLOT] = tmp
            }

            // Инвентарь: E — открыть/закрыть (мышь освобождается для перетаскивания предметов)
            if (win.isKeyClicked(GLFW_KEY_E) && !typing) {
                inventoryOpen = !inventoryOpen
                dragSlot = -1
                if (inventoryOpen) win.releaseCursor() else win.captureCursor()
            }

            // Drag & drop в открытом инвентаре: зажал на слоте с предметом — предмет
            // за курсором, отпустил на другом слоте — обмен местами.
            // Клик по слоту НЕ выбирает его (как в Minecraft: выбор хотбара
            // в инвентаре не выполняется — только клавишами 1..0 вне инвентаря).
            if (inventoryOpen) {
                val mx = ui.mouseX.toFloat()
                val my = ui.mouseY.toFloat()
                val bottomBarX = ui.guiWidth / 2f - HudRenderer.HOTBAR_W / 2f
                val bottomBarY = ui.guiHeight - HudRenderer.HOTBAR_H - 1f
                val panelX = ui.guiWidth / 2f - HudRenderer.INV_W / 2f
                val panelY = ui.guiHeight / 2f - HudRenderer.INV_H / 2f
                fun slotUnderCursor(): Int {
                    // Нижний HUD-бар (0..9) и слот доп. руки (10)
                    val s = HudRenderer.slotAt(bottomBarX, bottomBarY, mx, my)
                    if (s >= 0) return s
                    val ox = bottomBarX + 186f
                    if (mx >= ox && mx < ox + 18f && my >= bottomBarY && my < bottomBarY + 18f) return HudRenderer.OFFHAND_SLOT
                    // Панель инвентаря
                    return HudRenderer.panelSlotAt(panelX, panelY, mx, my)
                }
                if (win.isMouseClicked(GLFW_MOUSE_BUTTON_1)) {
                    val slot = slotUnderCursor()
                    // Начали перетаскивание — только если в слоте есть предмет
                    if (slot >= 0 && dragSlot < 0 && hotbar[slot] != null) dragSlot = slot
                }
                if (dragSlot >= 0 && !win.isMouseButtonDown(GLFW_MOUSE_BUTTON_1)) {
                    val drop = slotUnderCursor()
                    if (drop >= 0 && drop != dragSlot) {
                        val tmp = hotbar[dragSlot]
                        hotbar[dragSlot] = hotbar[drop]
                        hotbar[drop] = tmp
                    }
                    dragSlot = -1
                }
            } else if (dragSlot >= 0) {
                dragSlot = -1
            }

            // Отладка: F3 — информационный оверлей, F3+G — границы текущего чанка
            // (оба — по нажатию, не удержанию)
            val f3Down = win.isKeyPressed(GLFW_KEY_F3)
            if (f3Down && !wasF3Down) showDebugInfo = !showDebugInfo
            if (f3Down && win.isKeyClicked(GLFW_KEY_G)) showChunkBorders = !showChunkBorders
            if (win.isKeyClicked(GLFW_KEY_F5)) cameraMode = (cameraMode + 1) % 3
            wasF3Down = f3Down

            // Живая подгонка руки от первого лица (только F3 + клавиши, камера 0):
            // +/− — масштаб, I/K — вверх/вниз, J/L — влево/вправо, U/O — дальше/ближе.
            // Значения печатаются в консоль и показываются в F3-оверлее.
            if (f3Down && cameraMode == 0 && !chat.isOpen && !inventoryOpen) {
                playerRenderer?.let { pr ->
                    var changed = false
                    if (win.isKeyClicked(GLFW_KEY_EQUAL) || win.isKeyClicked(GLFW_KEY_KP_ADD)) {
                        pr.handScale += 0.02f; changed = true
                    }
                    if (win.isKeyClicked(GLFW_KEY_MINUS) || win.isKeyClicked(GLFW_KEY_KP_SUBTRACT)) {
                        pr.handScale -= 0.02f; changed = true
                    }
                    if (win.isKeyClicked(GLFW_KEY_I)) { pr.handOffset.y += 0.01f; changed = true }
                    if (win.isKeyClicked(GLFW_KEY_K)) { pr.handOffset.y -= 0.01f; changed = true }
                    if (win.isKeyClicked(GLFW_KEY_J)) { pr.handOffset.x -= 0.01f; changed = true }
                    if (win.isKeyClicked(GLFW_KEY_L)) { pr.handOffset.x += 0.01f; changed = true }
                    if (win.isKeyClicked(GLFW_KEY_O)) { pr.handOffset.z += 0.05f; changed = true }
                    if (win.isKeyClicked(GLFW_KEY_U)) { pr.handOffset.z -= 0.05f; changed = true }
                    if (changed) println(
                        "[hand] scale=${"%.2f".format(java.util.Locale.ROOT, pr.handScale)} " +
                            "offset=${"%.2f; %.2f; %.2f".format(
                                java.util.Locale.ROOT, pr.handOffset.x, pr.handOffset.y, pr.handOffset.z
                            )}"
                    )
                }
            }

            // Движение
            var moveForward = 0f
            var moveStrafe = 0f
            if (!typing) {
                if (win.isKeyPressed(GLFW_KEY_W)) moveForward += 1f
                if (win.isKeyPressed(GLFW_KEY_S)) moveForward -= 1f
                if (win.isKeyPressed(GLFW_KEY_D)) moveStrafe += 1f
                if (win.isKeyPressed(GLFW_KEY_A)) moveStrafe -= 1f
            }

            // Спринт (W+Ctrl) и присед (Shift) — как в Minecraft
            val shiftDown = !typing && win.isKeyPressed(GLFW_KEY_LEFT_SHIFT)
            val ctrlDown = !typing && win.isKeyPressed(GLFW_KEY_LEFT_CONTROL)
            p.sneaking = shiftDown
            // Спринт как в Minecraft: включается Ctrl + вперёд и держится, пока идёшь вперёд.
            // Назад или присед — мгновенно выключается (бег спиной невозможен).
            if (ctrlDown && moveForward > 0f && !shiftDown) p.sprinting = true
            if (moveForward <= 0f || shiftDown) p.sprinting = false

            // Обзор мышью (мышь захвачена)
            val (mdx, mdy) = win.consumeMouseDelta()
            if (!typing) {
                val sensitivity = 0.1f * GameOptions.sensitivity / 100f
                p.yaw += mdx.toFloat() * sensitivity
                // Нормализуем yaw в диапазон -180..180, чтобы не рос бесконечно
                p.yaw = ((p.yaw + 180f) % 360f + 360f) % 360f - 180f
                p.pitch = (p.pitch - mdy.toFloat() * sensitivity).coerceIn(-89.9f, 89.9f)
            }

            p.update(dt, moveForward, moveStrafe, !typing && win.isKeyPressed(GLFW_KEY_SPACE), w)

            // Не выпускаем игрока за границу мира; страховка от падения в пустоту
            val half = Player.WIDTH / 2f + 0.05f
            val max = World.WORLD_BORDER - half
            p.position.x = p.position.x.coerceIn(-max, max)
            p.position.z = p.position.z.coerceIn(-max, max)
            if (p.position.y < -20f) {
                p.position.set(spawnPos)
                println("[hgdsEngine] Возврат на спавн")
            }

            // Смерть (здоровье кончилось) — респавн на спавне с полным здоровьем
            if (p.health <= 0f) {
                p.position.set(spawnPos)
                p.respawn()
                println("[hgdsEngine] Респавн игрока")
            }

            // Живой мир: обновляем жителей (ИИ + физика)
            for (v in villagers) v.tick(dt, w, p)

            // Колонка чанка, в которой стоит игрок
            val pcx = kotlin.math.floor(p.position.x).toInt().floorDiv(World.CHUNK_SIZE_X)
            val pcz = kotlin.math.floor(p.position.z).toInt().floorDiv(World.CHUNK_SIZE_Z)

            // Ленивая генерация колонок вокруг игрока (бюджет времени на кадр,
            // чтобы генерация не «съедала» кадр целиком)
            var genBudget = COLUMNS_GENERATE_PER_FRAME
            val genDeadline = System.nanoTime() + CHUNK_WORK_BUDGET_NS
            for (offset in offsets) {
                if (genBudget <= 0) break
                if (System.nanoTime() > genDeadline) break
                val cx = pcx + offset[0]
                val cz = pcz + offset[1]
                if (w.columnAt(cx, cz)?.generated == true) continue
                if (w.ensureColumn(cx, cz) != null) genBudget--
            }

            // Рейкаст из глаз игрока
            val eyeY = p.eyeY
            val yawRad = Math.toRadians(p.yaw.toDouble())
            val pitchRad = Math.toRadians(p.pitch.toDouble())
            val dirX = (kotlin.math.sin(yawRad) * kotlin.math.cos(pitchRad)).toFloat()
            val dirY = kotlin.math.sin(pitchRad).toFloat()
            val dirZ = (-kotlin.math.cos(yawRad) * kotlin.math.cos(pitchRad)).toFloat()
            lastHit = Raycaster.cast(w, p.position.x, eyeY, p.position.z, dirX, dirY, dirZ, REACH_DISTANCE)

            // Ломать / ставить блоки (изменения игрока запоминаются для сохранения)
            val hit = lastHit
            if (hit != null && !typing && !inventoryOpen) {
                if (win.isMouseClicked(GLFW_MOUSE_BUTTON_1)) {
                    val x = hit.block.x
                    val y = hit.block.y
                    val z = hit.block.z
                    w.setBlockByPlayer(x, y, z, BlockRegistry.AIR)
                    // На сервере изменение применяется сам и приходит обратно всем игрокам
                    session?.sendBlockChange(x, y, z, BlockRegistry.AIR)
                } else if (win.isMouseClicked(GLFW_MOUSE_BUTTON_2)) {
                    val blockId = hotbar[selectedSlot]
                    if (blockId != null) {
                        val placed = placeBlock(p, w, registry, blockId, hit)
                        if (placed != null) session?.sendBlockChange(placed)
                    }
                }
            }

            // Мешинг «грязных» секций, ближайшие сначала (бюджет времени на кадр).
            // Секция мешится только когда соседние колонки уже сгенерированы —
            // иначе мешер сам запустит генерацию посреди кадра (фриз).
            val toMesh = ArrayList<Pair<Section, Int>>()
            val meshDeadline = System.nanoTime() + CHUNK_WORK_BUDGET_NS
            for (offset in offsets) {
                val column = w.columnAt(pcx + offset[0], pcz + offset[1]) ?: continue
                for (section in column.sections) {
                    val s = section ?: continue
                    if (!s.dirty || s.isEmpty) continue
                    if (!neighborsGenerated(w, column)) continue
                    toMesh.add(s to offset[0] * offset[0] + offset[1] * offset[1])
                }
            }
            toMesh.sortBy { it.second }
            var meshBudget = SECTIONS_MESH_PER_FRAME
            for ((section, _) in toMesh) {
                if (meshBudget <= 0) break
                if (System.nanoTime() > meshDeadline) break
                section.mesh?.dispose()
                section.mesh = ChunkMeshFactory.build(section, w, registry, atlasData)
                section.dirty = false
                meshBudget--
            }

            // Выгрузка мешей далёких чанков (память + быстрый рендер)
            for (column in w.allColumns()) {
                val dist = maxOf(kotlin.math.abs(column.cx - pcx), kotlin.math.abs(column.cz - pcz))
                if (dist > renderDistance + 2) {
                    for (section in column.sections) {
                        section?.mesh?.dispose()
                        section?.mesh = null
                        section?.dirty = true
                    }
                }
            }

            // Сеть: своя позиция + пинг, затем обработка пакетов сервера
            val net = session
            if (net != null) {
                net.client.keepAlive()
                net.sendPosition(p.position.x, p.position.y, p.position.z, p.yaw, p.pitch)
                if (net.process(w)) leaveServer(net.disconnectReason ?: Lang.get("chat.disconnected"))
            }
        }

        /**
         * Frustum culling: видна ли секция в пирамиде видимости.
         * Тест по 6 плоскостям через «положительную вершину» AABB.
         */
        fun sectionVisible(planes: Array<Vector4f>, s: Section): Boolean {
            val x0 = s.originX.toFloat(); val x1 = x0 + World.SECTION_SIZE
            val y0 = s.originY.toFloat(); val y1 = y0 + World.SECTION_SIZE
            val z0 = s.originZ.toFloat(); val z1 = z0 + World.SECTION_SIZE
            for (p in planes) {
                val px = if (p.x >= 0f) x1 else x0
                val py = if (p.y >= 0f) y1 else y0
                val pz = if (p.z >= 0f) z1 else z0
                if (p.x * px + p.y * py + p.z * pz + p.w < 0f) return false
            }
            return true
        }

        // ----- Рендер мира -----
        fun renderWorld(update: Boolean, dt: Float) {
            val w = gameWorld ?: return
            val p = player ?: return
            val pcx = kotlin.math.floor(p.position.x).toInt().floorDiv(World.CHUNK_SIZE_X)
            val pcz = kotlin.math.floor(p.position.z).toInt().floorDiv(World.CHUNK_SIZE_Z)

            // Камера: от глаз (0), третье лицо за спиной (1), третье лицо спереди — в лицо (2)
            val yawRad = Math.toRadians(p.yaw.toDouble()).toFloat()
            val pitchRad = Math.toRadians(p.pitch.toDouble()).toFloat()
            viewMatrix.identity()
                .rotateX(if (cameraMode == 2) pitchRad else -pitchRad) // 2 — камера смотрит в лицо (обратный pitch)
                .rotateY(if (cameraMode == 2) yawRad + Math.PI.toFloat() else yawRad)
            if (cameraMode != 0) {
                // Позиция камеры: глаза игрока ± направление взгляда * дистанция,
                // поэтому камера всегда точно на линии взгляда и вращается вместе с ним
                val dist = 4f
                val fx = kotlin.math.sin(yawRad) * kotlin.math.cos(pitchRad)
                val fy = kotlin.math.sin(pitchRad)
                val fz = -kotlin.math.cos(yawRad) * kotlin.math.cos(pitchRad)
                val sign = if (cameraMode == 2) 1f else -1f // 2 — камера перед лицом
                // Коллизия камеры: луч от глаз к камере, чтобы не залезать под блоки
                val camHit = Raycaster.cast(w, p.position.x, p.eyeY, p.position.z, sign * fx, sign * fy, sign * fz, dist)
                val camDist = if (camHit != null) (camHit.distance - 0.2f).coerceIn(0.5f, dist) else dist
                viewMatrix.translate(
                    -(p.position.x + sign * fx * camDist),
                    -(p.eyeY + sign * fy * camDist),
                    -(p.position.z + sign * fz * camDist)
                )
            } else {
                viewMatrix.translate(-p.position.x, -p.eyeY, -p.position.z)
            }
            projMatrix.identity()
                .perspective(Math.toRadians(GameOptions.fov.toDouble()).toFloat(), win.aspect(), 0.1f, 1000f)

            // Цвет неба зависит от времени суток (рассвет/день/закат/ночь)
            val skyCol = dayCycle.skyColor(skyColorBuf)
            glClearColor(skyCol[0], skyCol[1], skyCol[2], 1f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

            // Блоки (с frustum culling: рисуем только секции в пирамиде видимости)
            blockProg.use()
            blockProg.setMatrix4("uProjection", projMatrix)
            blockProg.setMatrix4("uView", viewMatrix)
            // Яркость мира: настройки игрока, умноженные на освещение дня/ночи
            blockProg.setFloat("uBrightness", GameOptions.brightnessFactor * dayCycle.lightLevel())

            // Солнце и луна: 3D-квады далеко в мире (блоки их перекрывают по глубине)
            dayCycle.sunDirection(sunDirBuf)
            celestial.render(sky, p.position, viewMatrix, sunDirBuf)

            atl.bind(0)
            val vp = Matrix4f(projMatrix).mul(viewMatrix)
            val planes = Array(6) { Vector4f() }
            for (i in 0 until 6) vp.frustumPlane(i, planes[i])
            for (offset in offsets) {
                val column = w.columnAt(pcx + offset[0], pcz + offset[1]) ?: continue
                for (section in column.sections) {
                    val mesh = section?.mesh ?: continue
                    if (!sectionVisible(planes, section)) continue
                    mesh.draw()
                }
            }

            // Игроки: модель со скином и анимацией ходьбы (F5 — и свой игрок)
            val pr = playerRenderer
            if (pr != null) {
                val list = ArrayList<PlayerRenderer.Instance>(4)
                if (cameraMode != 0) {
                    // Свой игрок: модель повёрнута зеркально к yaw камеры (выведено из матрицы вида)
                    list.add(PlayerRenderer.Instance(
                        -1, p.position.x, p.position.y, p.position.z,
                        -Math.toRadians(p.yaw.toDouble()).toFloat(),
                        Math.toRadians(p.pitch.toDouble()).toFloat(),
                        useGivenYaw = true,
                        sneaking = p.sneaking
                    ))
                }
                val netNow = session
                if (netNow != null) {
                    for (remote in netNow.remotePlayers.values) {
                        list.add(PlayerRenderer.Instance(
                            remote.id, remote.position.x, remote.position.y, remote.position.z,
                            0f, 0f, useGivenYaw = false
                        ))
                    }
                }
                if (list.isNotEmpty()) pr.render(blockProg, dt, list)
                atl.bind(0) // возвращаем атлас блоков

                // Жители: модель игрока с текстурой жителя, анимация по движению
                val vr = villagerRenderer
                if (vr != null && villagers.isNotEmpty()) {
                    val vlist = ArrayList<PlayerRenderer.Instance>(villagers.size)
                    for ((i, v) in villagers.withIndex()) {
                        vlist.add(PlayerRenderer.Instance(
                            10_000 + i, v.position.x, v.position.y, v.position.z,
                            -Math.toRadians(v.yaw.toDouble()).toFloat(),
                            0f,
                            useGivenYaw = false
                        ))
                    }
                    vr.render(blockProg, dt, vlist)
                    atl.bind(0) // возвращаем атлас блоков
                }

                // Рука от первого лица (камера 0): поверх мира, без проверки глубины
                if (cameraMode == 0) {
                    glClear(GL_DEPTH_BUFFER_BIT)
                    blockProg.use()
                    blockProg.setMatrix4("uView", identityMatrix)
                    // Как в Minecraft (GameRenderer.renderItemInHand): рука рисуется
                    // отдельным проходом с жёстким FOV 70, независимо от FOV мира.
                    handProjMatrix.identity()
                        .perspective(Math.toRadians(70.0).toFloat(), win.aspect(), 0.1f, 1000f)
                    blockProg.setMatrix4("uProjection", handProjMatrix)
                    // Предмет в руке: блок из выбранного слота хотбара
                    val held = hotbar[selectedSlot]?.let { id ->
                        val def = registry[id]
                        val uvs = Array(6) { f ->
                            val bf = def.face(hgds.IloveGroup.world.Face.entries[f])
                            if (bf == null) floatArrayOf(0f, 0f, 0f, 0f)
                            else {
                                val rect = atlasData.uvRect(bf.texture)
                                floatArrayOf(rect.u0, rect.v0, rect.u1, rect.v1)
                            }
                        }
                        PlayerRenderer.HeldItem(registry.idToIndex[id] ?: -1, uvs)
                    }
                    pr.renderHand(
                        blockProg, dt,
                        // «typing» в update — локальная переменная; здесь то же условие по полям класса
                        (session == null || !chat.isOpen) && !inventoryOpen &&
                            win.isMouseButtonDown(GLFW_MOUSE_BUTTON_1),
                        // Использование: ПКМ удерживается (та же защита чата/инвентаря)
                        (session == null || !chat.isOpen) && !inventoryOpen &&
                            win.isMouseButtonDown(GLFW_MOUSE_BUTTON_2),
                        held,
                        // Позиция игрока — для покачивания руки при ходьбе (bobbing)
                        p.position.x, p.position.z
                    )
                    atl.bind(0)
                }
            }

            // Линии: границы чанков + обводка блока под прицелом + прицел
            val hit = if (update) lastHit else null
            val net = session
            if (showChunkBorders || hit != null || net != null) {
                lineProg.use()
                lineProg.setMatrix4("uProjection", projMatrix)
                lineProg.setMatrix4("uView", viewMatrix)
                glEnable(GL_BLEND)
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

                val data = ArrayList<Float>(8192)
                if (showChunkBorders) {
                    // Только чанк, в котором стоит игрок
                    w.columnAt(pcx, pcz)?.let { addColumnBorder(data, it, 0f, 1f, 0f, 0.85f) }
                }
                if (hit != null) {
                    addBlockOutline(data, hit.block.x, hit.block.y, hit.block.z)
                }
                // Другие игроки рисуются моделью (PlayerRenderer), каркас больше не нужен
                lines.upload(data.toFloatArray())
                lines.draw(data.size / LineRenderer.FLOATS_PER_LINE)

                // Прицел в центре экрана (в NDC, поверх всего) — единственный без глубины
                glDisable(GL_DEPTH_TEST)
                lineProg.setMatrix4("uProjection", identityMatrix)
                lineProg.setMatrix4("uView", identityMatrix)
                lines.upload(CROSSHAIR)
                lines.draw(2)

                glEnable(GL_DEPTH_TEST)
                glDisable(GL_BLEND)
            }

            // Матрица для подписей игроков поверх мира
            hudViewProj.set(projMatrix).mul(viewMatrix)
        }

        /** Иконка блока из 3D-атласа в GUI (верхняя грань блока). */
        fun drawBlockIcon(gui: GuiRenderer, slot: Int, cx: Float, cy: Float, size: Float = 16f) {
            val id = hotbar.getOrNull(slot) ?: return
            val def = registry[id]
            val face = def.face(hgds.IloveGroup.world.Face.TOP) ?: return
            val rect = atlasData.uvRect(face.texture)
            // uvRect нормализован — переводим в пиксели атласа для blitRaw
            gui.blitRaw(
                atl.glTextureId, atl.width, atl.height,
                cx - size / 2f, cy - size / 2f, size, size,
                rect.u0 * atl.width, rect.v0 * atl.height,
                rect.u1 * atl.width, rect.v1 * atl.height
            )
        }

        /** Игровой HUD: сердца, броня, хотбар и панель инвентаря (как в Minecraft). */
        fun drawHud() {
            val p = player ?: return
            val gw = ui.guiWidth.toFloat()
            val gh = ui.guiHeight.toFloat()
            val barX = gw / 2f - HudRenderer.HOTBAR_W / 2f
            val barY = gh - HudRenderer.HOTBAR_H - 1f
            val statY = barY - HudRenderer.ICON_SIZE - 1f

            // Сердца слева над хотбаром, броня справа (как в Minecraft)
            hud.drawHearts(gui, barX, statY, p.health)
            hud.drawArmor(gui, barX + HudRenderer.HOTBAR_W, statY, p.armor)

            hud.drawHotbarBar(gui, barX, barY)
            // Ячейки бара 18×18: граница на barX + i*18, иконка заполняет клетку целиком
            // Рамка выбора рисуется ПОД иконкой, чтобы не закрашивать предмет
            hud.drawSlotHighlight(gui, barX + selectedSlot * 18f, barY, 18f)
            for (i in 0 until HudRenderer.SLOTS) {
                if (inventoryOpen && dragSlot == i) continue // предмет «в руке»
                drawBlockIcon(gui, i, barX + 9f + i * 18f, barY + 9f, 18f)
            }

            // Слот дополнительной руки справа от бара (зазор 7 px, как в спрайте)
            val offX = barX + 186f
            hud.drawOffhandSlot(gui, offX, barY)
            if (!(inventoryOpen && dragSlot == HudRenderer.OFFHAND_SLOT)) {
                drawBlockIcon(gui, HudRenderer.OFFHAND_SLOT, offX + 9f, barY + 9f, 18f)
            }

            // Название выбранного блока над статистикой
            val name = hotbar[selectedSlot]?.let { registry[it].displayName } ?: ""
            if (name.isNotEmpty()) {
                font.draw(gui, name, gw / 2f - font.width(name) / 2f, statY - 12f, 1f, 0xFFFFFF)
            }

            // Панель инвентаря (E): предметы рисуются в ячейках арта, drag & drop
            if (inventoryOpen) {
                val px = gw / 2f - HudRenderer.INV_W / 2f
                val py = gh / 2f - HudRenderer.INV_H / 2f
                hud.drawInventoryPanel(gui, px, py)
                fun cellTopLeft(index: Int): Pair<Float, Float>? = when {
                    // Нижний ряд панели — хотбар 0..9
                    index < HudRenderer.SLOTS ->
                        px + HudRenderer.PANEL_GRID_X + index * HudRenderer.PANEL_CELL to py + HudRenderer.PANEL_HOTBAR_ROW_Y
                    // Доп. рука (10) — ячейка справа от нижнего ряда
                    index == HudRenderer.OFFHAND_SLOT ->
                        px + HudRenderer.PANEL_OFFHAND_X to py + HudRenderer.PANEL_HOTBAR_ROW_Y
                    // Основная сетка 11..40: 30 ячеек (3 ряда × 10 колонок)
                    else -> {
                        val k = index - HudRenderer.OFFHAND_SLOT - 1
                        px + HudRenderer.PANEL_GRID_X + (k % HudRenderer.PANEL_COLS) * HudRenderer.PANEL_CELL to
                            py + HudRenderer.PANEL_MAIN_Y + (k / HudRenderer.PANEL_COLS) * HudRenderer.PANEL_CELL
                    }
                }
                for (i in hotbar.indices) {
                    if (hotbar[i] == null) continue
                    if (dragSlot == i) continue // предмет «в руке»
                    val (cx, cy) = cellTopLeft(i) ?: continue
                    drawBlockIcon(gui, i, cx + HudRenderer.PANEL_CELL / 2f, cy + HudRenderer.PANEL_CELL / 2f)
                }
                // Подсветка выбранного слота хотбара внутри панели
                if (selectedSlot < HudRenderer.SLOTS) {
                    cellTopLeft(selectedSlot)?.let { (sx, sy) ->
                        hud.drawSlotHighlight(gui, sx + 1f, sy + 1f, 16f)
                    }
                }
                // Предмет, который перетаскивают, следует за курсором
                if (dragSlot >= 0) {
                    drawBlockIcon(gui, dragSlot, ui.mouseX.toFloat(), ui.mouseY.toFloat(), 18f)
                }
            }
        }

        /** Сообщение «Мир сохранён!» вверху экрана. */
        fun drawSavedToast() {
            if (savedToast <= 0f) return
            val msg = Lang.get("misc.world_saved")
            font.draw(gui, msg, ui.guiWidth / 2f - font.width(msg) / 2f, 12f, 1f, 0xFFFF55)
        }

        /**
         * Отладочный оверлей F3 (как в Minecraft): версия, FPS, координаты,
         * поворот камеры, чанк, память. Рисуется в левом верхнем углу.
         */
        fun drawDebugInfo() {
            if (!showDebugInfo) return
            val w = gameWorld ?: return
            val p = player ?: return

            val yaw = ((p.yaw % 360f) + 360f) % 360f
            val facing = when {
                yaw in 315f..360f || yaw in 0f..45f -> "север (-Z)"
                yaw in 45f..135f -> "восток (+X)"
                yaw in 135f..225f -> "юг (+Z)"
                else -> "запад (-X)"
            }
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val totalMb = rt.totalMemory() / (1024 * 1024)
            val maxMb = rt.maxMemory() / (1024 * 1024)
            val cx = kotlin.math.floor(p.position.x).toInt().floorDiv(World.CHUNK_SIZE_X)
            val cz = kotlin.math.floor(p.position.z).toInt().floorDiv(World.CHUNK_SIZE_Z)
            val columns = w.allColumns().size

            val lines = listOf(
                "${GameInfo.DISPLAY_NAME} ($fps fps)",
                "XYZ: %.3f / %.3f / %.3f".format(p.position.x, p.position.y, p.position.z),
                "Блок: ${kotlin.math.floor(p.position.x).toInt()} " +
                    "${kotlin.math.floor(p.position.y).toInt()} ${kotlin.math.floor(p.position.z).toInt()}",
                "Чанк: $cx $cz (в чанке: " +
                    "${kotlin.math.floor(p.position.x).toInt().mod(World.CHUNK_SIZE_X)} " +
                    "${kotlin.math.floor(p.position.z).toInt().mod(World.CHUNK_SIZE_Z)})",
                "Взгляд: yaw %.1f pitch %.1f ($facing)".format(p.yaw, p.pitch),
                "День ${dayCycle.day}, время ${dayCycle.clockText()}",
                "Память: $usedMb МБ / $totalMb МБ (макс. $maxMb МБ)",
                "Колонок чанков: $columns, дальность: $renderDistance",
                "Сид мира: ${w.terrainSeed}",
                villagers.firstOrNull()?.let { v ->
                    val dist = kotlin.math.hypot(v.position.x - v.homeX, v.position.z - v.homeZ)
                    "Житель: %.1f / %.1f / %.1f (%s, до дома %.0f)".format(
                        v.position.x, v.position.y, v.position.z, v.state, dist
                    )
                } ?: "Житель: нет",
                playerRenderer?.let { pr ->
                    "Рука: scale %.2f, offset %.2f / %.2f / %.2f".format(
                        java.util.Locale.ROOT, pr.handScale, pr.handOffset.x, pr.handOffset.y, pr.handOffset.z
                    )
                } ?: "Рука: нет",
                "F3 — этот оверлей, F3+G — границы чанка, " +
                    "F3+I/K/J/L, U/O, +/− — подгонка руки"
            )
            var y = 2f
            for (text in lines) {
                font.draw(gui, text, 2f, y, 1f, 0xFFFFFF, shadow = true)
                y += 10f
            }
        }

        /**
         * Ввод чата в сетевом режиме: T — открыть, Enter — отправить,
         * Esc — закрыть, Backspace — стереть символ, остальные символы — в строку.
         */
        fun handleChatInput(chars: List<Int>) {
            val net = session ?: return
            if (!chat.isOpen) {
                if (win.isKeyClicked(GLFW_KEY_T)) {
                    chat.open()
                    win.releaseCursor()
                }
                return
            }
            if (win.isKeyClicked(GLFW_KEY_ESCAPE)) {
                chat.close()
                win.captureCursor()
                return
            }
            for (c in chars) chat.typeChar(c)
            if (win.isKeyClicked(GLFW_KEY_BACKSPACE)) chat.backspace()
            if (win.isKeyClicked(GLFW_KEY_ENTER) || win.isKeyClicked(GLFW_KEY_KP_ENTER)) {
                val message = chat.submit()
                if (message.isNotEmpty()) net.sendChat(message)
                win.captureCursor()
            }
        }

        /** Имена других игроков над их каркасами (проекция позиции на экран). */
        fun drawPlayerTags() {
            val net = session ?: return
            for (remote in net.remotePlayers.values) {
                hudClip.set(remote.position.x, remote.position.y + Player.HEIGHT + 0.35f, remote.position.z, 1f)
                hudViewProj.transform(hudClip)
                if (hudClip.w <= 0.001f) continue // игрок позади камеры
                val sx = (ui.guiWidth / 2f) * (1f + hudClip.x / hudClip.w)
                val sy = (ui.guiHeight / 2f) * (1f - hudClip.y / hudClip.w)
                if (sx < -60f || sx > ui.guiWidth + 60f || sy < -10f || sy > ui.guiHeight) continue
                val name = remote.name
                font.draw(gui, name, sx - font.width(name) / 2f, sy, 1f, 0xBFE6FF)
            }
        }

        /**
         * Солнце и луна рисуются в 3D-мире (см. renderWorld и CelestialRenderer) —
         * блоки корректно перекрывают их по глубине.
         */

        // Для автоматических проверок: VOXEL_AUTO_EXIT_SECONDS=N закроет игру через N секунд
        val autoExitSeconds = System.getenv("VOXEL_AUTO_EXIT_SECONDS")?.toDoubleOrNull()
        val startTime = glfwGetTime()
        var lastTime = startTime

        // Размеры GUI на прошлом кадре — для отслеживания изменения размера окна
        var lastGuiWidth = ui.guiWidth
        var lastGuiHeight = ui.guiHeight

        showTitleScreen()
        println("Управление: WASD + мышь — движение, Space — прыжок, ЛКМ — сломать, ПКМ — поставить")
        println("            1/2/3 — выбор блока, F3 — границы чанков, Esc — пауза (Сохранить / Выйти)")

        while (!win.shouldClose()) {
            val frameStartNs = System.nanoTime()
            val now = glfwGetTime()
            if (autoExitSeconds != null && now - startTime >= autoExitSeconds) {
                if (state == GameState.PLAYING) saveCurrent(false)
                win.requestClose()
            }
            val dt = (now - lastTime).coerceIn(0.0, 0.1).toFloat()
            lastTime = now
            fpsFrames++
            fpsTimer += dt
            if (fpsTimer >= 1f) { fps = fpsFrames; fpsFrames = 0; fpsTimer = 0f }

            // При изменении размера окна (разворачивание/полноэкранный режим)
            // размеры GUI пересчитываются — пересобираем виджеты текущего экрана,
            // чтобы кнопки не «уезжали» со старых координат
            if (screen != null && (ui.guiWidth != lastGuiWidth || ui.guiHeight != lastGuiHeight)) {
                lastGuiWidth = ui.guiWidth
                lastGuiHeight = ui.guiHeight
                screen?.init()
            }

            win.pollEvents()

            // Защита курсора: он должен быть виден в любом меню (пауза, чат, экраны)
            // и захвачен только в активном мире. Исправляет «пропавшую» мышь,
            // если какой-то переход состояния забыл вызвать release/captureCursor.
            val shouldCaptureCursor = state == GameState.PLAYING && !paused && !chat.isOpen && !inventoryOpen
            if (win.isCursorCaptured != shouldCaptureCursor) {
                if (shouldCaptureCursor) win.captureCursor() else win.releaseCursor()
            }

            if (savedToast > 0f) savedToast -= dt

            val typedChars = win.consumeTypedChars()
            val scroll = win.consumeScroll()
            ui.mouseX = (win.cursorX / ui.guiScale).toInt().coerceIn(0, ui.guiWidth - 1)
            ui.mouseY = (win.cursorY / ui.guiScale).toInt().coerceIn(0, ui.guiHeight - 1)

            if (state == GameState.PLAYING) {
                if (!paused && win.isKeyClicked(GLFW_KEY_ESCAPE)) pauseGame()
                if (paused) {
                    screen?.tick(dt)
                    for (button in 0..2) {
                        if (win.isMouseClicked(button)) screen?.mouseClicked(ui.mouseX, ui.mouseY, button)
                    }
                } else {
                    handleChatInput(typedChars)
                    updateGame(dt)
                }
                // Мир рисуется и в паузе (застывший кадр за полупрозрачным меню)
                renderWorld(update = !paused, dt = dt)
                gui.begin(ui.guiWidth.toFloat(), ui.guiHeight.toFloat())
                if (paused) {
                    screen?.render()
                } else {
                    drawPlayerTags()
                    drawHud()
                }
                chat.tick(dt)
                chat.render(ui)
                drawSavedToast()
                drawDebugInfo()
                gui.end()
            } else {
                // В ожидании подключения ждём, пока фоновый поток закончит рукопожатие
                checkConnectResult()
                val s = screen
                if (s != null) {
                    if (win.isKeyClicked(GLFW_KEY_ESCAPE)) s.onEscape()
                    s.tick(dt)
                    for (c in typedChars) s.charTyped(c)
                    if (scroll != 0.0) s.onScroll(scroll)
                    for (button in 0..2) {
                        if (win.isMouseClicked(button)) s.mouseClicked(ui.mouseX, ui.mouseY, button)
                    }
                    gui.begin(ui.guiWidth.toFloat(), ui.guiHeight.toFloat())
                    s.render()
                    gui.end()
                }
            }

            win.consumeMouseClicks()
            win.endFrame()
            win.swapBuffers()

            // Ограничение FPS:
            //  - «Свернуть» — в свёрнутом окне 10 FPS;
            //  - «Простое» — после 20 минут бездействия 30 FPS (экономия ресурсов).
            val idleSeconds = now - win.lastInputTime
            val targetFps = when {
                GameOptions.fpsLimit == 2 && win.isIconified() -> 10
                GameOptions.fpsLimit >= 1 && idleSeconds > 20.0 * 60.0 -> 30
                else -> 0
            }
            if (targetFps > 0) {
                val minFrameNs = 1_000_000_000L / targetFps
                val elapsedNs = System.nanoTime() - frameStartNs
                if (elapsedNs < minFrameNs) {
                    Thread.sleep((minFrameNs - elapsedNs) / 1_000_000L)
                }
            }
        }

        // Если окно закрыли крестиком во время игры — сохраняем мир
        if (state == GameState.PLAYING) saveCurrent(false)
        // Сохраняем настройки (могли меняться, а «Готово» не нажималось)
        GameOptions.save()
        // Соединение с сервером закрываем в любом случае
        session?.close()
        session = null
        connectJob?.cancel()
    } finally {
        // Освобождение всех мешей секций и ресурсов
        world?.allColumns()?.forEach { column ->
            column.sections.forEach { section -> section?.mesh?.dispose() }
        }
        lineRenderer?.dispose()
        guiRenderer?.dispose()
        uiTextures?.dispose()
        skyRenderer?.dispose()
        celestialRenderer?.dispose()
        playerRenderer?.dispose()
        villagerRenderer?.dispose()
        fontRenderer?.dispose()
        blockShader?.dispose()
        lineShader?.dispose()
        atlas?.dispose()
        window?.destroy()
    }
}

/** Прицел в центре экрана (NDC): горизонталь + вертикаль. */
private val CROSSHAIR = floatArrayOf(
    -0.025f, 0f, 0f, 1f, 1f, 1f, 1f, 0.025f, 0f, 0f, 1f, 1f, 1f, 1f,
    0f, -0.025f, 0f, 1f, 1f, 1f, 1f, 0f, 0.025f, 0f, 1f, 1f, 1f, 1f
)

/** Ставит блок на грань попадания, если он не пересекается с игроком. Возвращает изменение для сервера. */
fun placeBlock(
    player: Player,
    world: World,
    registry: BlockRegistry,
    blockId: String,
    hit: hgds.IloveGroup.world.RayHit
): BlockChangeEntry? {
    val px = hit.block.x + hit.normal.x
    val py = hit.block.y + hit.normal.y
    val pz = hit.block.z + hit.normal.z
    if (py < 0 || py >= World.WORLD_HEIGHT) return null
    if (world.isSolid(px, py, pz)) return null
    val overlapsPlayer =
        px + 1 > player.position.x - Player.WIDTH / 2f &&
        px < player.position.x + Player.WIDTH / 2f &&
        py + 1 > player.position.y &&
        py < player.position.y + Player.HEIGHT &&
        pz + 1 > player.position.z - Player.WIDTH / 2f &&
        pz < player.position.z + Player.WIDTH / 2f
    if (overlapsPlayer) return null
    // Изменение игрока запоминается и попадёт в сохранение мира
    val index = registry.index(blockId)
    world.setBlockByPlayer(px, py, pz, index)
    return BlockChangeEntry(px, py, pz, index)
}

/** Каркас колонки чанка 32×512×32 от её основания. */
fun addColumnBorder(data: MutableList<Float>, column: ChunkColumn, r: Float, g: Float, b: Float, a: Float) {
    boxEdges(
        data,
        column.originX.toFloat(), 0f, column.originZ.toFloat(),
        (column.originX + World.CHUNK_SIZE_X).toFloat(), World.WORLD_HEIGHT.toFloat(),
        (column.originZ + World.CHUNK_SIZE_Z).toFloat(),
        r, g, b, a
    )
}

/** Обводка блока (чуть увеличена, чтобы не мерцала с текстурой). */
fun addBlockOutline(data: MutableList<Float>, bx: Int, by: Int, bz: Int) {
    val e = 0.003f
    boxEdges(data, bx - e, by - e, bz - e, bx + 1 + e, by + 1 + e, bz + 1 + e, 1f, 1f, 1f, 1f)
}

/** Каркас другого игрока — по позиции, пришедшей с сервера. */
fun addPlayerOutline(data: MutableList<Float>, x: Float, y: Float, z: Float) {
    val half = Player.WIDTH / 2f
    boxEdges(
        data,
        x - half, y, z - half,
        x + half, y + Player.HEIGHT, z + half,
        0.2f, 0.9f, 1f, 0.9f
    )
}

/** 12 рёбер куба по двум углам (24 вершины). */
private fun boxEdges(
    data: MutableList<Float>,
    x0: Float, y0: Float, z0: Float,
    x1: Float, y1: Float, z1: Float,
    r: Float, g: Float, b: Float, a: Float
) {
    line(data, x0, y0, z0, x1, y0, z0, r, g, b, a)
    line(data, x1, y0, z0, x1, y0, z1, r, g, b, a)
    line(data, x1, y0, z1, x0, y0, z1, r, g, b, a)
    line(data, x0, y0, z1, x0, y0, z0, r, g, b, a)
    line(data, x0, y1, z0, x1, y1, z0, r, g, b, a)
    line(data, x1, y1, z0, x1, y1, z1, r, g, b, a)
    line(data, x1, y1, z1, x0, y1, z1, r, g, b, a)
    line(data, x0, y1, z1, x0, y1, z0, r, g, b, a)
    line(data, x0, y0, z0, x0, y1, z0, r, g, b, a)
    line(data, x1, y0, z0, x1, y1, z0, r, g, b, a)
    line(data, x1, y0, z1, x1, y1, z1, r, g, b, a)
    line(data, x0, y0, z1, x0, y1, z1, r, g, b, a)
}

private fun line(
    data: MutableList<Float>,
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    r: Float, g: Float, b: Float, a: Float
) {
    data.add(ax); data.add(ay); data.add(az); data.add(r); data.add(g); data.add(b); data.add(a)
    data.add(bx); data.add(by); data.add(bz); data.add(r); data.add(g); data.add(b); data.add(a)
}
