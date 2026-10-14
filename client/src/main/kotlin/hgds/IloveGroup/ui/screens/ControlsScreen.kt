package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.GameOptions
import hgds.IloveGroup.util.Lang

/**
 * Раздел «Управление»: чувствительность мыши. Применяется сразу.
 */
class ControlsScreen(
    ctx: UiContext,
    private val onDone: () -> Unit,
    private val darkBackground: Boolean = false
) : Screen(ctx) {

    private lateinit var sensButton: Button

    override fun init() {
        widgets.clear()
        val x = ctx.centerX(Button.STANDARD_WIDTH)
        val y0 = ctx.guiHeight / 4 + 24
        sensButton = Button(x, y0, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, sensLabel()) { cycleSensitivity() }
        add(sensButton)
        add(Button(x, y0 + 36, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("options.done")) { finish() })
    }

    override fun renderBackground() {
        // Из мира — затемнение поверх кадра, из меню — фон как у других окон
        if (darkBackground) {
            ctx.gui.fill(0f, 0f, ctx.guiWidth.toFloat(), ctx.guiHeight.toFloat(), 0f, 0f, 0f, 0.55f)
        } else {
            ctx.gui.tile(ctx.textures.menuBackground, 0, 0, ctx.guiWidth, ctx.guiHeight, BG_TILE)
        }
    }

    override fun render() {
        renderBackground()
        drawCenteredText(Lang.get("options.controls"), 15f)
        renderWidgets()
    }

    private fun sensLabel(): String = Lang.format("options.sensitivity", GameOptions.sensitivity)

    private fun cycleSensitivity() {
        val values = intArrayOf(10, 25, 50, 75, 100, 150, 200, 300, 400)
        val i = (values.indexOf(GameOptions.sensitivity) + 1) % values.size
        GameOptions.sensitivity = values[i]
        sensButton.label = sensLabel()
        GameOptions.save()
    }

    private fun finish() {
        GameOptions.save()
        onDone()
    }

    override fun onEscape() {
        finish()
    }

    private companion object {
        const val BG_TILE = 32
    }
}
