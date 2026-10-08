package space.seydlitz.dataslate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.auth.AuthState
import space.seydlitz.dataslate.auth.AuthViewModel
import space.seydlitz.dataslate.auth.SessionsViewModel

@Composable
fun App(vm: AuthViewModel, sessionsVm: SessionsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    var registering by rememberSaveable { mutableStateOf(false) }

    DataslateTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val s = state) {
                AuthState.Loading -> Unit
                is AuthState.SignedOut -> {
                    if (registering) {
                        RegisterScreen(form, vm::register) { registering = false; vm.clearError() }
                    } else {
                        LoginScreen(form, if (s.revoked) stringResource(R.string.revoked) else null, vm::login) { registering = true; vm.clearError() }
                        LanguageSwitch(Modifier.align(Alignment.TopEnd).safeDrawingPadding())
                    }
                }
                is AuthState.SignedIn -> MainShell(s.user, s.offline, sessionsVm, vm::logout)
            }
        }
    }
}
