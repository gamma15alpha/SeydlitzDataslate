package space.seydlitz.dataslate.sync

import kotlinx.coroutines.test.runTest
import space.seydlitz.dataslate.content.Author
import space.seydlitz.dataslate.content.CharacterFile
import space.seydlitz.dataslate.content.newCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryDocumentStore : DocumentStore {
    private val docs = mutableMapOf<String, StoredDocument>()
    private var cursor = 0L

    override suspend fun all() = docs.values.toList()

    override suspend fun <R> transaction(block: suspend DocumentTransaction.() -> R): R = object : DocumentTransaction {
        override suspend fun get(key: String) = docs[key]
        override suspend fun put(doc: StoredDocument) {
            docs[doc.key] = doc
        }
        override suspend fun delete(key: String) {
            docs.remove(key)
        }
    }.block()

    override suspend fun cursor() = cursor

    override suspend fun setCursor(cursor: Long) {
        this.cursor = cursor
    }
}

// Сервер в памяти с поведением server/internal/character: ревизии, If-Match, надгробия, курсор.
class FakeServer : CharacterApi {
    class Row(val revision: Long, val seq: Long, val json: String?)

    var seq = 0L
    val rows = mutableMapOf<String, Row>()
    val foreign = mutableSetOf<String>()
    var offline = false
    var dropNextResponse = false

    private fun remote(id: String) = rows.getValue(id).let { RemoteCharacter(id, it.revision, it.json == null, "2026-10-08T10:00:00Z", it.json) }

    override suspend fun changes(since: Long): Changes {
        if (offline) throw SyncException(true, "offline")
        val list = rows.entries.filter { it.value.seq > since && (since > 0 || it.value.json != null) }
            .sortedBy { it.value.seq }.map { remote(it.key) }
        return Changes(seq, list)
    }

    override suspend fun put(id: String, json: String, base: Long) = write(id, json, base)

    override suspend fun delete(id: String, base: Long) = write(id, null, base)

    private fun write(id: String, json: String?, base: Long): WriteResult {
        if (offline) throw SyncException(true, "offline")
        if (id in foreign) return WriteResult.IdTaken
        val row = rows[id]
        when {
            row == null -> {
                if (base != 0L || json == null) return WriteResult.NotFound
                rows[id] = Row(1, ++seq, json)
            }
            row.revision != base -> return WriteResult.Conflict(remote(id))
            row.json == null && json == null -> Unit
            else -> rows[id] = Row(row.revision + 1, ++seq, json)
        }
        if (dropNextResponse) {
            dropNextResponse = false
            throw SyncException(true, "connection reset")
        }
        return WriteResult.Ok(rows.getValue(id).revision)
    }
}

class CharacterSyncTest {
    private val server = FakeServer()

    private class Device(server: CharacterApi) {
        val docs = CharacterDocuments(MemoryDocumentStore())
        val sync = CharacterSync(docs, server)
    }

    private fun device() = Device(server)

    private fun create(name: String) = newCharacter(name, Author("01a10fdd-b783-7016-adf1-bdcf1a01711e", "Сейдлиц"))

    private fun CharacterFile.renamed(name: String) = copy(name = name)

    @Test
    fun editReachesAnotherDevice() = runTest {
        val (a, b) = device() to device()
        val sheet = create("Вэйн")
        a.docs.put(sheet)
        a.sync.sync()
        assertEquals(emptyList(), a.docs.pending())

        assertEquals(listOf(sheet.id), b.sync.sync().changed)
        b.docs.put(sheet.renamed("Вэйн II"))
        b.sync.sync()

        assertEquals(listOf(sheet.id), a.sync.sync().changed)
        assertEquals("Вэйн II", a.docs.get(sheet.id)?.name)
        // Своя запись, вернувшаяся в списке изменений, — не изменение.
        assertEquals(emptyList(), b.sync.sync().changed)
    }

    @Test
    fun conflictChoices() = runTest {
        val (a, b) = device() to device()
        val sheet = create("Вэйн")
        a.docs.put(sheet)
        a.sync.sync()
        b.sync.sync()
        a.docs.put(sheet.renamed("A"))
        b.docs.put(sheet.renamed("B"))
        a.sync.sync()

        val conflict = b.sync.sync().conflicts.single()
        assertEquals("B", conflict.local?.name)
        assertEquals("A", conflict.server?.name)
        assertEquals(2, conflict.serverRevision)
        assertEquals("B", b.docs.get(sheet.id)?.name) // неотправленное не затёрто

        b.sync.keepMine(conflict)
        assertEquals(emptyList(), b.sync.sync().conflicts)
        a.sync.sync()
        assertEquals("B", a.docs.get(sheet.id)?.name)

        a.docs.put(sheet.renamed("A2"))
        b.docs.put(sheet.renamed("B2"))
        a.sync.sync()
        b.sync.takeServer(b.sync.sync().conflicts.single())
        assertEquals("A2", b.docs.get(sheet.id)?.name)
        assertEquals(emptyList(), b.docs.pending())

        a.docs.put(sheet.renamed("A3"))
        b.docs.put(sheet.renamed("B3"))
        a.sync.sync()
        val copy = b.sync.keepBoth(b.sync.sync().conflicts.single())
        b.sync.sync()
        a.sync.sync()
        assertEquals(listOf("A3", "B3"), a.docs.list().map { it.name }.sorted())
        assertNotEquals(sheet.id, copy.id)
    }

    @Test
    fun deletesPropagateAndDeletedThereEditedHereIsAConflict() = runTest {
        val (a, b) = device() to device()
        val sheet = create("Вэйн")
        a.docs.put(sheet)
        a.sync.sync()
        b.sync.sync()

        a.docs.remove(sheet.id, "2026-10-08T11:00:00Z")
        b.docs.put(sheet.renamed("Правка"))
        a.sync.sync()
        assertNull(a.docs.document(sheet.id))

        val conflict = b.sync.sync().conflicts.single()
        assertEquals("Правка", conflict.local?.name)
        assertNull(conflict.server)
        assertFailsWith<IllegalArgumentException> { b.sync.keepBoth(conflict) }
        b.sync.keepMine(conflict)
        b.sync.sync()
        a.sync.sync()
        assertEquals("Правка", a.docs.get(sheet.id)?.name)

        b.docs.remove(sheet.id, "2026-10-08T12:00:00Z")
        b.sync.sync()
        assertEquals(listOf(sheet.id), a.sync.sync().changed)
        assertEquals(emptyList(), a.docs.list())
    }

    @Test
    fun lostResponseIsNotAConflict() = runTest {
        val a = device()
        a.docs.put(create("Вэйн"))
        server.dropNextResponse = true
        assertTrue(assertFailsWith<SyncException> { a.sync.sync() }.offline)
        assertEquals(1, a.docs.pending().size)
        assertEquals(emptyList(), a.sync.sync().conflicts)
        assertEquals(emptyList(), a.docs.pending())
    }

    @Test
    fun takenIdIsSavedAsCopy() = runTest {
        val a = device()
        val sheet = create("Вэйн")
        server.foreign += sheet.id
        a.docs.put(sheet)
        val copyId = a.sync.sync().renamed.getValue(sheet.id)
        assertNull(a.docs.document(sheet.id))
        assertEquals("Вэйн", a.docs.get(copyId)?.name)
        assertTrue(copyId in server.rows)
    }

    @Test
    fun missingOnServerIsCreatedAgain() = runTest {
        val a = device()
        val sheet = create("Вэйн")
        a.docs.put(sheet)
        a.sync.sync()
        server.rows.clear()
        a.docs.put(sheet.renamed("После сброса"))
        a.sync.sync()
        assertEquals(1, server.rows.getValue(sheet.id).revision)
        assertTrue(server.rows.getValue(sheet.id).json!!.contains("После сброса"))
    }

    @Test
    fun offlineKeepsChanges() = runTest {
        val a = device()
        a.docs.put(create("Вэйн"))
        server.offline = true
        assertTrue(assertFailsWith<SyncException> { a.sync.sync() }.offline)
        assertEquals(1, a.docs.pending().size)
    }

    @Test
    fun openDraftIsNotOverwritten() = runTest {
        val (a, b) = device() to device()
        val sheet = create("Вэйн")
        a.docs.put(sheet)
        a.sync.sync()
        b.sync.sync()
        a.docs.put(sheet.renamed("A"))
        a.sync.sync()
        assertEquals(emptyList(), b.sync.sync { it == sheet.id }.changed)
        assertEquals("Вэйн", b.docs.get(sheet.id)?.name)
    }

    @Test
    fun documentRules() = runTest {
        val docs = CharacterDocuments(MemoryDocumentStore())
        val sheet = create("Вэйн")
        docs.put(sheet)
        docs.remove(sheet.id, "2026-10-08T11:00:00Z")
        assertNull(docs.document(sheet.id)) // не было на сервере — без надгробия

        docs.put(sheet)
        docs.put(sheet.renamed("Правка во время отправки"))
        docs.markSent(sheet.id, 1, 5)
        assertEquals(true to 5L, docs.document(sheet.id)!!.let { it.dirty to it.serverRevision })
        docs.markSent(sheet.id, 2, 6)
        assertEquals(emptyList(), docs.pending())

        assertTrue(docs.applyRemote(sheet.id, 7, null, "2026-10-08T11:00:00Z"))
        assertNull(docs.document(sheet.id))
        docs.put(sheet)
        assertFalse(docs.applyRemote(sheet.id, 8, null, "2026-10-08T11:00:00Z"))
    }

    @Test
    fun jsonComparisonIgnoresKeyOrder() {
        assertTrue(sameJson("""{"a":1,"b":{"c":[1,{"d":2,"e":3}]}}""", """{"b":{"c":[1,{"e":3,"d":2}]},"a":1}"""))
        assertFalse(sameJson("""{"a":1}""", """{"a":2}"""))
        assertTrue(sameJson(null, null))
        assertFalse(sameJson("{}", null))
    }
}
