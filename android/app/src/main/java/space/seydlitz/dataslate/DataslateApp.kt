package space.seydlitz.dataslate

import android.app.Application
import android.os.Build
import io.ktor.client.engine.okhttp.OkHttp
import space.seydlitz.dataslate.api.ApiClient
import space.seydlitz.dataslate.auth.AuthRepository
import space.seydlitz.dataslate.auth.KeystoreSessionStore

class DataslateApp : Application() {
    // Узнаваемо в списке сессий: «SeydlitzDataslate-Android/0.1.0 (Android 15; Pixel 8)».
    private val userAgent = "SeydlitzDataslate-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    val auth by lazy {
        AuthRepository(KeystoreSessionStore(this)) { token, onNewToken ->
            ApiClient(BuildConfig.API_URL, OkHttp.create(), userAgent, token, onNewToken)
        }
    }
}
