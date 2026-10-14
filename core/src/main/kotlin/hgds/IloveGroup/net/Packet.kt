package hgds.IloveGroup.net

import hgds.IloveGroup.net.HgdsNet.readVarInt
import hgds.IloveGroup.net.HgdsNet.writeVarInt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Типы пакетов hgdsNET: идентификатор — один байт перед телом. */
enum class PacketType(val id: Int) {
    /** C2S: рукопожатие — имя игрока, магическое слово и версия протокола. */
    LOGIN_REQUEST(0x00),

    /** S2C: результат входа — сид мира, спавн, изменения блоков и список игроков. */
    LOGIN_RESULT(0x01),

    /** S2C: игрок появился в мире. */
    PLAYER_JOIN(0x02),

    /** S2C: игрок покинул мир. */
    PLAYER_LEAVE(0x03),

    /** S2C: пакетное обновление позиций игроков. */
    PLAYER_UPDATE(0x04),

    /** C2S: позиция и поворот своего игрока. */
    PLAYER_POSITION(0x05),

    /** C2S/S2C: изменение блока (ломание/установка). */
    BLOCK_CHANGE(0x06),

    /** C2S/S2C: сообщение чата. */
    CHAT(0x07),

    /** C2S: проверка соединения (замер задержки). */
    PING(0x08),

    /** S2C: ответ на [PING]. */
    PONG(0x09),

    /** S2C: причина отключения. */
    KICK(0x0A),

    /** C2S: запрос информации о сервере из списка (без входа в мир). */
    SERVER_PING(0x0B),

    /** S2C: ответ на [SERVER_PING] — описание, число игроков, задержка. */
    SERVER_PONG(0x0C);

    companion object {
        private val byIdMap = values().associateBy { it.id }
        fun byId(id: Int): PacketType? = byIdMap[id]
    }
}

/** Вид сообщения чата: влияет на цвет в интерфейсе. */
enum class ChatKind(val code: Int) {
    /** Реплика игрока: «Игрок: текст». */
    CHAT(0),

    /** Системное сообщение сервера (жёлтое). */
    SYSTEM(1),

    /** Подключение/отключение игрока (серое). */
    JOIN_LEAVE(2),

    /** Действие в звёздочках (*Игрок сломал блок*). */
    ACTION(3);

    companion object {
        fun byCode(code: Int): ChatKind = values().firstOrNull { it.code == code } ?: CHAT
    }
}

/** Позиция и поворот игрока в протоколе. */
data class NetworkPosition(
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
    val pitch: Float
)

/** Игрок в протоколе: идентификатор на сервере, имя и позиция. */
data class NetworkPlayer(val id: Int, val name: String, val position: NetworkPosition)

/** Изменение блока: координата + индекс блока в реестре. */
data class BlockChangeEntry(val x: Int, val y: Int, val z: Int, val blockIndex: Int)

/** Буфер тела пакета: последовательность значений hgdsNET. */
class PacketWriter {
    private val bytes = ByteArrayOutputStream(64)
    private val out = DataOutputStream(bytes)

    fun writeBoolean(value: Boolean) = out.writeBoolean(value)
    fun writeByte(value: Int) = out.writeByte(value)
    fun writeInt(value: Int) = out.writeInt(value)
    fun writeLong(value: Long) = out.writeLong(value)
    fun writeFloat(value: Float) = out.writeFloat(value)
    fun writeVarInt(value: Int) = out.writeVarInt(value)

    /** Строка: varint длины + UTF-8 (длиннее лимита — обрезается). */
    fun writeString(value: String) {
        val clipped = if (value.length > HgdsNet.MAX_STRING_LENGTH) {
            value.substring(0, HgdsNet.MAX_STRING_LENGTH)
        } else {
            value
        }
        val raw = clipped.toByteArray(Charsets.UTF_8)
        out.writeVarInt(raw.size)
        out.write(raw)
    }

    fun toByteArray(): ByteArray {
        out.flush()
        return bytes.toByteArray()
    }
}

/** Читалка тела пакета. */
class PacketReader(body: ByteArray) {
    private val input = DataInputStream(ByteArrayInputStream(body))

    fun readBoolean(): Boolean = input.readBoolean()
    fun readInt(): Int = input.readInt()
    fun readLong(): Long = input.readLong()
    fun readFloat(): Float = input.readFloat()
    fun readVarInt(): Int = input.readVarInt()
    fun readUnsignedByte(): Int = input.readUnsignedByte()

    /** Строка: varint длины + UTF-8. */
    fun readString(): String {
        val length = input.readVarInt()
        if (length < 0 || length > HgdsNet.MAX_PACKET_SIZE) {
            throw IOException("Некорректная длина строки: $length")
        }
        val raw = ByteArray(length)
        input.readFully(raw)
        return String(raw, Charsets.UTF_8)
    }

    /** Сколько байт тела осталось непрочитанным. */
    val remaining: Int get() = input.available()
}

/**
 * Пакет hgdsNET. Тело пакета — последовательность значений, записанная
 * [PacketWriter] и прочитанная [PacketReader] в одном и том же порядке.
 */
sealed class Packet {

    /** Тип пакета (его идентификатор пишется перед телом). */
    abstract val type: PacketType

    /** Записывает тело пакета в буфер. */
    internal abstract fun writeBody(body: PacketWriter)

    /** Рукопожатие клиента: имя игрока, магическое слово и версия протокола. */
    data class LoginRequest(
        val name: String,
        val magic: String = HgdsNet.MAGIC,
        val protocolVersion: Int = HgdsNet.VERSION
    ) : Packet() {
        override val type: PacketType get() = PacketType.LOGIN_REQUEST

        internal override fun writeBody(body: PacketWriter) {
            body.writeString(name)
            body.writeString(magic)
            body.writeVarInt(protocolVersion)
        }

        internal companion object {
            internal fun readBody(body: PacketReader): LoginRequest =
                LoginRequest(body.readString(), body.readString(), body.readVarInt())
        }
    }

    /** Результат входа: параметры мира + начальные данные (изменения блоков, игроки). */
    data class LoginResult(
        val accepted: Boolean,
        val playerId: Int,
        val message: String,
        val seed: Long,
        val flat: Boolean,
        val renderDistance: Int,
        val spawn: NetworkPosition,
        val changes: List<BlockChangeEntry>,
        val players: List<NetworkPlayer>
    ) : Packet() {
        override val type: PacketType get() = PacketType.LOGIN_RESULT

        internal override fun writeBody(body: PacketWriter) {
            body.writeBoolean(accepted)
            body.writeVarInt(playerId)
            body.writeString(message)
            body.writeLong(seed)
            body.writeBoolean(flat)
            body.writeVarInt(renderDistance)
            writePosition(body, spawn)
            body.writeVarInt(changes.size)
            for (change in changes) writeBlockChange(body, change)
            writePlayers(body, players)
        }

        internal companion object {
            internal fun readBody(body: PacketReader): LoginResult {
                val accepted = body.readBoolean()
                val playerId = body.readVarInt()
                val message = body.readString()
                val seed = body.readLong()
                val flat = body.readBoolean()
                val renderDistance = body.readVarInt()
                val spawn = readPosition(body)
                val changes = ArrayList<BlockChangeEntry>()
                repeat(body.readVarInt()) { changes.add(readBlockChange(body)) }
                return LoginResult(
                    accepted, playerId, message, seed, flat, renderDistance, spawn,
                    changes, readPlayers(body)
                )
            }
        }
    }

    /** Игрок появился в мире. */
    data class PlayerJoin(val player: NetworkPlayer) : Packet() {
        override val type: PacketType get() = PacketType.PLAYER_JOIN

        internal override fun writeBody(body: PacketWriter) {
            body.writeVarInt(player.id)
            body.writeString(player.name)
            writePosition(body, player.position)
        }

        internal companion object {
            internal fun readBody(body: PacketReader): PlayerJoin = PlayerJoin(
                NetworkPlayer(body.readVarInt(), body.readString(), readPosition(body))
            )
        }
    }

    /** Игрок покинул мир. */
    data class PlayerLeave(val playerId: Int) : Packet() {
        override val type: PacketType get() = PacketType.PLAYER_LEAVE

        internal override fun writeBody(body: PacketWriter) = body.writeVarInt(playerId)

        internal companion object {
            internal fun readBody(body: PacketReader): PlayerLeave = PlayerLeave(body.readVarInt())
        }
    }

    /** Пакетное обновление позиций игроков (рассылка 20 раз в секунду). */
    data class PlayerUpdate(val positions: List<NetworkPlayer>) : Packet() {
        override val type: PacketType get() = PacketType.PLAYER_UPDATE

        internal override fun writeBody(body: PacketWriter) = writePlayers(body, positions)

        internal companion object {
            internal fun readBody(body: PacketReader): PlayerUpdate = PlayerUpdate(readPlayers(body))
        }
    }

    /** Клиент сообщает свою позицию и поворот. */
    data class PlayerPosition(val position: NetworkPosition) : Packet() {
        override val type: PacketType get() = PacketType.PLAYER_POSITION

        internal override fun writeBody(body: PacketWriter) = writePosition(body, position)

        internal companion object {
            internal fun readBody(body: PacketReader): PlayerPosition = PlayerPosition(readPosition(body))
        }
    }

    /** Изменение блока: сервер рассылает его всем, клиент — запрашивает. */
    data class BlockChange(val change: BlockChangeEntry) : Packet() {
        override val type: PacketType get() = PacketType.BLOCK_CHANGE

        internal override fun writeBody(body: PacketWriter) = writeBlockChange(body, change)

        internal companion object {
            internal fun readBody(body: PacketReader): BlockChange = BlockChange(readBlockChange(body))
        }
    }

    /** Сообщение чата: отправитель, текст и вид. */
    data class Chat(val sender: String, val text: String, val kind: ChatKind) : Packet() {
        override val type: PacketType get() = PacketType.CHAT

        internal override fun writeBody(body: PacketWriter) {
            body.writeString(sender)
            body.writeString(text)
            body.writeByte(kind.code)
        }

        internal companion object {
            internal fun readBody(body: PacketReader): Chat =
                Chat(body.readString(), body.readString(), ChatKind.byCode(body.readUnsignedByte()))
        }
    }

    /** Проверка соединения (замер задержки). */
    data class Ping(val time: Long) : Packet() {
        override val type: PacketType get() = PacketType.PING

        internal override fun writeBody(body: PacketWriter) = body.writeLong(time)

        internal companion object {
            internal fun readBody(body: PacketReader): Ping = Ping(body.readLong())
        }
    }

    /** Ответ на [Ping]. */
    data class Pong(val time: Long) : Packet() {
        override val type: PacketType get() = PacketType.PONG

        internal override fun writeBody(body: PacketWriter) = body.writeLong(time)

        internal companion object {
            internal fun readBody(body: PacketReader): Pong = Pong(body.readLong())
        }
    }

    /** Причина отключения клиента. */
    data class Kick(val reason: String) : Packet() {
        override val type: PacketType get() = PacketType.KICK

        internal override fun writeBody(body: PacketWriter) = body.writeString(reason)

        internal companion object {
            internal fun readBody(body: PacketReader): Kick = Kick(body.readString())
        }
    }

    /** Запрос информации о сервере из списка серверов (без входа в мир). */
    data class ServerPing(val time: Long) : Packet() {
        override val type: PacketType get() = PacketType.SERVER_PING

        internal override fun writeBody(body: PacketWriter) = body.writeLong(time)

        internal companion object {
            internal fun readBody(body: PacketReader): ServerPing = ServerPing(body.readLong())
        }
    }

    /** Ответ на [ServerPing]: описание, игроки, версия протокола. */
    data class ServerPong(
        val motd: String,
        val players: Int,
        val maxPlayers: Int,
        val protocolVersion: Int,
        val time: Long
    ) : Packet() {
        override val type: PacketType get() = PacketType.SERVER_PONG

        internal override fun writeBody(body: PacketWriter) {
            body.writeString(motd)
            body.writeVarInt(players)
            body.writeVarInt(maxPlayers)
            body.writeVarInt(protocolVersion)
            body.writeLong(time)
        }

        internal companion object {
            internal fun readBody(body: PacketReader): ServerPong = ServerPong(
                body.readString(), body.readVarInt(), body.readVarInt(),
                body.readVarInt(), body.readLong()
            )
        }
    }

    companion object {
        /** Идентификатор пакета занимает один байт перед телом. */
        const val TYPE_ID_SIZE = 1

        /** Читает пакет данного типа из тела. */
        internal fun read(type: PacketType, body: PacketReader): Packet = when (type) {
            PacketType.LOGIN_REQUEST -> LoginRequest.readBody(body)
            PacketType.LOGIN_RESULT -> LoginResult.readBody(body)
            PacketType.PLAYER_JOIN -> PlayerJoin.readBody(body)
            PacketType.PLAYER_LEAVE -> PlayerLeave.readBody(body)
            PacketType.PLAYER_UPDATE -> PlayerUpdate.readBody(body)
            PacketType.PLAYER_POSITION -> PlayerPosition.readBody(body)
            PacketType.BLOCK_CHANGE -> BlockChange.readBody(body)
            PacketType.CHAT -> Chat.readBody(body)
            PacketType.PING -> Ping.readBody(body)
            PacketType.PONG -> Pong.readBody(body)
            PacketType.KICK -> Kick.readBody(body)
            PacketType.SERVER_PING -> ServerPing.readBody(body)
            PacketType.SERVER_PONG -> ServerPong.readBody(body)
        }
    }
}

/** Кодирование пакетов hgdsNET в байты тела и обратно. */
internal object PacketEncoder {

    /** Собирает тело пакета (без идентификатора типа). */
    fun encodeBody(packet: Packet): ByteArray {
        val body = PacketWriter()
        packet.writeBody(body)
        return body.toByteArray()
    }
}

private fun writePosition(body: PacketWriter, position: NetworkPosition) {
    body.writeFloat(position.x)
    body.writeFloat(position.y)
    body.writeFloat(position.z)
    body.writeFloat(position.yaw)
    body.writeFloat(position.pitch)
}

private fun readPosition(body: PacketReader): NetworkPosition = NetworkPosition(
    body.readFloat(), body.readFloat(), body.readFloat(), body.readFloat(), body.readFloat()
)

private fun writeBlockChange(body: PacketWriter, change: BlockChangeEntry) {
    body.writeInt(change.x)
    body.writeInt(change.y)
    body.writeInt(change.z)
    body.writeVarInt(change.blockIndex)
}

private fun readBlockChange(body: PacketReader): BlockChangeEntry = BlockChangeEntry(
    body.readInt(), body.readInt(), body.readInt(), body.readVarInt()
)

private fun writePlayers(body: PacketWriter, players: List<NetworkPlayer>) {
    body.writeVarInt(players.size)
    for (player in players) {
        body.writeVarInt(player.id)
        body.writeString(player.name)
        writePosition(body, player.position)
    }
}

private fun readPlayers(body: PacketReader): List<NetworkPlayer> {
    val players = ArrayList<NetworkPlayer>()
    repeat(body.readVarInt()) {
        players.add(NetworkPlayer(body.readVarInt(), body.readString(), readPosition(body)))
    }
    return players
}



