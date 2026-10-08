package space.seydlitz.dataslate.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.seydlitz.dataslate.DataslateApp
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.api.User
import space.seydlitz.dataslate.characters.ImportResult
import space.seydlitz.dataslate.characters.SyncState
import space.seydlitz.dataslate.content.Author
import space.seydlitz.dataslate.content.CharacterFile
import space.seydlitz.dataslate.content.Choice
import space.seydlitz.dataslate.content.Dh1Content
import space.seydlitz.dataslate.content.LocalContentLang
import space.seydlitz.dataslate.content.ReadError
import java.io.IOException

private val SYSTEM_NAMES = mapOf("dh1" to "Dark Heresy")

private val IMPORT_ERRORS = mapOf(
    ReadError.NOT_JSON to R.string.import_error_NOT_JSON,
    ReadError.NOT_CHARACTER to R.string.import_error_NOT_CHARACTER,
    ReadError.FORMAT_TOO_NEW to R.string.import_error_FORMAT_TOO_NEW,
    ReadError.UNKNOWN_SYSTEM to R.string.import_error_UNKNOWN_SYSTEM,
    ReadError.SHEET_TOO_NEW to R.string.import_error_SHEET_TOO_NEW,
    ReadError.INVALID to R.string.import_error_INVALID,
)

// Своя анкета — текущее имя учётки (могли сменить), чужая — сохранённое в файле (D67).
fun authorName(character: CharacterFile, me: User?): String? =
    character.author?.let { if (it.id == me?.id) me.displayName else it.name }

fun choiceText(content: Dh1Content?, section: String, choice: Choice?, lang: String): String? = when (choice) {
    null -> null
    is Choice.Custom -> choice.text
    is Choice.Id -> content?.find("$section/${choice.id}")?.name?.pick(lang) ?: choice.id
}

// null — прочитать не удалось.
suspend fun Context.readText(uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

private class Notice(val text: String, val error: Boolean = false)

@Composable
fun SheetsScreen(user: User, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DataslateApp
    val repo = app.characters
    val list by repo.list.collectAsStateWithLifecycle()
    val syncState by repo.syncState.collectAsStateWithLifecycle()
    val conflicts by repo.conflicts.collectAsStateWithLifecycle()
    val content by produceState<Dh1Content?>(null) { value = app.content() }
    val lang = LocalContentLang.current
    val scope = rememberCoroutineScope()

    var notice by remember { mutableStateOf<Notice?>(null) }
    var importConflict by remember { mutableStateOf<ImportResult.Conflict?>(null) }
    val importedText = stringResource(R.string.sheets_imported)
    val readFailed = stringResource(R.string.file_read_failed)
    val errorTexts = IMPORT_ERRORS.mapValues { stringResource(it.value) }
    val newName = stringResource(R.string.sheets_new_name)

    fun imported(file: CharacterFile) {
        importConflict = null
        notice = Notice(importedText.format(file.name))
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importConflict = null
            val text = context.readText(uri)
            if (text == null) {
                notice = Notice(readFailed, error = true)
                return@launch
            }
            when (val r = repo.import(text)) {
                is ImportResult.Error -> notice = Notice(errorTexts.getValue(r.error), error = true)
                is ImportResult.Conflict -> {
                    notice = null
                    importConflict = r
                }
                is ImportResult.Imported -> imported(r.file)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Actions {
                OutlinedButton({
                    scope.launch { onOpen(repo.create(newName, Author(user.id, user.displayName)).id) }
                }) { Text(stringResource(R.string.sheets_create)) }
                OutlinedButton({ importer.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.sheets_import)) }
            }
        }
        notice?.let { n ->
            item { Panel { Text(n.text, color = if (n.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
        }
        importConflict?.let { c ->
            item {
                Panel {
                    Text(stringResource(R.string.sheets_conflict, c.existing.name))
                    Muted(stringResource(R.string.sheets_conflict_existing, formatDate(c.existing.updatedAt)))
                    Muted(stringResource(R.string.sheets_conflict_incoming, formatDate(c.incoming.updatedAt)))
                    Actions {
                        OutlinedButton({ scope.launch { repo.replace(c.incoming); imported(c.incoming) } }) {
                            Text(stringResource(R.string.sheets_replace))
                        }
                        OutlinedButton({ scope.launch { imported(repo.importAsCopy(c.incoming)) } }) {
                            Text(stringResource(R.string.sheets_keep_both))
                        }
                        TextButton({ importConflict = null }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
        if (syncState == SyncState.Offline || syncState == SyncState.Error) {
            item {
                Panel {
                    Muted(stringResource(if (syncState == SyncState.Offline) R.string.sync_offline else R.string.sync_error))
                    TextButton(repo::requestSync) { Text(stringResource(R.string.sync_retry)) }
                }
            }
        }
        items(conflicts, key = { "conflict-${it.id}" }) { c ->
            SyncConflictPanel(c, { how -> repo.resolve(c, how) }, onCopy = { onOpen(it.id) })
        }

        val sheets = list
        when {
            sheets == null -> Unit
            sheets.isEmpty() -> item { Muted(stringResource(R.string.sheets_empty)) }
            else -> items(sheets, key = { it.id }) { c ->
                Column(
                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).clickable { onOpen(c.id) }.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Muted(SYSTEM_NAMES[c.system] ?: c.system)
                    Text(c.name, style = MaterialTheme.typography.headlineMedium)
                    val career = listOfNotNull(
                        choiceText(content, "careers", c.sheet.career, lang),
                        choiceText(content, "ranks", c.sheet.rank, lang),
                    ).joinToString(" · ")
                    if (career.isNotEmpty()) Text(career)
                    val meta = listOfNotNull(
                        authorName(c, user)?.let { stringResource(R.string.sheets_player, it) },
                        stringResource(R.string.sheets_updated, formatDate(c.updatedAt)),
                    ).joinToString(" · ")
                    Muted(meta)
                }
            }
        }
    }
}
