package hgds.IloveGroup.server

import hgds.IloveGroup.net.HgdsNet
import hgds.IloveGroup.util.AppDirs
import hgds.IloveGroup.util.Resources
import kotlin.system.exitProcess

/**
 * Точка входа выделенного сервера: консольное приложение с миром, игроками
 * и журналом. Запуск из Maven:
 *
 * `mvn -o exec:java "-Dexec.mainClass=hgds.IloveGroup.server.ServerMainKt" "-Dexec.args=--world=world --port=45165"`
 *
 * Или скриптом `run-server.ps1 -World world -Port 45165 -Seed 123 -Flat`.
 */
fun main(args: Array<String>) {
    AppDirs.init()
    Resources.exportDefaults() // ресурсы (блоки и т.п.) доступны снаружи и для сервера
    val base = ServerOptions()
    if (args.any { it in listOf("-h", "--help", "help") }) {
        printUsage(base)
        return
    }
    val options = when (val parsed = parseArguments(args, base)) {
        is ParseResult.Ok -> parsed.options
        is ParseResult.Error -> {
            System.err.println("[сервер] ${parsed.message}")
            printUsage(base)
            exitProcess(2)
        }
    }

    val server = try {
        GameServer(options)
    } catch (e: Exception) {
        System.err.println("[сервер] не удалось открыть мир «${options.worldName}»: ${e.message}")
        exitProcess(1)
    }

    try {
        server.start()
    } catch (e: Exception) {
        System.err.println("[сервер] не удалось запустить сервер на порту ${options.port}: ${e.message}")
        exitProcess(1)
    }

    val shutdownHook = Thread({ server.stop() }, "hgdsNET-shutdown")
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    printWelcome(server)
    consoleLoop(server)
    server.stop()
    try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
    } catch (e: IllegalStateException) {
        // JVM уже завершается — снимать hook поздно
    }
}

/** Читает команды консоли сервера, пока сервер работает. */
private fun consoleLoop(server: GameServer) {
    while (server.isRunning()) {
        val line = readLine()
        if (line == null) {
            println("[консоль] ввод недоступен — сервер работает в фоне (Ctrl+C для остановки)")
            while (server.isRunning()) Thread.sleep(1000L)
            return
        }
        if (line.isBlank()) continue
        val result = server.execute(line)
        for (response in result.lines) println("[консоль] $response")
        if (result.shutdown) return
    }
}

private fun printWelcome(server: GameServer) {
    val options = server.options
    println("------------------------------------------------------------")
    println("  VoxelEngine · выделенный сервер (${HgdsNet.MAGIC} v${HgdsNet.VERSION})")
    println("  Мир: ${options.worldName} · порт: ${options.port} · игроков: ${options.maxPlayers} · тиков: ${options.ticksPerSecond}/с")
    println("  help — список команд, stop — остановка сервера")
    println("------------------------------------------------------------")
}


/** Справка по аргументам командной строки. */
private fun printUsage(base: ServerOptions) {
    println("Выделенный сервер VoxelEngine (протокол ${HgdsNet.MAGIC} v${HgdsNet.VERSION}).")
    println()
    println("Использование: server [параметры]")
    println("  --world=<имя>      мир сервера (по умолчанию ${base.worldName}; создаётся, если его нет)")
    println("  --port=<число>     порт (по умолчанию ${base.port})")
    println("  --seed=<число>     сид мира (только при создании мира)")
    println("  --flat             суперплоский мир (только при создании)")
    println("  --max=<1..64>      максимум игроков (по умолчанию ${base.maxPlayers})")
    println("  --render=<1..32>   дальность прорисовки в чанках (по умолчанию ${base.renderDistance})")
    println("  --tps=<1..60>      тиков в секунду (по умолчанию ${base.ticksPerSecond})")
    println("  --motd=<текст>     описание сервера в списке серверов")
    println("  help               этот список")
    println()
    println("Миры сервера хранятся в <папка игры>/server/saves, журналы — в <папка игры>/server/logs.")
}

/** Результат разбора аргументов командной строки. */
sealed class ParseResult {
    class Ok(val options: ServerOptions) : ParseResult()
    class Error(val message: String) : ParseResult()
}

/**
 * Разбирает аргументы вида `--port=45165`, `--flat`, `--motd=текст`.
 * [base] — значения по умолчанию (в тесте можно передать свои папки).
 */
fun parseArguments(args: Array<String>, base: ServerOptions = ServerOptions()): ParseResult {
    var port = base.port
    var worldName = base.worldName
    var seed: Long? = base.seed
    var flat = base.flat
    var maxPlayers = base.maxPlayers
    var renderDistance = base.renderDistance
    var tps = base.ticksPerSecond
    var motd = base.motd

    for (arg in args) {
        val separator = arg.indexOf('=')
        val name = (if (separator < 0) arg else arg.substring(0, separator)).trim()
        val value = if (separator < 0) "" else arg.substring(separator + 1)
        when (name.lowercase().trimStart('-')) {
            "world", "world-name" -> if (value.isNotBlank()) worldName = value.trim()
            "port" -> port = value.toIntOrNull()
                ?: return ParseResult.Error("Порт должен быть числом: $value")
            "seed" -> seed = value.toLongOrNull()
                ?: return ParseResult.Error("Сид должен быть числом: $value")
            "flat" -> flat = value.isBlank() || value.equals("true", ignoreCase = true)
            "max", "players", "max-players" -> maxPlayers = value.toIntOrNull()
                ?: return ParseResult.Error("Число игроков должно быть числом: $value")
            "render", "render-distance" -> renderDistance = value.toIntOrNull()
                ?: return ParseResult.Error("Дальность прорисовки должна быть числом: $value")
            "tps", "ticks", "ticks-per-second" -> tps = value.toIntOrNull()
                ?: return ParseResult.Error("Число тиков должно быть числом: $value")
            "motd", "description" -> if (value.isNotBlank()) motd = value.trim()
            else -> return ParseResult.Error("Неизвестный параметр «$arg»")
        }
    }
    if (port !in 1..65535) return ParseResult.Error("Порт вне диапазона 1..65535: $port")
    if (maxPlayers !in 1..64) return ParseResult.Error("Максимум игроков вне диапазона 1..64: $maxPlayers")
    if (tps !in 1..60) return ParseResult.Error("Тиков в секунду вне диапазона 1..60: $tps")

    return ParseResult.Ok(
        base.copy(
            port = port,
            worldName = worldName,
            seed = seed,
            flat = flat,
            maxPlayers = maxPlayers,
            renderDistance = renderDistance.coerceIn(1, 32),
            motd = motd,
            ticksPerSecond = tps
        )
    )
}
