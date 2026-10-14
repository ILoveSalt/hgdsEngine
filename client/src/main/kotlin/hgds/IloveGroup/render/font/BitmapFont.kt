package hgds.IloveGroup.render.font

import hgds.IloveGroup.render.GuiRenderer
import hgds.IloveGroup.render.Texture2D

/**
 * Растровый шрифт в стиле Minecraft: ascii.png (латиница), nonlatin_european.png
 * (кириллица и другие алфавиты) и accented.png (символы с диакритикой).
 *
 * Карты символов — FontMaps (сгенерированы из default.json Minecraft 1.21.4),
 * ширина глифа определяется по пикселям текстуры, тень рисуется как в игре.
 */
class BitmapFont private constructor(
    private val textures: List<Texture2D>,
    private val glyphs: Map<Char, Glyph>
) : FontRenderer() {

    /** Глиф: фрагмент текстуры (в пикселях) + метрики. */
    class Glyph(
        val textureIndex: Int,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val width: Int,
        val advance: Int,
        val yOffset: Int
    )

    /** Ширина строки текста в пикселях GUI. */
    override fun width(text: String, scale: Float): Float {
        var w = 0f
        for (c in text) w += advanceOf(c) * scale
        return w
    }

    private fun advanceOf(c: Char): Int {
        if (c == ' ') return SPACE_ADVANCE
        return glyphs[c]?.advance ?: (SPACE_ADVANCE + 1)
    }

    /**
     * Рисует текст. (x, y) — левый верхний угол строки, y направлен вниз.
     * color — 0xRRGGBB; shadow — тень со сдвигом на 1 пиксель шрифта (как в Minecraft).
     */
    override fun draw(
        gui: GuiRenderer,
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Int,
        alpha: Float,
        shadow: Boolean
    ) {
        if (shadow) drawLayer(gui, text, x + scale, y + scale, scale, shadowColor(color), alpha)
        drawLayer(gui, text, x, y, scale, color, alpha)
    }

    private fun drawLayer(gui: GuiRenderer, text: String, x: Float, y: Float, scale: Float, color: Int, alpha: Float) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        var cursor = x
        for (c in text) {
            if (c == ' ') {
                cursor += SPACE_ADVANCE * scale
                continue
            }
            val glyph = glyphs[c]
            if (glyph == null || glyph.width == 0) {
                cursor += (SPACE_ADVANCE + 1) * scale
                continue
            }
            gui.blit(
                textures[glyph.textureIndex],
                cursor, y + glyph.yOffset * scale,
                glyph.width * scale, (glyph.v1 - glyph.v0) * scale,
                glyph.u0, glyph.v0, glyph.u1, glyph.v1,
                r, g, b, alpha
            )
            cursor += glyph.advance * scale
        }
    }

    override fun dispose() {
        for (tex in textures) tex.dispose()
    }

    companion object {
        const val SPACE_ADVANCE = 4
        const val BASELINE = 7
        const val LINE_HEIGHT = 9

        /**
         * Загружает шрифт из текстур classpath: font/ascii.png,
         * font/nonlatin_european.png и font/accented.png.
         * Порядок провайдеров совпадает с default.json: первый найденный символ выигрывает.
         */
        fun load(): BitmapFont {
            val providers = listOf(
                Provider("/font/nonlatin_european.png", FontMaps.NONLATIN_EUROPEAN, ascent = 7),
                Provider("/font/accented.png", FontMaps.ACCENTED, ascent = 10),
                Provider("/font/ascii.png", FontMaps.ASCII, ascent = 7)
            )
            val textures = ArrayList<Texture2D>()
            val glyphs = HashMap<Char, Glyph>()
            for (provider in providers) {
                val image = Texture2D.readImage(provider.resource)
                val pixels = Texture2D.readPixelsTopDown(provider.resource)
                val texW = image.width
                val texH = image.height
                val rows = provider.rows.size
                val cols = provider.rows[0].length
                val cellW = texW / cols
                val cellH = texH / rows
                val textureIndex = textures.size
                textures.add(Texture2D.load(provider.resource))
                for (rowIdx in 0 until rows) {
                    val row = provider.rows[rowIdx]
                    for (colIdx in 0 until row.length) {
                        val c = row[colIdx]
                        if (c.code == 0) continue
                        if (glyphs.containsKey(c)) continue
                        val cellX = colIdx * cellW
                        val cellY = rowIdx * cellH
                        // Ширина глифа — самая правая непрозрачная колонка в ячейке
                        var glyphWidth = 0
                        for (gy in 0 until cellH) {
                            for (gx in 0 until cellW) {
                                val p = pixels[(cellY + gy) * texW + (cellX + gx)]
                                if (((p ushr 24) and 0xFF) != 0 && gx + 1 > glyphWidth) glyphWidth = gx + 1
                            }
                        }
                        glyphs[c] = Glyph(
                            textureIndex,
                            cellX.toFloat(), cellY.toFloat(),
                            (cellX + cellW).toFloat(), (cellY + cellH).toFloat(),
                            glyphWidth,
                            glyphWidth + 1,
                            BASELINE - provider.ascent
                        )
                    }
                }
            }
            return BitmapFont(textures, glyphs)
        }

        private class Provider(val resource: String, val rows: Array<String>, val ascent: Int)
    }
}