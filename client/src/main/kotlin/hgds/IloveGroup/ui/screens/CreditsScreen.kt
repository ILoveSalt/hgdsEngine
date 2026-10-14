package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.Lang

/**
 * «Титры и авторы»: прокручивающийся список авторов и благодарностей,
 * как титры в Minecraft. Esc или «Готово» — выход.
 */
class CreditsScreen(
    ctx: UiContext,
    private val onDone: () -> Unit,
    private val darkBackground: Boolean = false
) : Screen(ctx) {

    private var offset = 0f

    override fun init() {
        widgets.clear()
        val x = ctx.centerX(Button.STANDARD_WIDTH)
        add(
            Button(x, ctx.guiHeight - 30, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("options.done")) {
                onDone()
            }
        )
    }

    override fun tick(dt: Float) {
        super.tick(dt)
        offset += dt * 12f // пикселей GUI в секунду
    }

    override fun renderBackground() {
        if (darkBackground) {
            ctx.gui.fill(0f, 0f, ctx.guiWidth.toFloat(), ctx.guiHeight.toFloat(), 0f, 0f, 0f, 0.85f)
        } else {
            ctx.gui.fill(0f, 0f, ctx.guiWidth.toFloat(), ctx.guiHeight.toFloat(), 0f, 0f, 0f, 1f)
        }
    }

    override fun render() {
        renderBackground()
        val lines = Lang.get("credits.lines").split("\n")
        var y = ctx.guiHeight.toFloat() - offset
        for (line in lines) {
            if (y > -12f && y < ctx.guiHeight + 12f) {
                drawCenteredText(line, y, COLOR_TEXT)
            }
            y += 12f
        }
        renderWidgets()
    }

    override fun onScroll(dy: Double) {
        offset = (offset - (dy * 8.0).toFloat()).coerceAtLeast(0f)
    }

    private companion object {
        const val COLOR_TEXT = 0xE0E0E0
    }
}
