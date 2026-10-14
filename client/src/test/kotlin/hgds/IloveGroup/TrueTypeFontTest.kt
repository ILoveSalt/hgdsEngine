package hgds.IloveGroup

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO

/**
 * TrueType-шрифт создаёт GL-текстуру, поэтому здесь проверяется только
 * наличие подключаемого файла minecraft.ttf на classpath (без OpenGL).
 */
class TrueTypeFontTest {

    @Test
    fun `minecraft ttf resource exists`() {
        val stream = javaClass.getResourceAsStream("/assets/font/minecraft.ttf")
            ?: javaClass.getResourceAsStream("/font/minecraft.ttf")
            ?: javaClass.getResourceAsStream("/minecraft.ttf")
        assertTrue(stream != null, "Файл шрифта minecraft.ttf доступен на classpath")
        stream!!.use { assertTrue(it.read() >= 0) }
    }

    @Test
    fun `ttf loads as java font`() {
        val stream = javaClass.getResourceAsStream("/assets/font/minecraft.ttf")
            ?: javaClass.getResourceAsStream("/font/minecraft.ttf")
            ?: javaClass.getResourceAsStream("/minecraft.ttf")
            ?: return
        val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream)
        assertTrue(font.size > 0)
    }

    @Test
    fun `font can rasterize cyrillic`() {
        val stream = javaClass.getResourceAsStream("/assets/font/minecraft.ttf")
            ?: javaClass.getResourceAsStream("/font/minecraft.ttf")
            ?: javaClass.getResourceAsStream("/minecraft.ttf")
            ?: return
        val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream).deriveFont(16f)
        val image = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.font = font
        val metrics: java.awt.FontMetrics = g.getFontMetrics(font)
        assertTrue(metrics.stringWidth("Привет") > 0, "Шрифт содержит кириллицу")
        g.dispose()
        ImageIO.setUseCache(false)
    }
}
