package space.seydlitz.dataslate.sync

import space.seydlitz.dataslate.content.CharacterFile
import space.seydlitz.dataslate.content.ReadResult
import space.seydlitz.dataslate.content.readCharacter
import space.seydlitz.dataslate.content.writeCharacter

// D25, D68: документ хранилища клиента; key — id анкеты.
data class StoredDocument(
    val key: String,
    val json: String, // "" у надгробия
    // Локальный счётчик правок: отправленная версия ещё та же, что в хранилище?
    val revision: Long,
    val updatedAt: String,
    val deleted: Boolean,
    // Ревизия сервера, на которой основана локальная версия; 0 — на сервере нет.
    val serverRevision: Long,
    // Есть изменения, не отправленные на сервер.
    val dirty: Boolean,
)

interface DocumentTransaction {
    suspend fun get(key: String): StoredDocument?
    suspend fun put(doc: StoredDocument)
    suspend fun delete(key: String)
}

// Хранилище платформы (Android — Room): анкеты и курсор обмена. Правила — в CharacterDocuments.
interface DocumentStore {
    suspend fun all(): List<StoredDocument>
    suspend fun <R> transaction(block: suspend DocumentTransaction.() -> R): R
    suspend fun cursor(): Long
    suspend fun setCursor(cursor: Long)
}

// Через ядро: лист старой версии мигрирует при чтении; нечитаемое (формат новее) не показывается.
fun parseStored(json: String): CharacterFile? = (readCharacter(json) as? ReadResult.Ok)?.file

// Анкеты в хранилище: правка, удаление, отметки обмена. Повторяет web/app/utils/characterStore.ts.
class CharacterDocuments(private val store: DocumentStore) {
    suspend fun list(): List<CharacterFile> = store.all().filter { !it.deleted }.mapNotNull { parseStored(it.json) }

    suspend fun document(id: String): StoredDocument? = store.transaction { get(id) }

    suspend fun get(id: String): CharacterFile? = document(id)?.takeIf { !it.deleted }?.let { parseStored(it.json) }

    // Не отправленные на сервер, в том числе удаления.
    suspend fun pending(): List<StoredDocument> = store.all().filter { it.dirty }

    // Правка на устройстве.
    suspend fun put(file: CharacterFile) = store.transaction {
        val existing = get(file.id)
        put(
            StoredDocument(
                key = file.id,
                json = writeCharacter(file),
                revision = (existing?.revision ?: 0) + 1,
                updatedAt = file.updatedAt,
                deleted = false,
                serverRevision = existing?.serverRevision ?: 0,
                dirty = true,
            ),
        )
    }

    // Удаление на устройстве: надгробие, пока не дойдёт до сервера; не было на сервере — сразу.
    suspend fun remove(id: String, now: String) = store.transaction {
        val existing = get(id) ?: return@transaction
        when {
            existing.deleted -> Unit
            existing.serverRevision == 0L -> delete(id)
            else -> put(existing.copy(json = "", revision = existing.revision + 1, updatedAt = now, deleted = true, dirty = true))
        }
    }

    // Сервер принял версию с локальной ревизией sent. Правки, сделанные пока шёл запрос, остаются неотправленными.
    suspend fun markSent(id: String, sent: Long, serverRevision: Long) = store.transaction {
        val doc = get(id) ?: return@transaction
        val dirty = doc.revision != sent
        if (doc.deleted && !dirty) delete(id) else put(doc.copy(serverRevision = serverRevision, dirty = dirty))
    }

    // Версия с сервера (json; null — удалена). Без force не трогает неотправленное — это конфликт, его решает пользователь.
    suspend fun applyRemote(id: String, serverRevision: Long, json: String?, updatedAt: String, force: Boolean = false): Boolean =
        store.transaction {
            val doc = get(id)
            when {
                doc?.dirty == true && !force -> false
                json == null -> (doc != null).also { if (it) delete(id) }
                else -> {
                    put(StoredDocument(id, json, (doc?.revision ?: 0) + 1, updatedAt, false, serverRevision, dirty = false))
                    true
                }
            }
        }

    // «Оставить мою»: правка теперь основана на версии сервера и уйдёт поверх неё.
    suspend fun rebase(id: String, serverRevision: Long) = store.transaction {
        get(id)?.let { put(it.copy(serverRevision = serverRevision, dirty = true)) }
    }

    // Без следа и без отправки.
    suspend fun forget(id: String) = store.transaction { delete(id) }

    suspend fun cursor(): Long = store.cursor()

    suspend fun setCursor(cursor: Long) = store.setCursor(cursor)
}
