package hgds.IloveGroup.engine

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL


/**
 * Окно GLFW: создание контекста OpenGL, обработка клавиатуры и мыши.
 * Мышь захватывается (cursor disabled) для свободного обзора.
 */
class Window(
    private val title: String,
    private var width: Int,
    private var height: Int
) {

    private var handle: Long = NULL
    private var glfwInitialized = false

    /** Время последнего ввода (glfwGetTime) — для ограничения FPS при бездействии. */
    var lastInputTime: Double = 0.0
        private set

    private fun markInput() {
        lastInputTime = org.lwjgl.glfw.GLFW.glfwGetTime()
    }

    private val keys = BooleanArray(GLFW_KEY_LAST + 1)
    private val mouseButtons = BooleanArray(GLFW_MOUSE_BUTTON_LAST + 1)
    private val mouseButtonsPressed = BooleanArray(GLFW_MOUSE_BUTTON_LAST + 1)
    private var mouseX = 0.0
    private var mouseY = 0.0
    private var mouseDX = 0.0
    private var mouseDY = 0.0
    private var firstMouse = true
    private var cursorCaptured = false

    /** Клавиши, нажатые в текущем кадре (одноразовые). */
    private val keysPressed = BooleanArray(GLFW_KEY_LAST + 1)

    /** Символы, введённые с клавиатуры в текущем кадре (любая раскладка). */
    private val typedChars = ArrayList<Int>()

    /** Накопленная прокрутка колесом мыши. */
    private var scrollDelta = 0.0

    /** Создаёт окно и контекст OpenGL. */
    fun init() {
        GLFWErrorCallback.createPrint(System.err).set()

        check(glfwInit()) { "Unable to initialize GLFW" }
        glfwInitialized = true

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)

        handle = glfwCreateWindow(width, height, title, NULL, NULL)
        check(handle != NULL) { "Failed to create the GLFW window" }

        val vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())
        if (vidMode != null) {
            glfwSetWindowPos(handle, (vidMode.width() - width) / 2, (vidMode.height() - height) / 2)
        }

        glfwSetFramebufferSizeCallback(handle) { _, w, h ->
            if (w > 0 && h > 0) {
                width = w
                height = h
                glViewport(0, 0, w, h)
            }
        }
        glfwSetKeyCallback(handle) { _, key, _, action, _ ->
            markInput()
            if (key in keys.indices) {
                when (action) {
                    GLFW_PRESS -> {
                        keys[key] = true
                        keysPressed[key] = true
                    }
                    GLFW_RELEASE -> keys[key] = false
                }
            }
        }
        // Ввод текста с любой раскладки клавиатуры
        glfwSetCharCallback(handle) { _, codepoint ->
            markInput()
            typedChars.add(codepoint)
        }
        // Колесо мыши
        glfwSetScrollCallback(handle) { _, _, dy ->
            markInput()
            scrollDelta += dy
        }
        glfwSetMouseButtonCallback(handle) { _, button, action, _ ->
            markInput()
            if (button in mouseButtons.indices) {
                if (action == GLFW_PRESS) {
                    mouseButtons[button] = true
                    mouseButtonsPressed[button] = true
                } else if (action == GLFW_RELEASE) {
                    mouseButtons[button] = false
                }
            }
        }
        glfwSetCursorPosCallback(handle) { _, x, y ->
            markInput()
            if (cursorCaptured) {
                if (firstMouse) {
                    firstMouse = false
                } else {
                    mouseDX += x - mouseX
                    mouseDY += y - mouseY
                }
            }
            mouseX = x
            mouseY = y
        }

        glfwMakeContextCurrent(handle)
        glfwSwapInterval(1)
        glfwShowWindow(handle)
        GL.createCapabilities()
        glViewport(0, 0, width, height)

        // Курсор в меню должен быть виден: захват включается при входе в мир (captureCursor)
        releaseCursor()
    }

    /** Захватывает мышь для обзора. */
    fun captureCursor() {
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
        cursorCaptured = true
        firstMouse = true
    }

    /** Освобождает мышь. */
    fun releaseCursor() {
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
        cursorCaptured = false
        firstMouse = true
    }

    /** Находится ли курсор в захваченном состоянии (скрыт и привязан к обзору). */
    val isCursorCaptured: Boolean get() = cursorCaptured

    fun toggleCursor() {
        if (cursorCaptured) releaseCursor() else captureCursor()
    }

    /** Включает/выключает V-Sync. */
    fun setVsync(on: Boolean) {
        glfwSwapInterval(if (on) 1 else 0)
    }

    /** Свёрнуто ли окно. */
    fun isIconified(): Boolean = glfwGetWindowAttrib(handle, GLFW_ICONIFIED) == GLFW_TRUE

    /** Полноэкранный ли режим (рамочный, без смены видеорежима — надёжно на Optimus). */
    var isFullscreen: Boolean = false
        private set

    // Сохранённые параметры оконного режима для возврата из полного экрана
    private var windowedX = 0
    private var windowedY = 0
    private var windowedW = width
    private var windowedH = height

    /** Включает/выключает полноэкранный режим (borderless: окно без рамки во весь монитор). */
    fun setFullscreen(on: Boolean) {
        if (on && !isFullscreen) {
            MemoryStack.stackPush().use { stack ->
                val xb = stack.mallocInt(1)
                val yb = stack.mallocInt(1)
                glfwGetWindowPos(handle, xb, yb)
                windowedX = xb[0]
                windowedY = yb[0]
            }
            windowedW = width
            windowedH = height
            val mode = glfwGetVideoMode(glfwGetPrimaryMonitor())
            if (mode != null) {
                isFullscreen = true
                glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_FALSE)
                glfwSetWindowPos(handle, 0, 0)
                glfwSetWindowSize(handle, mode.width(), mode.height())
                glfwFocusWindow(handle)
            }
        } else if (!on && isFullscreen) {
            isFullscreen = false
            glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_TRUE)
            glfwSetWindowPos(handle, windowedX, windowedY)
            glfwSetWindowSize(handle, windowedW, windowedH)
        }
    }

    /** Меняет размер окна (оконный режим). */
    fun setWindowSize(w: Int, h: Int) {
        if (!isFullscreen) glfwSetWindowSize(handle, w, h)
    }

    /** Уникальные видеорежимы монитора (отсортированы по убыванию площади). */
    fun videoModes(): List<Pair<Int, Int>> {
        val modes = glfwGetVideoModes(glfwGetPrimaryMonitor()) ?: return emptyList()
        val seen = HashSet<Long>()
        val result = ArrayList<Pair<Int, Int>>()
        for (i in 0 until modes.capacity()) {
            val m = modes[i]
            val key = m.width().toLong() * 100000L + m.height()
            if (seen.add(key)) result.add(m.width() to m.height())
        }
        modes.free()
        return result.sortedByDescending { it.first * it.second }
    }

    fun isKeyPressed(key: Int): Boolean = key in keys.indices && keys[key]

    fun isMouseButtonDown(button: Int): Boolean =
        button in mouseButtons.indices && mouseButtons[button]

    /** true один раз за клик (сбрасывается методом consumeMouseClicks). */
    fun isMouseClicked(button: Int): Boolean =
        button in mouseButtonsPressed.indices && mouseButtonsPressed[button]

    fun consumeMouseClicks() {
        mouseButtonsPressed.fill(false)
    }

    /** Возвращает накопленное смещение мыши и сбрасывает его. */
    fun consumeMouseDelta(): Pair<Double, Double> {
        val delta = mouseDX to mouseDY
        mouseDX = 0.0
        mouseDY = 0.0
        return delta
    }

    fun shouldClose(): Boolean = handle != NULL && glfwWindowShouldClose(handle)

    fun requestClose() = glfwSetWindowShouldClose(handle, true)

    fun pollEvents() = glfwPollEvents()

    fun swapBuffers() = glfwSwapBuffers(handle)

    fun aspect(): Float = if (height > 0) width.toFloat() / height.toFloat() else 1f

    /** Размер окна в пикселях экрана. */
    val screenWidth: Int get() = width
    val screenHeight: Int get() = height

    /** Позиция курсора в пикселях экрана. */
    val cursorX: Double get() = mouseX
    val cursorY: Double get() = mouseY

    /** true один раз за нажатие клавиши (сбрасывается в endFrame). */
    fun isKeyClicked(key: Int): Boolean = key in keysPressed.indices && keysPressed[key]

    /** Символы, введённые с клавиатуры за кадр. */
    fun consumeTypedChars(): List<Int> {
        val copy = typedChars.toList()
        typedChars.clear()
        return copy
    }

    /** Накопленная прокрутка колесом мыши за кадр. */
    fun consumeScroll(): Double {
        val d = scrollDelta
        scrollDelta = 0.0
        return d
    }

    /** Текст из буфера обмена (для вставки в поля ввода). */
    fun clipboardText(): String? = glfwGetClipboardString(handle)

    /** Сброс одноразовых состояний клавиш — вызывать в конце кадра. */
    fun endFrame() {
        keysPressed.fill(false)
    }

    fun destroy() {
        if (handle != NULL) {
            glfwDestroyWindow(handle)
            handle = NULL
        }
        if (glfwInitialized) {
            glfwTerminate()
            glfwInitialized = false
        }
        glfwSetErrorCallback(null)?.free()
    }
}