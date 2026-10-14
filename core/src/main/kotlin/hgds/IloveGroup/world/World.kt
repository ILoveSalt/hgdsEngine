package hgds.IloveGroup.world

/**
 * Большой мир с центром в нуле: от -WORLD_SIZE до +WORLD_SIZE блоков по обеим
 * осям (30 000 000 блоков в каждую сторону), внутри колонки — секции 32×32×32.
 *
 * Игрок ограничен границей мира (±WORLD_BORDER = ±30 000 000), но рельеф
 * генерируется и дальше — на GENERATION_OVERFLOW блоков за границей,
 * чтобы мир не обрывался визуально сразу за границей.
 *
 * Колонки генерируются лениво и детерминированно из seed генератора
 * (seed выбирается случайно при каждом запуске игры).
 */
class World(private val generator: TerrainGenerator) {

    private val columns = HashMap<Long, ChunkColumn>()

    /** Изменения игрока по колонкам: ключ колонки → (упакованная позиция → блок). */
    private val editsByColumn = HashMap<Long, HashMap<Int, Int>>()

    /** Радиус мира в блоках: от -WORLD_SIZE до +WORLD_SIZE. */
    val sizeBlocks: Int get() = WORLD_SIZE

    val terrainSeed: Long get() = generator.seed

    /** Можно ли генерировать колонку чанка: мир + запас за границей. */
    fun inTerritory(cx: Int, cz: Int): Boolean =
        cx in -MAX_GENERATED_CHUNK..MAX_GENERATED_CHUNK &&
            cz in -MAX_GENERATED_CHUNK..MAX_GENERATED_CHUNK

    /** Уже созданная колонка (без генерации). */
    fun columnAt(cx: Int, cz: Int): ChunkColumn? = columns[columnKey(cx, cz)]

    /** Возвращает колонку, при необходимости генерируя рельеф. null — вне территории. */
    fun ensureColumn(cx: Int, cz: Int): ChunkColumn? {
        if (!inTerritory(cx, cz)) return null
        val key = columnKey(cx, cz)
        val existing = columns[key]
        if (existing != null && existing.generated) return existing
        val column = existing ?: ChunkColumn(cx, cz).also { columns[key] = it }
        if (!column.generated) {
            generator.generateColumn(column)
            column.generated = true
            // Применяем сохранённые изменения игрока поверх рельефа
            editsByColumn[columnKey(cx, cz)]?.let { edits ->
                for ((packed, blockIndex) in edits) {
                    val lx = packed and LOCAL_MASK
                    val lz = (packed shr 5) and LOCAL_MASK
                    val y = packed shr LOCAL_Y_SHIFT
                    putBlock(column, column.originX + lx, y, column.originZ + lz, blockIndex)
                }
            }
            for (section in column.sections) section?.dirty = true
            // Стыки: секции соседних колонок должны перестроиться
            markColumnDirty(cx - 1, cz)
            markColumnDirty(cx + 1, cz)
            markColumnDirty(cx, cz - 1)
            markColumnDirty(cx, cz + 1)
        }
        return column
    }

    private fun markColumnDirty(cx: Int, cz: Int) {
        val neighbor = columnAt(cx, cz) ?: return
        for (section in neighbor.sections) section?.dirty = true
    }

    fun getBlock(x: Int, y: Int, z: Int): Int {
        if (y < 0 || y >= WORLD_HEIGHT) return BlockRegistry.AIR
        val cx = x.floorDiv(CHUNK_SIZE_X)
        val cz = z.floorDiv(CHUNK_SIZE_Z)
        val column = ensureColumn(cx, cz) ?: return BlockRegistry.AIR
        val section = column.sections[y / SECTION_SIZE] ?: return BlockRegistry.AIR
        return section.get(x.mod(CHUNK_SIZE_X), y.mod(SECTION_SIZE), z.mod(CHUNK_SIZE_Z))
    }

    fun setBlock(x: Int, y: Int, z: Int, blockIndex: Int) {
        if (y < 0 || y >= WORLD_HEIGHT) return
        val cx = x.floorDiv(CHUNK_SIZE_X)
        val cz = z.floorDiv(CHUNK_SIZE_Z)
        val column = ensureColumn(cx, cz) ?: return
        putBlock(column, x, y, z, blockIndex)
    }

    /**
     * Ставит блок действием игрока: изменение запоминается
     * и попадёт в сохранение мира (level.json).
     */
    fun setBlockByPlayer(x: Int, y: Int, z: Int, blockIndex: Int) {
        setBlock(x, y, z, blockIndex)
        if (y < 0 || y >= WORLD_HEIGHT) return
        val cx = x.floorDiv(CHUNK_SIZE_X)
        val cz = z.floorDiv(CHUNK_SIZE_Z)
        val packed = packLocal(x.mod(CHUNK_SIZE_X), y, z.mod(CHUNK_SIZE_Z))
        editsByColumn.getOrPut(columnKey(cx, cz)) { HashMap() }[packed] = blockIndex
    }

    private fun putBlock(column: ChunkColumn, x: Int, y: Int, z: Int, blockIndex: Int) {
        val cx = column.cx
        val cz = column.cz
        val sy = y / SECTION_SIZE
        val section = column.sections[sy]
            ?: Section(cx, sy, cz).also { column.sections[sy] = it }
        val lx = x.mod(CHUNK_SIZE_X)
        val ly = y.mod(SECTION_SIZE)
        val lz = z.mod(CHUNK_SIZE_Z)
        if (section.get(lx, ly, lz) == blockIndex) return
        section.set(lx, ly, lz, blockIndex)
        // Перестроить соседние секции, если блок на границе
        if (lx == 0) markSectionDirty(cx - 1, sy, cz)
        if (lx == CHUNK_SIZE_X - 1) markSectionDirty(cx + 1, sy, cz)
        if (ly == 0 && sy > 0) markSectionDirty(cx, sy - 1, cz)
        if (ly == SECTION_SIZE - 1 && sy < SECTIONS_PER_CHUNK - 1) markSectionDirty(cx, sy + 1, cz)
        if (lz == 0) markSectionDirty(cx, sy, cz - 1)
        if (lz == SECTION_SIZE - 1) markSectionDirty(cx, sy, cz + 1)
    }

    /** Применяет сохранённые изменения игрока (вызывается при загрузке мира). */
    fun loadEdits(edits: Map<String, Int>) {
        for ((key, blockIndex) in edits) {
            val parts = key.split(",")
            if (parts.size != 3) continue
            val x = parts[0].trim().toIntOrNull() ?: continue
            val y = parts[1].trim().toIntOrNull() ?: continue
            val z = parts[2].trim().toIntOrNull() ?: continue
            if (y < 0 || y >= WORLD_HEIGHT) continue
            val cx = x.floorDiv(CHUNK_SIZE_X)
            val cz = z.floorDiv(CHUNK_SIZE_Z)
            val packed = packLocal(x.mod(CHUNK_SIZE_X), y, z.mod(CHUNK_SIZE_Z))
            editsByColumn.getOrPut(columnKey(cx, cz)) { HashMap() }[packed] = blockIndex
            // Для уже сгенерированных колонок применяем сразу
            columnAt(cx, cz)?.let { putBlock(it, x, y, z, blockIndex) }
        }
    }

    /** Экспорт изменений для сохранения: "x,y,z" → индекс блока. */
    fun exportEdits(): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for ((columnKey, blocks) in editsByColumn) {
            val cx = (columnKey shr 32).toInt()
            val cz = columnKey.toInt()
            val originX = cx * CHUNK_SIZE_X
            val originZ = cz * CHUNK_SIZE_Z
            for ((packed, blockIndex) in blocks) {
                val lx = packed and LOCAL_MASK
                val lz = (packed shr 5) and LOCAL_MASK
                val y = packed shr LOCAL_Y_SHIFT
                out["${originX + lx},$y,${originZ + lz}"] = blockIndex
            }
        }
        return out
    }

    /** Упаковка локальной позиции в колонке: lx (5 бит) | lz (5 бит) | y (9 бит). */
    private fun packLocal(lx: Int, y: Int, lz: Int): Int =
        lx or (lz shl 5) or (y shl LOCAL_Y_SHIFT)

    private fun markSectionDirty(cx: Int, sy: Int, cz: Int) {
        columnAt(cx, cz)?.sections?.getOrNull(sy)?.let { it.dirty = true }
    }

    fun isSolid(x: Int, y: Int, z: Int): Boolean = getBlock(x, y, z) != BlockRegistry.AIR

    /** Высота рельефа (Y верхнего блока травы) — из генератора. */
    fun surfaceHeight(x: Int, z: Int): Int = generator.height(x, z)

    fun allColumns(): Collection<ChunkColumn> = columns.values

    private fun columnKey(cx: Int, cz: Int): Long =
        (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    companion object {
        /** Максимальная высота мира (512 блоков от Y=0). */
        const val WORLD_HEIGHT = 512

        /** Размер чанка по горизонтали. */
        const val CHUNK_SIZE_X = 32
        const val CHUNK_SIZE_Z = 32

        /** Размер секции внутри чанка (32×32×32). */
        const val SECTION_SIZE = 32

        /** Секций в колонке чанка по вертикали (512 / 32 = 16). */
        const val SECTIONS_PER_CHUNK = WORLD_HEIGHT / SECTION_SIZE

        /** Радиус мира в блоках: мир простирается от -WORLD_SIZE до +WORLD_SIZE. */
        const val WORLD_SIZE = 30_000_000

        /** Граница мира: дальше игрок пройти не может (во все стороны от центра). */
        const val WORLD_BORDER = 30_000_000

        /** На столько блоков рельеф продолжает генерироваться за границей мира. */
        const val GENERATION_OVERFLOW = 30_000

        /** Последний чанк, который может быть сгенерирован (за границей мира). */
        const val MAX_GENERATED_CHUNK = (WORLD_BORDER + GENERATION_OVERFLOW) / CHUNK_SIZE_X

        /** Маски упаковки локальной позиции блока внутри колонки. */
        const val LOCAL_MASK = 0x1F
        const val LOCAL_Y_SHIFT = 10
    }
}