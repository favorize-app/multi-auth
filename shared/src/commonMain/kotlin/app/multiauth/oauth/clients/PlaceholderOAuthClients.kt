package app.multiauth.oauth.clients

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
 * Placeholder OAuth client implementations for providers that need custom implementation.
 * These are basic implementations that should be enhanced with real API integration.
 */

// Twitch OAuth Client
class TwitchOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: app.multiauth.oauth.HttpClient,
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
        logger.debug("oath", "Generated Twitch OAuth authorization URL: $authUrl")
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

// Reddit OAuth Client
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
            append("&redirect_uri=${config.redirectUri}")
            append("&response_type=code")
            append("&scope=${config.scopes.ifEmpty { "identity read" }}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=$codeChallengeMethod")
            append("&duration=permanent")
        }
        
        val authUrl = "https://www.reddit.com/api/v1/authorize$params"
        logger.debug("oath", "Generated Reddit OAuth authorization URL: $authUrl")
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
        val credentials = "$username:$password"
        return credentials.encodeToByteArray().encodeBase64()
    }
    
    /**
     * Simple Base64 encoding for multiplatform compatibility.
     */
    private fun ByteArray.encodeBase64(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val result = StringBuilder()
        
        var i = 0
        while (i < size) {
            val b1 = this[i].toInt() and 0xFF
            val b2 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
            val b3 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0
            
            val bitmap = (b1 shl 16) or (b2 shl 8) or b3
            
            result.append(chars[(bitmap shr 18) and 0x3F])
            result.append(chars[(bitmap shr 12) and 0x3F])
            result.append(if (i + 1 < size) chars[(bitmap shr 6) and 0x3F] else '=')
            result.append(if (i + 2 < size) chars[bitmap and 0x3F] else '=')
            
            i += 3
        }
        
        return result.toString()
    }
}

// Steam OAuth Client
class SteamOAuthClient(
    private val config: OAuthConfig,
    private val httpClient: HttpClient,
    override val logger: Logger
) : OAuthClient {

    override suspend fun getAuthorizationUrl(
        state: String,
        codeChallenge: String,
        codeChallengeMethod: String
    ): String {
        // Steam uses OpenID, not standard OAuth 2.0
        logger.warn("general", "Steam uses OpenID, not OAuth 2.0 - using placeholder")
        return "https://steamcommunity.com/openid/login?openid.ns=http://specs.openid.net/auth/2.0&openid.mode=checkid_setup&openid.return_to=${config.redirectUri}&openid.realm=${config.redirectUri}&openid.identity=http://specs.openid.net/auth/2.0/identifier_select&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select"
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        logger.warn("oath", "Steam OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.fromOAuthResponse(
                error = "not_implemented",
                errorDescription = "Steam OAuth client not fully implemented"
            )
        )
    }
    
    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        logger.warn("oath", "Steam OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.networkError(
                message = "Steam OAuth client not fully implemented"
            )
        )
    }
    
    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        logger.warn("oath", "Steam OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.fromOAuthResponse(
                error = "not_implemented",
                errorDescription = "Steam OAuth client not fully implemented"
            )
        )
    }
    
    override suspend fun revokeToken(token: String): Boolean {
        logger.warn("oath", "Steam OAuth client not fully implemented - using placeholder")
        return false
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        logger.warn("placeholder", "Steam OAuth client not fully implemented - using placeholder")
        return false
    }
}

// Epic Games OAuth Client
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
            append("&scope=${config.scopes.ifEmpty { "basic_profile friends_list" }}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=$codeChallengeMethod")
        }
        
        val authUrl = "https://www.epicgames.com/id/authorize$params"
        logger.debug("oath", "Generated Epic Games OAuth authorization URL: $authUrl")
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
        val credentials = "$username:$password"
        return credentials.encodeToByteArray().encodeBase64()
    }
    
    /**
     * Simple Base64 encoding for multiplatform compatibility.
     */
    private fun ByteArray.encodeBase64(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val result = StringBuilder()
        
        var i = 0
        while (i < size) {
            val b1 = this[i].toInt() and 0xFF
            val b2 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
            val b3 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0
            
            val bitmap = (b1 shl 16) or (b2 shl 8) or b3
            
            result.append(chars[(bitmap shr 18) and 0x3F])
            result.append(chars[(bitmap shr 12) and 0x3F])
            result.append(if (i + 1 < size) chars[(bitmap shr 6) and 0x3F] else '=')
            result.append(if (i + 2 < size) chars[bitmap and 0x3F] else '=')
            
            i += 3
        }
        
        return result.toString()
    }
}

// Spotify OAuth Client
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
            append("&redirect_uri=${config.redirectUri}")
            append("&response_type=code")
            append("&scope=${config.scopes.ifEmpty { "user-read-email user-read-private" }}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=$codeChallengeMethod")
        }
        
        val authUrl = "https://accounts.spotify.com/authorize$params"
        logger.debug("oath", "Generated Spotify OAuth authorization URL: $authUrl")
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
        val credentials = "$username:$password"
        return credentials.encodeToByteArray().encodeBase64()
    }
    
    /**
     * Simple Base64 encoding for multiplatform compatibility.
     */
    private fun ByteArray.encodeBase64(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val result = StringBuilder()
        
        var i = 0
        while (i < size) {
            val b1 = this[i].toInt() and 0xFF
            val b2 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
            val b3 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0
            
            val bitmap = (b1 shl 16) or (b2 shl 8) or b3
            
            result.append(chars[(bitmap shr 18) and 0x3F])
            result.append(chars[(bitmap shr 12) and 0x3F])
            result.append(if (i + 1 < size) chars[(bitmap shr 6) and 0x3F] else '=')
            result.append(if (i + 2 < size) chars[bitmap and 0x3F] else '=')
            
            i += 3
        }
        
        return result.toString()
    }
}

// Facebook OAuth Client
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
            append("&scope=${config.scopes.ifEmpty { "email public_profile" }}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=$codeChallengeMethod")
        }
        
        val authUrl = "https://www.facebook.com/v18.0/dialog/oauth$params"
        logger.debug("oath", "Generated Facebook OAuth authorization URL: $authUrl")
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

// Apple OAuth Client
class AppleOAuthClient(
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
            append("&scope=${config.scopes.ifEmpty { "name email" }}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=$codeChallengeMethod")
            append("&response_mode=form_post")
        }
        
        val authUrl = "https://appleid.apple.com/auth/authorize$params"
        logger.debug("oath", "Generated Apple OAuth authorization URL: $authUrl")
        return authUrl
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        logger.warn("oath", "Apple OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.fromOAuthResponse(
                error = "not_implemented",
                errorDescription = "Apple OAuth client not fully implemented"
            )
        )
    }
    
    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        logger.warn("oath", "Apple OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.networkError(
                message = "Apple OAuth client not fully implemented"
            )
        )
    }
    
    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        logger.warn("oath", "Apple OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.fromOAuthResponse(
                error = "not_implemented",
                errorDescription = "Apple OAuth client not fully implemented"
            )
        )
    }
    
    override suspend fun revokeToken(token: String): Boolean {
        logger.warn("oath", "Apple OAuth client not fully implemented - using placeholder")
        return false
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        logger.warn("placeholder", "Apple OAuth client not fully implemented - using placeholder")
        return false
    }
}

// Data classes for Twitch API responses
@Serializable
private data class TwitchTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val scope: List<String>? = null
)

@Serializable
private data class TwitchUserResponse(
    val data: List<TwitchUser>
)

@Serializable
private data class TwitchUser(
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

// Data classes for Reddit API responses
@Serializable
private data class RedditTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val scope: String? = null
)

@Serializable
private data class RedditUser(
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

// Data classes for Facebook API responses
@Serializable
private data class FacebookTokenResponse(
    val access_token: String,
    val expires_in: Long? = null,
    val token_type: String? = null
)

@Serializable
private data class FacebookUser(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val picture: FacebookPicture? = null
)

@Serializable
private data class FacebookPicture(
    val data: FacebookPictureData? = null
)

@Serializable
private data class FacebookPictureData(
    val url: String? = null,
    val is_silhouette: Boolean? = null,
    val height: Int? = null,
    val width: Int? = null
)

// Data classes for Epic Games API responses
@Serializable
private data class EpicTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val scope: String? = null,
    val account_id: String? = null
)

@Serializable
private data class EpicUser(
    val accountId: String,
    val displayName: String? = null,
    val email: String? = null,
    val externalAuths: Map<String, EpicExternalAuth>? = null
)

@Serializable
private data class EpicExternalAuth(
    val accountId: String? = null,
    val type: String? = null
)

// Data classes for Spotify API responses
@Serializable
private data class SpotifyTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val scope: String? = null
)

@Serializable
private data class SpotifyUser(
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
private data class SpotifyFollowers(
    val total: Int? = null
)

@Serializable
private data class SpotifyImage(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)