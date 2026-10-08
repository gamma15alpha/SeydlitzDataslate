package space.seydlitz.dataslate.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.seydlitz.dataslate.DataslateApp
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.api.User
import space.seydlitz.dataslate.characters.SheetState
import space.seydlitz.dataslate.characters.SheetViewModel
import space.seydlitz.dataslate.content.CatalogEntry
import space.seydlitz.dataslate.content.CharacterFile
import space.seydlitz.dataslate.content.Choice
import space.seydlitz.dataslate.content.Dh1Content
import space.seydlitz.dataslate.content.Dh1Sheet
import space.seydlitz.dataslate.content.Entry
import space.seydlitz.dataslate.content.LocalizedText
import space.seydlitz.dataslate.rules.DH1_CHARACTERISTICS
import space.seydlitz.dataslate.rules.Dh1Engine
import java.io.IOException

// onOpen — другая анкета (копия после конфликта или после смены id), onClosed — анкета удалена.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScreen(id: String, user: User, onOpen: (String) -> Unit, onClosed: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DataslateApp
    val repo = app.characters
    val vm: SheetViewModel = viewModel(key = id, factory = viewModelFactory { initializer { SheetViewModel(repo, id) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val content by produceState<Dh1Content?>(null) { value = app.content() }
    val conflicts by repo.conflicts.collectAsStateWithLifecycle()
    val renamed by repo.renamed.collectAsStateWithLifecycle()
    val saveFailed by repo.saveFailed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(renamed[id]) { renamed[id]?.let(onOpen) }

    // Справки: последняя — на экране, «Назад» снимает её.
    var opened by remember { mutableStateOf(listOf<CatalogEntry>()) }
    var notFound by remember { mutableStateOf<String?>(null) }
    val open: (CatalogEntry) -> Unit = { opened = listOf(it) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var fileError by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = repo.export(id)?.let(repo::exportText) ?: return@launch
            fileError = !withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } != null
                } catch (_: IOException) {
                    false
                }
            }
        }
    }

    val file = (state as? SheetState.Ready)?.file
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (file == null) {
            if (state == SheetState.Missing) Muted(stringResource(R.string.sheet_not_found))
            return@Column
        }
        if (saveFailed) Text(stringResource(R.string.sheet_save_failed), color = MaterialTheme.colorScheme.error)
        conflicts.firstOrNull { it.id == id }?.let { c ->
            SyncConflictPanel(c, { how -> repo.resolve(c, how) }, onCopy = { onOpen(it.id) })
        }

        Header(file, user, content, vm::edit, open)
        content?.let { Stats(file.sheet, it, open) }
        notFound?.let { Text(stringResource(R.string.not_found, it), color = MaterialTheme.colorScheme.error) }

        Actions {
            OutlinedButton({ exporter.launch(exportName(file)) }) { Text(stringResource(R.string.sheet_export)) }
            if (!confirmDelete) OutlinedButton({ confirmDelete = true }) { Text(stringResource(R.string.sheet_delete)) }
        }
        if (fileError) Text(stringResource(R.string.file_read_failed), color = MaterialTheme.colorScheme.error)
        if (confirmDelete) {
            Panel {
                Text(stringResource(R.string.sheet_delete_confirm, file.name), color = MaterialTheme.colorScheme.error)
                Actions {
                    OutlinedButton({
                        scope.launch {
                            vm.delete()
                            onClosed()
                        }
                    }) { Text(stringResource(R.string.sheet_delete_yes)) }
                    TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }

    if (opened.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { opened = emptyList() }, containerColor = MaterialTheme.colorScheme.background) {
            ContentArticle(
                entry = opened.last(),
                canGoBack = opened.size > 1,
                onBack = { opened = opened.dropLast(1) },
                onClose = { opened = emptyList() },
                onNavigate = { ref ->
                    val entry = content?.find(ref)
                    notFound = if (entry == null) ref else null
                    if (entry != null) opened = opened + entry
                },
                modifier = Modifier.fillMaxHeight(0.75f),
            )
        }
    }
}

private fun exportName(file: CharacterFile) =
    "${file.name.replace(Regex("""[\\/:*?"<>|]+"""), "_").trim().ifEmpty { "character" }}.character.json"

@Composable
private fun Header(
    file: CharacterFile,
    user: User,
    content: Dh1Content?,
    edit: ((CharacterFile) -> CharacterFile) -> Unit,
    open: (CatalogEntry) -> Unit,
) {
    val sheet = file.sheet
    fun editSheet(change: Dh1Sheet.() -> Dh1Sheet) = edit { it.copy(sheet = it.sheet.change()) }

    OutlinedTextField(
        file.name,
        { name -> edit { it.copy(name = name) } },
        Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.sheet_name)) },
    )
    Field(stringResource(R.string.player)) {
        Text(authorName(file, user) ?: "—", color = if (file.author == null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    val careers = content?.careers.orEmpty()
    // Ранги — выбранной карьеры; карьера своя или не выбрана — всех.
    val careerId = (sheet.career as? Choice.Id)?.id
    val ranks = careers.filter { careerId == null || it.id == careerId }.flatMap { it.ranks }

    ChoiceField(stringResource(R.string.homeworld), sheet.homeworld, content?.homeworlds.orEmpty(), { editSheet { copy(homeworld = it) } }, open)
    ChoiceField(stringResource(R.string.career), sheet.career, careers, { career ->
        editSheet {
            val newId = (career as? Choice.Id)?.id
            val allowed = careers.filter { newId == null || it.id == newId }.flatMap { it.ranks }.map { it.id }
            val keepRank = (rank as? Choice.Id)?.id?.let { it in allowed } ?: true
            copy(career = career, rank = if (keepRank) rank else null)
        }
    }, open)
    ChoiceField(stringResource(R.string.rank), sheet.rank, ranks, { editSheet { copy(rank = it) } }, open)
}

@Composable
private fun Stats(sheet: Dh1Sheet, content: Dh1Content, open: (CatalogEntry) -> Unit) {
    val evaluation = remember(sheet, content) { Dh1Engine.evaluate(sheet, content) }
    Section(stringResource(R.string.characteristics))
    DH1_CHARACTERISTICS.forEach { id ->
        val stat = evaluation.characteristics.firstOrNull { it.id == id }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ContentTerm(content.find("characteristics/$id") ?: Entry(id, LocalizedText(id.uppercase())), onOpen = open)
                // Источник каждой цифры.
                stat?.contributions?.takeIf { it.isNotEmpty() }?.let { c -> Muted(c.joinToString(" · ") { "${it.source} ${it.value}" }) }
            }
            Text(stat?.value?.toString() ?: "—")
        }
    }

    Section(stringResource(R.string.skills))
    sheet.skills.forEach { s ->
        Column {
            ContentTerm(content.find("skills/${s.skill}") ?: Entry(s.skill, LocalizedText(s.skill)), onOpen = open)
            when (val spec = s.specialization) {
                is Choice.Custom -> ContentTerm(null, custom = spec.text)
                is Choice.Id -> ContentTerm(content.find("specializations/${spec.id}"), onOpen = open)
                null -> Unit
            }
            Muted(if (s.training > 1) stringResource(R.string.training_bonus, 10 * (s.training - 1)) else stringResource(R.string.trained))
        }
    }
}

@Composable
private fun Field(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.width(120.dp))
        value()
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
}
