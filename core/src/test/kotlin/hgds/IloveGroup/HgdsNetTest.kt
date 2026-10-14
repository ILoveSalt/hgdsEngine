package hgds.IloveGroup

import hgds.IloveGroup.net.BlockChangeEntry
import hgds.IloveGroup.net.ChatKind
import hgds.IloveGroup.net.HgdsNet
import hgds.IloveGroup.net.NetworkPlayer
import hgds.IloveGroup.net.NetworkPosition
import hgds.IloveGroup.net.Packet
import hgds.IloveGroup.net.PacketType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Тесты протокола hgdsNET: кодирование/декодирование пакетов. */
class HgdsNetTest {

    /** Кодирует пакет в байты кадра и читает обратно. */
    private fun roundtrip(packet: Packet): Packet {
        val bytes = ByteArrayOutputStream()
        HgdsNet.writePacket(DataOutputStream(bytes), packet)
        return HgdsNet.readPacket(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
    }

    @Test
    fun `varint roundtrip`() {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        for (value in listOf(0, 1, 127, 128, 255, 16384, Int.MAX_VALUE)) {
            out.writeVarInt(value)
        }
        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        for (value in listOf(0, 1, 127, 128, 255, 16384, Int.MAX_VALUE)) {
            assertEquals(value, input.readVarInt())
        }
    }

    @Test
    fun `login request roundtrip`() {
        val original = Packet.LoginRequest("Steve")
        val decoded = roundtrip(original) as Packet.LoginRequest
        assertEquals("Steve", decoded.name)
        assertEquals(HgdsNet.MAGIC, decoded.magic)
        assertEquals(HgdsNet.VERSION, decoded.protocolVersion)
    }

    @Test
    fun `login result roundtrip with changes and players`() {
        val original = Packet.LoginResult(
            accepted = true,
            playerId = 7,
            message = "Добро пожаловать",
            seed = 123456789L,
            flat = true,
            renderDistance = 8,
            spawn = NetworkPosition(0.5f, 70f, 0.5f, 90f, 0f),
            changes = listOf(BlockChangeEntry(1, 2, 3, 4), BlockChangeEntry(-5, 64, 7, 2)),
            players = listOf(NetworkPlayer(1, "Steve", NetworkPosition(1f, 2f, 3f, 0f, 0f)))
        )
        val decoded = roundtrip(original) as Packet.LoginResult
        assertTrue(decoded.accepted)
        assertEquals(7, decoded.playerId)
        assertEquals(123456789L, decoded.seed)
        assertTrue(decoded.flat)
        assertEquals(8, decoded.renderDistance)
        assertEquals(2, decoded.changes.size)
        assertEquals(BlockChangeEntry(1, 2, 3, 4), decoded.changes[0])
        assertEquals(1, decoded.players.size)
        assertEquals("Steve", decoded.players[0].name)
    }

    @Test
    fun `chat packet preserves kind`() {
        val decoded = roundtrip(Packet.Chat("Steve", "Привет!", ChatKind.SYSTEM)) as Packet.Chat
        assertEquals("Steve", decoded.sender)
        assertEquals("Привет!", decoded.text)
        assertEquals(ChatKind.SYSTEM, decoded.kind)
    }

    @Test
    fun `block change roundtrip with negative coords`() {
        val decoded = roundtrip(Packet.BlockChange(BlockChangeEntry(-100, 255, -300, 3))) as Packet.BlockChange
        assertEquals(BlockChangeEntry(-100, 255, -300, 3), decoded.change)
    }

    @Test
    fun `kick packet roundtrip`() {
        val decoded = roundtrip(Packet.Kick("Неверная версия протокола")) as Packet.Kick
        assertEquals("Неверная версия протокола", decoded.reason)
    }

    @Test
    fun `server pong roundtrip`() {
        val decoded = roundtrip(Packet.ServerPong("MOTD", 3, 8, HgdsNet.VERSION, 42L)) as Packet.ServerPong
        assertEquals("MOTD", decoded.motd)
        assertEquals(3, decoded.players)
        assertEquals(8, decoded.maxPlayers)
        assertEquals(HgdsNet.VERSION, decoded.protocolVersion)
        assertEquals(42L, decoded.time)
    }

    @Test
    fun `packet types have unique ids`() {
        val ids = PacketType.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Идентификаторы пакетов не должны повторяться")
    }

    @Test
    fun `packet type by id resolves`() {
        assertEquals(PacketType.LOGIN_REQUEST, PacketType.byId(0x00))
        assertEquals(PacketType.SERVER_PONG, PacketType.byId(0x0C))
        assertEquals(null, PacketType.byId(0xFF))
    }
}
