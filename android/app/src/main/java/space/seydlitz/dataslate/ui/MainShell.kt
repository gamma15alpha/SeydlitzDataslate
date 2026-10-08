package space.seydlitz.dataslate.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import space.seydlitz.dataslate.DataslateApp
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.api.User
import space.seydlitz.dataslate.auth.SessionsViewModel
import space.seydlitz.dataslate.content.ContentLang
import space.seydlitz.dataslate.content.LocalContentLang
import space.seydlitz.dataslate.content.resolve

@Serializable
object SheetsRoute

@Serializable
data class SheetRoute(val id: String)

@Serializable
object ReferenceRoute

@Serializable
object ProfileRoute

@Serializable
object SessionsRoute

@Serializable
object SettingsRoute

private class Section(val route: Any, @StringRes val label: Int)

// Разделы — как на вебе (D61): анкеты, справочник, профиль, настройки.
private val sections = listOf(
    Section(SheetsRoute, R.string.nav_sheets),
    Section(ReferenceRoute, R.string.nav_reference),
    Section(ProfileRoute, R.string.nav_profile),
    Section(SettingsRoute, R.string.nav_settings),
)

@Composable
fun MainShell(user: User, offline: Boolean, sessionsVm: SessionsViewModel, onLogout: () -> Unit) {
    val app = LocalContext.current.applicationContext as DataslateApp
    val contentLang by app.contentLanguage.setting.collectAsStateWithLifecycle(ContentLang.UI)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination

    CompositionLocalProvider(LocalContentLang provides contentLang.resolve()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Только на разделах: анкета и сессии — во весь экран, с «Назад».
                if (sections.none { destination?.hasRoute(it.route::class) == true }) return@Scaffold
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    sections.forEach { s ->
                        val selected = destination?.hasRoute(s.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(s.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(stringResource(s.label).uppercase(), style = MaterialTheme.typography.labelLarge) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, SheetsRoute, Modifier.padding(padding).consumeWindowInsets(padding)) {
                composable<SheetsRoute> { SheetsScreen(user) { nav.navigate(SheetRoute(it)) } }
                composable<SheetRoute> { e ->
                    val id = e.toRoute<SheetRoute>().id
                    Column {
                        TextButton({ nav.popBackStack() }) { Text(stringResource(R.string.back), color = MaterialTheme.colorScheme.secondary) }
                        SheetScreen(
                            id,
                            user,
                            onOpen = { other -> nav.navigate(SheetRoute(other)) { popUpTo<SheetRoute> { inclusive = true } } },
                            onClosed = { nav.popBackStack() },
                        )
                    }
                }
                composable<ReferenceRoute> { Page { Muted(stringResource(R.string.reference_soon)) } }
                composable<ProfileRoute> { ProfileScreen(user, offline, { nav.navigate(SessionsRoute) }, onLogout) }
                composable<SessionsRoute> {
                    LaunchedEffect(Unit) { sessionsVm.reload() }
                    val sessions by sessionsVm.state.collectAsStateWithLifecycle()
                    SessionsScreen(sessions, sessionsVm::end, sessionsVm::endOthers) { nav.popBackStack() }
                }
                composable<SettingsRoute> { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun ProfileScreen(user: User, offline: Boolean, onSessions: () -> Unit, onLogout: () -> Unit) = Page {
    Text(user.displayName, style = MaterialTheme.typography.headlineMedium)
    Muted(user.login)
    if (offline) Text(stringResource(R.string.offline), color = MaterialTheme.colorScheme.error)
    TextButton(onSessions) { Text(stringResource(R.string.sessions_link)) }
    OutlinedButton(onLogout) { Text(stringResource(R.string.logout).uppercase()) }
}

@Composable
private fun SettingsScreen() = Page {
    val store = (LocalContext.current.applicationContext as DataslateApp).contentLanguage
    val setting by store.setting.collectAsStateWithLifecycle(ContentLang.UI)
    val scope = rememberCoroutineScope()
    Muted(stringResource(R.string.ui_lang))
    LanguageSwitch()
    Muted(stringResource(R.string.content_lang))
    Actions {
        ContentLang.entries.forEach { lang ->
            TextButton({ scope.launch { store.set(lang) } }, enabled = lang != setting) {
                Text(if (lang == ContentLang.UI) stringResource(R.string.content_lang_ui) else lang.code!!.uppercase())
            }
        }
    }
}
