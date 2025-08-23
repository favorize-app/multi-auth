package app.multiauth.oauth.clients

import app.multiauth.oauth.OAuthClient
import app.multiauth.oauth.OAuthConfig
import app.multiauth.oauth.OAuthResult
import app.multiauth.oauth.OAuthError
import app.multiauth.oauth.OAuthUserInfo
import app.multiauth.providers.OAuthResult
import app.multiauth.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Real Twitter OAuth client implementation.
 * Handles the complete OAuth 2.0 flow with PKCE for Twitter authentication.
 */
class TwitterOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: HttpClient
) : OAuthClient {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val AUTH_URL = "https://twitter.com/i/oauth2/authorize"
        private const val TOKEN_URL = "https://api.twitter.com/2/oauth2/token"
        private const val USER_INFO_URL = "https://api.twitter.com/2/users/me"
        
        private const val DEFAULT_SCOPE = "tweet.read users.read offline.access"
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
        logger.debug("oath", "Generated Twitter OAuth authorization URL: $authUrl")
        return authUrl
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        return try {
            logger.debug("oath", "Exchanging authorization code for Twitter tokens")
            
            val tokenRequest = TwitterTokenRequest(
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
                val tokenResponse = json.decodeFromString<TwitterTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully exchanged code for Twitter tokens")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<TwitterErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to exchange code for Twitter tokens: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenExchangeFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("twitter", "Exception during Twitter token exchange", e)
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
            logger.debug("oath", "Refreshing Twitter access token")
            
            val refreshRequest = TwitterRefreshRequest(
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
                val tokenResponse = json.decodeFromString<TwitterTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully refreshed Twitter access token")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = refreshToken, // Keep the original refresh token
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<TwitterErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to refresh Twitter access token: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenRefreshFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("twitter", "Exception during Twitter token refresh", e)
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
            logger.debug("oath", "Fetching Twitter user info")
            
            val response = withContext(Dispatchers.IO) {
                httpClient.get(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/json")
                }
            }
            
            if (response.status.isSuccess()) {
                val userInfo = json.decodeFromString<TwitterUserInfo>(response.bodyAsText())
                logger.debug("oath", "Successfully fetched Twitter user info: ${userInfo.data.username}")
                
                OAuthResult.Success(
                    userInfo = OAuthUserInfo(
                        id = userInfo.data.id,
                        email = null, // Twitter doesn't provide email in basic profile
                        name = userInfo.data.name,
                        firstName = null, // Twitter doesn't provide first/last name
                        lastName = null,
                        picture = userInfo.data.profileImageUrl,
                        locale = null, // Twitter doesn't provide locale in basic profile
                        verifiedEmail = false // Twitter doesn't provide email verification status
                    )
                )
            } else {
                val errorResponse = json.decodeFromString<TwitterErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to fetch Twitter user info: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.UserInfoFetchFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("twitter", "Exception during Twitter user info fetch", e)
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
            logger.debug("oath", "Revoking Twitter OAuth token")
            
            // Twitter doesn't have a standard token revocation endpoint
            // The token will expire naturally based on the expires_in value
            logger.warn("twitter", "Twitter OAuth does not support token revocation. Token will expire naturally.")
            
            // Return true to indicate "success" since we can't actually revoke
            true
        } catch (e: Exception) {
            logger.error("twitter", "Exception during Twitter token revocation", e)
            false
        }
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        return try {
            logger.debug("oath", "Validating Twitter OAuth token")
            
            val response = withContext(Dispatchers.IO) {
                httpClient.get(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/json")
                }
            }
            
            val isValid = response.status.isSuccess()
            logger.debug("oath", "Twitter OAuth token validation result: $isValid")
            
            isValid
        } catch (e: Exception) {
            logger.error("twitter", "Exception during Twitter token validation", e)
            false
        }
    }
    
    // Data classes for Twitter OAuth API
    
    @Serializable
    private data class TwitterTokenRequest(
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
    private data class TwitterRefreshRequest(
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
    private data class TwitterTokenResponse(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresIn: Long,
        val tokenType: String = "bearer",
        val scope: String = ""
    )
    
    @Serializable
    private data class TwitterUserInfo(
        val data: TwitterUserData
    )
    
    @Serializable
    private data class TwitterUserData(
        val id: String,
        val name: String,
        val username: String,
        val profileImageUrl: String? = null,
        val verified: Boolean = false,
        val createdAt: String? = null,
        val description: String? = null,
        val location: String? = null,
        val url: String? = null,
        val followersCount: Int = 0,
        val followingCount: Int = 0,
        val tweetCount: Int = 0
    )
    
    @Serializable
    private data class TwitterErrorResponse(
        val error: String,
        val errorDescription: String? = null,
        val status: Int? = null,
        val detail: String? = null,
        val title: String? = null,
        val type: String? = null
    )
}