package app.multiauth.providers

import app.multiauth.models.AuthResult
import app.multiauth.oauth.OAuthProvider as OAuthProviderType

/**
 * Interface for OAuth-based authentication providers.
 * This allows for pluggable OAuth services (Google, Apple, Facebook, etc.).
 */
interface OAuthProvider {
    
    /**
     * Initiates the OAuth authentication flow.
     * @param provider The OAuth provider to use
     * @param redirectUri The redirect URI for the OAuth flow
     * @param scopes The requested OAuth scopes
     * @param state Optional state parameter for security
     * @return Result containing the OAuth flow information
     */
    suspend fun initiateAuth(
        provider: OAuthProviderType,
        redirectUri: String,
        scopes: List<String> = emptyList(),
        state: String? = null
    ): AuthResult<OAuthFlowInfo>
    
    /**
     * Completes the OAuth authentication flow using the authorization code.
     * @param provider The OAuth provider used
     * @param authorizationCode The authorization code from the OAuth callback
     * @param redirectUri The redirect URI used in the flow
     * @param codeVerifier The PKCE code verifier (if using PKCE)
     * @return Result containing the OAuth tokens and user information
     */
    suspend fun completeAuth(
        provider: OAuthProviderType,
        authorizationCode: String,
        redirectUri: String,
        codeVerifier: String? = null
    ): AuthResult<OAuthResult>
    
    /**
     * Refreshes an OAuth access token using the refresh token.
     * @param provider The OAuth provider
     * @param refreshToken The refresh token to use
     * @return Result containing the new tokens
     */
    suspend fun refreshToken(
        provider: OAuthProviderType,
        refreshToken: String
    ): AuthResult<OAuthTokens>
    
    /**
     * Revokes an OAuth access token.
     * @param provider The OAuth provider
     * @param accessToken The access token to revoke
     * @return Result indicating success or failure
     */
    suspend fun revokeToken(
        provider: OAuthProviderType,
        accessToken: String
    ): AuthResult<Unit>
    
    /**
     * Gets user profile information from the OAuth provider.
     * @param provider The OAuth provider
     * @param accessToken The access token to use
     * @return Result containing the user profile
     */
    suspend fun getUserProfile(
        provider: OAuthProviderType,
        accessToken: String
    ): AuthResult<OAuthUserProfile>
    
    /**
     * Links an existing account with an OAuth provider.
     * @param provider The OAuth provider to link
     * @param accessToken The OAuth access token
     * @param userId The existing user ID to link
     * @return Result indicating success or failure
     */
    suspend fun linkAccount(
        provider: OAuthProviderType,
        accessToken: String,
        userId: String
    ): AuthResult<Unit>
    
    /**
     * Unlinks an account from an OAuth provider.
     * @param provider The OAuth provider to unlink
     * @param userId The user ID to unlink
     * @return Result indicating success or failure
     */
    suspend fun unlinkAccount(
        provider: OAuthProviderType,
        userId: String
    ): AuthResult<Unit>
    
    /**
     * Gets the provider's configuration and capabilities.
     * @return Provider configuration information
     */
    fun getProviderInfo(): OAuthProviderInfo
    
    /**
     * Validates an OAuth token.
     * @param provider The OAuth provider
     * @param accessToken The access token to validate
     * @return Result indicating if the token is valid
     */
    suspend fun validateToken(
        provider: OAuthProviderType,
        accessToken: String
    ): AuthResult<Boolean>
}

/**
 * Information about an OAuth flow.
 */
data class OAuthFlowInfo(
    val authorizationUrl: String,
    val state: String,
    val codeVerifier: String? = null,
    val expiresAt: Long
)

/**
 * Result of an OAuth authentication.
 */
data class OAuthResult(
    val tokens: OAuthTokens,
    val userProfile: OAuthUserProfile,
    val provider: OAuthProviderType
)

/**
 * OAuth tokens (access token, refresh token, etc.).
 */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long,
    val tokenType: String = "Bearer",
    val scope: String? = null
)

/**
 * User profile information from an OAuth provider.
 */
data class OAuthUserProfile(
    val providerUserId: String,
    val email: String? = null,
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val photoUrl: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val emailVerified: Boolean = false,
    val provider: OAuthProviderType,
    val rawData: Map<String, Any> = emptyMap()
)

/**
 * Information about an OAuth provider's capabilities and configuration.
 */
data class OAuthProviderInfo(
    val name: String,
    val version: String,
    val supportsPKCE: Boolean = true,
    val supportsRefreshTokens: Boolean = true,
    val supportsTokenRevocation: Boolean = true,
    val supportsAccountLinking: Boolean = true,
    val supportedScopes: List<String> = emptyList(),
    val features: List<String> = emptyList()
)

/**
 * Configuration for OAuth providers.
 */
data class OAuthProviderConfig(
    val clientId: String,
    val clientSecret: String? = null,
    val redirectUris: List<String> = emptyList(),
    val scopes: List<String> = emptyList(),
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String? = null,
    val revocationEndpoint: String? = null,
    val usePKCE: Boolean = true,
    val customHeaders: Map<String, String> = emptyMap()
)

/**
 * OAuth scopes that can be requested.
 */
enum class OAuthScope(val value: String) {
    OPENID("openid"),
    PROFILE("profile"),
    EMAIL("email"),
    PHONE("phone"),
    ADDRESS("address"),
    OFFLINE_ACCESS("offline_access")
}

/**
 * OAuth flow types.
 */
enum class OAuthFlowType {
    AUTHORIZATION_CODE,
    IMPLICIT,
    CLIENT_CREDENTIALS,
    DEVICE_AUTHORIZATION
}