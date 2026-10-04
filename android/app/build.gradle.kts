plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "space.seydlitz.dataslate"
    // compileSdk — какими API можно пользоваться (новые AndroidX требуют 37); поведение на устройстве задаёт targetSdk
    compileSdk = 37

    defaultConfig {
        // Идентификатор приложения: после первой раздачи APK не менять — иначе это другое приложение
        applicationId = "space.seydlitz.dataslate"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // Отладочная сборка ставится рядом с рабочей и не трогает её данные
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Dataslate (debug)")
        }
        release {
            resValue("string", "app_name", "Seydlitz Dataslate")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
