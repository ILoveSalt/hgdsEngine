package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.net.HgdsClient
import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.TextField
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.ui.Widget
import hgds.IloveGroup.util.AppDirs
import hgds.IloveGroup.util.Lang
import hgds.IloveGroup.util.ServerEntry
import hgds.IloveGroup.util.ServerListStorage
import java.util.concurrent.ConcurrentHashMap

/**
 * Экран «Сетевая игра»: список серверов (servers.json), проверка доступности
 * и задержки в фоновом потоке, добавление/удаление сервера и подключение.
 */
class MultiplayerScreen(
    ctx: UiContext,
    initialNick: String,
    private val notice: String = "",
    private val onConnect: (address: String, nick: String) -> Unit,
    private val onBack: () -> Unit
) : Screen(ctx) {

    /** Ответ сервера на запрос из списка (для строки списка). */
    data class PingInfo(val motd: String, val players: Int, val maxPlayers: Int, val latencyMs: Int)

    /** Список серверов (обновляется при каждом показе экрана). */
    var servers = ArrayList<ServerEntry>()
        private set

    var selectedIndex = -1
    var scrollOffset = 0

    /** Имя игрока, с которым пойдём на сервер. */
    var nick: String = initialNick

    private val pingResults = ConcurrentHashMap<String, PingInfo>()
    private var pingThread: Thread? = null
    private var addingServer = false
    private var confirmingDelete = false
    private var nickField: TextField? = null
    private var nameField: TextField? = null
    private var addressField: TextField? = null

    /** Верх списка (в пикселях GUI); низ — над рядами кнопок. */
    val listTop = LIST_MARGIN_TOP
    val listBottom get() = ctx.guiHeight - 56

    override fun init() {
        stopPing()
        servers.clear()
        servers.addAll(ServerListStorage.load(AppDirs.gameDir))
        selectedIndex = if (servers.isEmpty()) -1 else 0
        addingServer = false
        confirmingDelete = false
        startPing()
        rebuildWidgets()
    }

    /** Проверка серверов в фоне: поток опрашивает адреса по очереди. */
    private fun startPing() {
        pingResults.clear()
        val addresses = servers.map { it.address }
        if (addresses.isEmpty()) return
        pingThread = Thread({
            for (address in addresses) {
                if (Thread.currentThread().isInterrupted) return@Thread
                val started = System.currentTimeMillis()
                val answer = HgdsClient.ping(address, PING_TIMEOUT_MS) ?: continue
                pingResults[address] = PingInfo(
                    answer.motd, answer.players, answer.maxPlayers,
                    (System.currentTimeMillis() - started).toInt()
                )
            }
        }, "hgdsNET-list-ping").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopPing() {
        pingThread?.interrupt()
        pingThread = null
    }

    private fun rebuildWidgets() {
        widgets.clear()
        when {
            confirmingDelete -> buildConfirmWidgets()
            addingServer -> buildAddWidgets()
            else -> buildListWidgets()
        }
    }

    private fun buildListWidgets() {
        // Имя игрока — под заголовком
        val field = TextField(ctx.centerX(NICK_WIDTH), 30, NICK_WIDTH, 20)
        field.hint = Lang.get("multiplayer.player_hint")
        field.maxLength = MAX_NICK_LENGTH
        field.text = nick
        nickField = field
        widgets.add(field)

        val entryX = ctx.centerX(ENTRY_WIDTH)
        for (i in servers.indices) {
            val rowY = listTop + ROW_SPACING + i * (ENTRY_HEIGHT + ENTRY_GAP) - scrollOffset
            if (rowY + ENTRY_HEIGHT > listTop) {
                widgets.add(ServerRow(i, entryX, rowY, ENTRY_WIDTH, ENTRY_HEIGHT))
            }
        }

        val buttonW = Button.STANDARD_WIDTH / 2
        val left = ctx.centerX(buttonW * 2 + 4)
        val rowY = ctx.guiHeight - 52
        widgets.add(Button(left, rowY, buttonW, Button.STANDARD_HEIGHT, Lang.get("multiplayer.add")) { startAdd() })
        widgets.add(Button(left + buttonW + 4, rowY, buttonW, Button.STANDARD_HEIGHT, Lang.get("multiplayer.delete")) { startDelete() })
        widgets.add(Button(left, rowY + 24, buttonW, Button.STANDARD_HEIGHT, Lang.get("multiplayer.connect")) { connectSelected() })
        widgets.add(Button(left + buttonW + 4, rowY + 24, buttonW, Button.STANDARD_HEIGHT, Lang.get("menu.back")) { onBack() })
    }

    /** Строка списка серверов: название, описание, игроки и задержка. */
    private inner class ServerRow(val index: Int, x: Int, y: Int, width: Int, height: Int) :
        Widget(x, y, width, height) {

        override fun render(ctx: UiContext) {
            val entry = servers[index]
            val selected = index == selectedIndex
            val tex = when {
                selected || contains(ctx.mouseX, ctx.mouseY) -> ctx.textures.buttonHighlighted
                else -> ctx.textures.button
            }
            ctx.gui.nineSlice(tex, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), Button.BORDER)
            ctx.font.draw(
                ctx.gui, shrink(entry.name, width - 8), (x + 4).toFloat(), (y + 2).toFloat(), 1f,
                if (selected) COLOR_SELECTED else COLOR_TEXT
            )
            ctx.font.draw(ctx.gui, infoLine(entry), (x + 4).toFloat(), (y + 12).toFloat(), 1f, COLOR_INFO)
        }

        override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
            if (button != 0 || !contains(mx, my)) return false
            // Повторный клик по выбранному серверу — подключение, как в Minecraft
            if (index == selectedIndex) connect(servers[index]) else selectedIndex = index
            return true
        }
    }

    private fun infoLine(entry: ServerEntry): String {
        val answer = pingResults[entry.address] ?: return Lang.get("multiplayer.pinging")
        return Lang.format("multiplayer.players", answer.players, answer.maxPlayers, answer.latencyMs)
    }

    /** Укорачивает строку до ширины maxWidth, добавляя многоточие. */
    private fun shrink(text: String, maxWidth: Int): String {
        if (ctx.textWidth(text) <= maxWidth) return text
        var cut = text.length
        while (cut > 1 && ctx.textWidth("${text.substring(0, cut)}…") > maxWidth) cut--
        return "${text.substring(0, cut)}…"
    }

    // ---------------- Добавление сервера ----------------

    private fun startAdd() {
        addingServer = true
        rebuildWidgets()
    }

    private fun cancelAdd() {
        addingServer = false
        rebuildWidgets()
    }

    private fun buildAddWidgets() {
        val x = ctx.centerX(FORM_WIDTH)
        var y = ctx.guiHeight / 2 - 34
        widgets.add(TextField(x, y, FORM_WIDTH, 20).apply {
            hint = Lang.get("multiplayer.name_hint")
            maxLength = 48
            nameField = this
        })
        y += 24
        widgets.add(TextField(x, y, FORM_WIDTH, 20).apply {
            hint = Lang.get("multiplayer.address_hint")
            maxLength = 64
            addressField = this
        })
        y += 26
        val buttonW = (FORM_WIDTH - 4) / 2
        widgets.add(Button(x, y, buttonW, Button.STANDARD_HEIGHT, Lang.get("multiplayer.connect")) { saveAndConnect() })
        widgets.add(Button(x + buttonW + 4, y, buttonW, Button.STANDARD_HEIGHT, Lang.get("menu.back")) { cancelAdd() })
    }

    /** Сохраняет новый сервер в список и сразу на него подключается. */
    private fun saveAndConnect() {
        val name = (nameField?.text ?: "").trim().ifEmpty { Lang.get("multiplayer.name_hint") }
        val address = (addressField?.text ?: "").trim()
        val (host, _) = HgdsClient.parseAddress(address)
        if (address.isEmpty() || host.isEmpty()) return
        val entry = ServerEntry(name, address)
        servers.add(entry)
        ServerListStorage.save(AppDirs.gameDir, servers)
        selectedIndex = servers.size - 1
        addingServer = false
        connect(entry)
    }

    // ---------------- Удаление сервера ----------------

    private fun startDelete() {
        if (selectedIndex in servers.indices) {
            confirmingDelete = true
            rebuildWidgets()
        }
    }

    private fun buildConfirmWidgets() {
        val x = ctx.centerX(Button.STANDARD_WIDTH)
        val y = ctx.guiHeight / 2
        widgets.add(Button(x, y, 98, Button.STANDARD_HEIGHT, Lang.get("menu.yes")) { deleteSelected() })
        widgets.add(Button(x + 102, y, 98, Button.STANDARD_HEIGHT, Lang.get("menu.no")) { cancelDelete() })
    }

    private fun deleteSelected() {
        if (selectedIndex in servers.indices) {
            servers.removeAt(selectedIndex)
            ServerListStorage.save(AppDirs.gameDir, servers)
            selectedIndex = if (servers.isEmpty()) -1 else 0
        }
        confirmingDelete = false
        rebuildWidgets()
    }

    private fun cancelDelete() {
        confirmingDelete = false
        rebuildWidgets()
    }

    private fun connectSelected() {
        if (selectedIndex in servers.indices) connect(servers[selectedIndex])
    }

    private fun connect(entry: ServerEntry) {
        nick = (nickField?.text ?: nick).trim().ifEmpty { Lang.get("multiplayer.player_hint") }
        stopPing()
        onConnect(entry.address, nick)
    }

    // ---------------- Рендер и ввод ----------------

    override fun renderBackground() {
        ctx.gui.tile(ctx.textures.menuBackground, 0, 0, ctx.guiWidth, ctx.guiHeight, BG_TILE)
    }

    override fun render() {
        renderBackground()
        drawCenteredText(Lang.get("multiplayer.title"), 15f)
        if (confirmingDelete && selectedIndex in servers.indices) {
            ctx.gui.fill(0f, 0f, ctx.guiWidth.toFloat(), ctx.guiHeight.toFloat(), 0f, 0f, 0f, 0.65f)
            drawCenteredText(
                Lang.format("multiplayer.confirm_delete", servers[selectedIndex].name),
                ctx.guiHeight / 2f - 24f
            )
            renderWidgets()
            return
        }
        if (addingServer) {
            drawCenteredText(Lang.get("multiplayer.name_label"), ctx.guiHeight / 2f - 48f)
            drawCenteredText(Lang.get("multiplayer.address_label"), ctx.guiHeight / 2f - 24f)
            renderWidgets()
            return
        }
        drawCenteredText(Lang.get("multiplayer.player_label"), 20f, COLOR_INFO)
        if (servers.isEmpty()) {
            drawCenteredText(Lang.get("multiplayer.empty"), listTop + 20f, COLOR_INFO)
        }
        renderWidgets()
        // Причина выхода с сервера или отказа в подключении — поверх списка
        if (notice.isNotEmpty()) {
            val text = shrink(notice, ctx.guiWidth - 16)
            val width = ctx.textWidth(text) + 8f
            val x = (ctx.guiWidth - width) / 2f
            val y = (ctx.guiHeight - 74).toFloat()
            ctx.gui.fill(x, y, width, 14f, 0f, 0f, 0f, 0.75f)
            ctx.font.draw(ctx.gui, text, x + 4f, y + 3f, 1f, COLOR_ALERT)
        }
    }

    override fun onScroll(dy: Double) {
        if (addingServer || confirmingDelete) return
        val totalHeight = servers.size * (ENTRY_HEIGHT + ENTRY_GAP)
        val visible = listBottom - listTop - ROW_SPACING
        val maxScroll = (totalHeight - visible).coerceAtLeast(0)
        scrollOffset = (scrollOffset - (dy * ENTRY_HEIGHT).toInt()).coerceIn(0, maxScroll)
    }

    override fun onEscape() {
        when {
            confirmingDelete -> cancelDelete()
            addingServer -> cancelAdd()
            else -> onBack()
        }
    }

    companion object {
        const val ENTRY_WIDTH = 220
        const val ENTRY_HEIGHT = 28
        const val ENTRY_GAP = 4
        const val ROW_SPACING = 26
        const val LIST_MARGIN_TOP = 56
        const val FORM_WIDTH = 220
        const val NICK_WIDTH = 220
        const val MAX_NICK_LENGTH = 16
        const val BG_TILE = 32
        const val PING_TIMEOUT_MS = 1200
        const val COLOR_TEXT = 0xE0E0E0
        const val COLOR_SELECTED = 0xFFFFA0
        const val COLOR_INFO = 0xA0A0A0
        const val COLOR_ALERT = 0xFFFF55
    }
}

