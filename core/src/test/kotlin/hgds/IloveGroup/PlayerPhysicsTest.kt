package hgds.IloveGroup

import hgds.IloveGroup.entity.Entity
import hgds.IloveGroup.player.Player
import hgds.IloveGroup.world.BlockRegistry
import hgds.IloveGroup.world.TerrainGenerator
import hgds.IloveGroup.world.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Тесты физики игрока: гравитация, коллизии, прыжок. */
class PlayerPhysicsTest {

    /** Плоский мир из камня на высоте 64 — предсказуемая опора. */
    private fun flatWorld(): World {
        val registry = BlockRegistry.load()
        val generator = TerrainGenerator(
            seed = 42L,
            grassIndex = registry.index("stone_block"),
            dirtIndex = registry.index("stone_block"),
            stoneIndex = registry.index("stone_block"),
            flat = true
        )
        return World(generator)
    }

    @Test
    fun `player falls onto ground and stays`() {
        val world = flatWorld()
        val player = Player(position = org.joml.Vector3f(0.5f, 70f, 0.5f))
        repeat(240) { player.update(Entity.PHYSICS_STEP, 0f, 0f, false, world) }
        assertTrue(player.onGround, "Игрок должен приземлиться на плоский мир")
        assertEquals(64f, player.position.y, 0.1f, "Стоит на поверхности (Y=64)")
    }

    @Test
    fun `jump reaches about one and a quarter blocks`() {
        val world = flatWorld()
        val player = Player(position = org.joml.Vector3f(0.5f, 64f, 0.5f))
        repeat(120) { player.update(Entity.PHYSICS_STEP, 0f, 0f, false, world) }
        assertTrue(player.onGround)

        var maxY = player.position.y
        repeat(120) {
            player.update(Entity.PHYSICS_STEP, 0f, 0f, true, world)
            maxY = maxOf(maxY, player.position.y)
        }
        val jumpHeight = maxY - 64f
        assertTrue(
            jumpHeight in 1.0f..1.5f,
            "Высота прыжка ~1.25 блока, получено $jumpHeight"
        )
    }

    @Test
    fun `walking moves player forward`() {
        val world = flatWorld()
        val player = Player(position = org.joml.Vector3f(0.5f, 64f, 0.5f))
        repeat(120) { player.update(Entity.PHYSICS_STEP, 0f, 0f, false, world) }

        val startX = player.position.x
        repeat(60) { player.update(Entity.PHYSICS_STEP, 1f, 0f, false, world) }
        assertTrue(player.position.x > startX, "Движение вперёд увеличивает X при yaw=0")
    }

    @Test
    fun `fall damage applies after long fall`() {
        val world = flatWorld()
        val player = Player(position = org.joml.Vector3f(0.5f, 90f, 0.5f))
        repeat(600) { player.update(Entity.PHYSICS_STEP, 0f, 0f, false, world) }
        assertTrue(player.health < Player.MAX_HEALTH, "Падение с 26 блоков наносит урон")
    }

    @Test
    fun `respawn restores health`() {
        val player = Player()
        player.damage(6f)
        assertTrue(player.health < Player.MAX_HEALTH)
        player.respawn()
        assertEquals(Player.MAX_HEALTH, player.health)
    }
}
