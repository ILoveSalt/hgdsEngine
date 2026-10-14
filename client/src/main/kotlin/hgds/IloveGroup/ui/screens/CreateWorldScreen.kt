package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.TextField
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.Lang
import hgds.IloveGroup.world.save.WorldConfig
import kotlin.random.Random

/**
 * Экран «Настройки мира»: имя мира, сид генератора, тип мира
 * (обычный / суперплоский) и дальность прорисовки.
 * Кнопки «Создать новый мир» и «Отмена».
 */
class CreateWorldScreen(
    ctx: UiContext,
    private val onCreate: (WorldConfig) -> Unit,
    private val onCancel: () -> Unit
) : Screen(ctx) {

    private lateinit var nameField: TextField
    private lateinit var seedField: TextField
    private lateinit var typeButton: Button
    private lateinit var distanceButton: Button

    /** 0 — обычный мир, 1 — суперплоский. */
    private var worldTypeIndex = 0
    private var renderDistance = DEFAULT_RENDER_DISTANCE

    override fun init() {
        widgets.clear()
        val fieldX = ctx.centerX(FIELD_WIDTH)
        nameField = TextField(fieldX, NAME_FIELD_Y, FIELD_WIDTH).also {
            it.hint = Lang.get("create_world.name_hint")
            it.text = Lang.get("create_world.name_hint")
            it.focused = true
            widgets.add(it)
        }
        seedField = TextField(fieldX, SEED_FIELD_Y, FIELD_WIDTH).also {
            it.hint = Lang.get("create_world.seed_hint")
            it.maxLength = 32
            widgets.add(it)
        }
        typeButton = Button(fieldX, TYPE_BUTTON_Y, FIELD_WIDTH, Button.STANDARD_HEIGHT, typeLabel()) { cycleType() }
        distanceButton = Button(fieldX, DISTANCE_BUTTON_Y, FIELD_WIDTH, Button.STANDARD_HEIGHT, distanceLabel()) { cycleDistance() }
        widgets.add(typeButton)
        widgets.add(distanceButton)
        // Нижний ряд: [Создать новый мир][Отмена]
        val rowY = ctx.guiHeight - 26
        val left = ctx.centerX(BOTTOM_ROW_WIDTH)
        widgets.add(Button(left, rowY, CREATE_WIDTH, Button.STANDARD_HEIGHT, Lang.get("create_world.create")) { create() })
        widgets.add(Button(left + CREATE_WIDTH + 4, rowY, CANCEL_WIDTH, Button.STANDARD_HEIGHT, Lang.get("create_world.cancel")) { onCancel() })
    }

    private fun typeLabel(): String = Lang.format(
        "create_world.type",
        if (worldTypeIndex == 0) Lang.get("world.type.normal") else Lang.get("world.type.flat")
    )

    private fun distanceLabel(): String = Lang.format("create_world.render_distance", renderDistance)

    private fun cycleType() {
        worldTypeIndex = 1 - worldTypeIndex
        typeButton.label = typeLabel()
    }

    private fun cycleDistance() {
        val i = (RENDER_DISTANCES.indexOf(renderDistance) + 1) % RENDER_DISTANCES.size
        renderDistance = RENDER_DISTANCES[i]
        distanceButton.label = distanceLabel()
    }

    /** Пустой сид — случайный; иначе число или хэш строки. */
    private fun create() {
        val name = nameField.text.trim().ifEmpty { Lang.get("create_world.name_hint") }
        val seedText = seedField.text.trim()
        val seed = if (seedText.isEmpty()) {
            Random.Default.nextLong()
        } else {
            seedText.toLongOrNull() ?: seedText.hashCode().toLong()
        }
        onCreate(WorldConfig(name, seed, worldTypeIndex == 1, renderDistance))
    }

    override fun renderBackground() {
        ctx.gui.tile(ctx.textures.menuBackground, 0, 0, ctx.guiWidth, ctx.guiHeight, BG_TILE)
    }

    override fun render() {
        renderBackground()
        drawCenteredText(Lang.get("create_world.title"), 15f)
        val fieldX = ctx.centerX(FIELD_WIDTH).toFloat()
        ctx.font.draw(ctx.gui, Lang.get("create_world.name_label"), fieldX, NAME_FIELD_Y - 12f, 1f, LABEL_COLOR)
        ctx.font.draw(ctx.gui, Lang.get("create_world.seed_label"), fieldX, SEED_FIELD_Y - 12f, 1f, LABEL_COLOR)
        renderWidgets()
    }

    override fun onEscape() {
        onCancel()
    }

    companion object {
        const val FIELD_WIDTH = 200
        const val BOTTOM_ROW_WIDTH = 204 // Создать (148) + зазор (4) + Отмена (52)
        const val CREATE_WIDTH = 148
        const val CANCEL_WIDTH = 52
        const val NAME_FIELD_Y = 42
        const val SEED_FIELD_Y = 84
        const val TYPE_BUTTON_Y = 116
        const val DISTANCE_BUTTON_Y = 140
        const val DEFAULT_RENDER_DISTANCE = 8
        const val BG_TILE = 32
        const val LABEL_COLOR = 0xA0A0A0
        val RENDER_DISTANCES = intArrayOf(4, 6, 8, 10, 12, 16)
    }
}