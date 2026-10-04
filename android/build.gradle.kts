// Плагины подключаются в модулях; здесь — только версии (gradle/libs.versions.toml)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
