package hgds.IloveGroup.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Папки игровых данных на диске — аналог .minecraft у Minecraft.
 *
 * Основная папка: %APPDATA%\hgdsEngine\game_1, то есть AppData\Roaming,
 * создаётся при запуске игры вместе с подпапками:
 *  - saves\ — сохранённые миры;
 *  - langs\ — языковые пакеты.
 *
 * На других ОС используется стандартное место для данных приложений.
 */
object AppDirs {

    lateinit var gameDir: Path
        private set

    /** true после первого вызова init(). */
    val initialized: Boolean get() = ::gameDir.isInitialized

    /** Папка сохранений миров (saves). */
    val savesDir: Path get() = gameDir.resolve("saves")

    /** Папка языковых пакетов (langs). */
    val langsDir: Path get() = gameDir.resolve("langs")

    /** Папка данных выделенного сервера (server). */
    val serverDir: Path get() = gameDir.resolve("server")

    /** Сохранения миров выделенного сервера: server/saves. */
    val serverSavesDir: Path get() = serverDir.resolve("saves")

    /** Корень внешних игровых ресурсов: assets. */
    val assetsDir: Path get() = gameDir.resolve("assets")

    /** Журналы сервера: server/logs. */
    val serverLogsDir: Path get() = serverDir.resolve("logs")

    /** Создаёт папки игровых данных (если их нет) и возвращает путь к ним. */
    fun init(): Path {
        val os = System.getProperty("os.name").lowercase()
        val base: Path = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (appData != null) Paths.get(appData)
                else Paths.get(System.getProperty("user.home"), "AppData", "Roaming")
            }
            os.contains("mac") ->
                Paths.get(System.getProperty("user.home"), "Library", "Application Support")
            else ->
                Paths.get(System.getProperty("user.home"), ".local", "share")
        }
        gameDir = base.resolve("hgdsEngine").resolve("game_1")
        Files.createDirectories(savesDir)
        Files.createDirectories(langsDir)
        Files.createDirectories(serverSavesDir)
        Files.createDirectories(serverLogsDir)
        Files.createDirectories(assetsDir)
        println("[hgdsEngine] Папка данных игры: ${gameDir.toAbsolutePath()}")
        return gameDir
    }
}