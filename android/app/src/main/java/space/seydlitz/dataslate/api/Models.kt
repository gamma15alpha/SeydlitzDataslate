package space.seydlitz.dataslate.api

import kotlinx.serialization.Serializable

// Модели — по schemas/openapi.yaml.

@Serializable
data class User(
    val id: String,
    val login: String,
    val displayName: String,
    val isAdmin: Boolean,
    val createdAt: String,
)

@Serializable
data class Session(val token: String, val user: User)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class RegisterRequest(
    val invite: String,
    val login: String,
    val password: String,
    val displayName: String? = null,
)

@Serializable
data class SessionInfo(
    val id: String,
    val createdAt: String,
    val lastUsedAt: String,
    val userAgent: String,
    val current: Boolean,
)

@Serializable
data class ApiError(val error: String, val code: String)
