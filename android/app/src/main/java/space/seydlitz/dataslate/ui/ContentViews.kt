package space.seydlitz.dataslate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.content.CatalogEntry
import space.seydlitz.dataslate.content.LocalContentLang
import space.seydlitz.dataslate.content.termParts

private const val REF_SCHEME = "dataslate:"

// Ag · Ловкость · Agility; нажимается, если у записи есть описание. custom — своё значение игрока, не переводится.
@Composable
fun ContentTerm(entry: CatalogEntry?, custom: String? = null, onOpen: (CatalogEntry) -> Unit = {}) {
    if (custom != null) {
        Text(custom, fontStyle = FontStyle.Italic)
        return
    }
    if (entry == null) {
        Text("—", color = MaterialTheme.colorScheme.secondary)
        return
    }
    val parts = entry.termParts(LocalContentLang.current)
    val openable = entry.description != null
    val text = buildAnnotatedString {
        parts.abbreviation?.let { withStyle(SpanStyle(color = Amber)) { append(it) }; append("  ") }
        withStyle(SpanStyle(textDecoration = if (openable) TextDecoration.Underline else null)) { append(parts.name) }
        parts.english?.let { append("  "); withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)) { append(it) } }
    }
    Text(text, Modifier.then(if (openable) Modifier.clickable { onOpen(entry) } else Modifier))
}

// Справка: полоса заголовка и прокручиваемое тело. Ссылки dataslate: — onNavigate, http(s) — браузер, прочие игнорируются.
@Composable
fun ContentArticle(
    entry: CatalogEntry,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalContentLang.current
    val main = entry.description?.pick(lang).orEmpty()
    val others = entry.description?.all()?.filter { it.second != main }.orEmpty()
    var showOriginal by rememberSaveable(entry.id) { mutableStateOf(false) }

    val external = LocalUriHandler.current
    val uriHandler = remember(onNavigate, external) {
        object : UriHandler {
            override fun openUri(uri: String) {
                when {
                    uri.startsWith(REF_SCHEME) -> onNavigate(uri.removePrefix(REF_SCHEME))
                    uri.startsWith("https://") || uri.startsWith("http://") -> external.openUri(uri)
                }
            }
        }
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(PhosphorDim.copy(alpha = 0.22f)).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canGoBack) TextButton(onBack) { Text(stringResource(R.string.back)) }
            Column(Modifier.weight(1f)) { ContentTerm(entry.withoutDescription()) }
            TextButton(onClose) { Text("×", style = MaterialTheme.typography.headlineMedium) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                Markdown(main)
                if (others.isNotEmpty()) {
                    TextButton({ showOriginal = !showOriginal }) { Text(stringResource(R.string.original)) }
                    if (showOriginal) others.forEach { (l, text) ->
                        Text(l.uppercase(), color = MaterialTheme.colorScheme.secondary)
                        Markdown(text)
                    }
                }
            }
        }
    }
}

// Заголовок справки — та же подпись, но не нажимается.
private fun CatalogEntry.withoutDescription(): CatalogEntry = object : CatalogEntry by this {
    override val description = null
}
