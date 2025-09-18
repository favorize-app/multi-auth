package app.multiauth.oauth

import app.multiauth.oauth.clients.*
import app.multiauth.oauth.clients.implemented.TwitchOAuthClient
import app.multiauth.oauth.clients.implemented.RedditOAuthClient
import app.multiauth.oauth.clients.implemented.SpotifyOAuthClient
import app.multiauth.oauth.clients.implemented.FacebookOAuthClient
import app.multiauth.oauth.clients.implemented.EpicGamesOAuthClient
import app.multiauth.oauth.clients.placeholders.SteamOAuthClient
import app.multiauth.oauth.clients.placeholders.AppleOAuthClient
import app.multiauth.util.Logger

/**
 * Factory for creating OAuth clients based on provider type.
 * Provides a centralized way to instantiate the appropriate OAuth client.
 */
object OAuthClientFactory {

    private val logger = Logger.getLogger(this::class)

    /**
     * Creates an OAuth client for the specified provider.
     *
     * @param provider The OAuth provider type
     * @param config The OAuth configuration
     * @param httpClient The HTTP client to use for requests
     * @return An OAuthClient implementation for the specified provider
     * @throws IllegalArgumentException if the provider is not supported
     */
    fun createClient(
        provider: OAuthProvider,
        config: OAuthConfig,
        httpClient: HttpClient
    ): OAuthClient {
        logger.info("oauth", "Creating OAuth client for provider: $provider")

        return when (provider) {
            OAuthProvider.GOOGLE -> GoogleOAuthClient(config, httpClient, logger)
            OAuthProvider.DISCORD -> DiscordOAuthClient(config, httpClient, logger)
            OAuthProvider.GITHUB -> GitHubOAuthClient(config, httpClient, logger)
            OAuthProvider.MICROSOFT -> MicrosoftOAuthClient(config, httpClient, logger)
            OAuthProvider.LINKEDIN -> LinkedInOAuthClient(config, httpClient, logger)
            OAuthProvider.TWITTER -> TwitterOAuthClient(config, httpClient, logger)
            OAuthProvider.TWITCH -> TwitchOAuthClient(config, httpClient, logger)
            OAuthProvider.REDDIT -> RedditOAuthClient(config, httpClient, logger)
            OAuthProvider.STEAM -> SteamOAuthClient(config, httpClient, logger)
            OAuthProvider.EPIC_GAMES -> EpicGamesOAuthClient(config, httpClient, logger)
            OAuthProvider.SPOTIFY -> SpotifyOAuthClient(config, httpClient, logger)
            OAuthProvider.FACEBOOK -> FacebookOAuthClient(config, httpClient, logger)
            OAuthProvider.APPLE -> AppleOAuthClient(config, httpClient, logger)
        }
    }

    /**
     * Creates an OAuth client for the specified provider by string identifier.
     *
     * @param providerId The OAuth provider identifier string
     * @param config The OAuth configuration
     * @param httpClient The HTTP client to use for requests
     * @return An OAuthClient implementation for the specified provider
     * @throws IllegalArgumentException if the provider is not supported
     */
    fun createClientById(
        providerId: String,
        config: OAuthConfig,
        httpClient: HttpClient
    ): OAuthClient {
        val provider = OAuthProvider.fromString(providerId)
            ?: throw IllegalArgumentException("Unsupported OAuth provider: $providerId")

        return createClient(provider, config, httpClient)
    }

    /**
     * Gets a list of all supported OAuth providers.
     *
     * @return List of supported OAuth providers
     */
    fun getSupportedProviders(): List<OAuthProvider> {
        return OAuthProvider.values().toList()
    }

    /**
     * Checks if a provider is supported.
     *
     * @param provider The OAuth provider to check
     * @return true if supported, false otherwise
     */
    fun isProviderSupported(provider: OAuthProvider): Boolean {
        return provider in OAuthProvider.values()
    }

    /**
     * Gets the default scopes for a specific provider.
     *
     * @param provider The OAuth provider
     * @return List of default scopes for the provider
     */
    fun getDefaultScopes(provider: OAuthProvider): List<String> {
        return when (provider) {
            OAuthProvider.GOOGLE -> listOf("openid", "email", "profile")
            OAuthProvider.DISCORD -> listOf("identify", "email")
            OAuthProvider.GITHUB -> listOf("read:user", "user:email")
            OAuthProvider.MICROSOFT -> listOf("openid", "email", "profile")
            OAuthProvider.LINKEDIN -> listOf("r_liteprofile", "r_emailaddress")
            OAuthProvider.TWITTER -> listOf("tweet.read", "users.read", "offline.access")
            OAuthProvider.TWITCH -> listOf("user:read:email")
            OAuthProvider.REDDIT -> listOf("identity", "read")
            OAuthProvider.STEAM -> listOf("openid")
            OAuthProvider.EPIC_GAMES -> listOf("basic_profile", "friends_list")
            OAuthProvider.SPOTIFY -> listOf("user-read-email", "user-read-private")
            OAuthProvider.FACEBOOK -> listOf("email", "public_profile")
            OAuthProvider.APPLE -> listOf("name", "email")
        }
    }

    /**
     * Gets the authorization URL template for a specific provider.
     *
     * @param provider The OAuth provider
     * @return The authorization URL template
     */
    fun getAuthorizationUrlTemplate(provider: OAuthProvider): String {
        return when (provider) {
            OAuthProvider.GOOGLE -> "https://accounts.google.com/o/oauth2/v2/auth"
            OAuthProvider.DISCORD -> "https://discord.com/api/oauth2/authorize"
            OAuthProvider.GITHUB -> "https://github.com/login/oauth/authorize"
            OAuthProvider.MICROSOFT -> "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
            OAuthProvider.LINKEDIN -> "https://www.linkedin.com/oauth/v2/authorization"
            OAuthProvider.TWITTER -> "https://twitter.com/i/oauth2/authorize"
            OAuthProvider.TWITCH -> "https://id.twitch.tv/oauth2/authorize"
            OAuthProvider.REDDIT -> "https://www.reddit.com/api/v1/authorize"
            OAuthProvider.STEAM -> "https://steamcommunity.com/openid/login"
            OAuthProvider.EPIC_GAMES -> "https://www.epicgames.com/id/authorize"
            OAuthProvider.SPOTIFY -> "https://accounts.spotify.com/authorize"
            OAuthProvider.FACEBOOK -> "https://www.facebook.com/v18.0/dialog/oauth"
            OAuthProvider.APPLE -> "https://appleid.apple.com/auth/authorize"
        }
    }

    /**
     * Gets the token URL for a specific provider.
     *
     * @param provider The OAuth provider
     * @return The token URL
     */
    fun getTokenUrl(provider: OAuthProvider): String {
        return when (provider) {
            OAuthProvider.GOOGLE -> "https://oauth2.googleapis.com/token"
            OAuthProvider.DISCORD -> "https://discord.com/api/oauth2/token"
            OAuthProvider.GITHUB -> "https://github.com/login/oauth/access_token"
            OAuthProvider.MICROSOFT -> "https://login.microsoftonline.com/common/oauth2/v2.0/token"
            OAuthProvider.LINKEDIN -> "https://www.linkedin.com/oauth/v2/accessToken"
            OAuthProvider.TWITTER -> "https://api.twitter.com/2/oauth2/token"
            OAuthProvider.TWITCH -> "https://id.twitch.tv/oauth2/token"
            OAuthProvider.REDDIT -> "https://www.reddit.com/api/v1/access_token"
            OAuthProvider.STEAM -> "https://api.steampowered.com/ISteamUserOAuth/GetTokenDetails/v1/"
            OAuthProvider.EPIC_GAMES -> "https://api.epicgames.dev/epic/oauth/v1/token"
            OAuthProvider.SPOTIFY -> "https://accounts.spotify.com/api/token"
            OAuthProvider.FACEBOOK -> "https://graph.facebook.com/v18.0/oauth/access_token"
            OAuthProvider.APPLE -> "https://appleid.apple.com/auth/token"
        }
    }

    /**
     * Gets the user info URL for a specific provider.
     *
     * @param provider The OAuth provider
     * @return The user info URL
     */
    fun getUserInfoUrl(provider: OAuthProvider): String {
        return when (provider) {
            OAuthProvider.GOOGLE -> "https://www.googleapis.com/oauth2/v2/userinfo"
            OAuthProvider.DISCORD -> "https://discord.com/api/users/@me"
            OAuthProvider.GITHUB -> "https://api.github.com/user"
            OAuthProvider.MICROSOFT -> "https://graph.microsoft.com/v1.0/me"
            OAuthProvider.LINKEDIN -> "https://api.linkedin.com/v2/me"
            OAuthProvider.TWITTER -> "https://api.twitter.com/2/users/me"
            OAuthProvider.TWITCH -> "https://api.twitch.tv/helix/users"
            OAuthProvider.REDDIT -> "https://oauth.reddit.com/api/v1/me"
            OAuthProvider.STEAM -> "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/"
            OAuthProvider.EPIC_GAMES -> "https://api.epicgames.dev/epic/oauth/v1/userinfo"
            OAuthProvider.SPOTIFY -> "https://api.spotify.com/v1/me"
            OAuthProvider.FACEBOOK -> "https://graph.facebook.com/v18.0/me"
            OAuthProvider.APPLE -> "https://appleid.apple.com/auth/userinfo"
        }
    }

    /**
     * Gets provider-specific information and capabilities.
     *
     * @param provider The OAuth provider
     * @return ProviderInfo with details about the provider
     */
    fun getProviderInfo(provider: OAuthProvider): ProviderInfo {
        return when (provider) {
            OAuthProvider.GOOGLE -> ProviderInfo(
                name = "Google",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.DISCORD -> ProviderInfo(
                name = "Discord",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.GITHUB -> ProviderInfo(
                name = "GitHub",
                supportsRefreshTokens = false,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.MICROSOFT -> ProviderInfo(
                name = "Microsoft",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.LINKEDIN -> ProviderInfo(
                name = "LinkedIn",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.TWITTER -> ProviderInfo(
                name = "Twitter",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.TWITCH -> ProviderInfo(
                name = "Twitch",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.REDDIT -> ProviderInfo(
                name = "Reddit",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.STEAM -> ProviderInfo(
                name = "Steam",
                supportsRefreshTokens = false,
                supportsPKCE = false,
                supportsScopes = false,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.EPIC_GAMES -> ProviderInfo(
                name = "Epic Games",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.SPOTIFY -> ProviderInfo(
                name = "Spotify",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.FACEBOOK -> ProviderInfo(
                name = "Facebook",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
            OAuthProvider.APPLE -> ProviderInfo(
                name = "Apple",
                supportsRefreshTokens = true,
                supportsPKCE = true,
                supportsScopes = true,
                defaultScopes = getDefaultScopes(provider),
                authUrl = getAuthorizationUrlTemplate(provider),
                tokenUrl = getTokenUrl(provider),
                userInfoUrl = getUserInfoUrl(provider)
            )
        }
    }
}

/**
 * Information about an OAuth provider's capabilities and endpoints.
 */
data class ProviderInfo(
    val name: String,
    val supportsRefreshTokens: Boolean,
    val supportsPKCE: Boolean,
    val supportsScopes: Boolean,
    val defaultScopes: List<String>,
    val authUrl: String,
    val tokenUrl: String,
    val userInfoUrl: String
)
