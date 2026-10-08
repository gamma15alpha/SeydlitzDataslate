package space.seydlitz.dataslate

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import space.seydlitz.dataslate.api.ApiClient
import space.seydlitz.dataslate.auth.AuthRepository
import space.seydlitz.dataslate.auth.AuthState
import space.seydlitz.dataslate.auth.KeystoreSessionStore
import space.seydlitz.dataslate.characters.CharacterRepository
import space.seydlitz.dataslate.characters.DataslateDatabase
import space.seydlitz.dataslate.characters.OpenedStore
import space.seydlitz.dataslate.characters.RoomDocumentStore
import space.seydlitz.dataslate.characters.ServerCharacterApi
import space.seydlitz.dataslate.content.ContentLanguageStore
import space.seydlitz.dataslate.content.Dh1Content
import space.seydlitz.dataslate.content.parseContentPack
import kotlin.time.Duration.Companion.minutes

class DataslateApp : Application() {
    // Узнаваемо в списке сессий: «SeydlitzDataslate-Android/0.1.0 (Android 15; Pixel 8)».
    private val userAgent = "SeydlitzDataslate-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    val auth by lazy {
        AuthRepository(KeystoreSessionStore(this)) { token, onNewToken ->
            ApiClient(BuildConfig.API_URL, OkHttp.create(), userAgent, token, onNewToken)
        }
    }

    val contentLanguage by lazy { ContentLanguageStore(this) }

    // Мок-пакет из schemas/examples — тот же, что у веба; каталог с сервера — срез 4.
    private val mockContent = lazy {
        parseContentPack(assets.open("dh1-mock.content.json").bufferedReader().use { it.readText() }).content
    }

    suspend fun content(): Dh1Content = withContext(Dispatchers.IO) { mockContent.value }

    val characters by lazy {
        CharacterRepository(
            open = { userId -> DataslateDatabase.open(this, userId).let { OpenedStore(RoomDocumentStore(it), it::close) } },
            api = ServerCharacterApi(auth),
        )
    }

    override fun onCreate() {
        super.onCreate()
        val process = ProcessLifecycleOwner.get()
        process.lifecycleScope.launch {
            // Своё хранилище на учётку; связь вернулась — сразу обмен.
            auth.state.map { (it as? AuthState.SignedIn)?.let { s -> s.user.id to s.offline } }
                .distinctUntilChanged()
                .collect { signedIn ->
                    characters.bind(signedIn?.first)
                    if (signedIn?.second == false) characters.requestSync()
                }
        }
        // Изменения с других устройств — опросом, пока приложение на экране (до Centrifuge, D68).
        process.lifecycleScope.launch {
            process.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    characters.requestSync()
                    delay(1.minutes)
                }
            }
        }
        process.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                // В фоне процесс могут убить без предупреждения.
                override fun onStop(owner: LifecycleOwner) {
                    owner.lifecycleScope.launch { characters.flush() }
                }
            },
        )
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = characters.requestSync()
            },
        )
    }
}
