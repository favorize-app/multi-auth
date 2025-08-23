package app.multiauth.oauth.clients

import app.multiauth.oauth.OAuthClient
import app.multiauth.oauth.OAuthConfig
import app.multiauth.oauth.OAuthResult
import app.multiauth.oauth.OAuthError
import app.multiauth.oauth.OAuthUserInfo
import app.multiauth.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Real Discord OAuth client implementation.
 * Handles the complete OAuth 2.0 flow with PKCE for Discord authentication.
 */
class DiscordOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: HttpClient
) : OAuthClient {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val AUTH_URL = "https://discord.com/api/oauth2/authorize"
        private const val TOKEN_URL = "https://discord.com/api/oauth2/token"
        private const val USER_INFO_URL = "https://discord.com/api/users/@me"
        private const val REVOKE_URL = "https://discord.com/api/oauth2/token/revoke"
        
        private const val DEFAULT_SCOPE = "identify email"
        private const val RESPONSE_TYPE = "code"
        private const val GRANT_TYPE_AUTH_CODE = "authorization_code"
        private const val GRANT_TYPE_REFRESH = "refresh_token"
    }
    
    override suspend fun getAuthorizationUrl(
        state: String,
        codeChallenge: String,
        codeChallengeMethod: String
    ): String {
        val params = buildString {
            append("?client_id=${config.clientId}")
            append("&redirect_uri=${config.redirectUri}")
            append("&response_type=$RESPONSE_TYPE")
            append("&scope=${config.scopes.ifEmpty { DEFAULT_SCOPE }}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=$codeChallengeMethod")
        }
        
        val authUrl = "$AUTH_URL$params"
        logger.debug("oath", "Generated Discord OAuth authorization URL: $authUrl")
        return authUrl
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        return try {
            logger.debug("oath", "Exchanging authorization code for Discord tokens")
            
            val tokenRequest = DiscordTokenRequest(
                clientId = config.clientId,
                clientSecret = config.clientSecret,
                code = authorizationCode,
                redirectUri = config.redirectUri,
                grantType = GRANT_TYPE_AUTH_CODE,
                codeVerifier = codeVerifier
            )
            
            val response = withContext(Dispatchers.IO) {
                httpClient.post(TOKEN_URL) {
                    setBody(tokenRequest.toFormData())
                    header("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            
            if (response.status.isSuccess()) {
                val tokenResponse = json.decodeFromString<DiscordTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully exchanged code for Discord tokens")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<DiscordErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to exchange code for Discord tokens: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenExchangeFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("Exception during Discord token exchange", e)
            OAuthResult.Error(
                OAuthError.TokenExchangeFailed(
                    error = "token_exchange_failed",
                    errorDescription = e.message ?: "Unknown error"
                )
            )
        }
    }
    
    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        return try {
            logger.debug("oath", "Refreshing Discord access token")
            
            val refreshRequest = DiscordRefreshRequest(
                clientId = config.clientId,
                clientSecret = config.clientSecret,
                refreshToken = refreshToken,
                grantType = GRANT_TYPE_REFRESH
            )
            
            val response = withContext(Dispatchers.IO) {
                httpClient.post(TOKEN_URL) {
                    setBody(refreshRequest.toFormData())
                    header("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            
            if (response.status.isSuccess()) {
                val tokenResponse = json.decodeFromString<DiscordTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully refreshed Discord access token")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = refreshToken, // Keep the original refresh token
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<DiscordErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to refresh Discord access token: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenRefreshFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("Exception during Discord token refresh", e)
            OAuthResult.Error(
                OAuthError.TokenRefreshFailed(
                    error = "token_refresh_failed",
                    errorDescription = e.message ?: "Unknown error"
                )
            )
        }
    }
    
    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        return try {
            logger.debug("oath", "Fetching Discord user info")
            
            val response = withContext(Dispatchers.IO) {
                httpClient.get(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
            
            if (response.status.isSuccess()) {
                val userInfo = json.decodeFromString<DiscordUserInfo>(response.bodyAsText())
                logger.debug("oath", "Successfully fetched Discord user info: ${userInfo.username}")
                
                OAuthResult.Success(
                    userInfo = OAuthUserInfo(
                        id = userInfo.id,
                        email = userInfo.email,
                        name = userInfo.username,
                        firstName = null, // Discord doesn't provide first/last name
                        lastName = null,
                        picture = "https://cdn.discordapp.com/avatars/${userInfo.id}/${userInfo.avatar}.png",
                        locale = userInfo.locale,
                        verifiedEmail = userInfo.verified
                    )
                )
            } else {
                val errorResponse = json.decodeFromString<DiscordErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to fetch Discord user info: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.UserInfoFetchFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("Exception during Discord user info fetch", e)
            OAuthResult.Error(
                OAuthError.UserInfoFetchFailed(
                    error = "user_info_fetch_failed",
                    errorDescription = e.message ?: "Unknown error"
                )
            )
        }
    }
    
    override suspend fun revokeToken(token: String): Boolean {
        return try {
            logger.debug("oath", "Revoking Discord OAuth token")
            
            val response = withContext(Dispatchers.IO) {
                httpClient.post(REVOKE_URL) {
                    setBody("token=$token")
                    header("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            
            val success = response.status.isSuccess()
            if (success) {
                logger.debug("oath", "Successfully revoked Discord OAuth token")
            } else {
                logger.warn("discord", "Failed to revoke Discord OAuth token: ${response.status}")
            }
            
            success
        } catch (e: Exception) {
            logger.error("Exception during Discord token revocation", e)
            false
        }
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        return try {
            logger.debug("oath", "Validating Discord OAuth token")
            
            val response = withContext(Dispatchers.IO) {
                httpClient.get(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
            
            val isValid = response.status.isSuccess()
            logger.debug("oath", "Discord OAuth token validation result: $isValid")
            
            isValid
        } catch (e: Exception) {
            logger.error("Exception during Discord token validation", e)
            false
        }
    }
    
    // Data classes for Discord OAuth API
    
    @Serializable
    private data class DiscordTokenRequest(
        val clientId: String,
        val clientSecret: String,
        val code: String,
        val redirectUri: String,
        val grantType: String,
        val codeVerifier: String
    ) {
        fun toFormData(): String {
            return buildString {
                append("client_id=$clientId")
                append("&client_secret=$clientSecret")
                append("&code=$code")
                append("&redirect_uri=$redirectUri")
                append("&grant_type=$grantType")
                append("&code_verifier=$codeVerifier")
            }
        }
    }
    
    @Serializable
    private data class DiscordRefreshRequest(
        val clientId: String,
        val clientSecret: String,
        val refreshToken: String,
        val grantType: String
    ) {
        fun toFormData(): String {
            return buildString {
                append("client_id=$clientId")
                append("&client_secret=$clientSecret")
                append("&refresh_token=$refreshToken")
                append("&grant_type=$grantType")
            }
        }
    }
    
    @Serializable
    private data class DiscordTokenResponse(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresIn: Long,
        val tokenType: String,
        val scope: String
    )
    
    @Serializable
    private data class DiscordUserInfo(
        val id: String,
        val username: String,
        val email: String? = null,
        val verified: Boolean = false,
        val avatar: String? = null,
        val locale: String? = null
    )
    
    @Serializable
    private data class DiscordErrorResponse(
        val error: String,
        val errorDescription: String? = null
    )
}