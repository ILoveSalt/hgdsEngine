package hgds.IloveGroup.util

import com.google.gson.GsonBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Настройки игры (как options.txt у Minecraft): хранятся в options.json
 * в игровой папке и применяются на ходу, без перезапуска.
 *
 *  - guiScale: 0 — автоматически (как раньше), иначе фиксированный масштаб 2..5;
 *  - fov: угол обзора в градусах (30..110);
 *  - sensitivity: чувствительность мыши в процентах (10..400);
 *  - renderDistance: дальность прорисовки в чанках (2..32).
 */
object GameOptions {

    /** Масштаб интерфейса: 0 — авто, иначе фиксированное значение в пикселях экрана. */
    var guiScale: Int = 0
        set(value) {
            field = if (value == 0 || value in 2..5) value else 0
        }

    /** Угол обзора (FOV) в градусах. */
    var fov: Int = 70
        set(value) {
            field = value.coerceIn(30, 110)
        }

    /** Чувствительность мыши, % (100 — как раньше). */
    var sensitivity: Int = 100
        set(value) {
            field = value.coerceIn(10, 400)
        }

    /** Дальность прорисовки мира в чанках. */
    var renderDistance: Int = 12
        set(value) {
            field = value.coerceIn(2, 32)
        }

    /** Масштаб интерфейса по-новому: 0 — авто, 1..3 — как в Minecraft. */
    var uiScale: Int = 0
        set(value) {
            field = value.coerceIn(0, 3)
        }

    /** Вертикальная синхронизация (V-Sync). */
    var vsync: Boolean = true

    /**
     * Ограничение FPS: 0 — выключено, 1 — «Простое» (после 20 минут бездействия
     * FPS снижается до 30), 2 — «Свернуть» (в свёрнутом окне FPS = 10).
     */
    var fpsLimit: Int = 1
        set(value) {
            field = value.coerceIn(0, 2)
        }

    /** Яркость экрана: 0..100, 50 — по умолчанию (середина). */
    var brightness: Int = 50
        set(value) {
            field = value.coerceIn(0, 100)
        }

    /** Полноэкранный режим. */
    var fullscreen: Boolean = false

    /** Разрешение (0×0 — «Авто», иначе размер окна/режима экрана). */
    var resolutionW: Int = 0
    var resolutionH: Int = 0

    /** Выбранный графический процессор (индекс в [gpus]). */
    var gpuIndex: Int = 0

    /** Обнаруженные GPU (заполняется при старте игры; не сохраняется). */
    @Transient
    val gpus = ArrayList<String>()

    /** Эффективный масштаб GUI: авто или выбранный «Интерфейс» (1→2, 2→3, 3→4). */
    fun effectiveGuiScale(autoScale: Int): Int =
        if (uiScale == 0) autoScale else uiScale + 1

    /** Множитель яркости для шейдеров: 50 → 1.0 (нейтрально), 0 → 0.7, 100 → 1.3. */
    val brightnessFactor: Float
        get() = 1.0f + (brightness - 50) / 100f * 0.6f

    /**
     * Ищет графические процессоры (Win32_VideoController) и заполняет [gpus].
     * Если определить не удалось — в списке остаётся одна запись «GPU»
     * (кнопка выбора тогда неактивна, как и задумано для одного GPU).
     */
    fun detectGpus() {
        if (gpus.isNotEmpty()) return
        var names: List<String> = emptyList()
        try {
            // Сначала быстрый wmic (может отсутствовать в новых Windows)
            names = queryGpus("wmic", "path", "win32_VideoController", "get", "Name")
            if (names.isEmpty()) {
                names = queryGpus(
                    "powershell", "-NoProfile", "-Command",
                    "(Get-CimInstance Win32_VideoController | Sort-Object Name).Name"
                )
            }
        } catch (_: Exception) {
            // GPU определить не удалось — не критично
        }
        if (names.isEmpty()) names = listOf("GPU")
        gpus.addAll(names)
        if (gpuIndex >= gpus.size) gpuIndex = 0
    }

    private fun queryGpus(vararg command: String): List<String> {
        return try {
            val process = ProcessBuilder(*command).start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("Name", ignoreCase = true) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Transient
    private var dir: Path? = null

    private const val FILE_NAME = "options.json"

    /** Загружает настройки из игровой папки (при отсутствии файла — значения по умолчанию). */
    fun load(optionsDir: Path) {
        dir = optionsDir
        val file = optionsDir.resolve(FILE_NAME)
        try {
            if (!Files.exists(file)) return
            val json = gson.fromJson(
                Files.readString(file, StandardCharsets.UTF_8),
                com.google.gson.JsonObject::class.java
            ) ?: return
            json.get("guiScale")?.takeIf { it.isJsonPrimitive }?.let { guiScale = it.asInt }
            json.get("fov")?.takeIf { it.isJsonPrimitive }?.let { fov = it.asInt }
            json.get("sensitivity")?.takeIf { it.isJsonPrimitive }?.let { sensitivity = it.asInt }
            json.get("renderDistance")?.takeIf { it.isJsonPrimitive }?.let { renderDistance = it.asInt }
            json.get("uiScale")?.takeIf { it.isJsonPrimitive }?.let { uiScale = it.asInt }
            // Совместимость со старыми настройками (guiScale: 2..5 → uiScale 1..3)
            if (uiScale == 0) {
                json.get("guiScale")?.takeIf { it.isJsonPrimitive && it.asInt >= 2 }?.let { uiScale = (it.asInt - 1).coerceIn(1, 3) }
            }
            json.get("vsync")?.takeIf { it.isJsonPrimitive }?.let { vsync = it.asBoolean }
            json.get("fpsLimit")?.takeIf { it.isJsonPrimitive }?.let { fpsLimit = it.asInt }
            json.get("brightness")?.takeIf { it.isJsonPrimitive }?.let { brightness = it.asInt }
            json.get("fullscreen")?.takeIf { it.isJsonPrimitive }?.let { fullscreen = it.asBoolean }
            json.get("resolutionW")?.takeIf { it.isJsonPrimitive }?.let { resolutionW = it.asInt }
            json.get("resolutionH")?.takeIf { it.isJsonPrimitive }?.let { resolutionH = it.asInt }
            json.get("gpuIndex")?.takeIf { it.isJsonPrimitive }?.let { gpuIndex = it.asInt }
        } catch (e: Exception) {
            System.err.println("[hgdsEngine] Не удалось прочитать $file: ${e.message}")
        }
    }

    /** Сохраняет настройки в игровую папку. */
    fun save() {
        val target = dir ?: return
        try {
            Files.createDirectories(target)
            val json = com.google.gson.JsonObject()
            json.addProperty("guiScale", guiScale)
            json.addProperty("fov", fov)
            json.addProperty("sensitivity", sensitivity)
            json.addProperty("renderDistance", renderDistance)
            json.addProperty("uiScale", uiScale)
            json.addProperty("vsync", vsync)
            json.addProperty("fpsLimit", fpsLimit)
            json.addProperty("brightness", brightness)
            json.addProperty("fullscreen", fullscreen)
            json.addProperty("resolutionW", resolutionW)
            json.addProperty("resolutionH", resolutionH)
            json.addProperty("gpuIndex", gpuIndex)
            Files.write(target.resolve(FILE_NAME), gson.toJson(json).toByteArray(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            System.err.println("[hgdsEngine] Не удалось сохранить настройки: ${e.message}")
        }
    }
}
