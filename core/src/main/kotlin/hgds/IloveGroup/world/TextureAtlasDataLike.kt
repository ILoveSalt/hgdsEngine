package hgds.IloveGroup.world

/**
 * UV-прямоугольник плитки внутри атласа. v0 — верх плитки, v1 — низ.
 * Общая (core) точка стыковки: render-атлас клиента реализует [TextureAtlasDataLike].
 */
data class UVRect(val u0: Float, val v0: Float, val u1: Float, val v1: Float)

/**
 * Минимальный интерфейс атласа, нужный мешеру (чтобы не тянуть весь класс в тесты
 * и не делать зависимость core -> render).
 */
interface TextureAtlasDataLike {
    fun uvRect(name: String): UVRect
}
