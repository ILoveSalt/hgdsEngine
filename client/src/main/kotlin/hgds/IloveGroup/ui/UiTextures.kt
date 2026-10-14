package hgds.IloveGroup.ui

import hgds.IloveGroup.render.Texture2D

/**
 * Текстуры интерфейса (стиль Minecraft), загружаются один раз при старте игры.
 * Пути соответствуют файлам в папке textures — потом их можно заменить своими.
 */
class UiTextures {

    val menuBackground = Texture2D.load("/gui/menu_background.png")
    val inworldMenuBackground = Texture2D.load("/gui/inworld_menu_background.png")
    val button = Texture2D.load("/gui/sprites/widget/button.png")
    val buttonHighlighted = Texture2D.load("/gui/sprites/widget/button_highlighted.png")
    val buttonDisabled = Texture2D.load("/gui/sprites/widget/button_disabled.png")
    val textField = Texture2D.load("/gui/sprites/widget/text_field.png")
    val textFieldHighlighted = Texture2D.load("/gui/sprites/widget/text_field_highlighted.png")
    /** Логотип главного меню (пользовательская картинка title.png, 1024×256). */
    val logo = Texture2D.load("/title.png")
    val panorama = Texture2D.load("/gui/title/background/panorama_0.png", linear = true, repeatS = true)
    val panoramaOverlay = Texture2D.load("/gui/title/background/panorama_overlay.png", linear = true)

    fun dispose() {
        menuBackground.dispose()
        inworldMenuBackground.dispose()
        button.dispose()
        buttonHighlighted.dispose()
        buttonDisabled.dispose()
        textField.dispose()
        textFieldHighlighted.dispose()
        logo.dispose()
        panorama.dispose()
        panoramaOverlay.dispose()
    }
}