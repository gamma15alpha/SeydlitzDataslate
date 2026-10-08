package space.seydlitz.dataslate.characters

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.seydlitz.dataslate.content.Author
import space.seydlitz.dataslate.content.CharacterFile
import space.seydlitz.dataslate.content.ReadError
import space.seydlitz.dataslate.content.ReadResult
import space.seydlitz.dataslate.content.asCopy
import space.seydlitz.dataslate.content.newCharacter
import space.seydlitz.dataslate.content.nowIso
import space.seydlitz.dataslate.content.readCharacter
import space.seydlitz.dataslate.content.writeCharacter
import space.seydlitz.dataslate.sync.CharacterApi
import space.seydlitz.dataslate.sync.CharacterDocuments
import space.seydlitz.dataslate.sync.CharacterSync
import space.seydlitz.dataslate.sync.DocumentStore
import space.seydlitz.dataslate.sync.SyncConflict
import space.seydlitz.dataslate.sync.SyncException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class SyncState { Idle, Syncing, Offline, Error }

enum class Resolution { Mine, Server, Both }

sealed interface ImportResult {
    data class Error(val error: ReadError) : ImportResult
    data class Conflict(val existing: CharacterFile, val incoming: CharacterFile) : ImportResult
    data class Imported(val file: CharacterFile) : ImportResult
}

// Хранилище учётки; close — когда учётка сменилась и черновики дописаны.
class OpenedStore(val store: DocumentStore, val close: () -> Unit)

/**
 * Анкеты текущей учётки (D66, D68): хранилище, автосохранение, обмен с сервером — как useCharacters на вебе.
 * Одна на приложение; всё состояние меняется на одном потоке (serial), поэтому без блокировок.
 */
class CharacterRepository(
    private val open: (userId: String) -> OpenedStore,
    private val api: CharacterApi,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val saveDelay: Duration = 500.milliseconds,
) {
    private val serial = dispatcher.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)

    private class Bound(val userId: String, val opened: OpenedStore) {
        val docs = CharacterDocuments(opened.store)
    }

    private var bound: Bound? = null

    // Черновик помнит своё хранилище: после смены учётки он допишется в прежнее.
    private class Draft(val bound: Bound, val file: CharacterFile, val job: Job)

    private val drafts = mutableMapOf<String, Draft>()
    private var syncing = false
    private var syncAgain = false

    private val _list = MutableStateFlow<List<CharacterFile>?>(null)
    val list = _list.asStateFlow()
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed = _saveFailed.asStateFlow()
    private val _syncState = MutableStateFlow(SyncState.Idle)
    val syncState = _syncState.asStateFlow()
    private val _conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val conflicts = _conflicts.asStateFlow()

    // Старый id → новый: анкета сохранена копией (id занят на сервере).
    private val _renamed = MutableStateFlow<Map<String, String>>(emptyMap())
    val renamed = _renamed.asStateFlow()

    // id анкеты, обновлённой с сервера или после конфликта: открытую — перечитать.
    private val _reloads = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val reloads = _reloads.asSharedFlow()

    private suspend fun <T> serial(block: suspend () -> T): T = withContext(serial) { block() }

    private fun current(): Bound = checkNotNull(bound) { "no signed-in user" }

    // Вызывать при каждой смене учётки (null — вышли).
    fun bind(userId: String?) {
        scope.launch {
            if (bound?.userId == userId) return@launch
            val old = bound
            bound = userId?.let { Bound(it, open(it)) }
            _list.value = null
            _conflicts.value = emptyList()
            _syncState.value = SyncState.Idle
            if (old != null) {
                flush()
                old.opened.close()
            }
            if (bound != null) {
                refreshList()
                requestSync()
            }
        }
    }

    private suspend fun refreshList() {
        _list.value = bound?.docs?.list()?.sortedByDescending { it.updatedAt }
    }

    suspend fun get(id: String): CharacterFile? = serial {
        flush()
        current().docs.get(id)
    }

    fun scheduleSave(file: CharacterFile) {
        scope.launch {
            val b = bound ?: return@launch
            drafts.remove(file.id)?.job?.cancel()
            val job = scope.launch {
                delay(saveDelay)
                write(file.id)
            }
            drafts[file.id] = Draft(b, file, job)
        }
    }

    private suspend fun write(id: String) {
        val draft = drafts.remove(id) ?: return
        draft.job.cancel()
        try {
            draft.bound.docs.put(draft.file.copy(updatedAt = nowIso()))
            _saveFailed.value = false
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
            _saveFailed.value = true
            return
        }
        if (draft.bound === bound) {
            refreshList()
            requestSync()
        }
    }

    // Черновики — в хранилище сразу: уход в фон, смена учётки, перед обменом.
    suspend fun flush() = serial {
        drafts.keys.toList().forEach { write(it) }
    }

    private suspend fun changed() {
        refreshList()
        requestSync()
    }

    suspend fun create(name: String, author: Author): CharacterFile = serial {
        newCharacter(name, author).also {
            current().docs.put(it)
            changed()
        }
    }

    suspend fun remove(id: String) = serial {
        drafts.remove(id)?.job?.cancel()
        current().docs.remove(id, nowIso())
        changed()
    }

    suspend fun import(text: String): ImportResult = serial {
        when (val r = readCharacter(text)) {
            is ReadResult.Failed -> ImportResult.Error(r.error)
            is ReadResult.Ok -> get(r.file.id)?.let { ImportResult.Conflict(it, r.file) } ?: run {
                current().docs.put(r.file)
                changed()
                ImportResult.Imported(r.file)
            }
        }
    }

    suspend fun replace(file: CharacterFile) = serial {
        current().docs.put(file)
        changed()
    }

    suspend fun importAsCopy(file: CharacterFile): CharacterFile = serial {
        file.asCopy().also {
            current().docs.put(it)
            changed()
        }
    }

    // Файл экспорта — форматированный.
    suspend fun export(id: String): CharacterFile? = get(id)

    fun exportText(file: CharacterFile) = writeCharacter(file, pretty = true)

    // Возвращает копию при Resolution.Both.
    suspend fun resolve(conflict: SyncConflict, how: Resolution): CharacterFile? = serial {
        flush()
        val sync = CharacterSync(current().docs, api)
        val copy = when (how) {
            Resolution.Mine -> null.also { sync.keepMine(conflict) }
            Resolution.Server -> null.also { sync.takeServer(conflict) }
            Resolution.Both -> sync.keepBoth(conflict)
        }
        _conflicts.value = _conflicts.value.filter { it.id != conflict.id }
        if (how != Resolution.Mine) _reloads.emit(conflict.id)
        changed()
        copy
    }

    // Один обмен за раз; запрос во время обмена — ещё один круг после него.
    fun requestSync() {
        scope.launch {
            if (bound == null) return@launch
            if (syncing) {
                syncAgain = true
                return@launch
            }
            syncing = true
            try {
                do {
                    syncAgain = false
                    runSync(current())
                } while (syncAgain && bound != null)
            } finally {
                syncing = false
            }
        }
    }

    private suspend fun runSync(b: Bound) {
        flush()
        _syncState.value = SyncState.Syncing
        val state = try {
            val r = CharacterSync(b.docs, api).sync { it in drafts }
            if (b !== bound) return
            _conflicts.value = r.conflicts
            _renamed.value += r.renamed
            r.changed.forEach { _reloads.emit(it) }
            if (r.changed.isNotEmpty() || r.renamed.isNotEmpty()) refreshList()
            SyncState.Idle
        } catch (e: SyncException) {
            if (e.offline) SyncState.Offline else SyncState.Error
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            SyncState.Error
        }
        if (b === bound) _syncState.value = state
    }

    private companion object {
        const val TAG = "Characters"
    }
}
