package app.multiauth.providers

import app.multiauth.models.*
import app.multiauth.models.OIDCProvider as OIDCProviderType

/**
 * Interface for OpenID Connect authentication providers.
 * This allows for pluggable OIDC services (Google, Microsoft, Auth0, etc.).
 * 
 * OIDC extends OAuth 2.0 with standardized identity layer and JWT-based ID tokens.
 */
interface OIDCProvider {
    
    /**
     * Gets the OIDC configuration for this provider.
     * @return OIDC configuration containing endpoints and capabilities
     */
    suspend fun getConfiguration(): OIDCConfiguration
    
    /**
     * Initiates the OIDC authentication flow.
     * @param config Provider configuration
     * @param redirectUri The redirect URI for the OIDC flow
     * @param scopes The requested OIDC scopes (should include "openid")
     * @param state Optional state parameter for security
     * @param nonce Optional nonce for replay protection
     * @param prompt Optional prompt parameter
     * @return Result containing the OIDC flow information
     */
    suspend fun initiateAuth(
        config: OIDCProviderConfig,
        redirectUri: String,
        scopes: List<String> = listOf("openid", "profile", "email"),
        state: String? = null,
        nonce: String? = null,
        prompt: String? = null
    ): AuthResult<OIDCAuthRequest>
    
    /**
     * Completes the OIDC authentication flow using the authorization code.
     * @param config Provider configuration
     * @param authorizationCode The authorization code from the OIDC callback
     * @param redirectUri The redirect URI used in the flow
     * @param codeVerifier The PKCE code verifier (if using PKCE)
     * @param nonce The nonce used in the request (for ID token validation)
     * @return Result containing the OIDC tokens and user information
     */
    suspend fun completeAuth(
        config: OIDCProviderConfig,
        authorizationCode: String,
        redirectUri: String,
        codeVerifier: String? = null,
        nonce: String? = null
    ): AuthResult<OIDCAuthResult>
    
    /**
     * Refreshes an OIDC access token using the refresh token.
     * @param config Provider configuration
     * @param refreshToken The refresh token to use
     * @return Result containing the new tokens
     */
    suspend fun refreshToken(
        config: OIDCProviderConfig,
        refreshToken: String
    ): AuthResult<OIDCTokenResponse>
    
    /**
     * Revokes an OIDC access token.
     * @param config Provider configuration
     * @param accessToken The access token to revoke
     * @return Result indicating success or failure
     */
    suspend fun revokeToken(
        config: OIDCProviderConfig,
        accessToken: String
    ): AuthResult<Unit>
    
    /**
     * Gets user profile information from the OIDC provider.
     * @param config Provider configuration
     * @param accessToken The access token to use
     * @return Result containing the user profile
     */
    suspend fun getUserInfo(
        config: OIDCProviderConfig,
        accessToken: String
    ): AuthResult<OIDCUserInfo>
    
    /**
     * Validates an OIDC ID token.
     * @param config Provider configuration
     * @param idToken The ID token to validate
     * @param nonce The nonce used in the request (if applicable)
     * @param clientId The client ID to validate against
     * @return Result containing the decoded and validated ID token
     */
    suspend fun validateIdToken(
        config: OIDCProviderConfig,
        idToken: String,
        nonce: String? = null,
        clientId: String? = null
    ): AuthResult<OIDCIDToken>
    
    /**
     * Ends the OIDC session (logout).
     * @param config Provider configuration
     * @param idToken The ID token to use for logout
     * @param postLogoutRedirectUri Optional redirect URI after logout
     * @return Result indicating success or failure
     */
    suspend fun endSession(
        config: OIDCProviderConfig,
        idToken: String,
        postLogoutRedirectUri: String? = null
    ): AuthResult<Unit>
    
    /**
     * Gets the provider's configuration and capabilities.
     * @return Provider information
     */
    fun getProviderInfo(): OIDCProviderInfo
    
    /**
     * Checks if the provider supports a specific feature.
     * @param feature The feature to check
     * @return True if the feature is supported
     */
    fun supportsFeature(feature: OIDCFeature): Boolean
}

/**
 * Information about an OIDC provider.
 */
data class OIDCProviderInfo(
    val name: String,
    val description: String,
    val supportedFeatures: List<OIDCFeature>,
    val defaultScopes: List<String>,
    val supportedScopes: List<String>,
    val issuer: String,
    val documentationUrl: String? = null
)

/**
 * OIDC features that a provider may support.
 */
enum class OIDCFeature {
    PKCE, // Proof Key for Code Exchange
    NONCE, // Nonce validation
    REFRESH_TOKENS, // Refresh token support
    REVOCATION, // Token revocation
    END_SESSION, // End session/logout
    USER_INFO, // User info endpoint
    CUSTOM_CLAIMS, // Custom claims support
    MULTI_TENANT, // Multi-tenant support
    DEVICE_AUTH, // Device authorization flow
    INTROSPECTION // Token introspection
}