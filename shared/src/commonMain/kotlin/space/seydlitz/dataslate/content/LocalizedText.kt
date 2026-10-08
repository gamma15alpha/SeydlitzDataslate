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

private const val ORIGINAL = "ru"

// localizedText / localizedMarkdown из schemas/content.schema.json: строка (русский оригинал) или переводы по тегу BCP 47.
@Serializable(with = LocalizedTextSerializer::class)
data class LocalizedText(val translations: Map<String, String>) {
    constructor(original: String) : this(mapOf(ORIGINAL to original))

    // Правило из схемы: язык → его основной язык (en-GB → en) → ru → en → первый по порядку.
    fun pick(lang: String): String =
        translations[lang] ?: translations[lang.substringBefore('-')] ?: translations[ORIGINAL]
            ?: translations["en"] ?: translations.values.firstOrNull() ?: ""

    // Все варианты — для сверки с оригиналом без смены языка; оригинал первым.
    fun all(): List<Pair<String, String>> = translations.toList().sortedByDescending { it.first == ORIGINAL }
}

object LocalizedTextSerializer : KSerializer<LocalizedText> {
    override val descriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): LocalizedText = when (val json = (decoder as JsonDecoder).decodeJsonElement()) {
        is JsonPrimitive -> LocalizedText(json.content)
        is JsonObject -> LocalizedText(json.mapValues { it.value.jsonPrimitive.content })
        else -> throw SerializationException("localizedText: expected a string or an object")
    }

    override fun serialize(encoder: Encoder, value: LocalizedText) =
        (encoder as JsonEncoder).encodeJsonElement(JsonObject(value.translations.mapValues { JsonPrimitive(it.value) }))
}

data class TermParts(val abbreviation: String?, val name: String, val english: String?)

// Ag · Ловкость · Agility: английский дубль — только если язык контента не английский и название другое.
fun CatalogEntry.termParts(lang: String): TermParts {
    val name = this.name.pick(lang)
    val english = if (lang.substringBefore('-') == "en") null else this.name.pick("en")
    return TermParts(abbreviation?.pick(lang), name, english?.takeIf { it != name })
}
