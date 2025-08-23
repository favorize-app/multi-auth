package app.multiauth.providers.impl

import app.multiauth.models.*
import app.multiauth.providers.*
import app.multiauth.util.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Google OpenID Connect provider implementation.
 * 
 * Google OIDC endpoints:
 * - Discovery: https://accounts.google.com/.well-known/openid_configuration
 * - Authorization: https://accounts.google.com/o/oauth2/v2/auth
 * - Token: https://oauth2.googleapis.com/token
 * - User Info: https://www.googleapis.com/oauth2/v3/userinfo
 * - JWKS: https://www.googleapis.com/oauth2/v3/certs
 */
class GoogleOIDCProvider : OIDCProvider {
    
    private val logger = Logger.getLogger(this::class)
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }
    
    // Google OIDC well-known configuration
    private val wellKnownConfig = OIDCConfiguration(
        issuer = "https://accounts.google.com",
        authorizationEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenEndpoint = "https://oauth2.googleapis.com/token",
        userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo",
        jwksUri = "https://www.googleapis.com/oauth2/v3/certs",
        endSessionEndpoint = "https://oauth2.googleapis.com/revoke",
        scopesSupported = listOf("openid", "email", "profile", "address", "phone"),
        responseTypesSupported = listOf("code", "token", "id_token", "code token", "code id_token"),
        responseModesSupported = listOf("query", "fragment"),
        grantTypesSupported = listOf("authorization_code", "refresh_token"),
        tokenEndpointAuthMethodsSupported = listOf("client_secret_post", "client_secret_basic"),
        tokenEndpointAuthSigningAlgValuesSupported = listOf("RS256"),
        displayValuesSupported = listOf("page", "popup", "touch", "wap"),
        claimTypesSupported = listOf("normal"),
        claimsSupported = listOf(
            "sub", "iss", "name", "given_name", "family_name", "picture", "email",
            "email_verified", "locale", "hd", "updated_at"
        )
    )
    
    override suspend fun getConfiguration(): OIDCConfiguration {
        return wellKnownConfig
    }
    
    override suspend fun initiateAuth(
        config: OIDCProviderConfig,
        redirectUri: String,
        scopes: List<String>,
        state: String?,
        nonce: String?,
        prompt: String?
    ): AuthResult<OIDCAuthRequest> {
        return try {
            logger.info("Initiating Google OIDC authentication")
            
            val authRequest = OIDCAuthRequest(
                clientId = config.clientId,
                redirectUri = redirectUri,
                responseType = "code",
                scope = scopes.joinToString(" "),
                state = state,
                nonce = nonce,
                prompt = prompt,
                display = "page"
            )
            
            AuthResult.Success(authRequest)
        } catch (e: Exception) {
            logger.error("Failed to initiate Google OIDC authentication", e)
            AuthResult.Failure(AuthError.UnknownError("Failed to initiate authentication: ${e.message}", e))
        }
    }
    
    override suspend fun completeAuth(
        config: OIDCProviderConfig,
        authorizationCode: String,
        redirectUri: String,
        codeVerifier: String?,
        nonce: String?
    ): AuthResult<OIDCAuthResult> {
        return try {
            logger.info("Completing Google OIDC authentication")
            
            // Exchange authorization code for tokens
            val tokenRequest = OIDCTokenRequest(
                grantType = "authorization_code",
                clientId = config.clientId,
                clientSecret = config.clientSecret,
                code = authorizationCode,
                redirectUri = redirectUri,
                codeVerifier = codeVerifier
            )
            
            val tokenResponse = exchangeCodeForTokens(tokenRequest)
            
            tokenResponse.onSuccess { tokens ->
                // Get user info
                val userInfo = getUserInfoWithToken(tokens.accessToken)
                
                userInfo.onSuccess { info ->
                    // Validate ID token
                    val idTokenValidation = validateIdToken(
                        config = config,
                        idToken = tokens.idToken ?: "",
                        nonce = nonce,
                        clientId = config.clientId
                    )
                    
                    idTokenValidation.onSuccess { decodedIdToken ->
                        val authResult = OIDCAuthResult(
                            accessToken = tokens.accessToken,
                            idToken = tokens.idToken ?: "",
                            refreshToken = tokens.refreshToken,
                            tokenType = tokens.tokenType,
                            expiresIn = tokens.expiresIn,
                            scope = tokens.scope,
                            userInfo = info,
                            decodedIdToken = decodedIdToken
                        )
                        
                        AuthResult.Success(authResult)
                    }.onFailure { error ->
                        AuthResult.Failure(error)
                    }
                }.onFailure { error ->
                    AuthResult.Failure(error)
                }
            }.onFailure { error ->
                AuthResult.Failure(error)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to complete Google OIDC authentication", e)
            AuthResult.Failure(AuthError.UnknownError("Failed to complete authentication: ${e.message}", e))
        }
    }
    
    override suspend fun refreshToken(
        config: OIDCProviderConfig,
        refreshToken: String
    ): AuthResult<OIDCTokenResponse> {
        return try {
            logger.info("Refreshing Google OIDC token")
            
            val response = httpClient.post(wellKnownConfig.tokenEndpoint) {
                setBody(
                    mapOf(
                        "grant_type" to "refresh_token",
                        "client_id" to config.clientId,
                        "client_secret" to config.clientSecret,
                        "refresh_token" to refreshToken
                    )
                )
                contentType(ContentType.Application.FormUrlEncoded)
            }
            
            if (response.status.isSuccess()) {
                val tokens = response.body<OIDCTokenResponse>()
                AuthResult.Success(tokens)
            } else {
                val error = response.body<OIDCError>()
                AuthResult.Failure(AuthError.AuthenticationError("Token refresh failed: ${error.errorDescription}"))
            }
            
        } catch (e: Exception) {
            logger.error("Failed to refresh Google OIDC token", e)
            AuthResult.Failure(AuthError.UnknownError("Failed to refresh token: ${e.message}", e))
        }
    }
    
    override suspend fun revokeToken(
        config: OIDCProviderConfig,
        accessToken: String
    ): AuthResult<Unit> {
        return try {
            logger.info("Revoking Google OIDC token")
            
            val response = httpClient.post(wellKnownConfig.endSessionEndpoint!!) {
                setBody(
                    mapOf(
                        "token" to accessToken
                    )
                )
                contentType(ContentType.Application.FormUrlEncoded)
            }
            
            if (response.status.isSuccess()) {
                AuthResult.Success(Unit)
            } else {
                AuthResult.Failure(AuthError.AuthenticationError("Token revocation failed"))
            }
            
        } catch (e: Exception) {
            logger.error("Failed to revoke Google OIDC token", e)
            AuthResult.Failure(AuthError.UnknownError("Failed to revoke token: ${e.message}", e))
        }
    }
    
    override suspend fun getUserInfo(
        config: OIDCProviderConfig,
        accessToken: String
    ): AuthResult<OIDCUserInfo> {
        return getUserInfoWithToken(accessToken)
    }
    
    override suspend fun validateIdToken(
        config: OIDCProviderConfig,
        idToken: String,
        nonce: String?,
        clientId: String?
    ): AuthResult<OIDCIDToken> {
        return try {
            logger.info("Validating Google OIDC ID token")
            
            // For now, return a basic validation
            // In production, this should validate the JWT signature, expiration, issuer, etc.
            val decodedToken = decodeIdToken(idToken)
            
            if (decodedToken != null) {
                // Basic validation
                if (decodedToken.iss != wellKnownConfig.issuer) {
                    return AuthResult.Failure(AuthError.ValidationError("Invalid issuer", "issuer"))
                }
                
                if (decodedToken.aud != (clientId ?: config.clientId)) {
                    return AuthResult.Failure(AuthError.ValidationError("Invalid audience", "audience"))
                }
                
                if (decodedToken.exp < System.currentTimeMillis() / 1000) {
                    return AuthResult.Failure(AuthError.ValidationError("Token expired", "expiration"))
                }
                
                if (nonce != null && decodedToken.nonce != nonce) {
                    return AuthResult.Failure(AuthError.ValidationError("Invalid nonce", "nonce"))
                }
                
                AuthResult.Success(decodedToken)
            } else {
                AuthResult.Failure(AuthError.ValidationError("Invalid ID token format", "format"))
            }
            
        } catch (e: Exception) {
            logger.error("Failed to validate Google OIDC ID token", e)
            AuthResult.Failure(AuthError.UnknownError("Failed to validate ID token: ${e.message}", e))
        }
    }
    
    override suspend fun endSession(
        config: OIDCProviderConfig,
        idToken: String,
        postLogoutRedirectUri: String?
    ): AuthResult<Unit> {
        // Google doesn't have a standard end session endpoint
        // We can revoke the access token instead
        return revokeToken(config, idToken)
    }
    
    override fun getProviderInfo(): OIDCProviderInfo {
        return OIDCProviderInfo(
            name = "Google",
            description = "Google OpenID Connect provider",
            supportedFeatures = listOf(
                OIDCFeature.PKCE,
                OIDCFeature.NONCE,
                OIDCFeature.REFRESH_TOKENS,
                OIDCFeature.REVOCATION,
                OIDCFeature.USER_INFO,
                OIDCFeature.CUSTOM_CLAIMS
            ),
            defaultScopes = listOf("openid", "profile", "email"),
            supportedScopes = listOf("openid", "profile", "email", "address", "phone"),
            issuer = "https://accounts.google.com",
            documentationUrl = "https://developers.google.com/identity/protocols/oauth2/openid-connect"
        )
    }
    
    override fun supportsFeature(feature: OIDCFeature): Boolean {
        return getProviderInfo().supportedFeatures.contains(feature)
    }
    
    // Private helper methods
    
    private suspend fun exchangeCodeForTokens(tokenRequest: OIDCTokenRequest): AuthResult<OIDCTokenResponse> {
        return try {
            val response = httpClient.post(wellKnownConfig.tokenEndpoint) {
                setBody(
                    mapOf(
                        "grant_type" to tokenRequest.grantType,
                        "client_id" to tokenRequest.clientId,
                        "client_secret" to tokenRequest.clientSecret,
                        "code" to tokenRequest.code,
                        "redirect_uri" to tokenRequest.redirectUri,
                        "code_verifier" to (tokenRequest.codeVerifier ?: "")
                    )
                )
                contentType(ContentType.Application.FormUrlEncoded)
            }
            
            if (response.status.isSuccess()) {
                val tokens = response.body<OIDCTokenResponse>()
                AuthResult.Success(tokens)
            } else {
                val error = response.body<OIDCError>()
                AuthResult.Failure(AuthError.AuthenticationError("Token exchange failed: ${error.errorDescription}"))
            }
            
        } catch (e: Exception) {
            logger.error("Failed to exchange code for tokens", e)
            AuthResult.Failure(AuthError.UnknownError("Failed to exchange code: ${e.message}", e))
        }
    }
    
    private suspend fun getUserInfoWithToken(accessToken: String): AuthResult<OIDCUserInfo> {
        return try {
            val response = httpClient.get(wellKnownConfig.userInfoEndpoint) {
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val userInfo = response.body<OIDCUserInfo>()
                AuthResult.Success(userInfo)
            } else {
                AuthResult.Failure(AuthError.AuthenticationError("Failed to get user info"))
            }
            
        } catch (e: Exception) {
            logger.error("Failed to get user info", e)
            AuthResult.Failure(AuthError.UnknownError("Failed to get user info: ${e.message}", e))
        }
    }
    
    private fun decodeIdToken(idToken: String): OIDCIDToken? {
        return try {
            // This is a simplified JWT decode - in production, use a proper JWT library
            // For now, we'll return null to indicate the token couldn't be decoded
            // In a real implementation, you would decode the JWT and extract claims
            null
        } catch (e: Exception) {
            logger.error("Failed to decode ID token", e)
            null
        }
    }
}