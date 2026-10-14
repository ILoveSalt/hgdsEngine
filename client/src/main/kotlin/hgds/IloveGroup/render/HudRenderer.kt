package hgds.IloveGroup.render

/**
 * Текстуры и геометрия игрового HUD в стиле Minecraft:
 * хотбар (hotbar.png), инвентарь (inventory.png), сердца (папка hearts) и броня (папка armors).
 *
 * Все PNG — канвы 256×256, спрайты лежат по известным смещениям:
 *  - полоса хотбара: (7,141)-(186,159)  → 179×18, 10 слотов;
 *  - рамка выбранного слота: (193,141)-(211,159) → 18×18;
 *  - панель инвентаря: (0,0)-(218,166) → 218×166;
 *  - сердца: (16,64)-(224,240); броня: (16,16)-(240,240).
 */
class HudRenderer {

    val hotbar = Texture2D.load("/assets/textures/game_1/gui/hotbar.png")
    val inventory = Texture2D.load("/assets/textures/game_1/gui/inventory.png")

    val heartFull = Texture2D.load("/assets/textures/game_1/gui/hearts/full.png")
    val heartHalf = Texture2D.load("/assets/textures/game_1/gui/hearts/half.png")
    val heartEmpty = Texture2D.load("/assets/textures/game_1/gui/hearts/dead.png")

    val armorFull = Texture2D.load("/assets/textures/game_1/gui/armors/full_a.png")
    val armorHalf = Texture2D.load("/assets/textures/game_1/gui/armors/half_a.png")
    val armorEmpty = Texture2D.load("/assets/textures/game_1/gui/armors/non_a.png")

    fun dispose() {
        hotbar.dispose()
        inventory.dispose()
        heartFull.dispose()
        heartHalf.dispose()
        heartEmpty.dispose()
        armorFull.dispose()
        armorHalf.dispose()
        armorEmpty.dispose()
    }

    /** Рисует полосу хотбара, левый верхний угол (x, y). */
    fun drawHotbarBar(gui: GuiRenderer, x: Float, y: Float) {
        gui.blit(hotbar, x, y, HOTBAR_W, HOTBAR_H, 7f, 141f, 186f, 159f)
    }

    /** Рамка выбранного слота по центру слота [slot]: белая полупрозрачная (как в Minecraft). */
    fun drawSlotHighlight(gui: GuiRenderer, x: Float, y: Float, size: Float = SEL_SIZE) {
        gui.fill(x - 1f, y - 1f, size + 2f, size + 2f, 1f, 1f, 1f, 0.75f)
    }

    /** Слот дополнительной руки (спрайт справа от полосы хотбара в hotbar.png). */
    fun drawOffhandSlot(gui: GuiRenderer, x: Float, y: Float) {
        gui.blit(hotbar, x, y, 18f, 18f, 193f, 141f, 211f, 159f)
    }

    /** Один значок (сердце/броня): [tex] — вся канва со спрайтом. */
    private fun drawIcon(gui: GuiRenderer, tex: Texture2D, x: Float, y: Float, size: Float, u0: Float, v0: Float, u1: Float, v1: Float) {
        gui.blit(tex, x, y, size, size, u0, v0, u1, v1)
    }

    /**
     * Ряд сердец (10 штук, шаг 8 px, как в Minecraft).
     * @param health 0..20 (полусердца).
     */
    fun drawHearts(gui: GuiRenderer, x: Float, y: Float, health: Float) {
        for (i in 0 until HEARTS) {
            val tex = when {
                health >= (i + 1) * 2f -> heartFull
                health >= i * 2f + 1f -> heartHalf
                else -> heartEmpty
            }
            drawIcon(gui, tex, x + i * ICON_STEP, y, ICON_SIZE, 16f, 64f, 224f, 240f)
        }
    }

    /**
     * Ряд брони (10 значков справа налево, шаг 8 px, как в Minecraft).
     * @param armor 0..20 пунктов.
     */
    fun drawArmor(gui: GuiRenderer, rightX: Float, y: Float, armor: Float) {
        for (i in 0 until HEARTS) {
            val tex = when {
                armor >= (i + 1) * 2f -> armorFull
                armor >= i * 2f + 1f -> armorHalf
                else -> armorEmpty
            }
            // Правая половина бара заполняется справа налево
            drawIcon(gui, tex, rightX - ICON_SIZE - i * ICON_STEP, y, ICON_SIZE, 16f, 16f, 240f, 240f)
        }
    }

    /** Панель инвентаря по центру экрана. */
    fun drawInventoryPanel(gui: GuiRenderer, x: Float, y: Float) {
        gui.blit(inventory, x, y, INV_W, INV_H, 0f, 0f, 218f, 166f)
    }

    companion object {
        /** Полоса хотбара: ширина и высота спрайта (px). */
        const val HOTBAR_W = 179f
        const val HOTBAR_H = 18f

        /** Ширина одного слота. */
        const val SLOT_W = HOTBAR_W / 10f
        const val SLOTS = 10

        /** Индекс слота дополнительной руки в общем массиве предметов (после 10 слотов хотбара). */
        const val OFFHAND_SLOT = 10

        /** Рамка выбора. */
        const val SEL_SIZE = 18f

        /** Значки сердец/брони. */
        const val HEARTS = 10
        const val ICON_SIZE = 9f
        const val ICON_STEP = 8f

        /** Панель инвентаря. */
        const val INV_W = 218f
        const val INV_H = 166f

        /** Слот под мышью в координатах бара (−1 — мимо). Ячейки: шаг 18 px, интерьер с +1. */
        fun slotAt(barX: Float, barY: Float, mx: Float, my: Float): Int {
            if (my < barY || my > barY + HOTBAR_H) return -1
            val slot = ((mx - barX - 1f) / 18f).toInt()
            return if (slot in 0 until SLOTS) slot else -1
        }

        // ----- Геометрия панели инвентаря (inventory.png, 218×166) -----
        // Сетка: 10 колонок с шагом 18 px от x=8; 3 ряда основной сетки (y=83,101,119)
        // и нижний ряд (y=141). Слоты 18×18.

        /** Шаг и размер ячейки на арте панели. Границы ячеек с x=7, интерьер с x=8. */
        const val PANEL_CELL = 18f
        const val PANEL_GRID_X = 7f
        const val PANEL_COLS = 10
        const val PANEL_MAIN_ROWS = 3

        /** Y верхней границы основной сетки и нижнего ряда (внутри панели). */
        const val PANEL_MAIN_Y = 83f
        const val PANEL_HOTBAR_ROW_Y = 141f

        /** X ячейки доп. руки на панели (спрайт справа от нижнего ряда, как в hotbar.png). */
        const val PANEL_OFFHAND_X = 193f

        /**
         * Слот панели под курсором (координаты в системе панели).
         * Индексы: нижний ряд 0..9 — хотбар, 10 — доп. рука (ячейка справа
         * от нижнего ряда), основной инвентарь 11..40 (ряд r, колонка c → 11 + r*10 + c).
         */
        fun panelSlotAt(panelX: Float, panelY: Float, mx: Float, my: Float): Int {
            fun cell(col: Int, rowY: Float): Int {
                val x = panelX + PANEL_GRID_X + col * PANEL_CELL
                val y = panelY + rowY
                return if (mx >= x && mx < x + PANEL_CELL && my >= y && my < y + PANEL_CELL) 1 else 0
            }
            // Нижний ряд — хотбар 0..9
            for (c in 0 until HudRenderer.SLOTS) {
                if (cell(c, PANEL_HOTBAR_ROW_Y) == 1) return c
            }
            // Ячейка доп. руки справа от нижнего ряда (слот 10)
            val ox = panelX + PANEL_OFFHAND_X
            val oy = panelY + PANEL_HOTBAR_ROW_Y
            if (mx >= ox && mx < ox + PANEL_CELL && my >= oy && my < oy + PANEL_CELL) {
                return HudRenderer.OFFHAND_SLOT
            }
            // Основная сетка (10 колонок × 3 ряда → 11..40; 10 — доп. рука)
            for (r in 0 until PANEL_MAIN_ROWS) {
                for (c in 0 until PANEL_COLS) {
                    if (cell(c, PANEL_MAIN_Y + r * PANEL_CELL) == 1) return HudRenderer.OFFHAND_SLOT + 1 + r * PANEL_COLS + c
                }
            }
            return -1
        }
    }
}
