package app.multiauth.database

import app.multiauth.models.User
import app.multiauth.models.OAuthAccount
import app.multiauth.models.Session
import app.multiauth.models.AuditLog
import kotlinx.coroutines.flow.Flow

/**
 * Core database interface for the Multi-Auth system.
 * Provides methods for managing users, OAuth accounts, sessions, and audit logs.
 */
interface Database {
    
    /**
     * Initializes the database and creates necessary tables.
     * 
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean
    
    /**
     * Closes the database connection.
     */
    suspend fun close()
    
    /**
     * Checks if the database is initialized and ready for use.
     * 
     * @return true if ready, false otherwise
     */
    suspend fun isReady(): Boolean
    
    /**
     * Gets database information and statistics.
     * 
     * @return DatabaseInfo with details about the database
     */
    suspend fun getDatabaseInfo(): DatabaseInfo
    
    // User management
    
    /**
     * Creates a new user in the database.
     * 
     * @param user The user to create
     * @return The created user with generated ID
     */
    suspend fun createUser(user: User): User?
    
    /**
     * Updates an existing user in the database.
     * 
     * @param user The user to update
     * @return true if update successful, false otherwise
     */
    suspend fun updateUser(user: User): Boolean
    
    /**
     * Retrieves a user by their ID.
     * 
     * @param userId The user ID
     * @return The user if found, null otherwise
     */
    suspend fun getUserById(userId: String): User?
    
    /**
     * Retrieves a user by their email address.
     * 
     * @param email The email address
     * @return The user if found, null otherwise
     */
    suspend fun getUserByEmail(email: String): User?
    
    /**
     * Retrieves a user by their username.
     * 
     * @param username The username
     * @return The user if found, null otherwise
     */
    suspend fun getUserByUsername(username: String): User?
    
    /**
     * Retrieves all users from the database.
     * 
     * @return Flow of all users
     */
    fun getAllUsers(): Flow<List<User>>
    
    /**
     * Deletes a user from the database.
     * 
     * @param userId The user ID to delete
     * @return true if deletion successful, false otherwise
     */
    suspend fun deleteUser(userId: String): Boolean
    
    /**
     * Searches for users based on criteria.
     * 
     * @param query The search query
     * @param limit Maximum number of results
     * @return List of matching users
     */
    suspend fun searchUsers(query: String, limit: Int = 50): List<User>
    
    /**
     * Gets the total number of users in the database.
     * 
     * @return Total user count
     */
    suspend fun getUserCount(): Long
    
    // OAuth account management
    
    /**
     * Links an OAuth account to a user.
     * 
     * @param oauthAccount The OAuth account to link
     * @return true if linking successful, false otherwise
     */
    suspend fun linkOAuthAccount(oauthAccount: OAuthAccount): Boolean
    
    /**
     * Unlinks an OAuth account from a user.
     * 
     * @param providerId The OAuth provider ID
     * @param externalUserId The external user ID
     * @return true if unlinking successful, false otherwise
     */
    suspend fun unlinkOAuthAccount(providerId: String, externalUserId: String): Boolean
    
    /**
     * Gets OAuth accounts for a user.
     * 
     * @param userId The user ID
     * @return List of OAuth accounts
     */
    suspend fun getOAuthAccountsForUser(userId: String): List<OAuthAccount>
    
    /**
     * Gets a user by OAuth account.
     * 
     * @param providerId The OAuth provider ID
     * @param externalUserId The external user ID
     * @return The user if found, null otherwise
     */
    suspend fun getUserByOAuthAccount(providerId: String, externalUserId: String): User?
    
    /**
     * Updates an OAuth account.
     * 
     * @param oauthAccount The OAuth account to update
     * @return true if update successful, false otherwise
     */
    suspend fun updateOAuthAccount(oauthAccount: OAuthAccount): Boolean
    
    // Session management
    
    /**
     * Creates a new session.
     * 
     * @param session The session to create
     * @return true if creation successful, false otherwise
     */
    suspend fun createSession(session: Session): Boolean
    
    /**
     * Gets a session by its ID.
     * 
     * @param sessionId The session ID
     * @return The session if found, null otherwise
     */
    suspend fun getSessionById(sessionId: String): Session?
    
    /**
     * Gets active sessions for a user.
     * 
     * @param userId The user ID
     * @return List of active sessions
     */
    suspend fun getActiveSessionsForUser(userId: String): List<Session>
    
    /**
     * Updates a session.
     * 
     * @param session The session to update
     * @return true if update successful, false otherwise
     */
    suspend fun updateSession(session: Session): Boolean
    
    /**
     * Deletes a session.
     * 
     * @param sessionId The session ID to delete
     * @return true if deletion successful, false otherwise
     */
    suspend fun deleteSession(sessionId: String): Boolean
    
    /**
     * Deletes expired sessions.
     * 
     * @return Number of sessions deleted
     */
    suspend fun cleanupExpiredSessions(): Int
    
    // Audit logging
    
    /**
     * Logs an audit event.
     * 
     * @param auditLog The audit log entry to create
     * @return true if logging successful, false otherwise
     */
    suspend fun logAuditEvent(auditLog: AuditLog): Boolean
    
    /**
     * Gets audit logs for a user.
     * 
     * @param userId The user ID
     * @param limit Maximum number of results
     * @return List of audit logs
     */
    suspend fun getAuditLogsForUser(userId: String, limit: Int = 100): List<AuditLog>
    
    /**
     * Gets audit logs for a specific time range.
     * 
     * @param startTime Start time (inclusive)
     * @param endTime End time (exclusive)
     * @param limit Maximum number of results
     * @return List of audit logs
     */
    suspend fun getAuditLogsForTimeRange(
        startTime: Long,
        endTime: Long,
        limit: Int = 1000
    ): List<AuditLog>
    
    /**
     * Gets audit logs by event type.
     * 
     * @param eventType The event type to filter by
     * @param limit Maximum number of results
     * @return List of audit logs
     */
    suspend fun getAuditLogsByEventType(eventType: String, limit: Int = 1000): List<AuditLog>
    
    // Database maintenance
    
    /**
     * Performs database maintenance tasks.
     * 
     * @return true if maintenance successful, false otherwise
     */
    suspend fun performMaintenance(): Boolean
    
    /**
     * Creates a backup of the database.
     * 
     * @param backupPath The path to store the backup
     * @return true if backup successful, false otherwise
     */
    suspend fun createBackup(backupPath: String): Boolean
    
    /**
     * Restores the database from a backup.
     * 
     * @param backupPath The path to the backup file
     * @return true if restore successful, false otherwise
     */
    suspend fun restoreFromBackup(backupPath: String): Boolean
    
    /**
     * Optimizes the database for better performance.
     * 
     * @return true if optimization successful, false otherwise
     */
    suspend fun optimize(): Boolean
}

/**
 * Information about the database implementation and status.
 */
data class DatabaseInfo(
    val name: String,
    val version: String,
    val isInitialized: Boolean,
    val userCount: Long,
    val sessionCount: Long,
    val auditLogCount: Long,
    val lastMaintenance: Long?,
    val sizeInBytes: Long?,
    val supportsTransactions: Boolean,
    val supportsForeignKeys: Boolean,
    val supportsFullTextSearch: Boolean
)

/**
 * Database configuration options.
 */
data class DatabaseConfig(
    val name: String = "multiauth.db",
    val version: Int = 1,
    val enableForeignKeys: Boolean = true,
    val enableWAL: Boolean = true,
    val journalMode: String = "WAL",
    val synchronous: String = "NORMAL",
    val cacheSize: Int = -2000, // 2MB
    val tempStore: String = "MEMORY",
    val mmapSize: Long = 268435456, // 256MB
    val pageSize: Int = 4096,
    val autoVacuum: String = "INCREMENTAL"
)

/**
 * Database migration interface for version upgrades.
 */
interface DatabaseMigration {
    val fromVersion: Int
    val toVersion: Int
    
    suspend fun migrate(database: Database): Boolean
}

/**
 * Database migration result.
 */
sealed class MigrationResult {
    object Success : MigrationResult()
    data class Failure(val error: String) : MigrationResult()
    object Skipped : MigrationResult()
}

/**
 * Database statistics for monitoring and analytics.
 */
data class DatabaseStats(
    val totalUsers: Long,
    val activeUsers: Long,
    val totalSessions: Long,
    val activeSessions: Long,
    val totalOAuthAccounts: Long,
    val totalAuditLogs: Long,
    val databaseSize: Long,
    val lastBackup: Long?,
    val uptime: Long,
    val queryCount: Long,
    val slowQueries: Long
)