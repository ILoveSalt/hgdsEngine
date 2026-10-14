package hgds.IloveGroup.server

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Журнал выделенного сервера: строки одновременно печатаются в консоль
 * и пишутся в файл-чейнджлог `<папка сервера>/logs/server-<дата>.log`.
 *
 * Сообщения чата дополнительно сохраняются в `logs/chat-<дата>.log`,
 * изменения мира — в `logs/world-<дата>.log` (чтобы не засорять консоль).
 */
class ServerLog(private val logsDir: Path) {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** Файл полного журнала запуска. */
    val serverLogFile: Path

    /** Файл истории чата. */
    val chatLogFile: Path

    /** Файл изменений мира (block changes). */
    val worldLogFile: Path

    private val lock = Any()

    /** Последние строки журнала (для команд консоли). */
    private val recent = ArrayList<String>()

    /** Количество записанных изменений мира. */
    var blockChanges: Int = 0
        private set

    init {
        Files.createDirectories(logsDir)
        val now = LocalDateTime.now()
        serverLogFile = logsDir.resolve("server-${now.format(stamp)}.log")
        chatLogFile = logsDir.resolve("chat-${now.format(day)}.log")
        worldLogFile = logsDir.resolve("world-${now.format(day)}.log")
    }

    /** Строка в консоль и в журнал сервера. */
    fun info(text: String) = line("INFO", text)

    /** Предупреждение (жёлтым не выделяем — консоль текстовая). */
    fun warn(text: String) = line("WARN", text)

    /** Ошибка. */
    fun error(text: String) = line("ERROR", text)

    /** Запись чата: в консоль, в журнал сервера и в историю чата. */
    fun chat(sender: String, text: String) {
        val rendered = if (sender.isEmpty()) text else "<$sender> $text"
        line("CHAT", rendered)
        append(chatLogFile, "${now()} $rendered")
    }

    /**
     * Изменение мира: только в файл (в консоли такой поток нечитаем),
     * счётчик доступен команде status.
     */
    fun blockChange(text: String) {
        blockChanges++
        append(worldLogFile, "${now()} $text")
    }

    /** Последние [count] строк журнала. */
    fun tail(count: Int): List<String> = synchronized(lock) {
        recent.drop((recent.size - count).coerceAtLeast(0))
    }

    private fun line(level: String, text: String) {
        val stamped = "${now()} [$level]: $text"
        synchronized(lock) {
            recent.add(stamped)
            while (recent.size > MAX_RECENT) recent.removeAt(0)
        }
        println("[server] $stamped")
        append(serverLogFile, stamped)
    }

    private fun now(): String = LocalDateTime.now().format(clock)

    private fun append(file: Path, text: String) {
        try {
            Files.writeString(
                file,
                text + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        } catch (e: IOException) {
            System.err.println("[server] не удалось записать журнал ${file.fileName}: ${e.message}")
        }
    }

    companion object {
        /** Сколько последних строк журнала держать в памяти (команда log). */
        const val MAX_RECENT = 200
    }
}
