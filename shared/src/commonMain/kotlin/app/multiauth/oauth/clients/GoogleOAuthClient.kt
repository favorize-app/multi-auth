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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real Google OAuth client implementation.
 * Handles the complete OAuth 2.0 flow with PKCE for Google authentication.
 */
class GoogleOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: HttpClient
) : OAuthClient {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
        private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        
        private const val DEFAULT_SCOPE = "openid email profile"
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
            append("&access_type=offline")
            append("&prompt=consent")
        }
        
        val authUrl = "$AUTH_URL$params"
        logger.debug("oath", "Generated Google OAuth authorization URL: $authUrl")
        return authUrl
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        return try {
            logger.debug("oath", "Exchanging authorization code for tokens")
            
            val tokenRequest = GoogleTokenRequest(
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
                val tokenResponse = json.decodeFromString<GoogleTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully exchanged code for tokens")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<GoogleErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to exchange code for tokens: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenExchangeFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("google", "Exception during token exchange", e)
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
            logger.debug("oath", "Refreshing access token")
            
            val refreshRequest = GoogleRefreshRequest(
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
                val tokenResponse = json.decodeFromString<GoogleTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully refreshed access token")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = refreshToken, // Keep the original refresh token
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<GoogleErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to refresh access token: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenRefreshFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("google", "Exception during token refresh", e)
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
            logger.debug("oath", "Fetching user info from Google")
            
            val response = withContext(Dispatchers.IO) {
                httpClient(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
            
            if (response.status.isSuccess()) {
                val userInfo = json.decodeFromString<GoogleUserInfo>(response.bodyAsText())
                logger.debug("oath", "Successfully fetched user info: ${userInfo.email}")
                
                OAuthResult.Success(
                    userInfo = OAuthUserInfo(
                        id = userInfo.id,
                        email = userInfo.email,
                        name = userInfo.name,
                        firstName = userInfo.givenName,
                        lastName = userInfo.familyName,
                        picture = userInfo.picture,
                        locale = userInfo.locale,
                        verifiedEmail = userInfo.verifiedEmail
                    )
                )
            } else {
                val errorResponse = json.decodeFromString<GoogleErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to fetch user info: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.UserInfoFetchFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("google", "Exception during user info fetch", e)
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
            logger.debug("oath", "Revoking Google OAuth token")
            
            val response = withContext(Dispatchers.IO) {
                httpClient.post(REVOKE_URL) {
                    setBody("token=$token")
                    header("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            
            val success = response.status.isSuccess()
            if (success) {
                logger.debug("oath", "Successfully revoked Google OAuth token")
            } else {
                logger.warn("google", "Failed to revoke Google OAuth token: ${response.status}")
            }
            
            success
        } catch (e: Exception) {
            logger.error("google", "Exception during token revocation", e)
            false
        }
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        return try {
            logger.debug("oath", "Validating Google OAuth token")
            
            val response = withContext(Dispatchers.IO) {
                httpClient(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
            
            val isValid = response.status.isSuccess()
            logger.debug("oath", "Google OAuth token validation result: $isValid")
            
            isValid
        } catch (e: Exception) {
            logger.error("google", "Exception during token validation", e)
            false
        }
    }
    
    // Data classes for Google OAuth API
    
    @Serializable
    private data class GoogleTokenRequest(
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
    private data class GoogleRefreshRequest(
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
    private data class GoogleTokenResponse(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresIn: Long,
        val tokenType: String,
        val scope: String
    )
    
    @Serializable
    private data class GoogleUserInfo(
        val id: String,
        val email: String,
        val verifiedEmail: Boolean,
        val name: String? = null,
        val givenName: String? = null,
        val familyName: String? = null,
        val picture: String? = null,
        val locale: String? = null
    )
    
    @Serializable
    private data class GoogleErrorResponse(
        val error: String,
        val errorDescription: String? = null
    )
}

/**
 * HTTP client interface for making OAuth requests.
 * This should be implemented by platform-specific HTTP clients.
 */
interface HttpClient {
    suspend fun get(url: String, block: HttpRequestBuilder.() -> Unit): HttpResponse
    suspend fun post(url: String, block: HttpRequestBuilder.() -> Unit): HttpResponse
}

/**
 * HTTP request builder for configuring requests.
 */
interface HttpRequestBuilder {
    fun setBody(body: String)
    fun header(name: String, value: String)
}

/**
 * HTTP response from OAuth requests.
 */
interface HttpResponse {
    val status: HttpStatus
    fun bodyAsText(): String
}

/**
 * HTTP status codes.
 */
interface HttpStatus {
    val code: Int
    fun isSuccess(): Boolean
}

/**
 * Simple HTTP status implementation.
 */
class SimpleHttpStatus(override val code: Int) : HttpStatus {
    override fun isSuccess(): Boolean = code in 200..299
}