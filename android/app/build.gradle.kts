plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Схемы базы — в git: по ним Room проверяет миграции.
room {
    schemaDirectory("$projectDir/schemas")
}

// Адрес API: -Pdataslate.apiUrl=… или в gradle.properties. Debug по умолчанию — компьютер разработчика из эмулятора.
val apiUrl = providers.gradleProperty("dataslate.apiUrl")

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
            buildConfigField("String", "API_URL", "\"${apiUrl.getOrElse("http://10.0.2.2:8090/")}\"")
        }
        release {
            resValue("string", "app_name", "Seydlitz Dataslate")
            buildConfigField("String", "API_URL", "\"${apiUrl.getOrElse("")}\"")
            isMinifyEnabled = false
        }
    }

    // Мок-пакет контента — из schemas/examples, тот же, что и у веба; работает всегда, до каталога (срез 4).
    sourceSets.getByName("main").assets.directories.add("../../schemas/examples")

    // Язык приложения можно выбрать в настройках системы (Android 13+); список — из values-*/.
    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.process)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst { require(apiUrl.isPresent) { "Release: задайте адрес сервера, -Pdataslate.apiUrl=https://…" } }
}
