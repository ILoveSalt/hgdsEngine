package hgds.IloveGroup.ui

/**
 * Ползунок в стиле Minecraft: дорожка-кнопка с подвижным рычажком,
 * значение подписано по центру. Тянется мышью слева направо.
 *
 * [value] — 0..1; [format] — подпись по текущему значению;
 * [onChange] — вызывается при перетаскивании.
 */
class Slider(
    x: Int, y: Int, width: Int, height: Int,
    private val ctx: UiContext,
    private val label: String,
    var value: Float,
    private val format: (Float) -> String,
    private val onChange: (Float) -> Unit
) : Widget(x, y, width, height) {

    private var dragging = false

    override fun tick(dt: Float) {
        if (dragging) {
            if (!ctx.window.isMouseButtonDown(0)) {
                dragging = false
            } else {
                setFromMouse(ctx.mouseX)
            }
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (button == 0 && contains(mx, my)) {
            dragging = true
            setFromMouse(mx)
            return true
        }
        return false
    }

    private fun setFromMouse(mx: Int) {
        val handleWidth = HANDLE_WIDTH
        val usable = (width - handleWidth).coerceAtLeast(1)
        value = ((mx - x - handleWidth / 2f) / usable).coerceIn(0f, 1f)
        onChange(value)
    }

    override fun render(ctx: UiContext) {
        // Дорожка
        ctx.gui.nineSlice(ctx.textures.button, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), Button.BORDER)
        // Рычажок
        val handleWidth = HANDLE_WIDTH
        val usable = (width - handleWidth).coerceAtLeast(1)
        val handleX = x + (value * usable).toInt()
        ctx.gui.nineSlice(
            ctx.textures.buttonHighlighted,
            handleX.toFloat(), y.toFloat(), handleWidth.toFloat(), height.toFloat(), Button.BORDER
        )
        // Подпись
        val text = "$label: ${format(value)}"
        val textX = x + (width - ctx.font.width(text)) / 2f
        ctx.font.draw(ctx.gui, text, textX, y + (height - 8) / 2f, 1f, Button.COLOR_NORMAL)
    }

    companion object {
        const val HANDLE_WIDTH = 16
    }
}
