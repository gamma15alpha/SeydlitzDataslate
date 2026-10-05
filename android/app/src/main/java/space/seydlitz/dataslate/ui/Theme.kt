package space.seydlitz.dataslate.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import space.seydlitz.dataslate.R

// Палитра когитатора (люминофор P1) — та же, что в web/app/assets/css/main.css.
val Screen = Color(0xFF030603)
val Phosphor = Color(0xFF8DFF7A)
val PhosphorDim = Color(0xFF4FA845)
val Amber = Color(0xFFFFB347)

val MonoFont = FontFamily(Font(R.font.pt_mono))
val InscriptionFont = FontFamily(Font(R.font.forum))

private val colors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Screen,
    secondary = PhosphorDim,
    background = Screen,
    onBackground = Phosphor,
    surface = Screen,
    onSurface = Phosphor,
    onSurfaceVariant = PhosphorDim,
    outline = PhosphorDim,
    error = Amber,
)

private val mono = TextStyle(fontFamily = MonoFont)

private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = InscriptionFont, fontSize = 24.sp, letterSpacing = 4.sp),
    bodyLarge = mono.copy(fontSize = 16.sp),
    bodyMedium = mono.copy(fontSize = 14.sp),
    labelLarge = mono.copy(fontSize = 14.sp, letterSpacing = 1.sp),
    bodySmall = mono.copy(fontSize = 12.sp),
)

@Composable
fun DataslateTheme(content: @Composable () -> Unit) = MaterialTheme(colors, typography = typography, content = content)
