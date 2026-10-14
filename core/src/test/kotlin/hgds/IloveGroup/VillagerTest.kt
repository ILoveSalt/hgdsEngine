package hgds.IloveGroup

import hgds.IloveGroup.entity.Entity
import hgds.IloveGroup.entity.Villager
import hgds.IloveGroup.player.Player
import hgds.IloveGroup.world.BlockRegistry
import hgds.IloveGroup.world.TerrainGenerator
import hgds.IloveGroup.world.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Тесты жителя (NPC): состояния, территория, ходьба. */
class VillagerTest {

    private fun flatWorld(): World {
        val registry = BlockRegistry.load()
        val generator = TerrainGenerator(
            seed = 7L,
            grassIndex = registry.index("stone_block"),
            dirtIndex = registry.index("stone_block"),
            stoneIndex = registry.index("stone_block"),
            flat = true
        )
        return World(generator)
    }

    @Test
    fun `villager spawns on ground and does not fall through`() {
        val world = flatWorld()
        val villager = Villager(0.5f, 70f, 0.5f)
        val player = Player()
        repeat(240) { villager.tick(Entity.PHYSICS_STEP, world, player) }
        assertTrue(villager.onGround, "Житель приземляется")
        assertEquals(64f, villager.position.y, 0.2f)
    }

    @Test
    fun `villager starts idle`() {
        val villager = Villager(0f, 64f, 0f)
        assertEquals(Villager.State.IDLE, villager.state)
    }

    @Test
    fun `villager follows player when close`() {
        val world = flatWorld()
        val villager = Villager(0.5f, 64f, 0.5f)
        val player = Player(position = org.joml.Vector3f(3.5f, 64f, 3.5f))
        repeat(60) { villager.tick(0.05f, world, player) }
        assertEquals(Villager.State.FOLLOW, villager.state, "Игрок в радиусе 8 блоков — житель идёт знакомиться")
    }

    @Test
    fun `villager walks towards wander target`() {
        val world = flatWorld()
        val villager = Villager(0.5f, 64f, 0.5f)
        val player = Player(position = org.joml.Vector3f(100f, 64f, 100f))
        // Прогреваем до появления WANDER (IDLE 2..6 сек — ускоряем большим dt)
        repeat(200) { villager.tick(0.1f, world, player) }
        val startX = villager.position.x
        repeat(120) { villager.tick(0.05f, world, player) }
        assertTrue(
            kotlin.math.abs(villager.position.x - startX) > 0.01f ||
                villager.state == Villager.State.IDLE,
            "Житель либо идёт, либо стоит в IDLE"
        )
    }

    @Test
    fun `villager stays near home`() {
        val world = flatWorld()
        val villager = Villager(0.5f, 64f, 0.5f)
        val player = Player(position = org.joml.Vector3f(100f, 64f, 100f))
        repeat(600) { villager.tick(0.1f, world, player) }
        val dist = kotlin.math.hypot(
            (villager.position.x - villager.homeX).toDouble(),
            (villager.position.z - villager.homeZ).toDouble()
        )
        assertTrue(dist <= Villager.WANDER_RADIUS, "Житель не уходит от точки спавна")
    }
}
