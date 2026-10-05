package space.seydlitz.dataslate.content

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Язык контента отдельно от языка интерфейса: например, интерфейс на английском, а правила — в оригинале.
enum class ContentLang(val code: String?) { UI(null), RU("ru"), EN("en") }

private val Context.settings by preferencesDataStore("settings")
private val CONTENT_LANG = stringPreferencesKey("content_lang")

class ContentLanguageStore(private val context: Context) {
    val setting: Flow<ContentLang> = context.settings.data.map { prefs ->
        ContentLang.entries.firstOrNull { it.name == prefs[CONTENT_LANG] } ?: ContentLang.UI
    }

    suspend fun set(value: ContentLang) {
        context.settings.edit { it[CONTENT_LANG] = value.name }
    }
}

// Язык, на котором показывать контент; задаётся в корне экрана.
val LocalContentLang = compositionLocalOf { "ru" }

@Composable
fun ContentLang.resolve(): String = code ?: LocalConfiguration.current.locales[0].toLanguageTag()
