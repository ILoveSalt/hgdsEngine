package hgds.IloveGroup.ui.screens

import hgds.IloveGroup.ui.Button
import hgds.IloveGroup.ui.Screen
import hgds.IloveGroup.ui.Slider
import hgds.IloveGroup.ui.UiContext
import hgds.IloveGroup.util.GameOptions
import hgds.IloveGroup.util.Lang

/**
 * Раздел «Графика»: две колонки, как в Minecraft.
 *
 *   Разрешение        | V-Sync
 *   Ограничение FPS   | Интерфейс (1/2/3)
 *   Полноэкранный     | Граф. процессор
 *   Яркость (ползунок)
 *
 * Изменения применяются сразу; V-Sync, полноэкранный режим, яркость,
 * разрешение и лимит FPS меняют параметры окна на ходу.
 */
class VideoSettingsScreen(
    ctx: UiContext,
    private val onDone: () -> Unit,
    private val onChanged: () -> Unit = {},
    private val darkBackground: Boolean = false
) : Screen(ctx) {

    private lateinit var resolutionButton: Button
    private lateinit var vsyncButton: Button
    private lateinit var fpsButton: Button
    private lateinit var uiScaleButton: Button
    private lateinit var fullscreenButton: Button
    private lateinit var gpuButton: Button
    private lateinit var brightnessSlider: Slider
    private var resolutions: List<Pair<Int, Int>> = emptyList()

    override fun init() {
        widgets.clear()
        val buttonY = maxOf(30, minOf(ctx.guiHeight / 4 + 20, ctx.guiHeight - 190))
        val leftX = ctx.centerX(2 * COLUMN_WIDTH + COLUMN_GAP)
        val rightX = leftX + COLUMN_WIDTH + COLUMN_GAP

        // Разрешение | V-Sync
        resolutionButton =
            Button(leftX, buttonY, COLUMN_WIDTH, Button.STANDARD_HEIGHT, resolutionLabel()) { cycleResolution() }
        vsyncButton = Button(rightX, buttonY, COLUMN_WIDTH, Button.STANDARD_HEIGHT, vsyncLabel()) { toggleVsync() }
        add(resolutionButton)
        add(vsyncButton)

        // Ограничение FPS | Интерфейс
        fpsButton = Button(leftX, buttonY + 24, COLUMN_WIDTH, Button.STANDARD_HEIGHT, fpsLabel()) { cycleFpsLimit() }
        uiScaleButton =
            Button(rightX, buttonY + 24, COLUMN_WIDTH, Button.STANDARD_HEIGHT, uiScaleLabel()) { cycleUiScale() }
        add(fpsButton)
        add(uiScaleButton)

        // Полноэкранный | Граф. процессор
        fullscreenButton =
            Button(leftX, buttonY + 48, COLUMN_WIDTH, Button.STANDARD_HEIGHT, fullscreenLabel()) { toggleFullscreen() }
        gpuButton = if (GameOptions.gpus.size > 1) {
            Button(rightX, buttonY + 48, COLUMN_WIDTH, Button.STANDARD_HEIGHT, gpuLabel()) { cycleGpu() }
        } else {
            Button(rightX, buttonY + 48, COLUMN_WIDTH, Button.STANDARD_HEIGHT, gpuLabel()) { }.also { it.enabled = false }
        }
        add(fullscreenButton)
        add(gpuButton)

        // Яркость — ползунок на всю ширину
        brightnessSlider = Slider(
            leftX, buttonY + 84, 2 * COLUMN_WIDTH + COLUMN_GAP, Button.STANDARD_HEIGHT, ctx,
            Lang.get("options.brightness"), GameOptions.brightness / 100f,
            format = { v -> (v * 100).toInt().toString() }
        ) { v ->
            GameOptions.brightness = (v * 100).toInt()
            GameOptions.save()
        }
        add(brightnessSlider)

        add(
            Button(
                ctx.centerX(Button.STANDARD_WIDTH), buttonY + 128,
                Button.STANDARD_WIDTH, Button.STANDARD_HEIGHT, Lang.get("options.done")
            ) { finish() }
        )
    }

    override fun renderBackground() {
        // Из мира — затемнение поверх кадра, из меню — фон как у других окон
        if (darkBackground) {
            ctx.gui.fill(0f, 0f, ctx.guiWidth.toFloat(), ctx.guiHeight.toFloat(), 0f, 0f, 0f, 0.55f)
        } else {
            ctx.gui.tile(ctx.textures.menuBackground, 0, 0, ctx.guiWidth, ctx.guiHeight, BG_TILE)
        }
    }

    override fun render() {
        renderBackground()
        drawCenteredText(Lang.get("options.video"), 15f)
        renderWidgets()
    }

    // ----- Подписи -----

    /** Обрезает текст с «…», если он не влезает в кнопку [maxPx] пикселей. */
    private fun fit(text: String, maxPx: Float): String {
        if (ctx.font.width(text) <= maxPx) return text
        var t = text
        while (t.isNotEmpty() && ctx.font.width("$t…") > maxPx) t = t.dropLast(1)
        return "$t…"
    }

    private fun resolutionLabel(): String =
        if (GameOptions.resolutionW == 0) Lang.get("options.resolution_auto")
        else fit(Lang.format("options.resolution", "${GameOptions.resolutionW}x${GameOptions.resolutionH}"), (COLUMN_WIDTH - 8).toFloat())

    private fun vsyncLabel(): String =
        Lang.format("options.vsync", if (GameOptions.vsync) Lang.get("options.on") else Lang.get("options.off"))

    private fun fpsLabel(): String = fit(
        Lang.format(
            "options.fps_limit",
            when (GameOptions.fpsLimit) {
                0 -> Lang.get("options.fps_off")
                1 -> Lang.get("options.fps_simple")
                else -> Lang.get("options.fps_minimize")
            }
        ), (COLUMN_WIDTH - 8).toFloat()
    )

    private fun uiScaleLabel(): String = Lang.format(
        "options.ui_scale",
        if (GameOptions.uiScale == 0) Lang.get("options.auto") else GameOptions.uiScale.toString()
    )

    private fun fullscreenLabel(): String = fit(
        Lang.format(
            "options.fullscreen", if (GameOptions.fullscreen) Lang.get("options.on") else Lang.get("options.off")
        ), (COLUMN_WIDTH - 8).toFloat()
    )

    private fun gpuLabel(): String {
        val gpu = GameOptions.gpus.getOrNull(GameOptions.gpuIndex)
        val text = when {
            gpu == null -> Lang.get("options.gpu")
            GameOptions.gpus.size > 1 -> Lang.format("options.gpu_choice", gpu)
            else -> Lang.format("options.gpu_choice", gpu)
        }
        return fit(text, (COLUMN_WIDTH - 8).toFloat())
    }

    // ----- Циклы и переключатели -----

    private fun cycleResolution() {
        if (resolutions.isEmpty()) resolutions = ctx.window.videoModes()
        if (resolutions.isEmpty()) return
        val current = resolutions.indexOf(GameOptions.resolutionW to GameOptions.resolutionH)
        val next = resolutions[(current + 1) % resolutions.size]
        GameOptions.resolutionW = next.first
        GameOptions.resolutionH = next.second
        applyResolution()
        resolutionButton.label = resolutionLabel()
        GameOptions.save()
    }

    private fun applyResolution() {
        val w = GameOptions.resolutionW
        val h = GameOptions.resolutionH
        if (w == 0 || h == 0) return
        if (ctx.window.isFullscreen) {
            // В полном экране «Разрешение» не применяется — выходим из полного экрана
            ctx.window.setFullscreen(false)
            GameOptions.fullscreen = false
            fullscreenButton.label = fullscreenLabel()
        } else {
            ctx.window.setWindowSize(w, h)
        }
    }

    private fun toggleVsync() {
        GameOptions.vsync = !GameOptions.vsync
        ctx.window.setVsync(GameOptions.vsync)
        vsyncButton.label = vsyncLabel()
        GameOptions.save()
    }

    private fun cycleFpsLimit() {
        GameOptions.fpsLimit = (GameOptions.fpsLimit + 1) % 3
        fpsButton.label = fpsLabel()
        GameOptions.save()
    }

    private fun cycleUiScale() {
        GameOptions.uiScale = (GameOptions.uiScale + 1) % 4
        uiScaleButton.label = uiScaleLabel()
        onChanged()
        GameOptions.save()
    }

    private fun toggleFullscreen() {
        GameOptions.fullscreen = !GameOptions.fullscreen
        ctx.window.setFullscreen(GameOptions.fullscreen)
        ctx.window.setVsync(GameOptions.vsync)
        fullscreenButton.label = fullscreenLabel()
        GameOptions.save()
    }

    private fun cycleGpu() {
        if (GameOptions.gpus.size > 1) {
            GameOptions.gpuIndex = (GameOptions.gpuIndex + 1) % GameOptions.gpus.size
            gpuButton.label = gpuLabel()
            GameOptions.save()
        }
    }

    private fun finish() {
        GameOptions.save()
        onDone()
    }

    override fun onEscape() {
        finish()
    }

    private companion object {
        const val COLUMN_WIDTH = 150
        const val COLUMN_GAP = 10
        const val BG_TILE = 32
    }
}
