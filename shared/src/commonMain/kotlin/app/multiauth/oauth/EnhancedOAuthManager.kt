package app.multiauth.oauth

import app.multiauth.core.AuthEngine
import app.multiauth.events.AuthEvent
import app.multiauth.events.EventBus
import app.multiauth.models.User
import app.multiauth.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.collections.MutableMap

/**
 * Enhanced OAuth Manager with account linking and multiple provider support.
 * Extends the basic OAuthManager with advanced features.
 */
class EnhancedOAuthManager(
    private val authEngine: AuthEngine,
    private val eventBus: EventBus = EventBusInstance()
) {
    
    private val logger = LoggerLogger(this::class)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        private const val MAX_LINKED_ACCOUNTS = 5
        private const val OAUTH_TOKEN_EXPIRY_BUFFER_MINUTES = 5L
    }
    
    private val _oauthState = MutableStateFlow<EnhancedOAuthState>(EnhancedOAuthState.Idle)
    val oauthState: StateFlow<EnhancedOAuthState> = _oauthState.asStateFlow()
    
    private val _linkedAccounts = MutableStateFlow<Map<String, List<LinkedAccount>>>(emptyMap())
    val linkedAccounts: StateFlow<Map<String, List<LinkedAccount>>> = _linkedAccounts.asStateFlow()
    
    private val _oauthAnalytics = MutableStateFlow<OAuthAnalytics>(OAuthAnalytics())
    val oauthAnalytics: StateFlow<OAuthAnalytics> = _oauthAnalytics.asStateFlow()
    
    private val _activeSessions = mutableMapOf<String, OAuthSession>()
    
    /**
     * Links an OAuth account to an existing user account.
     * 
     * @param user The user to link the account to
     * @param provider The OAuth provider
     * @param oauthData The OAuth data from the provider
     * @return Result indicating success or failure
     */
    suspend fun linkAccount(
        user: User,
        provider: OAuthProvider,
        oauthData: OAuthData
    ): Result<Unit> {
        return try {
            logger.info("general", "Linking OAuth account for user: ${user.displayName}, provider: ${provider.name}")
            
            _oauthState.value = EnhancedOAuthState.LinkingAccount(provider)
            
            // Check if user has reached the maximum number of linked accounts
            val currentLinkedAccounts = _linkedAccounts.value[user.id] ?: emptyList()
            if (currentLinkedAccounts.size >= MAX_LINKED_ACCOUNTS) {
                return Result.failure(OAuthException("Maximum number of linked accounts reached"))
            }
            
            // Check if this provider is already linked
            if (currentLinkedAccounts.any { it.provider == provider }) {
                return Result.failure(OAuthException("Account already linked with ${provider.name}"))
            }
            
            // Validate OAuth data
            val validationResult = validateOAuthData(oauthData)
            if (validationResult.isFailure) {
                return validationResult
            }
            
            // Create linked account
            val linkedAccount = LinkedAccount(
                userId = user.id,
                provider = provider,
                providerUserId = oauthData.providerUserId,
                email = oauthData.email,
                displayName = oauthData.displayName,
                profilePicture = oauthData.profilePicture,
                accessToken = oauthData.accessToken,
                refreshToken = oauthData.refreshToken,
                tokenExpiry = oauthData.tokenExpiry,
                linkedAt = Clock.System.now()(),
                lastUsed = Clock.System.now()()
            )
            
            // Store the linked account
            val updatedAccounts = currentLinkedAccounts + linkedAccount
            _linkedAccounts.value = _linkedAccounts.value + (user.id to updatedAccounts)
            
            // Update analytics
            updateAnalytics(provider, OAuthAction.ACCOUNT_LINKED)
            
            _oauthState.value = EnhancedOAuthState.Idle
            
            // Dispatch success event
            eventBus.dispatch(AuthEvent.OAuth.AccountLinked(user, provider, linkedAccount))
            
            logger.info("general", "OAuth account linked successfully for user: ${user.displayName}, provider: ${provider.name}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("oauth", "Failed to link OAuth account", e)
            _oauthState.value = EnhancedOAuthState.Error(e)
            _oauthState.value = EnhancedOAuthState.Idle
            
            // Dispatch failure event
            eventBus.dispatch(AuthEvent.OAuth.AccountLinkFailed(user, provider, e))
            
            Result.failure(e)
        }
    }
    
    /**
     * Unlinks an OAuth account from a user.
     * 
     * @param user The user to unlink the account from
     * @param provider The OAuth provider to unlink
     * @return Result indicating success or failure
     */
    suspend fun unlinkAccount(user: User, provider: OAuthProvider): Result<Unit> {
        return try {
            logger.info("general", "Unlinking OAuth account for user: ${user.displayName}, provider: ${provider.name}")
            
            _oauthState.value = EnhancedOAuthState.UnlinkingAccount(provider)
            
            val currentLinkedAccounts = _linkedAccounts.value[user.id] ?: emptyList()
            val accountToRemove = currentLinkedAccounts.find { it.provider == provider }
            
            if (accountToRemove == null) {
                return Result.failure(OAuthException("No linked account found for ${provider.name}"))
            }
            
            // Check if this is the last authentication method
            if (currentLinkedAccounts.size == 1) {
                return Result.failure(OAuthException("Cannot unlink the last authentication method"))
            }
            
            // Remove the linked account
            val updatedAccounts = currentLinkedAccounts.filter { it.provider != provider }
            _linkedAccounts.value = _linkedAccounts.value + (user.id to updatedAccounts)
            
            // Update analytics
            updateAnalytics(provider, OAuthAction.ACCOUNT_UNLINKED)
            
            _oauthState.value = EnhancedOAuthState.Idle
            
            // Dispatch success event
            eventBus.dispatch(AuthEvent.OAuth.AccountUnlinked(user, provider, accountToRemove))
            
            logger.info("general", "OAuth account unlinked successfully for user: ${user.displayName}, provider: ${provider.name}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("oauth", "Failed to unlink OAuth account", e)
            _oauthState.value = EnhancedOAuthState.Error(e)
            _oauthState.value = EnhancedOAuthState.Idle
            
            // Dispatch failure event
            eventBus.dispatch(AuthEvent.OAuth.AccountUnlinkFailed(user, provider, e))
            
            Result.failure(e)
        }
    }
    
    /**
     * Signs in with a linked OAuth account.
     * 
     * @param provider The OAuth provider
     * @param oauthData The OAuth data from the provider
     * @return Result with the user if successful
     */
    suspend fun signInWithLinkedAccount(
        provider: OAuthProvider,
        oauthData: OAuthData
    ): Result<User> {
        return try {
            logger.info("general", "Signing in with linked OAuth account, provider: ${provider.name}")
            
            _oauthState.value = EnhancedOAuthState.SigningIn(provider)
            
            // Find the user with this linked account
            val linkedAccount = findLinkedAccount(provider, oauthData.providerUserId)
            if (linkedAccount == null) {
                return Result.failure(OAuthException("No linked account found for ${provider.name}"))
            }
            
            // Validate the OAuth data
            val validationResult = validateOAuthData(oauthData)
            if (validationResult.isFailure) {
                return validationResult.map { null }
            }
            
            // Update the linked account with new tokens
            updateLinkedAccount(linkedAccount, oauthData)
            
            // Get the user from the auth engine
            val user = authEngineCurrentUser()
            if (user == null) {
                return Result.failure(OAuthException("User not found"))
            }
            
            // Update analytics
            updateAnalytics(provider, OAuthAction.SIGN_IN)
            
            _oauthState.value = EnhancedOAuthState.Idle
            
            // Dispatch success event
            eventBus.dispatch(AuthEvent.OAuth.SignInWithLinkedAccount(user, provider, linkedAccount))
            
            logger.info("oath", "Sign in with linked OAuth account successful for user: ${user.displayName}")
            Result.success(user)
            
        } catch (e: Exception) {
            logger.error("oauth", "Failed to sign in with linked OAuth account", e)
            _oauthState.value = EnhancedOAuthState.Error(e)
            _oauthState.value = EnhancedOAuthState.Idle
            
            Result.failure(e)
        }
    }
    
    /**
     * Refreshes OAuth tokens for a linked account.
     * 
     * @param user The user to refresh tokens for
     * @param provider The OAuth provider
     * @return Result indicating success or failure
     */
    suspend fun refreshTokens(user: User, provider: OAuthProvider): Result<Unit> {
        return try {
            logger.info("general", "Refreshing OAuth tokens for user: ${user.displayName}, provider: ${provider.name}")
            
            _oauthState.value = EnhancedOAuthState.RefreshingTokens(provider)
            
            val linkedAccount = getLinkedAccount(user, provider)
            if (linkedAccount == null) {
                return Result.failure(OAuthException("No linked account found for ${provider.name}"))
            }
            
            // Check if refresh is needed
            if (!isTokenRefreshNeeded(linkedAccount)) {
                logger.info("general", "Token refresh not needed for user: ${user.displayName}, provider: ${provider.name}")
                return Result.success(Unit)
            }
            
            // In a real implementation, this would:
            // 1. Call the OAuth provider's token refresh endpoint
            // 2. Update the stored tokens
            // 3. Handle refresh token rotation if supported
            
            // Simulate token refresh
            kotlinx.coroutines.delay(1000)
            
            // Update the linked account with new expiry time
            val updatedAccount = linkedAccount.copy(
                tokenExpiry = Clock.System.now()().plus(1, java.time.temporal.ChronoUnit.HOURS),
                lastUsed = Clock.System.now()()
            )
            
            updateLinkedAccountInStorage(user.id, updatedAccount)
            
            // Update analytics
            updateAnalytics(provider, OAuthAction.TOKENS_REFRESHED)
            
            _oauthState.value = EnhancedOAuthState.Idle
            
            // Dispatch success event
            eventBus.dispatch(AuthEvent.OAuth.TokensRefreshed(user, provider))
            
            logger.info("general", "OAuth tokens refreshed successfully for user: ${user.displayName}, provider: ${provider.name}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("oauth", "Failed to refresh OAuth tokens", e)
            _oauthState.value = EnhancedOAuthState.Error(e)
            _oauthState.value = EnhancedOAuthState.Idle
            
            Result.failure(e)
        }
    }
    
    /**
     * Gets all linked accounts for a user.
     * 
     * @param user The user to get linked accounts for
     * @return List of linked accounts
     */
    fun getLinkedAccounts(user: User): List<LinkedAccount> {
        return _linkedAccounts.value[user.id] ?: emptyList()
    }
    
    /**
     * Gets a specific linked account for a user.
     * 
     * @param user The user to get the account for
     * @param provider The OAuth provider
     * @return The linked account, or null if not found
     */
    fun getLinkedAccount(user: User, provider: OAuthProvider): LinkedAccount? {
        return _linkedAccounts.value[user.id]?.find { it.provider == provider }
    }
    
    /**
     * Checks if a user has a linked account with a specific provider.
     * 
     * @param user The user to check
     * @param provider The OAuth provider
     * @return true if the account is linked, false otherwise
     */
    fun hasLinkedAccount(user: User, provider: OAuthProvider): Boolean {
        return getLinkedAccount(user, provider) != null
    }
    
    /**
     * Gets OAuth analytics data.
     * 
     * @return OAuth analytics
     */
    fun getAnalytics(): OAuthAnalytics {
        return _oauthAnalytics.value
    }
    
    // Private implementation methods
    
    private fun validateOAuthData(oauthData: OAuthData): Result<Unit> {
        return try {
            if (oauthData.providerUserId.isBlank()) {
                return Result.failure(OAuthException("Invalid provider user ID"))
            }
            
            if (oauthData.accessToken.isBlank()) {
                return Result.failure(OAuthException("Invalid access token"))
            }
            
            if (oauthData.tokenExpiry.isBefore(Clock.System.now()())) {
                return Result.failure(OAuthException("Access token has expired"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun findLinkedAccount(provider: OAuthProvider, providerUserId: String): LinkedAccount? {
        return _linkedAccounts.value.values.flatten().find { 
            it.provider == provider && it.providerUserId == providerUserId 
        }
    }
    
    private fun updateLinkedAccount(linkedAccount: LinkedAccount, oauthData: OAuthData) {
        val updatedAccount = linkedAccount.copy(
            accessToken = oauthData.accessToken,
            refreshToken = oauthData.refreshToken,
            tokenExpiry = oauthData.tokenExpiry,
            lastUsed = Clock.System.now()()
        )
        
        updateLinkedAccountInStorage(linkedAccount.userId, updatedAccount)
    }
    
    private fun updateLinkedAccountInStorage(userId: String, updatedAccount: LinkedAccount) {
        val currentAccounts = _linkedAccounts.value[userId] ?: emptyList()
        val updatedAccounts = currentAccounts.map { 
            if (it.provider == updatedAccount.provider) updatedAccount else it 
        }
        _linkedAccounts.value = _linkedAccounts.value + (userId to updatedAccounts)
    }
    
    private fun isTokenRefreshNeeded(linkedAccount: LinkedAccount): Boolean {
        val bufferTime = Clock.System.now()().plus(OAUTH_TOKEN_EXPIRY_BUFFER_MINUTES, java.time.temporal.ChronoUnit.MINUTES)
        return linkedAccount.tokenExpiry.isBefore(bufferTime)
    }
    
    private fun updateAnalytics(provider: OAuthProvider, action: OAuthAction) {
        val currentAnalytics = _oauthAnalytics.value
        val providerStats = currentAnalytics.providerStatsOrPut(provider) { ProviderStats() }
        
        val updatedStats = when (action) {
            OAuthAction.ACCOUNT_LINKED -> providerStats.copy(accountsLinked = providerStats.accountsLinked + 1)
            OAuthAction.ACCOUNT_UNLINKED -> providerStats.copy(accountsUnlinked = providerStats.accountsUnlinked + 1)
            OAuthAction.SIGN_IN -> providerStats.copy(signIns = providerStats.signIns + 1)
            OAuthAction.TOKENS_REFRESHED -> providerStats.copy(tokensRefreshed = providerStats.tokensRefreshed + 1)
        }
        
        val updatedProviderStats = currentAnalytics.providerStats + (provider to updatedStats)
        _oauthAnalytics.value = currentAnalytics.copy(
            providerStats = updatedProviderStats,
            lastUpdated = Clock.System.now()()
        )
    }
}

/**
 * Represents the state of enhanced OAuth operations.
 */
sealed class EnhancedOAuthState {
    object Idle : EnhancedOAuthState()
    data class LinkingAccount(val provider: OAuthProvider) : EnhancedOAuthState()
    data class UnlinkingAccount(val provider: OAuthProvider) : EnhancedOAuthState()
    data class SigningIn(val provider: OAuthProvider) : EnhancedOAuthState()
    data class RefreshingTokens(val provider: OAuthProvider) : EnhancedOAuthState()
    data class Error(val error: Throwable) : EnhancedOAuthState()
}

/**
 * Represents a linked OAuth account.
 */
data class LinkedAccount(
    val userId: String,
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val displayName: String?,
    val profilePicture: String?,
    val accessToken: String,
    val refreshToken: String?,
    val tokenExpiry: Instant,
    val linkedAt: Instant,
    val lastUsed: Instant
)

/**
 * Represents OAuth data from a provider.
 */
data class OAuthData(
    val providerUserId: String,
    val email: String?,
    val displayName: String?,
    val profilePicture: String?,
    val accessToken: String,
    val refreshToken: String?,
    val tokenExpiry: Instant,
    val scopes: List<String> = emptyList()
)

/**
 * Represents OAuth analytics data.
 */
data class OAuthAnalytics(
    val providerStats: Map<OAuthProvider, ProviderStats> = emptyMap(),
    val lastUpdated: Instant = Clock.System.now()()
)

/**
 * Represents statistics for a specific OAuth provider.
 */
data class ProviderStats(
    val accountsLinked: Int = 0,
    val accountsUnlinked: Int = 0,
    val signIns: Int = 0,
    val tokensRefreshed: Int = 0
)

/**
 * Represents OAuth actions for analytics.
 */
enum class OAuthAction {
    ACCOUNT_LINKED,
    ACCOUNT_UNLINKED,
    SIGN_IN,
    TOKENS_REFRESHED
}

/**
 * Represents an OAuth session.
 */
data class OAuthSession(
    val userId: String,
    val provider: OAuthProvider,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant,
    val scopes: List<String>
)

/**
 * Exception thrown during OAuth operations.
 */
class OAuthException(message: String) : Exception(message)