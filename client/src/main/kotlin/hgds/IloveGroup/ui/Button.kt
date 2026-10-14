package hgds.IloveGroup.ui

/**
 * Кнопка в стиле Minecraft: спрайт 200×20 растягивается nine-slice'ом,
 * подпись по центру; при наведении кнопка подсвечивается, текст желтеет.
 */
open class Button(
    x: Int, y: Int, width: Int, height: Int,
    var label: String,
    private val onPress: () -> Unit
) : Widget(x, y, width, height) {

    var enabled = true

    protected var hovered = false

    override fun render(ctx: UiContext) {
        hovered = enabled && contains(ctx.mouseX, ctx.mouseY)
        val tex = when {
            !enabled -> ctx.textures.buttonDisabled
            hovered -> ctx.textures.buttonHighlighted
            else -> ctx.textures.button
        }
        ctx.gui.nineSlice(tex, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), BORDER)
        val color = when {
            !enabled -> COLOR_DISABLED
            hovered -> COLOR_HOVERED
            else -> COLOR_NORMAL
        }
        val textX = x + (width - ctx.font.width(label)) / 2f
        ctx.font.draw(ctx.gui, label, textX, y + (height - 8) / 2f, 1f, color)
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (enabled && button == 0 && contains(mx, my)) {
            onPress()
            return true
        }
        return false
    }

    companion object {
        /** Рамка nine-slice спрайта кнопки (см. button.png.mcmeta). */
        const val BORDER = 3
        const val STANDARD_WIDTH = 200
        const val STANDARD_HEIGHT = 20
        const val COLOR_NORMAL = 0xE0E0E0
        const val COLOR_HOVERED = 0xFFFFA0
        const val COLOR_DISABLED = 0xA0A0A0
    }
}