package space.seydlitz.dataslate.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.characters.Resolution
import space.seydlitz.dataslate.content.CharacterFile
import space.seydlitz.dataslate.sync.SyncConflict

// Панель на странице вместо модальных окон — как на вебе (D61).
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Actions(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun Muted(text: String) {
    Text(text, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
}

// Конфликт синхронизации (D68): анкету изменили и здесь, и на другом устройстве. onResolve возвращает копию для «обе».
@Composable
fun SyncConflictPanel(conflict: SyncConflict, onResolve: suspend (Resolution) -> CharacterFile?, onCopy: (CharacterFile) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val describe: @Composable (CharacterFile?) -> String = {
        if (it == null) stringResource(R.string.sync_deleted) else stringResource(R.string.sync_changed, formatDate(it.updatedAt))
    }
    val resolve: (Resolution) -> Unit = { how ->
        busy = true
        scope.launch {
            try {
                onResolve(how)?.let(onCopy)
            } finally {
                busy = false
            }
        }
    }
    Panel {
        Text(stringResource(R.string.sync_conflict, (conflict.local ?: conflict.server)?.name.orEmpty()), color = MaterialTheme.colorScheme.error)
        Muted(stringResource(R.string.sync_local, describe(conflict.local)))
        Muted(stringResource(R.string.sync_server, describe(conflict.server)))
        Actions {
            OutlinedButton({ resolve(Resolution.Mine) }, enabled = !busy) { Text(stringResource(R.string.sync_keep_mine)) }
            OutlinedButton({ resolve(Resolution.Server) }, enabled = !busy) { Text(stringResource(R.string.sync_take_server)) }
            if (conflict.local != null && conflict.server != null) {
                OutlinedButton({ resolve(Resolution.Both) }, enabled = !busy) { Text(stringResource(R.string.sync_keep_both)) }
            }
        }
    }
}
