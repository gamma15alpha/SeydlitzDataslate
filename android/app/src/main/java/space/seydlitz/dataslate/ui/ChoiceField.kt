package space.seydlitz.dataslate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.content.CatalogEntry
import space.seydlitz.dataslate.content.Choice
import space.seydlitz.dataslate.content.LocalContentLang

// choice из dh1-sheet.schema.json: запись каталога или своё значение (пустым не бывает) — как ChoiceField.vue.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceField(
    label: String,
    value: Choice?,
    entries: List<CatalogEntry>,
    onChange: (Choice?) -> Unit,
    onOpen: (CatalogEntry) -> Unit,
) {
    val lang = LocalContentLang.current
    // Режим отдельно от значения: «своё» с пустым текстом — режим есть, значения нет.
    var custom by rememberSaveable { mutableStateOf(value is Choice.Custom) }
    var text by rememberSaveable { mutableStateOf((value as? Choice.Custom)?.text.orEmpty()) }
    LaunchedEffect(value) {
        if (value != null || !custom) custom = value is Choice.Custom
        if (value is Choice.Custom) text = value.text
    }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val id = (value as? Choice.Id)?.id
    val entry = entries.firstOrNull { it.id == id }
    val shown = when {
        custom -> stringResource(R.string.sheet_custom)
        entry != null -> entry.name.pick(lang)
        id != null -> id // запись не из этого пакета — показываем как есть, не теряем
        else -> stringResource(R.string.sheet_none)
    }
    val pick: (Choice?, Boolean) -> Unit = { choice, toCustom ->
        custom = toCustom
        expanded = false
        onChange(choice)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExposedDropdownMenuBox(expanded, { expanded = it }) {
            OutlinedTextField(
                shown,
                {},
                Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                readOnly = true,
                singleLine = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            )
            ExposedDropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.sheet_none)) }, { pick(null, false) })
                entries.forEach { e -> DropdownMenuItem({ Text(e.name.pick(lang)) }, { pick(Choice.Id(e.id), false) }) }
                if (id != null && entry == null) DropdownMenuItem({ Text(id) }, { pick(value, false) })
                DropdownMenuItem({ Text(stringResource(R.string.sheet_custom)) }, {
                    pick(text.takeIf { it.isNotBlank() }?.let { Choice.Custom(it) }, true)
                })
            }
        }
        if (custom) {
            OutlinedTextField(
                text,
                {
                    text = it
                    onChange(if (it.isBlank()) null else Choice.Custom(it))
                },
                Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("$label: ${stringResource(R.string.sheet_custom)}") },
            )
        } else if (entry != null) {
            ContentTerm(entry, onOpen = onOpen)
        }
    }
}
