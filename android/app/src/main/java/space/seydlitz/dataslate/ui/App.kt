package space.seydlitz.dataslate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.api.User
import space.seydlitz.dataslate.auth.AuthState
import space.seydlitz.dataslate.auth.AuthViewModel
import space.seydlitz.dataslate.auth.SessionsViewModel


@Composable
fun App(vm: AuthViewModel, sessionsVm: SessionsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    var registering by rememberSaveable { mutableStateOf(false) }
    var showSessions by rememberSaveable { mutableStateOf(false) }

    DataslateTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val s = state) {
                AuthState.Loading -> Unit
                is AuthState.SignedOut -> if (registering) {
                    RegisterScreen(form, vm::register) { registering = false; vm.clearError() }
                } else {
                    LoginScreen(form, if (s.revoked) stringResource(R.string.revoked) else null, vm::login) { registering = true; vm.clearError() }
                }
                is AuthState.SignedIn -> if (showSessions) {
                    BackHandler { showSessions = false }
                    LaunchedEffect(Unit) { sessionsVm.reload() }
                    val sessions by sessionsVm.state.collectAsStateWithLifecycle()
                    SessionsScreen(sessions, sessionsVm::end, sessionsVm::endOthers) { showSessions = false }
                } else {
                    HomeScreen(s.user, s.offline, { showSessions = true }, vm::logout)
                }
            }
            val onLoginOrHome = (state is AuthState.SignedOut && !registering) || (state is AuthState.SignedIn && !showSessions)
            if (onLoginOrHome) LanguageSwitch(Modifier.align(Alignment.TopEnd).safeDrawingPadding())
        }
    }
}

@Composable
private fun HomeScreen(user: User, offline: Boolean, onSessions: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineMedium)
        Text(user.displayName)
        Text("(${user.login})", color = MaterialTheme.colorScheme.secondary)
        if (offline) Text(stringResource(R.string.offline), color = MaterialTheme.colorScheme.error)
        TextButton(onSessions) { Text(stringResource(R.string.sessions_link), color = MaterialTheme.colorScheme.secondary) }
        OutlinedButton(onLogout) { Text(stringResource(R.string.logout).uppercase()) }
    }
}
