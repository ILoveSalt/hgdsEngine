package hgds.IloveGroup.ui

import hgds.IloveGroup.engine.Window
import hgds.IloveGroup.render.GuiRenderer
import hgds.IloveGroup.render.font.FontRenderer
import hgds.IloveGroup.util.GameOptions

/**
 * Контекст интерфейса: окно, рендерер GUI, шрифт и текстуры.
 * Интерфейс рисуется в «пикселях игры» с автоматическим масштабом,
 * как в Minecraft: экран 1080p даёт масштаб 4, 720p — 3.
 */
class UiContext(
    val window: Window,
    val gui: GuiRenderer,
    val font: FontRenderer,
    val textures: UiTextures
) {

    /** Масштаб GUI: из настроек «Интерфейс» (Авто/1/2/3) или автоматически. */
    val guiScale: Int
        get() = GameOptions.effectiveGuiScale((window.screenHeight / 240).coerceIn(2, 4))

    /** Ширина экрана в пикселях GUI. */
    val guiWidth: Int get() = (window.screenWidth / guiScale).coerceAtLeast(320)

    /** Высота экрана в пикселях GUI. */
    val guiHeight: Int get() = (window.screenHeight / guiScale).coerceAtLeast(240)

    /** Позиция мыши в пикселях GUI (обновляется каждый кадр). */
    var mouseX: Int = 0
    var mouseY: Int = 0

    /** Координата X элемента шириной width, отцентрированного по экрану. */
    fun centerX(width: Int): Int = (guiWidth - width) / 2

    fun textWidth(text: String): Float = font.width(text)
}