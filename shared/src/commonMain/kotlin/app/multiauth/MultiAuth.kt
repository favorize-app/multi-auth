package app.multiauth

import app.multiauth.config.OAuthConfigurationManager
import app.multiauth.config.GeneratedOAuthConfig
import app.multiauth.core.AuthEngine
import app.multiauth.core.AuthStateManager
import app.multiauth.oauth.OAuthManager
import app.multiauth.oauth.EnhancedOAuthManager
import app.multiauth.mfa.MfaManager
import app.multiauth.biometric.BiometricManager
import app.multiauth.util.Logger

/**
 * Main entry point for the Multi-Auth library.
 * Provides a simple way to initialize and access authentication features.
 */
object MultiAuth {
    
    private var isInitialized = false
    private var authEngine: AuthEngine? = null
    private var authStateManager: AuthStateManager? = null
    private var oauthManager: OAuthManager? = null
    private var enhancedOAuthManager: EnhancedOAuthManager? = null
    private var mfaManager: MfaManager? = null
    private var biometricManager: BiometricManager? = null
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Initializes the Multi-Auth library.
     * This should be called once during app startup.
     */
    fun initialize(config: MultiAuthConfig) {
        if (isInitialized) {
            logger.warn("MultiAuth", "MultiAuth is already initialized")
            return
        }
        
        logger.info("MultiAuth", "Initializing Multi-Auth library")
        
        // Load OAuth configuration from generated config
        try {
            GeneratedOAuthConfig.loadConfiguration()
            logger.info("MultiAuth", "OAuth configuration loaded successfully")
        } catch (e: Exception) {
            logger.error("MultiAuth", "Failed to load OAuth configuration", e)
            // Continue initialization without OAuth configuration
        }
        
        // Initialize core components
        authStateManager = AuthStateManager.getInstance()
        authEngine = AuthEngine.create(
            config.emailProvider,
            config.smsProvider,
            config.oauthProvider,
            config.eventBus
        )
        
        // Initialize OAuth managers
        authEngine?.let { engine ->
            oauthManager = OAuthManager(engine, config.eventBus)
            enhancedOAuthManager = EnhancedOAuthManager(engine, config.eventBus)
        }
        
        // Initialize MFA manager if storage is provided
        config.secureStorage?.let { storage ->
            authEngine?.let { engine ->
                mfaManager = MfaManager(
                    authEngine = engine,
                    secureStorage = storage,
                    smsProvider = config.smsProvider
                )
            }
        }
        
        // Initialize biometric manager
        biometricManager = BiometricManager(
            authEngine = authEngine ?: throw IllegalStateException("AuthEngine not initialized")
        )
        
        isInitialized = true
        logger.info("MultiAuth", "Multi-Auth library initialized successfully")
    }
    
    /**
     * Gets the AuthEngine instance.
     * @throws IllegalStateException if not initialized
     */
    fun getAuthEngine(): AuthEngine {
        checkInitialized()
        return authEngine!!
    }
    
    /**
     * Gets the AuthStateManager instance.
     * @throws IllegalStateException if not initialized
     */
    fun getAuthStateManager(): AuthStateManager {
        checkInitialized()
        return authStateManager!!
    }
    
    /**
     * Gets the OAuthManager instance.
     * @throws IllegalStateException if not initialized
     */
    fun getOAuthManager(): OAuthManager {
        checkInitialized()
        return oauthManager!!
    }
    
    /**
     * Gets the EnhancedOAuthManager instance.
     * @throws IllegalStateException if not initialized
     */
    fun getEnhancedOAuthManager(): EnhancedOAuthManager {
        checkInitialized()
        return enhancedOAuthManager!!
    }
    
    /**
     * Gets the MfaManager instance.
     * @throws IllegalStateException if not initialized or MFA not configured
     */
    fun getMfaManager(): MfaManager {
        checkInitialized()
        return mfaManager ?: throw IllegalStateException("MFA manager not initialized. Make sure to provide secureStorage and smsProvider in MultiAuthConfig.")
    }
    
    /**
     * Gets the BiometricManager instance.
     * @throws IllegalStateException if not initialized or biometrics not configured
     */
    fun getBiometricManager(): BiometricManager {
        checkInitialized()
        return biometricManager ?: throw IllegalStateException("Biometric manager not initialized. Make sure to provide secureStorage in MultiAuthConfig.")
    }
    
    /**
     * Checks if the library is initialized.
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Shuts down the Multi-Auth library and cleans up resources.
     */
    fun shutdown() {
        if (!isInitialized) {
            logger.warn("MultiAuth", "MultiAuth is not initialized")
            return
        }
        
        logger.info("MultiAuth", "Shutting down Multi-Auth library")
        
        // Clear OAuth configuration
        OAuthConfigurationManager.clearConfiguration()
        
        // Clean up managers
        authEngine = null
        authStateManager = null
        oauthManager = null
        enhancedOAuthManager = null
        mfaManager = null
        biometricManager = null
        
        isInitialized = false
        logger.info("MultiAuth", "Multi-Auth library shutdown complete")
    }
    
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("MultiAuth is not initialized. Call MultiAuth.initialize() first.")
        }
    }
}

/**
 * Configuration for Multi-Auth library initialization.
 */
data class MultiAuthConfig(
    val emailProvider: app.multiauth.providers.EmailProvider,
    val smsProvider: app.multiauth.providers.SmsProvider,
    val oauthProvider: app.multiauth.oauth.OAuthProvider,
    val authConfig: AuthConfig = AuthConfig(),
    val eventBus: app.multiauth.events.EventBus = app.multiauth.events.EventBusInstance(),
    val secureStorage: app.multiauth.storage.SecureStorage? = null
)

/**
 * Configuration for the authentication engine.
 */
data class AuthConfig(
    val sessionTimeoutMinutes: Long = 30,
    val maxLoginAttempts: Int = 5,
    val lockoutDurationMinutes: Long = 15,
    val enableRateLimiting: Boolean = true,
    val enableAuditLogging: Boolean = true,
    val tokenRefreshThresholdMinutes: Long = 5
)
