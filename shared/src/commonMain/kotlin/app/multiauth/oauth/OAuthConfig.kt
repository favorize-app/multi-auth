package app.multiauth.oauth

import kotlinx.serialization.Serializable

/**
 * Configuration for OAuth authentication.
 * Contains all necessary parameters for OAuth flows.
 */
@Serializable
data class OAuthConfig(
    val provider: String,
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: List<String> = emptyList(),
    val state: String? = null,
    val responseType: String = "code",
    val grantType: String = "authorization_code",
    val authUrl: String,
    val tokenUrl: String,
    val userInfoUrl: String,
    val revokeUrl: String? = null,
    val pkceEnabled: Boolean = true,
    val additionalParams: Map<String, String> = emptyMap()
) {
    
    companion object {
        /**
         * Creates a configuration for a specific OAuth provider.
         */
        fun forProvider(
            provider: String,
            clientId: String,
            clientSecret: String,
            redirectUri: String,
            scopes: List<String> = emptyList(),
            additionalParams: Map<String, String> = emptyMap()
        ): OAuthConfig {
            val (authUrl, tokenUrl, userInfoUrl, revokeUrl) = getProviderUrls(provider)
            
            return OAuthConfig(
                provider = provider,
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri,
                scopes = scopes,
                authUrl = authUrl,
                tokenUrl = tokenUrl,
                userInfoUrl = userInfoUrl,
                revokeUrl = revokeUrl,
                additionalParams = additionalParams
            )
        }
        
        /**
         * Gets the default URLs for common OAuth providers.
         */
        private fun getProviderUrls(provider: String): ProviderUrls {
            return when (provider.lowercase()) {
                "google" -> ProviderUrls(
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    "https://www.googleapis.com/oauth2/v2/userinfo",
                    "https://oauth2.googleapis.com/revoke"
                )
                "github" -> ProviderUrls(
                    "https://github.com/login/oauth/authorize",
                    "https://github.com/login/oauth/access_token",
                    "https://api.github.com/user",
                    null
                )
                "discord" -> ProviderUrls(
                    "https://discord.com/api/oauth2/authorize",
                    "https://discord.com/api/oauth2/token",
                    "https://discord.com/api/users/@me",
                    null
                )
                "microsoft" -> ProviderUrls(
                    "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                    "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                    "https://graph.microsoft.com/v1.0/me",
                    null
                )
                "linkedin" -> ProviderUrls(
                    "https://www.linkedin.com/oauth/v2/authorization",
                    "https://www.linkedin.com/oauth/v2/accessToken",
                    "https://api.linkedin.com/v2/me",
                    null
                )
                "twitter" -> ProviderUrls(
                    "https://twitter.com/i/oauth2/authorize",
                    "https://api.twitter.com/2/oauth2/token",
                    "https://api.twitter.com/2/users/me",
                    null
                )
                else -> throw IllegalArgumentException("Unknown OAuth provider: $provider")
            }
        }
    }
    
    /**
     * Builds the authorization URL for the OAuth flow.
     */
    fun buildAuthUrl(): String {
        val params = mutableMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to responseType,
            "scope" to scopes.joinToString(" ")
        )
        
        state?.let { params["state"] = it }
        
        if (pkceEnabled) {
            params["code_challenge_method"] = "S256"
            // Note: code_challenge should be set by the client
        }
        
        // Add additional parameters
        params.putAll(additionalParams)
        
        val queryString = params.entries
            .joinToString("&") { "${it.key}=${it.value}" }
        
        return "$authUrl?$queryString"
    }
    
    /**
     * Builds the token exchange request body.
     */
    fun buildTokenRequestBody(code: String, codeVerifier: String? = null): Map<String, String> {
        val body = mutableMapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "grant_type" to grantType,
            "code" to code,
            "redirect_uri" to redirectUri
        )
        
        if (pkceEnabled && codeVerifier != null) {
            body["code_verifier"] = codeVerifier
        }
        
        return body
    }
}

/**
 * Internal data class for provider URLs.
 */
private data class ProviderUrls(
    val authUrl: String,
    val tokenUrl: String,
    val userInfoUrl: String,
    val revokeUrl: String?
)
