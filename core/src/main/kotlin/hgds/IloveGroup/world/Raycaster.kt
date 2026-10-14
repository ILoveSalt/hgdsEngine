package hgds.IloveGroup.world

import org.joml.Vector3i

/** Точка попадания луча: позиция блока и нормаль грани, в которую ставится новый блок. */
data class RayHit(
    val block: Vector3i,
    val normal: Vector3i,
    val distance: Float
)

/**
 * Воксельный raycast (алгоритм Amanatides & Woo, DDA).
 * Возвращает первый твёрдый блок на пути луча.
 */
object Raycaster {

    fun cast(
        world: World,
        originX: Float, originY: Float, originZ: Float,
        dirX: Float, dirY: Float, dirZ: Float,
        maxDistance: Float
    ): RayHit? {
        var x = kotlin.math.floor(originX).toInt()
        var y = kotlin.math.floor(originY).toInt()
        var z = kotlin.math.floor(originZ).toInt()

        val stepX = if (dirX > 0f) 1 else -1
        val stepY = if (dirY > 0f) 1 else -1
        val stepZ = if (dirZ > 0f) 1 else -1

        val tDeltaX = if (dirX != 0f) (1f / kotlin.math.abs(dirX)) else Float.MAX_VALUE
        val tDeltaY = if (dirY != 0f) (1f / kotlin.math.abs(dirY)) else Float.MAX_VALUE
        val tDeltaZ = if (dirZ != 0f) (1f / kotlin.math.abs(dirZ)) else Float.MAX_VALUE

        var tMaxX = if (dirX != 0f) {
            ((if (dirX > 0f) (x + 1) - originX else originX - x)) * tDeltaX
        } else Float.MAX_VALUE
        var tMaxY = if (dirY != 0f) {
            ((if (dirY > 0f) (y + 1) - originY else originY - y)) * tDeltaY
        } else Float.MAX_VALUE
        var tMaxZ = if (dirZ != 0f) {
            ((if (dirZ > 0f) (z + 1) - originZ else originZ - z)) * tDeltaZ
        } else Float.MAX_VALUE

        var normalX = 0
        var normalY = 0
        var normalZ = 0
        var t = 0f

        // Проверяем блок, в котором находится начало луча
        if (world.isSolid(x, y, z)) {
            return RayHit(Vector3i(x, y, z), Vector3i(0, 1, 0), 0f)
        }

        while (t <= maxDistance) {
            when {
                tMaxX < tMaxY && tMaxX < tMaxZ -> {
                    x += stepX; t = tMaxX; tMaxX += tDeltaX; normalX = -stepX; normalY = 0; normalZ = 0
                }
                tMaxY < tMaxZ -> {
                    y += stepY; t = tMaxY; tMaxY += tDeltaY; normalX = 0; normalY = -stepY; normalZ = 0
                }
                else -> {
                    z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; normalX = 0; normalY = 0; normalZ = -stepZ
                }
            }
            if (t > maxDistance) break
            if (world.isSolid(x, y, z)) {
                return RayHit(Vector3i(x, y, z), Vector3i(normalX, normalY, normalZ), t)
            }
        }
        return null
    }
}