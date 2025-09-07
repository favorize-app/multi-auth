package app.multiauth.oauth.clients.placeholders

import app.multiauth.oauth.HttpClient
import app.multiauth.oauth.OAuthClient
import app.multiauth.oauth.OAuthConfig
import app.multiauth.oauth.OAuthResult
import app.multiauth.oauth.OAuthError
import app.multiauth.util.Logger

/**
 * Steam OAuth client placeholder.
 * Steam uses OpenID Connect, not standard OAuth 2.0.
 * This requires a different implementation approach.
 */
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
        val params = buildString {
            append("?openid.ns=http://specs.openid.net/auth/2.0")
            append("&openid.mode=checkid_setup")
            append("&openid.return_to=${config.redirectUri}")
            append("&openid.realm=${config.redirectUri}")
            append("&openid.identity=http://specs.openid.net/auth/2.0/identifier_select")
            append("&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select")
        }
        
        val authUrl = "https://steamcommunity.com/openid/login$params"
        logger.debug("oauth", "Generated Steam OpenID authorization URL: $authUrl")
        logger.warn("general", "Steam uses OpenID, not OAuth 2.0 - placeholder implementation")
        return authUrl
    }

    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        logger.warn("oauth", "Steam OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.fromOAuthResponse(
                error = "not_implemented",
                errorDescription = "Steam uses OpenID Connect, requires custom implementation"
            )
        )
    }

    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        logger.warn("oauth", "Steam OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.networkError("Steam OpenID does not support refresh tokens")
        )
    }

    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        logger.warn("oauth", "Steam OAuth client not fully implemented - using placeholder")
        return OAuthResult.Failure(
            OAuthError.fromOAuthResponse(
                error = "not_implemented",
                errorDescription = "Steam OpenID user info requires custom implementation"
            )
        )
    }

    override suspend fun revokeToken(token: String): Boolean {
        logger.warn("oauth", "Steam OAuth client not fully implemented - using placeholder")
        return false
    }

    override suspend fun validateToken(accessToken: String): Boolean {
        logger.warn("placeholder", "Steam OAuth client not fully implemented - using placeholder")
        return false
    }
}