package space.seydlitz.dataslate.characters

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.seydlitz.dataslate.content.Author
import space.seydlitz.dataslate.sync.CharacterApi
import space.seydlitz.dataslate.sync.Changes
import space.seydlitz.dataslate.sync.DocumentStore
import space.seydlitz.dataslate.sync.DocumentTransaction
import space.seydlitz.dataslate.sync.RemoteCharacter
import space.seydlitz.dataslate.sync.StoredDocument
import space.seydlitz.dataslate.sync.SyncException
import space.seydlitz.dataslate.sync.WriteResult

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterRepositoryTest {
    private class MemoryStore : DocumentStore {
        val docs = mutableMapOf<String, StoredDocument>()
        var closed = false
        override suspend fun all() = docs.values.toList()
        override suspend fun <R> transaction(block: suspend DocumentTransaction.() -> R): R = object : DocumentTransaction {
            override suspend fun get(key: String) = docs[key]
            override suspend fun put(doc: StoredDocument) { docs[doc.key] = doc }
            override suspend fun delete(key: String) { docs.remove(key) }
        }.block()
        override suspend fun cursor() = 0L
        override suspend fun setCursor(cursor: Long) = Unit
    }

    // Сервер принимает всё; conflictOn — id, на запись которого ответит 412 с версией «С сервера».
    private class Server : CharacterApi {
        val puts = mutableListOf<String>()
        var conflictOn: String? = null
        var offline = false
        override suspend fun changes(since: Long): Changes {
            if (offline) throw SyncException(true, "offline")
            return Changes(0, emptyList())
        }
        override suspend fun put(id: String, json: String, base: Long): WriteResult {
            puts += json
            if (id == conflictOn) {
                return WriteResult.Conflict(RemoteCharacter(id, 9, false, "t", json.replace(Regex(""""name":"[^"]*""""), "\"name\":\"С сервера\"")))
            }
            return WriteResult.Ok(base + 1)
        }
        override suspend fun delete(id: String, base: Long) = WriteResult.Ok(base + 1)
    }

    private val stores = mutableMapOf<String, MemoryStore>()
    private val server = Server()
    private val author = Author("u1", "Инквизитор")

    private fun TestScope.repo() = CharacterRepository(
        open = { user -> stores.getOrPut(user) { MemoryStore() }.let { OpenedStore(it) { it.closed = true } } },
        api = server,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun createdSheetIsListedAndSent() = runTest {
        val repo = repo()
        repo.bind("u1")
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), repo.list.value)

        val sheet = repo.create("Вэйн", author)
        advanceUntilIdle()
        assertEquals(listOf("Вэйн"), repo.list.value?.map { it.name })
        assertEquals(1, server.puts.size)
        assertEquals(false, stores.getValue("u1").docs.getValue(sheet.id).dirty)
        assertEquals(SyncState.Idle, repo.syncState.value)
    }

    @Test
    fun autosaveIsDebouncedAndFlushedOnUserChange() = runTest {
        val repo = repo()
        repo.bind("u1")
        val sheet = repo.create("Вэйн", author)
        advanceUntilIdle()

        repo.scheduleSave(sheet.copy(name = "В"))
        advanceTimeBy(100)
        repo.scheduleSave(sheet.copy(name = "Вэйн II"))
        advanceTimeBy(400)
        assertEquals(1L, stores.getValue("u1").docs.getValue(sheet.id).revision) // вторая правка отложила запись
        advanceUntilIdle()
        val doc = stores.getValue("u1").docs.getValue(sheet.id)
        assertEquals(2L, doc.revision) // одна запись на две правки
        assertTrue(doc.json.contains("Вэйн II"))

        // Черновик прежней учётки дописывается в её хранилище, а не в новое.
        repo.scheduleSave(sheet.copy(name = "Последняя правка"))
        repo.bind("u2")
        advanceUntilIdle()
        assertTrue(stores.getValue("u1").docs.getValue(sheet.id).json.contains("Последняя правка"))
        assertTrue(stores.getValue("u1").closed)
        assertNull(stores.getValue("u2").docs[sheet.id])
        assertEquals(emptyList<Any>(), repo.list.value)
    }

    @Test
    fun conflictIsShownAndResolved() = runTest {
        val repo = repo()
        repo.bind("u1")
        val sheet = repo.create("Вэйн", author)
        advanceUntilIdle()
        server.conflictOn = sheet.id
        repo.scheduleSave(sheet.copy(name = "Моя"))
        advanceUntilIdle()

        val conflict = repo.conflicts.value.single()
        assertEquals("Моя", conflict.local?.name)
        assertEquals("С сервера", conflict.server?.name)

        val reload = backgroundScope.launch { assertEquals(sheet.id, repo.reloads.first()) }
        assertNull(repo.resolve(conflict, Resolution.Server))
        advanceUntilIdle()
        assertTrue(reload.isCompleted)
        assertEquals("С сервера", repo.get(sheet.id)?.name)
        assertEquals(emptyList<Any>(), repo.conflicts.value)
    }

    @Test
    fun offlineIsReportedAndChangesWait() = runTest {
        val repo = repo()
        server.offline = true
        repo.bind("u1")
        val sheet = repo.create("Вэйн", author)
        advanceUntilIdle()
        assertEquals(SyncState.Offline, repo.syncState.value)
        assertTrue(stores.getValue("u1").docs.getValue(sheet.id).dirty)

        server.offline = false
        repo.requestSync()
        advanceUntilIdle()
        assertEquals(SyncState.Idle, repo.syncState.value)
    }
}
