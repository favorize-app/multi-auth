package app.multiauth.oauth

import app.multiauth.util.Logger
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for OAuth client implementations.
 * Provides methods for OAuth authentication flows.
 */
interface OAuthClient {
    
    val logger: Logger
    
    /**
     * Gets the authorization URL for the OAuth flow.
     * 
     * @param state State parameter for security
     * @param codeChallenge PKCE code challenge
     * @param codeChallengeMethod PKCE code challenge method (usually "S256")
     * @return The authorization URL
     */
    suspend fun getAuthorizationUrl(
        state: String,
        codeChallenge: String,
        codeChallengeMethod: String
    ): String
    
    /**
     * Exchanges authorization code for access tokens.
     * 
     * @param authorizationCode Authorization code from OAuth provider
     * @param codeVerifier PKCE code verifier
     * @return Result containing OAuth result or error
     */
    suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult
    
    /**
     * Refreshes expired access tokens.
     * 
     * @param refreshToken Refresh token
     * @return Result containing OAuth result or error
     */
    suspend fun refreshAccessToken(refreshToken: String): OAuthResult
    
    /**
     * Gets user information from the OAuth provider.
     * 
     * @param accessToken Valid access token
     * @return Result containing OAuth result with user info or error
     */
    suspend fun getUserInfo(accessToken: String): OAuthResult
    
    /**
     * Revokes access tokens.
     * 
     * @param token Access token to revoke
     * @return true if successful, false otherwise
     */
    suspend fun revokeToken(token: String): Boolean
    
    /**
     * Validates if a token is still valid.
     * 
     * @param accessToken Access token to validate
     * @return true if valid, false otherwise
     */
    suspend fun validateToken(accessToken: String): Boolean
}

/**
 * Base implementation of OAuthClient with common functionality.
 * Provides default implementations and common error handling.
 */
abstract class BaseOAuthClient : OAuthClient {
    
    override val logger: Logger = Logger.getLogger(this::class)
    
    override suspend fun getAuthorizationUrl(
        state: String,
        codeChallenge: String,
        codeChallengeMethod: String
    ): String {
        return try {
            logger.info("oauth", "Generating authorization URL")
            performGetAuthorizationUrl(state, codeChallenge, codeChallengeMethod)
        } catch (e: Exception) {
            logger.error("oauth", "Failed to generate authorization URL", e)
            throw e
        }
    }
    
    override suspend fun exchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult {
        return try {
            logger.info("oauth", "Exchanging authorization code for tokens")
            performExchangeCodeForTokens(authorizationCode, codeVerifier)
        } catch (e: Exception) {
            logger.error("oauth", "Token exchange failed", e)
            OAuthResult.Failure(OAuthError.networkError("Token exchange failed: ${e.message}", e))
        }
    }
    
    override suspend fun refreshAccessToken(refreshToken: String): OAuthResult {
        return try {
            logger.info("oauth", "Refreshing access token")
            performRefreshAccessToken(refreshToken)
        } catch (e: Exception) {
            logger.error("oauth", "Token refresh failed", e)
            OAuthResult.Failure(OAuthError.networkError("Token refresh failed: ${e.message}", e))
        }
    }
    
    override suspend fun getUserInfo(accessToken: String): OAuthResult {
        return try {
            logger.info("oauth", "Fetching user info from OAuth provider")
            performGetUserInfo(accessToken)
        } catch (e: Exception) {
            logger.error("oauth", "Failed to get user info", e)
            OAuthResult.Failure(OAuthError.networkError("Failed to get user info: ${e.message}", e))
        }
    }
    
    override suspend fun revokeToken(token: String): Boolean {
        return try {
            logger.info("oauth", "Revoking OAuth token")
            performRevokeToken(token)
        } catch (e: Exception) {
            logger.error("oauth", "Token revocation failed", e)
            false
        }
    }
    
    override suspend fun validateToken(accessToken: String): Boolean {
        return try {
            logger.info("oauth", "Validating OAuth token")
            performValidateToken(accessToken)
        } catch (e: Exception) {
            logger.error("oauth", "Token validation failed", e)
            false
        }
    }
    
    // Abstract methods to be implemented by subclasses
    protected abstract suspend fun performGetAuthorizationUrl(
        state: String,
        codeChallenge: String,
        codeChallengeMethod: String
    ): String
    
    protected abstract suspend fun performExchangeCodeForTokens(
        authorizationCode: String,
        codeVerifier: String
    ): OAuthResult
    
    protected abstract suspend fun performRefreshAccessToken(refreshToken: String): OAuthResult
    
    protected abstract suspend fun performGetUserInfo(accessToken: String): OAuthResult
    
    protected abstract suspend fun performRevokeToken(token: String): Boolean
    
    protected abstract suspend fun performValidateToken(accessToken: String): Boolean
}