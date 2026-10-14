package hgds.IloveGroup.world.save

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import hgds.IloveGroup.player.Player
import hgds.IloveGroup.world.World
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

/** Настройки создаваемого мира. */
data class WorldConfig(
    val name: String,
    val seed: Long,
    val flat: Boolean,
    val renderDistance: Int
)

/** Данные уровня — файл level.json в папке мира (аналог level.dat у Minecraft). */
data class LevelData(
    val version: Int,
    val name: String,
    val seed: Long,
    val flat: Boolean,
    val renderDistance: Int,
    val created: Long,
    var lastPlayed: Long,
    val player: PlayerData?,
    val edits: Map<String, Int>,
    /** Время суток 0..1 (цикл дня и ночи). */
    var timeOfDay: Float = 0f,
    /** Номер дня (счётчик дней). */
    var day: Int = 1
) {
    /** Сохранённый игрок: позиция и поворот. */
    data class PlayerData(val x: Float, val y: Float, val z: Float, val yaw: Float, val pitch: Float)
}

/** Мир из списка сохранений. */
data class WorldSummary(val dir: Path, val data: LevelData)

/** Загруженный мир: данные уровня + папка на диске. */
class LoadedWorld(val dir: Path, val data: LevelData)

/**
 * Хранилище миров: <папка игры>/saves/<имя мира>/level.json.
 *
 * Рельеф детерминирован по сиду генератора, поэтому на диск сохраняются
 * только сид и настройки мира, позиция игрока и изменения блоков,
 * сделанные игроком (дельта поверх генерации).
 */
object WorldStorage {

    const val LEVEL_FILE = "level.json"
    const val FORMAT_VERSION = 1

    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    /** Создаёт новый мир с уникальной папкой и записывает level.json. */
    fun create(savesDir: Path, config: WorldConfig): LoadedWorld {
        val dir = uniqueDir(savesDir, sanitize(config.name))
        val now = System.currentTimeMillis()
        val data = LevelData(
            FORMAT_VERSION, config.name, config.seed, config.flat,
            config.renderDistance, now, now, null, emptyMap(), 0f, 1
        )
        write(dir, data)
        println("[hgdsEngine] Создан мир «${config.name}» (сид ${config.seed}): ${dir.fileName}")
        return LoadedWorld(dir, data)
    }

    /** Список миров, отсортированный по времени последней игры (сначала свежие). */
    fun list(savesDir: Path): List<WorldSummary> {
        if (!Files.isDirectory(savesDir)) return emptyList()
        val result = ArrayList<WorldSummary>()
        Files.list(savesDir).use { stream ->
            for (dir in stream) {
                if (!Files.isDirectory(dir)) continue
                val levelFile = dir.resolve(LEVEL_FILE)
                if (!Files.exists(levelFile)) continue
                try {
                    val data = read(levelFile) ?: continue
                    result.add(WorldSummary(dir, data))
                } catch (e: Exception) {
                    System.err.println("[hgdsEngine] Не удалось прочитать мир ${dir.fileName}: ${e.message}")
                }
            }
        }
        result.sortByDescending { it.data.lastPlayed }
        return result
    }

    /**
     * Сохраняет мир: метаданные, позицию игрока и изменения блоков.
     * Возвращает обновлённые данные уровня.
     */
    fun save(dir: Path, data: LevelData, player: Player?, world: World): LevelData {
        val edits = world.exportEdits()
        val playerData = player?.let {
            LevelData.PlayerData(it.position.x, it.position.y, it.position.z, it.yaw, it.pitch)
        }
        val updated = LevelData(
            data.version, data.name, data.seed, data.flat, data.renderDistance,
            data.created, System.currentTimeMillis(), playerData, edits,
            data.timeOfDay, data.day
        )
        write(dir, updated)
        println("[hgdsEngine] Мир сохранён: ${dir.fileName} (изменений блоков: ${edits.size})")
        return updated
    }

    /** Загружает данные уровня из папки мира. */
    fun load(dir: Path): LoadedWorld {
        val data = read(dir.resolve(LEVEL_FILE))
            ?: throw IllegalStateException("Не удалось прочитать ${dir.resolve(LEVEL_FILE)}")
        return LoadedWorld(dir, data)
    }

    /** Удаляет папку мира целиком. */
    fun delete(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
        println("[hgdsEngine] Мир удалён: ${dir.fileName}")
    }

    private fun read(levelFile: Path): LevelData? {
        val text = Files.readString(levelFile, StandardCharsets.UTF_8)
        return gson.fromJson(text, LevelData::class.java)
    }

    private fun write(dir: Path, data: LevelData) {
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve(LEVEL_FILE),
            prettyGson.toJson(data),
            StandardCharsets.UTF_8
        )
    }

    /** Убирает из имени мира символы, запрещённые в именах папок Windows. */
    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "world" }
    }

    /** Подбирает уникальное имя папки: «Мир», «Мир (2)», «Мир (3)»… */
    private fun uniqueDir(savesDir: Path, baseName: String): Path {
        var dir = savesDir.resolve(baseName)
        var index = 2
        while (Files.exists(dir)) {
            dir = savesDir.resolve("$baseName ($index)")
            index++
        }
        return dir
    }
}