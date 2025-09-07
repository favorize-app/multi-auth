package app.multiauth.oauth.clients.implemented

import app.multiauth.oauth.HttpClient
import app.multiauth.oauth.OAuthClient
import app.multiauth.oauth.OAuthConfig
import app.multiauth.oauth.OAuthResult
import app.multiauth.oauth.OAuthError
import app.multiauth.oauth.OAuthUserInfo
import app.multiauth.util.Logger
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Facebook OAuth client implementation.
 * Provides complete OAuth 2.0 integration with Facebook's Graph API.
 */
class FacebookOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: HttpClient,
    override val logger: Logger
) : OAuthClient {

    override suspend fun getAuthorizationUrl(
        state: String,
        codeChallenge: String,
        codeChallengeMethod: String
    ): String {
        val params = buildString {
            append("?client_id=${config.clientId}")
            append("&redirect_uri=${config.redirectUri}")
            append("&response_type=code")
            append("&scope=${config.scopes.ifEmpty { "email" }}")
            append("&state=$state")
        }
        
        val authUrl = "https://www.facebook.com/v18.0/dialog/oauth$params"
        logger.debug("oauth", "Generated Facebook OAuth authorization URL: $authUrl")
        return authUrl
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        logger.debug("oauth", "Exchanging Facebook authorization code for tokens")
        
        return try {
            val tokenRequest = "grant_type=authorization_code" +
                "&client_id=${config.clientId}" +
                "&client_secret=${config.clientSecret}" +
                "&redirect_uri=${config.redirectUri}" +
                "&code=$authorizationCode"
            
            val response = httpClient.post("https://graph.facebook.com/v18.0/oauth/access_token") {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(tokenRequest)
            }
            
            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<FacebookTokenResponse>(response.bodyAsText())
                
                // Get user info
                val userInfo = getFacebookUserInfo(tokenData.access_token)
                
                OAuthResult.Success(
                    accessToken = tokenData.access_token,
                    refreshToken = null, // Facebook doesn't use refresh tokens
                    expiresIn = tokenData.expires_in,
                    tokenType = tokenData.token_type ?: "Bearer",
                    scope = null,
                    userInfo = userInfo
                )
            } else {
                val errorBody = response.bodyAsText()
                logger.error("oauth", "Facebook token exchange failed: $errorBody")
                
                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "token_exchange_failed",
                        errorDescription = "Failed to exchange code for tokens: HTTP ${response.status}"
                    )
                )
            }
            
        } catch (e: Exception) {
            logger.error("oauth", "Facebook OAuth error", e)
            OAuthResult.Failure(
                OAuthError.networkError("Token exchange failed: ${e.message}", e)
            )
        }
    }
    
    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        logger.debug("oauth", "Facebook does not support refresh tokens")
        
        return OAuthResult.Failure(
            OAuthError.fromOAuthResponse(
                error = "refresh_not_supported",
                errorDescription = "Facebook OAuth does not support refresh tokens"
            )
        )
    }
    
    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        logger.debug("oauth", "Getting Facebook user info")
        
        return try {
            val response = httpClient.get("https://graph.facebook.com/me?fields=id,email,name,picture") {
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<FacebookUser>(response.bodyAsText())
                
                val userInfo = OAuthUserInfo(
                    id = userData.id,
                    email = userData.email,
                    name = userData.name,
                    displayName = userData.name,
                    picture = userData.picture?.data?.url,
                    provider = "facebook",
                    providerId = userData.id
                )
                
                OAuthResult.Success(
                    accessToken = accessToken,
                    refreshToken = null,
                    expiresIn = null,
                    userInfo = userInfo
                )
            } else {
                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "user_info_failed",
                        errorDescription = "Failed to get user info: HTTP ${response.status}"
                    )
                )
            }
            
        } catch (e: Exception) {
            OAuthResult.Failure(
                OAuthError.networkError("User info request failed: ${e.message}", e)
            )
        }
    }
    
    override suspend fun revokeToken(token: String): Boolean {
        logger.debug("oauth", "Revoking Facebook token")
        
        return try {
            val response = httpClient.post("https://graph.facebook.com/me/permissions") {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody("method=delete")
            }
            
            response.status.isSuccess
            
        } catch (e: Exception) {
            logger.error("oauth", "Facebook token revocation error", e)
            false
        }
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        logger.debug("oauth", "Validating Facebook token")
        
        return try {
            val response = httpClient.get("https://graph.facebook.com/me") {
                header("Authorization", "Bearer $accessToken")
            }
            
            response.status.isSuccess
            
        } catch (e: Exception) {
            logger.error("oauth", "Facebook token validation error", e)
            false
        }
    }
    
    // Helper method to get user info during token exchange
    private suspend fun getFacebookUserInfo(accessToken: String): OAuthUserInfo? {
        return try {
            val response = httpClient.get("https://graph.facebook.com/me?fields=id,email,name,picture") {
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<FacebookUser>(response.bodyAsText())
                
                OAuthUserInfo(
                    id = userData.id,
                    email = userData.email,
                    name = userData.name,
                    displayName = userData.name,
                    picture = userData.picture?.data?.url,
                    provider = "facebook",
                    providerId = userData.id
                )
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("oauth", "Failed to get Facebook user info during token exchange", e)
            null
        }
    }
}

/**
 * Facebook API response data classes.
 */
@Serializable
data class FacebookTokenResponse(
    val access_token: String,
    val expires_in: Long? = null,
    val token_type: String? = null
)

@Serializable
data class FacebookUser(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val picture: FacebookPicture? = null
)

@Serializable
data class FacebookPicture(
    val data: FacebookPictureData? = null
)

@Serializable
data class FacebookPictureData(
    val url: String? = null,
    val is_silhouette: Boolean? = null,
    val height: Int? = null,
    val width: Int? = null
)