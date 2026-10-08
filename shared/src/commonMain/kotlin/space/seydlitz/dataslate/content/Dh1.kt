package space.seydlitz.dataslate.content

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

// Модели — по schemas/content.schema.json, dh1-content.schema.json, dh1-sheet.schema.json.

interface CatalogEntry {
    val id: String
    val name: LocalizedText
    val abbreviation: LocalizedText?
    val description: LocalizedText?
}

@Serializable
data class Entry(
    override val id: String,
    override val name: LocalizedText,
    override val abbreviation: LocalizedText? = null,
    override val description: LocalizedText? = null,
) : CatalogEntry

@Serializable
data class Career(
    override val id: String,
    override val name: LocalizedText,
    override val abbreviation: LocalizedText? = null,
    override val description: LocalizedText? = null,
    val ranks: List<Entry> = emptyList(),
) : CatalogEntry

@Serializable
data class Skill(
    override val id: String,
    override val name: LocalizedText,
    override val abbreviation: LocalizedText? = null,
    override val description: LocalizedText? = null,
    val characteristic: String,
    val kind: String,
    val specializations: Boolean = false,
    val specializationOptions: List<Entry> = emptyList(),
) : CatalogEntry

@Serializable
data class Dh1Content(
    val characteristics: List<Entry> = emptyList(),
    val rules: List<Entry> = emptyList(),
    val items: List<Entry> = emptyList(),
    val homeworlds: List<Entry> = emptyList(),
    val careers: List<Career> = emptyList(),
    val skills: List<Skill> = emptyList(),
) {
    // Ссылка dataslate:<раздел>/<id>; ранги ищутся во всех карьерах, специализации — во всех навыках.
    fun find(ref: String): CatalogEntry? {
        val section = ref.substringBefore('/')
        val id = ref.substringAfter('/', "")
        val list: List<CatalogEntry> = when (section) {
            "characteristics" -> characteristics
            "rules" -> rules
            "items" -> items
            "homeworlds" -> homeworlds
            "careers" -> careers
            "skills" -> skills
            "ranks" -> careers.flatMap { it.ranks }
            "specializations" -> skills.flatMap { it.specializationOptions }
            else -> emptyList()
        }
        return list.firstOrNull { it.id == id }
    }
}

@Serializable
data class ContentPack(val system: String, val name: LocalizedText? = null, val content: Dh1Content)

// Значение листа: из каталога ({"id"}) или своё ({"custom"}).
@Serializable(with = ChoiceSerializer::class)
sealed interface Choice {
    data class Id(val id: String) : Choice
    data class Custom(val text: String) : Choice
}

object ChoiceSerializer : KSerializer<Choice> {
    override val descriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): Choice {
        val json = (decoder as JsonDecoder).decodeJsonElement() as? JsonObject
            ?: throw SerializationException("choice: expected an object")
        json["id"]?.let { return Choice.Id(it.jsonPrimitive.content) }
        json["custom"]?.let { return Choice.Custom(it.jsonPrimitive.content) }
        throw SerializationException("choice: expected id or custom")
    }

    override fun serialize(encoder: Encoder, value: Choice) = (encoder as JsonEncoder).encodeJsonElement(
        when (value) {
            is Choice.Id -> JsonObject(mapOf("id" to JsonPrimitive(value.id)))
            is Choice.Custom -> JsonObject(mapOf("custom" to JsonPrimitive(value.text)))
        },
    )
}

@Serializable
data class Characteristic(val base: Int? = null, val advances: Int = 0, val unnatural: Int = 1) {
    val value: Int? get() = base?.let { it + 5 * advances }
}

@Serializable
data class SheetSkill(val skill: String, val specialization: Choice? = null, val training: Int = 1)

@Serializable
data class Dh1Sheet(
    val player: String? = null,
    val homeworld: Choice? = null,
    val career: Choice? = null,
    val rank: Choice? = null,
    val characteristics: Map<String, Characteristic> = emptyMap(),
    val skills: List<SheetSkill> = emptyList(),
)

@Serializable
data class CharacterFile(val id: String, val name: String, val system: String, val sheetVersion: Int, val sheet: Dh1Sheet)
