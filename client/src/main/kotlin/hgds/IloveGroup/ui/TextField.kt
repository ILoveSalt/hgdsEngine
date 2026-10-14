package hgds.IloveGroup.ui

import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_V

/**
 * Поле ввода текста в стиле Minecraft (имя мира и сид при создании мира).
 * Ввод работает с любой раскладкой клавиатуры, поддерживаются Backspace
 * и вставка из буфера обмена (Ctrl+V).
 */
class TextField(
    x: Int, y: Int, width: Int, height: Int = 20
) : Widget(x, y, width, height) {

    var text = ""
    var hint = ""
    var focused = false
    var maxLength = 32
    var numericOnly = false

    private var caretVisible = true
    private var caretTimer = 0f

    override fun tick(dt: Float) {
        caretTimer += dt
        if (caretTimer >= 0.5f) {
            caretTimer = 0f
            caretVisible = !caretVisible
        }
    }

    override fun render(ctx: UiContext) {
        val tex = if (focused) ctx.textures.textFieldHighlighted else ctx.textures.textField
        ctx.gui.nineSlice(tex, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), BORDER)
        val textX = x + 4f
        val textY = y + (height - 8) / 2f
        if (text.isEmpty()) {
            ctx.font.draw(ctx.gui, hint, textX, textY, 1f, COLOR_HINT, shadow = false)
        } else {
            val shown = if (focused && caretVisible) text + "_" else text
            ctx.font.draw(ctx.gui, shown, textX, textY, 1f, COLOR_TEXT)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        focused = contains(mx, my)
        return focused
    }

    override fun charTyped(codepoint: Int) {
        if (!focused) return
        if (text.length >= maxLength) return
        val c = codepoint.toChar()
        if (c.code < 32 || c.code == 127) return
        if (numericOnly && !c.isDigit() && c != '-') return
        text += c
    }

    override fun keyPressed(key: Int, ctrlDown: Boolean, ctx: UiContext): Boolean {
        if (!focused) return false
        when (key) {
            GLFW_KEY_BACKSPACE -> {
                if (text.isNotEmpty()) text = text.dropLast(1)
                return true
            }
            GLFW_KEY_V -> if (ctrlDown) {
                val clipboard = ctx.window.clipboardText() ?: return true
                for (c in clipboard) {
                    if (text.length >= maxLength) break
                    if (c.code < 32 || c.code == 127) continue
                    if (numericOnly && !c.isDigit() && c != '-') continue
                    text += c
                }
                return true
            }
        }
        return false
    }

    companion object {
        /** Рамка nine-slice спрайта текстового поля (см. text_field.png.mcmeta). */
        const val BORDER = 1
        const val COLOR_TEXT = 0xE0E0E0
        const val COLOR_HINT = 0x707070
    }
}