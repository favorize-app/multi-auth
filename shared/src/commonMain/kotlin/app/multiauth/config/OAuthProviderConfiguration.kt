package app.multiauth.config

import kotlinx.serialization.Serializable

/**
 * Configuration for a specific OAuth provider.
 * This class represents the configuration that will be generated from Gradle build scripts.
 */
@Serializable
data class OAuthProviderConfiguration(
    val providerId: String,
    val clientId: String,
    val clientSecret: String? = null,
    val redirectUri: String,
    val scopes: List<String> = emptyList(),
    val customAuthUrl: String? = null,
    val customTokenUrl: String? = null,
    val customUserInfoUrl: String? = null,
    val customRevokeUrl: String? = null,
    val usePKCE: Boolean = true,
    val additionalParams: Map<String, String> = emptyMap(),
    val isEnabled: Boolean = true
) {
    
    /**
     * Converts this configuration to the runtime OAuthConfig format.
     */
    fun toOAuthConfig(): app.multiauth.oauth.OAuthConfig {
        return app.multiauth.oauth.OAuthConfig.forProvider(
            provider = providerId,
            clientId = clientId,
            clientSecret = clientSecret ?: "",
            redirectUri = redirectUri,
            scopes = scopes,
            additionalParams = additionalParams
        ).copy(
            pkceEnabled = usePKCE,
            authUrl = customAuthUrl ?: getDefaultAuthUrl(),
            tokenUrl = customTokenUrl ?: getDefaultTokenUrl(),
            userInfoUrl = customUserInfoUrl ?: getDefaultUserInfoUrl(),
            revokeUrl = customRevokeUrl ?: getDefaultRevokeUrl()
        )
    }
    
    private fun getDefaultAuthUrl(): String {
        return when (providerId.lowercase()) {
            "google" -> "https://accounts.google.com/o/oauth2/v2/auth"
            "github" -> "https://github.com/login/oauth/authorize"
            "discord" -> "https://discord.com/api/oauth2/authorize"
            "microsoft" -> "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
            "linkedin" -> "https://www.linkedin.com/oauth/v2/authorization"
            "twitter" -> "https://twitter.com/i/oauth2/authorize"
            "twitch" -> "https://id.twitch.tv/oauth2/authorize"
            "reddit" -> "https://www.reddit.com/api/v1/authorize"
            "spotify" -> "https://accounts.spotify.com/authorize"
            "apple" -> "https://appleid.apple.com/auth/authorize"
            else -> throw IllegalArgumentException("Unknown OAuth provider: $providerId")
        }
    }
    
    private fun getDefaultTokenUrl(): String {
        return when (providerId.lowercase()) {
            "google" -> "https://oauth2.googleapis.com/token"
            "github" -> "https://github.com/login/oauth/access_token"
            "discord" -> "https://discord.com/api/oauth2/token"
            "microsoft" -> "https://login.microsoftonline.com/common/oauth2/v2.0/token"
            "linkedin" -> "https://www.linkedin.com/oauth/v2/accessToken"
            "twitter" -> "https://api.twitter.com/2/oauth2/token"
            "twitch" -> "https://id.twitch.tv/oauth2/token"
            "reddit" -> "https://www.reddit.com/api/v1/access_token"
            "spotify" -> "https://accounts.spotify.com/api/token"
            "apple" -> "https://appleid.apple.com/auth/token"
            else -> throw IllegalArgumentException("Unknown OAuth provider: $providerId")
        }
    }
    
    private fun getDefaultUserInfoUrl(): String {
        return when (providerId.lowercase()) {
            "google" -> "https://www.googleapis.com/oauth2/v2/userinfo"
            "github" -> "https://api.github.com/user"
            "discord" -> "https://discord.com/api/users/@me"
            "microsoft" -> "https://graph.microsoft.com/v1.0/me"
            "linkedin" -> "https://api.linkedin.com/v2/me"
            "twitter" -> "https://api.twitter.com/2/users/me"
            "twitch" -> "https://api.twitch.tv/helix/users"
            "reddit" -> "https://oauth.reddit.com/api/v1/me"
            "spotify" -> "https://api.spotify.com/v1/me"
            "apple" -> "https://appleid.apple.com/auth/userinfo"
            else -> throw IllegalArgumentException("Unknown OAuth provider: $providerId")
        }
    }
    
    private fun getDefaultRevokeUrl(): String? {
        return when (providerId.lowercase()) {
            "google" -> "https://oauth2.googleapis.com/revoke"
            "github" -> null
            "discord" -> null
            "microsoft" -> null
            "linkedin" -> null
            "twitter" -> null
            "twitch" -> null
            "reddit" -> null
            "spotify" -> "https://accounts.spotify.com/api/token/revoke"
            "apple" -> null
            else -> null
        }
    }
}

/**
 * Collection of OAuth provider configurations.
 */
@Serializable
data class OAuthConfiguration(
    val providers: Map<String, OAuthProviderConfiguration> = emptyMap()
) {
    
    /**
     * Gets a specific provider configuration by ID.
     */
    fun getProvider(providerId: String): OAuthProviderConfiguration? {
        return providers[providerId.lowercase()]
    }
    
    /**
     * Gets all enabled providers.
     */
    fun getEnabledProviders(): List<OAuthProviderConfiguration> {
        return providers.values.filter { it.isEnabled }
    }
    
    /**
     * Checks if a provider is configured and enabled.
     */
    fun isProviderEnabled(providerId: String): Boolean {
        return providers[providerId.lowercase()]?.isEnabled == true
    }
    
    /**
     * Gets the list of configured provider IDs.
     */
    fun getConfiguredProviders(): List<String> {
        return providers.keys.toList()
    }
}
