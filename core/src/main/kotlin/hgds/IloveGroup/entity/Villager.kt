package hgds.IloveGroup.entity

import hgds.IloveGroup.player.Player
import hgds.IloveGroup.world.World
import org.joml.Vector3f
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * Житель (NPC): ходит по миру, подходит к игроку, когда тот рядом,
 * бродит по окрестности и не уходит дальше [WANDER_RADIUS] блоков от
 * точки спавна. Физика (гравитация, коллизии, прыжок) унаследована от
 * [Entity] — «мозг» лишь выбирает направление и подаёт ввод в update.
 */
class Villager(
    x: Float,
    y: Float,
    z: Float
) : Entity(Vector3f(x, y, z)) {

    /** Точка спавна — центр территории (радиус жизни). */
    val homeX = x
    val homeZ = z

    /** Скорость ходьбы жителя (блоков/с) — медленнее игрока. */
    override val moveSpeed: Float = WALK_SPEED

    /** Чем занят житель сейчас. */
    var state = State.IDLE
        private set

    /** Цель блуждания (горизонталь). */
    private var targetX = x
    private var targetZ = z

    /** Сколько секунд осталось в текущем состоянии IDLE. */
    private var idleTimer = 0f

    /** Накопитель «застревания»: идём, но не сдвигаемся с места. */
    private var stuckTimer = 0f
    private var lastProgressX = x
    private var lastProgressZ = z

    /** Прыжок запрошен «мозгом» на следующий шаг физики. */
    private var wantsJump = false

    /** Идти ли в этом кадре (решает «мозг» в think). */
    private var wantsToMove = false

    /** Обновление жителя: «мозг» выбирает цель, физика — движение. */
    fun tick(dt: Float, world: World, player: Player) {
        think(dt, world, player)

        update(dt, if (wantsToMove) 1f else 0f, 0f, wantsJump, world)
        wantsJump = false
    }

    // ----- «Мозг» -----

    private fun think(dt: Float, world: World, player: Player) {
        val distToPlayer = hypot(player.position.x - position.x, player.position.z - position.z)
        val distToHome = hypot(position.x - homeX, position.z - homeZ)

        // Жёсткая граница территории: дальше радиуса не уходим никогда
        if (distToHome > WANDER_RADIUS - 2f) {
            state = State.WANDER
            targetX = homeX
            targetZ = homeZ
        }

        when (state) {
            State.IDLE -> {
                wantsToMove = false
                idleTimer -= dt
                // Увидел игрока рядом — идёт знакомиться
                if (distToPlayer <= FOLLOW_RADIUS) {
                    state = State.FOLLOW
                } else if (idleTimer <= 0f) {
                    // Большинство пауз — прогулка до случайной точки, остальные — снова стоит
                    if (Random.nextFloat() < 0.6f) {
                        state = State.WANDER
                        pickWanderTarget()
                    } else {
                        idleTimer = Random.nextFloat() * 4f + 2f
                    }
                }
            }

            State.FOLLOW -> {
                // Потерял игрока (ушёл далеко) — снова бродит
                if (distToPlayer > FOLLOW_RADIUS + 4f || distToHome >= WANDER_RADIUS) {
                    state = State.IDLE
                    idleTimer = 1f
                } else if (distToPlayer > STOP_DISTANCE) {
                    targetX = player.position.x
                    targetZ = player.position.z
                    wantsToMove = true
                    faceTo(targetX, targetZ)
                    tryJump(world)
                } else {
                    // Дошёл до игрока — стоит рядом лицом к нему
                    wantsToMove = false
                    faceTo(player.position.x, player.position.z)
                }
            }

            State.WANDER -> {
                // Замечает игрока на том же радиусе, что и в покое (иначе «слеп» во время прогулки)
                if (distToPlayer <= FOLLOW_RADIUS) {
                    state = State.FOLLOW
                } else {
                    wantsToMove = true
                    faceTo(targetX, targetZ)
                    tryJump(world)
                    val arrived = hypot(targetX - position.x, targetZ - position.z) < 1.2f
                    if (arrived || stuckTimer > STUCK_TIMEOUT) {
                        state = State.IDLE
                        idleTimer = Random.nextFloat() * 4f + 1.5f
                        if (stuckTimer > STUCK_TIMEOUT) stuckTimer = 0f
                    }
                }
            }
        }

        trackStuck(dt)
    }

    /** Случайная точка прогулки в радиусе спавна (не ближе 4 блоков от текущей). */
    private fun pickWanderTarget() {
        for (attempt in 0 until 8) {
            val angle = Random.nextFloat() * (2f * Math.PI).toFloat()
            val dist = Random.nextFloat() * (WANDER_RADIUS - 6f)
            val tx = homeX + sin(angle) * dist
            val tz = homeZ + cos(angle) * dist
            if (hypot(tx - position.x, tz - position.z) > 4f) {
                targetX = tx
                targetZ = tz
                return
            }
        }
        targetX = homeX
        targetZ = homeZ
    }

    /** Поворот к точке (в градусах, нормализовано в -180..180). */
    private fun faceTo(x: Float, z: Float) {
        val dx = x - position.x
        val dz = z - position.z
        if (dx * dx + dz * dz < 1e-6f) return
        // Впереди у Player: (sin(yaw), -cos(yaw)) → yaw = atan2(dx, -dz)
        yaw = Math.toDegrees(atan2(dx.toDouble(), -dz.toDouble())).toFloat()
        yaw = ((yaw + 180f) % 360f + 360f) % 360f - 180f
    }

    /** Автопрыжок: впереди препятствие в 1 блок, выше — свободно. */
    private fun tryJump(world: World) {
        if (!onGround) return
        val yawRad = Math.toRadians(yaw.toDouble())
        val fx = sin(yawRad).toFloat()
        val fz = -cos(yawRad).toFloat()
        val bx = floor(position.x + fx * 0.8f).toInt()
        val bz = floor(position.z + fz * 0.8f).toInt()
        val feet = floor(position.y + 0.05f).toInt()
        val aheadSolid = world.isSolid(bx, feet, bz)
        val aboveFree = !world.isSolid(bx, feet + 1, bz) && !world.isSolid(bx, feet + 2, bz)
        if (aheadSolid && aboveFree) wantsJump = true
    }

    /** Детектор застревания: идём, но позиция не меняется — сменить цель. */
    private fun trackStuck(dt: Float) {
        val moved = hypot(position.x - lastProgressX, position.z - lastProgressZ)
        lastProgressX = position.x
        lastProgressZ = position.z
        if (state == State.IDLE) {
            stuckTimer = 0f
        } else {
            // Нормальный шаг за кадр — moveSpeed·dt; порог ниже его половины,
            // чтобы обычная ходьба (0.04 блока/кадр при 60 FPS) не считалась застреванием
            stuckTimer = if (moved < moveSpeed * dt * 0.5f) stuckTimer + dt else 0f
        }
    }

    /** Состояния жителя. */
    enum class State { IDLE, WANDER, FOLLOW }

    companion object {
        /** Скорость ходьбы жителя (блоков/с). */
        const val WALK_SPEED = 2.4f

        /** Радиус жизни от точки спавна (блоки). */
        const val WANDER_RADIUS = 200f

        /** С этого расстояния житель замечает игрока и идёт к нему. */
        const val FOLLOW_RADIUS = 8f

        /** Подойдя так близко к игроку, останавливается (блоки). */
        const val STOP_DISTANCE = 2.2f

        /** Сколько секунд можно «упираться» в стену, прежде чем сменить цель. */
        const val STUCK_TIMEOUT = 2f
    }
}
