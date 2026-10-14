package hgds.IloveGroup.ui

/**
 * Базовый элемент интерфейса. Координаты — в пикселях GUI,
 * (0,0) — левый верхний угол, y направлен вниз.
 */
abstract class Widget(
    var x: Int,
    var y: Int,
    var width: Int,
    var height: Int
) {

    var visible = true

    fun contains(px: Int, py: Int): Boolean =
        px >= x && px < x + width && py >= y && py < y + height

    abstract fun render(ctx: UiContext)

    open fun tick(dt: Float) {}

    /** Возвращает true, если клик обработан этим виджетом. */
    open fun mouseClicked(mx: Int, my: Int, button: Int): Boolean = false

    /** Ввод символа (учитывает любую раскладку клавиатуры). */
    open fun charTyped(codepoint: Int) {}

    /** Возвращает true, если клавиша обработана этим виджетом. */
    open fun keyPressed(key: Int, ctrlDown: Boolean, ctx: UiContext): Boolean = false
}