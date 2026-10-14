package hgds.IloveGroup

import hgds.IloveGroup.world.DayNightCycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Тесты цикла дня и ночи. */
class DayNightCycleTest {

    @Test
    fun `tick advances time and counts days`() {
        val cycle = DayNightCycle(timeOfDay = 0.99f, day = 1)
        val newDay = cycle.tick(DayNightCycle.DAY_LENGTH_SECONDS * 0.02f)
        assertTrue(newDay, "После перехода через полночь должен наступить новый день")
        assertEquals(2, cycle.day)
        assertTrue(cycle.timeOfDay < 0.05f)
    }

    @Test
    fun `tick with zero dt does nothing`() {
        val cycle = DayNightCycle()
        assertFalse(cycle.tick(0f))
        assertEquals(DayNightCycle.START_TIME, cycle.timeOfDay)
        assertEquals(1, cycle.day)
    }

    @Test
    fun `light level is bright at noon and dark at midnight`() {
        val noon = DayNightCycle(timeOfDay = 0.25f)
        val midnight = DayNightCycle(timeOfDay = 0.75f)
        assertTrue(noon.lightLevel() > 0.9f, "В полдень светло")
        assertEquals(DayNightCycle.NIGHT_LIGHT, midnight.lightLevel(), 0.01f, "В полночь темно")
    }

    @Test
    fun `sun direction follows east-west arc`() {
        val dawn = DayNightCycle(timeOfDay = 0f)
        val dir = dawn.sunDirection(FloatArray(3))
        assertEquals(1f, dir[0], 0.001f, "На рассвете солнце на востоке (+X)")
        assertEquals(0f, dir[1], 0.001f, "На рассвете солнце на горизонте")
    }

    @Test
    fun `clock text formats hours and minutes`() {
        assertEquals("06:00", DayNightCycle(timeOfDay = 0f).clockText())
        assertEquals("12:00", DayNightCycle(timeOfDay = 0.25f).clockText())
        assertEquals("00:00", DayNightCycle(timeOfDay = 0.75f).clockText())
    }
}
