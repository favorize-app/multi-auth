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
 * Spotify OAuth client implementation.
 * Provides complete OAuth 2.0 integration with Spotify's Web API.
 */
class SpotifyOAuthClient(
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
            append("&redirect_uri=${config.redirectUri}")
            append("&scope=${config.scopes.ifEmpty { "user-read-email user-read-private" }}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=$codeChallengeMethod")
        }

        val authUrl = "https://accounts.spotify.com/authorize$params"
        logger.debug("oauth", "Generated Spotify OAuth authorization URL: $authUrl")
        return authUrl
    }

    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        logger.debug("oauth", "Exchanging Spotify authorization code for tokens")

        return try {
            val tokenRequest = "grant_type=authorization_code" +
                "&code=$authorizationCode" +
                "&redirect_uri=${config.redirectUri}" +
                "&code_verifier=$codeVerifier"

            val response = httpClient.post("https://accounts.spotify.com/api/token") {
                header("Authorization", "Basic ${encodeBasicAuth(config.clientId, config.clientSecret)}")
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(tokenRequest)
            }

            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<SpotifyTokenResponse>(response.bodyAsText())

                // Get user info
                val userInfo = getSpotifyUserInfo(tokenData.access_token)

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
                logger.error("oauth", "Spotify token exchange failed: $errorBody")

                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "token_exchange_failed",
                        errorDescription = "Failed to exchange code for tokens: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("oauth", "Spotify OAuth error", e)
            OAuthResult.Failure(
                OAuthError.networkError("Token exchange failed: ${e.message}", e)
            )
        }
    }

    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        logger.debug("oauth", "Refreshing Spotify access token")

        return try {
            val refreshRequest = "grant_type=refresh_token&refresh_token=$refreshToken"

            val response = httpClient.post("https://accounts.spotify.com/api/token") {
                header("Authorization", "Basic ${encodeBasicAuth(config.clientId, config.clientSecret)}")
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(refreshRequest)
            }

            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<SpotifyTokenResponse>(response.bodyAsText())

                OAuthResult.Success(
                    accessToken = tokenData.access_token,
                    refreshToken = tokenData.refresh_token ?: refreshToken,
                    expiresIn = tokenData.expires_in,
                    tokenType = tokenData.token_type ?: "Bearer",
                    scope = tokenData.scope
                )
            } else {
                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "token_refresh_failed",
                        errorDescription = "Failed to refresh token: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            OAuthResult.Failure(
                OAuthError.networkError("Token refresh failed: ${e.message}", e)
            )
        }
    }

    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        logger.debug("oauth", "Getting Spotify user info")

        return try {
            val response = httpClient.get("https://api.spotify.com/v1/me") {
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<SpotifyUser>(response.bodyAsText())

                val userInfo = OAuthUserInfo(
                    id = userData.id,
                    email = userData.email,
                    name = userData.display_name,
                    displayName = userData.display_name,
                    picture = userData.images?.firstOrNull()?.url,
                    provider = "spotify",
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
        logger.debug("oauth", "Revoking Spotify token")

        return try {
            // Spotify doesn't have a standard revoke endpoint
            // Token revocation happens automatically on expiration
            logger.info("oauth", "Spotify token revocation not supported - tokens expire automatically")
            true

        } catch (e: Exception) {
            logger.error("oauth", "Spotify token revocation error", e)
            false
        }
    }

    override suspend fun validateToken(accessToken: String): Boolean {
        logger.debug("oauth", "Validating Spotify token")

        return try {
            val response = httpClient.get("https://api.spotify.com/v1/me") {
                header("Authorization", "Bearer $accessToken")
            }

            response.status.isSuccess

        } catch (e: Exception) {
            logger.error("oauth", "Spotify token validation error", e)
            false
        }
    }

    // Helper method to get user info during token exchange
    private suspend fun getSpotifyUserInfo(accessToken: String): OAuthUserInfo? {
        return try {
            val response = httpClient.get("https://api.spotify.com/v1/me") {
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<SpotifyUser>(response.bodyAsText())

                OAuthUserInfo(
                    id = userData.id,
                    email = userData.email,
                    name = userData.display_name,
                    displayName = userData.display_name,
                    picture = userData.images?.firstOrNull()?.url,
                    provider = "spotify",
                    providerId = userData.id
                )
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("oauth", "Failed to get Spotify user info during token exchange", e)
            null
        }
    }

    // Helper method for basic auth encoding
    private fun encodeBasicAuth(username: String, password: String): String {
        return Base64Util.encodeBasicAuth(username, password)
    }

}

/**
 * Spotify API response data classes.
 */
@Serializable
data class SpotifyTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val scope: String? = null
)

@Serializable
data class SpotifyUser(
    val id: String,
    val display_name: String? = null,
    val email: String? = null,
    val country: String? = null,
    val followers: SpotifyFollowers? = null,
    val images: List<SpotifyImage>? = null,
    val product: String? = null,
    val type: String? = null,
    val uri: String? = null,
    val external_urls: Map<String, String>? = null
)

@Serializable
data class SpotifyFollowers(
    val total: Int? = null
)

@Serializable
data class SpotifyImage(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)
