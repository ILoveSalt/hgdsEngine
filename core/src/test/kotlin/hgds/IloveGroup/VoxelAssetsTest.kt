package hgds.IloveGroup

import hgds.IloveGroup.world.BlockRegistry
import hgds.IloveGroup.world.ChunkMesher
import hgds.IloveGroup.world.Face
import hgds.IloveGroup.world.PerlinNoise
import hgds.IloveGroup.world.Raycaster
import hgds.IloveGroup.world.Section
import hgds.IloveGroup.world.TerrainGenerator
import hgds.IloveGroup.world.World
import hgds.IloveGroup.world.UVRect
import hgds.IloveGroup.world.TextureAtlasDataLike
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Тесты воксельного ядра: шум, генерация, реестр блоков, мешер, raycast. */
class VoxelAssetsTest {

    /** Атлас-заглушка: любая текстура занимает одну плитку. */
    private object FakeAtlas : TextureAtlasDataLike {
        override fun uvRect(name: String): UVRect = UVRect(0f, 0f, 1f, 1f)
    }

    /** Плоский мир с блоками из blocks.json. */
    private fun flatWorld(seed: Long = 11L): World {
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
    fun `perlin noise is deterministic for same seed`() {
        val a = PerlinNoise(123L)
        val b = PerlinNoise(123L)
        for (i in 0 until 50) {
            val x = i * 0.37
            val y = i * -0.11
            assertEquals(a.noise2D(x, y), b.noise2D(x, y), 1e-12, "Один seed — один шум")
        }
    }

    @Test
    fun `perlin noise stays in range`() {
        val noise = PerlinNoise(1L)
        for (i in 0 until 500) {
            val v = noise.noise2D(i * 0.13, i * 0.07)
            assertTrue(v in -1.0..1.0, "Шум в диапазоне [-1;1], получено $v")
        }
    }

    @Test
    fun `terrain height is deterministic and bounded`() {
        val gen = TerrainGenerator(99L, 1, 2, 3)
        assertEquals(gen.height(10, 10), gen.height(10, 10))
        for (x in 0 until 32) {
            for (z in 0 until 32) {
                val h = gen.height(x, z)
                assertTrue(h in TerrainGenerator.MIN_HEIGHT..TerrainGenerator.MAX_HEIGHT)
            }
        }
    }

    @Test
    fun `flat world has constant surface`() {
        val gen = TerrainGenerator(5L, 1, 2, 3, flat = true)
        assertEquals(TerrainGenerator.FLAT_SURFACE, gen.height(0, 0))
        assertEquals(TerrainGenerator.FLAT_SURFACE, gen.height(1000, -1000))
    }

    @Test
    fun `block registry loads blocks json`() {
        val registry = BlockRegistry.load()
        assertTrue(registry.all().isNotEmpty(), "blocks.json содержит блоки")
        assertEquals(1, registry.index("grass_block"), "Первый блок получает индекс 1 (0 — воздух)")
        assertNotNull(registry.byIndex(registry.index("stone_block")))
    }

    @Test
    fun `world generates and reads blocks`() {
        val registry = BlockRegistry.load()
        val world = flatWorld()
        val surface = TerrainGenerator.FLAT_SURFACE
        assertEquals(registry.index("grass_block"), world.getBlock(0, surface, 0), "На поверхности трава")
        assertEquals(
            registry.index("stone_block"),
            world.getBlock(0, surface - TerrainGenerator.DIRT_DEPTH - 1, 0),
            "Глубже — камень"
        )
        assertEquals(BlockRegistry.AIR, world.getBlock(0, surface + 1, 0), "Над поверхностью воздух")
    }

    @Test
    fun `world set and get block`() {
        val registry = BlockRegistry.load()
        val world = flatWorld()
        val y = TerrainGenerator.FLAT_SURFACE + 5
        world.setBlock(3, y, 4, registry.index("oak_log"))
        assertEquals(registry.index("oak_log"), world.getBlock(3, y, 4))
        assertTrue(world.isSolid(3, y, 4))
    }

    @Test
    fun `mesher builds geometry for flat section`() {
        val registry = BlockRegistry.load()
        val world = flatWorld()
        val column = world.ensureColumn(0, 0)!!
        val section = column.sections[TerrainGenerator.FLAT_SURFACE / World.SECTION_SIZE]!!
        val (vertices, indices) = ChunkMesher.buildGeometry(section, world, registry, FakeAtlas)
        assertTrue(indices.isNotEmpty(), "У поверхности есть видимые грани")
        assertEquals(0, indices.size % 6, "Индексы кратны 6 (квады)")
        assertEquals(0, vertices.size % ChunkMesher.FLOATS_PER_VERTEX)
    }

    @Test
    fun `raycast hits ground and returns normal`() {
        val registry = BlockRegistry.load()
        val world = flatWorld()
        val surface = TerrainGenerator.FLAT_SURFACE
        val hit = Raycaster.cast(world, 0.5f, surface + 5f, 0.5f, 0f, -1f, 0f, 10f)
        assertNotNull(hit, "Луч вниз попадает в землю")
        assertEquals(surface, hit!!.block.y, "Первый твёрдый блок — поверхность")
        assertEquals(1, hit.normal.y, "Нормаль верхней грани направлена вверх")
    }

    @Test
    fun `raycast misses empty sky`() {
        val world = flatWorld()
        val hit = Raycaster.cast(world, 0.5f, 400f, 0.5f, 0f, 1f, 0f, 10f)
        assertEquals(null, hit, "Луч вверх в небо не попадает")
    }

    @Test
    fun `face enum covers all six sides`() {
        assertEquals(6, Face.entries.size)
    }
}
