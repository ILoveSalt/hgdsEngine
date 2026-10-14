package hgds.IloveGroup.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import hgds.IloveGroup.GameInfo
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Языковой пакет игры. Все надписи в интерфейсе берутся отсюда по ключам,
 * поэтому текст можно заменить, не трогая код.
 *
 * Дефолтные строки (русский язык) зашиты здесь; при первом запуске игра
 * выгружает их в <игровая папка>/langs/ru_RU.json. Этот файл можно править
 * или заменить полностью другим языковым пакетом.
 */
object Lang {

    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    /** Дефолтные строки (русский язык). Ключи → надписи. */
    private val defaults: Map<String, String> = linkedMapOf(
        // Главное меню
        "title.singleplayer" to "Одиночная игра",
        "title.multiplayer" to "Сетевая игра",
        "title.quit" to "Выход из игры",
        "title.version" to GameInfo.DISPLAY_NAME,
        // Выбор мира
        "select_world.title" to "Выбор мира",
        "select_world.play" to "Играть",
        "select_world.create" to "Создать мир",
        "select_world.delete" to "Удалить",
        "select_world.empty" to "Сохранённых миров пока нет",
        "select_world.entry_info" to "Сид: %s, %s",
        "select_world.confirm_delete" to "Удалить мир «%s» навсегда?",
        // Общие кнопки
        "menu.back" to "Назад",
        "menu.yes" to "Да",
        "menu.no" to "Нет",
        // Настройки мира (создание)
        "create_world.title" to "Настройки мира",
        "create_world.name_label" to "Имя мира",
        "create_world.name_hint" to "Новый мир",
        "create_world.seed_label" to "Сид генератора мира",
        "create_world.seed_hint" to "Пусто — случайный сид",
        "create_world.type" to "Тип мира: %s",
        "world.type.normal" to "Обычный",
        "world.type.flat" to "Суперплоский",
        "create_world.render_distance" to "Дальность прорисовки: %s чанков",
        "create_world.create" to "Создать новый мир",
        "create_world.cancel" to "Отмена",
        // Пауза в мире
        "pause.resume" to "Вернуться в игру",
        "pause.save" to "Сохранить",
        "pause.quit" to "Выйти из мира",
        "pause.quit_server" to "Выйти с сервера",
        // Сетевая игра: список серверов и подключение
        "multiplayer.title" to "Сетевая игра",
        "multiplayer.empty" to "Список серверов пуст — добавьте сервер",
        "multiplayer.add" to "Добавить сервер",
        "multiplayer.connect" to "Подключиться",
        "multiplayer.delete" to "Удалить",
        "multiplayer.name_label" to "Название сервера",
        "multiplayer.name_hint" to "Мой сервер",
        "multiplayer.address_label" to "Адрес сервера",
        "multiplayer.address_hint" to "127.0.0.1:45165",
        "multiplayer.player_label" to "Имя игрока",
        "multiplayer.player_hint" to "Steve",
        "multiplayer.join" to "Войти на сервер",
        "multiplayer.connecting" to "Подключение к серверу…",
        "multiplayer.connect_hint" to "Esc — отменить",
        "multiplayer.cancelled" to "Подключение отменено",
        "multiplayer.pinging" to "Проверка серверов…",
        "multiplayer.offline" to "Сервер недоступен",
        "multiplayer.players" to "%s/%s игроков · %s мс",
        "multiplayer.confirm_delete" to "Удалить сервер «%s» из списка?",
        "multiplayer.connect_failed" to "Не удалось подключиться: %s",
        // Настройки (options)
        "title.options" to "Настройки...",
        "pause.options" to "Настройки...",
        "options.title" to "Настройки",
        "options.done" to "Готово",
        "options.auto" to "Авто",
        "options.gui_scale" to "Масштаб интерфейса: %s",
        "options.fov" to "Обзор: %s",
        "options.sensitivity" to "Чувствительность мыши: %s%%",
        "options.render_distance" to "Дальность прорисовки: %s чанков",
        // Ползунки и пункты графики
        "options.fov_short" to "Обзор",
        "options.brightness" to "Яркость",
        "options.on" to "Вкл",
        "options.off" to "Выкл",
        "options.resolution_auto" to "Разрешение: Авто",
        "options.resolution" to "Разрешение: %s",
        "options.vsync" to "V-Sync: %s",
        "options.fps_limit" to "Ограничение FPS: %s",
        "options.fps_off" to "Выкл",
        "options.fps_simple" to "Простое",
        "options.fps_minimize" to "Свернуть",
        "options.ui_scale" to "Интерфейс: %s",
        "options.fullscreen" to "Полноэкранный режим: %s",
        "options.gpu" to "Граф. процессор",
        "options.gpu_choice" to "Граф. процессор: %s",
        // Разделы настроек (две колонки, как в оригинальном Minecraft)
        "options.online" to "Онлайн",
        "options.skin" to "Внешний вид",
        "options.video" to "Графика",
        "options.chat" to "Чат",
        "options.language" to "Язык",
        "options.sound" to "Звуки",
        "options.controls" to "Управление",
        "options.resource_pack" to "Набор текстур",
        "options.accessibility" to "Специальные возможности",
        "options.credits" to "Титры и авторы",
        "credits.lines" to "${GameInfo.DISPLAY_NAME}\n\nИгровой движок и сервер:\nhgds\n\nШрифт интерфейса:\nMinecraft RUS (minecraft.ttf)\n\nТекстуры:\nMinecraft (Mojang Studios)\n\nБиблиотеки:\nLWJGL 3 · Netty · JOML\nGson · Kotlin Coroutines\n\nСпасибо за игру!",
        // Чат в игре
        "chat.placeholder" to "Введите сообщение…",
        "chat.joined" to "%s присоединился к игре",
        "chat.left" to "Вы вышли с сервера",
        "chat.disconnected" to "Связь с сервером потеряна",
        "misc.chat_hint" to "T — чат",
        "misc.joined_server" to "Вы подключились к %s",
        "misc.disconnected" to "Соединение с сервером прервано: %s",
        // Сообщения
        "misc.world_saved" to "Мир сохранён!",
        // Инвентарь
        "inventory.title" to "Инвентарь",
    )

    private var values: Map<String, String> = defaults

    /**
     * Ключи, которые всегда берутся из кода и не переопределяются
     * языковым пакетом (иначе в меню оставалась бы старая версия из json).
     */
    private val FORCED_KEYS: Map<String, String> = mapOf(
        "title.version" to GameInfo.DISPLAY_NAME
    )

    /** Файл текущего языкового пакета (заполняется в init). */
    var langFile: Path? = null
        private set

    /**
     * Инициализация: при первом запуске выгружает дефолтный языковой пакет
     * в <langs>/ru_RU.json и загружает его (если файл правили — применяются правки).
     */
    fun init(langsDir: Path) {
        val file = langsDir.resolve("ru_RU.json")
        langFile = file
        if (!Files.exists(file)) {
            Files.createDirectories(langsDir)
            Files.write(file, prettyGson.toJson(defaults).toByteArray(StandardCharsets.UTF_8))
            println("[hgdsEngine] Создан языковой пакет: $file")
        }
        load(file)
    }

    /** Загружает языковой пакет из файла; недостающие ключи берутся из дефолта. */
    fun load(file: Path) {
        try {
            if (!Files.exists(file)) {
                // Файла нет — откат к дефолтным строкам
                values = defaults
                return
            }
            val text = Files.readString(file, StandardCharsets.UTF_8)
            val type = object : TypeToken<Map<String, String>>() {}.type
            val parsed: Map<String, String>? = gson.fromJson(text, type)
            // Версия всегда из кода (GameInfo), локализация её переопределить не может
            values = if (parsed.isNullOrEmpty()) defaults else (defaults + parsed) + FORCED_KEYS
        } catch (e: Exception) {
            System.err.println("[hgdsEngine] Ошибка загрузки языкового пакета $file: ${e.message}")
            values = defaults
        }
    }

    /** Строка по ключу (если ключа нет — возвращается сам ключ). */
    fun get(key: String): String = values[key] ?: key

    /** Строка по ключу с подстановкой аргументов вместо %s по очереди. */
    fun format(key: String, vararg args: Any?): String {
        var text = get(key)
        for (arg in args) text = text.replaceFirst("%s", arg.toString())
        return text
    }
}