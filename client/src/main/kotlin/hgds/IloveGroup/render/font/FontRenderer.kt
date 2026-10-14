package hgds.IloveGroup.render.font

import hgds.IloveGroup.render.GuiRenderer

/**
 * Общий интерфейс шрифтов интерфейса: рисование текста квадами GUI
 * и измерение ширины строки в пикселях интерфейса.
 *
 * Реализации: [TrueTypeFont] (растровый атлас из minecraft.ttf) и
 * [BitmapFont] (атласные текстуры Minecraft ascii.png / nonlatin_european.png).
 */
abstract class FontRenderer {

    /** Ширина строки текста в пикселях GUI. */
    abstract fun width(text: String, scale: Float = 1f): Float

    /**
     * Рисует текст. (x, y) — левый верхний угол строки, y направлен вниз.
     * color — 0xRRGGBB; shadow — тень со сдвигом на 1 пиксель шрифта (как в Minecraft).
     */
    abstract fun draw(
        gui: GuiRenderer,
        text: String,
        x: Float,
        y: Float,
        scale: Float = 1f,
        color: Int = 0xFFFFFF,
        alpha: Float = 1f,
        shadow: Boolean = true
    )

    /** Высота строки в пикселях GUI. */
    open val lineHeight: Int get() = LINE_HEIGHT

    abstract fun dispose()

    /** Тень Minecraft — тот же цвет, затемнённый до 25%. */
    protected fun shadowColor(color: Int): Int {
        val r = (((color shr 16) and 0xFF) / 4) shl 16
        val g = (((color shr 8) and 0xFF) / 4) shl 8
        val b = (color and 0xFF) / 4
        return r or g or b
    }

    companion object {
        /** Базовая линия строки: отсчёт от её верхнего края (как в Minecraft). */
        const val BASELINE = 7

        /** Межстрочный интервал интерфейсного текста. */
        const val LINE_HEIGHT = 9

        /** Ширина пробела у растрового шрифта Minecraft. */
        const val SPACE_ADVANCE = 4
    }
}
