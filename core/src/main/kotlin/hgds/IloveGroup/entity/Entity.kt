package hgds.IloveGroup.entity

import hgds.IloveGroup.world.World
import org.joml.Vector3f
import kotlin.math.sqrt

/**
 * Базовая сущность мира: AABB-коллизии, гравитация, прыжок и ходьба.
 * Позиция — низ AABB (ноги). Физика идёт фиксированными шагами PHYSICS_STEP
 * (не зависит от FPS). Подклассы задают поведение: Player подаёт ввод
 * с клавиатуры, Villager — решения своего «мозга»; для этого update()
 * принимает направление движения и прыжок вместо прямого доступа к физике.
 */
abstract class Entity(
    val position: Vector3f = Vector3f(0f, 5f, 0f),
    var yaw: Float = 0f,
    var pitch: Float = 0f
) {
    var onGround = false
        private set

    /** Бежит ли сущность (быстрее движение и шире шаг анимации). */
    open var sprinting: Boolean = false

    /** Тихий медленный шаг (корпус заваливается, не сходит с кромки блока). */
    open var sneaking: Boolean = false

    /** Базовая скорость ходьбы (блоков/с) — подклассы задают свою. */
    open val moveSpeed: Float = DEFAULT_MOVE_SPEED

    private val velocity = Vector3f()

    /** Накопитель фиксированного шага физики (не зависит от FPS). */
    private var accumulator = 0f

    /** Накопленная высота падения (блоки) — для урона при приземлении. */
    private var fallDistance = 0f

    // Удобные геттеры для рендера и логики
    val x: Float get() = position.x
    val y: Float get() = position.y
    val z: Float get() = position.z
    val yawRad: Float get() = Math.toRadians(yaw.toDouble()).toFloat()
    val pitchRad: Float get() = Math.toRadians(pitch.toDouble()).toFloat()

    /**
     * Обновление физики. Реальное время накапливается, а симуляция идёт
     * фиксированными шагами PHYSICS_STEP — высота прыжка и поведение коллизий
     * одинаковы при любых FPS. При просадке кадра время ограничено
     * MAX_FRAME_TIME, чтобы движение не «проскакивало» сквозь блоки.
     */
    fun update(deltaTime: Float, moveForward: Float, moveStrafe: Float, jump: Boolean, world: World) {
        preUpdate(deltaTime)
        accumulator += deltaTime.coerceIn(0f, MAX_FRAME_TIME)
        while (accumulator >= PHYSICS_STEP) {
            step(PHYSICS_STEP, moveForward, moveStrafe, jump, world)
            accumulator -= PHYSICS_STEP
        }
    }

    /** Хук перед шагами физики: подклассы плавно меняют свои параметры (камера и т.п.). */
    protected open fun preUpdate(deltaTime: Float) {}

    /** Приземление после падения с высотой больше [FALL_DAMAGE_MIN_FALL] блоков. */
    protected open fun onLand(fallBlocks: Float) {}

    /** Один фиксированный шаг симуляции. */
    private fun step(dt: Float, moveForward: Float, moveStrafe: Float, jump: Boolean, world: World) {
        // Приземление определяется заново каждый шаг по вертикальному движению;
        // для прыжка используем состояние прошлого шага
        val wasOnGround = onGround
        onGround = false
        // Горизонтальное ускорение в направлении взгляда
        val yawRad = Math.toRadians(yaw.toDouble())
        val forwardX = kotlin.math.sin(yawRad).toFloat()
        val forwardZ = -kotlin.math.cos(yawRad).toFloat()
        val rightX = -forwardZ
        val rightZ = forwardX

        var vx = forwardX * moveForward + rightX * moveStrafe
        var vz = forwardZ * moveForward + rightZ * moveStrafe
        val len = sqrt(vx * vx + vz * vz)
        // Спринт ускоряет, присед замедляет, движение назад — 30% (как в Minecraft)
        val speed = moveSpeed *
            (if (sprinting) SPRINT_MULT else 1f) *
            (if (sneaking) SNEAK_MULT else 1f) *
            (if (moveForward < 0f) BACKWARD_MULT else 1f)
        if (len > 1e-6f) {
            vx = vx / len * speed
            vz = vz / len * speed
        }

        // Прыжок — только с земли; импульс даёт ровно JUMP_HEIGHT блока
        if (jump && wasOnGround) {
            velocity.y = JUMP_VELOCITY
        }

        // Гравитация
        velocity.y -= GRAVITY * dt
        if (velocity.y < -MAX_FALL_SPEED) velocity.y = -MAX_FALL_SPEED.toFloat()

        // Накопление высоты падения (для урона при приземлении)
        if (velocity.y < 0f && !onGround) {
            fallDistance -= velocity.y * dt
        }

        // Движение по осям с разрешением коллизий
        var mx = vx * dt
        var mz = vz * dt
        if (sneaking && wasOnGround) {
            // Приседание: не сходить с края блока (как в Minecraft) —
            // откатываем смещение, если под новым положением нет опоры
            while (mx != 0f && !hasGroundBelow(position.x + mx, position.z, world)) {
                mx = if (kotlin.math.abs(mx) <= 0.05f) 0f else mx - kotlin.math.sign(mx) * 0.05f
            }
            while (mz != 0f && !hasGroundBelow(position.x + mx, position.z + mz, world)) {
                mz = if (kotlin.math.abs(mz) <= 0.05f) 0f else mz - kotlin.math.sign(mz) * 0.05f
            }
        }
        moveAxis(mx, 0f, 0f, world)
        moveAxis(0f, velocity.y * dt, 0f, world)
        moveAxis(0f, 0f, mz, world)

        // Приземление после долгого падения — уведомляем подкласс (урон от падения)
        if (onGround && !wasOnGround && fallDistance > FALL_DAMAGE_MIN_FALL) {
            onLand(fallDistance)
        }
        if (onGround) fallDistance = 0f
    }

    /**
     * Движение по одной оси. Большое смещение (просадка FPS, высокая скорость
     * падения) разбивается на подшаги не более MAX_STEP блоков — иначе AABB
     * может «перепрыгнуть» тонкую стенку/пол и сущность провалится под мир.
     */
    private fun moveAxis(dx: Float, dy: Float, dz: Float, world: World) {
        val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy), kotlin.math.abs(dz))
        val steps = (kotlin.math.ceil(distance / MAX_STEP).toInt()).coerceAtLeast(1)
        val sx = dx / steps
        val sy = dy / steps
        val sz = dz / steps
        repeat(steps) { moveStep(sx, sy, sz, world) }
    }

    /** Один подшаг движения по оси с разрешением коллизий. */
    private fun moveStep(dx: Float, dy: Float, dz: Float, world: World) {
        position.x += dx
        position.y += dy
        position.z += dz

        val minX = position.x - DEFAULT_WIDTH / 2f
        val maxX = position.x + DEFAULT_WIDTH / 2f
        val minY = position.y
        val maxY = position.y + DEFAULT_HEIGHT
        val minZ = position.z - DEFAULT_WIDTH / 2f
        val maxZ = position.z + DEFAULT_WIDTH / 2f

        // ВАЖНО: floor, а не toInt() — toInt() усекает к нулю и ломает
        // коллизии на отрицательных координатах (мир центрирован на нуле)
        val bx0 = kotlin.math.floor(minX).toInt(); val bx1 = kotlin.math.floor(maxX - 1e-4f).toInt()
        val by0 = kotlin.math.floor(minY).toInt(); val by1 = kotlin.math.floor(maxY - 1e-4f).toInt()
        val bz0 = kotlin.math.floor(minZ).toInt(); val bz1 = kotlin.math.floor(maxZ - 1e-4f).toInt()

        // Разрешаем ВСЕ перекрывающиеся блоки по оси движения,
        // а не только первый найденный
        repeat(MAX_RESOLUTION_PASSES) {
            var hit = false
            outer@ for (bx in bx0..bx1) {
                for (by in by0..by1) {
                    for (bz in bz0..bz1) {
                        if (!world.isSolid(bx, by, bz)) continue
                        if (!overlaps(bx, by, bz)) continue // уже разрешён ранее
                        hit = true
                        // Разрешаем коллизию по той оси, по которой двигались
                        when {
                            dx > 0 -> position.x = bx - DEFAULT_WIDTH / 2f - EPSILON
                            dx < 0 -> position.x = bx + 1f + DEFAULT_WIDTH / 2f + EPSILON
                            dy < 0 -> {
                                position.y = by + 1f
                                onGround = true
                                velocity.y = 0f
                            }
                            dy > 0 -> {
                                position.y = by - DEFAULT_HEIGHT - EPSILON
                                velocity.y = 0f
                            }
                            dz > 0 -> position.z = bz - DEFAULT_WIDTH / 2f - EPSILON
                            dz < 0 -> position.z = bz + 1f + DEFAULT_WIDTH / 2f + EPSILON
                        }
                        break@outer
                    }
                }
            }
            if (!hit) return@repeat
        }
    }

    /** Есть ли твёрдая опора под AABB сущности в позиции (px, pz) на текущей высоте. */
    private fun hasGroundBelow(px: Float, pz: Float, world: World): Boolean {
        val minX = px - DEFAULT_WIDTH / 2f
        val maxX = px + DEFAULT_WIDTH / 2f
        val minZ = pz - DEFAULT_WIDTH / 2f
        val maxZ = pz + DEFAULT_WIDTH / 2f
        val by = kotlin.math.floor(position.y - 0.06f).toInt()
        val bx0 = kotlin.math.floor(minX).toInt(); val bx1 = kotlin.math.floor(maxX - 1e-4f).toInt()
        val bz0 = kotlin.math.floor(minZ).toInt(); val bz1 = kotlin.math.floor(maxZ - 1e-4f).toInt()
        for (bx in bx0..bx1) for (bz in bz0..bz1) {
            if (world.isSolid(bx, by, bz)) return true
        }
        return false
    }

    /** Перекрывает ли текущий AABB сущности блок (bx, by, bz). */
    private fun overlaps(bx: Int, by: Int, bz: Int): Boolean {
        val minX = position.x - DEFAULT_WIDTH / 2f
        val maxX = position.x + DEFAULT_WIDTH / 2f
        val minY = position.y
        val maxY = position.y + DEFAULT_HEIGHT
        val minZ = position.z - DEFAULT_WIDTH / 2f
        val maxZ = position.z + DEFAULT_WIDTH / 2f
        return bx + 1f > minX && bx < maxX &&
            by + 1f > minY && by < maxY - 1e-4f &&
            bz + 1f > minZ && bz < maxZ
    }

    companion object {
        /** Габариты гуманоида (игрок, житель): 0.6 × 1.8 × 0.6. */
        const val DEFAULT_WIDTH = 0.6f
        const val DEFAULT_HEIGHT = 1.8f

        /** Базовая скорость ходьбы гуманоида (блоков/с). */
        const val DEFAULT_MOVE_SPEED = 6f

        /** Множитель скорости спринта. */
        const val SPRINT_MULT = 1.3f

        /** Множитель скорости при движении назад (как в Minecraft — 30%). */
        const val BACKWARD_MULT = 0.3f

        /** Множитель скорости приседа. */
        const val SNEAK_MULT = 0.3f

        const val GRAVITY = 24f
        const val MAX_FALL_SPEED = 40

        /** Падение с большей высоты (блоков) вызывает урон при приземлении. */
        const val FALL_DAMAGE_MIN_FALL = 3f

        /** Целевая высота прыжка в блоках. */
        const val JUMP_HEIGHT = 1.25f

        /** Фиксированный шаг физики: 120 Гц — не зависит от FPS. */
        const val PHYSICS_STEP = 1f / 120f

        /** Ограничение кадра: при просадках физика замедляется, но не туннелирует. */
        const val MAX_FRAME_TIME = 0.25f

        /** Максимальное смещение за один подшаг (< половины блока — без туннелирования). */
        const val MAX_STEP = 0.4f

        /** Максимум проходов разрешения коллизий за подшаг. */
        const val MAX_RESOLUTION_PASSES = 4

        /** v²/(2g) = JUMP_HEIGHT блока высоты прыжка. */
        val JUMP_VELOCITY = sqrt(2f * GRAVITY * JUMP_HEIGHT)
        private const val EPSILON = 1e-3f
    }
}
