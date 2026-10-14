package hgds.IloveGroup

import hgds.IloveGroup.util.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Тесты языкового пакета. */
class LangTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `init writes default language pack`() {
        val langsDir = tempDir.resolve("langs")
        Lang.init(langsDir)
        assertTrue(Files.exists(langsDir.resolve("ru_RU.json")), "Дефолтный пакет выгружается на диск")
    }

    @Test
    fun `get returns value by key`() {
        Lang.init(tempDir.resolve("langs"))
        assertEquals("Одиночная игра", Lang.get("title.singleplayer"))
    }

    @Test
    fun `get returns key itself for unknown key`() {
        Lang.init(tempDir.resolve("langs"))
        assertEquals("no.such.key", Lang.get("no.such.key"))
    }

    @Test
    fun `format substitutes arguments`() {
        Lang.init(tempDir.resolve("langs"))
        val text = Lang.format("select_world.entry_info", "123", "Обычный")
        assertTrue(text.startsWith("Сид: 123"), "Подстановка аргументов вместо %s")
    }

    @Test
    fun `load merges custom values over defaults`() {
        val langsDir = tempDir.resolve("langs")
        Lang.init(langsDir)
        val file = langsDir.resolve("ru_RU.json")
        Files.writeString(file, """{"title.singleplayer": "Своя игра"}""")
        Lang.load(file)
        assertEquals("Своя игра", Lang.get("title.singleplayer"))
        // Остальные ключи остаются из дефолта
        assertEquals("Назад", Lang.get("menu.back"))
    }

    @Test
    fun `version string always comes from GameInfo`() {
        Lang.init(tempDir.resolve("langs"))
        assertEquals(GameInfo.DISPLAY_NAME, Lang.get("title.version"))
    }
}
