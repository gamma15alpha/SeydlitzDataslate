package space.seydlitz.dataslate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import space.seydlitz.dataslate.auth.AuthViewModel
import space.seydlitz.dataslate.auth.SessionsViewModel
import space.seydlitz.dataslate.ui.App

// AppCompatActivity — для выбора языка в приложении (AppCompatDelegate.setApplicationLocales).
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = (application as DataslateApp).auth
        setContent {
            App(
                viewModel(factory = viewModelFactory { initializer { AuthViewModel(auth) } }),
                viewModel(factory = viewModelFactory { initializer { SessionsViewModel(auth) } }),
            )
        }
    }
}
