package space.seydlitz.dataslate.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.seydlitz.dataslate.api.ApiResult
import space.seydlitz.dataslate.api.SessionInfo

data class SessionsState(
    val sessions: List<SessionInfo> = emptyList(),
    val busy: Boolean = true,
    val error: String? = null,
)

class SessionsViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SessionsState())
    val state = _state.asStateFlow()

    fun end(id: String) = run { repo.deleteSession(id) }

    fun endOthers() = run { repo.deleteOtherSessions() }

    private fun run(action: suspend () -> ApiResult<Unit>) {
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val r = action()
            load(error = (r as? ApiResult.Problem)?.takeUnless { it is ApiResult.Failure && it.status == 404 }?.message())
        }
    }

    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load(error: String? = null) {
        _state.value = when (val r = repo.sessions()) {
            is ApiResult.Ok -> SessionsState(r.value, busy = false, error = error)
            is ApiResult.Problem -> _state.value.copy(busy = false, error = r.message())
        }
    }
}
