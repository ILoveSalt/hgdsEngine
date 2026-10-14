package hgds.IloveGroup

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Тесты карты символов растрового шрифта (без OpenGL — карты чистые данные).
 */
class FontTest {

    @Test
    fun `bitmap font maps cover cyrillic and latin`() {
        val all = hgds.IloveGroup.render.font.FontMaps.NONLATIN_EUROPEAN.joinToString("") +
            hgds.IloveGroup.render.font.FontMaps.ASCII.joinToString("")
        assertTrue(all.contains('А'), "Кириллица А есть в nonlatin_european")
        assertTrue(all.contains('я'), "Кириллица я есть")
        assertTrue(all.contains('A'), "Латиница есть в ascii")
        assertTrue(all.contains('0'), "Цифры есть в ascii")
    }

    @Test
    fun `ascii map has 16 rows of 16 glyphs`() {
        val ascii = hgds.IloveGroup.render.font.FontMaps.ASCII
        assertTrue(ascii.size == 16, "ascii.png — 16 строк")
        ascii.forEach { assertTrue(it.length == 16, "Каждая строка — 16 глифов") }
    }

    @Test
    fun `nonlatin map has 67 rows of 16 glyphs`() {
        val map = hgds.IloveGroup.render.font.FontMaps.NONLATIN_EUROPEAN
        assertTrue(map.size == 67, "nonlatin_european.png — 67 строк")
        map.forEach { assertTrue(it.length == 16, "Каждая строка — 16 глифов") }
    }
}
