package space.seydlitz.dataslate.content

import kotlinx.serialization.json.Json

val FileJson = Json { ignoreUnknownKeys = true }

fun parseCharacter(json: String): CharacterFile = FileJson.decodeFromString(CharacterFile.serializer(), json)

fun parseContentPack(json: String): ContentPack = FileJson.decodeFromString(ContentPack.serializer(), json)
