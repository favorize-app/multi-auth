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
 * Epic Games OAuth client implementation.
 * Provides complete OAuth 2.0 integration with Epic Games' API.
 */
class EpicGamesOAuthClient(
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
            append("&scope=${config.scopes.ifEmpty { "basic_profile" }}")
            append("&state=$state")
        }

        val authUrl = "https://www.epicgames.com/id/authorize$params"
        logger.debug("oauth", "Generated Epic Games OAuth authorization URL: $authUrl")
        return authUrl
    }

    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        logger.debug("oauth", "Exchanging Epic Games authorization code for tokens")

        return try {
            val tokenRequest = mapOf(
                "grant_type" to "authorization_code",
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "code" to authorizationCode,
                "redirect_uri" to config.redirectUri
            )

            val response = httpClient.post("https://api.epicgames.dev/epic/oauth/v1/token") {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(tokenRequest.entries.joinToString("&") { "${it.key}=${it.value}" })
            }

            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<EpicTokenResponse>(response.bodyAsText())

                // Get user info
                val userInfo = getEpicUserInfo(tokenData.access_token)

                OAuthResult.Success(
                    accessToken = tokenData.access_token,
                    refreshToken = tokenData.refresh_token,
                    expiresIn = tokenData.expires_in,
                    tokenType = tokenData.token_type ?: "Bearer",
                    scope = tokenData.scope,
                    userInfo = userInfo
                )
            } else {
                OAuthResult.Failure(
                    OAuthError.fromOAuthResponse(
                        error = "token_exchange_failed",
                        errorDescription = "Failed to exchange code for tokens: HTTP ${response.status}"
                    )
                )
            }

        } catch (e: Exception) {
            OAuthResult.Failure(
                OAuthError.networkError("Token exchange failed: ${e.message}", e)
            )
        }
    }

    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        logger.debug("oauth", "Refreshing Epic Games access token")

        return try {
            val refreshRequest = mapOf(
                "grant_type" to "refresh_token",
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "refresh_token" to refreshToken
            )

            val response = httpClient.post("https://api.epicgames.dev/epic/oauth/v1/token") {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(refreshRequest.entries.joinToString("&") { "${it.key}=${it.value}" })
            }

            if (response.status.isSuccess) {
                val tokenData = Json.decodeFromString<EpicTokenResponse>(response.bodyAsText())

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
        logger.debug("oauth", "Getting Epic Games user info")

        return try {
            val response = httpClient.get("https://api.epicgames.dev/epic/id/v1/accounts") {
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<List<EpicUser>>(response.bodyAsText())
                val user = userData.firstOrNull()

                if (user != null) {
                    val userInfo = OAuthUserInfo(
                        id = user.accountId,
                        email = user.email,
                        name = user.displayName,
                        displayName = user.displayName,
                        picture = null, // Epic doesn't provide profile pictures in this endpoint
                        provider = "epic",
                        providerId = user.accountId
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
                            errorDescription = "No user data returned from Epic API"
                        )
                    )
                }
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
        logger.debug("oauth", "Revoking Epic Games token")

        return try {
            val response = httpClient.post("https://api.epicgames.dev/epic/oauth/v1/revoke") {
                header("Authorization", "Basic ${encodeBasicAuth(config.clientId, config.clientSecret)}")
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody("token=$token")
            }

            response.status.isSuccess

        } catch (e: Exception) {
            logger.error("oauth", "Epic Games token revocation error", e)
            false
        }
    }

    override suspend fun validateToken(accessToken: String): Boolean {
        logger.debug("oauth", "Validating Epic Games token")

        return try {
            val response = httpClient.get("https://api.epicgames.dev/epic/id/v1/accounts") {
                header("Authorization", "Bearer $accessToken")
            }

            response.status.isSuccess

        } catch (e: Exception) {
            logger.error("oauth", "Epic Games token validation error", e)
            false
        }
    }

    // Helper method to get user info during token exchange
    private suspend fun getEpicUserInfo(accessToken: String): OAuthUserInfo? {
        return try {
            val response = httpClient.get("https://api.epicgames.dev/epic/id/v1/accounts") {
                header("Authorization", "Bearer $accessToken")
            }

            if (response.status.isSuccess) {
                val userData = Json.decodeFromString<List<EpicUser>>(response.bodyAsText())
                val user = userData.firstOrNull()

                user?.let {
                    OAuthUserInfo(
                        id = it.accountId,
                        email = it.email,
                        name = it.displayName,
                        displayName = it.displayName,
                        picture = null,
                        provider = "epic",
                        providerId = it.accountId
                    )
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("oauth", "Failed to get Epic user info during token exchange", e)
            null
        }
    }

    // Helper method for basic auth encoding
    private fun encodeBasicAuth(username: String, password: String): String {
        return Base64Util.encodeBasicAuth(username, password)
    }

}

/**
 * Epic Games API response data classes.
 */
@Serializable
data class EpicTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val scope: String? = null,
    val account_id: String? = null
)

@Serializable
data class EpicUser(
    val accountId: String,
    val displayName: String? = null,
    val email: String? = null,
    val externalAuths: Map<String, EpicExternalAuth>? = null
)

@Serializable
data class EpicExternalAuth(
    val accountId: String? = null,
    val type: String? = null
)
