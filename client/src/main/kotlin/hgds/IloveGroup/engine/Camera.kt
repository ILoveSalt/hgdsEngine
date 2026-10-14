package hgds.IloveGroup.engine

import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Свободная камера:
 *  - WASD — движение (вперёд — по направлению взгляда),
 *  - Space/Shift (или Ctrl) — вверх/вниз,
 *  - мышь — поворот (yaw) и наклон (pitch).
 */
class Camera(
    val position: Vector3f = Vector3f(0f, 0f, 0f),
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    private val fovDegrees: Float = 70f,
    private val moveSpeed: Float = 10f,
    private val mouseSensitivity: Float = 0.1f,
    private val maxPitchDegrees: Float = 89.9f
) {

    private val forward = Vector3f()
    private val right = Vector3f()
    private val move = Vector3f()
    private val viewMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()

    /** Обновляет камеру по вводу за кадр. */
    fun update(deltaTime: Float, window: Window) {
        updateLook(window.consumeMouseDelta())
        updateMovement(deltaTime, window)
    }

    private fun updateLook(delta: Pair<Double, Double>) {
        val (dx, dy) = delta
        yaw += dx.toFloat() * mouseSensitivity
        // Нормализуем yaw в диапазон -180..180, чтобы не рос бесконечно
        yaw = ((yaw + 180f) % 360f + 360f) % 360f - 180f
        pitch = (pitch - dy.toFloat() * mouseSensitivity).coerceIn(-maxPitchDegrees, maxPitchDegrees)

        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        forward.set(
            (sin(yawRad) * cos(pitchRad)).toFloat(),
            sin(pitchRad).toFloat(),
            (-cos(yawRad) * cos(pitchRad)).toFloat()
        )
        right.set(-forward.z, 0f, forward.x).normalize()
    }

    private fun updateMovement(deltaTime: Float, window: Window) {
        move.set(0f, 0f, 0f)
        if (window.isKeyPressed(GLFW_KEY_W)) move.add(forward)
        if (window.isKeyPressed(GLFW_KEY_S)) move.sub(forward)
        if (window.isKeyPressed(GLFW_KEY_D)) move.add(right)
        if (window.isKeyPressed(GLFW_KEY_A)) move.sub(right)
        if (window.isKeyPressed(GLFW_KEY_SPACE)) move.y += 1f
        if (window.isKeyPressed(GLFW_KEY_LEFT_SHIFT) || window.isKeyPressed(GLFW_KEY_LEFT_CONTROL)) move.y -= 1f

        if (move.lengthSquared() > 1e-6f) {
            move.normalize().mul(moveSpeed * deltaTime)
            position.add(move)
        }
    }

    fun viewMatrix(): Matrix4f = viewMatrix
        .identity()
        .rotateX(-Math.toRadians(pitch.toDouble()).toFloat())
        .rotateY(Math.toRadians(yaw.toDouble()).toFloat())
        .translate(-position.x, -position.y, -position.z)

    fun projectionMatrix(aspect: Float): Matrix4f = projectionMatrix
        .identity()
        .perspective(Math.toRadians(fovDegrees.toDouble()).toFloat(), aspect, NEAR_PLANE, FAR_PLANE)

    companion object {
        private const val NEAR_PLANE = 0.1f
        private const val FAR_PLANE = 1000f
    }
}