package space.seydlitz.dataslate.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.api.ApiClient
import space.seydlitz.dataslate.api.User

class AuthRepositoryTest {
    private val user = User("u1", "player", "Инквизитор", false, "2026-10-05T10:00:00Z")
    private val userJson = """{"id":"u1","login":"player","displayName":"Инквизитор","isAdmin":false,"createdAt":"2026-10-05T10:00:00Z","future":1}"""

    private class MemoryStore(var saved: SavedSession? = null) : SessionStore {
        override suspend fun load() = saved
        override suspend fun save(session: SavedSession) { saved = session }
        override suspend fun clear() { saved = null }
    }

    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(store: SessionStore, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        AuthRepository(store) { token, onNewToken ->
            ApiClient("http://test", MockEngine { requests += it; handler(it) }, "test", token, onNewToken)
        }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK, vararg extra: Pair<String, String>) =
        respond(body, status, headersOf(HttpHeaders.ContentType to listOf("application/json"), *extra.map { it.first to listOf(it.second) }.toTypedArray()))

    @Test
    fun loginStoresSessionAndSendsBearer() = runTest {
        val store = MemoryStore()
        val repo = repo(store) {
            when (it.url.encodedPath) {
                "/api/auth/login" -> json("""{"token":"T1","user":$userJson}""")
                else -> json(userJson)
            }
        }
        repo.restore()
        assertEquals(AuthState.SignedOut(), repo.state.value)

        assertNull(repo.login("player", "player-password"))
        assertEquals(AuthState.SignedIn(user, offline = false), repo.state.value)
        assertEquals("T1", store.saved?.token)

        repo.restore()
        assertEquals("Bearer T1", requests.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun loginErrorIsMappedToMessage() = runTest {
        val repo = repo(MemoryStore()) {
            json("""{"error":"invalid login or password","code":"invalid_credentials"}""", HttpStatusCode.Unauthorized)
        }
        assertEquals(UiText(R.string.error_invalid_credentials), repo.login("player", "wrong")?.message())
        assertEquals(AuthState.Loading, repo.state.value)
    }

    @Test
    fun rateLimitAndUnknownErrorMessages() = runTest {
        var status = HttpStatusCode.TooManyRequests
        val repo = repo(MemoryStore()) {
            json("""{"error":"x","code":"${if (status.value == 429) "rate_limited" else "internal"}"}""", status,
                "Retry-After" to "120", "X-Request-ID" to "REQ42")
        }
        assertEquals(UiText(R.string.error_too_many, listOf(UiText(R.string.minutes, listOf(2)))), repo.login("a", "b")?.message())
        status = HttpStatusCode.InternalServerError
        assertEquals(UiText(R.string.error_server_with_id, listOf(500, "REQ42")), repo.login("a", "b")?.message())
    }

    @Test
    fun restoreOfflineKeepsCachedUser() = runTest {
        val store = MemoryStore(SavedSession("T1", user))
        val repo = repo(store) { throw IOException("no route to host") }
        repo.restore()
        assertEquals(AuthState.SignedIn(user, offline = true), repo.state.value)
        assertEquals("T1", store.saved?.token)
    }

    @Test
    fun restoreWith401SignsOut() = runTest {
        val store = MemoryStore(SavedSession("T1", user))
        val repo = repo(store) { json("""{"error":"expired","code":"unauthorized"}""", HttpStatusCode.Unauthorized) }
        repo.restore()
        assertEquals(AuthState.SignedOut(), repo.state.value)
        assertNull(store.saved)
    }

    @Test
    fun restoreWithServerErrorKeepsSession() = runTest {
        val store = MemoryStore(SavedSession("T1", user))
        val repo = repo(store) { json("""{"error":"x","code":"internal"}""", HttpStatusCode.InternalServerError) }
        repo.restore()
        assertEquals(AuthState.SignedIn(user, offline = false), repo.state.value)
        assertEquals("T1", store.saved?.token)
    }

    @Test
    fun logoutWorksOffline() = runTest {
        val store = MemoryStore(SavedSession("T1", user))
        var online = true
        val repo = repo(store) { if (online) json(userJson) else throw IOException("offline") }
        repo.restore()
        online = false
        repo.logout()
        assertEquals(AuthState.SignedOut(), repo.state.value)
        assertNull(store.saved)
    }

    @Test
    fun rotatedTokenIsSavedAndUsed() = runTest {
        val store = MemoryStore(SavedSession("T1", user))
        val repo = repo(store) {
            when {
                it.url.encodedPath == "/api/sessions" -> json("[]")
                it.headers[HttpHeaders.Authorization] == "Bearer T1" -> json(userJson, HttpStatusCode.OK, "X-Session-Token" to "T2")
                else -> json(userJson)
            }
        }
        repo.restore()
        assertEquals("T2", store.saved?.token)
        assertEquals(AuthState.SignedIn(user, offline = false), repo.state.value)

        repo.sessions()
        assertEquals("Bearer T2", requests.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun revokedSessionSignsOutWithNotice() = runTest {
        val store = MemoryStore(SavedSession("T1", user))
        var revoked = false
        val repo = repo(store) {
            if (revoked) json("""{"error":"reuse","code":"session_revoked"}""", HttpStatusCode.Unauthorized)
            else json(userJson)
        }
        repo.restore()
        revoked = true
        repo.sessions()
        assertEquals(AuthState.SignedOut(revoked = true), repo.state.value)
        assertNull(store.saved)
    }

    @Test
    fun malformedBodyIsFailureNotCrash() = runTest {
        val repo = repo(MemoryStore(SavedSession("T1", user))) {
            json(userJson) // и на /api/sessions, где ждут массив
        }
        repo.restore()
        val r = repo.sessions()
        assertEquals("bad_response", (r as space.seydlitz.dataslate.api.ApiResult.Failure).code)
    }
}
