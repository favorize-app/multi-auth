package app.multiauth.database

import app.multiauth.models.User
import app.multiauth.models.OAuthAccount
import app.multiauth.models.Session
import app.multiauth.models.AuditLog
import app.multiauth.util.Logger
import app.multiauth.models.AuditLogEvent
import app.multiauth.models.AuditLogSeverity
import kotlinx.serialization.ListSerializer
import kotlinx.serialization.MapSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

/**
 * SQLite database implementation for the Multi-Auth system.
 * Provides persistent storage with proper SQL schema and transactions.
 */
class SqliteDatabase(
    private val config: DatabaseConfig
) : Database {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    private var connection: Connection? = null
    private var isInitialized = false
    
    companion object {
        private const val DRIVER = "org.sqlite.JDBC"
        private const val JDBC_PREFIX = "jdbc:sqlite:"
        
        // SQL statements for table creation
        private const val CREATE_USERS_TABLE = """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT,
                phone_number TEXT,
                phone_verified BOOLEAN DEFAULT FALSE,
                is_anonymous BOOLEAN DEFAULT FALSE,
                anonymous_session_id TEXT,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                last_sign_in_at TIMESTAMP,
                email_verified BOOLEAN DEFAULT FALSE,
                mfa_enabled BOOLEAN DEFAULT FALSE,
                account_locked BOOLEAN DEFAULT FALSE,
                failed_login_attempts INTEGER DEFAULT 0,
                locked_until TIMESTAMP,
                metadata TEXT
            )
        """
        
        private const val CREATE_OAUTH_ACCOUNTS_TABLE = """
            CREATE TABLE IF NOT EXISTS oauth_accounts (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                provider_id TEXT NOT NULL,
                external_user_id TEXT NOT NULL,
                access_token TEXT,
                refresh_token TEXT,
                expires_at TIMESTAMP,
                scope TEXT,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                metadata TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                UNIQUE(provider_id, external_user_id)
            )
        """
        
        private const val CREATE_SESSIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                access_token TEXT NOT NULL,
                refresh_token TEXT,
                expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP NOT NULL,
                last_used_at TIMESTAMP NOT NULL,
                device_info TEXT,
                ip_address TEXT,
                user_agent TEXT,
                is_active BOOLEAN DEFAULT TRUE,
                metadata TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """
        
        private const val CREATE_AUDIT_LOGS_TABLE = """
            CREATE TABLE IF NOT EXISTS audit_logs (
                id TEXT PRIMARY KEY,
                user_id TEXT,
                event_type TEXT NOT NULL,
                event_data TEXT,
                ip_address TEXT,
                user_agent TEXT,
                timestamp TIMESTAMP NOT NULL,
                severity TEXT DEFAULT 'INFO',
                metadata TEXT
            )
        """
        
        // Indexes for performance
        private const val CREATE_INDEXES = """
            CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
            CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
            CREATE INDEX IF NOT EXISTS idx_oauth_provider_external ON oauth_accounts(provider_id, external_user_id);
            CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
            CREATE INDEX IF NOT EXISTS idx_sessions_access_token ON sessions(access_token);
            CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);
            CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
            CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp);
            CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs(event_type);
        """
    }
    
    override suspend fun initialize(): Boolean {
        return try {
            logger.info("db", "Initializing SQLite database: ${config.name}")
            
            // Load SQLite JDBC driver
            Class.forName(DRIVER)
            
            // Create database connection
            val dbPath = config.name
            connection = DriverManager.getConnection("$JDBC_PREFIX$dbPath")
            
            // Enable foreign keys
            if (config.enableForeignKeys) {
                connection?.createStatement()?.execute("PRAGMA foreign_keys = ON")
            }
            
            // Create tables
            createTables()
            
            // Create indexes
            createIndexes()
            
            isInitialized = true
            logger.info("SQLite database initialized successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to initialize SQLite database", e)
            false
        }
    }
    
    override suspend fun close() {
        try {
            connection?.close()
            connection = null
            isInitialized = false
            logger.info("db", "SQLite database connection closed")
        } catch (e: Exception) {
            logger.error("Error closing database connection", e)
        }
    }
    
    override suspend fun isReady(): Boolean = isInitialized
    
    override suspend fun getDatabaseInfo(): DatabaseInfo {
        return try {
            val userCount = getUserCount()
            val sessionCount = getSessionCount()
            val auditLogCount = getAuditLogCount()
            
            DatabaseInfo(
                name = config.name,
                version = config.version.toString(),
                isInitialized = isInitialized,
                userCount = userCount,
                sessionCount = sessionCount,
                auditLogCount = auditLogCount,
                lastMaintenance = null,
                sizeInBytes = getDatabaseSize(),
                supportsTransactions = true,
                supportsForeignKeys = config.enableForeignKeys,
                supportsFullTextSearch = false
            )
        } catch (e: Exception) {
            logger.error("Error getting database info", e)
            DatabaseInfo(
                name = config.name,
                version = "0",
                isInitialized = false,
                userCount = 0,
                sessionCount = 0,
                auditLogCount = 0,
                lastMaintenance = null,
                sizeInBytes = null,
                supportsTransactions = false,
                supportsForeignKeys = false,
                supportsFullTextSearch = false
            )
        }
    }
    
    // User management implementation
    
    override suspend fun createUser(user: User): User? {
        return withContext(Dispatchers.IO) {
            try {
                val sql = """
                    INSERT INTO users (
                        id, username, email, password_hash, phone_number, phone_verified,
                        is_anonymous, anonymous_session_id, created_at, updated_at,
                        last_sign_in_at, email_verified, mfa_enabled, account_locked,
                        failed_login_attempts, locked_until, metadata
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, user.id)
                    setString(2, user.username)
                    setString(3, user.email)
                    setString(4, user.passwordHash)
                    setString(5, user.phoneNumber)
                    setBoolean(6, user.phoneVerified)
                    setBoolean(7, user.isAnonymous)
                    setString(8, user.anonymousSessionId)
                    setTimestamp(9, Timestamp.from(user.createdAt))
                    setTimestamp(10, Timestamp.from(user.updatedAt))
                    setTimestamp(11, user.lastSignInAt?.let { Timestamp.from(it) })
                    setBoolean(12, user.emailVerified)
                    setBoolean(13, user.mfaEnabled)
                    setBoolean(14, user.accountLocked)
                    setInt(15, user.failedLoginAttempts)
                    setTimestamp(16, user.lockedUntil?.let { Timestamp.from(it) })
                    setString(17, user.metadata?.let { json.encodeToString(it) })
                }
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "User created successfully: ${user.id}")
                    user
                } else {
                    logger.error("Failed to create user: ${user.id}")
                    null
                }
            } catch (e: Exception) {
                logger.error("Exception creating user: ${user.id}", e)
                null
            }
        }
    }
    
    override suspend fun updateUser(user: User): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = """
                    UPDATE users SET
                        username = ?, email = ?, password_hash = ?, phone_number = ?,
                        phone_verified = ?, is_anonymous = ?, anonymous_session_id = ?,
                        updated_at = ?, last_sign_in_at = ?, email_verified = ?,
                        mfa_enabled = ?, account_locked = ?, failed_login_attempts = ?,
                        locked_until = ?, metadata = ?
                    WHERE id = ?
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, user.username)
                    setString(2, user.email)
                    setString(3, user.passwordHash)
                    setString(4, user.phoneNumber)
                    setBoolean(5, user.phoneVerified)
                    setBoolean(6, user.isAnonymous)
                    setString(7, user.anonymousSessionId)
                    setTimestamp(8, Timestamp.from(user.updatedAt))
                    setTimestamp(9, user.lastSignInAt?.let { Timestamp.from(it) })
                    setBoolean(10, user.emailVerified)
                    setBoolean(11, user.mfaEnabled)
                    setBoolean(12, user.accountLocked)
                    setInt(13, user.failedLoginAttempts)
                    setTimestamp(14, user.lockedUntil?.let { Timestamp.from(it) })
                    setString(15, user.metadata?.let { json.encodeToString(it) })
                    setString(16, user.id)
                }
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "User updated successfully: ${user.id}")
                    true
                } else {
                    logger.error("Failed to update user: ${user.id}")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception updating user: ${user.id}", e)
                false
            }
        }
    }
    
    override suspend fun getUserById(userId: String): User? {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "SELECT * FROM users WHERE id = ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setString(1, userId)
                
                val rs = stmt?.executeQuery()
                val user = rs?.let { resultSetToUser(it) }
                
                rs?.close()
                stmt?.close()
                
                user
            } catch (e: Exception) {
                logger.error("Exception getting user by ID: $userId", e)
                null
            }
        }
    }
    
    override suspend fun getUserByEmail(email: String): User? {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "SELECT * FROM users WHERE email = ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setString(1, email)
                
                val rs = stmt?.executeQuery()
                val user = rs?.let { resultSetToUser(it) }
                
                rs?.close()
                stmt?.close()
                
                user
            } catch (e: Exception) {
                logger.error("Exception getting user by email: $email", e)
                null
            }
        }
    }
    
    override suspend fun getUserByUsername(username: String): User? {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "SELECT * FROM users WHERE username = ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setString(1, username)
                
                val rs = stmt?.executeQuery()
                val user = rs?.let { resultSetToUser(it) }
                
                rs?.close()
                stmt?.close()
                
                user
            } catch (e: Exception) {
                logger.error("Exception getting user by username: $username", e)
                null
            }
        }
    }
    
    override fun getAllUsers(): Flow<List<User>> = flow {
        try {
            val users = mutableListOf<User>()
            val sql = "SELECT * FROM users ORDER BY created_at DESC"
            val stmt = connection?.createStatement()
            val rs = stmt?.executeQuery(sql)
            
            rs?.let { resultSet ->
                while (resultSet.next()) {
                    resultSetToUser(resultSet)?.let { users.add(it) }
                }
            }
            
            rs?.close()
            stmt?.close()
            
            emit(users)
        } catch (e: Exception) {
            logger.error("Exception getting all users", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun deleteUser(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "DELETE FROM users WHERE id = ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setString(1, userId)
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "User deleted successfully: $userId")
                    true
                } else {
                    logger.error("Failed to delete user: $userId")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception deleting user: $userId", e)
                false
            }
        }
    }
    
    override suspend fun searchUsers(query: String, limit: Int): List<User> {
        return withContext(Dispatchers.IO) {
            try {
                val users = mutableListOf<User>()
                val sql = """
                    SELECT * FROM users 
                    WHERE username LIKE ? OR email LIKE ? OR id LIKE ?
                    ORDER BY created_at DESC 
                    LIMIT ?
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, "%$query%")
                    setString(2, "%$query%")
                    setString(3, "%$query%")
                    setInt(4, limit)
                }
                
                val rs = stmt?.executeQuery()
                rs?.let { resultSet ->
                    while (resultSet.next()) {
                        resultSetToUser(resultSet)?.let { users.add(it) }
                    }
                }
                
                rs?.close()
                stmt?.close()
                
                users
            } catch (e: Exception) {
                logger.error("Exception searching users: $query", e)
                emptyList()
            }
        }
    }
    
    override suspend fun getUserCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "SELECT COUNT(*) FROM users"
                val stmt = connection?.createStatement()
                val rs = stmt?.executeQuery(sql)
                
                val count = rs?.getLong(1) ?: 0
                
                rs?.close()
                stmt?.close()
                
                count
            } catch (e: Exception) {
                logger.error("Exception getting user count", e)
                0
            }
        }
    }
    
    // Private helper methods
    
    private fun createTables() {
        try {
            connection?.createStatement()?.apply {
                execute(CREATE_USERS_TABLE)
                execute(CREATE_OAUTH_ACCOUNTS_TABLE)
                execute(CREATE_SESSIONS_TABLE)
                execute(CREATE_AUDIT_LOGS_TABLE)
                close()
            }
            logger.debug("db", "Database tables created successfully")
        } catch (e: Exception) {
            logger.error("Error creating database tables", e)
            throw e
        }
    }
    
    private fun createIndexes() {
        try {
            connection?.createStatement()?.apply {
                execute(CREATE_INDEXES)
                close()
            }
            logger.debug("db", "Database indexes created successfully")
        } catch (e: Exception) {
            logger.error("Error creating database indexes", e)
            // Don't throw here as indexes are not critical for basic functionality
        }
    }
    
    private fun resultSetToUser(rs: ResultSet): User? {
        return try {
            User(
                id = rs.getString("id"),
                username = rs.getString("username"),
                email = rs.getString("email"),
                passwordHash = rs.getString("password_hash"),
                phoneNumber = rs.getString("phone_number"),
                phoneVerified = rs.getBoolean("phone_verified"),
                isAnonymous = rs.getBoolean("is_anonymous"),
                anonymousSessionId = rs.getString("anonymous_session_id"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at").toInstant(),
                lastSignInAt = rs.getTimestamp("last_sign_in_at")?.toInstant(),
                emailVerified = rs.getBoolean("email_verified"),
                mfaEnabled = rs.getBoolean("mfa_enabled"),
                accountLocked = rs.getBoolean("account_locked"),
                failedLoginAttempts = rs.getInt("failed_login_attempts"),
                lockedUntil = rs.getTimestamp("locked_until")?.toInstant(),
                metadata = rs.getString("metadata")?.let { json.decodeFromString(it) }
            )
        } catch (e: Exception) {
            logger.error("Error converting result set to user", e)
            null
        }
    }
    
    private fun resultSetToOAuthAccount(rs: ResultSet): OAuthAccount? {
        return try {
            OAuthAccount(
                id = rs.getString("id"),
                userId = rs.getString("user_id"),
                providerId = rs.getString("provider_id"),
                externalUserId = rs.getString("external_user_id"),
                accessToken = rs.getString("access_token"),
                refreshToken = rs.getString("refresh_token"),
                expiresAt = rs.getTimestamp("expires_at")?.toInstant(),
                scope = rs.getString("scope"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at").toInstant(),
                metadata = rs.getString("metadata")?.let { json.decodeFromString(it) }
            )
        } catch (e: Exception) {
            logger.error("Error converting result set to OAuth account", e)
            null
        }
    }
    
    private fun resultSetToSession(rs: ResultSet): Session? {
        return try {
            Session(
                id = rs.getString("id"),
                userId = rs.getString("user_id"),
                accessToken = rs.getString("access_token"),
                refreshToken = rs.getString("refresh_token"),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                lastUsedAt = rs.getTimestamp("last_used_at").toInstant(),
                deviceInfo = rs.getString("device_info")?.let { json.decodeFromString(it) },
                ipAddress = rs.getString("ip_address"),
                userAgent = rs.getString("user_agent"),
                isActive = rs.getBoolean("is_active"),
                metadata = rs.getString("metadata")?.let { json.decodeFromString(it) }
            )
        } catch (e: Exception) {
            logger.error("Error converting result set to session", e)
            null
        }
    }
    
    private fun resultSetToAuditLog(rs: ResultSet): AuditLog? {
        return try {
            AuditLog(
                id = rs.getString("id"),
                userId = rs.getString("user_id"),
                eventType = rs.getString("event_type"),
                eventData = rs.getString("event_data")?.let { json.decodeFromString(it) },
                ipAddress = rs.getString("ip_address"),
                userAgent = rs.getString("user_agent"),
                timestamp = rs.getTimestamp("timestamp").toInstant(),
                severity = rs.getString("severity"),
                metadata = rs.getString("metadata")?.let { json.decodeFromString(it) }
            )
        } catch (e: Exception) {
            logger.error("Error converting result set to audit log", e)
            null
        }
    }
    
    private suspend fun getSessionCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "SELECT COUNT(*) FROM sessions"
                val stmt = connection?.createStatement()
                val rs = stmt?.executeQuery(sql)
                
                val count = rs?.getLong(1) ?: 0
                
                rs?.close()
                stmt?.close()
                
                count
            } catch (e: Exception) {
                logger.error("Exception getting session count", e)
                0
            }
        }
    }
    
    private suspend fun getAuditLogCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "SELECT COUNT(*) FROM audit_logs"
                val stmt = connection?.createStatement()
                val rs = stmt?.executeQuery(sql)
                
                val count = rs?.getLong(1) ?: 0
                
                rs?.close()
                stmt?.close()
                
                count
            } catch (e: Exception) {
                logger.error("Exception getting audit log count", e)
                0
            }
        }
    }
    
    private fun getDatabaseSize(): Long? {
        return try {
            // This is a simplified approach - in production you might want to use
            // platform-specific methods to get actual file size
            java.io.File(config.name).length()
        } catch (e: Exception) {
            logger.error("Error getting database size", e)
            null
        }
    }
    
    // OAuth Account Management
    override suspend fun linkOAuthAccount(oauthAccount: OAuthAccount): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = """
                    INSERT INTO oauth_accounts (
                        id, user_id, provider_id, external_user_id, access_token,
                        refresh_token, expires_at, scope, created_at, updated_at, metadata
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, oauthAccount.id)
                    setString(2, oauthAccount.userId)
                    setString(3, oauthAccount.providerId)
                    setString(4, oauthAccount.externalUserId)
                    setString(5, oauthAccount.accessToken)
                    setString(6, oauthAccount.refreshToken)
                    setTimestamp(7, oauthAccount.expiresAt?.let { Timestamp.from(it) })
                    setString(8, oauthAccount.scope)
                    setTimestamp(9, Timestamp.from(oauthAccount.createdAt))
                    setTimestamp(10, Timestamp.from(oauthAccount.updatedAt))
                    setString(11, oauthAccount.metadata?.let { json.encodeToString(it) })
                }
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "OAuth account linked successfully: ${oauthAccount.id}")
                    true
                } else {
                    logger.error("Failed to link OAuth account: ${oauthAccount.id}")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception linking OAuth account: ${oauthAccount.id}", e)
                false
            }
        }
    }
    
    override suspend fun unlinkOAuthAccount(providerId: String, externalUserId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "DELETE FROM oauth_accounts WHERE provider_id = ? AND external_user_id = ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, providerId)
                    setString(2, externalUserId)
                }
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "OAuth account unlinked successfully: $providerId/$externalUserId")
                    true
                } else {
                    logger.warn("No OAuth account found to unlink: $providerId/$externalUserId")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception unlinking OAuth account: $providerId/$externalUserId", e)
                false
            }
        }
    }
    
    override suspend fun getOAuthAccountsForUser(userId: String): List<OAuthAccount> {
        return withContext(Dispatchers.IO) {
            try {
                val accounts = mutableListOf<OAuthAccount>()
                val sql = "SELECT * FROM oauth_accounts WHERE user_id = ? ORDER BY created_at DESC"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setString(1, userId)
                
                val rs = stmt?.executeQuery()
                rs?.let { resultSet ->
                    while (resultSet.next()) {
                        resultSetToOAuthAccount(resultSet)?.let { accounts.add(it) }
                    }
                }
                
                rs?.close()
                stmt?.close()
                
                accounts
            } catch (e: Exception) {
                logger.error("Exception getting OAuth accounts for user: $userId", e)
                emptyList()
            }
        }
    }
    
    override suspend fun getUserByOAuthAccount(providerId: String, externalUserId: String): User? {
        return withContext(Dispatchers.IO) {
            try {
                val sql = """
                    SELECT u.* FROM users u
                    JOIN oauth_accounts oa ON u.id = oa.user_id
                    WHERE oa.provider_id = ? AND oa.external_user_id = ?
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, providerId)
                    setString(2, externalUserId)
                }
                
                val rs = stmt?.executeQuery()
                val user = rs?.let { resultSetToUser(it) }
                
                rs?.close()
                stmt?.close()
                
                user
            } catch (e: Exception) {
                logger.error("Exception getting user by OAuth account: $providerId/$externalUserId", e)
                null
            }
        }
    }
    
    override suspend fun updateOAuthAccount(oauthAccount: OAuthAccount): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = """
                    UPDATE oauth_accounts SET
                        access_token = ?, refresh_token = ?, expires_at = ?,
                        scope = ?, updated_at = ?, metadata = ?
                    WHERE id = ?
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, oauthAccount.accessToken)
                    setString(2, oauthAccount.refreshToken)
                    setTimestamp(3, oauthAccount.expiresAt?.let { Timestamp.from(it) })
                    setString(4, oauthAccount.scope)
                    setTimestamp(5, Timestamp.from(oauthAccount.updatedAt))
                    setString(6, oauthAccount.metadata?.let { json.encodeToString(it) })
                    setString(7, oauthAccount.id)
                }
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "OAuth account updated successfully: ${oauthAccount.id}")
                    true
                } else {
                    logger.error("Failed to update OAuth account: ${oauthAccount.id}")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception updating OAuth account: ${oauthAccount.id}", e)
                false
            }
        }
    }
    
    // Session Management
    override suspend fun createSession(session: Session): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = """
                    INSERT INTO sessions (
                        id, user_id, access_token, refresh_token, expires_at,
                        created_at, last_used_at, device_info, ip_address, user_agent, is_active, metadata
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, session.id)
                    setString(2, session.userId)
                    setString(3, session.accessToken)
                    setString(4, session.refreshToken)
                    setTimestamp(5, Timestamp.from(session.expiresAt))
                    setTimestamp(6, Timestamp.from(session.createdAt))
                    setTimestamp(7, Timestamp.from(session.lastUsedAt))
                    setString(8, session.deviceInfo?.let { json.encodeToString(it) })
                    setString(9, session.ipAddress)
                    setString(10, session.userAgent)
                    setBoolean(11, session.isActive)
                    setString(12, session.metadata?.let { json.encodeToString(it) })
                }
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "Session created successfully: ${session.id}")
                    true
                } else {
                    logger.error("Failed to create session: ${session.id}")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception creating session: ${session.id}", e)
                false
            }
        }
    }
    
    override suspend fun getSessionById(sessionId: String): Session? {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "SELECT * FROM sessions WHERE id = ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setString(1, sessionId)
                
                val rs = stmt?.executeQuery()
                val session = rs?.let { resultSetToSession(it) }
                
                rs?.close()
                stmt?.close()
                
                session
            } catch (e: Exception) {
                logger.error("Exception getting session by ID: $sessionId", e)
                null
            }
        }
    }
    
    override suspend fun getActiveSessionsForUser(userId: String): List<Session> {
        return withContext(Dispatchers.IO) {
            try {
                val sessions = mutableListOf<Session>()
                val sql = "SELECT * FROM sessions WHERE user_id = ? AND is_active = TRUE ORDER BY last_used_at DESC"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setString(1, userId)
                
                val rs = stmt?.executeQuery()
                rs?.let { resultSet ->
                    while (resultSet.next()) {
                        resultSetToSession(resultSet)?.let { sessions.add(it) }
                    }
                }
                
                rs?.close()
                stmt?.close()
                
                sessions
            } catch (e: Exception) {
                logger.error("Exception getting active sessions for user: $userId", e)
                emptyList()
            }
        }
    }
    
    override suspend fun updateSession(session: Session): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = """
                    UPDATE sessions SET
                        access_token = ?, refresh_token = ?, expires_at = ?,
                        last_used_at = ?, device_info = ?, ip_address = ?,
                        user_agent = ?, is_active = ?, metadata = ?
                    WHERE id = ?
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, session.accessToken)
                    setString(2, session.refreshToken)
                    setTimestamp(3, Timestamp.from(session.expiresAt))
                    setTimestamp(4, Timestamp.from(session.lastUsedAt))
                    setString(5, session.deviceInfo?.let { json.encodeToString(it) })
                    setString(6, session.ipAddress)
                    setString(7, session.userAgent)
                    setBoolean(8, session.isActive)
                    setString(9, session.metadata?.let { json.encodeToString(it) })
                    setString(10, session.id)
                }
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "Session updated successfully: ${session.id}")
                    true
                } else {
                    logger.error("Failed to update session: ${session.id}")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception updating session: ${session.id}", e)
                false
            }
        }
    }
    
    override suspend fun deleteSession(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "DELETE FROM sessions WHERE id = ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setString(1, sessionId)
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "Session deleted successfully: $sessionId")
                    true
                } else {
                    logger.error("Failed to delete session: $sessionId")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception deleting session: $sessionId", e)
                false
            }
        }
    }
    
    override suspend fun cleanupExpiredSessions(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val sql = "DELETE FROM sessions WHERE expires_at < ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.setTimestamp(1, Timestamp.from(Instant.now()))
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                logger.debug("db", "Cleaned up $result expired sessions")
                result
            } catch (e: Exception) {
                logger.error("Exception cleaning up expired sessions", e)
                0
            }
        }
    }
    
    // Audit Logging
    override suspend fun logAuditEvent(auditLog: AuditLog): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sql = """
                    INSERT INTO audit_logs (
                        id, user_id, event_type, event_data, ip_address,
                        user_agent, timestamp, severity, metadata
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, auditLog.id)
                    setString(2, auditLog.userId)
                    setString(3, auditLog.eventType)
                    setString(4, auditLog.eventData?.let { json.encodeToString(it) })
                    setString(5, auditLog.ipAddress)
                    setString(6, auditLog.userAgent)
                    setTimestamp(7, Timestamp.from(auditLog.timestamp))
                    setString(8, auditLog.severity)
                    setString(9, auditLog.metadata?.let { json.encodeToString(it) })
                }
                
                val result = stmt?.executeUpdate()
                stmt?.close()
                
                if (result == 1) {
                    logger.debug("db", "Audit log created successfully: ${auditLog.id}")
                    true
                } else {
                    logger.error("Failed to create audit log: ${auditLog.id}")
                    false
                }
            } catch (e: Exception) {
                logger.error("Exception creating audit log: ${auditLog.id}", e)
                false
            }
        }
    }
    
    override suspend fun getAuditLogsForUser(userId: String, limit: Int): List<AuditLog> {
        return withContext(Dispatchers.IO) {
            try {
                val logs = mutableListOf<AuditLog>()
                val sql = "SELECT * FROM audit_logs WHERE user_id = ? ORDER BY timestamp DESC LIMIT ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, userId)
                    setInt(2, limit)
                }
                
                val rs = stmt?.executeQuery()
                rs?.let { resultSet ->
                    while (resultSet.next()) {
                        resultSetToAuditLog(resultSet)?.let { logs.add(it) }
                    }
                }
                
                rs?.close()
                stmt?.close()
                
                logs
            } catch (e: Exception) {
                logger.error("Exception getting audit logs for user: $userId", e)
                emptyList()
            }
        }
    }
    
    override suspend fun getAuditLogsForTimeRange(startTime: Long, endTime: Long, limit: Int): List<AuditLog> {
        return withContext(Dispatchers.IO) {
            try {
                val logs = mutableListOf<AuditLog>()
                val sql = "SELECT * FROM audit_logs WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC LIMIT ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(startTime)))
                    setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(endTime)))
                    setInt(3, limit)
                }
                
                val rs = stmt?.executeQuery()
                rs?.let { resultSet ->
                    while (resultSet.next()) {
                        resultSetToAuditLog(resultSet)?.let { logs.add(it) }
                    }
                }
                
                rs?.close()
                stmt?.close()
                
                logs
            } catch (e: Exception) {
                logger.error("Exception getting audit logs for time range: $startTime - $endTime", e)
                emptyList()
            }
        }
    }
    
    override suspend fun getAuditLogsByEventType(eventType: String, limit: Int): List<AuditLog> {
        return withContext(Dispatchers.IO) {
            try {
                val logs = mutableListOf<AuditLog>()
                val sql = "SELECT * FROM audit_logs WHERE event_type = ? ORDER BY timestamp DESC LIMIT ?"
                val stmt = connection?.prepareStatement(sql)
                stmt?.apply {
                    setString(1, eventType)
                    setInt(2, limit)
                }
                
                val rs = stmt?.executeQuery()
                rs?.let { resultSet ->
                    while (resultSet.next()) {
                        resultSetToAuditLog(resultSet)?.let { logs.add(it) }
                    }
                }
                
                rs?.close()
                stmt?.close()
                
                logs
            } catch (e: Exception) {
                logger.error("Exception getting audit logs by event type: $eventType", e)
                emptyList()
            }
        }
    }
    
    // Database Maintenance
    override suspend fun performMaintenance(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Run VACUUM to optimize the database
                connection?.createStatement()?.execute("VACUUM")
                
                // Run ANALYZE to update statistics
                connection?.createStatement()?.execute("ANALYZE")
                
                logger.debug("db", "Database maintenance completed successfully")
                true
            } catch (e: Exception) {
                logger.error("Exception performing database maintenance", e)
                false
            }
        }
    }
    
    override suspend fun createBackup(backupPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // For SQLite, we can copy the database file
                val backupFile = java.io.File(backupPath)
                val sourceFile = java.io.File(config.name)
                
                if (!sourceFile.exists()) {
                    logger.error("db", "Source database file does not exist: ${config.name}")
                    return@withContext false
                }
                
                sourceFile.copyTo(backupFile, overwrite = true)
                
                logger.debug("db", "Database backed up to: $backupPath")
                true
            } catch (e: Exception) {
                logger.error("Exception creating database backup", e)
                false
            }
        }
    }
    
    override suspend fun restoreFromBackup(backupPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val backupFile = java.io.File(backupPath)
                val targetFile = java.io.File(config.name)
                
                if (!backupFile.exists()) {
                    logger.error("db", "Backup file does not exist: $backupPath")
                    return@withContext false
                }
                
                // Close current connection
                close()
                
                // Copy backup to target
                backupFile.copyTo(targetFile, overwrite = true)
                
                // Reinitialize
                initialize()
                
                logger.debug("db", "Database restored from: $backupPath")
                true
            } catch (e: Exception) {
                logger.error("Exception restoring database from backup", e)
                false
            }
        }
    }
    
    override suspend fun optimize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Run VACUUM to optimize the database
                connection?.createStatement()?.execute("VACUUM")
                
                // Run ANALYZE to update statistics
                connection?.createStatement()?.execute("ANALYZE")
                
                logger.debug("db", "Database optimization completed successfully")
                true
            } catch (e: Exception) {
                logger.error("Exception optimizing database", e)
                false
            }
        }
    }
}