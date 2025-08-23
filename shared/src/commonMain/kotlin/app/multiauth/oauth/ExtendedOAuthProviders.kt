package app.multiauth.oauth

import app.multiauth.util.Logger
import java.time.Instant

/**
 * Extended OAuth providers for additional services.
 * Provides configuration and metadata for various OAuth providers.
 */
object ExtendedOAuthProviders {
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Discord OAuth provider configuration.
     */
    object Discord {
        const val PROVIDER_NAME = "Discord"
        const val PROVIDER_ID = "discord"
        const val AUTH_URL = "https://discord.com/api/oauth2/authorize"
        const val TOKEN_URL = "https://discord.com/api/oauth2/token"
        const val USER_INFO_URL = "https://discord.com/api/users/@me"
        
        val DEFAULT_SCOPES = listOf(
            "identify",
            "email"
        )
        
        val EXTENDED_SCOPES = listOf(
            "identify",
            "email",
            "guilds",
            "guilds.join",
            "guilds.members.read"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null
        ): String {
            val scopeParam = scopes.joinToString(" ")
            val stateParam = state?.let { "&state=$it" } ?: ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scopeParam$stateParam"
        }
    }
    
    /**
     * GitHub OAuth provider configuration.
     */
    object GitHub {
        const val PROVIDER_NAME = "GitHub"
        const val PROVIDER_ID = "github"
        const val AUTH_URL = "https://github.com/login/oauth/authorize"
        const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val USER_INFO_URL = "https://api.github.com/user"
        const val USER_EMAILS_URL = "https://api.github.com/user/emails"
        
        val DEFAULT_SCOPES = listOf(
            "read:user",
            "user:email"
        )
        
        val EXTENDED_SCOPES = listOf(
            "read:user",
            "user:email",
            "repo",
            "workflow"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null
        ): String {
            val scopeParam = scopes.joinToString(",")
            val stateParam = state?.let { "&state=$it" } ?: ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&scope=$scopeParam$stateParam"
        }
    }
    
    /**
     * Microsoft OAuth provider configuration.
     */
    object Microsoft {
        const val PROVIDER_NAME = "Microsoft"
        const val PROVIDER_ID = "microsoft"
        const val AUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        const val TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        const val USER_INFO_URL = "https://graph.microsoft.com/v1.0/me"
        
        val DEFAULT_SCOPES = listOf(
            "openid",
            "profile",
            "email"
        )
        
        val EXTENDED_SCOPES = listOf(
            "openid",
            "profile",
            "email",
            "User.Read",
            "Calendars.Read",
            "Mail.Read"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null
        ): String {
            val scopeParam = scopes.joinToString(" ")
            val stateParam = state?.let { "&state=$it" } ?: ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scopeParam$stateParam"
        }
    }
    
    /**
     * LinkedIn OAuth provider configuration.
     */
    object LinkedIn {
        const val PROVIDER_NAME = "LinkedIn"
        const val PROVIDER_ID = "linkedin"
        const val AUTH_URL = "https://www.linkedin.com/oauth/v2/authorization"
        const val TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken"
        const val USER_INFO_URL = "https://api.linkedin.com/v2/me"
        const val USER_EMAIL_URL = "https://api.linkedin.com/v2/emailAddress?q=members&projection=(elements*(handle~))"
        
        val DEFAULT_SCOPES = listOf(
            "r_liteprofile",
            "r_emailaddress"
        )
        
        val EXTENDED_SCOPES = listOf(
            "r_liteprofile",
            "r_emailaddress",
            "w_member_social"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null
        ): String {
            val scopeParam = scopes.joinToString(" ")
            val stateParam = state?.let { "&state=$it" } ?: ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scopeParam$stateParam"
        }
    }
    
    /**
     * Twitter OAuth provider configuration.
     */
    object Twitter {
        const val PROVIDER_NAME = "Twitter"
        const val PROVIDER_ID = "twitter"
        const val AUTH_URL = "https://twitter.com/i/oauth2/authorize"
        const val TOKEN_URL = "https://api.twitter.com/2/oauth2/token"
        const val USER_INFO_URL = "https://api.twitter.com/2/users/me"
        
        val DEFAULT_SCOPES = listOf(
            "tweet.read",
            "users.read"
        )
        
        val EXTENDED_SCOPES = listOf(
            "tweet.read",
            "tweet.write",
            "users.read",
            "offline.access"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null,
            codeChallenge: String? = null,
            codeChallengeMethod: String = "S256"
        ): String {
            val scopeParam = scopes.joinToString(" ")
            val stateParam = state?.let { "&state=$it" } ?: ""
            val codeChallengeParam = codeChallenge?.let { "&code_challenge=$it" } ?: ""
            val codeChallengeMethodParam = codeChallenge?.let { "&code_challenge_method=$codeChallengeMethod" } ?: ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scopeParam$stateParam$codeChallengeParam$codeChallengeMethodParam"
        }
    }
    
    /**
     * Twitch OAuth provider configuration.
     */
    object Twitch {
        const val PROVIDER_NAME = "Twitch"
        const val PROVIDER_ID = "twitch"
        const val AUTH_URL = "https://id.twitch.tv/oauth2/authorize"
        const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        const val USER_INFO_URL = "https://api.twitch.tv/helix/users"
        
        val DEFAULT_SCOPES = listOf(
            "user:read:email"
        )
        
        val EXTENDED_SCOPES = listOf(
            "user:read:email",
            "channel:read:subscriptions",
            "channel:read:redemptions"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null,
            forceVerify: Boolean = false
        ): String {
            val scopeParam = scopes.joinToString(" ")
            val stateParam = state?.let { "&state=$it" } ?: ""
            val forceVerifyParam = if (forceVerify) "&force_verify=true" else ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scopeParam$stateParam$forceVerifyParam"
        }
    }
    
    /**
     * Reddit OAuth provider configuration.
     */
    object Reddit {
        const val PROVIDER_NAME = "Reddit"
        const val PROVIDER_ID = "reddit"
        const val AUTH_URL = "https://www.reddit.com/api/v1/authorize"
        const val TOKEN_URL = "https://www.reddit.com/api/v1/access_token"
        const val USER_INFO_URL = "https://oauth.reddit.com/api/v1/me"
        
        val DEFAULT_SCOPES = listOf(
            "identity"
        )
        
        val EXTENDED_SCOPES = listOf(
            "identity",
            "read",
            "history"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null,
            duration: String = "temporary"
        ): String {
            val scopeParam = scopes.joinToString(",")
            val stateParam = state?.let { "&state=$it" } ?: ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scopeParam&duration=$duration$stateParam"
        }
    }
    
    /**
     * Steam OAuth provider configuration.
     */
    object Steam {
        const val PROVIDER_NAME = "Steam"
        const val PROVIDER_ID = "steam"
        const val AUTH_URL = "https://steamcommunity.com/openid/login"
        const val USER_INFO_URL = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/"
        
        // Steam uses OpenID 2.0, not OAuth 2.0
        fun getOpenIdUrl(
            returnUrl: String,
            realm: String
        ): String {
            return "$AUTH_URL?openid.ns=http://specs.openid.net/auth/2.0&openid.mode=checkid_setup&openid.return_to=$returnUrl&openid.realm=$realm&openid.identity=http://specs.openid.net/auth/2.0/identifier_select"
        }
    }
    
    /**
     * Epic Games OAuth provider configuration.
     */
    object EpicGames {
        const val PROVIDER_NAME = "Epic Games"
        const val PROVIDER_ID = "epic_games"
        const val AUTH_URL = "https://www.epicgames.com/id/authorize"
        const val TOKEN_URL = "https://api.epicgames.com/epic/oauth/v1/token"
        const val USER_INFO_URL = "https://api.epicgames.com/epic/oauth/v1/userinfo"
        
        val DEFAULT_SCOPES = listOf(
            "basic_profile"
        )
        
        val EXTENDED_SCOPES = listOf(
            "basic_profile",
            "friends_list",
            "presence"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null
        ): String {
            val scopeParam = scopes.joinToString(" ")
            val stateParam = state?.let { "&state=$it" } ?: ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scopeParam$stateParam"
        }
    }
    
    /**
     * Spotify OAuth provider configuration.
     */
    object Spotify {
        const val PROVIDER_NAME = "Spotify"
        const val PROVIDER_ID = "spotify"
        const val AUTH_URL = "https://accounts.spotify.com/authorize"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val USER_INFO_URL = "https://api.spotify.com/v1/me"
        
        val DEFAULT_SCOPES = listOf(
            "user-read-email",
            "user-read-private"
        )
        
        val EXTENDED_SCOPES = listOf(
            "user-read-email",
            "user-read-private",
            "playlist-read-private",
            "playlist-modify-public",
            "playlist-modify-private"
        )
        
        fun getAuthUrl(
            clientId: String,
            redirectUri: String,
            scopes: List<String> = DEFAULT_SCOPES,
            state: String? = null,
            showDialog: Boolean = false
        ): String {
            val scopeParam = scopes.joinToString(" ")
            val stateParam = state?.let { "&state=$it" } ?: ""
            val showDialogParam = if (showDialog) "&show_dialog=true" else ""
            
            return "$AUTH_URL?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=$scopeParam$stateParam$showDialogParam"
        }
    }
    
    /**
     * Gets all available OAuth providers.
     * 
     * @return List of all OAuth provider configurations
     */
    fun getAllProviders(): List<OAuthProviderConfig> {
        return listOf(
            OAuthProviderConfig(
                id = Discord.PROVIDER_ID,
                name = Discord.PROVIDER_NAME,
                displayName = "Discord",
                authUrl = Discord.AUTH_URL,
                tokenUrl = Discord.TOKEN_URL,
                userInfoUrl = Discord.USER_INFO_URL,
                defaultScopes = Discord.DEFAULT_SCOPES,
                supportedScopes = Discord.EXTENDED_SCOPES,
                color = OAuthColors.discord,
                icon = "discord_icon"
            ),
            OAuthProviderConfig(
                id = GitHub.PROVIDER_ID,
                name = GitHub.PROVIDER_NAME,
                displayName = "GitHub",
                authUrl = GitHub.AUTH_URL,
                tokenUrl = GitHub.TOKEN_URL,
                userInfoUrl = GitHub.USER_INFO_URL,
                defaultScopes = GitHub.DEFAULT_SCOPES,
                supportedScopes = GitHub.EXTENDED_SCOPES,
                color = OAuthColors.github,
                icon = "github_icon"
            ),
            OAuthProviderConfig(
                id = Microsoft.PROVIDER_ID,
                name = Microsoft.PROVIDER_NAME,
                displayName = "Microsoft",
                authUrl = Microsoft.AUTH_URL,
                tokenUrl = Microsoft.TOKEN_URL,
                userInfoUrl = Microsoft.USER_INFO_URL,
                defaultScopes = Microsoft.DEFAULT_SCOPES,
                supportedScopes = Microsoft.EXTENDED_SCOPES,
                color = OAuthColors.microsoft,
                icon = "microsoft_icon"
            ),
            OAuthProviderConfig(
                id = LinkedIn.PROVIDER_ID,
                name = LinkedIn.PROVIDER_NAME,
                displayName = "LinkedIn",
                authUrl = LinkedIn.AUTH_URL,
                tokenUrl = LinkedIn.TOKEN_URL,
                userInfoUrl = LinkedIn.USER_INFO_URL,
                defaultScopes = LinkedIn.DEFAULT_SCOPES,
                supportedScopes = LinkedIn.EXTENDED_SCOPES,
                color = OAuthColors.linkedin,
                icon = "linkedin_icon"
            ),
            OAuthProviderConfig(
                id = Twitter.PROVIDER_ID,
                name = Twitter.PROVIDER_NAME,
                displayName = "Twitter",
                authUrl = Twitter.AUTH_URL,
                tokenUrl = Twitter.TOKEN_URL,
                userInfoUrl = Twitter.USER_INFO_URL,
                defaultScopes = Twitter.DEFAULT_SCOPES,
                supportedScopes = Twitter.EXTENDED_SCOPES,
                color = OAuthColors.twitter,
                icon = "twitter_icon"
            ),
            OAuthProviderConfig(
                id = Twitch.PROVIDER_ID,
                name = Twitch.PROVIDER_NAME,
                displayName = "Twitch",
                authUrl = Twitch.AUTH_URL,
                tokenUrl = Twitch.TOKEN_URL,
                userInfoUrl = Twitch.USER_INFO_URL,
                defaultScopes = Twitch.DEFAULT_SCOPES,
                supportedScopes = Twitch.EXTENDED_SCOPES,
                color = OAuthColors.twitch,
                icon = "twitch_icon"
            ),
            OAuthProviderConfig(
                id = Reddit.PROVIDER_ID,
                name = Reddit.PROVIDER_NAME,
                displayName = "Reddit",
                authUrl = Reddit.AUTH_URL,
                tokenUrl = Reddit.TOKEN_URL,
                userInfoUrl = Reddit.USER_INFO_URL,
                defaultScopes = Reddit.DEFAULT_SCOPES,
                supportedScopes = Reddit.EXTENDED_SCOPES,
                color = OAuthColors.reddit,
                icon = "reddit_icon"
            ),
            OAuthProviderConfig(
                id = EpicGames.PROVIDER_ID,
                name = EpicGames.PROVIDER_NAME,
                displayName = "Epic Games",
                authUrl = EpicGames.AUTH_URL,
                tokenUrl = EpicGames.TOKEN_URL,
                userInfoUrl = EpicGames.USER_INFO_URL,
                defaultScopes = EpicGames.DEFAULT_SCOPES,
                supportedScopes = EpicGames.EXTENDED_SCOPES,
                color = OAuthColors.epic_games,
                icon = "epic_games_icon"
            ),
            OAuthProviderConfig(
                id = Spotify.PROVIDER_ID,
                name = Spotify.PROVIDER_NAME,
                displayName = "Spotify",
                authUrl = Spotify.AUTH_URL,
                tokenUrl = Spotify.TOKEN_URL,
                userInfoUrl = Spotify.USER_INFO_URL,
                defaultScopes = Spotify.DEFAULT_SCOPES,
                supportedScopes = Spotify.EXTENDED_SCOPES,
                color = OAuthColors.spotify,
                icon = "spotify_icon"
            )
        )
    }
    
    /**
     * Gets OAuth provider configuration by ID.
     * 
     * @param providerId The provider ID to get configuration for
     * @return OAuth provider configuration or null if not found
     */
    fun getProviderById(providerId: String): OAuthProviderConfig? {
        return getAllProviders().find { it.id == providerId }
    }
    
    /**
     * Checks if a provider supports specific scopes.
     * 
     * @param providerId The provider ID to check
     * @param scopes The scopes to check support for
     * @return true if all scopes are supported, false otherwise
     */
    fun supportsScopes(providerId: String, scopes: List<String>): Boolean {
        val provider = getProviderById(providerId) ?: return false
        return scopes.all { scope -> provider.supportedScopes.contains(scope) }
    }
}

/**
 * Extended OAuth provider configuration.
 */
data class OAuthProviderConfig(
    val id: String,
    val name: String,
    val displayName: String,
    val authUrl: String,
    val tokenUrl: String,
    val userInfoUrl: String,
    val defaultScopes: List<String>,
    val supportedScopes: List<String>,
    val color: androidx.compose.ui.graphics.Color,
    val icon: String,
    val isEnabled: Boolean = true,
    val requiresPkce: Boolean = false,
    val supportsRefreshTokens: Boolean = true,
    val tokenExpiryHours: Long = 1
)

/**
 * Extended OAuth colors for additional providers.
 */
object OAuthColors {
    val discord = androidx.compose.ui.graphics.Color(0xFF5865F2)
    val github = androidx.compose.ui.graphics.Color(0xFF333333)
    val microsoft = androidx.compose.ui.graphics.Color(0xFF00A4EF)
    val linkedin = androidx.compose.ui.graphics.Color(0xFF0077B5)
    val twitter = androidx.compose.ui.graphics.Color(0xFF1DA1F2)
    val twitch = androidx.compose.ui.graphics.Color(0xFF9146FF)
    val reddit = androidx.compose.ui.graphics.Color(0xFFFF4500)
    val epic_games = androidx.compose.ui.graphics.Color(0xFF2A2A2A)
    val spotify = androidx.compose.ui.graphics.Color(0xFF1DB954)
}