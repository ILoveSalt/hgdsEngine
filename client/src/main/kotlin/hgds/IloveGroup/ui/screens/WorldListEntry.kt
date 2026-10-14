package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.ui.Widget
import hgds.IloveGroup.util.Lang
import hgds.IloveGroup.world.save.WorldSummary

/**
 * Элемент списка миров: имя, сид и тип; клик выделяет, повторный — играет.
 * Выделенный элемент подсвечивается спрайтом кнопки.
 */
internal class WorldListEntry(
    private val screen: SingleplayerScreen,
    x: Int, y: Int, width: Int, height: Int,
    private val index: Int
) : Widget(x, y, width, height) {

    private fun visibleY(): Int = y - screen.scrollOffset

    private val summary: WorldSummary get() = screen.worlds[index]

    override fun render(ctx: UiContext) {
        val vy = visibleY()
        if (vy + height <= screen.listTop || vy >= screen.listBottom) return
        val selected = index == screen.selectedIndex
        val hovered = contains(ctx.mouseX, ctx.mouseY + screen.scrollOffset)
        val tex = if (selected) ctx.textures.buttonHighlighted else ctx.textures.button
        ctx.gui.nineSlice(tex, x.toFloat(), vy.toFloat(), width.toFloat(), height.toFloat(), Button.BORDER)
        if (hovered && !selected) {
            ctx.gui.fill(x.toFloat(), vy.toFloat(), width.toFloat(), height.toFloat(), 1f, 1f, 1f, 0.08f)
        }
        val s = summary
        ctx.font.draw(ctx.gui, s.data.name, x + 5f, vy + 4f, 1f, COLOR_NAME)
        val type = if (s.data.flat) Lang.get("world.type.flat") else Lang.get("world.type.normal")
        ctx.font.draw(ctx.gui, Lang.format("select_world.entry_info", s.data.seed, type), x + 5f, vy + 13f, 1f, COLOR_INFO)
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (button != 0) return false
        if (!contains(mx, my + screen.scrollOffset)) return false
        val vy = visibleY()
        if (vy < screen.listTop || vy + height > screen.listBottom) return false
        screen.selectEntry(index)
        return true
    }

    private companion object {
        const val COLOR_NAME = 0xFFFFFF
        const val COLOR_INFO = 0x909090
    }
}