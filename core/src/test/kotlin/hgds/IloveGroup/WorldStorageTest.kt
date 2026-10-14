package hgds.IloveGroup

import hgds.IloveGroup.player.Player
import hgds.IloveGroup.world.BlockRegistry
import hgds.IloveGroup.world.TerrainGenerator
import hgds.IloveGroup.world.World
import hgds.IloveGroup.world.save.LevelData
import hgds.IloveGroup.world.save.WorldConfig
import hgds.IloveGroup.world.save.WorldStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Тесты сохранения мира: level.json, изменения блоков, позиция игрока. */
class WorldStorageTest {

    @TempDir
    lateinit var savesDir: Path

    private fun flatWorld(seed: Long): World {
        val registry = BlockRegistry.load()
        val gen = TerrainGenerator(
            seed,
            registry.index("grass_block"),
            registry.index("dirt_block"),
            registry.index("stone_block"),
            flat = true
        )
        return World(gen)
    }

    @Test
    fun `create writes level json`() {
        val world = WorldStorage.create(
            savesDir,
            WorldConfig(name = "Тест", seed = 42L, flat = true, renderDistance = 8)
        )
        assertTrue(java.nio.file.Files.exists(world.dir.resolve(WorldStorage.LEVEL_FILE)))
        assertEquals("Тест", world.data.name)
        assertEquals(42L, world.data.seed)
        assertTrue(world.data.flat)
    }

    @Test
    fun `list returns created worlds`() {
        WorldStorage.create(savesDir, WorldConfig("Мир А", 1L, false, 8))
        WorldStorage.create(savesDir, WorldConfig("Мир Б", 2L, false, 8))
        val list = WorldStorage.list(savesDir)
        assertEquals(2, list.size)
    }

    @Test
    fun `save keeps block edits and player position`() {
        val world = WorldStorage.create(savesDir, WorldConfig("Правки", 7L, true, 8))
        val voxel = flatWorld(7L)
        val registry = BlockRegistry.load()
        voxel.setBlock(1, 70, 2, registry.index("oak_log"))

        val player = Player(position = org.joml.Vector3f(1.5f, 66f, 2.5f), yaw = 45f, pitch = 10f)
        val saved = WorldStorage.save(world.dir, world.data, player, voxel)
        assertTrue(saved.edits.isNotEmpty(), "Изменения блоков уходят в level.json")

        val loaded = WorldStorage.load(world.dir)
        assertEquals(45f, loaded.data.player?.yaw)
        voxel.loadEdits(loaded.data.edits)
        assertEquals(
            registry.index("oak_log"),
            voxel.getBlock(1, 70, 2),
            "Правка восстанавливается после загрузки"
        )
    }

    @Test
    fun `load restores time of day and day counter`() {
        val world = WorldStorage.create(savesDir, WorldConfig("Время", 3L, true, 8))
        val data = world.data
        data.timeOfDay = 0.42f
        data.day = 5
        WorldStorage.save(world.dir, data, null, flatWorld(3L))
        val loaded = WorldStorage.load(world.dir)
        assertEquals(0.42f, loaded.data.timeOfDay, 0.0001f)
        assertEquals(5, loaded.data.day)
    }

    @Test
    fun `delete removes world directory`() {
        val world = WorldStorage.create(savesDir, WorldConfig("Удаляемый", 1L, false, 8))
        WorldStorage.delete(world.dir)
        assertEquals(emptyList<LevelData>(), WorldStorage.list(savesDir).map { it.data })
        assertNotNull(world)
    }
}
