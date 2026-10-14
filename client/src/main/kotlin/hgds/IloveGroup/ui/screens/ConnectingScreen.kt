package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.Lang

/**
 * Экран ожидания ответа сервера: показывается, пока фоновый поток выполняет
 * подключение и рукопожатие. Отмена закрывает соединение по таймауту.
 */
class ConnectingScreen(
    ctx: UiContext,
    private val address: String,
    private val onCancel: () -> Unit
) : Screen(ctx) {

    override fun init() {
        widgets.clear()
    }

    override fun renderBackground() {
        ctx.gui.tile(ctx.textures.menuBackground, 0, 0, ctx.guiWidth, ctx.guiHeight, BG_TILE)
    }

    override fun render() {
        renderBackground()
        drawCenteredText(Lang.get("multiplayer.connecting"), ctx.guiHeight / 2f - 16f)
        drawCenteredText(address, ctx.guiHeight / 2f + 2f, 0xA0A0A0)
        drawCenteredText(Lang.get("multiplayer.connect_hint"), ctx.guiHeight / 2f + 26f, 0xA0A0A0)
    }

    override fun onEscape() {
        onCancel()
    }

    companion object {
        const val BG_TILE = 32
    }
}
