package space.seydlitz.dataslate.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.api.ApiResult
import space.seydlitz.dataslate.api.RegisterRequest

data class FormStatus(val busy: Boolean = false, val error: UiText? = null)

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    val state = repo.state

    private val _form = MutableStateFlow(FormStatus())
    val form = _form.asStateFlow()

    init {
        viewModelScope.launch { repo.restore() }
    }

    fun login(login: String, password: String) = submit { repo.login(login.trim(), password) }

    fun register(invite: String, login: String, displayName: String, password: String, repeat: String) {
        if (password != repeat) {
            _form.value = FormStatus(error = UiText(R.string.passwords_mismatch))
            return
        }
        submit {
            repo.register(
                RegisterRequest(
                    invite = invite.trim(),
                    login = login.trim(),
                    password = password,
                    displayName = displayName.trim().ifEmpty { null },
                ),
            )
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }

    fun clearError() {
        _form.value = _form.value.copy(error = null)
    }

    private fun submit(action: suspend () -> ApiResult.Problem?) {
        if (_form.value.busy) return
        _form.value = FormStatus(busy = true)
        viewModelScope.launch {
            _form.value = FormStatus(error = action()?.message())
        }
    }
}
