package hgds.IloveGroup.world

import kotlin.math.roundToInt

/**
 * Генератор рельефа: высота поверхности из шума Перлина (крупные холмы + детали),
 * три слоя блока: трава сверху, ниже — земля, глубже — камень.
 * Seed выбирается случайно при каждом запуске игры, но генерация детерминирована:
 * один и тот же seed всегда даёт один и тот же мир.
 */
class TerrainGenerator(
    val seed: Long,
    private val grassIndex: Int,
    private val dirtIndex: Int,
    private val stoneIndex: Int,
    private val flat: Boolean = false,
    /** Индекс блока древесины (дуб); < 0 — деревья не генерируются. */
    private val logIndex: Int = -1,
    /** Индекс блока листвы; < 0 — деревья не генерируются. */
    private val leavesIndex: Int = -1
) {

    private val noise = PerlinNoise(seed)

    /**
     * Зона леса: отдельный крупномасштабный шум выделяет области (биомы),
     * в которых растут деревья. Вне зоны леса — открытая равнина.
     */
    private fun isForest(x: Int, z: Int): Boolean =
        noise.fbm(x * FOREST_SCALE + 9000.0, z * FOREST_SCALE - 4000.0, FOREST_OCTAVES) > FOREST_THRESHOLD

    /** Высота поверхности (Y верхнего блока травы) в точке (x, z). */
    fun height(x: Int, z: Int): Int {
        if (flat) return FLAT_SURFACE
        val continents = noise.fbm(x * 0.0035, z * 0.0035, OCTAVES_LARGE)
        val detail = noise.fbm(x * 0.02 + 512.0, z * 0.02 + 512.0, OCTAVES_SMALL)
        val h = BASE_HEIGHT + (continents * 0.8 + detail * 0.2) * HEIGHT_AMPLITUDE
        return h.roundToInt().coerceIn(MIN_HEIGHT, MAX_HEIGHT)
    }

    /** Блок по глубине: трава на поверхности, затем земля, глубже — камень. */
    fun blockFor(y: Int, surface: Int): Int = when {
        y > surface -> BlockRegistry.AIR
        y == surface -> grassIndex
        y >= surface - DIRT_DEPTH -> dirtIndex
        else -> stoneIndex
    }

    /** Заполняет колонку чанка рельефом (секции создаются по мере надобности). */
    fun generateColumn(column: ChunkColumn) {
        val originX = column.cx * World.CHUNK_SIZE_X
        val originZ = column.cz * World.CHUNK_SIZE_Z
        for (lx in 0 until World.CHUNK_SIZE_X) {
            for (lz in 0 until World.CHUNK_SIZE_Z) {
                val surface = height(originX + lx, originZ + lz)
                for (y in 0..surface) {
                    val sy = y / World.SECTION_SIZE
                    val section = column.sections[sy]
                        ?: Section(column.cx, sy, column.cz).also { column.sections[sy] = it }
                    section.set(lx, y % World.SECTION_SIZE, lz, blockFor(y, surface))
                }
            }
        }
        // Деревья: детерминированы по мировым координатам, поэтому кроны,
        // выходящие за границу чанка, одинаково достроятся в соседней колонке.
        // Смотрим позиции стволов с запасом ±CANOPY_RADIUS вокруг колонки.
        if (hasTrees && !flat) {
            for (tx in originX - CANOPY_RADIUS until originX + World.CHUNK_SIZE_X + CANOPY_RADIUS) {
                for (tz in originZ - CANOPY_RADIUS until originZ + World.CHUNK_SIZE_Z + CANOPY_RADIUS) {
                    if (treeAt(tx, tz)) placeTree(column, tx, tz)
                }
            }
        }
    }

    // ----- Деревья -----

    private val hasTrees: Boolean get() = logIndex > 0 && leavesIndex > 0

    /** Детерминированный хеш мировой позиции (зависит от seed мира). */
    private fun hash(x: Int, z: Int): Int {
        var h = (x * 374761393) xor (z * 668265263) xor (seed * 981039).toInt()
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    /**
     * Есть ли дерево со стволом в (x, z). Дерево стоит там, где хеш позиции —
     * строгий минимум среди всех позиций в радиусе TREE_SPACING, поэтому
     * деревья гарантированно не слипаются и распределены равномерно.
     */
    private fun treeAt(x: Int, z: Int): Boolean {
        // Деревья только в зоне леса
        if (!isForest(x, z)) return false
        val h = hash(x, z)
        for (dx in -TREE_SPACING..TREE_SPACING) {
            for (dz in -TREE_SPACING..TREE_SPACING) {
                if (dx == 0 && dz == 0) continue
                if (hash(x + dx, z + dz) <= h) return false
            }
        }
        return true
    }

    /** Высота ствола дерева в (x, z): 4–6 блоков. */
    private fun trunkHeight(x: Int, z: Int): Int = 4 + (hash(x * 3 + 7, z * 5 + 11) and 0x7) % 3

    /** Ставит дерево: ствол из земли + крона из листвы. */
    private fun placeTree(column: ChunkColumn, x: Int, z: Int) {
        val base = height(x, z) + 1
        if (base + 8 >= World.WORLD_HEIGHT) return
        val top = base + trunkHeight(x, z) - 1

        // Ствол
        for (y in base..top) setInColumn(column, x, y, z, logIndex, onlyAir = false)

        // Крона: два широких слоя (радиус 2) вокруг верха ствола,
        // затем узкий слой (радиус 1) и шапка над стволом
        for (y in top - 2..top + 1) {
            val radius = if (y >= top) 1 else 2
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx == 0 && dz == 0 && y <= top) continue // внутри ствола
                    val corner = kotlin.math.abs(dx) == radius && kotlin.math.abs(dz) == radius
                    // Углы широких слоёв срезаем детерминированно — крона выглядит живой
                    if (corner && (y >= top || hash(x + dx * 17 + y, z + dz * 31 + y) % 2 == 0)) continue
                    setInColumn(column, x + dx, y, z + dz, leavesIndex, onlyAir = true)
                }
            }
        }
    }

    /** Записывает блок в колонку, если мировая позиция попадает внутрь неё. */
    private fun setInColumn(column: ChunkColumn, wx: Int, wy: Int, wz: Int, index: Int, onlyAir: Boolean) {
        val lx = wx - column.originX
        val lz = wz - column.originZ
        if (lx < 0 || lx >= World.CHUNK_SIZE_X || lz < 0 || lz >= World.CHUNK_SIZE_Z) return
        if (wy < 0 || wy >= World.WORLD_HEIGHT) return
        val sy = wy / World.SECTION_SIZE
        val section = column.sections[sy]
            ?: Section(column.cx, sy, column.cz).also { column.sections[sy] = it }
        val ly = wy % World.SECTION_SIZE
        if (onlyAir && section.get(lx, ly, lz) != BlockRegistry.AIR) return
        section.set(lx, ly, lz, index)
    }

    companion object {
        const val BASE_HEIGHT = 64.0
        const val HEIGHT_AMPLITUDE = 28.0
        const val MIN_HEIGHT = 2
        const val MAX_HEIGHT = 200
        const val DIRT_DEPTH = 3
        const val OCTAVES_LARGE = 3
        const val OCTAVES_SMALL = 2

        /** Высота плоского мира (тип «Суперплоский»). */
        const val FLAT_SURFACE = 64

        /** Радиус кроны дерева — запас при проверке стволов у границ чанка. */
        const val CANOPY_RADIUS = 2

        /** Минимальная дистанция между стволами деревьев (в блоках, в каждую сторону). */
        const val TREE_SPACING = 2

        /** Масштаб шума зоны леса (чем меньше — тем крупнее лесные массивы). */
        const val FOREST_SCALE = 0.0012

        /** Октавы шума зоны леса. */
        const val FOREST_OCTAVES = 2

        /** Порог шума: выше — лес, ниже — равнина (~30% карты занято лесом). */
        const val FOREST_THRESHOLD = 0.18
    }
}