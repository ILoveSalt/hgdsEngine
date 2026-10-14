package hgds.IloveGroup

import hgds.IloveGroup.net.BlockChangeEntry
import hgds.IloveGroup.net.ChatKind
import hgds.IloveGroup.net.HgdsClient
import hgds.IloveGroup.net.HgdsNet
import hgds.IloveGroup.net.Packet
import hgds.IloveGroup.server.GameServer
import hgds.IloveGroup.server.ServerOptions
import hgds.IloveGroup.world.BlockRegistry
import hgds.IloveGroup.world.TerrainGenerator
import hgds.IloveGroup.world.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Интеграционный тест: поднимает настоящий выделенный сервер на свободном
 * порту, подключается клиентом hgdsNET и проверяет полный цикл:
 * вход, спавн, изменение блока, чат, отключение.
 */
class ServerIntegrationTest {


    @Test
    fun `client login receives world data`() {
        val server = GameServer(options(port = 0))
        server.start()
        try {
            val client = HgdsClient()
            val result = client.connect(
                host = "127.0.0.1",
                port = server.boundPort,
                name = "Steve",
                timeoutMs = 5000
            )
            assertTrue(result.accepted)
            assertTrue(client.playerId >= 0, "Сервер выдал идентификатор игрока")
            assertEquals(TerrainGenerator.FLAT_SURFACE + 2f, result.spawn.y, 0.01f, "Спавн над плоским миром")
            client.close()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `block change propagates to world`() {
        val server = GameServer(options(port = 0))
        server.start()
        try {
            val client = HgdsClient()
            client.connect("127.0.0.1", server.boundPort, "Builder", timeoutMs = 5000)

            val registry = BlockRegistry.load()
            val logIndex = registry.index("oak_log")
            client.send(Packet.BlockChange(BlockChangeEntry(5, 70, -3, logIndex)))

            Thread.sleep(300)
            assertEquals(logIndex, server.blockAt(5, 70, -3), "Сервер применил изменение блока")
            client.close()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `second client with same name is rejected`() {
        val server = GameServer(options(port = 0))
        server.start()
        try {
            val first = HgdsClient()
            first.connect("127.0.0.1", server.boundPort, "Дубликат", timeoutMs = 5000)

            val second = HgdsClient()
            val rejected = try {
                second.connect("127.0.0.1", server.boundPort, "Дубликат", timeoutMs = 5000)
                false
            } catch (e: java.io.IOException) {
                true
            }
            assertTrue(rejected, "Имя игрока должно быть уникальным")
            first.close()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `world persists between server restarts`() {
        val registry = BlockRegistry.load()
        val logIndex = registry.index("oak_log")
        val saves = tempDir.resolve("saves-persist")
        val logs = tempDir.resolve("logs-persist")

        val first = GameServer(
            ServerOptions("persist", 0, null, true, savesDir = saves, logsDir = logs)
        )
        first.start()
        try {
            val client = HgdsClient()
            client.connect("127.0.0.1", first.boundPort, "Editor", timeoutMs = 5000)
            client.send(Packet.BlockChange(BlockChangeEntry(9, 70, 9, logIndex)))
            Thread.sleep(300)
            client.close()
        } finally {
            first.stop()
        }

        val second = GameServer(
            ServerOptions("persist", 0, null, true, savesDir = saves, logsDir = logs)
        )
        second.start()
        try {
            assertEquals(logIndex, second.blockAt(9, 70, 9), "Правка переживает перезапуск")
        } finally {
            second.stop()
        }
    }

    @Test
    fun `flat world generator matches client expectations`() {
        val registry = BlockRegistry.load()
        val gen = TerrainGenerator(
            1L,
            registry.index("grass_block"),
            registry.index("dirt_block"),
            registry.index("stone_block"),
            flat = true
        )
        val world = World(gen)
        assertEquals(
            registry.index("grass_block"),
            world.getBlock(0, TerrainGenerator.FLAT_SURFACE, 0)
        )
        assertTrue(Files.exists(tempDir))
    }

    @Test
    fun `chat send does not break connection`() {
        val server = GameServer(options(port = 0))
        server.start()
        try {
            val client = HgdsClient()
            client.connect("127.0.0.1", server.boundPort, "Chatter", timeoutMs = 5000)
            client.send(Packet.Chat("Chatter", "Привет!", ChatKind.CHAT))
            Thread.sleep(300)
            assertTrue(client.connected, "Соединение живо после чата")
            client.close()
        } finally {
            server.stop()
        }
    }
}

    @TempDir
    lateinit var tempDir: Path

    private fun options(port: Int, world: String = "integration"): ServerOptions = ServerOptions(
        worldName = world,
        port = port,
        flat = true,
        savesDir = tempDir.resolve("saves"),
        logsDir = tempDir.resolve("logs")
    )
