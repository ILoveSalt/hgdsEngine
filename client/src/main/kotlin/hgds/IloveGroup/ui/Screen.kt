package hgds.IloveGroup.ui

import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL

/**
 * Базовый экран интерфейса: фон, виджеты и обработка ввода.
 * Экран создаётся заново при каждом показе — виджеты строятся в init().
 */
abstract class Screen(val ctx: UiContext) {

    val widgets = ArrayList<Widget>()

    fun add(widget: Widget) {
        widgets.add(widget)
    }

    /** Создание виджетов экрана (вызывается при каждом показе экрана). */
    abstract fun init()

    /** Фон экрана (панорама, земля и т.п.). */
    open fun renderBackground() {}

    open fun tick(dt: Float) {
        for (w in widgets) if (w.visible) w.tick(dt)
    }

    open fun render() {
        renderBackground()
        renderWidgets()
    }

    fun renderWidgets() {
        for (w in widgets) if (w.visible) w.render(ctx)
    }

    /** Клик мышью; true — клик обработан виджетом. */
    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        for (w in widgets.asReversed()) {
            if (!w.visible) continue
            if (w.mouseClicked(mx, my, button)) return true
        }
        return false
    }

    fun charTyped(codepoint: Int) {
        for (w in widgets) if (w.visible) w.charTyped(codepoint)
    }

    fun keyPressed(key: Int): Boolean {
        val ctrlDown = ctx.window.isKeyPressed(GLFW_KEY_LEFT_CONTROL)
        for (w in widgets.asReversed()) {
            if (!w.visible) continue
            if (w.keyPressed(key, ctrlDown, ctx)) return true
        }
        return false
    }

    /** Рисует текст по центру экрана (для заголовков и сообщений). */
    fun drawCenteredText(text: String, y: Float, color: Int = 0xFFFFFF, shadow: Boolean = true) {
        ctx.font.draw(ctx.gui, text, ctx.guiWidth / 2f - ctx.font.width(text) / 2f, y, 1f, color, shadow = shadow)
    }

    /** Прокрутка колесом мыши. */
    open fun onScroll(dy: Double) {}

    /** Нажатие Esc на экране. */
    open fun onEscape() {}
}