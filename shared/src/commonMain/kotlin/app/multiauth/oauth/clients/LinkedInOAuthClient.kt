package app.multiauth.oauth.clients

import app.multiauth.oauth.HttpClient
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
 * Real LinkedIn OAuth client implementation.
 * Handles the complete OAuth 2.0 flow with PKCE for LinkedIn authentication.
 */
class LinkedInOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: HttpClient,
    override val logger: Logger
) : OAuthClient {

    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val AUTH_URL = "https://www.linkedin.com/oauth/v2/authorization"
        private const val TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken"
        private const val USER_INFO_URL = "https://api.linkedin.com/v2/me"
        private const val USER_EMAIL_URL = "https://api.linkedin.com/v2/emailAddress?q=members&projection=(elements*(handle~))"
        
        private const val DEFAULT_SCOPE = "r_liteprofile r_emailaddress"
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
        logger.debug("oath", "Generated LinkedIn OAuth authorization URL: $authUrl")
        return authUrl
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        return try {
            logger.debug("oath", "Exchanging authorization code for LinkedIn tokens")
            
            val tokenRequest = LinkedInTokenRequest(
                clientId = config.clientId,
                clientSecret = config.clientSecret,
                code = authorizationCode,
                redirectUri = config.redirectUri,
                grantType = GRANT_TYPE_AUTH_CODE,
                codeVerifier = codeVerifier
            )
            
            val response = withContext(Dispatchers.Default) {
                httpClient.post(TOKEN_URL) {
                    setBody(tokenRequest.toFormData())
                    header("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            
            if (response.status.isSuccess) {
                val tokenResponse = json.decodeFromString<LinkedInTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully exchanged code for LinkedIn tokens")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<LinkedInErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to exchange code for LinkedIn tokens: ${errorResponse.error}")
                
                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("linkedin", "Exception during LinkedIn token exchange", e)
            OAuthResult.Failure(
                OAuthError.fromOAuthResponse(
                    error = "token_exchange_failed",
                    errorDescription = e.message ?: "Unknown error"
                )
            )
        }
    }
    
    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        return try {
            logger.debug("oath", "Refreshing LinkedIn access token")
            
            val refreshRequest = LinkedInRefreshRequest(
                clientId = config.clientId,
                clientSecret = config.clientSecret,
                refreshToken = refreshToken,
                grantType = GRANT_TYPE_REFRESH
            )
            
            val response = withContext(Dispatchers.Default) {
                httpClient.post(TOKEN_URL) {
                    setBody(refreshRequest.toFormData())
                    header("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            
            if (response.status.isSuccess) {
                val tokenResponse = json.decodeFromString<LinkedInTokenResponse>(response.bodyAsText())
                logger.debug("oath", "Successfully refreshed LinkedIn access token")
                
                OAuthResult.Success(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = refreshToken, // Keep the original refresh token
                    expiresIn = tokenResponse.expiresIn,
                    tokenType = tokenResponse.tokenType,
                    scope = tokenResponse.scope
                )
            } else {
                val errorResponse = json.decodeFromString<LinkedInErrorResponse>(response.bodyAsText())
                logger.error("oath", "Failed to refresh LinkedIn access token: ${errorResponse.error}")
                
                OAuthResult.Failure(
                    OAuthError.networkError(
                        message = "Token refresh failed: ${errorResponse.error} - ${errorResponse.errorDescription}",
                        cause = null
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("linkedin", "Exception during LinkedIn token refresh", e)
            OAuthResult.Failure(
                OAuthError.networkError(
                    message = e.message ?: "Unknown error during token refresh",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        return try {
            logger.debug("oath", "Fetching LinkedIn user info")
            
            // Fetch user profile
            val userResponse = withContext(Dispatchers.Default) {
                httpClient.get(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/json")
                }
            }
            
            if (userResponse.status.isSuccess) {
                val userInfo = json.decodeFromString<LinkedInUserInfo>(userResponse.bodyAsText())
                
                // Fetch user email
                val emailResponse = withContext(Dispatchers.Default) {
                    httpClient.get(USER_EMAIL_URL) {
                        header("Authorization", "Bearer $accessToken")
                        header("Accept", "application/json")
                    }
                }
                
                val email = if (emailResponse.status.isSuccess) {
                    val emailData = json.decodeFromString<LinkedInEmailResponse>(emailResponse.bodyAsText())
                    emailData.elements.firstOrNull()?.handle?.emailAddress
                } else {
                    null
                }
                
                logger.debug("oath", "Successfully fetched LinkedIn user info: ${userInfo.localizedFirstName}")
                
                OAuthResult.Success(
                    accessToken = accessToken,
                    refreshToken = null,
                    expiresIn = null,
                    userInfo = OAuthUserInfo(
                        id = userInfo.id,
                        email = email,
                        name = "${userInfo.localizedFirstName} ${userInfo.localizedLastName}",
                        givenName = userInfo.localizedFirstName,
                        familyName = userInfo.localizedLastName,
                        displayName = "${userInfo.localizedFirstName} ${userInfo.localizedLastName}",
                        picture = null, // LinkedIn doesn't provide profile picture in basic profile
                        locale = userInfo.preferredLocale?.language,
                        emailVerified = true, // LinkedIn accounts are verified
                        provider = "linkedin",
                        providerId = userInfo.id
                    )
                )
            } else {
                val errorResponse = json.decodeFromString<LinkedInErrorResponse>(userResponse.bodyAsText())
                logger.error("oath", "Failed to fetch LinkedIn user info: ${errorResponse.error}")
                
                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = errorResponse.error,
                        errorDescription = errorResponse.errorDescription
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("linkedin", "Exception during LinkedIn user info fetch", e)
            OAuthResult.Failure(
                OAuthError.fromOAuthResponse(
                    error = "user_info_fetch_failed",
                    errorDescription = e.message ?: "Unknown error"
                )
            )
        }
    }
    
    override suspend fun revokeToken(token: String): Boolean {
        return try {
            logger.debug("oath", "Revoking LinkedIn OAuth token")
            
            // LinkedIn doesn't have a standard token revocation endpoint
            // The token will expire naturally based on the expires_in value
            logger.warn("linkedin", "LinkedIn OAuth does not support token revocation. Token will expire naturally.")
            
            // Return true to indicate "success" since we can't actually revoke
            true
        } catch (e: Exception) {
            logger.error("linkedin", "Exception during LinkedIn token revocation", e)
            false
        }
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        return try {
            logger.debug("oath", "Validating LinkedIn OAuth token")
            
            val response = withContext(Dispatchers.Default) {
                httpClient.get(USER_INFO_URL) {
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/json")
                }
            }
            
            val isValid = response.status.isSuccess
            logger.debug("oath", "LinkedIn OAuth token validation result: $isValid")
            
            isValid
        } catch (e: Exception) {
            logger.error("linkedin", "Exception during LinkedIn token validation", e)
            false
        }
    }
    
    // Data classes for LinkedIn OAuth API
    
    @Serializable
    private data class LinkedInTokenRequest(
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
    private data class LinkedInRefreshRequest(
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
    private data class LinkedInTokenResponse(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresIn: Long,
        val tokenType: String = "bearer",
        val scope: String = ""
    )
    
    @Serializable
    private data class LinkedInUserInfo(
        val id: String,
        val localizedFirstName: String,
        val localizedLastName: String,
        val preferredLocale: LinkedInLocale? = null,
        val profilePicture: LinkedInProfilePicture? = null
    )
    
    @Serializable
    private data class LinkedInLocale(
        val country: String,
        val language: String
    )
    
    @Serializable
    private data class LinkedInProfilePicture(
        val displayImage: String? = null
    )
    
    @Serializable
    private data class LinkedInEmailResponse(
        val elements: List<LinkedInEmailElement>
    )
    
    @Serializable
    private data class LinkedInEmailElement(
        val handle: LinkedInEmailHandle
    )
    
    @Serializable
    private data class LinkedInEmailHandle(
        val emailAddress: String
    )
    
    @Serializable
    private data class LinkedInErrorResponse(
        val error: String,
        val errorDescription: String? = null,
        val status: Int? = null,
        val message: String? = null
    )
}