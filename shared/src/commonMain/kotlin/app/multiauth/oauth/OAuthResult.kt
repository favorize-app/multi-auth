package app.multiauth.oauth

import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Result of OAuth authentication operations.
 * Can represent success, failure, or specific error conditions.
 */
@Serializable
sealed class OAuthResult {
    
    /**
     * Successful OAuth authentication with tokens and user info.
     */
    @Serializable
    data class Success(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long?,
        val tokenType: String = "Bearer",
        val scope: String? = null,
        val userInfo: OAuthUserInfo? = null,
        val timestamp: @kotlinx.serialization.Contextual Instant = Clock.System.now
    ) : OAuthResult()
    
    /**
     * OAuth authentication failed with a specific error.
     */
    @Serializable
    data class Failure(
        val error: OAuthError,
        val timestamp: @kotlinx.serialization.Contextual Instant = Clock.System.now
    ) : OAuthResult()
    
    /**
     * OAuth flow was cancelled by the user.
     */
    @Serializable
    data class Cancelled(
        val reason: String? = null,
        val timestamp: @kotlinx.serialization.Contextual Instant = Clock.System.now
    ) : OAuthResult()
    
    /**
     * OAuth flow is in progress.
     */
    @Serializable
    data class InProgress(
        val step: String,
        val timestamp: @kotlinx.serialization.Contextual Instant = Clock.System.now
    ) : OAuthResult()
    
    /**
     * OAuth flow requires additional action (e.g., 2FA, consent).
     */
    @Serializable
    data class RequiresAction(
        val actionType: String,
        val actionData: Map<String, String> = emptyMap(),
        val timestamp: @kotlinx.serialization.Contextual Instant = Clock.System.now
    ) : OAuthResult()
}

/**
 * OAuth error types and details.
 */
@Serializable
data class OAuthError(
    val type: OAuthErrorType,
    val message: String,
    val description: String? = null,
    val errorCode: String? = null,
    val errorUri: String? = null,
    val state: String? = null
) {
    
    companion object {
        /**
         * Creates an OAuth error from standard OAuth 2.0 error response.
         */
        fun fromOAuthResponse(
            error: String,
            errorDescription: String? = null,
            errorUri: String? = null,
            state: String? = null
        ): OAuthError {
            val errorType = when (error.lowercase()) {
                "invalid_request" -> OAuthErrorType.INVALID_REQUEST
                "unauthorized_client" -> OAuthErrorType.UNAUTHORIZED_CLIENT
                "access_denied" -> OAuthErrorType.ACCESS_DENIED
                "unsupported_response_type" -> OAuthErrorType.UNSUPPORTED_RESPONSE_TYPE
                "invalid_scope" -> OAuthErrorType.INVALID_SCOPE
                "server_error" -> OAuthErrorType.SERVER_ERROR
                "temporarily_unavailable" -> OAuthErrorType.TEMPORARILY_UNAVAILABLE
                "invalid_client" -> OAuthErrorType.INVALID_CLIENT
                "invalid_grant" -> OAuthErrorType.INVALID_GRANT
                "unsupported_grant_type" -> OAuthErrorType.UNSUPPORTED_GRANT_TYPE
                else -> OAuthErrorType.UNKNOWN_ERROR
            }
            
            return OAuthError(
                type = errorType,
                message = error,
                description = errorDescription,
                errorUri = errorUri,
                state = state
            )
        }
        
        /**
         * Creates a network-related OAuth error.
         */
        fun networkError(message: String, cause: Throwable? = null): OAuthError {
            return OAuthError(
                type = OAuthErrorType.NETWORK_ERROR,
                message = message,
                description = cause?.message
            )
        }
        
        /**
         * Creates a configuration-related OAuth error.
         */
        fun configurationError(message: String): OAuthError {
            return OAuthError(
                type = OAuthErrorType.CONFIGURATION_ERROR,
                message = message
            )
        }
    }
}

/**
 * Types of OAuth errors that can occur.
 */
@Serializable
enum class OAuthErrorType {
    // Standard OAuth 2.0 errors
    INVALID_REQUEST,
    UNAUTHORIZED_CLIENT,
    ACCESS_DENIED,
    UNSUPPORTED_RESPONSE_TYPE,
    INVALID_SCOPE,
    SERVER_ERROR,
    TEMPORARILY_UNAVAILABLE,
    INVALID_CLIENT,
    INVALID_GRANT,
    UNSUPPORTED_GRANT_TYPE,
    
    // Custom errors
    NETWORK_ERROR,
    CONFIGURATION_ERROR,
    USER_CANCELLED,
    TIMEOUT,
    UNKNOWN_ERROR
}
