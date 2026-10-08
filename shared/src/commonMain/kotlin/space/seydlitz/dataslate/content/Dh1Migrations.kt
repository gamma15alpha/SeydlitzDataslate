package space.seydlitz.dataslate.content

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// По JSON до разбора: модель знает только текущую версию. v1 → v2 → v3 добавили поля — читаются как пустые.
fun migrateDh1Sheet(sheet: JsonObject, from: Int): JsonObject {
    var s = sheet
    if (from < 4) s = v3ToV4(s)
    return s
}

// v4: homeworld, career, rank, skills[].specialization — choice вместо строки.
private fun v3ToV4(sheet: JsonObject): JsonObject {
    val result = sheet.toMutableMap()
    for (key in listOf("homeworld", "career", "rank")) {
        sheet[key]?.let { value -> toChoice(value).let { if (it == null) result.remove(key) else result[key] = it } }
    }
    (sheet["skills"] as? JsonArray)?.let { skills ->
        result["skills"] = JsonArray(
            skills.map { skill ->
                val obj = skill as? JsonObject ?: return@map skill
                val spec = obj["specialization"] ?: return@map obj
                val choice = toChoice(spec)
                JsonObject(if (choice == null) obj - "specialization" else obj + ("specialization" to choice))
            },
        )
    }
    return JsonObject(result)
}

// Пустая строка — значения нет (custom не бывает пустым).
private fun toChoice(value: JsonElement): JsonElement? = when {
    value is JsonPrimitive && value.isString -> value.content.takeIf { it.isNotBlank() }?.let { JsonObject(mapOf("custom" to JsonPrimitive(it))) }
    else -> value
}
