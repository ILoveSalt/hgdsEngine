package hgds.IloveGroup.render

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import hgds.IloveGroup.util.Resources
import hgds.IloveGroup.world.UVRect
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.random.Random

/** Описание одной текстуры в textures.json. */
data class TextureDefinition(
    val name: String,
    /** Путь к PNG-файлу на classpath (например "/block/dirt.png"). Если задан — процедурный паттерн не используется. */
    val file: String? = null,
    /** Тинт (раскраска) текстуры в RGB 0–255 — например, зелёный для травы: [145, 189, 89]. */
    val tint: List<Int>? = null,
    /** Путь к PNG-оверлею на classpath (например "/block/grass_block_side_overlay.png"), накладывается поверх file. */
    val overlay: String? = null,
    /** Тинт для оверлея (RGB 0–255). */
    @SerializedName("overlay_tint") val overlayTint: List<Int>? = null,
    val pattern: String = "noise",
    @SerializedName("base_color") val baseColor: List<Float> = listOf(1f, 0f, 1f),
    @SerializedName("top_color") val topColor: List<Float>? = null,
    @SerializedName("top_rows") val topRows: Int = 4
)

/** Настройки компоновки атласа. */
data class AtlasLayout(
    @SerializedName("tile_size") val tileSize: Int = 16,
    val columns: Int = 4,
    val filter: String = "nearest"
)

/** Корневой объект textures.json. */
data class TexturesFile(
    @SerializedName("format_version") val formatVersion: Int,
    val atlas: AtlasLayout = AtlasLayout(),
    val textures: List<TextureDefinition>
)

/**
 * Данные атласа текстур: разбор textures.json, расчёт UV-прямоугольников
 * и сборка пикселей плиток. Каждая текстура берётся из PNG-файла на classpath
 * (например из оригинального Minecraft), либо генерируется процедурно,
 * если поле "file" не задано. Не зависит от OpenGL, поэтому легко тестируется.
 */
class TextureAtlasData private constructor(
    val tileSize: Int,
    val columns: Int,
    val rows: Int,
    val filter: String,
    private val textures: List<TextureDefinition>
) : hgds.IloveGroup.world.TextureAtlasDataLike {

    val width: Int get() = columns * tileSize
    val height: Int get() = rows * tileSize

    private val rects: Map<String, UVRect> = buildRects()

    fun textureNames(): List<String> = textures.map { it.name }

    override fun uvRect(name: String): UVRect =
        checkNotNull(rects[name]) { "Texture not found in atlas: '$name'" }

    /** Генерирует RGBA-пиксели всего атласа; строка 0 — верх атласа. */
    fun generatePixels(): ByteArray {
        val pixels = ByteArray(width * height * BYTES_PER_PIXEL)
        textures.forEachIndexed { index, definition ->
            val column = index % columns
            val row = index / columns
            val tile = generateTile(definition)
            for (y in 0 until tileSize) {
                val srcStart = y * tileSize * BYTES_PER_PIXEL
                val dstStart = ((row * tileSize + y) * width + column * tileSize) * BYTES_PER_PIXEL
                tile.copyInto(pixels, dstStart, srcStart, srcStart + tileSize * BYTES_PER_PIXEL)
            }
        }
        return pixels
    }

    private fun generateTile(definition: TextureDefinition): ByteArray {
        // Настоящая PNG-текстура из Minecraft, если она задана в textures.json
        if (definition.file != null) {
            val tile = loadTileFromFile(definition.file, definition.name)
            val tinted = if (definition.tint != null) applyTint(tile, definition.tint) else tile
            val overlay = definition.overlay ?: return tinted
            val overlayTile = loadTileFromFile(overlay, definition.name)
            val tintedOverlay = if (definition.overlayTint != null) {
                applyTint(overlayTile, definition.overlayTint)
            } else {
                overlayTile
            }
            return blendOverlay(tinted, tintedOverlay)
        }
        val bytes = ByteArray(tileSize * tileSize * BYTES_PER_PIXEL)
        val random = Random(definition.name.hashCode()) // детерминированная текстура
        val base = definition.baseColor
        val top = definition.topColor ?: base
        for (y in 0 until tileSize) {
            for (x in 0 until tileSize) {
                val color = when (definition.pattern) {
                    PATTERN_PLAIN -> base
                    PATTERN_CHECKER -> {
                        val bright = if (((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0) 1f else 0.85f
                        base.map { it * bright }
                    }
                    PATTERN_GRASS_SIDE -> {
                        val topDepth = definition.topRows + if (random.nextInt(3) == 0) 1 else 0
                        if (y < topDepth) top else base
                    }
                    else -> base // noise
                }
                val jitter = if (definition.pattern == PATTERN_PLAIN) 1f else 0.88f + random.nextFloat() * 0.12f
                val offset = (y * tileSize + x) * BYTES_PER_PIXEL
                bytes[offset] = toByteValue(color[0] * jitter)
                bytes[offset + 1] = toByteValue(color[1] * jitter)
                bytes[offset + 2] = toByteValue(color[2] * jitter)
                bytes[offset + 3] = 0xFF.toByte()
            }
        }
        return bytes
    }
    /** Умножает RGB каждого пикселя на тинт (каналы 0–255 → множитель 0–1). */
    private fun applyTint(tile: ByteArray, tint: List<Int>): ByteArray {
        val result = tile.copyOf()
        val scale = floatArrayOf(tint[0] / 255f, tint[1] / 255f, tint[2] / 255f)
        for (offset in 0 until result.size step BYTES_PER_PIXEL) {
            result[offset] = toByteValue(toFloatValue(result[offset]) * scale[0])
            result[offset + 1] = toByteValue(toFloatValue(result[offset + 1]) * scale[1])
            result[offset + 2] = toByteValue(toFloatValue(result[offset + 2]) * scale[2])
        }
        return result
    }

    /** Накладывает RGBA-оверлей поверх базовой плитки (alpha-блендинг по альфа-каналу оверлея). */
    private fun blendOverlay(base: ByteArray, overlay: ByteArray): ByteArray {
        val result = base.copyOf()
        for (offset in 0 until result.size step BYTES_PER_PIXEL) {
            val alpha = toFloatValue(overlay[offset + 3])
            if (alpha <= 0f) continue
            for (c in 0 until 3) {
                val b = toFloatValue(base[offset + c])
                val o = toFloatValue(overlay[offset + c])
                result[offset + c] = toByteValue(o * alpha + b * (1f - alpha))
            }
        }
        return result
    }

    private fun toFloatValue(value: Byte): Float = (value.toInt() and 0xFF) / 255f



    /**
     * Загружает PNG-текстуру (например из оригинального Minecraft) с classpath
     * и возвращает RGBA-пиксели размером tileSize×tileSize.
     * Не зависит от OpenGL, поэтому легко тестируется.
     */
    private fun loadTileFromFile(path: String, name: String): ByteArray {
        val stream = Resources.openStream(path)
        val image = stream.use { ImageIO.read(it) }
            ?: throw IllegalStateException("Failed to decode texture file: $path (texture '$name')")
        val scaled = if (image.width == tileSize && image.height == tileSize) {
            image
        } else {
            // Масштабируем до размера плитки атласа (обычно 16×16)
            val resized = BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB)
            val graphics = resized.createGraphics()
            graphics.drawImage(image, 0, 0, tileSize, tileSize, null)
            graphics.dispose()
            resized
        }
        val bytes = ByteArray(tileSize * tileSize * BYTES_PER_PIXEL)
        val pixels = IntArray(tileSize * tileSize)
        scaled.getRGB(0, 0, tileSize, tileSize, pixels, 0, tileSize)
        for (i in pixels.indices) {
            val argb = pixels[i]
            val offset = i * BYTES_PER_PIXEL
            bytes[offset] = ((argb shr 16) and 0xFF).toByte() // R
            bytes[offset + 1] = ((argb shr 8) and 0xFF).toByte()  // G
            bytes[offset + 2] = (argb and 0xFF).toByte()          // B
            bytes[offset + 3] = ((argb shr 24) and 0xFF).toByte() // A
        }
        return bytes
    }

    private fun buildRects(): Map<String, UVRect> {
        val halfTexelU = 0.5f / width
        val halfTexelV = 0.5f / height
        return textures.mapIndexed { index, definition ->
            val column = index % columns
            val row = index / columns
            val u0 = column * tileSize / width.toFloat() + halfTexelU
            val u1 = (column + 1) * tileSize / width.toFloat() - halfTexelU
            val v0 = row * tileSize / height.toFloat() + halfTexelV
            val v1 = (row + 1) * tileSize / height.toFloat() - halfTexelV
            definition.name to UVRect(u0, v0, u1, v1)
        }.toMap()
    }

    private fun toByteValue(value: Float): Byte = (value.coerceIn(0f, 1f) * 255f).toInt().toByte()

    companion object {
        const val BYTES_PER_PIXEL = 4
        const val PATTERN_NOISE = "noise"
        const val PATTERN_PLAIN = "plain"
        const val PATTERN_CHECKER = "checker"
        const val PATTERN_GRASS_SIDE = "grass_side"
        private const val CHECKER_SIZE = 2

        fun load(resource: String = "/assets/textures/textures.json"): TextureAtlasData {
            val file = Gson().fromJson(Resources.readText(resource), TexturesFile::class.java)
            check(file.textures.isNotEmpty()) { "textures.json must contain at least one texture" }
            val rows = ceil(file.textures.size / file.atlas.columns.toFloat()).toInt()
            return TextureAtlasData(file.atlas.tileSize, file.atlas.columns, rows, file.atlas.filter, file.textures)
        }
    }
}