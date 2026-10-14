package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.AppDirs
import hgds.IloveGroup.util.Lang
import hgds.IloveGroup.world.save.WorldStorage
import hgds.IloveGroup.world.save.WorldSummary

/**
 * Экран «Выбор мира»: список сохранённых миров с прокруткой и кнопки
 * «Играть», «Создать мир», «Удалить» (с подтверждением) и «Назад».
 */
class SingleplayerScreen(
    ctx: UiContext,
    private val onPlay: (WorldSummary) -> Unit,
    private val onCreateWorld: () -> Unit,
    private val onBack: () -> Unit
) : Screen(ctx) {

    /** Список сохранённых миров (обновляется при каждом показе экрана). */
    var worlds = ArrayList<WorldSummary>()
        private set

    var selectedIndex = -1
    var scrollOffset = 0
    private var confirmingDelete = false

    /** Границы зоны списка миров (в пикселях GUI). */
    val listTop = LIST_MARGIN_TOP
    val listBottom get() = ctx.guiHeight - 56

    override fun init() {
        widgets.clear()
        confirmingDelete = false
        scrollOffset = 0
        worlds.clear()
        worlds.addAll(WorldStorage.list(AppDirs.savesDir))
        selectedIndex = if (worlds.isEmpty()) -1 else 0
        buildListAndButtons()
    }

    private fun buildListAndButtons() {
        widgets.clear()
        // Элементы списка миров
        val entryX = ctx.centerX(ENTRY_WIDTH)
        var entryY = listTop
        for (i in worlds.indices) {
            widgets.add(WorldListEntry(this, entryX, entryY, ENTRY_WIDTH, ENTRY_HEIGHT, i))
            entryY += ENTRY_HEIGHT + ENTRY_GAP
        }
        // Нижние ряды кнопок: [Играть][Создать мир] / [Удалить][Назад]
        val buttonW = Button.STANDARD_WIDTH / 2
        val left = ctx.centerX(buttonW * 2 + 4)
        val rowY = ctx.guiHeight - 52
        widgets.add(Button(left, rowY, buttonW, Button.STANDARD_HEIGHT, Lang.get("select_world.play")) { playSelected() })
        widgets.add(Button(left + buttonW + 4, rowY, buttonW, Button.STANDARD_HEIGHT, Lang.get("select_world.create")) { onCreateWorld() })
        widgets.add(Button(left, rowY + 24, buttonW, Button.STANDARD_HEIGHT, Lang.get("select_world.delete")) { startDelete() })
        widgets.add(Button(left + buttonW + 4, rowY + 24, buttonW, Button.STANDARD_HEIGHT, Lang.get("menu.back")) { onBack() })
    }

    private fun buildConfirmWidgets() {
        widgets.clear()
        val x0 = ctx.centerX(Button.STANDARD_WIDTH)
        val y = ctx.guiHeight / 2
        widgets.add(Button(x0, y, 98, Button.STANDARD_HEIGHT, Lang.get("menu.yes")) { deleteSelected() })
        widgets.add(Button(x0 + 102, y, 98, Button.STANDARD_HEIGHT, Lang.get("menu.no")) { cancelDelete() })
    }

    /** Выбор элемента списка; повторный клик по выбранному — играть. */
    fun selectEntry(index: Int) {
        if (index == selectedIndex) {
            playSelected()
        } else {
            selectedIndex = index
        }
    }

    private fun playSelected() {
        if (selectedIndex in worlds.indices) {
            onPlay(worlds[selectedIndex])
        }
    }

    private fun startDelete() {
        if (selectedIndex in worlds.indices) {
            confirmingDelete = true
            buildConfirmWidgets()
        }
    }

    private fun deleteSelected() {
        if (selectedIndex in worlds.indices) {
            WorldStorage.delete(worlds[selectedIndex].dir)
        }
        confirmingDelete = false
        init()
    }

    private fun cancelDelete() {
        confirmingDelete = false
        init()
    }

    override fun renderBackground() {
        ctx.gui.tile(ctx.textures.menuBackground, 0, 0, ctx.guiWidth, ctx.guiHeight, BG_TILE)
    }

    override fun render() {
        renderBackground()
        drawCenteredText(Lang.get("select_world.title"), 15f)
        if (confirmingDelete && selectedIndex in worlds.indices) {
            ctx.gui.fill(0f, 0f, ctx.guiWidth.toFloat(), ctx.guiHeight.toFloat(), 0f, 0f, 0f, 0.65f)
            drawCenteredText(
                Lang.format("select_world.confirm_delete", worlds[selectedIndex].data.name),
                ctx.guiHeight / 2f - 24f
            )
            renderWidgets()
            return
        }
        if (worlds.isEmpty()) {
            drawCenteredText(Lang.get("select_world.empty"), listTop + 20f, 0xA0A0A0)
        }
        renderWidgets()
    }

    override fun onScroll(dy: Double) {
        val totalHeight = worlds.size * (ENTRY_HEIGHT + ENTRY_GAP)
        val visible = listBottom - listTop
        val maxScroll = (totalHeight - visible).coerceAtLeast(0)
        scrollOffset = (scrollOffset - (dy * ENTRY_HEIGHT).toInt()).coerceIn(0, maxScroll)
    }

    override fun onEscape() {
        if (confirmingDelete) cancelDelete() else onBack()
    }

    companion object {
        const val ENTRY_WIDTH = 220
        const val ENTRY_HEIGHT = 24
        const val ENTRY_GAP = 4
        const val LIST_MARGIN_TOP = 30
        const val BG_TILE = 32
    }
}