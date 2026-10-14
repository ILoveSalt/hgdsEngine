package hgds.IloveGroup.world

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import hgds.IloveGroup.util.Resources

/** Грани блока. */
enum class Face(val key: String) {
    TOP("top"),
    BOTTOM("bottom"),
    NORTH("north"),
    SOUTH("south"),
    WEST("west"),
    EAST("east")
}

/** Текстура и UV-прямоугольник (в долях плитки) одной грани блока. */
data class BlockFace(
    val texture: String,
    val uv: List<Float> = listOf(0f, 0f, 1f, 1f)
)

/** Описание блока из blocks.json (по умолчанию — куб 1×1×1). */
data class BlockDefinition(
    val id: String,
    @SerializedName("display_name") val displayName: String = id,
    val solid: Boolean = true,
    val size: List<Float> = listOf(1f, 1f, 1f),
    val faces: Map<String, BlockFace>
) {
    fun face(face: Face): BlockFace? = faces[face.key]
}

private data class BlocksFile(
    @SerializedName("format_version") val formatVersion: Int,
    val blocks: List<BlockDefinition>
)

/**
 * Реестр блоков: читает blocks.json через Gson.
 * Каждый блок получает числовой индекс (порядок в JSON, начиная с 1);
 * индекс 0 зарезервирован за воздухом — именно он хранится в чанках.
 */
class BlockRegistry private constructor(
    private val blocksById: Map<String, BlockDefinition>,
    private val blocksByIndex: Array<BlockDefinition?>,
    val idToIndex: Map<String, Int>
) {

    operator fun get(id: String): BlockDefinition =
        checkNotNull(blocksById[id]) { "Unknown block id: '$id'" }

    fun all(): Collection<BlockDefinition> = blocksById.values

    /** Числовой индекс блока по его id (0 — воздух). */
    fun index(id: String): Int = idToIndex.getValue(id)

    /** Определение блока по числовому индексу (null — воздух/неизвестный). */
    fun byIndex(index: Int): BlockDefinition? = blocksByIndex.getOrNull(index)

    companion object {
        const val AIR = 0

        fun load(resource: String = "/assets/blocks/blocks.json"): BlockRegistry {
            val file = Gson().fromJson(Resources.readText(resource), BlocksFile::class.java)
            check(file.blocks.isNotEmpty()) { "blocks.json must contain at least one block" }

            val idToIndex = HashMap<String, Int>()
            val byIndex = arrayOfNulls<BlockDefinition>(file.blocks.size + 1)
            file.blocks.forEachIndexed { i, def ->
                val index = i + 1 // 0 — воздух
                idToIndex[def.id] = index
                byIndex[index] = def
            }
            return BlockRegistry(file.blocks.associateBy { it.id }, byIndex, idToIndex)
        }
    }
}