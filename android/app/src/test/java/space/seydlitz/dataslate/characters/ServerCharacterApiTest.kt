package space.seydlitz.dataslate.characters

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.seydlitz.dataslate.api.ApiClient
import space.seydlitz.dataslate.api.User
import space.seydlitz.dataslate.auth.AuthRepository
import space.seydlitz.dataslate.auth.AuthState
import space.seydlitz.dataslate.auth.SavedSession
import space.seydlitz.dataslate.auth.SessionStore
import space.seydlitz.dataslate.sync.RemoteCharacter
import space.seydlitz.dataslate.sync.SyncException
import space.seydlitz.dataslate.sync.WriteResult

class ServerCharacterApiTest {
    private val user = User("u1", "player", "Инквизитор", false, "2026-10-05T10:00:00Z")
    private val requests = mutableListOf<HttpRequestData>()

    private class MemoryStore(var saved: SavedSession?) : SessionStore {
        override suspend fun load() = saved
        override suspend fun save(session: SavedSession) { saved = session }
        override suspend fun clear() { saved = null }
    }

    private suspend fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<ServerCharacterApi, AuthRepository> {
        val auth = AuthRepository(MemoryStore(SavedSession("T", user))) { token, onNewToken ->
            ApiClient("http://test", MockEngine { requests += it; handler(it) }, "test", token, onNewToken)
        }
        return ServerCharacterApi(auth) to auth
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private val file = """{"format":"seydlitz.character","id":"c1","name":"Вэйн","sheet":{}}"""

    @Test
    fun changesKeepTheFileAsJson() = runTest {
        val (api) = api {
            json("""{"cursor":7,"changes":[{"id":"c1","revision":2,"deleted":false,"updatedAt":"t","character":$file},""" +
                """{"id":"c2","revision":3,"deleted":true,"updatedAt":"t"}],"future":1}""")
        }
        val changes = api.changes(5)
        assertEquals(7, changes.cursor)
        assertEquals("since=5", requests.single().url.encodedQuery)
        assertEquals("Вэйн", changes.changes[0].json!!.substringAfter("\"name\":\"").substringBefore('"'))
        assertEquals(RemoteCharacter("c2", 3, true, "t", null), changes.changes[1])
    }

    @Test
    fun writesSendPreconditionsAndRawBody() = runTest {
        val (api) = api { json("""{"id":"c1","revision":4,"deleted":false,"updatedAt":"t"}""", if (it.method == HttpMethod.Put) HttpStatusCode.Created else HttpStatusCode.OK) }
        assertEquals(WriteResult.Ok(4), api.put("c1", file, 0))
        assertEquals("*", requests.last().headers[HttpHeaders.IfNoneMatch])
        assertEquals(file, (requests.last().body as TextContent).text)

        api.put("c1", file, 3)
        assertEquals("\"3\"", requests.last().headers[HttpHeaders.IfMatch])
        api.delete("c1", 4)
        assertEquals(HttpMethod.Delete, requests.last().method)
        assertEquals("\"4\"", requests.last().headers[HttpHeaders.IfMatch])
    }

    @Test
    fun errorStatusesBecomeResults() = runTest {
        var status = HttpStatusCode.PreconditionFailed
        val (api, auth) = api {
            when (status) {
                HttpStatusCode.PreconditionFailed -> json("""{"id":"c1","revision":5,"deleted":false,"updatedAt":"t","character":$file}""", status)
                HttpStatusCode.Conflict -> json("""{"error":"","code":"id_taken"}""", status)
                else -> json("""{"error":"","code":"x"}""", status)
            }
        }
        val conflict = api.put("c1", file, 3) as WriteResult.Conflict
        assertEquals(5, conflict.server.revision)

        status = HttpStatusCode.Conflict
        assertEquals(WriteResult.IdTaken, api.put("c1", file, 3))
        status = HttpStatusCode.NotFound
        assertEquals(WriteResult.NotFound, api.delete("c1", 3))

        status = HttpStatusCode.InternalServerError
        assertTrue(!runCatching { api.changes(0) }.exceptionOrNull().let { it as SyncException }.offline)

        // 401 — сессия кончилась: выходим, как и на остальных запросах.
        status = HttpStatusCode.Unauthorized
        runCatching { api.changes(0) }
        assertEquals(AuthState.SignedOut(), auth.state.value)
    }

    @Test
    fun noConnectionIsOffline() = runTest {
        val (api) = api { throw IOException("no route") }
        assertTrue((runCatching { api.put("c1", file, 0) }.exceptionOrNull() as SyncException).offline)
    }
}
