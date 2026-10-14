package hgds.IloveGroup.world

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Цикл дня и ночи: время суток, счётчик дней, цвет неба и освещение.
 *
 * Время хранится как [timeOfDay] в диапазоне 0..1:
 *   0.0  — рассвет (06:00), солнце на горизонте (восток, +X);
 *   0.25 — полдень (12:00), солнце в зените;
 *   0.5  — закат (18:00), солнце садится (запад, -X);
 *   0.75 — полночь (00:00), луна в зените.
 *
 * Полный день длится [DAY_LENGTH_SECONDS] реальных секунд.
 * Значения сохраняются в level.json (timeOfDay, day) и восстанавливаются при загрузке мира.
 */
class DayNightCycle(timeOfDay: Float = START_TIME, day: Int = 1) {

    /** Доля суток 0..1. */
    var timeOfDay = timeOfDay.coerceIn(0f, 1f)
        private set

    /** Номер текущего дня (начинается с 1). */
    var day = if (day < 1) 1 else day
        private set

    /**
     * Обновляет время. Возвращает true, если наступил новый день
     * (удобно для будущих событий «на рассвете»).
     */
    fun tick(dt: Float): Boolean {
        if (dt <= 0f) return false
        val prev = timeOfDay
        timeOfDay += dt / DAY_LENGTH_SECONDS
        return if (timeOfDay >= 1f) {
            timeOfDay -= 1f
            day++
            // Защита от пропуска дней при огромном dt (не должно случаться, но пусть будет)
            prev > timeOfDay
        } else {
            false
        }
    }

    /** Угол солнца над горизонтом в радианах: 0 — восход, PI/2 — зенит, PI — закат. */
    val sunAngle: Float
        get() = timeOfDay * 2f * PI.toFloat()

    /** Направление на солнце в мире (единичный вектор, Y — вверх).
     *  Светило ходит строго по линии восток (+X) — запад (-X) через зенит. */
    fun sunDirection(out: FloatArray): FloatArray {
        val a = sunAngle
        out[0] = cos(a)
        out[1] = sin(a)
        out[2] = 0f // без наклона: ровный путь с востока на запад
        return out
    }

    /**
     * Уровень освещения мира 0..1 (1 — день, [NIGHT_LIGHT] — ночь).
     * Плавные сумерки в течение [TWILIGHT] суток вокруг восхода/заката.
     */
    fun lightLevel(): Float {
        val elevation = sin(sunAngle) // <0 — ночь
        // Мягкая синусоида: 1 в полдень, NIGHT_LIGHT в полночь, резкий переход у горизонта
        val t = elevation.coerceIn(-1f, 1f)
        val smooth = (t + TWILIGHT) / (2f * TWILIGHT) // 0..1 в зоне сумерек
        val clamped = smooth.coerceIn(0f, 1f)
        return NIGHT_LIGHT + (1f - NIGHT_LIGHT) * clamped
    }

    /** Цвет неба (r, g, b) для очистки экрана — день/закат/ночь. */
    fun skyColor(out: FloatArray): FloatArray {
        val t = sin(sunAngle) // высота солнца: 1 полдень, 0 горизонт, -1 полночь
        val dayW = t.coerceIn(0f, 1f)
        val nightW = (-t).coerceIn(0f, 1f)
        val horizonW = 1f - dayW - nightW // у горизонта

        out[0] = DAY_SKY[0] * dayW + NIGHT_SKY[0] * nightW + SUNSET_SKY[0] * horizonW
        out[1] = DAY_SKY[1] * dayW + NIGHT_SKY[1] * nightW + SUNSET_SKY[1] * horizonW
        out[2] = DAY_SKY[2] * dayW + NIGHT_SKY[2] * nightW + SUNSET_SKY[2] * horizonW
        return out
    }

    /** Часы суток для F3: «HH:MM». */
    fun clockText(): String {
        val hours = (timeOfDay * 24f + 6f) % 24f // 0 суток = 06:00
        val h = hours.toInt()
        val m = ((hours - h) * 60f).toInt()
        return "%02d:%02d".format(h, m)
    }

    companion object {
        /** Полный день в реальных секундах (10 минут, как в Minecraft). */
        const val DAY_LENGTH_SECONDS = 600f

        /** Начальное время новых миров — утро (06:00). */
        const val START_TIME = 0f

        /** Освещение мира в ночи (не делаем чёрным, чтобы играть было возможно). */
        const val NIGHT_LIGHT = 0.25f

        /** Полдня сумерек (доля суток) вокруг горизонта солнца. */
        const val TWILIGHT = 0.12f

        /** Небо ясного дня (как прежний glClearColor). */
        private val DAY_SKY = floatArrayOf(0.53f, 0.74f, 0.92f)

        /** Небо ночью. */
        private val NIGHT_SKY = floatArrayOf(0.015f, 0.02f, 0.06f)

        /** Небо на горизонте солнца (закат/рассвет, тёплый оранжевый). */
        private val SUNSET_SKY = floatArrayOf(0.86f, 0.52f, 0.28f)
    }
}