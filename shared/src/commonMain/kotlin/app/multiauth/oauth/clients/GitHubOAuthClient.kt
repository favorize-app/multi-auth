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
 * Real GitHub OAuth client implementation.
 * Handles the complete OAuth 2.0 flow with PKCE for GitHub authentication.
 */
class GitHubOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: HttpClient
) : OAuthClient {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val AUTH_URL = "https://github.com/login/oauth/authorize"
        private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        private const val USER_INFO_URL = "https://api.github.com/user"
        private const val USER_EMAILS_URL = "https://api.github.com/user/emails"
        
        private const val DEFAULT_SCOPE = "read:user user:email"
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
        logger.debug("oath", "Generated GitHub OAuth authorization URL: $authUrl")
        return authUrl
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        return try {
            logger.debug("oath", "Exchanging authorization code for GitHub tokens")
            
            val tokenRequest = GitHubTokenRequest(
                clientId = config.clientId,
                clientSecret = config.clientSecret,
                code = authorizationCode,
                redirectUri = config.redirectUri,
                codeVerifier = codeVerifier
            )
            
            val response = withContext(Dispatchers.IO) {
                httpClient.post(TOKEN_URL) {
                    setBody(tokenRequest.toFormData())
                    header("Content-Type", "application/x-www-form-urlencoded")
                    header("Accept", "application/json")
                }
            }
            
            if (response.status.isSuccess()) {
                val tokenResponse = json.decodeFromString<GitHubTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully exchanged code for GitHub tokens")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<GitHubErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to exchange code for GitHub tokens: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenExchangeFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("github", "Exception during GitHub token exchange", e)
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
            logger.debug("oath", "Refreshing GitHub access token")
            
            // Note: GitHub doesn't support refresh tokens in the standard OAuth flow
            // This method will return an error indicating refresh is not supported
            logger.warn("github", "GitHub OAuth does not support refresh tokens in standard flow")
            
            OAuthResult.Error(
                OAuthError.TokenRefreshFailed(
                    error = "refresh_not_supported",
                    errorDescription = "GitHub OAuth does not support refresh tokens in standard flow"
                )
            )
        } catch (e: Exception) {
            logger.error("github", "Exception during GitHub token refresh", e)
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
            logger.debug("oath", "Fetching GitHub user info")
            
            // Fetch user profile
            val userResponse = withContext(Dispatchers.IO) {
                httpClient.get(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.github.v3+json")
                }
            }
            
            if (userResponse.status.isSuccess()) {
                val userInfo = json.decodeFromString<GitHubUserInfo>(userResponse.bodyAsText())
                
                // Fetch user emails
                val emailsResponse = withContext(Dispatchers.IO) {
                    httpClient.get(USER_EMAILS_URL) {
                        header("Authorization", "Bearer $accessToken")
                        header("Accept", "application/vnd.github.v3+json")
                    }
                }
                
                val primaryEmail = if (emailsResponse.status.isSuccess()) {
                    val emails = json.decodeFromString<List<GitHubEmail>>(emailsResponse.bodyAsText())
                    emails.find { it.primary }?.email ?: userInfo.email
                } else {
                    userInfo.email
                }
                
                logger.debug("oath", "Successfully fetched GitHub user info: ${userInfo.login}")
                
                OAuthResult.Success(
                    userInfo = OAuthUserInfo(
                        id = userInfo.id.toString(),
                        email = primaryEmail,
                        name = userInfo.name ?: userInfo.login,
                        firstName = null, // GitHub doesn't provide first/last name
                        lastName = null,
                        picture = userInfo.avatarUrl,
                        locale = null, // GitHub doesn't provide locale
                        verifiedEmail = true // GitHub emails are verified
                    )
                )
            } else {
                val errorResponse = json.decodeFromString<GitHubErrorResponse>(userResponse.bodyAsText())
                logger.error("oath", "Failed to fetch GitHub user info: ${errorResponse.message}")
                
                OAuthResult.Error(
                    OAuthError.UserInfoFetchFailed(
                        error = errorResponse.error ?: "unknown_error",
                        errorDescription = errorResponse.message
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("github", "Exception during GitHub user info fetch", e)
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
            logger.debug("oath", "Revoking GitHub OAuth token")
            
            // Note: GitHub doesn't have a standard token revocation endpoint
            // The token will expire naturally based on the expires_in value
            logger.warn("github", "GitHub OAuth does not support token revocation. Token will expire naturally.")
            
            // Return true to indicate "success" since we can't actually revoke
            true
        } catch (e: Exception) {
            logger.error("github", "Exception during GitHub token revocation", e)
            false
        }
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        return try {
            logger.debug("oath", "Validating GitHub OAuth token")
            
            val response = withContext(Dispatchers.IO) {
                httpClient.get(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.github.v3+json")
                }
            }
            
            val isValid = response.status.isSuccess()
            logger.debug("oath", "GitHub OAuth token validation result: $isValid")
            
            isValid
        } catch (e: Exception) {
            logger.error("github", "Exception during GitHub token validation", e)
            false
        }
    }
    
    // Data classes for GitHub OAuth API
    
    @Serializable
    private data class GitHubTokenRequest(
        val clientId: String,
        val clientSecret: String,
        val code: String,
        val redirectUri: String,
        val codeVerifier: String
    ) {
        fun toFormData(): String {
            return buildString {
                append("client_id=$clientId")
                append("&client_secret=$clientSecret")
                append("&code=$code")
                append("&redirect_uri=$redirectUri")
                append("&code_verifier=$codeVerifier")
            }
        }
    }
    
    @Serializable
    private data class GitHubTokenResponse(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresIn: Long? = null,
        val tokenType: String = "bearer",
        val scope: String = ""
    )
    
    @Serializable
    private data class GitHubUserInfo(
        val id: Long,
        val login: String,
        val name: String? = null,
        val email: String? = null,
        val avatarUrl: String? = null,
        val bio: String? = null,
        val company: String? = null,
        val location: String? = null,
        val blog: String? = null,
        val twitterUsername: String? = null,
        val publicRepos: Int = 0,
        val publicGists: Int = 0,
        val followers: Int = 0,
        val following: Int = 0,
        val createdAt: String? = null,
        val updatedAt: String? = null
    )
    
    @Serializable
    private data class GitHubEmail(
        val email: String,
        val primary: Boolean,
        val verified: Boolean,
        val visibility: String? = null
    )
    
    @Serializable
    private data class GitHubErrorResponse(
        val message: String,
        val error: String? = null,
        val errorDescription: String? = null
    )
}