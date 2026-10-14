package hgds.IloveGroup.render

import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_LINES
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glBufferSubData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.glDisableVertexAttribArray
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.system.MemoryUtil

/**
 * Рендер цветных линий (границы чанков, обводка выбранного блока).
 * Линии загружаются каждый кадр в динамический VBO.
 */
class LineRenderer {

    private val vaoId = glGenVertexArrays()
    private val vboId = glGenBuffers()
    private var capacityFloats = 0
    private var disposed = false

    init {
        glBindVertexArray(vaoId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, LINE_STRIDE_BYTES, 0L)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 4, GL_FLOAT, false, LINE_STRIDE_BYTES, 12L)
        glEnableVertexAttribArray(1)
        glBindVertexArray(0)
    }

    /** Загружает линии. data: [x,y,z, r,g,b,a] * 2 на линию. */
    fun upload(data: FloatArray) {
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        if (data.size > capacityFloats) {
            capacityFloats = maxOf(data.size, 4096)
            glBufferData(GL_ARRAY_BUFFER, capacityFloats * java.lang.Float.BYTES.toLong(), GL_DYNAMIC_DRAW)
        }
        if (data.isNotEmpty()) {
            val buffer = MemoryUtil.memAllocFloat(data.size)
            try {
                buffer.put(data).flip()
                glBufferSubData(GL_ARRAY_BUFFER, 0L, buffer)
            } finally {
                MemoryUtil.memFree(buffer)
            }
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    fun draw(lineCount: Int) {
        if (lineCount <= 0 || disposed) return
        glBindVertexArray(vaoId)
        glDrawArrays(GL_LINES, 0, lineCount * VERTICES_PER_LINE)
        glBindVertexArray(0)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        glDeleteBuffers(vboId)
        glDeleteVertexArrays(vaoId)
    }

    companion object {
        const val FLOATS_PER_LINE = 14 // 2 вершины × (3 позиции + 4 цвета)
        const val VERTICES_PER_LINE = 2
        const val LINE_STRIDE_BYTES = 7 * 4
    }
}