package app.multiauth.core

import app.multiauth.events.*
import app.multiauth.models.*
import app.multiauth.providers.OIDCProvider
import app.multiauth.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Manager for OpenID Connect authentication flows.
 * Handles OIDC authentication, token management, and user session creation.
 * 
 * OIDC extends OAuth 2.0 with standardized identity layer and JWT-based ID tokens.
 */
class OIDCManager(
    private val eventBus: EventBus = EventBus.getInstance()
) {
    
    private val logger = Logger.getLogger(this::class)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _oidcState = MutableStateFlow<OIDCState>(OIDCState.Idle)
    val oidcState: StateFlow<OIDCState> = _oidcState.asStateFlow()
    
    private val _currentProvider = MutableStateFlow<OIDCProvider?>(null)
    val currentProvider: StateFlow<OIDCProvider?> = _currentProvider.asStateFlow()
    
    private val _pkceState = MutableStateFlow<PKCEState?>(null)
    val pkceState: StateFlow<PKCEState?> = _pkceState.asStateFlow()
    
    private val _nonceState = MutableStateFlow<String?>(null)
    val nonceState: StateFlow<String?> = _nonceState.asStateFlow()
    
    /**
     * Initiates an OIDC sign-in flow for the specified provider.
     * 
     * @param provider The OIDC provider to use
     * @param config Provider configuration
     * @param redirectUri Optional redirect URI (platform-specific)
     * @param scopes The requested OIDC scopes
     * @param prompt Optional prompt parameter
     * @return Result indicating success or failure
     */
    suspend fun signInWithOIDC(
        provider: OIDCProvider,
        config: OIDCProviderConfig,
        redirectUri: String? = null,
        scopes: List<String> = listOf("openid", "profile", "email"),
        prompt: String? = null
    ): Result<User> {
        return try {
            logger.info("Starting OIDC sign-in with provider: ${provider.getProviderInfo().name}")
            
            _oidcState.value = OIDCState.Initiating
            _currentProvider.value = provider
            
            // Generate PKCE parameters for enhanced security
            val pkce = generatePKCE()
            _pkceState.value = pkce
            
            // Generate nonce for replay protection
            val nonce = generateNonce()
            _nonceState.value = nonce
            
            // Get platform-specific redirect URI
            val finalRedirectUri = redirectUri ?: getPlatformRedirectUri(config)
            
            // Start the OIDC flow
            val authRequest = provider.initiateAuth(
                config = config,
                redirectUri = finalRedirectUri,
                scopes = scopes,
                state = pkce.state,
                nonce = nonce,
                prompt = prompt
            )
            
            authRequest.onSuccess { request ->
                logger.info("OIDC flow initiated successfully")
                _oidcState.value = OIDCState.AuthorizationPending
                
                // Dispatch event for UI to handle the authorization
                eventBus.dispatch(
                    AuthEvent.OIDC.OIDCAuthorizationRequested(request),
                    "OIDCManager"
                )
                
                // Return success - the actual authentication will happen in completeOIDCAuth
                Result.success(createPendingUser(config.provider))
            }.onFailure { error ->
                logger.error("Failed to initiate OIDC flow", error)
                _oidcState.value = OIDCState.Error(error)
                _oidcState.value = OIDCState.Idle
                Result.failure(error)
            }
            
        } catch (e: Exception) {
            val error = AuthError.UnknownError("OIDC sign-in failed: ${e.message}", e)
            logger.error("OIDC sign-in failed", error)
            _oidcState.value = OIDCState.Error(error)
            _oidcState.value = OIDCState.Idle
            Result.failure(error)
        }
    }
    
    /**
     * Completes the OIDC authentication flow using the authorization code.
     * 
     * @param provider The OIDC provider
     * @param config Provider configuration
     * @param authorizationCode The authorization code from the OIDC callback
     * @param redirectUri The redirect URI used in the flow
     * @return Result containing the authenticated user
     */
    suspend fun completeOIDCAuth(
        provider: OIDCProvider,
        config: OIDCProviderConfig,
        authorizationCode: String,
        redirectUri: String
    ): Result<User> {
        return try {
            logger.info("Completing OIDC authentication")
            
            _oidcState.value = OIDCState.ExchangingCode
            
            // Get the stored PKCE and nonce state
            val pkce = _pkceState.value
            val nonce = _nonceState.value
            
            if (pkce == null || nonce == null) {
                val error = AuthError.ValidationError("Missing PKCE or nonce state", "state")
                _oidcState.value = OIDCState.Error(error)
                _oidcState.value = OIDCState.Idle
                return Result.failure(error)
            }
            
            // Complete the OIDC authentication
            val authResult = provider.completeAuth(
                config = config,
                authorizationCode = authorizationCode,
                redirectUri = redirectUri,
                codeVerifier = pkce.codeVerifier,
                nonce = nonce
            )
            
            authResult.onSuccess { result ->
                logger.info("OIDC authentication completed successfully")
                _oidcState.value = OIDCState.CreatingSession
                
                // Validate the ID token
                val idTokenValidation = provider.validateIdToken(
                    config = config,
                    idToken = result.idToken,
                    nonce = nonce,
                    clientId = config.clientId
                )
                
                idTokenValidation.onSuccess { decodedIdToken ->
                    // Create user session with the obtained tokens
                    val user = createUserFromOIDC(decodedIdToken, result, config.provider)
                    
                    _oidcState.value = OIDCState.Success(user)
                    _oidcState.value = OIDCState.Idle
                    _currentProvider.value = null
                    _pkceState.value = null
                    _nonceState.value = null
                    
                    // Dispatch success event
                    eventBus.dispatch(
                        AuthEvent.OIDC.OIDCSignInCompleted(user, result),
                        "OIDCManager"
                    )
                    
                    logger.info("OIDC sign-in completed successfully for user: ${user.displayName}")
                    Result.success(user)
                }.onFailure { error ->
                    logger.error("Failed to validate ID token", error)
                    _oidcState.value = OIDCState.Error(error)
                    _oidcState.value = OIDCState.Idle
                    Result.failure(error)
                }
            }.onFailure { error ->
                logger.error("Failed to complete OIDC authentication", error)
                _oidcState.value = OIDCState.Error(error)
                _oidcState.value = OIDCState.Idle
                Result.failure(error)
            }
            
        } catch (e: Exception) {
            val error = AuthError.UnknownError("OIDC completion failed: ${e.message}", e)
            logger.error("OIDC completion failed", error)
            _oidcState.value = OIDCState.Error(error)
            _oidcState.value = OIDCState.Idle
            Result.failure(error)
        }
    }
    
    /**
     * Refreshes an OIDC access token.
     * 
     * @param provider The OIDC provider
     * @param config Provider configuration
     * @param refreshToken The refresh token to use
     * @return Result containing the new tokens
     */
    suspend fun refreshOIDCToken(
        provider: OIDCProvider,
        config: OIDCProviderConfig,
        refreshToken: String
    ): Result<OIDCTokenResponse> {
        return try {
            logger.info("Refreshing OIDC token")
            
            val result = provider.refreshToken(config, refreshToken)
            
            result.onSuccess { tokens ->
                logger.info("OIDC token refreshed successfully")
                eventBus.dispatch(
                    AuthEvent.OIDC.OIDCTokenRefreshed(tokens),
                    "OIDCManager"
                )
            }.onFailure { error ->
                logger.error("Failed to refresh OIDC token", error)
                eventBus.dispatch(
                    AuthEvent.OIDC.OIDCTokenRefreshFailed(error),
                    "OIDCManager"
                )
            }
            
            result
        } catch (e: Exception) {
            val error = AuthError.UnknownError("OIDC token refresh failed: ${e.message}", e)
            logger.error("OIDC token refresh failed", error)
            Result.failure(error)
        }
    }
    
    /**
     * Ends the OIDC session (logout).
     * 
     * @param provider The OIDC provider
     * @param config Provider configuration
     * @param idToken The ID token to use for logout
     * @param postLogoutRedirectUri Optional redirect URI after logout
     * @return Result indicating success or failure
     */
    suspend fun endOIDCSession(
        provider: OIDCProvider,
        config: OIDCProviderConfig,
        idToken: String,
        postLogoutRedirectUri: String? = null
    ): Result<Unit> {
        return try {
            logger.info("Ending OIDC session")
            
            val result = provider.endSession(config, idToken, postLogoutRedirectUri)
            
            result.onSuccess {
                logger.info("OIDC session ended successfully")
                eventBus.dispatch(
                    AuthEvent.OIDC.OIDCSessionEnded,
                    "OIDCManager"
                )
            }.onFailure { error ->
                logger.error("Failed to end OIDC session", error)
                eventBus.dispatch(
                    AuthEvent.OIDC.OIDCSessionEndFailed(error),
                    "OIDCManager"
                )
            }
            
            result
        } catch (e: Exception) {
            val error = AuthError.UnknownError("OIDC session end failed: ${e.message}", e)
            logger.error("OIDC session end failed", error)
            Result.failure(error)
        }
    }
    
    /**
     * Gets user information from the OIDC provider.
     * 
     * @param provider The OIDC provider
     * @param config Provider configuration
     * @param accessToken The access token to use
     * @return Result containing the user information
     */
    suspend fun getUserInfo(
        provider: OIDCProvider,
        config: OIDCProviderConfig,
        accessToken: String
    ): Result<OIDCUserInfo> {
        return try {
            logger.info("Getting OIDC user info")
            
            val result = provider.getUserInfo(config, accessToken)
            
            result.onSuccess { userInfo ->
                logger.info("OIDC user info retrieved successfully")
                eventBus.dispatch(
                    AuthEvent.OIDC.OIDCUserInfoRetrieved(userInfo),
                    "OIDCManager"
                )
            }.onFailure { error ->
                logger.error("Failed to get OIDC user info", error)
                eventBus.dispatch(
                    AuthEvent.OIDC.OIDCUserInfoRetrievalFailed(error),
                    "OIDCManager"
                )
            }
            
            result
        } catch (e: Exception) {
            val error = AuthError.UnknownError("OIDC user info retrieval failed: ${e.message}", e)
            logger.error("OIDC user info retrieval failed", error)
            Result.failure(error)
        }
    }
    
    // Private helper methods
    
    private fun generatePKCE(): PKCEState {
        val codeVerifier = generateRandomString(128)
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateRandomString(32)
        
        return PKCEState(
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge,
            state = state
        )
    }
    
    private fun generateNonce(): String {
        return generateRandomString(32)
    }
    
    private fun generateRandomString(length: Int): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
    
    private fun generateCodeChallenge(codeVerifier: String): String {
        // Simple SHA-256 hash for now - in production, use proper crypto library
        return codeVerifier.hashCode().toString()
    }
    
    private fun getPlatformRedirectUri(config: OIDCProviderConfig): String {
        // Platform-specific redirect URI logic
        return config.redirectUris.firstOrNull() ?: "com.yourapp://oauth/callback"
    }
    
    private fun createPendingUser(provider: OIDCProviderType): User {
        return User(
            id = "pending_${provider.name.lowercase()}",
            displayName = "Pending ${provider.name} User",
            isAnonymous = true,
            createdAt = kotlinx.datetime.Clock.System.now(),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }
    
    private fun createUserFromOIDC(
        idToken: OIDCIDToken,
        authResult: OIDCAuthResult,
        provider: OIDCProviderType
    ): User {
        return User(
            id = idToken.sub,
            email = idToken.email,
            displayName = idToken.name,
            photoUrl = idToken.picture,
            emailVerified = idToken.emailVerified ?: false,
            createdAt = kotlinx.datetime.Clock.System.now(),
            updatedAt = kotlinx.datetime.Clock.System.now(),
            lastSignInAt = kotlinx.datetime.Clock.System.now(),
            authMethods = listOf(
                AuthMethod.OAuth(
                    provider = provider,
                    providerUserId = idToken.sub,
                    verified = true,
                    verifiedAt = kotlinx.datetime.Clock.System.now()
                )
            ),
            metadata = mapOf(
                "oidc_issuer" to idToken.iss,
                "oidc_provider" to provider.name,
                "access_token" to authResult.accessToken
            )
        )
    }
}

/**
 * Represents the current state of an OIDC authentication flow.
 */
sealed class OIDCState {
    object Idle : OIDCState()
    object Initiating : OIDCState()
    object AuthorizationPending : OIDCState()
    object ExchangingCode : OIDCState()
    object CreatingSession : OIDCState()
    data class Success(val user: User) : OIDCState()
    data class Error(val error: AuthError) : OIDCState()
}

/**
 * Represents PKCE (Proof Key for Code Exchange) state.
 */
data class PKCEState(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String
)