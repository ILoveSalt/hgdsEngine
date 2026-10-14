package hgds.IloveGroup.render

import hgds.IloveGroup.engine.Shader
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.glBindTexture
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Рендерер игроков с procedural-анимацией ходьбы (стиль Minecraft).
 *
 * На каждого игрока держит состояние анимации (фаза шага, амплитуда,
 * плавный поворот по направлению движения) и меш. Вершины перестраиваются
 * каждый кадр — это всего 168 вершин на игрока, копейки для CPU/GPU.
 */
class PlayerRenderer(skinResource: String = PlayerSkin.SKIN_RESOURCE) {

    private val skin = PlayerSkin(skinResource)

    /** Состояние анимации одного игрока. */
    private class Anim {
        var phase = 0f        // фаза шага
        var amp = 0f          // сила качания конечностей 0..1
        var bodyYaw = 0f      // поворот тела (догоняет голову с запаздыванием)
        var headYaw = 0f      // поворот головы (для локального игрока = направление взгляда)
        var sneak = 0f        // приседание 0..1 (плавное)
        var age = 0f          // возраст в тиках (микрокачание рук)
        var hasPrev = false
        var lastX = 0f
        var lastZ = 0f
        var mesh: Mesh? = null
    }

    private val anims = HashMap<Int, Anim>()
    private val vertices = FloatArray(PlayerModel.FLOATS_PER_PLAYER)

    // ----- Рука от первого лица -----
    private class Hand {
        var swing = 10f     // прогресс взмаха t (>=1 — нет взмаха; 0..1 — идёт удар)
        var equip = 1f      // прогресс поднятия предмета 0..1 (1 — поднят)
        var use = 0f        // прогресс использования 0..1 (ПКМ удерживается — рука к лицу)
        var lastItemId = -1 // id блока в руке на прошлом кадре (-1 — пусто; для equip-анимации)
        // Покачивание при ходьбе (Шаг 4): фаза «Время * Скорость» накапливается
        // от пройденного пути, амплитуда — по скорости (0 — стоит на месте)
        var bobPhase = 0f
        var bobAmp = 0f
        var hasPrev = false
        var lastX = 0f
        var lastZ = 0f
    }

    /** Предмет в руке: id блока (для equip-анимации) и UV 6 граней (порядок Face.entries). */
    class HeldItem(val id: Int, val faceUv: Array<FloatArray>) {
        init {
            require(faceUv.size == 6) { "HeldItem: нужно 6 граней" }
        }
    }

    private val hand = Hand()
    private val handVertices = FloatArray(PlayerModel.FLOATS_PER_PART)
    private val cubeVertices = FloatArray(PlayerModel.FLOATS_PER_PART)
    private var handMesh: Mesh? = null
    /** Индексы одной части (24 вершины, 36 индексов). */
    private val handIndices: IntArray = buildIndices().copyOfRange(0, 36)

    /** Рабочая матрица трансформа руки/предмета от первого лица. */
    private val handMatrix = Matrix4f()

    /** Индексы: 7 частей × 6 граней × 2 треугольника. */
    private val indices: IntArray = buildIndices()

    /** Описание игрока для отрисовки. */
    class Instance(
        val id: Int,
        val x: Float, val y: Float, val z: Float,
        val yawRad: Float,       // направление взгляда (голова); для удалённых — по движению
        val pitchRad: Float,
        val useGivenYaw: Boolean, // true — локальный игрок (голова = yawRad), false — по движению
        val sneaking: Boolean = false
    )

    /**
     * Рисует игроков. Шейдер [shader] (блоковый) должен быть уже активен
     * и с установленными uProjection/uView/uBrightness.
     */
    fun render(shader: Shader, dt: Float, players: List<Instance>) {
        if (players.isEmpty()) return
        shader.use()
        skin.texture.bind(0)

        val present = HashSet<Int>(players.size)
        for (pl in players) {
            present.add(pl.id)
            val a = anims.getOrPut(pl.id) { Anim() }

            // Скорость по смещению с прошлого кадра
            var speed = 0f
            val dx = pl.x - a.lastX
            val dz = pl.z - a.lastZ
            if (a.hasPrev) {
                speed = kotlin.math.sqrt(dx * dx + dz * dz) / dt.coerceAtLeast(1e-4f)
            }
            a.hasPrev = true
            a.lastX = pl.x
            a.lastZ = pl.z

            // ----- Голова и тело (как в Minecraft) -----
            // Голова смотрит туда, куда смотрит игрок; тело догоняет её плавно.
            // Пока разница мала — крутится только голова, при большом повороте
            // корпус поворачивается вслед, компенсируя позу.
            if (pl.useGivenYaw) {
                a.headYaw = pl.yawRad
            } else if (speed > 0.1f) {
                // Удалённый игрок: данных о взгляде нет — тело по направлению движения
                val target = kotlin.math.atan2(-dx, -dz)
                a.bodyYaw += shortestAngle(a.bodyYaw, target) * kotlin.math.min(1f, dt * 12f)
                a.headYaw = a.bodyYaw
            } else {
                a.headYaw = a.bodyYaw
            }
            if (pl.useGivenYaw) {
                a.bodyYaw += shortestAngle(a.bodyYaw, a.headYaw) * kotlin.math.min(1f, dt * BODY_FOLLOW_RATE)
            }
            val headYawRel = shortestAngle(a.bodyYaw, a.headYaw) // поворот головы относительно тела

            // Амплитуда и фаза шага: чем быстрее бежит, тем шире и чаще шаг
            val targetAmp = (speed / WALK_SPEED).coerceIn(0f, 1f)
            a.amp += (targetAmp - a.amp) * kotlin.math.min(1f, dt * 10f)
            a.phase += speed * dt * STEP_RATE
            a.age += dt * 20f

            // Приседание плавно нарастает/спадает
            val sneakTarget = if (pl.sneaking) 1f else 0f
            a.sneak += (sneakTarget - a.sneak) * kotlin.math.min(1f, dt * 14f)

            PlayerModel.build(
                vertices, pl.x, pl.y, pl.z,
                a.bodyYaw, headYawRel, pl.pitchRad,
                a.phase, a.amp, a.sneak, a.age
            )

            val mesh = a.mesh ?: Mesh(FloatArray(0), IntArray(0)).also { a.mesh = it }
            mesh.upload(vertices, indices)
            mesh.draw()
        }

        // Освобождаем меши ушедших игроков
        val gone = anims.entries.filter { it.key !in present }
        for ((id, a) in gone) {
            a.mesh?.dispose()
            anims.remove(id)
        }

        // Возвращаем атлас блоков на текстурный юнит 0
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    /**
     * Рука/предмет от первого лица — классическая схема Minecraft:
     * 1) мир уже нарисован, буфер глубины очищен (делает Main) — рука всегда поверх стен;
     * 2) базовая позиция (0.56, −0.52, −0.72) в пространстве камеры;
     * 3) базовые повороты: Y −15°, X 10°, Z −10°;
     * 4) покачивание при ходьбе: Sin/Cos от фазы, накопленной по скорости игрока
     *    (X = sin·0.2, Y = |sin|·0.2, поворот Y = sin·11°);
     * 5) удар: прогресс t 0..1 с рывком sin(√t·π).
     *
     * Строится в координатах камеры, поэтому [shader] должен иметь
     * uView = единичной матрице. Атлас блоков должен быть на юните 0.
     *
     * @param px, pz    позиция игрока — для вычисления скорости ходьбы (bobbing)
     * @param held      блок в руке (null — пустой кулак)
     * @param attacking ЛКМ удерживается — запускает взмах
     * @param using     ПКМ удерживается — анимация использования:
     *                  кулак поднимается к лицу (как в MC при еде),
     *                  с блоком в руке — взмах (как при установке блока)
     */
    fun renderHand(shader: Shader, dt: Float, attacking: Boolean, using: Boolean, held: HeldItem?, px: Float, pz: Float) {
        shader.use()

        // Equip-анимация: предмет сменился — опускаем и поднимаем заново
        val itemId = held?.id ?: -1
        if (itemId != hand.lastItemId) {
            hand.lastItemId = itemId
            hand.equip = 0f
        }
        if (hand.equip < 1f) hand.equip = (hand.equip + dt * 4f).coerceAtMost(1f)
        val eq = 1f - hand.equip.coerceIn(0f, 1f)

        // Взмах при ударе (ЛКМ) или использовании блока (ПКМ): 0..1 за ~0.3 с, как в MC
        if ((attacking || (using && held != null)) && hand.swing >= 1f) hand.swing = 0f
        if (hand.swing < 1f) hand.swing = (hand.swing + dt * 3.2f).coerceAtMost(1f)
        val sw = hand.swing.coerceIn(0f, 1f)

        // Использование (ПКМ удерживается): плавный подъём кулака к лицу, как при еде в MC
        val useTarget = if (using && held == null) 1f else 0f
        hand.use += (useTarget - hand.use) * (dt * 10f).coerceIn(0f, 1f)
        if (kotlin.math.abs(useTarget - hand.use) < 0.01f) hand.use = useTarget
        val usePull = if (held == null) hand.use else 0f

        // ----- Шаг 4: покачивание при ходьбе (Bobbing) -----
        // Скорость — по пройденному пути за кадр (как у моделей от третьего лица)
        val dx = px - hand.lastX
        val dz = pz - hand.lastZ
        var speed = 0f
        if (hand.hasPrev) speed = kotlin.math.sqrt(dx * dx + dz * dz) / dt.coerceAtLeast(1e-4f)
        hand.hasPrev = true
        hand.lastX = px
        hand.lastZ = pz
        // Сила покачивания: 0 стоя, 1 при полной скорости ходьбы (плавно)
        val targetBobAmp = (speed / WALK_SPEED).coerceIn(0f, 1f)
        hand.bobAmp += (targetBobAmp - hand.bobAmp) * kotlin.math.min(1f, dt * 10f)
        // Фаза «Время * Скорость»: накапливается от пройденного пути (rad/блок — STEP_RATE)
        hand.bobPhase += speed * dt * STEP_RATE

        // ----- Цепочка трансформов камеры -----
        // Интенсивность удара: резкий рывок по формуле MC — sin(√t·π) (Шаг 5)
        val swingF = sin(sqrt(sw) * PI.toFloat())
        handMatrix.identity()
        // Ручная подгонка позиции (handOffset: X — вправо, Y — вверх, Z — на себя)
        handMatrix.translate(handOffset)
        // Покачивание: X = sin(фаза)·0.2, Y = |sin(фаза)|·0.2 (по дуге вверх-вниз)
        handMatrix.translate(
            sin(hand.bobPhase) * 0.2f * hand.bobAmp,
            kotlin.math.abs(sin(hand.bobPhase)) * 0.2f * hand.bobAmp,
            0f
        )
        // Рывок от удара: влево-вперёд-вниз по синусам прогресса
        handMatrix.translate(
            -swingF * 0.4f,
            0.2f * sin(sqrt(sw) * 2f * PI.toFloat()),
            -0.2f * sin(sw * PI.toFloat())
        )
        // Equip-анимация: опускание при смене предмета
        handMatrix.translate(0f, -eq * 0.6f, 0f)
        // Использование (пустая рука): поза «ко рту», как EnumAction.EAT в ItemRenderer
        if (usePull > 0.01f) {
            handMatrix.translate(0f, usePull * 0.05f, 0f)
            handMatrix.rotateX(-90f * DEG2RAD * usePull)
            handMatrix.rotateY(10f * DEG2RAD * usePull)
            handMatrix.rotateX(60f * DEG2RAD * usePull)
        }
        val mesh = handMesh ?: Mesh(FloatArray(0), IntArray(0)).also { handMesh = it }
        // Обход вершин после перевода в систему y-вниз может стать CW — отсечение выключаем
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE)
        if (held != null) {
            // ----- Куб блока в руке: базовая позиция + повороты (как было — блоки ок) -----
            handMatrix.translate(0.56f, -0.52f, -0.71999997f)
            handMatrix.rotateY((-15f * DEG2RAD) + sin(hand.bobPhase) * 11f * DEG2RAD * hand.bobAmp + swingF * 20f * DEG2RAD)
            handMatrix.rotateX((10f - swingF * 60f) * DEG2RAD)
            handMatrix.rotateZ((-10f - swingF * 20f) * DEG2RAD)
            val m = Matrix4f(handMatrix)
            m.scale(0.4f * handScale)
            m.translate(-0.5f, -0.5f, -0.5f)
            PlayerModel.buildHeldBlockCube(cubeVertices, m, held.faceUv)
            atlTexture?.bind(0)
            mesh.upload(cubeVertices, handIndices)
            mesh.draw()
        } else {
            // ----- Пустой кулак: вытянутый вперёд брус, как рука в Minecraft -----
            // Геометрия rightArm (4×12×4 px, длинная ось Y-вниз) укладывается
            // вперёд, давая брус с пропорциями ≈ (0.25, 0.25, 0.75).
            val m = Matrix4f(handMatrix)
            // Шаг 2: базовая позиция; X больше — освобождает индикатор сытости,
            // Y ниже — нижний срез бруса уходит за край экрана
            m.translate(0.84f, -0.75f, -0.72f)
            // Шаг 3: наклоны + покачивание (±11° по Y) + рывок удара
            m.rotateY((15f * DEG2RAD) + sin(hand.bobPhase) * 11f * DEG2RAD * hand.bobAmp + swingF * 20f * DEG2RAD)
            m.rotateX((10f - swingF * 60f) * DEG2RAD)
            m.rotateZ((-10f - swingF * 20f) * DEG2RAD)
            // Укладка бруса: ось руки (Y-вниз) → −Z (от камеры вглубь экрана)
            m.rotateX(-90f * DEG2RAD)
            // Пивот rightArm (−5, 2, 0) px → блоки: брус левее и чуть ниже точки
            m.translate(-0.3125f, 0.125f, 0f)
            // Вершины строителя — в px модели (y вниз), 16 px = 1 блок
            m.scale(1f / 16f * handScale)
            skin.texture.bind(0)
            PlayerModel.buildFirstPersonArm(handVertices, m)
            mesh.upload(handVertices, handIndices)
            mesh.draw()
        }

        // Возвращаем состояние отсечения и атлас блоков на текстурный юнит 0
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    // ===== Подгонка руки от первого лица (1.0 / (0,0,0) = как в Minecraft) =====

    /** Масштаб руки и предмета: 1.0 = как в MC, 1.1 — на 10% крупнее. */
    var handScale = 1.0f

    /** Сдвиг в пространстве камеры (блоки): X — вправо, Y — вверх, Z — на себя. */
    val handOffset = org.joml.Vector3f(0f, 0f, 0f)

    /** Атлас блоков для отрисовки предмета в руке (устанавливается из Main). */
    var atlTexture: TextureAtlas? = null

    private fun shortestAngle(from: Float, to: Float): Float {
        var d = to - from
        while (d > Math.PI.toFloat()) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI.toFloat()) d += (2 * Math.PI).toFloat()
        return d
    }

    fun dispose() {
        for (a in anims.values) a.mesh?.dispose()
        anims.clear()
        handMesh?.dispose()
        handMesh = null
        skin.dispose()
    }

    companion object {
        /** Радиан в одном градусе (перевод углов трансформов руки). */
        val DEG2RAD = (Math.PI / 180.0).toFloat()

        /** Скорость ходьбы, при которой амплитуда максимальна (блоков/с). */
        const val WALK_SPEED = 4.3f

        /** Радиан фазы на блок пути. */
        const val STEP_RATE = 3.2f

        /** Скорость, с которой тело догоняет поворот головы (радиан/с экспоненты). */
        const val BODY_FOLLOW_RATE = 9f

        fun buildIndices(): IntArray {
            val idx = IntArray(PlayerModel.PART_COUNT * 36)
            var o = 0
            for (p in 0 until PlayerModel.PART_COUNT) {
                val base = p * PlayerModel.VERTS_PER_PART
                for (f in 0 until 6) {
                    val v0 = base + f * 4
                    idx[o++] = v0; idx[o++] = v0 + 1; idx[o++] = v0 + 2
                    idx[o++] = v0; idx[o++] = v0 + 2; idx[o++] = v0 + 3
                }
            }
            return idx
        }
    }
}