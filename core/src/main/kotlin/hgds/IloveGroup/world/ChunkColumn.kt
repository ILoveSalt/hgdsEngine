package hgds.IloveGroup.world

/**
 * Колонка чанка 32×512×32: 16 секций 32×32×32 по вертикали.
 * Территория мира — 32×32 таких колонок.
 */
class ChunkColumn(val cx: Int, val cz: Int) {

    val sections = arrayOfNulls<Section>(World.SECTIONS_PER_CHUNK)

    /** Рельеф колонки сгенерирован. */
    var generated = false

    val originX: Int get() = cx * World.CHUNK_SIZE_X
    val originZ: Int get() = cz * World.CHUNK_SIZE_Z

    fun sectionAt(y: Int): Section? {
        if (y < 0 || y >= World.WORLD_HEIGHT) return null
        return sections[y / World.SECTION_SIZE]
    }
}