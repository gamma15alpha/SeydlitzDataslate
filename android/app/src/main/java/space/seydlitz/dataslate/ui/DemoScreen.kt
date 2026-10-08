package space.seydlitz.dataslate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import space.seydlitz.dataslate.api.ApiJson
import space.seydlitz.dataslate.content.CatalogEntry
import space.seydlitz.dataslate.content.CharacterFile
import space.seydlitz.dataslate.content.Choice
import space.seydlitz.dataslate.content.ContentLang
import space.seydlitz.dataslate.content.ContentPack
import space.seydlitz.dataslate.content.Dh1Content
import space.seydlitz.dataslate.content.Entry
import space.seydlitz.dataslate.content.LocalContentLang
import space.seydlitz.dataslate.content.LocalizedText
import space.seydlitz.dataslate.content.resolve

private val CHARACTERISTICS = listOf("ws", "bs", "s", "t", "ag", "int", "per", "wp", "fel")

private class DemoData(val pack: ContentPack, val character: CharacterFile)

// Демо (только debug): примеры из schemas/examples попадают в assets debug-сборки.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as DataslateApp).contentLanguage
    val setting by store.setting.collectAsStateWithLifecycle(ContentLang.UI)
    val scope = rememberCoroutineScope()

    val data by produceState<DemoData?>(null) {
        value = withContext(Dispatchers.IO) {
            fun read(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }
            DemoData(
                ApiJson.decodeFromString(read("dh1-mock.content.json")),
                ApiJson.decodeFromString(read("dh1.character.json")),
            )
        }
    }

    // Открытые справки: последняя — на экране, «Назад» снимает её.
    var opened by remember { mutableStateOf(listOf<CatalogEntry>()) }
    var notFound by remember { mutableStateOf<String?>(null) }
    val open: (CatalogEntry) -> Unit = { opened = listOf(it) }

    CompositionLocalProvider(LocalContentLang provides setting.resolve()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text(stringResource(R.string.back), color = MaterialTheme.colorScheme.secondary) }
            }
            Text(stringResource(R.string.demo_title).uppercase(), style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.content_lang), color = MaterialTheme.colorScheme.secondary)
                ContentLang.entries.forEach { lang ->
                    TextButton({ scope.launch { store.set(lang) } }, enabled = lang != setting) {
                        Text(if (lang == ContentLang.UI) stringResource(R.string.content_lang_ui) else lang.code!!.uppercase())
                    }
                }
            }

            val d = data ?: return@Column
            val content = d.pack.content
            val sheet = d.character.sheet

            Text(d.character.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            Field(stringResource(R.string.player)) { Text(d.character.author?.name ?: "—") }
            Field(stringResource(R.string.homeworld)) { ChoiceTerm(content, "homeworlds", sheet.homeworld, open) }
            Field(stringResource(R.string.career)) { ChoiceTerm(content, "careers", sheet.career, open) }
            Field(stringResource(R.string.rank)) { ChoiceTerm(content, "ranks", sheet.rank, open) }

            Section(stringResource(R.string.characteristics))
            CHARACTERISTICS.forEach { id ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        ContentTerm(content.find("characteristics/$id") ?: Entry(id, LocalizedText(id.uppercase())), onOpen = open)
                    }
                    Text(sheet.characteristics[id]?.value?.toString() ?: "—")
                }
            }

            Section(stringResource(R.string.skills))
            sheet.skills.forEach { s ->
                Column {
                    ContentTerm(content.find("skills/${s.skill}") ?: Entry(s.skill, LocalizedText(s.skill)), onOpen = open)
                    s.specialization?.let { ChoiceTerm(content, "specializations", it, open) }
                    Text(
                        if (s.training > 1) stringResource(R.string.training_bonus, 10 * (s.training - 1)) else stringResource(R.string.trained),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            notFound?.let { Text(stringResource(R.string.not_found, it), color = MaterialTheme.colorScheme.error) }

            if (opened.isNotEmpty()) {
                ModalBottomSheet(onDismissRequest = { opened = emptyList() }, containerColor = MaterialTheme.colorScheme.background) {
                    ContentArticle(
                        entry = opened.last(),
                        canGoBack = opened.size > 1,
                        onBack = { opened = opened.dropLast(1) },
                        onClose = { opened = emptyList() },
                        onNavigate = { ref ->
                            val entry = content.find(ref)
                            notFound = if (entry == null) ref else null
                            if (entry != null) opened = opened + entry
                        },
                        modifier = Modifier.fillMaxHeight(0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceTerm(content: Dh1Content, section: String, choice: Choice?, onOpen: (CatalogEntry) -> Unit) = when (choice) {
    is Choice.Custom -> ContentTerm(null, custom = choice.text)
    is Choice.Id -> ContentTerm(content.find("$section/${choice.id}"), onOpen = onOpen)
    null -> ContentTerm(null)
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
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
}
