package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.Lang

/**
 * Меню паузы в мире: «Вернуться в игру», «Сохранить», «Выйти из мира».
 * Все надписи берутся из языкового пакета (Lang), Esc возвращает в игру.
 * В игре на сервере сохранения нет — кнопка сохраняется на сервере,
 * поэтому показывается только выход.
 */
class PauseScreen(
    ctx: UiContext,
    private val onResume: () -> Unit,
    private val onSave: () -> Unit,
    private val onOptions: () -> Unit,
    private val onQuit: () -> Unit,
    private val multiplayer: Boolean = false
) : Screen(ctx) {

    override fun init() {
        widgets.clear()
        val x = ctx.centerX(Button.STANDARD_WIDTH)
        val y0 = ctx.guiHeight / 4
        add(Button(x, y0, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("pause.resume")) { onResume() })
        add(Button(x, y0 + 24, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("pause.options")) { onOptions() })
        if (multiplayer) {
            add(
                Button(x, y0 + 48, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("pause.quit_server")) {
                    onQuit()
                }
            )
        } else {
            add(Button(x, y0 + 48, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("pause.save")) { onSave() })
            add(Button(x, y0 + 72, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("pause.quit")) { onQuit() })
        }
    }

    override fun renderBackground() {
        // Полупрозрачное затемнение поверх застывшего кадра мира
        ctx.gui.fill(0f, 0f, ctx.guiWidth.toFloat(), ctx.guiHeight.toFloat(), 0f, 0f, 0f, OVERLAY_ALPHA)
    }

    override fun onEscape() {
        onResume()
    }

    private companion object {
        const val OVERLAY_ALPHA = 0.55f
    }
}