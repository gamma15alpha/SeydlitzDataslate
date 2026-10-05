package space.seydlitz.dataslate.auth

import space.seydlitz.dataslate.api.ApiResult

private val messages = mapOf(
    "invalid_credentials" to "Неверный логин или пароль",
    "invalid_invite" to "Инвайт недействителен, истёк или уже использован",
    "login_taken" to "Логин уже занят",
    "invalid_login" to "Логин: от 3 до 32 символов — латиница, цифры, «_», «.», «-»",
    "invalid_password" to "Пароль: от 8 до 128 символов",
    "invalid_display_name" to "Имя — не длиннее 64 символов",
)

fun ApiResult.Problem.message(): String = when (this) {
    is ApiResult.Offline -> "Нет связи с сервером"
    is ApiResult.Failure -> when (code) {
        "too_many_attempts", "rate_limited" -> "Слишком много попыток. Повторите через ${retryAfterText(retryAfterSeconds)}"
        else -> messages[code] ?: "Ошибка сервера $status, код запроса ${requestId ?: "—"}"
    }
}

private fun retryAfterText(seconds: Long?): String {
    val s = seconds ?: 60
    return if (s < 60) "$s с" else "${(s + 59) / 60} мин"
}
