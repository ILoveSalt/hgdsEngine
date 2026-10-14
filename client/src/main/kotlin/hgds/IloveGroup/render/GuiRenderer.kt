package hgds.IloveGroup.render

import hgds.IloveGroup.engine.Shader
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_STREAM_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.system.MemoryUtil

/**
 * Пакетный рендерер интерфейса: текстурированные и цветные квадры
 * в ортографической проекции. Координаты: (0,0) — левый верхний угол,
 * ось Y направлена вниз (как в Minecraft GUI).
 *
 * Квадры накапливаются в буфер и сбрасываются на экран при смене
 * текстуры или вызове end().
 */
class GuiRenderer {

    private val shader = Shader("/assets/shaders/gui.vert", "/assets/shaders/gui.frag")
    private val vaoId = glGenVertexArrays()
    private val vboId = glGenBuffers()
    private val whiteTexId = buildWhiteTexture()
    private val matrix = Matrix4f()

    private val data = ArrayList<Float>(16384)
    private var activeTexId = -1
    private var disposed = false

    init {
        glBindVertexArray(vaoId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, STRIDE_BYTES, 0L)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE_BYTES, 8L)
        glEnableVertexAttribArray(1)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, STRIDE_BYTES, 16L)
        glEnableVertexAttribArray(2)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    /** Начало кадра GUI: экран шириной width и высотой height в пикселях GUI. */
    fun begin(width: Float, height: Float) {
        shader.use()
        matrix.setOrtho2D(0f, width, height, 0f)
        shader.setMatrix4("uMat", matrix)
        shader.setInt("uTex", 0)
        shader.setFloat("uBrightness", hgds.IloveGroup.util.GameOptions.brightnessFactor)
        data.clear()
        activeTexId = -1
        glActiveTexture(GL_TEXTURE0)
        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    }

    /** Сплошной цветной прямоугольник. */
    fun fill(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) {
        quad(whiteTexId, x, y, w, h, 0f, 0f, 1f, 1f, r, g, b, a)
    }

    /** Фрагмент текстуры: UV задаются в пикселях текстуры. */
    fun blit(
        tex: Texture2D, x: Float, y: Float, w: Float, h: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f
    ) {
        quad(
            tex.textureId, x, y, w, h,
            u0 / tex.width, v0 / tex.height, u1 / tex.width, v1 / tex.height,
            r, g, b, a
        )
    }

    /** Фрагмент «сырой» OpenGL-текстуры (id + размер) — для атласов вне Texture2D. */
    fun blitRaw(
        texId: Int, texW: Int, texH: Int, x: Float, y: Float, w: Float, h: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f
    ) {
        quad(
            texId, x, y, w, h,
            u0 / texW, v0 / texH, u1 / texW, v1 / texH,
            r, g, b, a
        )
    }

    /** Фрагмент текстуры: нормализованные UV (0..1), могут выходить за 1 (для GL_REPEAT). */
    fun blitUV(
        tex: Texture2D, x: Float, y: Float, w: Float, h: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f
    ) {
        quad(tex.textureId, x, y, w, h, u0, v0, u1, v1, r, g, b, a)
    }

    /** Вся текстура целиком. */
    fun blitFull(
        tex: Texture2D, x: Float, y: Float, w: Float, h: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f
    ) {
        quad(tex.textureId, x, y, w, h, 0f, 0f, 1f, 1f, r, g, b, a)
    }

    /** Выкладывает текстуру плитками tileSize × tileSize на заданную область. */
    fun tile(
        tex: Texture2D, x: Int, y: Int, w: Int, h: Int, tileSize: Int,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f
    ) {
        var ty = 0
        while (ty < h) {
            var tx = 0
            while (tx < w) {
                val tw = minOf(tileSize, w - tx)
                val th = minOf(tileSize, h - ty)
                blit(
                    tex, (x + tx).toFloat(), (y + ty).toFloat(), tw.toFloat(), th.toFloat(),
                    0f, 0f,
                    tw * tex.width / tileSize.toFloat(), th * tex.height / tileSize.toFloat(),
                    r, g, b, a
                )
                tx += tileSize
            }
            ty += tileSize
        }
    }

    /**
     * Спрайт, растянутый девятью фрагментами: углы и края рамки (border px)
     * не растягиваются, центр тянется — как nine_slice у Minecraft.
     */
    fun nineSlice(
        tex: Texture2D, x: Float, y: Float, w: Float, h: Float, border: Int,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f
    ) {
        val b = border.toFloat()
        if (w < b * 2f || h < b * 2f) {
            blitFull(tex, x, y, w, h, r, g, b, a)
            return
        }
        val tw = tex.width.toFloat()
        val th = tex.height.toFloat()
        val xs = floatArrayOf(0f, b, tw - b, tw)
        val ys = floatArrayOf(0f, b, th - b, th)
        val dxs = floatArrayOf(x, x + b, x + w - b, x + w)
        val dys = floatArrayOf(y, y + b, y + h - b, y + h)
        for (row in 0..2) {
            for (col in 0..2) {
                val dxA = dxs[col]
                val dxB = dxs[col + 1]
                val dyA = dys[row]
                val dyB = dys[row + 1]
                if (dxB - dxA <= 0f || dyB - dyA <= 0f) continue
                quad(
                    tex.textureId, dxA, dyA, dxB - dxA, dyB - dyA,
                    xs[col] / tw, ys[row] / th, xs[col + 1] / tw, ys[row + 1] / th,
                    r, g, b, a
                )
            }
        }
    }

    private fun quad(
        texId: Int, x: Float, y: Float, w: Float, h: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        if (texId != activeTexId) {
            flush()
            activeTexId = texId
        }
        // 2 треугольника (6 вершин), v=0 — верх текстуры
        addVertex(x, y, u0, v0, r, g, b, a)
        addVertex(x, y + h, u0, v1, r, g, b, a)
        addVertex(x + w, y + h, u1, v1, r, g, b, a)
        addVertex(x, y, u0, v0, r, g, b, a)
        addVertex(x + w, y + h, u1, v1, r, g, b, a)
        addVertex(x + w, y, u1, v0, r, g, b, a)
    }

    private fun addVertex(x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float) {
        data.add(x); data.add(y); data.add(u); data.add(v)
        data.add(r); data.add(g); data.add(b); data.add(a)
    }

    /** Сбрасывает накопленные квадры на экран. */
    fun flush() {
        if (data.isEmpty() || disposed) return
        val buf = MemoryUtil.memAllocFloat(data.size)
        try {
            for (f in data) buf.put(f)
            buf.flip()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, activeTexId)
            glBindVertexArray(vaoId)
            glBindBuffer(GL_ARRAY_BUFFER, vboId)
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STREAM_DRAW)
            glDrawArrays(GL_TRIANGLES, 0, data.size / FLOATS_PER_VERTEX)
            glBindVertexArray(0)
        } finally {
            MemoryUtil.memFree(buf)
        }
        data.clear()
    }

    /** Конец кадра GUI: сброс квадров и восстановление состояния 3D-рендера. */
    fun end() {
        flush()
        glBindTexture(GL_TEXTURE_2D, 0)
        glDisable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        glDeleteTextures(whiteTexId)
        glDeleteBuffers(vboId)
        glDeleteVertexArrays(vaoId)
        shader.dispose()
    }

    /** Белая текстура 1×1 — для сплошной заливки (fill). */
    private fun buildWhiteTexture(): Int {
        val id = glGenTextures()
        val buf = MemoryUtil.memAlloc(4)
        try {
            buf.put(byteArrayOf(-1, -1, -1, -1)).flip()
            glBindTexture(GL_TEXTURE_2D, id)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)
            glBindTexture(GL_TEXTURE_2D, 0)
        } finally {
            MemoryUtil.memFree(buf)
        }
        return id
    }

    companion object {
        const val FLOATS_PER_VERTEX = 8
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * java.lang.Float.BYTES
    }
}