package hgds.IloveGroup.render

import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glPixelStorei
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.system.MemoryUtil

/**
 * Загружает пиксели атласа (сгенерированные TextureAtlasData) в OpenGL-текстуру.
 */
class TextureAtlas(private val data: TextureAtlasData) {

    private val textureId: Int = glGenTextures()
    private var disposed = false

    init {
        val pixels = data.generatePixels()
        val buffer = MemoryUtil.memAlloc(pixels.size)
        try {
            buffer.put(pixels).flip()
            glBindTexture(GL_TEXTURE_2D, textureId)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(
                GL_TEXTURE_2D, 0, GL_RGBA8,
                data.width, data.height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer
            )
            glBindTexture(GL_TEXTURE_2D, 0)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    /** Привязывает атлас к текстурному юниту. */
    fun bind(unit: Int = 0) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, textureId)
    }

    /** OpenGL-id атласа и его размеры (для отрисовки иконок в GUI). */
    val glTextureId: Int get() = textureId
    val width: Int get() = data.width
    val height: Int get() = data.height

    fun dispose() {
        if (!disposed) {
            glDeleteTextures(textureId)
            disposed = true
        }
    }
}