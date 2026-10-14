package hgds.IloveGroup.render

import com.google.gson.JsonParser
import hgds.IloveGroup.util.Resources
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Модель игрока, загружаемая из geometry-файла player.json
 * (ресурс: /assets/textures/game_1/entities/players/player.json).
 *
 * Формат — Bedrock geometry: bones с pivot и cubes (box-UV раскладка).
 * Пивоты анимации берутся из pivot костей (head/leftArm/rightArm/leftLeg/rightLeg).
 *
 * Модель смотрит в -Z при нулевом повороте (перед — грань -Z).
 * Высота модели 32 px, масштаб подобран так, чтобы рост был 1.8 блока.
 */
object PlayerModel {

    const val MODEL_RESOURCE = "/assets/textures/game_1/entities/players/player.json"

    /**
     * Отдельная геометрия руки от первого лица (Bedrock geometry, ресурс в корне assets).
     * Если файл есть и корректен — кулак строится из него, иначе из rightArm player.json.
     */
    const val ARM_GEO_RESOURCE = "/arm.geo.json"

    /** px модели → блоки (высота модели 32 px = рост 1.8 блока). */
    private const val S = 1.8f / 32f

    /** Одна грань куба: 4 угла (12 float, px относительно пивота) + UV-прямоугольник [u0,v0,u1,v1]. */
    private class Quad(val corners: FloatArray, val uv: FloatArray)

    /** Кость с кубами: имя, пивот (px от ступней) и грани. */
    private class Part(val name: String, val px: Float, val py: Float, val pz: Float, quads: List<Quad>) {
        val quads = quads.toTypedArray()
    }

    private val parts: List<Part> = load()

    const val VERTS_PER_PART = 24                    // 6 граней × 4 вершины
    const val FLOATS_PER_PART = VERTS_PER_PART * 6   // x,y,z,u,v,яркость
    val PART_COUNT: Int get() = parts.size
    val FLOATS_PER_PLAYER: Int get() = FLOATS_PER_PART * PART_COUNT

    /** Яркость граней (порядок: верх, низ, право(+X), перед(-Z), лево(-X), спина(+Z)). */
    private val FACE_LIGHT = floatArrayOf(1.0f, 0.55f, 0.8f, 0.95f, 0.8f, 0.7f)

    /**
     * Процедурная анимация игрока в стиле Minecraft (аналог ModelBiped).
     *
     * @param bodyYawRad  поворот тела вокруг Y (в мировых координатах модели);
     * @param headYawRad  дополнительный поворот головы относительно тела;
     * @param pitchRad    наклон головы (вверх/вниз);
     * @param swing       фаза шага (limbSwing);
     * @param swingAmp    сила шага 0..1 (limbSwingAmount);
     * @param sneak       приседание 0..1 (наклон корпуса, как в MC);
     * @param ageTicks    время в тиках — микрокачание рук в покое.
     */
    fun build(
        out: FloatArray,
        x: Float, y: Float, z: Float,
        bodyYawRad: Float, headYawRad: Float, pitchRad: Float,
        swing: Float, swingAmp: Float, sneak: Float, ageTicks: Float
    ) {
        val cy = cos(bodyYawRad); val sy = sin(bodyYawRad)
        // Качание конечностей в стиле Minecraft (ModelBiped)
        val pi = PI.toFloat()
        val s = swing * 0.6662f
        val swingLegR = cos(s) * 1.4f * swingAmp
        val swingLegL = cos(s + pi) * 1.4f * swingAmp
        val swingArmR = cos(s + pi) * 1.0f * swingAmp
        val swingArmL = cos(s) * 1.0f * swingAmp
        // Микрокачание рук в покое (bobArms)
        val armSway = cos(ageTicks * 0.0662f) * 0.05f + 0.05f

        var o = 0
        for (p in parts) {
            // Углы поворота кости (в стиле Minecraft: +X — вперёд/вниз)
            val rotX: Float
            var rotZ = 0f
            var rotY = 0f
            when (p.name.lowercase()) {
                "rightleg" -> rotX = swingLegR
                "leftleg" -> rotX = swingLegL
                "rightarm" -> { rotX = swingArmR + 0.4f * sneak; rotZ = armSway } // 0.4 — как в MC
                "leftarm" -> { rotX = swingArmL + 0.4f * sneak; rotZ = -armSway }
                "body" -> rotX = 0f // наклон корпуса в приседе даёт lean вокруг бёдер
                "head" -> { rotX = pitchRad; rotY = headYawRad }
                else -> rotX = 0f
            }
            val cosX = cos(rotX); val sinX = sin(rotX)
            val cosZ = cos(rotZ); val sinZ = sin(rotZ)
            val cosY = cos(rotY); val sinY = sin(rotY)
            // Сдвиг пивота при приседании (как в MC): ноги чуть вперёд, голова вниз
            val hipY = 12f * S // бёдра — общая ось наклона верхней части
            val lean = -0.5f * sneak // плечи вперёд (модель смотрит в -Z)
            val cosL = cos(lean); val sinL = sin(lean)
            val upper = p.name.equals("body", true) || p.name.equals("head", true) || p.name.endsWith("arm", true)
            // Пивот кости в модели (px), поворот тела вокруг Y и перенос в мир
            val rx = p.px * S; val ry = p.py * S; val rz = p.pz * S
            // Наклонённый пивот (для верхней части тела)
            var lpx = rx; var lpy = ry; var lpz = rz
            if (upper) {
                val dyP = ry - hipY
                lpy = hipY + cosL * dyP - sinL * rz
                lpz = sinL * dyP + cosL * rz
            }
            val pvx = x + lpx * cy + lpz * sy
            val pvy = y + lpy
            val pvz = z - lpx * sy + lpz * cy
            for ((q, quad) in p.quads.withIndex()) {
                val light = FACE_LIGHT[q % FACE_LIGHT.size]
                val c = quad.corners
                for (k in 0 until 4) {
                    val lx = c[k * 3] * S
                    val ly = c[k * 3 + 1] * S
                    val lz = c[k * 3 + 2] * S
                    // 0) присед: наклон верхней части вокруг бёдер (модельные координаты)
                    var vx = lx
                    var vy = ly
                    var vz = lz
                    if (upper) {
                        val dyV = ry + ly - hipY
                        vy = hipY + cosL * dyV - sinL * (rz + lz) - lpy
                        vz = sinL * dyV + cosL * (rz + lz) - lpz
                    }
                    // 1) поворот кости вокруг X (шаг/наклон/pitch)
                    val tmpY = cosX * vy - sinX * vz
                    val tmpZ = sinX * vy + cosX * vz
                    vy = tmpY; vz = tmpZ
                    // 2) поворот вокруг Z (микрокачание рук)
                    if (sinZ != 0f) {
                        val tx = cosZ * vx - sinZ * vy; val ty = sinZ * vx + cosZ * vy
                        vx = tx; vy = ty
                    }
                    // 3) поворот головы вокруг Y относительно тела
                    if (sinY != 0f) {
                        val tx = cosY * vx + sinY * vz; val tz = -sinY * vx + cosY * vz
                        vx = tx; vz = tz
                    }
                    // 4) поворот тела вокруг Y и перенос в мир
                    out[o++] = pvx + vx * cy + vz * sy
                    out[o++] = pvy + vy
                    out[o++] = pvz - vx * sy + vz * cy
                    // UV из прямоугольника: обход углов k0..k3 → (u0,v0),(u1,v0),(u1,v1),(u0,v1)
                    out[o++] = if (k == 1 || k == 2) quad.uv[2] else quad.uv[0]
                    out[o++] = if (k >= 2) quad.uv[3] else quad.uv[1]
                    out[o++] = light
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Рука от первого лица                                               //
    // ------------------------------------------------------------------ //

    /** Часть «rightArm» (для руки от первого лица). */
    private val rightArmPart = parts.firstOrNull { it.name.equals("rightArm", true) }

    /**
     * Правая рука от первого лица.
     *
     * Геометрия берётся из /arm.geo.json (если загрузился), иначе из части
     * «rightArm» player.json. Модель arm.geo.json центрируется так же, как
     * рука в player.json, поэтому цепочка трансформов Minecraft в
     * PlayerRenderer (позиция 0.56,−0.52,−0.72 + поворот 45° + swing) даёт
     * расположение и размер руки на экране как в MC.
     *
     * UV-раскладка файла (текстура 4×4) не используется — грани текстурируются
     * регионами правой руки со скина, как в Minecraft. Поворот куба из файла
     * не применяется: он задавал позу для превью в Blockbench, а нужный
     * наклон в от первого лица даёт сама цепочка трансформов MC.
     */
    fun buildFirstPersonArm(out: FloatArray, m: Matrix4f) {
        val geo = armGeoQuads
        if (geo != null) {
            // arm.geo.json: куб 4×12×4, origin [-7,0,5] относительно пивота кости (0,0,0).
            // Переводим в систему Java-модели MC (ось y вниз) относительно пивота руки:
            // jx = gx + 4, jy = 10 - gy, jz = gz - 7 → бокс (-3..1, -2..10, -2..2),
            // ровно как rightArm в HumanoidModel: addBox(-3,-2,-2,4,12,4) с пивотом (-5,2,0).
            // Порядок вершин квадов сохраняем (обход из файла) — пивот и поворот добавит матрица.
            var o = 0
            for ((q, quad) in geo.withIndex()) {
                val light = FACE_LIGHT[q % FACE_LIGHT.size]
                val c = quad.corners
                for (k in 0 until 4) {
                    val vx = c[k * 3] + 4f
                    val vy = 10f - c[k * 3 + 1]
                    val vz = c[k * 3 + 2] - 7f
                    // JOML: mXY = колонка X, строка Y → перенос в m30/m31/m32
                    out[o++] = m.m00() * vx + m.m10() * vy + m.m20() * vz + m.m30()
                    out[o++] = m.m01() * vx + m.m11() * vy + m.m21() * vz + m.m31()
                    out[o++] = m.m02() * vx + m.m12() * vy + m.m22() * vz + m.m32()
                    out[o++] = if (k == 1 || k == 2) quad.uv[2] else quad.uv[0]
                    out[o++] = if (k >= 2) quad.uv[3] else quad.uv[1]
                    out[o++] = light
                }
            }
        } else {
            buildFirstPersonArmLegacy(out, m)
        }
    }

    /**
     * Загружает кубы из /arm.geo.json (Bedrock geometry).
     * UV берутся из части «rightArm» player.json (та же box-UV раскладка и тот же
     * размер бокса 4×12×4, порядок граней одинаковый: верх, низ, право, перед, лево, спина).
     * Максимум 6 квадов (один куб) — рендерер отводит буфер ровно под одну часть.
     */
    private fun loadArmGeo(): Array<Quad>? = try {
        val root = JsonParser.parseString(Resources.readText(ARM_GEO_RESOURCE)).asJsonObject
        val geo = root.getAsJsonArray("geometry")?.get(0)?.asJsonObject ?: return null
        val skinUv = rightArmPart?.quads ?: return null
        val out = ArrayList<Quad>(6)
        geo.getAsJsonArray("bones")?.forEach { b ->
            val bone = b.asJsonObject
            val pivot = bone.getAsJsonArray("pivot")
            val bpx = pivot?.get(0)?.asFloat ?: 0f
            val bpy = pivot?.get(1)?.asFloat ?: 0f
            val bpz = pivot?.get(2)?.asFloat ?: 0f
            bone.getAsJsonArray("cubes")?.forEach { cc ->
                if (out.size >= 6) return@forEach
                val cube = cc.asJsonObject
                val origin = cube.getAsJsonArray("origin") ?: return@forEach
                val size = cube.getAsJsonArray("size") ?: return@forEach
                if (origin.size() < 3 || size.size() < 3) return@forEach
                val x0 = origin[0].asFloat; val y0 = origin[1].asFloat; val z0 = origin[2].asFloat
                val x1 = x0 + size[0].asFloat; val y1 = y0 + size[1].asFloat; val z1 = z0 + size[2].asFloat
                out.add(Quad(pack(bpx, bpy, bpz,
                    floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0),
                    floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1)), skinUv[0].uv))
                out.add(Quad(pack(bpx, bpy, bpz,
                    floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1),
                    floatArrayOf(x1, y0, z0), floatArrayOf(x0, y0, z0)), skinUv[1].uv))
                out.add(Quad(pack(bpx, bpy, bpz,
                    floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1),
                    floatArrayOf(x1, y0, z1), floatArrayOf(x1, y0, z0)), skinUv[2].uv))
                out.add(Quad(pack(bpx, bpy, bpz,
                    floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0),
                    floatArrayOf(x1, y0, z0), floatArrayOf(x0, y0, z0)), skinUv[3].uv))
                out.add(Quad(pack(bpx, bpy, bpz,
                    floatArrayOf(x0, y1, z1), floatArrayOf(x0, y1, z0),
                    floatArrayOf(x0, y0, z0), floatArrayOf(x0, y0, z1)), skinUv[4].uv))
                out.add(Quad(pack(bpx, bpy, bpz,
                    floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1),
                    floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1)), skinUv[5].uv))
            }
        }
        if (out.isEmpty()) null else out.toTypedArray()
    } catch (e: Exception) {
        System.err.println("[hgdsEngine] $ARM_GEO_RESOURCE не загружен (${e.message}) — рука из player.json")
        null
    }

    /** Квады руки из /arm.geo.json (null — файл отсутствует или некорректен). */
    private val armGeoQuads: Array<Quad>? = loadArmGeo()

    /**
     * Куб блока в руке от первого лица: 6 граней единичного куба 0..1
     * с теми же обходами вершин и яркостью, что и в ChunkMesher (мир).
     * UV каждой грани — [u0, v0, u1, v1] в нормализованных координатах атласа.
     */
    /** Рука из части «rightArm» player.json (fallback, если /arm.geo.json не загрузился). */
    private fun buildFirstPersonArmLegacy(out: FloatArray, m: Matrix4f) {
        val p = rightArmPart ?: return
        // Вершины в px относительно пивота кости, ось y вниз (как в Java-модели MC):
        // player.json хранит y вверх (Bedrock) → инвертируем, x/z без изменений.
        // Бокс (-3..1, -2..10, -2..2) — совпадает с addBox(-3,-2,-2,4,12,4).
        var o = 0
        for ((q, quad) in p.quads.withIndex()) {
            val light = FACE_LIGHT[q % FACE_LIGHT.size]
            val c = quad.corners
            for (k in 0 until 4) {
                val vx = c[k * 3]
                val vy = -c[k * 3 + 1]
                val vz = c[k * 3 + 2]
                // JOML: mXY = колонка X, строка Y → перенос в m30/m31/m32
                out[o++] = m.m00() * vx + m.m10() * vy + m.m20() * vz + m.m30()
                out[o++] = m.m01() * vx + m.m11() * vy + m.m21() * vz + m.m31()
                out[o++] = m.m02() * vx + m.m12() * vy + m.m22() * vz + m.m32()
                out[o++] = if (k == 1 || k == 2) quad.uv[2] else quad.uv[0]
                out[o++] = if (k >= 2) quad.uv[3] else quad.uv[1]
                out[o++] = light
            }
        }
    }

    /** Центр описанного бокса части (в px модели) — для выравнивания руки. */
    private fun bboxCenter(quads: Array<Quad>): FloatArray {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (q in quads) {
            val c = q.corners
            for (k in 0 until 4) {
                val x = c[k * 3]; val y = c[k * 3 + 1]; val z = c[k * 3 + 2]
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            }
        }
        return floatArrayOf((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f)
    }


    fun buildHeldBlockCube(out: FloatArray, m: Matrix4f, uv: Array<FloatArray>) {
        var o = 0
        for (f in 0 until 6) {
            val rect = uv[f]
            val corners = HELD_CUBE_FACES[f]
            val light = HELD_CUBE_LIGHT[f]
            for (k in 0 until 4) {
                val c = corners[k]
                val vx = c[0]
                val vy = c[1]
                val vz = c[2]
                // JOML: mXY = колонка X, строка Y → перенос в m30/m31/m32
                out[o++] = m.m00() * vx + m.m10() * vy + m.m20() * vz + m.m30()
                out[o++] = m.m01() * vx + m.m11() * vy + m.m21() * vz + m.m31()
                out[o++] = m.m02() * vx + m.m12() * vy + m.m22() * vz + m.m32()
                out[o++] = rect[0] + (rect[2] - rect[0]) * c[3]
                out[o++] = rect[1] + (rect[3] - rect[1]) * c[4]
                out[o++] = light
            }
        }
    }

    /** Углы граней единичного куба [x, y, z, u, v] — обход CCW снаружи (как в ChunkMesher). */
    private val HELD_CUBE_FACES = arrayOf(
        // TOP (u→+X, v→+Z)
        arrayOf(
            floatArrayOf(0f, 1f, 0f, 0f, 0f), floatArrayOf(0f, 1f, 1f, 0f, 1f),
            floatArrayOf(1f, 1f, 1f, 1f, 1f), floatArrayOf(1f, 1f, 0f, 1f, 0f)
        ),
        // BOTTOM
        arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f), floatArrayOf(1f, 0f, 0f, 1f, 0f),
            floatArrayOf(1f, 0f, 1f, 1f, 1f), floatArrayOf(0f, 0f, 1f, 0f, 1f)
        ),
        // NORTH (−Z)
        arrayOf(
            floatArrayOf(1f, 0f, 0f, 0f, 1f), floatArrayOf(0f, 0f, 0f, 1f, 1f),
            floatArrayOf(0f, 1f, 0f, 1f, 0f), floatArrayOf(1f, 1f, 0f, 0f, 0f)
        ),
        // SOUTH (+Z)
        arrayOf(
            floatArrayOf(0f, 0f, 1f, 0f, 1f), floatArrayOf(1f, 0f, 1f, 1f, 1f),
            floatArrayOf(1f, 1f, 1f, 1f, 0f), floatArrayOf(0f, 1f, 1f, 0f, 0f)
        ),
        // WEST (−X)
        arrayOf(
            floatArrayOf(0f, 0f, 0f, 1f, 1f), floatArrayOf(0f, 0f, 1f, 0f, 1f),
            floatArrayOf(0f, 1f, 1f, 0f, 0f), floatArrayOf(0f, 1f, 0f, 1f, 0f)
        ),
        // EAST (+X)
        arrayOf(
            floatArrayOf(1f, 0f, 1f, 1f, 1f), floatArrayOf(1f, 0f, 0f, 0f, 1f),
            floatArrayOf(1f, 1f, 0f, 0f, 0f), floatArrayOf(1f, 1f, 1f, 1f, 0f)
        )
    )

    /** Яркость граней куба — как у блоков в мире (ChunkMesher). */
    private val HELD_CUBE_LIGHT = floatArrayOf(1.0f, 0.5f, 0.8f, 0.8f, 0.6f, 0.6f)

    // ------------------------------------------------------------------ //
    //  Разбор Bedrock geometry                                            //
    // ------------------------------------------------------------------ //

    /** Загружает и парсит модель из resources. */
    private fun load(): List<Part> {
        val root = JsonParser.parseString(Resources.readText(MODEL_RESOURCE)).asJsonObject
        val geometry = root.getAsJsonArray("geometry")
            ?: error("player.json: нет секции geometry")
        val geo = geometry[0].asJsonObject
        val desc = geo.getAsJsonObject("description")
        val texW = desc?.get("texture_width")?.asFloat ?: 64f
        val texH = desc?.get("texture_height")?.asFloat ?: 64f

        val result = ArrayList<Part>()
        geo.getAsJsonArray("bones")?.forEach { b ->
            val bone = b.asJsonObject
            val name = bone.get("name")?.asString ?: return@forEach
            val cubes = bone.getAsJsonArray("cubes") ?: return@forEach // кости без кубов пропускаем
            val pivot = bone.getAsJsonArray("pivot")
            val px = pivot?.get(0)?.asFloat ?: 0f
            val py = pivot?.get(1)?.asFloat ?: 0f
            val pz = pivot?.get(2)?.asFloat ?: 0f

            val quads = ArrayList<Quad>()
            cubes.forEach { c ->
                val cube = c.asJsonObject
                val origin = cube.getAsJsonArray("origin")
                val size = cube.getAsJsonArray("size")
                val uv = cube.getAsJsonArray("uv")
                    ?: error("player.json: у куба кости '$name' нет uv")
                val u0 = uv[0].asFloat; val v0 = uv[1].asFloat
                val w = size[0].asFloat; val h = size[1].asFloat; val d = size[2].asFloat
                val x0 = origin[0].asFloat; val y0 = origin[1].asFloat; val z0 = origin[2].asFloat
                val x1 = x0 + w; val y1 = y0 + h; val z1 = z0 + d
                fun rect(u: Float, v: Float, ru: Float, rv: Float): FloatArray =
                    floatArrayOf(u / texW, v / texH, (u + ru) / texW, (v + rv) / texH)
                // Классическая box-UV раскладка: [верх][низ] сверху, [право][перед][лево][спина] снизу
                quads.add(Quad(pack(px, py, pz,
                    floatArrayOf(x0, y1, z0), floatArrayOf(x0, y1, z1),
                    floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0)), rect(u0 + d, v0, w, d)))
                quads.add(Quad(pack(px, py, pz,
                    floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0),
                    floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1)), rect(u0 + d + w, v0, w, d)))
                quads.add(Quad(pack(px, py, pz,
                    floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1),
                    floatArrayOf(x1, y0, z1), floatArrayOf(x1, y0, z0)), rect(u0, v0 + d, d, h)))
                quads.add(Quad(pack(px, py, pz,
                    floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0),
                    floatArrayOf(x1, y0, z0), floatArrayOf(x0, y0, z0)), rect(u0 + d, v0 + d, w, h)))
                quads.add(Quad(pack(px, py, pz,
                    floatArrayOf(x0, y1, z1), floatArrayOf(x0, y1, z0),
                    floatArrayOf(x0, y0, z0), floatArrayOf(x0, y0, z1)), rect(u0 + d + w, v0 + d, d, h)))
                quads.add(Quad(pack(px, py, pz,
                    floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1),
                    floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1)), rect(u0 + d + w + d, v0 + d, w, h)))
            }
            if (quads.isNotEmpty()) result.add(Part(name, px, py, pz, quads))
        }
        check(result.isNotEmpty()) { "player.json не содержит ни одной кости с кубами" }
        return result
    }

    /** Собирает 4 угла (px относительно пивота кости) в плоский массив. */
    private inline fun pack(
        px: Float, py: Float, pz: Float,
        a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray
    ): FloatArray = floatArrayOf(
        a[0] - px, a[1] - py, a[2] - pz,
        b[0] - px, b[1] - py, b[2] - pz,
        c[0] - px, c[1] - py, c[2] - pz,
        d[0] - px, d[1] - py, d[2] - pz
    )
}
