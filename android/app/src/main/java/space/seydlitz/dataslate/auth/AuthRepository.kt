package space.seydlitz.dataslate.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.seydlitz.dataslate.api.ApiClient
import space.seydlitz.dataslate.api.ApiResult
import space.seydlitz.dataslate.api.LoginRequest
import space.seydlitz.dataslate.api.RegisterRequest
import space.seydlitz.dataslate.api.Session
import space.seydlitz.dataslate.api.User

sealed interface AuthState {
    data object Loading : AuthState
    data class SignedOut(val revoked: Boolean = false) : AuthState
    data class SignedIn(val user: User, val offline: Boolean) : AuthState
}

// Офлайн пускаем по сохранённой сессии; выкидывает только 401 от сервера.
class AuthRepository(
    private val store: SessionStore,
    apiFactory: (token: () -> String?, onNewToken: suspend (String) -> Unit) -> ApiClient,
) {
    @Volatile
    private var session: SavedSession? = null
    private val api = apiFactory({ session?.token }, ::rotated)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun restore() {
        val saved = store.load()
        if (saved == null) {
            _state.value = AuthState.SignedOut()
            return
        }
        session = saved
        _state.value = when (val r = authed { api.me() }) {
            is ApiResult.Ok -> {
                session?.let { save(it.copy(user = r.value)) } // токен мог смениться ротацией
                AuthState.SignedIn(r.value, offline = false)
            }
            is ApiResult.Offline -> AuthState.SignedIn(saved.user, offline = true)
            is ApiResult.Failure -> if (r.status == 401) {
                _state.value
            } else {
                AuthState.SignedIn(saved.user, offline = false) // сбой сервера — не повод разлогинивать
            }
        }
    }

    suspend fun sessions() = authed { api.sessions() }

    suspend fun deleteSession(id: String) = authed { api.deleteSession(id) }

    suspend fun deleteOtherSessions() = authed { api.deleteOtherSessions() }

    // 401 на запросе с сессией — она истекла или отозвана: выходим.
    private suspend fun <T> authed(call: suspend () -> ApiResult<T>): ApiResult<T> {
        val result = call()
        if (result is ApiResult.Failure && result.status == 401) {
            forget()
            _state.value = AuthState.SignedOut(revoked = result.code == "session_revoked")
        }
        return result
    }

    private suspend fun rotated(token: String) {
        session?.let { save(it.copy(token = token)) }
    }

    private suspend fun save(saved: SavedSession) {
        session = saved
        store.save(saved)
    }

    suspend fun login(login: String, password: String): ApiResult.Problem? =
        start(api.login(LoginRequest(login, password)))

    suspend fun register(request: RegisterRequest): ApiResult.Problem? = start(api.register(request))

    // Токен удаляется и без связи; серверная сессия тогда истечёт сама.
    suspend fun logout() {
        api.logout()
        forget()
        _state.value = AuthState.SignedOut()
    }

    private suspend fun start(result: ApiResult<Session>): ApiResult.Problem? {
        if (result !is ApiResult.Ok) return result as ApiResult.Problem
        save(SavedSession(result.value.token, result.value.user))
        _state.value = AuthState.SignedIn(result.value.user, offline = false)
        return null
    }

    private suspend fun forget() {
        session = null
        store.clear()
    }
}
