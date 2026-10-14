package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.Lang

/**
 * Главное меню: панорама на фоне, логотип и кнопки
 * «Одиночная игра» и «Выход из игры» (надписи — из языкового пакета).
 */
class TitleScreen(
    ctx: UiContext,
    private val onSingleplayer: () -> Unit,
    private val onMultiplayer: () -> Unit,
    private val onOptions: () -> Unit,
    private val onQuit: () -> Unit
) : Screen(ctx) {

    private var panoramaOffset = 0f

    override fun init() {
        widgets.clear()
        val buttonX = ctx.centerX(Button.STANDARD_WIDTH)
        val buttonY = ctx.guiHeight / 4 + 48
        add(
            Button(buttonX, buttonY, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("title.singleplayer")) {
                onSingleplayer()
            }
        )
        add(
            Button(
                buttonX, buttonY + 24, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT,
                Lang.get("title.multiplayer")
            ) { onMultiplayer() }
        )
        add(
            Button(buttonX, buttonY + 48, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("title.options")) {
                onOptions()
            }
        )
        add(
            Button(buttonX, buttonY + 72, Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("title.quit")) {
                onQuit()
            }
        )
    }

    override fun tick(dt: Float) {
        super.tick(dt)
        panoramaOffset += dt * 0.004f
    }

    override fun renderBackground() {
        val gui = ctx.gui
        val w = ctx.guiWidth.toFloat()
        val h = ctx.guiHeight.toFloat()
        // Панорама: квадратная текстура, медленно плывёт по горизонтали (GL_REPEAT)
        val uSpan = w / h
        gui.blitUV(ctx.textures.panorama, 0f, 0f, w, h, panoramaOffset, 0f, panoramaOffset + uSpan, 1f)
        // Виньетка поверх панорамы
        gui.blitFull(ctx.textures.panoramaOverlay, 0f, 0f, w, h)
    }

    override fun render() {
        renderBackground()
        // Логотип (minecraft.png 1024×256, пропорция 4:1)
        val logoW = (ctx.guiWidth * 0.55f).toInt().coerceIn(180, 320)
        val logoH = logoW / 4
        ctx.gui.blitFull(
            ctx.textures.logo,
            ctx.centerX(logoW).toFloat(),
            (ctx.guiHeight / 4 - logoH / 2 - 8).toFloat(),
            logoW.toFloat(), logoH.toFloat()
        )
        renderWidgets()
        // Версия слева внизу — как в Minecraft
        val version = Lang.get("title.version")
        ctx.font.draw(ctx.gui, version, 2f, ctx.guiHeight - 10f, 1f, COLOR_VERSION)
    }

    private companion object {
        const val COLOR_VERSION = 0xFFFFFF
    }
}