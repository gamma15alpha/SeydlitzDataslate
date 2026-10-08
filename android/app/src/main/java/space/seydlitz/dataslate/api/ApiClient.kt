package space.seydlitz.dataslate.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

sealed interface ApiResult<out T> {
    data class Ok<out T>(val value: T) : ApiResult<T>

    sealed interface Problem : ApiResult<Nothing>

    data class Failure(
        val status: Int,
        val code: String?,
        val requestId: String?,
        val retryAfterSeconds: Long?,
    ) : Problem

    data class Offline(val cause: IOException) : Problem
}

// ignoreUnknownKeys: новые поля в ответах сервера не ломают старые версии приложения.
val ApiJson = Json { ignoreUnknownKeys = true }

// onNewToken: сервер раз в сутки меняет токен (заголовок X-Session-Token) — его нужно сохранить.
class ApiClient(
    baseUrl: String,
    engine: HttpClientEngine,
    userAgent: String,
    private val token: () -> String?,
    private val onNewToken: suspend (String) -> Unit,
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(ApiJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
        defaultRequest {
            url(baseUrl.trimEnd('/') + "/")
            headers[HttpHeaders.UserAgent] = userAgent
            token()?.let { bearerAuth(it) }
        }
    }

    suspend fun me(): ApiResult<User> = call { get("api/me") }

    suspend fun login(body: LoginRequest): ApiResult<Session> = call { postJson("api/auth/login", body) }

    suspend fun register(body: RegisterRequest): ApiResult<Session> = call { postJson("api/auth/register", body) }

    suspend fun logout(): ApiResult<Unit> = call { post("api/auth/logout") }

    suspend fun sessions(): ApiResult<List<SessionInfo>> = call { get("api/sessions") }

    suspend fun deleteSession(id: String): ApiResult<Unit> = call { delete("api/sessions/$id") }

    suspend fun deleteOtherSessions(): ApiResult<Unit> = call { delete("api/sessions") }

    private suspend inline fun <reified B> HttpClient.postJson(path: String, body: B) = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun characterChanges(since: Long): ApiResult<CharacterChanges> =
        call { get("api/characters") { parameter("since", since) } }

    // Файл анкеты уходит как есть (TextContent мимо ContentNegotiation): сервер хранит его без разбора.
    suspend fun putCharacter(id: String, json: String, base: Long): ApiResult<CharacterWrite> = characterWrite {
        put("api/characters/$id") {
            precondition(base)
            setBody(TextContent(json, ContentType.Application.Json))
        }
    }

    suspend fun deleteCharacter(id: String, base: Long): ApiResult<CharacterWrite> = characterWrite {
        delete("api/characters/$id") { precondition(base) }
    }

    private fun HttpRequestBuilder.precondition(base: Long) {
        if (base == 0L) headers[HttpHeaders.IfNoneMatch] = "*" else headers[HttpHeaders.IfMatch] = "\"$base\""
    }

    // 412 — не ошибка: в теле версия сервера, клиент предложит выбор.
    private suspend inline fun characterWrite(send: HttpClient.() -> HttpResponse): ApiResult<CharacterWrite> {
        val response = try {
            client.send()
        } catch (e: IOException) {
            return ApiResult.Offline(e)
        }
        val conflict = response.status == HttpStatusCode.PreconditionFailed
        return when (val r = response.result<StoredCharacter>(conflict)) {
            is ApiResult.Ok -> ApiResult.Ok(if (conflict) CharacterWrite.Conflict(r.value) else CharacterWrite.Written(r.value))
            is ApiResult.Problem -> r
        }
    }

    private suspend inline fun <reified T> call(send: HttpClient.() -> HttpResponse): ApiResult<T> {
        val response = try {
            client.send()
        } catch (e: IOException) {
            return ApiResult.Offline(e)
        }
        return response.result()
    }

    private suspend inline fun <reified T> HttpResponse.result(acceptError: Boolean = false): ApiResult<T> {
        headers["X-Session-Token"]?.let { onNewToken(it) }
        val failure = ApiResult.Failure(
            status = status.value,
            code = null,
            requestId = headers["X-Request-ID"],
            retryAfterSeconds = headers["Retry-After"]?.toLongOrNull(),
        )
        if (status.isSuccess() || acceptError) {
            if (T::class == Unit::class) return ApiResult.Ok(Unit as T)
            // Неожиданное тело — ошибка сервера, а не падение приложения.
            return runCatching { ApiResult.Ok(body<T>()) }.getOrElse { failure.copy(code = "bad_response") }
        }
        return failure.copy(code = runCatching { body<ApiError>() }.getOrNull()?.code)
    }
}
