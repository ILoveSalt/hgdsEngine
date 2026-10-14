package hgds.IloveGroup.net

import hgds.IloveGroup.ui.ChatHud
import hgds.IloveGroup.world.World

/**
 * Сеанс игры на сервере: соединение, список других игроков и чат.
 *
 * Все серверные пакеты обрабатываются в игровом цикле через [process] —
 * сетевые потоки лишь складывают их в очередь клиента.
 */
class MultiplayerSession(
    val client: HgdsClient,
    val nick: String,
    val serverAddress: String,
    val chat: ChatHud
) {

    /** Другие игроки на сервере: идентификатор → позиция. */
    val remotePlayers = LinkedHashMap<Int, NetworkPlayer>()

    /** Описание сервера, полученное при входе. */
    var message: String = ""

    /** Причина разрыва: Kick сервера или потеря связи. null — связь есть. */
    @Volatile
    var disconnectReason: String? = null
        private set

    /** Замеренная задержка до сервера, мс. */
    val latencyMs: Int get() = client.latencyMs

    /**
     * Обрабатывает принятые пакеты. Возвращает true, если соединение разорвано
     * и нужно выходить в меню.
     */
    fun process(world: World, maxPackets: Int = 256): Boolean {
        for (packet in client.poll(maxPackets)) {
            when (packet) {
                is Packet.PlayerJoin -> remotePlayers[packet.player.id] = packet.player

                is Packet.PlayerLeave -> remotePlayers.remove(packet.playerId)

                is Packet.PlayerUpdate ->
                    for (player in packet.positions) {
                        if (player.id != client.playerId) remotePlayers[player.id] = player
                    }

                // Мир на сервере авторитетный: изменения приходят всем, и автору тоже
                is Packet.BlockChange -> {
                    println("[hgdsNET] Блок (${packet.change.x},${packet.change.y},${packet.change.z}) → ${packet.change.blockIndex}")
                    world.setBlock(packet.change.x, packet.change.y, packet.change.z, packet.change.blockIndex)
                }

                is Packet.Chat -> {
                    println("[hgdsNET] Чат ${packet.sender.ifEmpty { "сервер" }}: ${packet.text}")
                    chat.add(packet.sender, packet.text, packet.kind)
                }

                else -> Unit
            }
        }
        val reason = disconnectReason
        if (reason == null) {
            if (client.kickReason != null) disconnectReason = client.kickReason
            else if (client.disconnected || !client.connected) disconnectReason = "Связь с сервером потеряна"
        }
        return disconnectReason != null
    }

    /** Отправляет сообщение чата (пустое не отправляется). */
    fun sendChat(text: String) {
        if (text.isEmpty()) return
        client.send(Packet.Chat(nick, text, ChatKind.CHAT))
    }

    /** Изменение блока: сервер применит его и разошлёт всем игрокам. */
    fun sendBlockChange(change: BlockChangeEntry) {
        client.send(Packet.BlockChange(change))
    }

    /** Разрушение блока (в воздух — BlockRegistry.AIR). */
    fun sendBlockChange(x: Int, y: Int, z: Int, blockIndex: Int) {
        sendBlockChange(BlockChangeEntry(x, y, z, blockIndex))
    }

    /** Позиция своего игрока для рассылки другим. */
    fun sendPosition(x: Float, y: Float, z: Float, yaw: Float, pitch: Float) {
        client.sendPosition(NetworkPosition(x, y, z, yaw, pitch))
    }

    fun close() {
        disconnectReason = null
        remotePlayers.clear()
        client.close()
    }
}
