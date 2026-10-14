package hgds.IloveGroup.mapcreate

import hgds.IloveGroup.GameInfo
import hgds.IloveGroup.util.AppDirs
import hgds.IloveGroup.util.Resources

/**
 * Точка входа полигона MapCreate: песочница для тестов генерации мира,
 * редактора карт и экспериментов. Рендер и UI переиспользуются у клиента,
 * игровой цикл клиента здесь не запускается.
 *
 * Запуск: `mvn -pl mapcreate exec:java`
 */
fun main(args: Array<String>) {
    AppDirs.init()
    Resources.exportDefaults()

    println("------------------------------------------------------------")
    println("  ${GameInfo.DISPLAY_NAME} · MapCreate (полигон тестов)")
    println("  Аргументы: ${args.joinToString(" ").ifEmpty { "(нет)" }}")
    println("  Папка игры: ${AppDirs.gameDir.toAbsolutePath()}")
    println("------------------------------------------------------------")
    println("Пока это заготовка: сюда переезжают экспериментальные режимы.")
}
