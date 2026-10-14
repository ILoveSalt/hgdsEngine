package hgds.IloveGroup.ui

import hgds.IloveGroup.net.ChatKind
import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER

/**
 * Игровой чат: последние сообщения внизу экрана и строка ввода, открываемая
 * по T. Сообщения исчезают через [Line_LIFETIME] секунд, открытое окно чата
 * держит все строки на экране.
 */
class ChatHud {

    /** Строка чата: отправитель, текст, вид и возраст в секундах. */
    data class Line(val sender: String, val text: String, val kind: ChatKind, var age: Float = 0f)

    private val lines = ArrayList<Line>()

    /** Открыта ли строка ввода чата. */
    var isOpen = false
        private set

    /** Набранный текст. */
    var input = ""
        private set

    /** Непрочитанных (новых) сообщений с прошлого открытия — для подсказки. */
    var unread = 0

    val visibleLines: List<Line> get() = lines

    fun add(sender: String, text: String, kind: ChatKind = ChatKind.CHAT) {
        lines.add(Line(sender, text, kind))
        while (lines.size > MAX_LINES) lines.removeAt(0)
        if (!isOpen) unread++
    }

    fun open() {
        isOpen = true
        input = ""
        unread = 0
    }

    fun close() {
        isOpen = false
        input = ""
    }

    /** Ввод символа в строку чата. */
    fun typeChar(codepoint: Int) {
        if (!isOpen) return
        val c = codepoint.toChar()
        if (c.code < 32 || c.code == 127) return
        if (input.length >= MAX_INPUT_LENGTH) return
        input += c
    }

    fun backspace() {
        if (isOpen && input.isNotEmpty()) input = input.dropLast(1)
    }

    /**
     * Отправляет набранное сообщение: возвращает текст (пустой — если отправлять
     * нечего) и закрывает строку ввода.
     */
    fun submit(): String {
        val message = input.trim()
        close()
        return message
    }

    /** true, если Enter нажат в открытом чате (сообщение нужно отправить). */
    fun isEnterKey(key: Int): Boolean = key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER

    fun isBackspaceKey(key: Int): Boolean = key == GLFW_KEY_BACKSPACE

    fun tick(dt: Float) {
        for (line in lines) line.age += dt
        if (isOpen) return
        lines.removeAll { it.age > LINE_LIFETIME }
        if (lines.isEmpty()) unread = 0
    }

    /** Отриска сообщений и строки ввода (в координатах GUI). */
    fun render(ctx: UiContext) {
        var bottom = ctx.guiHeight.toFloat() - INPUT_HEIGHT - 4f
        val shown = lines.size.coerceAtMost(VISIBLE_LINES)
        for (i in lines.size - shown until lines.size) {
            val line = lines[i]
            val text = format(line)
            val width = (ctx.textWidth(text) + 4f).coerceAtMost((ctx.guiWidth - 8).toFloat())
            ctx.gui.fill(2f, bottom - TEXT_HEIGHT, width, TEXT_HEIGHT, 0f, 0f, 0f, BACKGROUND_ALPHA)
            ctx.font.draw(ctx.gui, text, 4f, bottom - TEXT_HEIGHT + 3f, 1f, color(line.kind))
            bottom -= TEXT_HEIGHT
        }
        if (isOpen) {
            val fieldY = ctx.guiHeight.toFloat() - INPUT_HEIGHT
            ctx.gui.fill(2f, fieldY, (ctx.guiWidth - 4).toFloat(), INPUT_HEIGHT, 0f, 0f, 0f, 0.75f)
            ctx.gui.nineSlice(
                ctx.textures.textField, 2f, fieldY, (ctx.guiWidth - 4).toFloat(), INPUT_HEIGHT,
                TextField.BORDER
            )
            ctx.font.draw(ctx.gui, "$PROMPT${input}_", 6f, fieldY + 5f, 1f, TextField.COLOR_TEXT)
        }
    }

    private fun format(line: Line): String = when (line.kind) {
        ChatKind.CHAT -> if (line.sender.isEmpty()) line.text else "<${line.sender}> ${line.text}"
        else -> line.text
    }

    private fun color(kind: ChatKind): Int = when (kind) {
        ChatKind.CHAT -> COLOR_CHAT
        ChatKind.SYSTEM -> COLOR_SYSTEM
        ChatKind.JOIN_LEAVE -> COLOR_JOIN_LEAVE
        ChatKind.ACTION -> COLOR_ACTION
    }

    companion object {
        const val MAX_LINES = 64
        const val VISIBLE_LINES = 10
        const val TEXT_HEIGHT = 11f
        const val INPUT_HEIGHT = 20f
        const val MAX_INPUT_LENGTH = 100
        const val LINE_LIFETIME = 15f
        const val BACKGROUND_ALPHA = 0.45f
        const val PROMPT = ">"
        const val COLOR_CHAT = 0xE0E0E0
        const val COLOR_SYSTEM = 0xFFFF55
        const val COLOR_JOIN_LEAVE = 0xA0A0A0
        const val COLOR_ACTION = 0xFFAAAA
    }
}
