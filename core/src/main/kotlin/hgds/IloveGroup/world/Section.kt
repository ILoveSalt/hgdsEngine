package hgds.IloveGroup.world

/** Тип меша секции. В core нет OpenGL — конкретный класс (GL-меш) живёт в клиенте. */
interface SectionMesh {
    fun draw()
    fun upload(vertices: FloatArray, indices: IntArray)
    fun dispose()
}
/**
 * Секция 32×32×32 — минимальная единица хранения и мешинга внутри колонки чанка.
 */
class Section(val cx: Int, val cy: Int, val cz: Int) {

    val blocks = ByteArray(SECTION_BLOCKS)

    /** Требуется перестроить меш. */
    var dirty = true

    /** Готовый меш секции (null, если пустая или ещё не построена). */
    var mesh: SectionMesh? = null

    /** Количество не-воздушных блоков (для быстрого пропуска пустых секций). */
    var nonAir = 0
        private set

    fun get(lx: Int, ly: Int, lz: Int): Int = blocks[blockIndex(lx, ly, lz)].toInt()

    fun set(lx: Int, ly: Int, lz: Int, blockIndex: Int) {
        val i = blockIndex(lx, ly, lz)
        val old = blocks[i].toInt()
        if (old == blockIndex) return
        if (old == BlockRegistry.AIR && blockIndex != BlockRegistry.AIR) nonAir++
        if (old != BlockRegistry.AIR && blockIndex == BlockRegistry.AIR) nonAir--
        blocks[i] = blockIndex.toByte()
        dirty = true
    }

    val isEmpty: Boolean get() = nonAir == 0

    val originX: Int get() = cx * World.SECTION_SIZE
    val originY: Int get() = cy * World.SECTION_SIZE
    val originZ: Int get() = cz * World.SECTION_SIZE

    companion object {
        const val SECTION_BLOCKS = World.SECTION_SIZE * World.SECTION_SIZE * World.SECTION_SIZE

        fun blockIndex(lx: Int, ly: Int, lz: Int): Int =
            (ly * World.SECTION_SIZE + lz) * World.SECTION_SIZE + lx
    }
}