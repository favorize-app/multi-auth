package app.multiauth.grpc

import app.multiauth.models.User
import app.multiauth.util.Logger

/**
 * Service definitions for gRPC authentication services.
 * These define the contracts that the backend must implement.
 */

/**
 * Authentication service definition.
 * Handles user authentication operations.
 */
object AuthenticationService {
    
    const val SERVICE_NAME = "auth.AuthenticationService"
    
    /**
     * Request for email/password sign-in.
     */
    data class SignInWithEmailRequest(
        val email: String,
        val password: String
    )
    
    /**
     * Request for phone/SMS sign-in.
     */
    data class SignInWithPhoneRequest(
        val phoneNumber: String,
        val smsCode: String
    )
    
    /**
     * Request for OAuth sign-in.
     */
    data class SignInWithOAuthRequest(
        val provider: String,
        val oauthToken: String,
        val redirectUri: String? = null
    )
    
    /**
     * Request for user registration.
     */
    data class RegisterRequest(
        val email: String,
        val password: String,
        val displayName: String,
        val phoneNumber: String? = null
    )
    
    /**
     * Request for password reset.
     */
    data class PasswordResetRequest(
        val email: String
    )
    
    /**
     * Request for password reset confirmation.
     */
    data class PasswordResetConfirmRequest(
        val resetToken: String,
        val newPassword: String
    )
    
    /**
     * Response for authentication operations.
     */
    data class AuthenticationResponse(
        val success: Boolean,
        val user: User? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresIn: Long = 0,
        val tokenType: String = "Bearer",
        val errorMessage: String? = null,
        val errorCode: String? = null
    )
    
    /**
     * Response for password reset operations.
     */
    data class PasswordResetResponse(
        val success: Boolean,
        val message: String? = null,
        val errorMessage: String? = null,
        val errorCode: String? = null
    )
}

/**
 * User management service definition.
 * Handles user profile and account management operations.
 */
object UserManagementService {
    
    const val SERVICE_NAME = "auth.UserManagementService"
    
    /**
     * Request for profile updates.
     */
    data class UpdateProfileRequest(
        val userId: String,
        val displayName: String? = null,
        val email: String? = null,
        val phoneNumber: String? = null,
        val profilePictureUrl: String? = null
    )
    
    /**
     * Request for password change.
     */
    data class ChangePasswordRequest(
        val userId: String,
        val currentPassword: String,
        val newPassword: String
    )
    
    /**
     * Request for account deletion.
     */
    data class DeleteAccountRequest(
        val userId: String,
        val password: String
    )
    
    /**
     * Request for OAuth provider linking.
     */
    data class LinkOAuthProviderRequest(
        val userId: String,
        val provider: String,
        val oauthToken: String
    )
    
    /**
     * Request for OAuth provider unlinking.
     */
    data class UnlinkOAuthProviderRequest(
        val userId: String,
        val provider: String
    )
    
    /**
     * Response for user management operations.
     */
    data class UserManagementResponse(
        val success: Boolean,
        val user: User? = null,
        val message: String? = null,
        val errorMessage: String? = null,
        val errorCode: String? = null
    )
    
    /**
     * Response for OAuth provider operations.
     */
    data class OAuthProviderResponse(
        val success: Boolean,
        val linkedProviders: List<String> = emptyList(),
        val message: String? = null,
        val errorMessage: String? = null,
        val errorCode: String? = null
    )
}

/**
 * Token validation service definition.
 * Handles token validation and refresh operations.
 */
object TokenValidationService {
    
    const val SERVICE_NAME = "auth.TokenValidationService"
    
    /**
     * Request for token validation.
     */
    data class ValidateTokenRequest(
        val token: String,
        val requiredScopes: List<String> = emptyList()
    )
    
    /**
     * Request for token refresh.
     */
    data class RefreshTokenRequest(
        val refreshToken: String
    )
    
    /**
     * Request for token revocation.
     */
    data class RevokeTokenRequest(
        val token: String,
        val tokenType: String = "access_token" // "access_token" or "refresh_token"
    )
    
    /**
     * Request for token information.
     */
    data class TokenInfoRequest(
        val token: String
    )
    
    /**
     * Response for token validation.
     */
    data class TokenValidationResponse(
        val isValid: Boolean,
        val userId: String? = null,
        val scopes: List<String> = emptyList(),
        val issuedAt: Long = 0,
        val expiresAt: Long = 0,
        val issuer: String? = null,
        val audience: List<String> = emptyList(),
        val errorMessage: String? = null,
        val errorCode: String? = null
    )
    
    /**
     * Response for token refresh.
     */
    data class TokenRefreshResponse(
        val success: Boolean,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresIn: Long = 0,
        val tokenType: String = "Bearer",
        val errorMessage: String? = null,
        val errorCode: String? = null
    )
    
    /**
     * Response for token operations.
     */
    data class TokenOperationResponse(
        val success: Boolean,
        val message: String? = null,
        val errorMessage: String? = null,
        val errorCode: String? = null
    )
}

/**
 * Error codes for gRPC service operations.
 */
object GrpcErrorCodes {
    
    // Authentication errors
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val USER_NOT_FOUND = "USER_NOT_FOUND"
    const val ACCOUNT_DISABLED = "ACCOUNT_DISABLED"
    const val EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED"
    const val PHONE_NOT_VERIFIED = "PHONE_NOT_VERIFIED"
    const val OAUTH_PROVIDER_ERROR = "OAUTH_PROVIDER_ERROR"
    const val INVALID_OAUTH_TOKEN = "INVALID_OAUTH_TOKEN"
    
    // Registration errors
    const val EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS"
    const val PHONE_ALREADY_EXISTS = "PHONE_ALREADY_EXISTS"
    const val WEAK_PASSWORD = "WEAK_PASSWORD"
    const val INVALID_EMAIL_FORMAT = "INVALID_EMAIL_FORMAT"
    const val INVALID_PHONE_FORMAT = "INVALID_PHONE_FORMAT"
    
    // Token errors
    const val INVALID_TOKEN = "INVALID_TOKEN"
    const val EXPIRED_TOKEN = "EXPIRED_TOKEN"
    const val INSUFFICIENT_SCOPES = "INSUFFICIENT_SCOPES"
    const val TOKEN_REVOKED = "TOKEN_REVOKED"
    
    // User management errors
    const val USER_NOT_AUTHORIZED = "USER_NOT_AUTHORIZED"
    const val INVALID_PROFILE_DATA = "INVALID_PROFILE_DATA"
    const val OAUTH_PROVIDER_ALREADY_LINKED = "OAUTH_PROVIDER_ALREADY_LINKED"
    const val OAUTH_PROVIDER_NOT_LINKED = "OAUTH_PROVIDER_NOT_LINKED"
    
    // System errors
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val TIMEOUT_ERROR = "TIMEOUT_ERROR"
}

/**
 * Utility functions for gRPC service operations.
 */
object GrpcServiceUtils {
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Creates a successful authentication response.
     */
    fun createSuccessAuthResponse(
        user: User,
        accessToken: String,
        refreshToken: String,
        expiresIn: Long
    ): AuthenticationService.AuthenticationResponse {
        return AuthenticationService.AuthenticationResponse(
            success = true,
            user = user,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn
        )
    }
    
    /**
     * Creates an error authentication response.
     */
    fun createErrorAuthResponse(
        errorMessage: String,
        errorCode: String
    ): AuthenticationService.AuthenticationResponse {
        logger.error("Authentication error: $errorCode - $errorMessage")
        return AuthenticationService.AuthenticationResponse(
            success = false,
            errorMessage = errorMessage,
            errorCode = errorCode
        )
    }
    
    /**
     * Creates a successful user management response.
     */
    fun createSuccessUserManagementResponse(
        user: User,
        message: String? = null
    ): UserManagementService.UserManagementResponse {
        return UserManagementService.UserManagementResponse(
            success = true,
            user = user,
            message = message
        )
    }
    
    /**
     * Creates an error user management response.
     */
    fun createErrorUserManagementResponse(
        errorMessage: String,
        errorCode: String
    ): UserManagementService.UserManagementResponse {
        logger.error("User management error: $errorCode - $errorMessage")
        return UserManagementService.UserManagementResponse(
            success = false,
            errorMessage = errorMessage,
            errorCode = errorCode
        )
    }
    
    /**
     * Creates a successful token validation response.
     */
    fun createSuccessTokenValidationResponse(
        userId: String,
        scopes: List<String>,
        issuedAt: Long,
        expiresAt: Long,
        issuer: String,
        audience: List<String>
    ): TokenValidationService.TokenValidationResponse {
        return TokenValidationService.TokenValidationResponse(
            isValid = true,
            userId = userId,
            scopes = scopes,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            issuer = issuer,
            audience = audience
        )
    }
    
    /**
     * Creates an error token validation response.
     */
    fun createErrorTokenValidationResponse(
        errorMessage: String,
        errorCode: String
    ): TokenValidationService.TokenValidationResponse {
        logger.error("Token validation error: $errorCode - $errorMessage")
        return TokenValidationService.TokenValidationResponse(
            isValid = false,
            errorMessage = errorMessage,
            errorCode = errorCode
        )
    }
}