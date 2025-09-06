package app.multiauth.models

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import app.multiauth.oauth.OAuthProvider


/**
 * Represents the current authentication state of the application.
 * This is a sealed class that covers all possible authentication states.
 */
sealed class AuthState {
    /**
     * Initial state - no authentication attempt has been made.
     */
    object Initial : AuthState()
    
    /**
     * Authentication is in progress.
     */
    object Loading : AuthState()
    
    /**
     * User is not authenticated.
     */
    object Unauthenticated : AuthState()
    
    /**
     * User is authenticated.
     */
    data class Authenticated(
        val user: User,
        val tokens: TokenPair,
        val lastActivity: Instant = Clock.System.now()
    ) : AuthState()
    
    /**
     * Authentication failed.
     */
    data class Error(
        val error: AuthError,
        val previousState: AuthState? = null
    ) : AuthState()
    
    /**
     * User is in the process of verifying their identity.
     */
    data class VerificationRequired(
        val method: VerificationMethod,
        val user: User? = null
    ) : AuthState()
}

/**
 * Represents the method of verification required.
 */
sealed class VerificationMethod {
    data class Email(
        val email: String,
        val resendAvailableAt: Instant? = null
    ) : VerificationMethod()
    
    data class Phone(
        val phoneNumber: String,
        val resendAvailableAt: Instant? = null
    ) : VerificationMethod()
    
    data class OAuth(
        val provider: OAuthProvider,
        val redirectUrl: String
    ) : VerificationMethod()
}

/**
 * Represents authentication errors.
 */
sealed class AuthError : Exception() {
    data class InvalidCredentials(
        override val message: String = "Invalid email or password"
    ) : AuthError()
    
    data class UserNotFound(
        override val message: String = "User not found"
    ) : AuthError()
    
    data class UserAlreadyExists(
        override val message: String = "User already exists"
    ) : AuthError()
    
    data class InvalidToken(
        override val message: String = "Invalid or expired token"
    ) : AuthError()
    
    data class NetworkError(
        override val message: String = "Network error occurred",
        override val cause: Throwable? = null
    ) : AuthError()
    
    data class ValidationError(
        override val message: String,
        val field: String? = null
    ) : AuthError()
    
    data class RateLimitExceeded(
        override val message: String = "Too many attempts, please try again later",
        val retryAfter: Instant? = null
    ) : AuthError()
    
    data class RateLimitError(
        override val message: String,
        val retryAfterDuration: kotlin.time.Duration
    ) : AuthError()
    
    data class ProviderError(
        override val message: String,
        val provider: String,
        override val cause: Throwable? = null
    ) : AuthError()
    
    data class SessionError(
        override val message: String = "Session error occurred"
    ) : AuthError()
    
    data class UnknownError(
        override val message: String = "An unknown error occurred",
        override val cause: Throwable? = null
    ) : AuthError()
}

/**
 * Represents a pair of access and refresh tokens.
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val tokenType: String = "Bearer"
) {
    val isExpired: Boolean
        get() = Clock.System.now() >= expiresAt
    
    val expiresInSeconds: Long
        get() = (expiresAt.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()) / 1000
}

/**
 * Represents the result of an authentication operation.
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Failure(val error: AuthError) : AuthResult<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error
    }
    
    fun onSuccess(action: (T) -> Unit): AuthResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    fun onFailure(action: (AuthError) -> Unit): AuthResult<T> {
        if (this is Failure) action(error)
        return this
    }
}