package hgds.IloveGroup.util

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile

/**
 * Загрузка игровых ресурсов (текстуры, шейдеры, шрифты, json).
 *
 * Ресурсы хранятся в двух местах:
 *  1. <папка игры>/assets/ — внешние ресурсы (приоритет);
 *  2. classpath (внутри jar) — встроенные ресурсы (fallback).
 *
 * При первом запуске всё содержимое resources из jar выгружается в
 * <папка игры>/assets/ (см. exportDefaults) — дальше ресурсы можно править
 * на диске без пересборки jar: внешний файл всегда перекрывает встроенный.
 * Путь ресурса вида "/assets/blocks/blocks.json" или "/gui/menu_background.png".
 */
object Resources {

    /** Корень внешних ресурсов в папке игры. */
    val assetsDir: Path get() = AppDirs.gameDir.resolve("assets")

    /** Открывает ресурс: сначала внешний файл, иначе classpath. */
    fun openStream(path: String): InputStream {
        val rel = path.trimStart('/')
        if (AppDirs.initialized) {
            val external = assetsDir.resolve(rel)
            if (Files.isRegularFile(external)) {
                return Files.newInputStream(external)
            }
        }
        val stream = Resources::class.java.getResourceAsStream(path)
            ?: throw IllegalStateException(
                "Ресурс не найден: $path (нет ни в ${assetsDir.toAbsolutePath()}, ни в jar)"
            )
        return stream
    }

    /** Читает текстовый ресурс целиком (UTF-8). */
    fun readText(path: String): String =
        openStream(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /**
     * Выгружает встроенные ресурсы из jar в <папка игры>/assets/.
     * Уже существующие файлы НЕ перезаписываются (правки пользователя в приоритете).
     * Вызывать после AppDirs.init(). Возвращает число выгруженных файлов.
     */
    fun exportDefaults(): Int {
        val codeSource = Resources::class.java.protectionDomain.codeSource ?: return 0
        val jarPath = try {
            java.net.URI.create(codeSource.location.toURI().toString()).path
        } catch (e: Exception) {
            codeSource.location.path
        }
        var exported = 0
        try {
            if (jarPath.endsWith(".jar") && Files.isRegularFile(Path.of(jarPath))) {
                JarFile(jarPath).use { jar ->
                    for (entry in jar.entries()) {
                        if (entry.isDirectory || entry.name.startsWith("game/") || entry.name.startsWith("META-INF/")) continue
                        if (copyEntry(jar, entry.name)) exported++
                    }
                }
            } else {
                // Запуск из IDE/classes: копируем дерево каталога ресурсов
                val root = Path.of(jarPath)
                if (Files.isDirectory(root)) {
                    Files.walk(root).use { paths ->
                        for (p in paths) {
                            if (Files.isRegularFile(p)) {
                                val rel = root.relativize(p).toString().replace('\\', '/')
                                if (copyFile(p, rel)) exported++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("[hgdsEngine] Не удалось выгрузить ресурсы в ${assetsDir.toAbsolutePath()}: ${e.message}")
        }
        if (exported > 0) {
            println("[hgdsEngine] Выгружено ресурсов в ${assetsDir.toAbsolutePath()}: $exported")
        }
        return exported
    }

    private fun copyEntry(jar: JarFile, entryName: String): Boolean {
        val target = assetsDir.resolve(entryName)
        if (Files.isRegularFile(target)) return false // правки пользователя не трогаем
        Files.createDirectories(target.parent)
        jar.getInputStream(jar.getJarEntry(entryName)).use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return true
    }

    private fun copyFile(source: Path, rel: String): Boolean {
        val target = assetsDir.resolve(rel)
        if (Files.isRegularFile(target)) return false
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        return true
    }
}