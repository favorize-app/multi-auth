package app.multiauth.core

import app.multiauth.events.*
import app.multiauth.models.*
import app.multiauth.util.Logger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Manages user sessions, tokens, and authentication state persistence.
 * Handles token refresh, session expiration, and secure storage.
 */
class SessionManager private constructor(
    private val eventBus: EventBus = EventBus.getInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    private var refreshJob: kotlinx.coroutines.Job? = null
    
    init {
        Logger.info("SessionManager", "SessionManager initialized")
        startSessionMonitoring()
    }
    
    /**
     * Create a new session for a user.
     */
    suspend fun createSession(user: User, tokens: TokenPair): Session {
        Logger.debug("SessionManager", "Creating session for user: ${user.id}")
        
        val session = Session(
            id = generateSessionId(),
            userId = user.id,
            user = user,
            tokens = tokens,
            createdAt = Clock.System.now(),
            lastActivityAt = Clock.System.now(),
            expiresAt = tokens.expiresAt,
            isActive = true
        )
        
        _currentSession.value = session
        
        // Schedule token refresh
        scheduleTokenRefresh(session)
        
        // Dispatch session created event
        eventBus.dispatch(AuthEvent.Session.SessionCreated(session), "SessionManager")
        
        return session
    }
    
    /**
     * Update the current session with new tokens.
     */
    suspend fun updateSession(tokens: TokenPair): Session? {
        val currentSession = _currentSession.value ?: return null
        
        Logger.debug("SessionManager", "Updating session: ${currentSession.id}")
        
        val updatedSession = currentSession.copy(
            tokens = tokens,
            lastActivityAt = Clock.System.now(),
            expiresAt = tokens.expiresAt
        )
        
        _currentSession.value = updatedSession
        
        // Reschedule token refresh
        scheduleTokenRefresh(updatedSession)
        
        eventBus.dispatch(AuthEvent.Session.SessionRefreshed(updatedSession), "SessionManager")
        
        return updatedSession
    }
    
    /**
     * End the current session.
     */
    suspend fun endSession(): Boolean {
        val currentSession = _currentSession.value ?: return false
        
        Logger.debug("SessionManager", "Ending session: ${currentSession.id}")
        
        // Cancel refresh job
        refreshJob?.cancel()
        refreshJob = null
        
        // Mark session as inactive
        val endedSession = currentSession.copy(
            isActive = false,
            endedAt = Clock.System.now()
        )
        
        _currentSession.value = null
        
        eventBus.dispatch(AuthEvent.Session.SessionEnded(endedSession), "SessionManager")
        
        return true
    }
    
    /**
     * Check if the current session is valid.
     */
    fun isSessionValid(): Boolean {
        val session = _currentSession.value ?: return false
        if (!session.isActive) return false
        
        val now = Clock.System.now()
        return session.expiresAt > now
    }
    
    /**
     * Check if the current session is expired.
     */
    fun isSessionExpired(): Boolean {
        val session = _currentSession.value ?: return true
        if (!session.isActive) return true
        
        val now = Clock.System.now()
        return session.expiresAt <= now
    }
    
    /**
     * Get the current access token.
     */
    fun getCurrentAccessToken(): String? {
        return _currentSession.value?.tokens?.accessToken
    }
    
    /**
     * Get the current refresh token.
     */
    fun getCurrentRefreshToken(): String? {
        return _currentSession.value?.tokens?.refreshToken
    }
    
    /**
     * Update last activity timestamp.
     */
    fun updateLastActivity() {
        val currentSession = _currentSession.value ?: return
        
        val updatedSession = currentSession.copy(
            lastActivityAt = Clock.System.now()
        )
        
        _currentSession.value = updatedSession
    }
    
    /**
     * Refresh the current session's tokens.
     */
    suspend fun refreshTokens(): AuthResult<TokenPair> {
        val currentSession = _currentSession.value ?: return AuthResult.Failure(
            AuthError.SessionError("No active session to refresh")
        )
        
        if (_isRefreshing.value) {
            return AuthResult.Failure(
                AuthError.SessionError("Token refresh already in progress")
            )
        }
        
        Logger.debug("SessionManager", "Refreshing tokens for session: ${currentSession.id}")
        
        return try {
            _isRefreshing.value = true
            
            // TODO: Implement actual token refresh with backend
            // For now, simulate successful refresh
            val newTokens = createMockTokens(currentSession.userId)
            
            updateSession(newTokens)
            
            AuthResult.Success(newTokens)
            
        } catch (e: Exception) {
            val error = AuthError.SessionError("Token refresh failed: ${e.message}")
            eventBus.dispatch(AuthEvent.Session.SessionRefreshFailed(error), "SessionManager")
            AuthResult.Failure(error)
        } finally {
            _isRefreshing.value = false
        }
    }
    
    /**
     * Start monitoring the current session for expiration.
     */
    private fun startSessionMonitoring() {
        scope.launch {
            while (true) {
                delay(SESSION_CHECK_INTERVAL)
                
                if (isSessionExpired()) {
                    Logger.warn("SessionManager", "Session expired, dispatching event")
                    eventBus.dispatch(AuthEvent.Session.SessionExpired, "SessionManager")
                    
                    // Auto-refresh if possible
                    if (_currentSession.value?.tokens?.refreshToken != null) {
                        try {
                            refreshTokens()
                        } catch (e: Exception) {
                            Logger.error("SessionManager", "Auto-refresh failed: ${e.message}")
                            endSession()
                        }
                    } else {
                        endSession()
                    }
                }
            }
        }
    }
    
    /**
     * Schedule token refresh before expiration.
     */
    private fun scheduleTokenRefresh(session: Session) {
        refreshJob?.cancel()
        
        val timeUntilRefresh = session.expiresAt - Clock.System.now()
        val refreshDelay = timeUntilRefresh - REFRESH_BUFFER_TIME
        
        if (refreshDelay.isPositive()) {
            refreshJob = scope.launch {
                delay(refreshDelay.inWholeMilliseconds)
                
                if (_currentSession.value?.id == session.id) {
                    Logger.debug("SessionManager", "Auto-refreshing tokens for session: ${session.id}")
                    refreshTokens()
                }
            }
        }
    }
    
    /**
     * Generate a unique session ID.
     */
    private fun generateSessionId(): String {
        return "session_${Clock.System.now().toEpochMilliseconds()}_${kotlin.random.Random.nextInt(1000, 9999)}"
    }
    
    /**
     * Create mock tokens for development/testing.
     */
    private fun createMockTokens(userId: String): TokenPair {
        val now = Clock.System.now()
        val expiresAt = now + 30.minutes // 30 minutes
        
        return TokenPair(
            accessToken = "access_token_${userId}_${now.toEpochMilliseconds()}",
            refreshToken = "refresh_token_${userId}_${now.toEpochMilliseconds()}",
            expiresAt = expiresAt
        )
    }
    
    companion object {
        private val SESSION_CHECK_INTERVAL = 30.seconds // 30 seconds
        private val REFRESH_BUFFER_TIME = 5.minutes // 5 minutes
        
        private var INSTANCE: SessionManager? = null
        
        fun getInstance(): SessionManager {
            return INSTANCE ?: SessionManager().also { INSTANCE = it }
        }
        
        fun reset() {
            INSTANCE = null
        }
    }
}

/**
 * Represents a user session with authentication state.
 */
data class Session(
    val id: String,
    val userId: String,
    val user: User,
    val tokens: TokenPair,
    val createdAt: Instant,
    val lastActivityAt: Instant,
    val expiresAt: Instant,
    val isActive: Boolean,
    val endedAt: Instant? = null
)