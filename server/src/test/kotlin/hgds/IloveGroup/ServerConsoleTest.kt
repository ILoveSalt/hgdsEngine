package hgds.IloveGroup

import hgds.IloveGroup.net.HgdsNet
import hgds.IloveGroup.server.GameServer
import hgds.IloveGroup.server.ParseResult
import hgds.IloveGroup.server.ServerOptions
import hgds.IloveGroup.server.parseArguments
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Тесты консоли и аргументов выделенного сервера. */
class ServerConsoleTest {

    @TempDir
    lateinit var tempDir: Path

    private fun options(): ServerOptions = ServerOptions(
        worldName = "console-test",
        port = 0,
        savesDir = tempDir.resolve("saves"),
        logsDir = tempDir.resolve("logs")
    )

    @AfterEach
    fun cleanup() {
        // Файлы тестового мира остаются в tempDir — JUnit удалит её сам
    }

    @Test
    fun `parse arguments defaults`() {
        val result = parseArguments(emptyArray())
        assertTrue(result is ParseResult.Ok)
        result as ParseResult.Ok
        assertEquals(HgdsNet.DEFAULT_PORT, result.options.port)
        assertEquals("world", result.options.worldName)
        assertFalse(result.options.flat)
    }

    @Test
    fun `parse arguments full set`() {
        val result = parseArguments(arrayOf("--world=тест", "--port=25599", "--seed=42", "--flat", "--max=16", "--tps=30"))
        assertTrue(result is ParseResult.Ok)
        result as ParseResult.Ok
        assertEquals("тест", result.options.worldName)
        assertEquals(25599, result.options.port)
        assertEquals(42L, result.options.seed)
        assertTrue(result.options.flat)
        assertEquals(16, result.options.maxPlayers)
        assertEquals(30, result.options.ticksPerSecond)
    }

    @Test
    fun `parse arguments rejects bad port`() {
        val result = parseArguments(arrayOf("--port=99999"))
        assertTrue(result is ParseResult.Error)
    }

    @Test
    fun `parse arguments rejects unknown parameter`() {
        val result = parseArguments(arrayOf("--что-то=1"))
        assertTrue(result is ParseResult.Error)
    }

    @Test
    fun `parse arguments clamps render distance`() {
        val result = parseArguments(arrayOf("--render=100")) as ParseResult.Ok
        assertEquals(32, result.options.renderDistance)
    }

    @Test
    fun `server executes version command`() {
        val server = GameServer(options())
        val result = server.execute("version")
        assertTrue(result.lines.isNotEmpty())
        assertTrue(result.lines.first().contains(GameInfo.VERSION))
        assertFalse(result.shutdown)
    }

    @Test
    fun `server executes help command`() {
        val server = GameServer(options())
        val result = server.execute("help")
        assertTrue(result.lines.isNotEmpty())
        assertFalse(result.shutdown)
    }

    @Test
    fun `server stop command shuts down`() {
        val server = GameServer(options())
        val result = server.execute("stop")
        assertTrue(result.shutdown, "stop останавливает сервер")
    }

    @Test
    fun `server unknown command returns error`() {
        val server = GameServer(options())
        val result = server.execute("абракадабра")
        assertTrue(result.lines.isNotEmpty())
    }
}
