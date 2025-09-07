package app.multiauth.models

import kotlin.time.Duration

sealed class AuthError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    data class ValidationError(
        override val message: String,
        val field: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class ProviderError(
        override val message: String,
        val provider: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class RateLimitError(
        override val message: String,
        val retryAfter: Duration,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class StorageError(
        override val message: String,
        val operation: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class ConfigurationError(
        override val message: String,
        val configKey: String? = null,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class InvalidCredentials(
        override val message: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class InvalidToken(
        override val message: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class TokenExpired(
        override val message: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class SessionError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class RateLimitExceeded(
        override val message: String,
        val retryAfterSeconds: Long = 60,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)

    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AuthError(message, cause)
}
