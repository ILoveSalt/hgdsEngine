package hgds.IloveGroup.net

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/**
 * hgdsNET — собственный протокол VoxelEngine поверх TCP.
 *
 * Рукопожатие: первое сообщение — [Packet.LoginRequest] с «магическим» словом
 * и версией протокола; несовпадение — сервер отвечает [Packet.Kick] и закрывает сокет.
 *
 * Кадрирование (framing): каждый пакет — varint длина полезной нагрузки,
 * затем байт идентификатора пакета и тело переменной длины.
 * Varint — 1..5 байт, 7 бит данных в байте, старший бит — «продолжение».
 * Строки — varint длины + UTF-8.
 */
object HgdsNet {

    /** «Магическое слово» протокола в рукопожатии. */
    const val MAGIC = "hgdsNET"

    /** Версия протокола. Меняется при несовместимых правках пакетов. */
    const val VERSION = 1

    /** Порт сервера по умолчанию. */
    const val DEFAULT_PORT = 45165

    /** Максимальный размер пакета; больше — соединение считается испорченным. */
    const val MAX_PACKET_SIZE = 8 * 1024 * 1024

    /** Максимальная длина строки (имя игрока, сообщение чата, описание блока). */
    const val MAX_STRING_LENGTH = 256

    /** Таймаут ожидания рукопожатия от клиента, мс. */
    const val HANDSHAKE_TIMEOUT_MS = 8000

    /** Пауза между рассылками позиций игроков, мс. */
    const val POSITION_BROADCAST_INTERVAL_MS = 50L

    /** Если пакетов от клиента нет дольше этого времени — соединение считается мёртвым. */
    const val READ_TIMEOUT_MS = 30_000

    /**
     * Пишет пакет в поток: varint длины + идентификатор + тело.
     * Поток не закрывается, сбрасывается только буфер.
     */
    fun writePacket(out: DataOutputStream, packet: Packet) {
        val body = PacketEncoder.encodeBody(packet)
        out.writeVarInt(body.size + Packet.TYPE_ID_SIZE)
        out.write(packet.type.id)
        out.write(body)
        out.flush()
    }

    /**
     * Читает один пакет из потока. null — поток закрыт другом (корректное завершение).
     */
    fun readPacket(input: DataInputStream): Packet {
        val length = input.readVarInt()
        if (length <= 0) throw IOException("Некорректная длина пакета: $length")
        if (length > MAX_PACKET_SIZE) throw IOException("Пакет слишком большой: $length")
        val body = ByteArray(length)
        input.readFully(body)
        val reader = PacketReader(body)
        val typeId = reader.readUnsignedByte()
        val type = PacketType.byId(typeId) ?: throw IOException("Неизвестный пакет: $typeId")
        return Packet.read(type, reader)
    }

    /** Кодирует varint в поток. */
    fun DataOutputStream.writeVarInt(value: Int) {
        var v = value
        repeat(4) {
            if ((v and 0x7F.inv()) == 0) {
                write(v)
                return
            }
            write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        write(v and 0x7F)
    }

    /** Читает varint из потока. */
    fun DataInputStream.readVarInt(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val b = read()
            if (b < 0) throw EOFException("Поток закрыт во время чтения varint")
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return value
            shift += 7
            if (shift > 28) throw IOException("Слишком длинный varint")
        }
    }

    /** Сколько байт займёт varint у числа [value]. */
    fun varIntSize(value: Int): Int {
        var v = value
        var size = 1
        while ((v and 0x7F.inv()) != 0) {
            size++
            v = v ushr 7
        }
        return size
    }
}
