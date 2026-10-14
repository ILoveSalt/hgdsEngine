package hgds.IloveGroup.render.font

import hgds.IloveGroup.render.GuiRenderer
import hgds.IloveGroup.render.Texture2D
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Шрифт интерфейса из TrueType-файла `minecraft.ttf` (пиксельный «Minecraft Rus»).
 *
 * При загрузке шрифт один раз растеризуется в атлас глифов (латиница, цифры,
 * кириллица, частая пунктуация), дальше текст рисуется теми же квадами GUI,
 * что и остальной интерфейс: `minecraft.ttf` подключается через
 * [Font.createFont] и не требует установки в Windows.
 *
 * Размер подбирается так, чтобы верхняя часть строки (ascent) не превышала
 * базовую линию растрового шрифта ([FontRenderer.BASELINE] = 7), поэтому
 * вёрстка экранов при смене шрифта не меняется, а ширины берутся
 * из метрик самого шрифта.
 */
class TrueTypeFont private constructor(
    private val texture: Texture2D,
    private val glyphs: Map<Char, Glyph>,
    private val spaceAdvance: Int,
    private val cellHeight: Int
) : FontRenderer() {

    /** Глиф: ячейка атласа (в пикселях текстуры) + метрики. */
    class Glyph(
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val width: Int,
        val advance: Int,
        val yOffset: Int
    )

    override fun width(text: String, scale: Float): Float {
        var w = 0f
        for (c in text) w += advanceOf(c) * scale
        return w
    }

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

    /** Ширина символа по метрикам шрифта. */
    private fun advanceOf(c: Char): Int = when {
        c == ' ' -> spaceAdvance
        else -> glyphs[c]?.advance ?: (spaceAdvance + 1)
    }

    private fun drawLayer(
        gui: GuiRenderer,
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Int,
        alpha: Float
    ) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        var cursor = x
        for (c in text) {
            if (c == ' ') {
                cursor += spaceAdvance * scale
                continue
            }
            val glyph = glyphs[c]
            if (glyph == null) {
                cursor += (spaceAdvance + 1) * scale
                continue
            }
            // Пиксельный шрифт должен попадать в пиксели экрана — округляем X вниз
            gui.blit(
                texture,
                floor(cursor), y + glyph.yOffset,
                glyph.width * scale, cellHeight * scale,
                glyph.u0, glyph.v0, glyph.u1, glyph.v1,
                r, g, b, alpha
            )
            cursor += glyph.advance * scale
        }
    }

    override fun dispose() {
        texture.dispose()
    }

    /** Результат растеризации шрифта: атлас + метрики (без OpenGL, тестируется напрямую). */
    class Atlas(
        val image: BufferedImage,
        val glyphs: Map<Char, Glyph>,
        val spaceAdvance: Int,
        val cellHeight: Int
    )

    companion object {
        /** Ресурс шрифта в classpath. */
        const val RESOURCE = "/minecraft.ttf"

        /** Колонок ячеек в атласе. */
        const val ATLAS_COLUMNS = 16

        /** Символы, попадающие в атлас: ASCII + кириллица + частая пунктуация. */
        val CHARS: CharArray = buildChars()

        private fun buildChars(): CharArray {
            val chars = LinkedHashSet<Char>()
            for (c in '!'..'~') chars.add(c)
            for (c in '\u0410'..'\u042F') chars.add(c) // А..Я
            for (c in '\u0430'..'\u044F') chars.add(c) // а..я
            chars.add('\u0401')
            chars.add('\u0451')             // Ё ё
            chars.addAll(
                listOf(
                    '\u00AB', '\u00BB', '\u2014', '\u2013', '\u2026', '\u00B7',
                    '\u00B0', '\u00B1', '\u20AC', '\u2116', '\u00B5'
                )
            )
            chars.remove(' ') // пробел — только по ширине-метру
            return chars.toCharArray()
        }

        /**
         * Подбирает размер шрифта, при котором ascent не больше базовой линии
         * интерфейса: строка занимает те же 9 пикселей GUI, что и растровый шрифт.
         */
        private fun fitSize(font: Font, maxAscent: Int): Font {
            val graphics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).graphics
            try {
                for (size in 20 downTo 4) {
                    val candidate = font.deriveFont(size.toFloat())
                    if (graphics.getFontMetrics(candidate).ascent <= maxAscent) return candidate
                }
                return font.deriveFont(8f)
            } finally {
                graphics.dispose()
            }
        }

        /**
         * Растеризует шрифт в атлас глифов. Метод не трогает OpenGL,
         * поэтому проверяется тестами без контекста (TrueTypeFontTest).
         */
        fun rasterize(ttfBytes: ByteArray, baseline: Int = FontRenderer.BASELINE): Atlas {
            val created = Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(ttfBytes))
            val font = fitSize(created, baseline)

            val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).graphics
            val metrics: FontMetrics = probe.getFontMetrics(font)
            val ascent = metrics.ascent
            val cellHeight = max(1, ascent + metrics.descent)

            var cellWidth = metrics.charWidth('W') + 2
            for (c in CHARS) cellWidth = max(cellWidth, metrics.charWidth(c) + 1)
            cellWidth = max(cellWidth, 1)

            val rows = (CHARS.size + ATLAS_COLUMNS - 1) / ATLAS_COLUMNS
            val atlas = BufferedImage(
                ATLAS_COLUMNS * cellWidth,
                max(1, rows) * cellHeight,
                BufferedImage.TYPE_INT_ARGB
            )
            val g = atlas.createGraphics()
            g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            )
            g.font = font
            g.color = Color.WHITE

            val glyphs = HashMap<Char, Glyph>()
            for (index in CHARS.indices) {
                val c = CHARS[index]
                val cellX = (index % ATLAS_COLUMNS) * cellWidth
                val cellY = (index / ATLAS_COLUMNS) * cellHeight
                g.drawString(c.toString(), cellX, cellY + ascent)
                glyphs[c] = Glyph(
                    cellX.toFloat(), cellY.toFloat(),
                    (cellX + cellWidth).toFloat(), (cellY + cellHeight).toFloat(),
                    cellWidth,
                    max(1, metrics.charWidth(c)),
                    baseline - ascent
                )
            }
            g.dispose()
            probe.dispose()

            val spaceAdvance = max(1, ceil(metrics.charWidth(' ').toDouble()).toInt())
            return Atlas(atlas, glyphs, spaceAdvance, cellHeight)
        }

        /** Растеризует шрифт из ресурса (внешний файл в папке игры или classpath). */
        fun rasterizeResource(resource: String = RESOURCE): Atlas {
            val stream = hgds.IloveGroup.util.Resources.openStream(resource)
            return rasterize(stream.use { it.readBytes() })
        }

        /** Загружает шрифт: растеризация атласа + загрузка текстуры в OpenGL. */
        fun load(resource: String = RESOURCE): TrueTypeFont {
            val atlas = rasterizeResource(resource)
            return TrueTypeFont(
                Texture2D.fromImage(atlas.image),
                atlas.glyphs,
                atlas.spaceAdvance,
                atlas.cellHeight
            )
        }
    }
}



