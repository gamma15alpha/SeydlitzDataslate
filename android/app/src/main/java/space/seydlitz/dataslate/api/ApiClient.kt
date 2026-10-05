package space.seydlitz.dataslate.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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

    private suspend inline fun <reified T> call(send: HttpClient.() -> HttpResponse): ApiResult<T> {
        val response = try {
            client.send()
        } catch (e: IOException) {
            return ApiResult.Offline(e)
        }
        response.headers["X-Session-Token"]?.let { onNewToken(it) }
        val failure = ApiResult.Failure(
            status = response.status.value,
            code = null,
            requestId = response.headers["X-Request-ID"],
            retryAfterSeconds = response.headers["Retry-After"]?.toLongOrNull(),
        )
        if (response.status.isSuccess()) {
            if (T::class == Unit::class) return ApiResult.Ok(Unit as T)
            // Неожиданное тело — ошибка сервера, а не падение приложения.
            return runCatching { ApiResult.Ok(response.body<T>()) }.getOrElse { failure.copy(code = "bad_response") }
        }
        return failure.copy(code = runCatching { response.body<ApiError>() }.getOrNull()?.code)
    }
}
