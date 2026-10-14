package hgds.IloveGroup.world

/**
 * Собирает меш секции 32×32×32: только грани, соседние с воздухом.
 * Соседи из других секций и чанков запрашиваются через World,
 * поэтому стыки секций и колонок сшиваются корректно.
 *
 * Класс чистый (без OpenGL) и живёт в core; загрузка геометрии в GPU —
 * в клиенте (hgds.IloveGroup.render.ChunkMeshFactory).
 */
object ChunkMesher {

    /** Геометрия одной грани единичного куба: 4 угла [x, y, z, u, v], обход CCW снаружи. */
    private data class FaceGeometry(
        val corners: Array<FloatArray>,
        val normal: IntArray,
        val brightness: Float
    )

    private val FACE_DATA: Map<Face, FaceGeometry> = mapOf(
        Face.TOP to FaceGeometry(
            arrayOf(
                floatArrayOf(0f, 1f, 0f, 0f, 0f),
                floatArrayOf(0f, 1f, 1f, 0f, 1f),
                floatArrayOf(1f, 1f, 1f, 1f, 1f),
                floatArrayOf(1f, 1f, 0f, 1f, 0f)
            ),
            intArrayOf(0, 1, 0), 1.0f
        ),
        Face.BOTTOM to FaceGeometry(
            arrayOf(
                floatArrayOf(0f, 0f, 0f, 0f, 0f),
                floatArrayOf(1f, 0f, 0f, 1f, 0f),
                floatArrayOf(1f, 0f, 1f, 1f, 1f),
                floatArrayOf(0f, 0f, 1f, 0f, 1f)
            ),
            intArrayOf(0, -1, 0), 0.5f
        ),
        Face.NORTH to FaceGeometry(
            arrayOf(
                floatArrayOf(1f, 0f, 0f, 0f, 1f),
                floatArrayOf(0f, 0f, 0f, 1f, 1f),
                floatArrayOf(0f, 1f, 0f, 1f, 0f),
                floatArrayOf(1f, 1f, 0f, 0f, 0f)
            ),
            intArrayOf(0, 0, -1), 0.8f
        ),
        Face.SOUTH to FaceGeometry(
            arrayOf(
                floatArrayOf(0f, 0f, 1f, 0f, 1f),
                floatArrayOf(1f, 0f, 1f, 1f, 1f),
                floatArrayOf(1f, 1f, 1f, 1f, 0f),
                floatArrayOf(0f, 1f, 1f, 0f, 0f)
            ),
            intArrayOf(0, 0, 1), 0.8f
        ),
        Face.WEST to FaceGeometry(
            arrayOf(
                floatArrayOf(0f, 0f, 0f, 1f, 1f),
                floatArrayOf(0f, 0f, 1f, 0f, 1f),
                floatArrayOf(0f, 1f, 1f, 0f, 0f),
                floatArrayOf(0f, 1f, 0f, 1f, 0f)
            ),
            intArrayOf(-1, 0, 0), 0.6f
        ),
        Face.EAST to FaceGeometry(
            arrayOf(
                floatArrayOf(1f, 0f, 1f, 1f, 1f),
                floatArrayOf(1f, 0f, 0f, 0f, 1f),
                floatArrayOf(1f, 1f, 0f, 0f, 0f),
                floatArrayOf(1f, 1f, 1f, 1f, 0f)
            ),
            intArrayOf(1, 0, 0), 0.6f
        )
    )

    /**
     * Чистая функция (без OpenGL): собирает геометрию одной секции.
     * Оптимизации: непрерывные буферы без боксинга, быстрый путь чтения
     * соседей внутри секции (без обращения к World).
     */
    fun buildGeometry(
        section: Section,
        world: World,
        registry: BlockRegistry,
        atlas: TextureAtlasDataLike
    ): Pair<FloatArray, IntArray> {
        val fl = FloatList(4096)
        val il = IntList(1024)
        val originX = section.originX
        val originY = section.originY
        val originZ = section.originZ
        val size = World.SECTION_SIZE
        val maxX = originX + size - 1
        val maxY = originY + size - 1
        val maxZ = originZ + size - 1

        for (ly in 0 until size) {
            for (lz in 0 until size) {
                for (lx in 0 until size) {
                    val blockIndex = section.get(lx, ly, lz)
                    if (blockIndex == BlockRegistry.AIR) continue
                    val block = registry.byIndex(blockIndex) ?: continue
                    val wx = originX + lx
                    val wy = originY + ly
                    val wz = originZ + lz

                    for (face in Face.entries) {
                        val geometry = FACE_DATA.getValue(face)
                        val nx = wx + geometry.normal[0]
                        val ny = wy + geometry.normal[1]
                        val nz = wz + geometry.normal[2]
                        // Сосед внутри секции — читаем напрямую, иначе через World
                        val neighborSolid = if (nx in originX..maxX && ny in originY..maxY && nz in originZ..maxZ) {
                            section.get(nx - originX, ny - originY, nz - originZ) != BlockRegistry.AIR
                        } else {
                            world.isSolid(nx, ny, nz)
                        }
                        if (neighborSolid) continue // грань скрыта

                        val blockFace = block.face(face) ?: continue
                        val rect = atlas.uvRect(blockFace.texture)
                        val uv = blockFace.uv
                        val brightness = geometry.brightness

                        // Верхняя грань поворачивается на 0/90/180/270° псевдослучайно
                        // от мировых координат — как в Minecraft (трава не «полосой»)
                        val rotation = if (face == Face.TOP) topRotation(wx, wz) else 0

                        val baseVertex = fl.size / FLOATS_PER_VERTEX
                        for (corner in geometry.corners) {
                            fl.add(wx + corner[0])
                            fl.add(wy + corner[1])
                            fl.add(wz + corner[2])
                            var uLocal = uv[0] + (uv[2] - uv[0]) * corner[3]
                            var vLocal = uv[1] + (uv[3] - uv[1]) * corner[4]
                            for (r in 0 until rotation) {
                                val nextU = vLocal
                                val nextV = 1f - uLocal
                                uLocal = nextU
                                vLocal = nextV
                            }
                            fl.add(rect.u0 + (rect.u1 - rect.u0) * uLocal)
                            fl.add(rect.v0 + (rect.v1 - rect.v0) * vLocal)
                            fl.add(brightness)
                        }
                        il.add(baseVertex)
                        il.add(baseVertex + 1)
                        il.add(baseVertex + 2)
                        il.add(baseVertex)
                        il.add(baseVertex + 2)
                        il.add(baseVertex + 3)
                    }
                }
            }
        }
        return fl.toArray() to il.toArray()
    }

    /** Простой непрерывный список float без боксинга. */
    private class FloatList(capacity: Int) {
        var data = FloatArray(capacity)
            private set
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun toArray(): FloatArray = if (size == data.size) data else data.copyOf(size)
    }

    /** Простой непрерывный список int без боксинга. */
    private class IntList(capacity: Int) {
        var data = IntArray(capacity)
            private set
        var size = 0
            private set

        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun toArray(): IntArray = if (size == data.size) data else data.copyOf(size)
    }

    /**
     * Детерминированный «случайный» поворот верхней текстуры (0..3 четверти)
     * по мировым координатам блока — как в Minecraft: соседи почти всегда
     * разные, при перестройке меша текстура не «прыгает».
     */
    private fun topRotation(x: Int, z: Int): Int {
        var h = x * 374761393 + z * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 3
    }

    /** Вершина: позиция(3) + UV(2) + яркость(1) — формат меша клиента (render.Mesh). */
    const val FLOATS_PER_VERTEX = 6
}
