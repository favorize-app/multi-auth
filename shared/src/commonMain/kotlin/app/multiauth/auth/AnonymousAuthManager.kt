package app.multiauth.auth

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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/**
 * Manager for anonymous authentication.
 * Allows users to access the app without creating a permanent account.
 * Useful for trial users, guest access, and conversion funnel optimization.
 */
class AnonymousAuthManager(
    private val authEngine: AuthEngine,
    private val eventBus: EventBus = EventBus.getInstance()
) {
    
    private val logger = Logger.getLogger(this::class)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        private const val ANONYMOUS_SESSION_DURATION_HOURS = 24L
        private const val MAX_ANONYMOUS_SESSIONS = 5
        private const val ANONYMOUS_USER_PREFIX = "anon_"
    }
    
    private val _anonymousState = MutableStateFlow<AnonymousAuthState>(AnonymousAuthState.Idle)
    val anonymousState: StateFlow<AnonymousAuthState> = _anonymousState.asStateFlow()
    
    private val _anonymousUsers = MutableStateFlow<Map<String, AnonymousUser>>(emptyMap())
    val anonymousUsers: StateFlow<Map<String, AnonymousUser>> = _anonymousUsers.asStateFlow()
    
    private val _conversionMetrics = MutableStateFlow(AnonymousConversionMetrics())
    val conversionMetrics: StateFlow<AnonymousConversionMetrics> = _conversionMetrics.asStateFlow()
    
    /**
     * Creates an anonymous user session.
     * 
     * @param deviceId Optional device identifier for session tracking
     * @param metadata Additional metadata about the anonymous session
     * @return Result with the anonymous user
     */
    suspend fun createAnonymousSession(
        deviceId: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): Result<User> {
        return try {
            logger.info("auth", "Creating anonymous user session")
            
            _anonymousState.value = AnonymousAuthState.CreatingSession
            
            // Check if we've reached the maximum number of anonymous sessions
            if (_anonymousUsers.value.size >= MAX_ANONYMOUS_SESSIONS) {
                return Result.failure(AnonymousAuthException("Maximum anonymous sessions reached"))
            }
            
            // Generate unique anonymous user ID
            val anonymousId = generateAnonymousId()
            val sessionId = UUID.randomUUID().toString()
            
            // Create anonymous user
            val anonymousUser = AnonymousUser(
                id = anonymousId,
                sessionId = sessionId,
                deviceId = deviceId,
                createdAt = Clock.System.now(),
                expiresAt = (Clock.System.now() + ANONYMOUS_SESSION_DURATION_HOURS.hours),
                metadata = metadata,
                isActive = true
            )
            
            // Create user object for the auth engine
            val user = User(
                id = anonymousId,
                email = null,
                displayName = "Guest User",
                emailVerified = false,
                createdAt = anonymousUser.createdAt,
                updatedAt = anonymousUser.createdAt,
                isAnonymous = true,
                anonymousSessionId = sessionId
            )
            
            // Store anonymous user
            _anonymousUsers.value = _anonymousUsers.value + (anonymousId to anonymousUser)
            
            // Update conversion metrics
            updateConversionMetrics(AnonymousAction.SESSION_CREATED)
            
            _anonymousState.value = AnonymousAuthState.Idle
            
            // Dispatch success event
            eventBus.dispatch(AuthEvent.Anonymous.AnonymousSessionCreated(user, anonymousUser))
            
            logger.info("auth", "Anonymous user session created: $anonymousId")
            Result.success(user)
            
        } catch (e: Exception) {
            logger.error("auth", "Failed to create anonymous session", e)
            _anonymousState.value = AnonymousAuthState.Error(e)
            _anonymousState.value = AnonymousAuthState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Converts an anonymous user to a permanent account.
     * 
     * @param anonymousUser The anonymous user to convert
     * @param email User's email address
     * @param password User's password
     * @param displayName User's display name
     * @return Result with the converted user
     */
    suspend fun convertToPermanentAccount(
        anonymousUser: User,
        email: String,
        password: String,
        displayName: String
    ): Result<User> {
        return try {
            logger.info("auth", "Converting anonymous user to permanent account: ${anonymousUser.id}")
            
            _anonymousState.value = AnonymousAuthState.ConvertingAccount
            
            // Validate input
            if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
                return Result.failure(AnonymousAuthException("Email, password, and display name are required"))
            }
            
            if (password.length < 8) {
                return Result.failure(AnonymousAuthException("Password must be at least 8 characters"))
            }
            
            // Create permanent user account
            val permanentUser = User(
                id = UUID.randomUUID().toString(),
                email = email,
                displayName = displayName,
                emailVerified = false,
                createdAt = anonymousUser.createdAt,
                updatedAt = Clock.System.now(),
                isAnonymous = false,
                anonymousSessionId = null
            )
            
            // Store user credentials (in a real app, this would hash the password)
            // For demo purposes, we'll just simulate the process
            
            // Remove anonymous user
            _anonymousUsers.value = _anonymousUsers.value - anonymousUser.id
            
            // Update conversion metrics
            updateConversionMetrics(AnonymousAction.ACCOUNT_CONVERTED)
            
            _anonymousState.value = AnonymousAuthState.Idle
            
            // Dispatch success event
            eventBus.dispatch(AuthEvent.Anonymous.AnonymousUserConverted(anonymousUser, permanentUser))
            
            logger.info("auth", "Anonymous user converted to permanent account: ${anonymousUser.id} -> ${permanentUser.id}")
            Result.success(permanentUser)
            
        } catch (e: Exception) {
            logger.error("auth", "Failed to convert anonymous user", e)
            _anonymousState.value = AnonymousAuthState.Error(e)
            _anonymousState.value = AnonymousAuthState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Extends an anonymous user session.
     * 
     * @param anonymousUser The anonymous user to extend
     * @param additionalHours Hours to extend the session by
     * @return Result indicating success or failure
     */
    suspend fun extendSession(
        anonymousUser: User,
        additionalHours: Long
    ): Result<Unit> {
        return try {
            logger.info("auth", "Extending anonymous user session: ${anonymousUser.id}")
            
            val anonymousUserData = _anonymousUsers.value[anonymousUser.id]
            if (anonymousUserData == null) {
                return Result.failure(AnonymousAuthException("Anonymous user not found"))
            }
            
            // Extend session
            val updatedAnonymousUser = anonymousUserData.copy(
                expiresAt = anonymousUserData.expiresAt + additionalHours.hours,
                updatedAt = Clock.System.now()
            )
            
            _anonymousUsers.value = _anonymousUsers.value + (anonymousUser.id to updatedAnonymousUser)
            
            // Update conversion metrics
            updateConversionMetrics(AnonymousAction.SESSION_EXTENDED)
            
            // Dispatch success event
            eventBus.dispatch(AuthEvent.Anonymous.AnonymousSessionExtended(anonymousUser, additionalHours))
            
            logger.info("auth", "Anonymous user session extended: ${anonymousUser.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("auth", "Failed to extend anonymous session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Terminates an anonymous user session.
     * 
     * @param anonymousUser The anonymous user to terminate
     * @return Result indicating success or failure
     */
    suspend fun terminateSession(anonymousUser: User): Result<Unit> {
        return try {
            logger.info("auth", "Terminating anonymous user session: ${anonymousUser.id}")
            
            val anonymousUserData = _anonymousUsers.value[anonymousUser.id]
            if (anonymousUserData == null) {
                return Result.failure(AnonymousAuthException("Anonymous user not found"))
            }
            
            // Mark session as inactive
            val updatedAnonymousUser = anonymousUserData.copy(
                isActive = false,
                terminatedAt = Clock.System.now()
            )
            
            _anonymousUsers.value = _anonymousUsers.value + (anonymousUser.id to updatedAnonymousUser)
            
            // Update conversion metrics
            updateConversionMetrics(AnonymousAction.SESSION_TERMINATED)
            
            // Dispatch success event
            eventBus.dispatch(AuthEvent.Anonymous.AnonymousSessionTerminated(anonymousUser))
            
            logger.info("auth", "Anonymous user session terminated: ${anonymousUser.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("auth", "Failed to terminate anonymous session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Gets anonymous user statistics.
     * 
     * @return AnonymousUserStats with current statistics
     */
    fun getAnonymousUserStats(): AnonymousUserStats {
        val currentUsers = _anonymousUsers.value
        val activeUsers = currentUsers.values.count { it.isActive }
        val expiredUsers = currentUsers.values.count { it.expiresAt < Clock.System.now() }
        val convertedUsers = _conversionMetrics.value.accountsConverted
        
        return AnonymousUserStats(
            totalUsers = currentUsers.size,
            activeUsers = activeUsers,
            expiredUsers = expiredUsers,
            convertedUsers = convertedUsers.toInt(),
            conversionRate = if (currentUsers.isNotEmpty()) {
                convertedUsers.toDouble() / currentUsers.size
            } else 0.0
        )
    }
    
    /**
     * Cleans up expired anonymous sessions.
     */
    fun cleanupExpiredSessions() {
        scope.launch {
            try {
                val now = Clock.System.now()
                val currentUsers = _anonymousUsers.value
                val expiredUsers = currentUsers.filter { (_, user) ->
                    user.expiresAt < now && user.isActive
                }
                
                if (expiredUsers.isNotEmpty()) {
                    // Mark expired users as inactive
                    expiredUsers.forEach { (id, user) ->
                        val updatedUser = user.copy(
                            isActive = false,
                            expiredAt = now
                        )
                        _anonymousUsers.value = _anonymousUsers.value + (id to updatedUser)
                    }
                    
                    logger.info("auth", "Cleaned up ${expiredUsers.size} expired anonymous sessions")
                }
            } catch (e: Exception) {
                logger.error("auth", "Failed to cleanup expired sessions", e)
            }
        }
    }
    
    /**
     * Gets conversion analytics data.
     * 
     * @return AnonymousConversionMetrics
     */
    fun getConversionAnalytics(): AnonymousConversionMetrics {
        return _conversionMetrics.value
    }
    
    // Private implementation methods
    
    private fun generateAnonymousId(): String {
        return ANONYMOUS_USER_PREFIX + UUID.randomUUID().toString().replace("-", "").take(12)
    }
    
    private fun updateConversionMetrics(action: AnonymousAction) {
        val currentMetrics = _conversionMetrics.value
        
        val updatedMetrics = when (action) {
            AnonymousAction.SESSION_CREATED -> currentMetrics.copy(
                sessionsCreated = currentMetrics.sessionsCreated + 1
            )
            AnonymousAction.ACCOUNT_CONVERTED -> currentMetrics.copy(
                accountsConverted = currentMetrics.accountsConverted + 1
            )
            AnonymousAction.SESSION_EXTENDED -> currentMetrics.copy(
                sessionsExtended = currentMetrics.sessionsExtended + 1
            )
            AnonymousAction.SESSION_TERMINATED -> currentMetrics.copy(
                sessionsTerminated = currentMetrics.sessionsTerminated + 1
            )
        }
        
        _conversionMetrics.value = updatedMetrics
    }
}

/**
 * Represents the state of anonymous authentication operations.
 */
sealed class AnonymousAuthState {
    object Idle : AnonymousAuthState()
    object CreatingSession : AnonymousAuthState()
    object ConvertingAccount : AnonymousAuthState()
    data class Error(val error: Throwable) : AnonymousAuthState()
}

/**
 * Represents an anonymous user session.
 */
data class AnonymousUser(
    val id: String,
    val sessionId: String,
    val deviceId: String?,
    val createdAt: Instant,
    val expiresAt: Instant,
    val metadata: Map<String, Any>,
    val isActive: Boolean,
    val updatedAt: Instant = createdAt,
    val terminatedAt: Instant? = null,
    val expiredAt: Instant? = null
)

/**
 * Represents anonymous user statistics.
 */
data class AnonymousUserStats(
    val totalUsers: Int,
    val activeUsers: Int,
    val expiredUsers: Int,
    val convertedUsers: Int,
    val conversionRate: Double
)

/**
 * Represents anonymous conversion metrics.
 */
data class AnonymousConversionMetrics(
    val sessionsCreated: Long = 0,
    val accountsConverted: Long = 0,
    val sessionsExtended: Long = 0,
    val sessionsTerminated: Long = 0,
    val lastUpdated: Instant = Clock.System.now()
)

/**
 * Represents anonymous user actions for analytics.
 */
enum class AnonymousAction {
    SESSION_CREATED,
    ACCOUNT_CONVERTED,
    SESSION_EXTENDED,
    SESSION_TERMINATED
}

/**
 * Exception thrown during anonymous authentication operations.
 */
class AnonymousAuthException(message: String) : Exception(message)