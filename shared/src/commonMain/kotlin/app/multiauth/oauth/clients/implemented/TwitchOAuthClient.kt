@file:OptIn(ExperimentalTime::class)

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
import kotlin.time.ExperimentalTime

/**
 * Twitch OAuth client implementation.
 * Provides complete OAuth 2.0 integration with Twitch's API.
 */
class TwitchOAuthClient(
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
            append("&scope=${config.scopes.ifEmpty { "user:read:email" }}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=$codeChallengeMethod")
        }

        val authUrl = "https://id.twitch.tv/oauth2/authorize$params"
        logger.debug("oauth", "Generated Twitch OAuth authorization URL: $authUrl")
        return authUrl
    }

    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        logger.debug("oauth", "Exchanging Twitch authorization code for tokens")

        return try {
            val tokenRequest = mapOf(
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "code" to authorizationCode,
                "grant_type" to "authorization_code",
                "redirect_uri" to config.redirectUri,
                "code_verifier" to codeVerifier
            )

            val response = httpClient.post("https://id.twitch.tv/oauth2/token") {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(tokenRequest.entries.joinToString("&") { "${it.key}=${it.value}" })
            }

            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<TwitchTokenResponse>(response.bodyAsText())

                // Get user info
                val userInfo = getTwitchUserInfo(tokenData.access_token)

                OAuthResult.Success(
                    accessToken = tokenData.access_token,
                    refreshToken = tokenData.refresh_token,
                    expiresIn = tokenData.expires_in,
                    tokenType = tokenData.token_type ?: "Bearer",
                    scope = tokenData.scope?.joinToString(" "),
                    userInfo = userInfo
                )
            } else {
                val errorBody = response.bodyAsText()
                logger.error("oauth", "Twitch token exchange failed: $errorBody")

                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "token_exchange_failed",
                        errorDescription = "Failed to exchange code for tokens: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("oauth", "Twitch OAuth error", e)
            OAuthResult.Failure(
                OAuthError.networkError("Token exchange failed: ${e.message}", e)
            )
        }
    }

    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        logger.debug("oauth", "Refreshing Twitch access token")

        return try {
            val refreshRequest = mapOf(
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken
            )

            val response = httpClient.post("https://id.twitch.tv/oauth2/token") {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(refreshRequest.entries.joinToString("&") { "${it.key}=${it.value}" })
            }

            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<TwitchTokenResponse>(response.bodyAsText())

                OAuthResult.Success(
                    accessToken = tokenData.access_token,
                    refreshToken = tokenData.refresh_token ?: refreshToken,
                    expiresIn = tokenData.expires_in,
                    tokenType = tokenData.token_type ?: "Bearer",
                    scope = tokenData.scope?.joinToString(" ")
                )
            } else {
                val errorBody = response.bodyAsText()
                logger.error("oauth", "Twitch token refresh failed: $errorBody")

                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "token_refresh_failed",
                        errorDescription = "Failed to refresh token: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("oauth", "Twitch token refresh error", e)
            OAuthResult.Failure(
                OAuthError.networkError("Token refresh failed: ${e.message}", e)
            )
        }
    }

    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        logger.debug("oauth", "Getting Twitch user info")

        return try {
            val response = httpClient.get("https://api.twitch.tv/helix/users") {
                header("Authorization", "Bearer $accessToken")
                header("Client-Id", config.clientId)
            }

            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<TwitchUserResponse>(response.bodyAsText())
                val user = userData.data.firstOrNull()

                if (user != null) {
                    val userInfo = OAuthUserInfo(
                        id = user.id,
                        email = user.email,
                        name = user.display_name,
                        displayName = user.display_name,
                        picture = user.profile_image_url,
                        provider = "twitch",
                        providerId = user.id
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
                            errorDescription = "No user data returned from Twitch API"
                        )
                    )
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("oauth", "Twitch user info failed: $errorBody")

                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "user_info_failed",
                        errorDescription = "Failed to get user info: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("oauth", "Twitch user info error", e)
            OAuthResult.Failure(
                OAuthError.networkError("User info request failed: ${e.message}", e)
            )
        }
    }

    override suspend fun revokeToken(token: String): Boolean {
        logger.debug("oauth", "Revoking Twitch token")

        return try {
            val response = httpClient.post("https://id.twitch.tv/oauth2/revoke") {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody("client_id=${config.clientId}&token=$token")
            }

            response.status.isSuccess

        } catch (e: Exception) {
            logger.error("oauth", "Twitch token revocation error", e)
            false
        }
    }

    override suspend fun validateToken(accessToken: String): Boolean {
        logger.debug("oauth", "Validating Twitch token")

        return try {
            val response = httpClient.get("https://id.twitch.tv/oauth2/validate") {
                header("Authorization", "OAuth $accessToken")
            }

            response.status.isSuccess

        } catch (e: Exception) {
            logger.error("oauth", "Twitch token validation error", e)
            false
        }
    }

    // Helper method to get user info during token exchange
    private suspend fun getTwitchUserInfo(accessToken: String): OAuthUserInfo? {
        return try {
            val response = httpClient.get("https://api.twitch.tv/helix/users") {
                header("Authorization", "Bearer $accessToken")
                header("Client-Id", config.clientId)
            }

            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<TwitchUserResponse>(response.bodyAsText())
                val user = userData.data.firstOrNull()

                user?.let {
                    OAuthUserInfo(
                        id = it.id,
                        email = it.email,
                        name = it.display_name,
                        displayName = it.display_name,
                        picture = it.profile_image_url,
                        provider = "twitch",
                        providerId = it.id
                    )
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("oauth", "Failed to get Twitch user info during token exchange", e)
            null
        }
    }
}

/**
 * Twitch API response data classes.
 */
@Serializable
data class TwitchTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val scope: List<String>? = null
)

@Serializable
data class TwitchUserResponse(
    val data: List<TwitchUser>
)

@Serializable
data class TwitchUser(
    val id: String,
    val login: String,
    val display_name: String,
    val email: String? = null,
    val profile_image_url: String? = null,
    val broadcaster_type: String? = null,
    val description: String? = null,
    val view_count: Int? = null,
    val created_at: String? = null
)
