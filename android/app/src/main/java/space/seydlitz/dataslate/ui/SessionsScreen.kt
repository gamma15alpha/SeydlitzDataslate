package space.seydlitz.dataslate.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.api.SessionInfo
import space.seydlitz.dataslate.auth.SessionsState
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


@Composable
fun SessionsScreen(state: SessionsState, onEnd: (String) -> Unit, onEndOthers: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.sessions_title).uppercase(), style = MaterialTheme.typography.headlineMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.sessions, key = { it.id }) { SessionItem(it, !state.busy, onEnd) }
        }
        state.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) }
        if (state.sessions.size > 1) {
            OutlinedButton(onEndOthers, Modifier.fillMaxWidth(), enabled = !state.busy) {
                Text(stringResource(R.string.sessions_end_others).uppercase())
            }
        }
        TextButton(onBack) { Text(stringResource(R.string.back), color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun SessionItem(session: SessionInfo, enabled: Boolean, onEnd: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(device(session.userAgent) + if (session.current) " " + stringResource(R.string.this_device) else "")
        Text(
            stringResource(R.string.session_dates, formatDate(session.createdAt), formatDate(session.lastUsedAt)),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!session.current) {
            OutlinedButton({ onEnd(session.id) }, enabled = enabled) { Text(stringResource(R.string.session_end).uppercase()) }
        }
    }
}

@Composable
internal fun formatDate(iso: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    return runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(format) }.getOrDefault(iso)
}

@Composable
private fun device(ua: String): String {
    if (ua.startsWith("SeydlitzDataslate-Android")) {
        val model = ua.substringAfter("; ", "").substringBefore(")").takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ""
        return stringResource(R.string.android_app) + model
    }
    val os = listOf("Android" to "Android", "iPhone" to "iPhone", "Windows" to "Windows", "Mac OS" to "macOS", "Linux" to "Linux")
        .firstOrNull { ua.contains(it.first) }?.second
    val browser = listOf("Edg/" to "Edge", "Firefox/" to "Firefox", "Chrome/" to "Chrome", "Safari/" to "Safari")
        .firstOrNull { ua.contains(it.first) }?.second
    return listOfNotNull(browser, os).joinToString(", ").ifEmpty { ua }.ifEmpty { stringResource(R.string.unknown_device) }
}
