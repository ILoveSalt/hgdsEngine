package hgds.IloveGroup.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Список серверов мультиплеера: <игровая папка>/servers.json
 * (аналог servers.dat у Minecraft).
 */
data class ServerEntry(var name: String, var address: String)

object ServerListStorage {

    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    fun load(gameDir: java.nio.file.Path): MutableList<ServerEntry> {
        val file = gameDir.resolve("servers.json")
        if (!java.nio.file.Files.exists(file)) return mutableListOf()
        return try {
            val text = java.nio.file.Files.readString(file, Charsets.UTF_8)
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                MutableList::class.java, ServerEntry::class.java
            ).type
            val parsed: MutableList<ServerEntry>? = gson.fromJson(text, type)
            parsed ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(gameDir: java.nio.file.Path, entries: List<ServerEntry>) {
        val file = gameDir.resolve("servers.json")
        java.nio.file.Files.createDirectories(gameDir)
        java.nio.file.Files.writeString(
            file, prettyGson.toJson(entries), Charsets.UTF_8
        )
    }
}
