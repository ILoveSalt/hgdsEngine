package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.Slider
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.GameOptions
import hgds.IloveGroup.util.Lang

/**
 * Главный экран настроек в стиле Minecraft: две колонки категорий.
 *
 * Левая колонка:  Онлайн, Внешний вид, Графика, Чат, Язык
 * Правая колонка: FOV, Звуки, Управление, Набор текстур,
 *                 Специальные возможности, Титры и авторы
 *
 * Функциональные разделы: FOV (сразу на экране), Графика (масштаб GUI,
 * дальность прорисовки), Управление (чувствительность мыши), Титры и авторы.
 * Остальные разделы пока показываются неактивными (серые кнопки).
 * Изменения применяются сразу; выход с экрана сохраняет options.json.
 */
class OptionsScreen(
    ctx: UiContext,
    private val onDone: () -> Unit,
    /** Вызывается после любого изменения (Main применяет дальность прорисовки). */
    private val onChanged: () -> Unit = {},
    /** true — настройки открыты из паузы (затемнение поверх мира), иначе — отдельное окно меню. */
    private val darkBackground: Boolean = false
) : Screen(ctx) {

    private fun fovLabel(): String = Lang.format("options.fov", GameOptions.fov)

    override fun init() {
        widgets.clear()
        // Начало сетки кнопок: центрируем по вертикали так, чтобы нижние
        // кнопки (Титры, Готово) не вылезали за экран в любом режиме
        // Две колонки симметрично от центра: centerX уже даёт левый край группы
        val buttonY = maxOf(30, minOf(ctx.guiHeight / 4 + 20, ctx.guiHeight - 194))
        val leftX = ctx.centerX(2 * COLUMN_WIDTH + COLUMN_GAP)
        val rightX = leftX + COLUMN_WIDTH + COLUMN_GAP

        // Левая колонка
        add(disabled(leftX, buttonY, Lang.get("options.online")))
        add(disabled(leftX, buttonY + 24, Lang.get("options.skin")))
        add(
            Button(leftX, buttonY + 48, COLUMN_WIDTH, Button.STANDARD_HEIGHT, Lang.get("options.video")) {
                open(VideoSettingsScreen(ctx, onDone = { returnToOptions() }, onChanged, darkBackground))
            }
        )
        add(disabled(leftX, buttonY + 72, Lang.get("options.chat")))
        add(disabled(leftX, buttonY + 96, Lang.get("options.language")))

        // Правая колонка
        add(
            Slider(
                rightX, buttonY, COLUMN_WIDTH, Button.STANDARD_HEIGHT, ctx,
                Lang.get("options.fov_short"), (GameOptions.fov - 60) / 50f,
                format = { v -> (60 + (v * 50).toInt()).toString() }
            ) { v ->
                GameOptions.fov = 60 + (v * 50).toInt()
                onChanged()
                GameOptions.save()
            }
        )
        add(disabled(rightX, buttonY + 24, Lang.get("options.sound")))
        add(
            Button(rightX, buttonY + 48, COLUMN_WIDTH, Button.STANDARD_HEIGHT, Lang.get("options.controls")) {
                open(ControlsScreen(ctx, onDone = { returnToOptions() }, darkBackground))
            }
        )
        add(disabled(rightX, buttonY + 72, Lang.get("options.resource_pack")))
        add(disabled(rightX, buttonY + 96, Lang.get("options.accessibility")))

        // Титры и авторы — по центру, под обеими колонками
        add(
            Button(
                ctx.centerX(Button.STANDARD_WIDTH), buttonY + 132,
                Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("options.credits")
            ) {
                open(CreditsScreen(ctx, onDone = { returnToOptions() }, darkBackground))
            }
        )

        // Готово — ниже титров, по центру
        add(
            Button(
                ctx.centerX(Button.STANDARD_WIDTH), buttonY + 164,
                Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("options.done")
            ) { finish() }
        )
    }

    /** Неактивная кнопка-заглушка для ещё не реализованного раздела. */
    private fun disabled(x: Int, y: Int, label: String): Button =
        Button(x, y, COLUMN_WIDTH, Button.STANDARD_HEIGHT, label) { }.also { it.enabled = false }

    /** Колбэк Main: показать вложенный экран. */
    var showSub: ((Screen) -> Unit)? = null

    /** Колбэк Main: вернуться к этим настройкам после закрытия вложенного экрана. */
    var onSubClosed: (() -> Unit)? = null

    private fun open(sub: Screen) {
        showSub?.invoke(sub)
    }

    private fun returnToOptions() {
        GameOptions.save()
        onSubClosed?.invoke()
        init()
    }

    /** Пересоздаёт виджеты (нужно после возврата из вложенного экрана). */
    fun rebuild() {
        GameOptions.save()
        init()
    }

    private fun finish() {
        GameOptions.save()
        onDone()
    }

    override fun render() {
        renderBackground()
        drawCenteredText(Lang.get("options.title"), 15f)
        renderWidgets()
    }

    override fun onEscape() {
        finish()
    }

    companion object {
        const val COLUMN_WIDTH = 150
        const val COLUMN_GAP = 10
        const val BG_TILE = 32
    }

    /** Как у других окон меню: из мира — затемнение, из меню — фон с текстурой. */
    override fun renderBackground() {
        if (darkBackground) {
            ctx.gui.fill(0f, 0f, ctx.guiWidth.toFloat(), ctx.guiHeight.toFloat(), 0f, 0f, 0f, 0.55f)
        } else {
            ctx.gui.tile(ctx.textures.menuBackground, 0, 0, ctx.guiWidth, ctx.guiHeight, BG_TILE)
        }
    }
}
