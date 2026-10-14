package hgds.IloveGroup.render

import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL11.GL_REPEAT
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
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * PNG-текстура интерфейса, загружаемая из classpath (например "/gui/menu_background.png").
 * Данные загружаются без переворота: первая строка данных — верх картинки,
 * а OpenGL считает её v=0. Поэтому v=0 — верх изображения и весь GUI
 * считает UV в экранных координатах (y вниз), как в Minecraft GUI.
 */
class Texture2D private constructor(
    val textureId: Int,
    val width: Int,
    val height: Int,
    repeatS: Boolean,
    repeatT: Boolean
) {

    private var disposed = false

    init {
        glBindTexture(GL_TEXTURE_2D, textureId)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, if (repeatS) GL_REPEAT else GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, if (repeatT) GL_REPEAT else GL_CLAMP_TO_EDGE)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    /** Привязывает текстуру к текстурному юниту. */
    fun bind(unit: Int = 0) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, textureId)
    }

    fun dispose() {
        if (!disposed) {
            glDeleteTextures(textureId)
            disposed = true
        }
    }

    companion object {

        /**
         * Загружает PNG из classpath.
         * linear=true — сглаживание (для панорамы), иначе пиксельный фильтр.
         */
        fun load(
            resource: String,
            linear: Boolean = false,
            repeatS: Boolean = false,
            repeatT: Boolean = false
        ): Texture2D = fromImage(readImage(resource), linear, repeatS, repeatT)

        /**
         * Создаёт текстуру из готового изображения в памяти
         * (например, растеризованного атласа глифов TrueType-шрифта).
         */
        fun fromImage(
            image: BufferedImage,
            linear: Boolean = false,
            repeatS: Boolean = false,
            repeatT: Boolean = false
        ): Texture2D {
            val texId = glGenTextures()
            val tex = Texture2D(texId, image.width, image.height, repeatS, repeatT)
            val buffer = MemoryUtil.memAlloc(image.width * image.height * BYTES_PER_PIXEL)
            try {
                buffer.put(extractPixels(image)).flip()
                tex.bind(0)
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, if (linear) GL_LINEAR else GL_NEAREST)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, if (linear) GL_LINEAR else GL_NEAREST)
                glTexImage2D(
                    GL_TEXTURE_2D, 0, GL_RGBA8,
                    image.width, image.height, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, buffer
                )
                glBindTexture(GL_TEXTURE_2D, 0)
            } finally {
                MemoryUtil.memFree(buffer)
            }
            return tex
        }

        /** Читает PNG (сначала внешние ресурсы в папке игры, затем classpath). */
        fun readImage(resource: String): BufferedImage {
            val stream: InputStream = hgds.IloveGroup.util.Resources.openStream(resource)
            stream.use {
                return ImageIO.read(it)
                    ?: throw IllegalStateException("Не удалось прочитать PNG: $resource")
            }
        }

        /** Пиксели PNG (ARGB int на пиксель), строка 0 — верх изображения. */
        fun readPixelsTopDown(resource: String): IntArray {
            val image = readImage(resource)
            return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        }

        /**
         * RGBA-байты в порядке сверху вниз: строка данных 0 = верх изображения.
         * OpenGL считает первую строку данных v=0, поэтому v=0 — верх картинки
         * и весь GUI может считать UV в экранных координатах (y вниз).
         * Контракт проверяется тестом Texture2DTest.
         */
        fun extractPixels(image: BufferedImage): ByteArray {
            val argb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            val out = ByteArray(image.width * image.height * BYTES_PER_PIXEL)
            var i = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val p = argb[y * image.width + x]
                    out[i++] = ((p shr 16) and 0xFF).toByte()
                    out[i++] = ((p shr 8) and 0xFF).toByte()
                    out[i++] = (p and 0xFF).toByte()
                    out[i++] = ((p shr 24) and 0xFF).toByte()
                }
            }
            return out
        }

        const val BYTES_PER_PIXEL = 4
    }
}