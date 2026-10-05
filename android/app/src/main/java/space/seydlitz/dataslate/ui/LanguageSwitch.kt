package space.seydlitz.dataslate.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat

private val languages = listOf("ru", "en")

// Выбор сохраняется системой (Android 13+) или AppCompat (8–12); Activity пересоздаётся сама.
@Composable
fun LanguageSwitch(modifier: Modifier = Modifier) {
    val current = LocalConfiguration.current.locales[0].language
    Row(modifier) {
        languages.forEach { lang ->
            val active = lang == current || (lang == "ru" && current !in languages)
            TextButton({ AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang)) }, enabled = !active) {
                Text(
                    lang.uppercase(),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
