package hgds.IloveGroup.render

import hgds.IloveGroup.world.BlockRegistry
import hgds.IloveGroup.world.ChunkMesher
import hgds.IloveGroup.world.Section
import hgds.IloveGroup.world.TextureAtlasDataLike
import hgds.IloveGroup.world.World

/**
 * Клиентская обвязка над [ChunkMesher] (core): загружает собранную геометрию
 * секции в GPU как GL-меш. Живёт в клиенте, потому что [Mesh] требует OpenGL.
 */
object ChunkMeshFactory {

    /** Строит и загружает меш секции в OpenGL (null — секция пуста). */
    fun build(section: Section, world: World, registry: BlockRegistry, atlas: TextureAtlasDataLike): Mesh? {
        val (vertices, indices) = ChunkMesher.buildGeometry(section, world, registry, atlas)
        if (indices.isEmpty()) return null
        return Mesh(vertices, indices)
    }
}
