package space.seydlitz.dataslate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Палитра когитатора (люминофор P1) и шрифты (SIL OFL, лицензии в assets/licenses)
private val Screen = Color(0xFF030603)
private val Phosphor = Color(0xFF8DFF7A)
private val PhosphorDim = Color(0xFF4FA845)
private val MonoFont = FontFamily(Font(R.font.pt_mono))
private val InscriptionFont = FontFamily(Font(R.font.forum))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Placeholder() }
    }
}

/** Каркас: Android-клиент на Kotlin + Compose. Лист персонажа переносится срезами. */
@Composable
fun Placeholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Screen)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SEYDLITZ DATASLATE", color = Phosphor, fontFamily = InscriptionFont, fontSize = 24.sp, letterSpacing = 4.sp)
        Text(
            "Каркас Android-клиента: Kotlin + Compose.",
            color = PhosphorDim,
            fontFamily = MonoFont,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Preview
@Composable
private fun PlaceholderPreview() = Placeholder()
