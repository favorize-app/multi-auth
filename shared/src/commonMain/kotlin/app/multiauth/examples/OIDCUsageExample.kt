package app.multiauth.examples

import app.multiauth.core.OIDCManager
import app.multiauth.events.EventBus
import app.multiauth.models.*
import app.multiauth.providers.OIDCProvider
import app.multiauth.providers.OIDCProviderFactory
import app.multiauth.util.Logger

/**
 * Example demonstrating how to use the OIDC authentication system.
 * This shows the complete flow from configuration to authentication.
 */
class OIDCUsageExample {
    
    private val logger = Logger.getLogger(this::class)
    private val oidcManager = OIDCManager()
    private val eventBus = EventBus.getInstance()
    
    init {
        // Subscribe to OIDC events
        subscribeToOIDCEvents()
    }
    
    /**
     * Example: Sign in with Google OIDC
     */
    suspend fun signInWithGoogle() {
        logger.info("Starting Google OIDC sign-in example")
        
        try {
            // Create Google OIDC provider
            val googleProvider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)
            
            // Configure the provider
            val config = OIDCProviderConfig(
                provider = OIDCProvider.GOOGLE,
                clientId = "your_google_client_id",
                clientSecret = "your_google_client_secret",
                redirectUris = listOf("com.yourapp://oauth/callback"),
                scopes = listOf("openid", "profile", "email")
            )
            
            // Start the OIDC sign-in flow
            val result = oidcManager.signInWithOIDC(
                provider = googleProvider,
                config = config,
                redirectUri = "com.yourapp://oauth/callback"
            )
            
            result.onSuccess { user ->
                logger.info("Google OIDC sign-in initiated successfully for user: ${user.displayName}")
                // The actual authentication will be completed when the user returns from the OAuth flow
            }.onFailure { error ->
                logger.error("Google OIDC sign-in failed", error)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to start Google OIDC sign-in", e)
        }
    }
    
    /**
     * Example: Complete OIDC authentication after user returns from OAuth flow
     */
    suspend fun completeGoogleOIDCAuth(authorizationCode: String) {
        logger.info("Completing Google OIDC authentication")
        
        try {
            val googleProvider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)
            val config = OIDCProviderConfig(
                provider = OIDCProvider.GOOGLE,
                clientId = "your_google_client_id",
                clientSecret = "your_google_client_secret",
                redirectUris = listOf("com.yourapp://oauth/callback")
            )
            
            val result = oidcManager.completeOIDCAuth(
                provider = googleProvider,
                config = config,
                authorizationCode = authorizationCode,
                redirectUri = "com.yourapp://oauth/callback"
            )
            
            result.onSuccess { user ->
                logger.info("Google OIDC authentication completed successfully for user: ${user.displayName}")
                // User is now authenticated and can access protected resources
            }.onFailure { error ->
                logger.error("Google OIDC authentication failed", error)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to complete Google OIDC authentication", e)
        }
    }
    
    /**
     * Example: Refresh OIDC token
     */
    suspend fun refreshGoogleOIDCToken(refreshToken: String) {
        logger.info("Refreshing Google OIDC token")
        
        try {
            val googleProvider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)
            val config = OIDCProviderConfig(
                provider = OIDCProvider.GOOGLE,
                clientId = "your_google_client_id",
                clientSecret = "your_google_client_secret",
                redirectUris = listOf("com.yourapp://oauth/callback")
            )
            
            val result = oidcManager.refreshOIDCToken(
                provider = googleProvider,
                config = config,
                refreshToken = refreshToken
            )
            
            result.onSuccess { tokens ->
                logger.info("Google OIDC token refreshed successfully")
                // Use the new access token
            }.onFailure { error ->
                logger.error("Google OIDC token refresh failed", error)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to refresh Google OIDC token", e)
        }
    }
    
    /**
     * Example: Get user information from OIDC provider
     */
    suspend fun getGoogleUserInfo(accessToken: String) {
        logger.info("Getting Google OIDC user info")
        
        try {
            val googleProvider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)
            val config = OIDCProviderConfig(
                provider = OIDCProvider.GOOGLE,
                clientId = "your_google_client_id",
                clientSecret = "your_google_client_secret",
                redirectUris = listOf("com.yourapp://oauth/callback")
            )
            
            val result = oidcManager.getUserInfo(
                provider = googleProvider,
                config = config,
                accessToken = accessToken
            )
            
            result.onSuccess { userInfo ->
                logger.info("Google OIDC user info retrieved successfully: ${userInfo.name}")
                // Use the user information
            }.onFailure { error ->
                logger.error("Failed to get Google OIDC user info", error)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to get Google OIDC user info", e)
        }
    }
    
    /**
     * Example: End OIDC session (logout)
     */
    suspend fun endGoogleOIDCSession(idToken: String) {
        logger.info("Ending Google OIDC session")
        
        try {
            val googleProvider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)
            val config = OIDCProviderConfig(
                provider = OIDCProvider.GOOGLE,
                clientId = "your_google_client_id",
                clientSecret = "your_google_client_secret",
                redirectUris = listOf("com.yourapp://oauth/callback")
            )
            
            val result = oidcManager.endOIDCSession(
                provider = googleProvider,
                config = config,
                idToken = idToken
            )
            
            result.onSuccess {
                logger.info("Google OIDC session ended successfully")
                // User is now logged out
            }.onFailure { error ->
                logger.error("Failed to end Google OIDC session", error)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to end Google OIDC session", e)
        }
    }
    
    /**
     * Example: Check available OIDC providers
     */
    fun checkAvailableProviders() {
        logger.info("Checking available OIDC providers")
        
        val supportedProviders = OIDCProviderFactory.getSupportedProviders()
        val implementedProviders = OIDCProviderFactory.getImplementedProviders()
        
        logger.info("Supported OIDC providers: ${supportedProviders.map { it.name }}")
        logger.info("Implemented OIDC providers: ${implementedProviders.map { it.name }}")
        
        // Check if specific providers are supported
        val isGoogleSupported = OIDCProviderFactory.isProviderSupported(OIDCProvider.GOOGLE)
        val isMicrosoftSupported = OIDCProviderFactory.isProviderSupported(OIDCProvider.MICROSOFT)
        
        logger.info("Google OIDC supported: $isGoogleSupported")
        logger.info("Microsoft OIDC supported: $isMicrosoftSupported")
    }
    
    /**
     * Example: Get provider information
     */
    fun getProviderInfo() {
        logger.info("Getting OIDC provider information")
        
        try {
            val googleProvider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)
            val providerInfo = googleProvider.getProviderInfo()
            
            logger.info("Provider: ${providerInfo.name}")
            logger.info("Description: ${providerInfo.description}")
            logger.info("Issuer: ${providerInfo.issuer}")
            logger.info("Supported features: ${providerInfo.supportedFeatures.map { it.name }}")
            logger.info("Default scopes: ${providerInfo.defaultScopes}")
            logger.info("Supported scopes: ${providerInfo.supportedScopes}")
            
        } catch (e: Exception) {
            logger.error("Failed to get provider information", e)
        }
    }
    
    // Event handling
    
    private fun subscribeToOIDCEvents() {
        eventBus.subscribe<AuthEvent.OIDC> { event, metadata ->
            when (event) {
                is AuthEvent.OIDC.OIDCAuthorizationRequested -> {
                    logger.info("OIDC authorization requested: ${event.authRequest}")
                    // Handle the authorization request (e.g., open browser, redirect user)
                }
                is AuthEvent.OIDC.OIDCSignInCompleted -> {
                    logger.info("OIDC sign-in completed for user: ${event.user.displayName}")
                    // Handle successful sign-in
                }
                is AuthEvent.OIDC.OIDCSignInFailed -> {
                    logger.error("OIDC sign-in failed: ${event.error.message}")
                    // Handle sign-in failure
                }
                is AuthEvent.OIDC.OIDCTokenRefreshed -> {
                    logger.info("OIDC token refreshed successfully")
                    // Handle token refresh
                }
                is AuthEvent.OIDC.OIDCTokenRefreshFailed -> {
                    logger.error("OIDC token refresh failed: ${event.error.message}")
                    // Handle token refresh failure
                }
                is AuthEvent.OIDC.OIDCSessionEnded -> {
                    logger.info("OIDC session ended successfully")
                    // Handle session end
                }
                is AuthEvent.OIDC.OIDCSessionEndFailed -> {
                    logger.error("OIDC session end failed: ${event.error.message}")
                    // Handle session end failure
                }
                is AuthEvent.OIDC.OIDCUserInfoRetrieved -> {
                    logger.info("OIDC user info retrieved: ${event.userInfo.name}")
                    // Handle user info retrieval
                }
                is AuthEvent.OIDC.OIDCUserInfoRetrievalFailed -> {
                    logger.error("OIDC user info retrieval failed: ${event.error.message}")
                    // Handle user info retrieval failure
                }
                is AuthEvent.OIDC.OIDCConfigurationRetrieved -> {
                    logger.info("OIDC configuration retrieved: ${event.configuration.issuer}")
                    // Handle configuration retrieval
                }
                is AuthEvent.OIDC.OIDCConfigurationFailed -> {
                    logger.error("OIDC configuration failed: ${event.error.message}")
                    // Handle configuration failure
                }
                is AuthEvent.OIDC.OIDCIDTokenValidated -> {
                    logger.info("OIDC ID token validated: ${event.idToken.sub}")
                    // Handle ID token validation
                }
                is AuthEvent.OIDC.OIDCIDTokenValidationFailed -> {
                    logger.error("OIDC ID token validation failed: ${event.error.message}")
                    // Handle ID token validation failure
                }
            }
        }
    }
}