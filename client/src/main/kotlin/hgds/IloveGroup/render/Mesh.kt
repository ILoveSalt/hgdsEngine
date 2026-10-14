package hgds.IloveGroup.render

import hgds.IloveGroup.world.SectionMesh
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

/**
 * Меш на VAO/VBO/EBO.
 * Вершина: позиция(3) + UV(2) + яркость(1) = 6 float (24 байта).
 */
class Mesh(vertices: FloatArray, indices: IntArray) : SectionMesh {

    private val vaoId = glGenVertexArrays()
    private val vboId = glGenBuffers()
    private val eboId = glGenBuffers()
    var indexCount: Int = indices.size
        private set
    private var disposed = false

    init {
        require(indexCount % INDICES_PER_QUAD == 0) { "Index count must be a multiple of $INDICES_PER_QUAD" }

        glBindVertexArray(vaoId)

        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        uploadFloats(vertices)

        glVertexAttribPointer(ATTRIBUTE_POSITION, 3, GL_FLOAT, false, STRIDE_BYTES, 0L)
        glEnableVertexAttribArray(ATTRIBUTE_POSITION)
        glVertexAttribPointer(ATTRIBUTE_UV, 2, GL_FLOAT, false, STRIDE_BYTES, 12L)
        glEnableVertexAttribArray(ATTRIBUTE_UV)
        glVertexAttribPointer(ATTRIBUTE_BRIGHTNESS, 1, GL_FLOAT, false, STRIDE_BYTES, 20L)
        glEnableVertexAttribArray(ATTRIBUTE_BRIGHTNESS)

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId)
        uploadInts(indices)

        glBindVertexArray(0)
    }

    private fun uploadFloats(data: FloatArray) {
        val buffer = MemoryUtil.memAllocFloat(data.size)
        try {
            buffer.put(data).flip()
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun uploadInts(data: IntArray) {
        val buffer = MemoryUtil.memAllocInt(data.size)
        try {
            buffer.put(data).flip()
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    override fun draw() {
        if (disposed) return
        glBindVertexArray(vaoId)
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L)
        glBindVertexArray(0)
    }

    /**
     * Перезаливает вершины и индексы (GL_DYNAMIC_DRAW) — для динамических мешей,
     * которые перестраиваются каждый кадр (например, квады солнца и луны).
     */
    override fun upload(vertices: FloatArray, indices: IntArray) {
        if (disposed) return
        require(indices.size % INDICES_PER_QUAD == 0) { "Index count must be a multiple of $INDICES_PER_QUAD" }
        glBindVertexArray(vaoId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        uploadFloats(vertices)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId)
        uploadInts(indices)
        indexCount = indices.size
        glBindVertexArray(0)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        MemoryStack.stackPush().use { stack ->
            glDeleteBuffers(stack.mallocInt(2).put(vboId).put(eboId).flip())
            glDeleteVertexArrays(stack.mallocInt(1).put(vaoId).flip())
        }
    }

    companion object {
        const val FLOATS_PER_VERTEX = 6
        const val INDICES_PER_QUAD = 6
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * java.lang.Float.BYTES
        private const val ATTRIBUTE_POSITION = 0
        private const val ATTRIBUTE_UV = 1
        private const val ATTRIBUTE_BRIGHTNESS = 2
    }
}