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
 * Real Microsoft OAuth client implementation.
 * Handles the complete OAuth 2.0 flow with PKCE for Microsoft authentication.
 */
class MicrosoftOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: HttpClient
) : OAuthClient {
    
    private val logger = LoggerLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val AUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        private const val TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        private const val USER_INFO_URL = "https://graph.microsoft.com/v1.0/me"
        
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
            append("&response_mode=query")
        }
        
        val authUrl = "$AUTH_URL$params"
        logger.debug("oath", "Generated Microsoft OAuth authorization URL: $authUrl")
        return authUrl
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        return try {
            logger.debug("oath", "Exchanging authorization code for Microsoft tokens")
            
            val tokenRequest = MicrosoftTokenRequest(
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
                val tokenResponse = json.decodeFromString<MicrosoftTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully exchanged code for Microsoft tokens")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<MicrosoftErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to exchange code for Microsoft tokens: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenExchangeFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("microsoft", "Exception during Microsoft token exchange", e)
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
            logger.debug("oath", "Refreshing Microsoft access token")
            
            val refreshRequest = MicrosoftRefreshRequest(
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
                val tokenResponse = json.decodeFromString<MicrosoftTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully refreshed Microsoft access token")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = refreshToken, // Keep the original refresh token
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<MicrosoftErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to refresh Microsoft access token: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.TokenRefreshFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("microsoft", "Exception during Microsoft token refresh", e)
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
            logger.debug("oath", "Fetching Microsoft user info")
            
            val response = withContext(Dispatchers.IO) {
                httpClient(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/json")
                }
            }
            
            if (response.status.isSuccess()) {
                val userInfo = json.decodeFromString<MicrosoftUserInfo>(response.bodyAsText())
                logger.debug("oath", "Successfully fetched Microsoft user info: ${userInfo.displayName}")
                
                OAuthResult.Success(
                    userInfo = OAuthUserInfo(
                        id = userInfo.id,
                        email = userInfo.mail ?: userInfo.userPrincipalName,
                        name = userInfo.displayName,
                        firstName = userInfo.givenName,
                        lastName = userInfo.surname,
                        picture = null, // Microsoft Graph doesn't provide profile picture in basic profile
                        locale = userInfo.preferredLanguage,
                        verifiedEmail = true // Microsoft accounts are verified
                    )
                )
            } else {
                val errorResponse = json.decodeFromString<MicrosoftErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to fetch Microsoft user info: ${errorResponse.error}")
                
                OAuthResult.Error(
                    OAuthError.UserInfoFetchFailed(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("microsoft", "Exception during Microsoft user info fetch", e)
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
            logger.debug("oath", "Revoking Microsoft OAuth token")
            
            // Microsoft doesn't have a standard token revocation endpoint
            // The token will expire naturally based on the expires_in value
            logger.warn("microsoft", "Microsoft OAuth does not support token revocation. Token will expire naturally.")
            
            // Return true to indicate "success" since we can't actually revoke
            true
        } catch (e: Exception) {
            logger.error("microsoft", "Exception during Microsoft token revocation", e)
            false
        }
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        return try {
            logger.debug("oath", "Validating Microsoft OAuth token")
            
            val response = withContext(Dispatchers.IO) {
                httpClient(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/json")
                }
            }
            
            val isValid = response.status.isSuccess()
            logger.debug("oath", "Microsoft OAuth token validation result: $isValid")
            
            isValid
        } catch (e: Exception) {
            logger.error("microsoft", "Exception during Microsoft token validation", e)
            false
        }
    }
    
    // Data classes for Microsoft OAuth API
    
    @Serializable
    private data class MicrosoftTokenRequest(
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
    private data class MicrosoftRefreshRequest(
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
    private data class MicrosoftTokenResponse(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresIn: Long,
        val tokenType: String,
        val scope: String,
        val idToken: String? = null
    )
    
    @Serializable
    private data class MicrosoftUserInfo(
        val id: String,
        val displayName: String,
        val givenName: String? = null,
        val surname: String? = null,
        val mail: String? = null,
        val userPrincipalName: String,
        val preferredLanguage: String? = null,
        val businessPhones: List<String>? = null,
        val mobilePhone: String? = null,
        val officeLocation: String? = null,
        val jobTitle: String? = null,
        val department: String? = null,
        val companyName: String? = null
    )
    
    @Serializable
    private data class MicrosoftErrorResponse(
        val error: String,
        val errorDescription: String? = null,
        val errorCodes: List<Int>? = null,
        val timestamp: String? = null,
        val traceId: String? = null,
        val correlationId: String? = null
    )
}