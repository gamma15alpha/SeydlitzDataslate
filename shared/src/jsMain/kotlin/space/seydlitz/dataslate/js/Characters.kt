@file:OptIn(ExperimentalJsExport::class)

package space.seydlitz.dataslate.js

import space.seydlitz.dataslate.content.Author
import space.seydlitz.dataslate.content.ReadResult
import space.seydlitz.dataslate.content.asCopy
import space.seydlitz.dataslate.content.newCharacter
import space.seydlitz.dataslate.content.readCharacter
import space.seydlitz.dataslate.content.writeCharacter

// json — файл, приведённый к текущей версии (компактный); error — код ReadError.
@JsExport
class JsReadResult(val json: String?, val error: String?, val migratedFrom: Int?)

@JsExport
fun readCharacterFile(json: String): JsReadResult = when (val r = readCharacter(json)) {
    is ReadResult.Ok -> JsReadResult(writeCharacter(r.file), null, r.migratedFrom)
    is ReadResult.Failed -> JsReadResult(null, r.error.name, null)
}

@JsExport
fun newCharacterFile(name: String, authorId: String, authorName: String): String =
    writeCharacter(newCharacter(name, Author(authorId, authorName)))

// null — файл не читается (в хранилище такого быть не должно).
@JsExport
fun exportCharacterFile(json: String): String? = (readCharacter(json) as? ReadResult.Ok)?.let { writeCharacter(it.file, pretty = true) }

@JsExport
fun copyCharacterFile(json: String): String? = (readCharacter(json) as? ReadResult.Ok)?.let { writeCharacter(it.file.asCopy()) }
