@file:OptIn(ExperimentalTime::class)

package app.multiauth.oauth.clients.implemented

import app.multiauth.oauth.HttpClient
import app.multiauth.oauth.OAuthClient
import app.multiauth.oauth.OAuthConfig
import app.multiauth.oauth.OAuthResult
import app.multiauth.oauth.OAuthError
import app.multiauth.oauth.OAuthUserInfo
import app.multiauth.util.Logger
import app.multiauth.util.Base64Util
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

/**
 * Reddit OAuth client implementation.
 * Provides complete OAuth 2.0 integration with Reddit's API.
 */
class RedditOAuthClient(
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
            append("&response_type=code")
            append("&state=$state")
            append("&redirect_uri=${config.redirectUri}")
            append("&duration=permanent")
            append("&scope=${config.scopes.ifEmpty { "identity" }}")
        }

        val authUrl = "https://www.reddit.com/api/v1/authorize$params"
        logger.debug("oauth", "Generated Reddit OAuth authorization URL: $authUrl")
        return authUrl
    }

    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        logger.debug("oauth", "Exchanging Reddit authorization code for tokens")

        return try {
            val tokenRequest = "grant_type=authorization_code" +
                "&code=$authorizationCode" +
                "&redirect_uri=${config.redirectUri}"

            val response = httpClient.post("https://www.reddit.com/api/v1/access_token") {
                header("Authorization", "Basic ${encodeBasicAuth(config.clientId, config.clientSecret)}")
                header("Content-Type", "application/x-www-form-urlencoded")
                header("User-Agent", "MultiAuth/1.0.0")
                setBody(tokenRequest)
            }

            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<RedditTokenResponse>(response.bodyAsText())

                // Get user info
                val userInfo = getRedditUserInfo(tokenData.access_token)

                OAuthResult.Success(
                    accessToken = tokenData.access_token,
                    refreshToken = tokenData.refresh_token,
                    expiresIn = tokenData.expires_in,
                    tokenType = tokenData.token_type ?: "Bearer",
                    scope = tokenData.scope,
                    userInfo = userInfo
                )
            } else {
                val errorBody = response.bodyAsText()
                logger.error("oauth", "Reddit token exchange failed: $errorBody")

                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "token_exchange_failed",
                        errorDescription = "Failed to exchange code for tokens: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("oauth", "Reddit OAuth error", e)
            OAuthResult.Failure(
                OAuthError.networkError("Token exchange failed: ${e.message}", e)
            )
        }
    }

    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        logger.debug("oauth", "Refreshing Reddit access token")

        return try {
            val refreshRequest = "grant_type=refresh_token&refresh_token=$refreshToken"

            val response = httpClient.post("https://www.reddit.com/api/v1/access_token") {
                header("Authorization", "Basic ${encodeBasicAuth(config.clientId, config.clientSecret)}")
                header("Content-Type", "application/x-www-form-urlencoded")
                header("User-Agent", "MultiAuth/1.0.0")
                setBody(refreshRequest)
            }

            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<RedditTokenResponse>(response.bodyAsText())

                OAuthResult.Success(
                    accessToken = tokenData.access_token,
                    refreshToken = tokenData.refresh_token ?: refreshToken,
                    expiresIn = tokenData.expires_in,
                    tokenType = tokenData.token_type ?: "Bearer",
                    scope = tokenData.scope
                )
            } else {
                val errorBody = response.bodyAsText()
                logger.error("oauth", "Reddit token refresh failed: $errorBody")

                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "token_refresh_failed",
                        errorDescription = "Failed to refresh token: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("oauth", "Reddit token refresh error", e)
            OAuthResult.Failure(
                OAuthError.networkError("Token refresh failed: ${e.message}", e)
            )
        }
    }

    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        logger.debug("oauth", "Getting Reddit user info")

        return try {
            val response = httpClient.get("https://oauth.reddit.com/api/v1/me") {
                header("Authorization", "Bearer $accessToken")
                header("User-Agent", "MultiAuth/1.0.0")
            }

            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<RedditUser>(response.bodyAsText())

                val userInfo = OAuthUserInfo(
                    id = userData.id,
                    email = userData.email,
                    name = userData.name,
                    displayName = userData.name,
                    picture = userData.icon_img?.takeIf { it.isNotEmpty() },
                    provider = "reddit",
                    providerId = userData.id
                )

                OAuthResult.Success(
                    accessToken = accessToken,
                    refreshToken = null,
                    expiresIn = null,
                    userInfo = userInfo
                )
            } else {
                val errorBody = response.bodyAsText()
                logger.error("oauth", "Reddit user info failed: $errorBody")

                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "user_info_failed",
                        errorDescription = "Failed to get user info: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("oauth", "Reddit user info error", e)
            OAuthResult.Failure(
                OAuthError.networkError("User info request failed: ${e.message}", e)
            )
        }
    }

    override suspend fun revokeToken(token: String): Boolean {
        logger.debug("oauth", "Revoking Reddit token")

        return try {
            val response = httpClient.post("https://www.reddit.com/api/v1/revoke_token") {
                header("Authorization", "Basic ${encodeBasicAuth(config.clientId, config.clientSecret)}")
                header("Content-Type", "application/x-www-form-urlencoded")
                header("User-Agent", "MultiAuth/1.0.0")
                setBody("token=$token")
            }

            response.status.isSuccess

        } catch (e: Exception) {
            logger.error("oauth", "Reddit token revocation error", e)
            false
        }
    }

    override suspend fun validateToken(accessToken: String): Boolean {
        logger.debug("oauth", "Validating Reddit token")

        return try {
            val response = httpClient.get("https://oauth.reddit.com/api/v1/me") {
                header("Authorization", "Bearer $accessToken")
                header("User-Agent", "MultiAuth/1.0.0")
            }

            response.status.isSuccess

        } catch (e: Exception) {
            logger.error("oauth", "Reddit token validation error", e)
            false
        }
    }

    // Helper method to get user info during token exchange
    private suspend fun getRedditUserInfo(accessToken: String): OAuthUserInfo? {
        return try {
            val response = httpClient.get("https://oauth.reddit.com/api/v1/me") {
                header("Authorization", "Bearer $accessToken")
                header("User-Agent", "MultiAuth/1.0.0")
            }

            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<RedditUser>(response.bodyAsText())

                OAuthUserInfo(
                    id = userData.id,
                    email = userData.email,
                    name = userData.name,
                    displayName = userData.name,
                    picture = userData.icon_img?.takeIf { it.isNotEmpty() },
                    provider = "reddit",
                    providerId = userData.id
                )
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("oauth", "Failed to get Reddit user info during token exchange", e)
            null
        }
    }

    // Helper method for basic auth encoding
    private fun encodeBasicAuth(username: String, password: String): String {
        return Base64Util.encodeBasicAuth(username, password)
    }

}

/**
 * Reddit API response data classes.
 */
@Serializable
data class RedditTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val scope: String? = null
)

@Serializable
data class RedditUser(
    val id: String,
    val name: String,
    val email: String? = null,
    val icon_img: String? = null,
    val created: Double? = null,
    val created_utc: Double? = null,
    val link_karma: Int? = null,
    val comment_karma: Int? = null,
    val is_gold: Boolean? = null,
    val is_mod: Boolean? = null,
    val verified: Boolean? = null
)
