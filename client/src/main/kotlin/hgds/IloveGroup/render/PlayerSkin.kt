package hgds.IloveGroup.render

/**
 * Скин игрока: PNG-текстура с раскладкой из player.json
 * (ресурс: /assets/textures/game_1/entities/players/blem.png,
 * лежит рядом с geometry-файлом player.json, 64×64, box-UV раскладка).
 *
 * UV-координаты для модели теперь берутся напрямую из player.json,
 * константы ниже оставлены только для совместимости.
 *
 * Ресурс текстуры задаётся параметром: у игрока и жителя одна геометрия,
 * но разные PNG (житель — /entities/villager/blem.png).
 */
class PlayerSkin(resource: String = SKIN_RESOURCE) {

    val texture: Texture2D = Texture2D.load(resource)

    fun dispose() = texture.dispose()

    companion object {
        const val SKIN_RESOURCE = "/assets/textures/game_1/entities/players/blem.png"

        /** Текстура жителя (та же box-UV раскладка 64×64, что у игрока). */
        const val VILLAGER_RESOURCE = "/assets/textures/game_1/entities/villager/blem.png"

        const val TEX_WIDTH = 64
        const val TEX_HEIGHT = 64

        // Левый верхний угол каждой части на текстуре (в пикселях)
        const val HEAD_ORIGIN_X = 32; const val HEAD_ORIGIN_Y = 0
        const val HAT_ORIGIN_X = 0;   const val HAT_ORIGIN_Y = 16
        const val BODY_ORIGIN_X = 0;  const val BODY_ORIGIN_Y = 40
        const val ARM_L_ORIGIN_X = 8; const val ARM_L_ORIGIN_Y = 32
        const val ARM_R_ORIGIN_X = 64; const val ARM_R_ORIGIN_Y = 32
        const val LEG_L_ORIGIN_X = 40; const val LEG_L_ORIGIN_Y = 96
        const val LEG_R_ORIGIN_X = 72; const val LEG_R_ORIGIN_Y = 96

        /**
         * UV-прямоугольники 6 граней части по стандартной раскладке.
         * Возвращает 6 пар (u0,v0,u1,v1) в порядке: верх, низ, право, перед, лево, спина.
         */
        fun faceUVs(ox: Int, oy: Int, w: Int, h: Int, d: Int): FloatArray {
            val tw = TEX_WIDTH.toFloat(); val th = TEX_HEIGHT.toFloat()
            fun rect(x: Int, y: Int, rw: Int, rh: Int): FloatArray =
                floatArrayOf(x / tw, y / th, (x + rw) / tw, (y + rh) / th)
            val top = rect(ox + d, oy, w, d)
            val bottom = rect(ox + d + w, oy, w, d)
            val right = rect(ox, oy + d, d, h)
            val front = rect(ox + d, oy + d, w, h)
            val left = rect(ox + d + w, oy + d, d, h)
            val back = rect(ox + d + w + d, oy + d, w, h)
            return top + bottom + right + front + left + back
        }
    }
}