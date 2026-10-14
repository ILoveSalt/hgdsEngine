package hgds.IloveGroup.render

import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Рендерер солнца и луны в 3D-мире.
 *
 * Светила — билборд-квады на большом расстоянии от камеры ([CELESTIAL_DISTANCE]
 * блоков, в пределах дальней плоскости проекции). Рисуются обычным блоковым
 * шейдером с проверкой глубины ДО рельефа, поэтому блоки корректно их
 * перекрывают — светило видно только в небе, а не «перед экраном».
 */
class CelestialRenderer {

    // Пустые меши-заготовки; вершины перезаливаются каждый кадр (upload)
    private val sunMesh = Mesh(EMPTY_VERTICES, INDICES)
    private val moonMesh = Mesh(EMPTY_VERTICES, INDICES)
    private val vertices = FloatArray(6 * Mesh.FLOATS_PER_VERTEX)

    fun render(
        sky: SkyTextures,
        cameraPos: Vector3f,
        viewMatrix: Matrix4f,
        sunDir: FloatArray
    ) {
        // Базис камеры из матрицы вида (столбцы обратной транспонированной ротации)
        val rx = viewMatrix.m00(); val ry = viewMatrix.m10(); val rz = viewMatrix.m20()
        val ux = viewMatrix.m01(); val uy = viewMatrix.m11(); val uz = viewMatrix.m21()

        drawBody(sunMesh, sky.sun, cameraPos, sunDir[0], sunDir[1], sunDir[2], rx, ry, rz, ux, uy, uz)
        // Луна — строго в противоположной точке небосвода
        drawBody(moonMesh, sky.moon, cameraPos, -sunDir[0], -sunDir[1], -sunDir[2], rx, ry, rz, ux, uy, uz)
    }

    /** Строит и рисует один билборд-квад; тело ниже горизонта не рисуется. */
    private fun drawBody(
        mesh: Mesh, tex: Texture2D,
        cam: Vector3f, dx: Float, dy: Float, dz: Float,
        rx: Float, ry: Float, rz: Float, ux: Float, uy: Float, uz: Float
    ) {
        if (dy < HORIZON_EPSILON) return // светило за горизонтом — не рисуем

        val cX = cam.x + dx * CELESTIAL_DISTANCE
        val cY = cam.y + dy * CELESTIAL_DISTANCE
        val cZ = cam.z + dz * CELESTIAL_DISTANCE
        val h = SIZE / 2f

        // 4 угла квада: центр ± right*half ± up*half
        val x0 = cX - rx * h - ux * h; val y0 = cY - ry * h - uy * h; val z0 = cZ - rz * h - uz * h
        val x1 = cX + rx * h - ux * h; val y1 = cY + ry * h - uy * h; val z1 = cZ + rz * h - uz * h
        val x2 = cX + rx * h + ux * h; val y2 = cY + ry * h + uy * h; val z2 = cZ + rz * h + uz * h
        val x3 = cX - rx * h + ux * h; val y3 = cY - ry * h + uy * h; val z3 = cZ - rz * h + uz * h

        // Вершина: x,y,z, u,v, brightness. v=0 — верх текстуры (Texture2D).
        var i = 0
        fun vert(px: Float, py: Float, pz: Float, u: Float, v: Float) {
            vertices[i++] = px; vertices[i++] = py; vertices[i++] = pz
            vertices[i++] = u; vertices[i++] = v; vertices[i++] = 1f
        }
        vert(x0, y0, z0, 0f, 1f)
        vert(x1, y1, z1, 1f, 1f)
        vert(x2, y2, z2, 1f, 0f)
        vert(x3, y3, z3, 0f, 0f)

        mesh.upload(vertices, INDICES)
        tex.bind(0)
        mesh.draw()
    }

    fun dispose() {
        sunMesh.dispose()
        moonMesh.dispose()
    }

    companion object {
        /** Расстояние до небосвода (ближе дальней плоскости 1000). */
        const val CELESTIAL_DISTANCE = 400f

        /** Видимый размер светила в блоках на этом расстоянии. */
        const val SIZE = 44f

        /** Ниже этой высоты (доля единичного вектора) светило считается зашедшим. */
        const val HORIZON_EPSILON = 0.02f

        private val INDICES = intArrayOf(0, 1, 2, 0, 2, 3)
        private val EMPTY_VERTICES = FloatArray(6 * Mesh.FLOATS_PER_VERTEX)
    }
}