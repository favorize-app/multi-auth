package app.multiauth.models

import kotlinx.serialization.Serializable

/**
 * Represents an OAuth account linked to a user.
 * Stores information about external OAuth providers like Google, GitHub, etc.
 */
@Serializable
data class OAuthAccount(
    /**
     * Unique identifier for this OAuth account record
     */
    val id: String,

    /**
     * ID of the user this OAuth account belongs to
     */
    val userId: String,

    /**
     * OAuth provider identifier (e.g., "google", "github", "microsoft")
     */
    val providerId: String,

    /**
     * User ID from the external OAuth provider
     */
    val externalUserId: String,

    /**
     * User's email address from the OAuth provider
     */
    val email: String,

    /**
     * Display name from the OAuth provider
     */
    val displayName: String?,

    /**
     * Avatar/profile picture URL from the OAuth provider
     */
    val avatarUrl: String?,

    /**
     * Access token (encrypted when stored)
     */
    val accessToken: String?,

    /**
     * Refresh token (encrypted when stored)
     */
    val refreshToken: String?,

    /**
     * Token expiration time in milliseconds since epoch
     */
    val tokenExpiresAt: Long?,

    /**
     * Scopes granted by the OAuth provider
     */
    val scopes: List<String> = emptyList(),

    /**
     * Raw profile data from the OAuth provider (as JSON string)
     */
    val rawProfileData: String?,

    /**
     * When this OAuth account was first linked
     */
    val createdAt: Instant,

    /**
     * When this OAuth account was last updated
     */
    val updatedAt: Instant,

    /**
     * When the tokens were last refreshed
     */
    val lastTokenRefresh: Instant?
) {
    /**
     * Checks if the access token is expired
     */
    fun isTokenExpired(): Boolean {
        return tokenExpiresAt?.let {
            Clock.System.now().epochSeconds() > it
        } ?: false
    }

    /**
     * Checks if this OAuth account has a valid access token
     */
    fun hasValidToken(): Boolean {
        return accessToken != null && !isTokenExpired()
    }

    /**
     * Gets a safe copy with sensitive data removed (for logging/debugging)
     */
    fun toSafeString(): String {
        return "OAuthAccount(id=$id, userId=$userId, providerId=$providerId, externalUserId=$externalUserId, email=$email, displayName=$displayName, hasAccessToken=${accessToken != null}, hasRefreshToken=${refreshToken != null}, isTokenExpired=${isTokenExpired()})"
    }
}

/**
 * OAuth provider information
 */
@Serializable
data class OAuthProvider(
    val id: String,
    val name: String,
    val displayName: String,
    val iconUrl: String?,
    val authorizationUrl: String,
    val tokenUrl: String,
    val userInfoUrl: String,
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>,
    val isEnabled: Boolean = true
)

/**
 * OAuth authorization request parameters
 */
@Serializable
data class OAuthAuthorizationRequest(
    val providerId: String,
    val state: String,
    val codeVerifier: String?,
    val redirectUri: String,
    val scopes: List<String>,
    val createdAt: Instant
)

/**
 * OAuth token response from provider
 */
@Serializable
data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val expiresIn: Long?,
    val scope: String?
)
