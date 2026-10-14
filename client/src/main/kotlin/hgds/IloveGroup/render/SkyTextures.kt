package hgds.IloveGroup.render

/**
 * Текстуры неба для цикла дня и ночи: солнце и луна.
 * PNG лежат в resources/assets/textures/game_1/s&m/ — их можно заменить своими.
 */
class SkyTextures {
    val sun = Texture2D.load("/assets/textures/game_1/s&m/sun.png", linear = true)
    val moon = Texture2D.load("/assets/textures/game_1/s&m/moon.png", linear = true)

    fun dispose() {
        sun.dispose()
        moon.dispose()
    }
}