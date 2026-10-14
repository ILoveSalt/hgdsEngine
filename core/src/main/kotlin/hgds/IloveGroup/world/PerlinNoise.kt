package hgds.IloveGroup.world

import kotlin.math.floor
import kotlin.random.Random

/**
 * Классический 2D-шум Перлина: перестановочная таблица перемешивается от seed,
 * градиенты берутся по хешу. Одинаковый seed — одинаковый шум.
 */
class PerlinNoise(seed: Long) {

    private val permutation = IntArray(PERMUTATION_SIZE * 2)

    init {
        val p = IntArray(PERMUTATION_SIZE) { it }
        val random = Random(seed)
        for (i in PERMUTATION_SIZE - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = p[i]
            p[i] = p[j]
            p[j] = tmp
        }
        for (i in 0 until PERMUTATION_SIZE * 2) {
            permutation[i] = p[i and (PERMUTATION_SIZE - 1)]
        }
    }

    /** Шум в диапазоне примерно [-1; 1]. */
    fun noise2D(x: Double, y: Double): Double {
        val xf = x - floor(x)
        val yf = y - floor(y)
        val xi = floor(x).toInt() and (PERMUTATION_SIZE - 1)
        val yi = floor(y).toInt() and (PERMUTATION_SIZE - 1)

        val u = fade(xf)
        val v = fade(yf)

        val aa = permutation[permutation[xi] + yi]
        val ab = permutation[permutation[xi] + yi + 1]
        val ba = permutation[permutation[xi + 1] + yi]
        val bb = permutation[permutation[xi + 1] + yi + 1]

        val x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf))
        val x2 = lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1))
        return lerp(v, x1, x2)
    }

    /** Фрактальный шум (сумма октав), диапазон примерно [-1; 1]. */
    fun fbm(x: Double, y: Double, octaves: Int): Double {
        var sum = 0.0
        var amplitude = 1.0
        var frequency = 1.0
        var normalization = 0.0
        for (octave in 0 until octaves) {
            sum += noise2D(x * frequency, y * frequency) * amplitude
            normalization += amplitude
            amplitude *= 0.5
            frequency *= 2.0
        }
        return sum / normalization
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)

    private fun lerp(t: Double, a: Double, b: Double): Double = a + t * (b - a)

    private fun grad(hash: Int, x: Double, y: Double): Double {
        val h = hash and GRADIENT_MASK
        val u = if (h < 4) x else y
        val v = if (h < 4) y else x
        return ((if (h and 1 == 0) u else -u) + (if (h and 2 == 0) 2.0 * v else -2.0 * v)) * 0.5
    }

    companion object {
        private const val PERMUTATION_SIZE = 256
        private const val GRADIENT_MASK = 7
    }
}