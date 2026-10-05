package space.seydlitz.dataslate.auth

import androidx.annotation.StringRes
import space.seydlitz.dataslate.R
import space.seydlitz.dataslate.api.ApiResult

// Текст из ресурсов: язык выбирается при показе, а не в ViewModel. Аргумент может быть UiText.
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList())

private val codes = mapOf(
    "invalid_credentials" to R.string.error_invalid_credentials,
    "invalid_invite" to R.string.error_invalid_invite,
    "login_taken" to R.string.error_login_taken,
    "invalid_login" to R.string.error_invalid_login,
    "invalid_password" to R.string.error_invalid_password,
    "invalid_display_name" to R.string.error_invalid_display_name,
)

fun ApiResult.Problem.message(): UiText = when (this) {
    is ApiResult.Offline -> UiText(R.string.error_no_connection)
    is ApiResult.Failure -> when (code) {
        "too_many_attempts", "rate_limited" -> UiText(R.string.error_too_many, listOf(retryAfter(retryAfterSeconds)))
        else -> codes[code]?.let { UiText(it) } ?: UiText(R.string.error_server_with_id, listOf(status, requestId ?: "—"))
    }
}

private fun retryAfter(seconds: Long?): UiText {
    val s = (seconds ?: 60).toInt()
    return if (s < 60) UiText(R.string.seconds, listOf(s)) else UiText(R.string.minutes, listOf((s + 59) / 60))
}
