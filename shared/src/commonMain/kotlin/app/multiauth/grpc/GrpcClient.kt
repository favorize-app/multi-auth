package app.multiauth.grpc

import app.multiauth.models.User
import app.multiauth.util.Logger
import kotlinx.coroutines.flow.Flow

/**
 * Interface for gRPC client operations.
 * Handles communication with backend authentication services.
 */
interface GrpcClient {
    
    /**
     * Connects to the gRPC server.
     * 
     * @param host The server host address
     * @param port The server port
     * @param useTls Whether to use TLS encryption
     * @return Result indicating success or failure
     */
    suspend fun connect(host: String, port: Int, useTls: Boolean = true): Result<Unit>
    
    /**
     * Disconnects from the gRPC server.
     * 
     * @return Result indicating success or failure
     */
    suspend fun disconnect(): Result<Unit>
    
    /**
     * Checks if the client is connected to the server.
     * 
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean
    
    /**
     * Gets the current connection status.
     * 
     * @return Flow of connection status updates
     */
    fun getConnectionStatus(): Flow<ConnectionStatus>
    
    /**
     * Sets authentication credentials for the client.
     * 
     * @param accessToken The access token for authentication
     * @param refreshToken Optional refresh token
     * @return Result indicating success or failure
     */
    suspend fun setCredentials(accessToken: String, refreshToken: String? = null): Result<Unit>
    
    /**
     * Clears authentication credentials.
     * 
     * @return Result indicating success or failure
     */
    suspend fun clearCredentials(): Result<Unit>
    
    /**
     * Gets the authentication service client.
     * 
     * @return AuthenticationServiceClient instance
     */
    fun getAuthenticationService(): AuthenticationServiceClient
    
    /**
     * Gets the user management service client.
     * 
     * @return UserManagementServiceClient instance
     */
    fun getUserManagementService(): UserManagementServiceClient
    
    /**
     * Gets the token validation service client.
     * 
     * @return TokenValidationServiceClient instance
     */
    fun getTokenValidationService(): TokenValidationServiceClient
}

/**
 * Base implementation of GrpcClient with common functionality.
 * Platform-specific implementations should extend this class.
 */
abstract class BaseGrpcClient : GrpcClient {
    
    protected val logger = Logger.getLogger(this::class)
    protected var isConnectedToServer = false
    protected var currentAccessToken: String? = null
    protected var currentRefreshToken: String? = null
    
    override fun isConnected(): Boolean = isConnectedToServer
    
    override suspend fun setCredentials(accessToken: String, refreshToken: String?): Result<Unit> {
        return try {
            currentAccessToken = accessToken
            currentRefreshToken = refreshToken
            logger.info("grpc", "Credentials set successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to set credentials", e)
            Result.failure(e)
        }
    }
    
    override suspend fun clearCredentials(): Result<Unit> {
        return try {
            currentAccessToken = null
            currentRefreshToken = null
            logger.info("grpc", "Credentials cleared successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to clear credentials", e)
            Result.failure(e)
        }
    }
    
    protected fun validateConnection(): Boolean {
        if (!isConnectedToServer) {
            logger.warn("grpc", "gRPC client not connected to server")
            return false
        }
        return true
    }
    
    protected fun validateCredentials(): Boolean {
        if (currentAccessToken.isNullOrBlank()) {
            logger.warn("No access token available")
            return false
        }
        return true
    }
}

/**
 * Represents the connection status of the gRPC client.
 */
sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
    data class Error(val error: Throwable) : ConnectionStatus()
}

/**
 * Interface for authentication service operations.
 */
interface AuthenticationServiceClient {
    
    /**
     * Signs in a user with email and password.
     * 
     * @param email The user's email address
     * @param password The user's password
     * @return Result with authentication response
     */
    suspend fun signInWithEmail(email: String, password: String): Result<AuthenticationResponse>
    
    /**
     * Signs in a user with phone number and SMS code.
     * 
     * @param phoneNumber The user's phone number
     * @param smsCode The SMS verification code
     * @return Result with authentication response
     */
    suspend fun signInWithPhone(phoneNumber: String, smsCode: String): Result<AuthenticationResponse>
    
    /**
     * Signs in a user with OAuth provider.
     * 
     * @param provider The OAuth provider name
     * @param oauthToken The OAuth token from the provider
     * @return Result with authentication response
     */
    suspend fun signInWithOAuth(provider: String, oauthToken: String): Result<AuthenticationResponse>
    
    /**
     * Registers a new user with email and password.
     * 
     * @param email The user's email address
     * @param password The user's password
     * @param displayName The user's display name
     * @return Result with authentication response
     */
    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<AuthenticationResponse>
    
    /**
     * Sends a password reset email.
     * 
     * @param email The user's email address
     * @return Result indicating success or failure
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    
    /**
     * Resets password with reset token.
     * 
     * @param resetToken The password reset token
     * @param newPassword The new password
     * @return Result indicating success or failure
     */
    suspend fun resetPassword(resetToken: String, newPassword: String): Result<Unit>
    
    /**
     * Signs out the current user.
     * 
     * @return Result indicating success or failure
     */
    suspend fun signOut(): Result<Unit>
}

/**
 * Interface for user management service operations.
 */
interface UserManagementServiceClient {
    
    /**
     * Gets the current user's profile.
     * 
     * @return Result with user profile
     */
    suspend fun getCurrentUser(): Result<User>
    
    /**
     * Updates the current user's profile.
     * 
     * @param updates The profile updates to apply
     * @return Result with updated user profile
     */
    suspend fun updateProfile(updates: UserProfileUpdates): Result<User>
    
    /**
     * Changes the user's password.
     * 
     * @param currentPassword The current password
     * @param newPassword The new password
     * @return Result indicating success or failure
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    
    /**
     * Deletes the current user's account.
     * 
     * @param password The user's password for confirmation
     * @return Result indicating success or failure
     */
    suspend fun deleteAccount(password: String): Result<Unit>
    
    /**
     * Links an OAuth provider to the current user's account.
     * 
     * @param provider The OAuth provider name
     * @param oauthToken The OAuth token from the provider
     * @return Result indicating success or failure
     */
    suspend fun linkOAuthProvider(provider: String, oauthToken: String): Result<Unit>
    
    /**
     * Unlinks an OAuth provider from the current user's account.
     * 
     * @param provider The OAuth provider name
     * @return Result indicating success or failure
     */
    suspend fun unlinkOAuthProvider(provider: String): Result<Unit>
}

/**
 * Interface for token validation service operations.
 */
interface TokenValidationServiceClient {
    
    /**
     * Validates an access token.
     * 
     * @param token The access token to validate
     * @return Result with token validation response
     */
    suspend fun validateToken(token: String): Result<TokenValidationResponse>
    
    /**
     * Refreshes an access token using a refresh token.
     * 
     * @param refreshToken The refresh token to use
     * @return Result with new access token
     */
    suspend fun refreshToken(refreshToken: String): Result<TokenRefreshResponse>
    
    /**
     * Revokes an access token.
     * 
     * @param token The access token to revoke
     * @return Result indicating success or failure
     */
    suspend fun revokeToken(token: String): Result<Unit>
    
    /**
     * Gets token information (expiration, scopes, etc.).
     * 
     * @param token The access token to inspect
     * @return Result with token information
     */
    suspend fun getTokenInfo(token: String): Result<TokenInfo>
}

/**
 * Response from authentication operations.
 */
data class AuthenticationResponse(
    val user: User,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

/**
 * Response from token validation operations.
 */
data class TokenValidationResponse(
    val isValid: Boolean,
    val userId: String?,
    val scopes: List<String>,
    val expiresAt: Long?,
    val errorMessage: String? = null
)

/**
 * Response from token refresh operations.
 */
data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

/**
 * Information about a token.
 */
data class TokenInfo(
    val userId: String,
    val scopes: List<String>,
    val issuedAt: Long,
    val expiresAt: Long,
    val issuer: String,
    val audience: List<String>
)

/**
 * Updates to apply to a user's profile.
 */
data class UserProfileUpdates(
    val displayName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val profilePictureUrl: String? = null
)