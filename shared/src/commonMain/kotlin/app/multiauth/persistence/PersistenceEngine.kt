package app.multiauth.persistence

import app.multiauth.models.*
import app.multiauth.events.AuthEvent
import app.multiauth.events.EventMetadata
import app.multiauth.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Core persistence engine that provides a clean abstraction over different storage backends.
 * Supports SQL databases, NoSQL stores, event streams, and more through lightweight adapters.
 */
interface PersistenceEngine {
    
    /**
     * User management operations
     */
    suspend fun saveUser(user: User): PersistenceResult<User>
    suspend fun findUserById(id: String): PersistenceResult<User?>
    suspend fun findUserByEmail(email: String): PersistenceResult<User?>
    suspend fun updateUser(user: User): PersistenceResult<User>
    suspend fun deleteUser(id: String): PersistenceResult<Boolean>
    
    /**
     * Session management operations
     */
    suspend fun saveSession(session: Session): PersistenceResult<Session>
    suspend fun findSessionById(id: String): PersistenceResult<Session?>
    suspend fun findSessionsByUserId(userId: String): PersistenceResult<List<Session>>
    suspend fun updateSession(session: Session): PersistenceResult<Session>
    suspend fun deleteSession(id: String): PersistenceResult<Boolean>
    suspend fun deleteExpiredSessions(): PersistenceResult<Int>
    
    /**
     * Event storage operations (for event sourcing)
     */
    suspend fun saveEvent(event: AuthEvent, metadata: EventMetadata): PersistenceResult<String>
    suspend fun getEvents(userId: String? = null, since: Instant? = null): Flow<Pair<AuthEvent, EventMetadata>>
    suspend fun getEventCount(userId: String? = null): PersistenceResult<Long>
    
    /**
     * OAuth account management
     */
    suspend fun saveOAuthAccount(account: OAuthAccount): PersistenceResult<OAuthAccount>
    suspend fun findOAuthAccountsByUserId(userId: String): PersistenceResult<List<OAuthAccount>>
    suspend fun deleteOAuthAccount(id: String): PersistenceResult<Boolean>
    
    /**
     * Audit logging
     */
    suspend fun saveAuditLog(log: AuditLog): PersistenceResult<AuditLog>
    suspend fun getAuditLogs(query: AuditLogQuery): Flow<AuditLog>
    
    /**
     * Health and maintenance
     */
    suspend fun healthCheck(): PersistenceResult<HealthStatus>
    suspend fun cleanup(): PersistenceResult<CleanupResult>
}

/**
 * Result wrapper for persistence operations
 */
sealed class PersistenceResult<out T> {
    data class Success<T>(val data: T) : PersistenceResult<T>()
    data class Failure(val error: PersistenceError) : PersistenceResult<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error
    }
}

/**
 * Persistence errors
 */
sealed class PersistenceError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    data class ConnectionError(override val message: String, override val cause: Throwable? = null) : PersistenceError(message, cause)
    data class QueryError(override val message: String, override val cause: Throwable? = null) : PersistenceError(message, cause)
    data class ConstraintViolation(override val message: String, val constraint: String) : PersistenceError(message)
    data class NotFound(override val message: String) : PersistenceError(message)
    data class Timeout(override val message: String) : PersistenceError(message)
    data class UnknownError(override val message: String, override val cause: Throwable? = null) : PersistenceError(message, cause)
}

/**
 * Health status for persistence backends
 */
data class HealthStatus(
    val isHealthy: Boolean,
    val latencyMs: Long,
    val connectionCount: Int,
    val lastError: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Cleanup operation result
 */
data class CleanupResult(
    val expiredSessionsRemoved: Int,
    val oldEventsRemoved: Int,
    val oldAuditLogsRemoved: Int,
    val totalSpaceFreed: Long = 0
)
