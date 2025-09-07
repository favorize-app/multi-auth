package app.multiauth.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import app.multiauth.models.*
import app.multiauth.models.SessionError
import app.multiauth.models.StorageFailure
import app.multiauth.models.TokenError
import app.multiauth.security.JwtTokenManager
import app.multiauth.security.TokenValidationResult
import app.multiauth.storage.SecureStorage
import app.multiauth.util.Logger
import app.multiauth.events.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.time.Duration.Companion.minutes

/**
 * Manages user sessions, token storage, and automatic token refresh.
 * Handles secure storage of authentication tokens and session data.
 */
class SessionManager(
    private val secureStorage: SecureStorage,
    private val eventBus: EventBus = EventBusInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jwtTokenManager = JwtTokenManager()
    
    private val _currentSession = MutableStateFlow<UserSession?>(null)
    val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()
    
    private val _isSessionValid = MutableStateFlow(false)
    val isSessionValid: StateFlow<Boolean> = _isSessionValid.asStateFlow()
    
    // Session storage keys
    private companion object {
        const val SESSION_KEY = "user_session"
        const val TOKEN_PAIR_KEY = "token_pair"
        const val SESSION_METADATA_KEY = "session_metadata"
    }
    
    init {
        Logger.info("SessionManager", "SessionManager initialized")
        
        // Start session monitoring
        scope.launch {
            startSessionMonitoring()
        }
        
        // Try to restore existing session
        scope.launch {
            restoreSession()
        }
    }
    
    /**
     * Creates a new session for a user with tokens.
     */
    suspend fun createSession(user: User, tokens: TokenPair): AuthResult<UserSession> {
        Logger.debug("SessionManager", "Creating session for user: ${user.id}")
        
        return try {
            val now = Clock.System.now()
            val session = UserSession(
                user = user,
                sessionId = generateSessionId(),
                createdAt = now,
                lastAccessedAt = now,
                expiresAt = tokens.expiresAt,
                isActive = true
            )
            
            val sessionMetadata = SessionMetadata(
                sessionId = session.sessionId,
                userId = user.id,
                deviceInfo = getDeviceInfo(),
                createdAt = now,
                lastRefreshAt = now
            )
            
            // Store session data securely
            val sessionStored = secureStorage.store(SESSION_KEY, Json.encodeToString(session))
            val tokensStored = secureStorage.store(TOKEN_PAIR_KEY, Json.encodeToString(tokens))
            val metadataStored = secureStorage.store(SESSION_METADATA_KEY, Json.encodeToString(sessionMetadata))
            
            if (sessionStored && tokensStored && metadataStored) {
                _currentSession.value = session
                _isSessionValid.value = true

                val eventMetadata = EventMetadata(source = "SessionManager")
                eventBus.dispatch(AuthEvent.Session.Created(session), eventMetadata)
                Logger.info("SessionManager", "Session created successfully for user: ${user.id}")
                
                AuthResult.Success(session)
            } else {
                val eventMetadata = EventMetadata(source = "SessionManager")
                val error = AuthError.StorageFailure("Failed to store session data securely")
                eventBus.dispatch(AuthEvent.Session.Error(error), eventMetadata)
                Logger.error("SessionManager", "Failed to store session data securely")
                AuthResult.Failure(error)
            }
        } catch (e: Exception) {
            val eventMetadata = EventMetadata(source = "SessionManager")
            val error = AuthError.UnknownError("Failed to create session: ${e.message}", e)
            eventBus.dispatch(AuthEvent.Session.Error(error), eventMetadata)
            Logger.error("SessionManager", "Failed to create session", e)
            AuthResult.Failure(error)
        }
    }
    
    /**
     * Refreshes the current session tokens.
     */
    suspend fun refreshSession(): AuthResult<TokenPair> {
        Logger.debug("SessionManager", "Refreshing session tokens")
        
        return try {
            val eventMetadata = EventMetadata(source = "SessionManager")
            val currentTokens = getCurrentTokens()
            if (currentTokens == null) {
                return AuthResult.Failure(
                    AuthError.SessionError("No active session to refresh")
                )
            }
            
            val currentSession = _currentSession.value
            if (currentSession == null) {
                return AuthResult.Failure(
                    AuthError.SessionError("No active session found")
                )
            }
            
            // Validate refresh token
            when (val validationResult = jwtTokenManager.validateToken(currentTokens.refreshToken)) {
                is TokenValidationResult.Valid -> {
                    if (validationResult.payload.tokenType != "refresh") {
                        return AuthResult.Failure(
                            AuthError.InvalidToken("Invalid refresh token type")
                        )
                    }
                    
                    // Create new tokens
                    val newAccessToken = jwtTokenManager.createAccessToken(
                        currentSession.user.id, 
                        currentSession.user.email
                    )
                    val newRefreshToken = jwtTokenManager.createRefreshToken(currentSession.user.id)
                    val newTokens = TokenPair(
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken,
                        expiresAt = Clock.System.now() + 30.minutes
                    )
                    
                    // Update stored tokens
                    val tokensStored = secureStorage.store(TOKEN_PAIR_KEY, Json.encodeToString(newTokens))
                    if (!tokensStored) {
                        return AuthResult.Failure(
                            AuthError.SessionError("Failed to store refreshed tokens")
                        )
                    }
                    
                    // Update session expiration
                    val updatedSession = currentSession.copy(
                        expiresAt = newTokens.expiresAt,
                        lastAccessedAt = Clock.System.now()
                    )
                    val sessionStored = secureStorage.store(SESSION_KEY, Json.encodeToString(updatedSession))
                    if (sessionStored) {
                        _currentSession.value = updatedSession
                    }
                    
                    // Update metadata
                    updateSessionMetadata()
                    
                    eventBus.dispatch(AuthEvent.Session.SessionRefreshed(updatedSession), eventMetadata)
                    Logger.info("SessionManager", "Session refreshed successfully")
                    
                    AuthResult.Success(newTokens)
                }
                
                is TokenValidationResult.Expired -> {
                    val session = _currentSession.value
                    if (session != null) {
                        eventBus.dispatch(AuthEvent.Session.SessionExpired(session), eventMetadata)
                    }
                    invalidateSession()
                    AuthResult.Failure(AuthError.SessionError("Refresh token expired"))
                }
                
                is TokenValidationResult.Invalid -> {
                    Logger.warn("SessionManager", "Invalid refresh token: ${validationResult.reason}")
                    invalidateSession()
                    AuthResult.Failure(AuthError.SessionError("Invalid refresh token"))
                }
            }
            
        } catch (e: Exception) {
            Logger.error("SessionManager", "Failed to refresh session", e)
            AuthResult.Failure(AuthError.UnknownError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Validates the current session and tokens.
     */
    suspend fun validateSession(): AuthResult<Boolean> {
        Logger.debug("SessionManager", "Validating current session")
        
        return try {
            val session = _currentSession.value
            val tokens = getCurrentTokens()
            
            if (session == null || tokens == null) {
                _isSessionValid.value = false
                return AuthResult.Success(false)
            }
            
            val now = Clock.System.now()
            
            // Check session expiration
            if (now > session.expiresAt) {
                Logger.info("SessionManager", "Session has expired")
                invalidateSession()
                return AuthResult.Success(false)
            }
            
            // Validate access token
            when (val validationResult = jwtTokenManager.validateToken(tokens.accessToken)) {
                is TokenValidationResult.Valid -> {
                    _isSessionValid.value = true
                    
                    // Update last accessed time
                    val updatedSession = session.copy(lastAccessedAt = now)
                    secureStorage.store(SESSION_KEY, Json.encodeToString(updatedSession))
                    _currentSession.value = updatedSession
                    
                    AuthResult.Success(true)
                }
                
                is TokenValidationResult.Expired -> {
                    Logger.info("SessionManager", "Access token expired, attempting refresh")
                    
                    // Try to refresh automatically
                    when (refreshSession()) {
                        is AuthResult.Success -> {
                            _isSessionValid.value = true
                            AuthResult.Success(true)
                        }
                        is AuthResult.Failure -> {
                            _isSessionValid.value = false
                            AuthResult.Success(false)
                        }
                    }
                }
                
                is TokenValidationResult.Invalid -> {
                    Logger.warn("SessionManager", "Invalid access token: ${validationResult.reason}")
                    invalidateSession()
                    AuthResult.Success(false)
                }
            }
            
        } catch (e: Exception) {
            Logger.error("SessionManager", "Failed to validate session", e)
            _isSessionValid.value = false
            AuthResult.Failure(
                AuthError.UnknownError("Session validation failed: ${e.message}", e)
            )
        }
    }
    
    /**
     * Invalidates the current session and clears all stored data.
     */
    suspend fun invalidateSession(): AuthResult<Unit> {
        Logger.debug("SessionManager", "Invalidating current session")
        
        return try {
            val currentSession = _currentSession.value
            val metadata = EventMetadata(source = "SessionManager")

            // Clear stored data
            secureStorage.remove(SESSION_KEY)
            secureStorage.remove(TOKEN_PAIR_KEY)
            secureStorage.remove(SESSION_METADATA_KEY)
            
            // Clear in-memory state
            _currentSession.value = null
            _isSessionValid.value = false
            
            if (currentSession != null) {
                eventBus.dispatch(AuthEvent.Session.SessionExpired(currentSession), metadata)
            }
            
            Logger.info("SessionManager", "Session invalidated successfully")
            AuthResult.Success(Unit)
            
        } catch (e: Exception) {
            Logger.error("SessionManager", "Failed to invalidate session", e)
            AuthResult.Failure(
                AuthError.UnknownError("Session invalidation failed: ${e.message}", e)
            )
        }
    }
    
    /**
     * Gets the current access token if session is valid.
     */
    suspend fun getCurrentAccessToken(): String? {
        return if (_isSessionValid.value) {
            getCurrentTokens()?.accessToken
        } else {
            null
        }
    }
    
    /**
     * Gets the current user if session is valid.
     */
    fun getCurrentUser(): User? {
        return if (_isSessionValid.value) {
            _currentSession.value?.user
        } else {
            null
        }
    }
    
    /**
     * Gets session information for monitoring/debugging.
     */
    suspend fun getSessionInfo(): SessionInfo? {
        val session = _currentSession.value ?: return null
        val metadata = getSessionMetadata() ?: return null
        
        return SessionInfo(
            sessionId = session.sessionId,
            userId = session.user.id,
            userEmail = session.user.email,
            createdAt = session.createdAt,
            lastAccessedAt = session.lastAccessedAt,
            expiresAt = session.expiresAt,
            isActive = session.isActive && _isSessionValid.value,
            deviceInfo = metadata.deviceInfo,
            lastRefreshAt = metadata.lastRefreshAt
        )
    }
    
    private suspend fun getCurrentTokens(): TokenPair? {
        return try {
            val tokensJson = secureStorage.retrieve(TOKEN_PAIR_KEY)
            if (tokensJson != null) {
                Json.decodeFromString<TokenPair>(tokensJson)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.error("SessionManager", "Failed to retrieve tokens", e)
            null
        }
    }
    
    private suspend fun restoreSession() {
        val eventMetadata = EventMetadata(source = "SessionManager")
        try {
            val sessionJson = secureStorage.retrieve(SESSION_KEY)
            if (sessionJson != null) {
                val session = Json.decodeFromString<UserSession>(sessionJson)
                _currentSession.value = session
                
                // Validate restored session
                if (validateSession().isSuccess()) {
                    _currentSession.value = session
                    _isSessionValid.value = true
                    eventBus.dispatch(AuthEvent.Session.Created(session), eventMetadata)
                    Logger.info("SessionManager", "Session restored successfully")
                } else {
                    eventBus.dispatch(AuthEvent.Session.SessionExpired(session), eventMetadata)
                    Logger.warn("SessionManager", "Stored session has expired")
                    invalidateSession()
                }
                
                Logger.info("SessionManager", "Session restored for user: ${session.user.id}")
            }
        } catch (e: Exception) {
            val error = AuthError.UnknownError("Failed to restore session: ${e.message}", e)
            eventBus.dispatch(AuthEvent.Session.SessionError(error), eventMetadata)
            Logger.error("SessionManager", "Failed to restore session", e)
            invalidateSession()
        }
    }
    
    private suspend fun startSessionMonitoring() {
        // Monitor session validity every 5 minutes
        while (true) {
            kotlinx.coroutines.delay(5.minutes.inWholeMilliseconds)
            
            if (_currentSession.value != null) {
                validateSession()
            }
        }
    }
    
    private suspend fun updateSessionMetadata() {
        try {
            val metadataJson = secureStorage.retrieve(SESSION_METADATA_KEY)
            if (metadataJson != null) {
                val metadata = Json.decodeFromString<SessionMetadata>(metadataJson)
                val updatedMetadata = metadata.copy(lastRefreshAt = Clock.System.now())
                secureStorage.store(SESSION_METADATA_KEY, Json.encodeToString(updatedMetadata))
            }
        } catch (e: Exception) {
            Logger.error("SessionManager", "Failed to update session metadata", e)
        }
    }
    
    private suspend fun getSessionMetadata(): SessionMetadata? {
        return try {
            val metadataJson = secureStorage.retrieve(SESSION_METADATA_KEY)
            if (metadataJson != null) {
                Json.decodeFromString<SessionMetadata>(metadataJson)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.error("SessionManager", "Failed to get session metadata", e)
            null
        }
    }
    
    private fun generateSessionId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }
    
    private fun getDeviceInfo(): String {
        // In a real implementation, this would collect device information
        // For now, return a basic identifier
        return "MultiAuth-Client-${Clock.System.now().toEpochMilliseconds()}"
    }
}

/**
 * Represents an active user session.
 */
@Serializable
data class UserSession(
    val user: User,
    val sessionId: String,
    val createdAt: @kotlinx.serialization.Contextual Instant,
    val lastAccessedAt: @kotlinx.serialization.Contextual Instant,
    val expiresAt: @kotlinx.serialization.Contextual Instant,
    val isActive: Boolean = true
)

/**
 * Session metadata for monitoring and analytics.
 */
@Serializable
private data class SessionMetadata(
    val sessionId: String,
    val userId: String,
    val deviceInfo: String,
    val createdAt: @kotlinx.serialization.Contextual Instant,
    val lastRefreshAt: @kotlinx.serialization.Contextual Instant
)

/**
 * Session information for monitoring/debugging.
 */
data class SessionInfo(
    val sessionId: String,
    val userId: String,
    val userEmail: String?,
    val createdAt: Instant,
    val lastAccessedAt: Instant,
    val expiresAt: Instant,
    val isActive: Boolean,
    val deviceInfo: String,
    val lastRefreshAt: Instant
)

