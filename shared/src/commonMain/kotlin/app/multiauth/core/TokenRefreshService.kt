package app.multiauth.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import app.multiauth.models.*
import app.multiauth.models.TokenError
import app.multiauth.security.JwtTokenManager
import app.multiauth.security.TokenValidationResult
import app.multiauth.util.Logger
import app.multiauth.events.*
import app.multiauth.events.Session as AuthEventSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Service that automatically handles token refresh before expiration.
 * Monitors token expiration and refreshes tokens proactively.
 */
class TokenRefreshService(
    private val sessionManager: SessionManager,
    private val eventBus: EventBus = EventBusInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jwtTokenManager = JwtTokenManager()
    
    private var refreshJob: Job? = null
    private var isRunning = false
    
    // Configuration
    private val refreshThreshold = 5.minutes // Refresh when token expires in 5 minutes
    private val checkInterval = 30.seconds // Check token expiration every 30 seconds
    private val maxRetries = 3
    private val retryDelay = 10.seconds
    
    private val _refreshStatus = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)
    val refreshStatus: StateFlow<RefreshStatus> = _refreshStatus.asStateFlow()
    
    init {
        Logger.info("TokenRefreshService", "TokenRefreshService initialized")
    }
    
    /**
     * Starts the automatic token refresh monitoring.
     */
    fun startAutoRefresh() {
        if (isRunning) {
            Logger.warn("TokenRefreshService", "Auto refresh already running")
            return
        }
        
        Logger.info("TokenRefreshService", "Starting automatic token refresh monitoring")
        isRunning = true
        
        refreshJob = scope.launch {
            monitorTokenExpiration()
        }
    }
    
    /**
     * Stops the automatic token refresh monitoring.
     */
    fun stopAutoRefresh() {
        if (!isRunning) {
            Logger.warn("TokenRefreshService", "Auto refresh not running")
            return
        }
        
        Logger.info("TokenRefreshService", "Stopping automatic token refresh monitoring")
        isRunning = false
        
        refreshJob?.cancel()
        refreshJob = null
        _refreshStatus.value = RefreshStatus.Idle
    }
    
    /**
     * Manually triggers a token refresh.
     */
    suspend fun refreshTokensNow(): AuthResult<TokenPair> {
        Logger.debug("TokenRefreshService", "Manual token refresh requested")
        
        return performTokenRefresh()
    }
    
    /**
     * Checks if tokens need refresh based on expiration time.
     */
    suspend fun needsRefresh(): Boolean {
        val sessionInfo = sessionManager.getSessionInfo()
        if (sessionInfo == null || !sessionInfo.isActive) {
            return false
        }
        
        val now = Clock.System.now()
        val timeUntilExpiry = sessionInfo.expiresAt - now
        
        return timeUntilExpiry <= refreshThreshold
    }
    
    /**
     * Gets the time remaining until token expiration.
     */
    suspend fun getTimeUntilExpiry(): Duration? {
        val sessionInfo = sessionManager.getSessionInfo()
        if (sessionInfo == null || !sessionInfo.isActive) {
            return null
        }
        
        val now = Clock.System.now()
        val timeRemaining = sessionInfo.expiresAt - now
        
        return if (timeRemaining.isPositive()) timeRemaining else Duration.ZERO
    }
    
    /**
     * Gets refresh service statistics.
     */
    fun getRefreshStats(): RefreshStats {
        return RefreshStats(
            isAutoRefreshActive = isRunning,
            currentStatus = _refreshStatus.value,
            refreshThreshold = refreshThreshold,
            checkInterval = checkInterval
        )
    }
    
    private suspend fun monitorTokenExpiration() {
        Logger.debug("TokenRefreshService", "Starting token expiration monitoring")
        
        while (isRunning) {
            try {
                // Check if session exists and is valid
                val sessionValid = sessionManager.validateSession()
                
                if (sessionValid is AuthResult.Success && sessionValid.data) {
                    // Check if refresh is needed
                    if (needsRefresh()) {
                        Logger.info("TokenRefreshService", "Token refresh needed, starting refresh process")
                        performTokenRefreshWithRetry()
                    } else {
                        _refreshStatus.value = RefreshStatus.Monitoring
                    }
                } else {
                    _refreshStatus.value = RefreshStatus.NoSession
                }
                
            } catch (e: Exception) {
                Logger.error("TokenRefreshService", "Error in token monitoring", e)
                _refreshStatus.value = RefreshStatus.Error(e.message ?: "Unknown error")
            }
            
            // Wait before next check
            kotlinx.coroutines.delay(checkInterval.inWholeMilliseconds)
        }
        
        Logger.debug("TokenRefreshService", "Token expiration monitoring stopped")
    }
    
    private suspend fun performTokenRefreshWithRetry() {
        var retries = 0
        
        while (retries < maxRetries && isRunning) {
            when (val result = performTokenRefresh()) {
                is AuthResult.Success -> {
                    Logger.info("TokenRefreshService", "Token refresh successful")
                    _refreshStatus.value = RefreshStatus.Success(Clock.System.now())
                    return
                }
                
                is AuthResult.Failure -> {
                    retries++
                    Logger.warn("TokenRefreshService", "Token refresh failed (attempt $retries/$maxRetries): ${result.error.message}")
                    
                    if (retries < maxRetries) {
                        _refreshStatus.value = RefreshStatus.Retrying(retries)
                        kotlinx.coroutines.delay((retryDelay * retries).inWholeMilliseconds) // Exponential backoff
                    } else {
                        Logger.error("TokenRefreshService", "Token refresh failed after $maxRetries attempts")
                        _refreshStatus.value = RefreshStatus.Failed(result.error.message ?: "Unknown error")
                        
                        // Notify about refresh failure
                        val eventMetadata = EventMetadata(source="TokenRefreshService")
                        eventBus.dispatch(
                            AuthEventSession.SessionRefreshFailed(result.error), 
                            eventMetadata
                        )
                        
                        // Stop auto refresh on persistent failure
                        stopAutoRefresh()
                    }
                }
            }
        }
    }
    
    private suspend fun performTokenRefresh(): AuthResult<TokenPair> {
        _refreshStatus.value = RefreshStatus.Refreshing
        val metadata = EventMetadata(source = "TokenRefreshService")

        return try {
            val refreshResult = sessionManager.refreshSession()
            
            when (refreshResult) {
                is AuthResult.Success -> {
                    eventBus.dispatch(
                        AuthEventSession.TokensRefreshed(refreshResult.data),
                        metadata
                    )
                    refreshResult
                }
                
                is AuthResult.Failure -> {
                    // If refresh fails due to invalid/expired refresh token, 
                    // invalidate the session
                    if (refreshResult.error is AuthError.InvalidToken) {
                        Logger.warn("TokenRefreshService", "Refresh token invalid, invalidating session")
                        sessionManager.invalidateSession()
                    }
                    refreshResult
                }
            }
            
        } catch (e: Exception) {
            Logger.error("TokenRefreshService", "Unexpected error during token refresh", e)
            AuthResult.Failure(
                AuthError.UnknownError("Token refresh failed: ${e.message}", e)
            )
        }
    }
}

/**
 * Status of the token refresh service.
 */
sealed class RefreshStatus {
    data object Idle : RefreshStatus()
    data object Monitoring : RefreshStatus()
    data object NoSession : RefreshStatus()
    data object Refreshing : RefreshStatus()
    data class Retrying(val attempt: Int) : RefreshStatus()
    data class Success(val refreshedAt: Instant) : RefreshStatus()
    data class Failed(val reason: String) : RefreshStatus()
    data class Error(val message: String) : RefreshStatus()
}

/**
 * Statistics about the refresh service.
 */
data class RefreshStats(
    val isAutoRefreshActive: Boolean,
    val currentStatus: RefreshStatus,
    val refreshThreshold: Duration,
    val checkInterval: Duration
)