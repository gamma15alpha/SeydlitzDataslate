package space.seydlitz.dataslate.sync

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import space.seydlitz.dataslate.content.CharacterFile
import space.seydlitz.dataslate.content.FileJson
import space.seydlitz.dataslate.content.asCopy
import space.seydlitz.dataslate.content.nowIso
import space.seydlitz.dataslate.content.writeCharacter

// StoredCharacter из schemas/openapi.yaml; json — файл анкеты как пришёл, null у надгробия.
data class RemoteCharacter(val id: String, val revision: Long, val deleted: Boolean, val updatedAt: String, val json: String?)

data class Changes(val cursor: Long, val changes: List<RemoteCharacter>)

sealed interface WriteResult {
    data class Ok(val revision: Long) : WriteResult

    // 412: на сервере другая ревизия.
    data class Conflict(val server: RemoteCharacter) : WriteResult

    // 404: If-Match, а анкеты на сервере нет.
    data object NotFound : WriteResult

    // 409: id у другого пользователя.
    data object IdTaken : WriteResult
}

// offline — нет связи; иначе сервер ответил ошибкой.
class SyncException(val offline: Boolean, message: String) : Exception(message)

// API анкет (D68); платформа переводит HTTP в эти результаты, прочие ответы — SyncException.
interface CharacterApi {
    suspend fun changes(since: Long): Changes

    // base 0 — создать (If-None-Match: *), иначе If-Match.
    suspend fun put(id: String, json: String, base: Long): WriteResult

    suspend fun delete(id: String, base: Long): WriteResult
}

// null — удалена (на устройстве или на сервере).
data class SyncConflict(val id: String, val local: CharacterFile?, val server: CharacterFile?, val serverRevision: Long)

data class SyncResult(
    // Обновлены с сервера: открытую анкету — перечитать.
    val changed: List<String>,
    val conflicts: List<SyncConflict>,
    // id занят другим пользователем — анкета сохранена копией под новым id.
    val renamed: Map<String, String>,
)

// Порядок ключей не важен: сервер хранит jsonb и отдаёт их в своём.
fun sameJson(a: String?, b: String?): Boolean {
    if (a == null || b == null) return a == b
    fun parse(s: String): JsonElement? = try {
        FileJson.parseToJsonElement(s)
    } catch (_: SerializationException) {
        null
    }
    return parse(a)?.let { it == parse(b) } ?: false
}

/**
 * Обмен анкетами (D68), как web/app/utils/characterSync.ts: сначала изменения с сервера, потом свои.
 * Неотправленное с сервера не перезаписывается — расхождение всплывёт при отправке (412) как конфликт.
 * busy(id) — правка ещё не дошла до хранилища (черновик автосохранения): её тоже не трогаем.
 */
class CharacterSync(private val docs: CharacterDocuments, private val api: CharacterApi) {
    suspend fun sync(busy: (String) -> Boolean = { false }): SyncResult {
        val changed = mutableListOf<String>()
        val conflicts = mutableListOf<SyncConflict>()
        val renamed = mutableMapOf<String, String>()

        val pulled = api.changes(docs.cursor())
        for (c in pulled.changes) {
            val doc = docs.document(c.id)
            if (doc != null && doc.serverRevision == c.revision) continue // своя же запись
            if (busy(c.id)) continue
            if (docs.applyRemote(c.id, c.revision, c.json.takeUnless { c.deleted }, c.updatedAt)) changed += c.id
        }
        docs.setCursor(pulled.cursor)

        for (doc in docs.pending()) push(doc, conflicts, renamed)
        return SyncResult(changed, conflicts, renamed)
    }

    private suspend fun push(doc: StoredDocument, conflicts: MutableList<SyncConflict>, renamed: MutableMap<String, String>) {
        val id = doc.key
        val result = if (doc.deleted) api.delete(id, doc.serverRevision) else api.put(id, doc.json, doc.serverRevision)
        when (result) {
            is WriteResult.Ok -> docs.markSent(id, doc.revision, result.revision)
            is WriteResult.Conflict -> {
                val server = result.server
                val serverJson = server.json.takeUnless { server.deleted }
                val localJson = doc.json.takeUnless { doc.deleted }
                // Совпадает — значит, это наша же запись, ответ на которую потерялся.
                if (sameJson(localJson, serverJson)) return docs.markSent(id, doc.revision, server.revision)
                val serverFile = serverJson?.let { parseStored(it) ?: return } // формат новее понятного — решать нечем
                conflicts += SyncConflict(id, localJson?.let(::parseStored), serverFile, server.revision)
            }
            // На сервере нет (база пересоздана): удалять нечего, остальное — создать заново.
            WriteResult.NotFound -> if (doc.deleted) {
                docs.forget(id)
            } else {
                docs.rebase(id, 0)
                push(doc.copy(serverRevision = 0), conflicts, renamed)
            }
            // id занят другим пользователем (один файл импортировали двое): своя — под новым id.
            WriteResult.IdTaken -> {
                val file = if (doc.deleted) null else parseStored(doc.json)
                docs.forget(id)
                if (file == null) return
                val copy = file.asCopy()
                docs.put(copy)
                renamed[id] = copy.id
                docs.document(copy.id)?.let { push(it, conflicts, renamed) }
            }
        }
    }

    // Решения конфликта; отправит следующий обмен.
    suspend fun keepMine(c: SyncConflict) = docs.rebase(c.id, c.serverRevision)

    suspend fun takeServer(c: SyncConflict) {
        docs.applyRemote(c.id, c.serverRevision, c.server?.let(::writeCharacter), c.server?.updatedAt ?: nowIso(), force = true)
    }

    // Только когда обе версии есть: своя — копией под новым id, у этого id — версия сервера.
    suspend fun keepBoth(c: SyncConflict): CharacterFile {
        val local = requireNotNull(c.local) { "keepBoth needs both versions" }
        requireNotNull(c.server) { "keepBoth needs both versions" }
        val copy = local.asCopy()
        docs.put(copy)
        takeServer(c)
        return copy
    }
}
