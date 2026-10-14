package hgds.IloveGroup.engine

import hgds.IloveGroup.util.Resources
import org.joml.Matrix4f
import org.lwjgl.opengl.GL20.glAttachShader
import org.lwjgl.opengl.GL20.glCompileShader
import org.lwjgl.opengl.GL20.glCreateProgram
import org.lwjgl.opengl.GL20.glCreateShader
import org.lwjgl.opengl.GL20.glDeleteProgram
import org.lwjgl.opengl.GL20.glDeleteShader
import org.lwjgl.opengl.GL20.glGetProgramInfoLog
import org.lwjgl.opengl.GL20.glGetProgrami
import org.lwjgl.opengl.GL20.glGetShaderInfoLog
import org.lwjgl.opengl.GL20.glGetShaderi
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glLinkProgram
import org.lwjgl.opengl.GL20.glShaderSource
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniformMatrix4fv
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL20.GL_COMPILE_STATUS
import org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER
import org.lwjgl.opengl.GL20.GL_FALSE
import org.lwjgl.opengl.GL20.GL_LINK_STATUS
import org.lwjgl.opengl.GL20.GL_VERTEX_SHADER
import org.lwjgl.system.MemoryStack

/**
 * Компилирует GLSL-шейдеры из ресурсов и управляет шейдерной программой OpenGL.
 */
class Shader(vertexResource: String, fragmentResource: String) {

    private val programId: Int

    init {
        val vertexId = compile(GL_VERTEX_SHADER, vertexResource)
        val fragmentId = compile(GL_FRAGMENT_SHADER, fragmentResource)

        programId = glCreateProgram()
        glAttachShader(programId, vertexId)
        glAttachShader(programId, fragmentId)
        glLinkProgram(programId)
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            val log = glGetProgramInfoLog(programId)
            throw IllegalStateException("Shader program link error:\n$log")
        }
        glDeleteShader(vertexId)
        glDeleteShader(fragmentId)
    }

    private fun compile(type: Int, resource: String): Int {
        val shaderId = glCreateShader(type)
        glShaderSource(shaderId, Resources.readText(resource))
        glCompileShader(shaderId)
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            val log = glGetShaderInfoLog(shaderId)
            glDeleteShader(shaderId)
            throw IllegalStateException("Failed to compile shader '$resource':\n$log")
        }
        return shaderId
    }

    fun use() = glUseProgram(programId)

    fun setInt(name: String, value: Int) = glUniform1i(uniformLocation(name), value)

    fun setFloat(name: String, value: Float) = glUniform1f(uniformLocation(name), value)

    fun setMatrix4(name: String, matrix: Matrix4f) {
        MemoryStack.stackPush().use { stack ->
            glUniformMatrix4fv(uniformLocation(name), false, matrix.get(stack.mallocFloat(16)))
        }
    }

    private fun uniformLocation(name: String): Int = glGetUniformLocation(programId, name)

    fun dispose() = glDeleteProgram(programId)
}