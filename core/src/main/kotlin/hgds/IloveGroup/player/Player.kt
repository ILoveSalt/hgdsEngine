package hgds.IloveGroup.player

import hgds.IloveGroup.entity.Entity
import org.joml.Vector3f

/**
 * Игрок — сущность, управляемая с клавиатуры/мыши. Вся физика движения
 * (гравитация, коллизии, прыжок) живёт в базовом [Entity]; здесь только
 * специфика игрока: здоровье, броня, урон от падения и камера (глаза).
 */
class Player(
    position: Vector3f = Vector3f(0f, 5f, 0f),
    yaw: Float = 0f,
    pitch: Float = 0f
) : Entity(position, yaw, pitch) {

    // ----- Характеристики игрока -----
    /** Здоровье: 0..20 (10 сердец, как в Minecraft). */
    var health: Float = MAX_HEALTH
        private set

    /** Броня: 0..20 (10 значков). Каждый пункт снижает урон на 4% (до 80%). */
    var armor: Float = MAX_ARMOR

    /** Наносит урон (в полусердцах: 2 = одно сердце). */
    fun damage(amount: Float) {
        if (amount <= 0f) return
        health = (health - amount).coerceAtLeast(0f)
    }

    /** Восстанавливает здоровье и характеристики (респавн). */
    fun respawn() {
        health = MAX_HEALTH
    }

    /** Урон от падения (как в Minecraft): броня снижает урон на 4% за пункт. */
    override fun onLand(fallBlocks: Float) {
        damage((fallBlocks - Entity.FALL_DAMAGE_MIN_FALL) * (1f - armor * 0.04f))
    }

    // ----- Камера -----
    /** Центр глаз для камеры (плавно опускается при приседании). */
    val eyeY: Float get() = position.y + eyeSmooth

    /** Текущая сглаженная высота глаз над ногами (блоки). */
    private var eyeSmooth = EYE_HEIGHT

    override fun preUpdate(deltaTime: Float) {
        // Плавное проседание камеры при приседании (1.62 → 1.27, как в MC)
        val targetEye = if (sneaking) SNEAK_EYE_HEIGHT else EYE_HEIGHT
        eyeSmooth += (targetEye - eyeSmooth) * minOf(1f, deltaTime * 12f)
    }

    companion object {
        // Алиасы габаритов и физики — ради совместимости старого кода (Main.kt, тесты).
        // Новая логика должна ссылаться на константы Entity.
        const val WIDTH = Entity.DEFAULT_WIDTH
        const val HEIGHT = Entity.DEFAULT_HEIGHT
        const val MOVE_SPEED = Entity.DEFAULT_MOVE_SPEED
        const val GRAVITY = Entity.GRAVITY
        const val MAX_FALL_SPEED = Entity.MAX_FALL_SPEED
        const val JUMP_HEIGHT = Entity.JUMP_HEIGHT
        const val PHYSICS_STEP = Entity.PHYSICS_STEP
        const val MAX_FRAME_TIME = Entity.MAX_FRAME_TIME
        const val MAX_STEP = Entity.MAX_STEP
        const val MAX_RESOLUTION_PASSES = Entity.MAX_RESOLUTION_PASSES
        val JUMP_VELOCITY = Entity.JUMP_VELOCITY

        /** Высота глаз стоя (как в Minecraft). */
        const val EYE_HEIGHT = 1.62f

        /** Высота глаз при приседании. */
        const val SNEAK_EYE_HEIGHT = 1.27f

        /** Максимальное здоровье (10 сердец = 20 полусердца). */
        const val MAX_HEALTH = 20f

        /** Максимум брони (10 значков = 20 пунктов). */
        const val MAX_ARMOR = 20f
    }
}
