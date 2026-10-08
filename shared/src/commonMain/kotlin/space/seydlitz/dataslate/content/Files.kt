package space.seydlitz.dataslate.content

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val CHARACTER_FORMAT = "seydlitz.character"
const val CHARACTER_FORMAT_VERSION = 1
const val DH1_SHEET_VERSION = 4

val FileJson = Json { ignoreUnknownKeys = true }
private val PrettyJson = Json(FileJson) {
    prettyPrint = true
    prettyPrintIndent = "  "
}

fun parseCharacter(json: String): CharacterFile = FileJson.decodeFromString(CharacterFile.serializer(), json)

fun parseContentPack(json: String): ContentPack = FileJson.decodeFromString(ContentPack.serializer(), json)

// Коды; текст — на стороне клиента.
enum class ReadError { NOT_JSON, NOT_CHARACTER, FORMAT_TOO_NEW, UNKNOWN_SYSTEM, SHEET_TOO_NEW, INVALID }

sealed interface ReadResult {
    // migratedFrom — версия листа в файле, если была старше текущей.
    data class Ok(val file: CharacterFile, val migratedFrom: Int?) : ReadResult
    data class Failed(val error: ReadError) : ReadResult
}

// Файл с диска или из хранилища: проверка конверта, миграция листа, разбор.
fun readCharacter(json: String): ReadResult {
    fun fail(error: ReadError) = ReadResult.Failed(error)
    val root = try {
        FileJson.parseToJsonElement(json)
    } catch (_: SerializationException) {
        return fail(ReadError.NOT_JSON)
    } as? JsonObject ?: return fail(ReadError.NOT_CHARACTER)

    fun primitive(key: String) = root[key] as? JsonPrimitive
    if (primitive("format")?.contentOrNull != CHARACTER_FORMAT) return fail(ReadError.NOT_CHARACTER)
    val formatVersion = primitive("formatVersion")?.intOrNull?.takeIf { it >= 1 } ?: return fail(ReadError.INVALID)
    if (formatVersion > CHARACTER_FORMAT_VERSION) return fail(ReadError.FORMAT_TOO_NEW)
    if (primitive("system")?.contentOrNull != "dh1") return fail(ReadError.UNKNOWN_SYSTEM)
    val sheetVersion = primitive("sheetVersion")?.intOrNull?.takeIf { it >= 1 } ?: return fail(ReadError.INVALID)
    if (sheetVersion > DH1_SHEET_VERSION) return fail(ReadError.SHEET_TOO_NEW)
    val sheet = root["sheet"] as? JsonObject ?: return fail(ReadError.INVALID)

    val migrated = JsonObject(
        root + mapOf("sheetVersion" to JsonPrimitive(DH1_SHEET_VERSION), "sheet" to migrateDh1Sheet(sheet, sheetVersion)),
    )
    return try {
        ReadResult.Ok(FileJson.decodeFromJsonElement(CharacterFile.serializer(), migrated), sheetVersion.takeIf { it < DH1_SHEET_VERSION })
    } catch (_: IllegalArgumentException) {
        // SerializationException — его подкласс; сюда же — неверные типы полей.
        fail(ReadError.INVALID)
    }
}

// pretty — для файла экспорта; в хранилище — компактно.
fun writeCharacter(file: CharacterFile, pretty: Boolean = false): String =
    (if (pretty) PrettyJson else FileJson).encodeToString(CharacterFile.serializer(), file)

fun nowIso(): String = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()).toString()

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.generateV7().toString()

fun newCharacter(name: String, author: Author? = null, now: String = nowIso(), id: String = newId()): CharacterFile = CharacterFile(
    format = CHARACTER_FORMAT,
    formatVersion = CHARACTER_FORMAT_VERSION,
    id = id,
    system = "dh1",
    sheetVersion = DH1_SHEET_VERSION,
    name = name,
    createdAt = now,
    updatedAt = now,
    author = author,
    sheet = Dh1Sheet(),
)

// Импорт копией: новый id и даты, содержимое и автор те же.
fun CharacterFile.asCopy(now: String = nowIso(), id: String = newId()): CharacterFile =
    copy(id = id, createdAt = now, updatedAt = now)
