package hgds.IloveGroup

import hgds.IloveGroup.util.Resources
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Чистые тесты загрузки PNG (без создания GL-текстур):
 * extractPixels/readImage работают без контекста OpenGL.
 */
class Texture2DTest {

    /** 2×1 PNG: верхний пиксель красный, нижний — синий. */
    private fun testPng(): ByteArray {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFF0000) // красный
        image.setRGB(1, 0, 0x0000FF) // синий
        val bytes = ByteArrayOutputStream()
        ImageIO.write(image, "png", bytes)
        return bytes.toByteArray()
    }

    @Test
    fun `extract pixels returns RGBA top-down`() {
        val image = BufferedImage(1, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFF0000)
        image.setRGB(0, 1, 0x0000FF)
        val pixels = hgds.IloveGroup.render.Texture2D.extractPixels(image)
        assertEquals(8, pixels.size)
        // Первый пиксель (верх): R=FF, G=00, B=00, A=FF
        assertEquals(0xFF.toByte(), pixels[0])
        assertEquals(0x00, pixels[1])
        assertEquals(0x00, pixels[2])
        assertEquals(0xFF.toByte(), pixels[3])
        // Второй пиксель (низ): B=FF
        assertEquals(0x00, pixels[4])
        assertEquals(0x00, pixels[5])
        assertEquals(0xFF.toByte(), pixels[6])
        assertEquals(0xFF.toByte(), pixels[7])
    }

    @Test
    fun `bytes per pixel constant`() {
        assertEquals(4, hgds.IloveGroup.render.Texture2D.BYTES_PER_PIXEL)
    }

    @Test
    fun `game resources exist on classpath`() {
        // Базовые ресурсы игры доступны для загрузки
        assertTrue(Resources.readText("/assets/blocks/blocks.json").contains("grass_block"))
        val image = javax.imageio.ImageIO.read(Resources.openStream("/assets/textures/game_1/s&m/sun.png"))
        assertTrue(image.width > 0)
    }
}
