package hgds.IloveGroup

/**
 * Общие игровые константы, нужные и клиенту, и серверу.
 * Раньше жили в Main.kt клиента — вынесены в core, чтобы server/mapcreate
 * не зависели от клиента.
 */

/** Дальность взаимодействия с блоками. */
const val REACH_DISTANCE = 6f

/** Дальность прорисовки по умолчанию (в чанках). */
const val DEFAULT_RENDER_DISTANCE = 32

/** Максимальная дальность прорисовки (в чанках). */
const val MAX_RENDER_DISTANCE = 32

const val COLUMNS_GENERATE_PER_FRAME = 12
const val SECTIONS_MESH_PER_FRAME = 10

/** Бюджет времени на генерацию/мешинг чанков в кадре (6 мс) — защита от фризов. */
const val CHUNK_WORK_BUDGET_NS = 6_000_000L

/**
 * Единая точка правды для названия и версии игры.
 * Перед билдом меняйте значения только здесь — они автоматически
 * подставляются в заголовок окна, F3-оверлей, главное меню,
 * титры и серверные сообщения.
 */
object GameInfo {
    /** Внутреннее имя (логи, MOTD, папка данных НЕ зависит от этого поля). */
    const val NAME = "hgdsEngine"

    /** Версия сборки. */
    const val VERSION = "0.2.4.15-INVENTORY-FIX"

    /** Заголовок окна игры. */
    const val WINDOW_TITLE = NAME

    /** Имя с версией для интерфейса: меню, титры, F3, команда version. */
    const val DISPLAY_NAME = "hgdsEngine $VERSION"
}