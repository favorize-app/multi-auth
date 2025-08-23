package app.multiauth.database

import app.multiauth.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Manages database migrations and schema updates.
 * Handles versioning, rollbacks, and ensures data integrity during updates.
 */
class DatabaseMigrationManager(
    private val database: Database
) {
    
    private val logger = Logger.getLogger(this::class)
    
    companion object {
        private const val MIGRATIONS_TABLE = "schema_migrations"
        private const val CURRENT_VERSION = 1
    }
    
    /**
     * Runs all pending migrations to bring the database to the current version.
     * 
     * @return Migration result with details about what was executed
     */
    suspend fun migrate(): MigrationResult {
        logger.info("db", "Starting database migration to version $CURRENT_VERSION")
        
        try {
            // Ensure migrations table exists
            createMigrationsTableIfNotExists()
            
            // Get current version
            val currentVersion = getCurrentVersion()
            logger.info("database", "Current database version: $currentVersion")
            
            if (currentVersion >= CURRENT_VERSION) {
                logger.info("database", "Database is already at version $CURRENT_VERSION")
                return MigrationResult(
                    success = true,
                    fromVersion = currentVersion,
                    toVersion = CURRENT_VERSION,
                    message = "Database is already up to date"
                )
            }
            
            // Execute pending migrations
            val migrationsExecuted = mutableListOf<MigrationInfo>()
            
            for (version in (currentVersion + 1)..CURRENT_VERSION) {
                val migration = getMigration(version)
                if (migration != null) {
                    logger.info("db", "Executing migration to version $version: ${migration.description}")
                    
                    try {
                        executeMigration(migration)
                        recordMigrationExecution(version, migration)
                        migrationsExecuted.add(migration)
                        
                        logger.info("db", "Successfully migrated to version $version")
                    } catch (e: Exception) {
                        logger.error("database", "Migration to version $version failed: ${e.message}")
                        return MigrationResult(
                            success = false,
                            fromVersion = currentVersion,
                            toVersion = version - 1,
                            migrationsExecuted = migrationsExecuted,
                            message = "Migration to version $version failed: ${e.message}",
                            error = e
                        )
                    }
                } else {
                    logger.warn("db", "No migration found for version $version")
                }
            }
            
            logger.info("database", "Database migration completed successfully")
            return MigrationResult(
                success = true,
                fromVersion = currentVersion,
                toVersion = CURRENT_VERSION,
                migrationsExecuted = migrationsExecuted,
                message = "Successfully migrated from version $currentVersion to $CURRENT_VERSION"
            )
            
        } catch (e: Exception) {
            logger.error("db", "Migration failed: ${e.message}")
            return MigrationResult(
                success = false,
                fromVersion = getCurrentVersion(),
                toVersion = getCurrentVersion(),
                migrationsExecuted = emptyList(),
                message = "Migration failed: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * Rolls back the database to a specific version.
     * 
     * @param targetVersion The version to roll back to
     * @return Migration result with details about the rollback
     */
    suspend fun rollback(targetVersion: Int): MigrationResult {
        logger.info("db", "Rolling back database to version $targetVersion")
        
        try {
            val currentVersion = getCurrentVersion()
            
            if (targetVersion >= currentVersion) {
                return MigrationResult(
                    success = false,
                    fromVersion = currentVersion,
                    toVersion = currentVersion,
                    migrationsExecuted = emptyList(),
                    message = "Cannot rollback to version $targetVersion (current: $currentVersion)"
                )
            }
            
            // Execute rollback migrations in reverse order
            val rollbacksExecuted = mutableListOf<MigrationInfo>()
            
            for (version in currentVersion downTo (targetVersion + 1)) {
                val rollback = getRollbackMigration(version)
                if (rollback != null) {
                    logger.info("db", "Executing rollback from version $version: ${rollback.description}")
                    
                    try {
                        executeMigration(rollback)
                        recordMigrationRollback(version, rollback)
                        rollbacksExecuted.add(rollback)
                        
                        logger.info("db", "Successfully rolled back from version $version")
                    } catch (e: Exception) {
                        logger.error("database", "Rollback from version $version failed: ${e.message}")
                        return MigrationResult(
                            success = false,
                            fromVersion = currentVersion,
                            toVersion = version,
                            migrationsExecuted = rollbacksExecuted,
                            message = "Rollback from version $version failed: ${e.message}",
                            error = e
                        )
                    }
                } else {
                    logger.warn("db", "No rollback migration found for version $version")
                }
            }
            
            logger.info("database", "Database rollback completed successfully")
            return MigrationResult(
                success = true,
                fromVersion = currentVersion,
                toVersion = targetVersion,
                migrationsExecuted = rollbacksExecuted,
                message = "Successfully rolled back from version $currentVersion to $targetVersion"
            )
            
        } catch (e: Exception) {
            logger.error("db", "Rollback failed: ${e.message}")
            return MigrationResult(
                success = false,
                fromVersion = getCurrentVersion(),
                toVersion = getCurrentVersion(),
                migrationsExecuted = emptyList(),
                message = "Rollback failed: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * Gets the current database version.
     * 
     * @return Current database version
     */
    suspend fun getCurrentVersion(): Int {
        return try {
            val result = database.executeQuery(
                "SELECT version FROM $MIGRATIONS_TABLE ORDER BY executed_at DESC LIMIT 1"
            )
            
            if (result.isNotEmpty() && result[0].isNotEmpty()) {
                result[0]["version"]?.toIntOrNull() ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            logger.warn("db", "Could not determine current version: ${e.message}")
            0
        }
    }
    
    /**
     * Gets migration history.
     * 
     * @return List of migration history entries
     */
    suspend fun getMigrationHistory(): List<MigrationHistoryEntry> {
        return try {
            val result = database.executeQuery(
                "SELECT version, description, executed_at, rollback_at FROM $MIGRATIONS_TABLE ORDER BY executed_at DESC"
            )
            
            result.map { row ->
                MigrationHistoryEntry(
                    version = row["version"]?.toIntOrNull() ?: 0,
                    description = row["description"] ?: "",
                    executedAt = row["executed_at"] ?: "",
                    rolledBackAt = row["rollback_at"]
                )
            }
        } catch (e: Exception) {
            logger.warn("db", "Could not retrieve migration history: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Checks if the database needs migration.
     * 
     * @return True if migration is needed
     */
    suspend fun needsMigration(): Boolean {
        return getCurrentVersion() < CURRENT_VERSION
    }
    
    /**
     * Gets pending migrations.
     * 
     * @return List of pending migrations
     */
    suspend fun getPendingMigrations(): List<MigrationInfo> {
        val currentVersion = getCurrentVersion()
        val pending = mutableListOf<MigrationInfo>()
        
        for (version in (currentVersion + 1)..CURRENT_VERSION) {
            val migration = getMigration(version)
            if (migration != null) {
                pending.add(migration)
            }
        }
        
        return pending
    }
    
    /**
     * Creates the migrations table if it doesn't exist.
     */
    private suspend fun createMigrationsTableIfNotExists() {
        val createTableSql = """
            CREATE TABLE IF NOT EXISTS $MIGRATIONS_TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                version INTEGER NOT NULL,
                description TEXT NOT NULL,
                executed_at TEXT NOT NULL,
                rollback_at TEXT,
                migration_data TEXT
            )
        """.trimIndent()
        
        database.executeUpdate(createTableSql)
        
        // Create index for performance
        val createIndexSql = "CREATE INDEX IF NOT EXISTS idx_migrations_version ON $MIGRATIONS_TABLE(version)"
        database.executeUpdate(createIndexSql)
    }
    
    /**
     * Gets a migration for a specific version.
     * 
     * @param version The version to get migration for
     * @return Migration info or null if not found
     */
    private fun getMigration(version: Int): MigrationInfo? {
        return when (version) {
            1 -> MigrationInfo(
                version = 1,
                description = "Initial schema creation",
                sql = getInitialSchemaSql(),
                rollbackSql = getInitialSchemaRollbackSql()
            )
            // Add more migrations here as the schema evolves
            else -> null
        }
    }
    
    /**
     * Gets a rollback migration for a specific version.
     * 
     * @param version The version to get rollback for
     * @return Rollback migration info or null if not found
     */
    private fun getRollbackMigration(version: Int): MigrationInfo? {
        return when (version) {
            1 -> MigrationInfo(
                version = 1,
                description = "Rollback initial schema",
                sql = getInitialSchemaRollbackSql(),
                rollbackSql = getInitialSchemaSql()
            )
            // Add more rollback migrations here
            else -> null
        }
    }
    
    /**
     * Executes a migration.
     * 
     * @param migration The migration to execute
     */
    private suspend fun executeMigration(migration: MigrationInfo) {
        // Execute the migration SQL
        database.executeUpdate(migration.sql)
        
        // Log the migration
        logger.info("db", "Executed migration ${migration.version}: ${migration.description}")
    }
    
    /**
     * Records that a migration was executed.
     * 
     * @param version The version that was migrated to
     * @param migration The migration that was executed
     */
    private suspend fun recordMigrationExecution(version: Int, migration: MigrationInfo) {
        val insertSql = """
            INSERT INTO $MIGRATIONS_TABLE (version, description, executed_at, migration_data)
            VALUES (?, ?, datetime('now'), ?)
        """.trimIndent()
        
        val migrationData = Json.encodeToString(MigrationData.serializer(), MigrationData(
            version = version,
            description = migration.description,
            timestamp = System.currentTimeMillis()
        ))
        
        database.executeUpdate(insertSql, listOf(version.toString(), migration.description, migrationData))
    }
    
    /**
     * Records that a migration was rolled back.
     * 
     * @param version The version that was rolled back from
     * @param migration The rollback migration that was executed
     */
    private suspend fun recordMigrationRollback(version: Int, migration: MigrationInfo) {
        val updateSql = """
            UPDATE $MIGRATIONS_TABLE 
            SET rollback_at = datetime('now')
            WHERE version = ?
        """.trimIndent()
        
        database.executeUpdate(updateSql, listOf(version.toString()))
    }
    
    /**
     * Gets the SQL for creating the initial schema.
     * 
     * @return SQL string for initial schema
     */
    private fun getInitialSchemaSql(): String {
        return """
            -- Create users table
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                email TEXT UNIQUE,
                email_verified BOOLEAN DEFAULT FALSE,
                phone_number TEXT,
                phone_verified BOOLEAN DEFAULT FALSE,
                is_anonymous BOOLEAN DEFAULT FALSE,
                anonymous_session_id TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                last_sign_in_at TEXT,
                display_name TEXT,
                avatar_url TEXT,
                metadata TEXT
            );
            
            -- Create oauth_accounts table
            CREATE TABLE IF NOT EXISTS oauth_accounts (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                provider TEXT NOT NULL,
                provider_user_id TEXT NOT NULL,
                access_token TEXT,
                refresh_token TEXT,
                token_expires_at TEXT,
                scopes TEXT,
                metadata TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                UNIQUE(provider, provider_user_id)
            );
            
            -- Create sessions table
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                token_hash TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                device_info TEXT,
                ip_address TEXT,
                user_agent TEXT,
                created_at TEXT NOT NULL,
                last_used_at TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            
            -- Create audit_logs table
            CREATE TABLE IF NOT EXISTS audit_logs (
                id TEXT PRIMARY KEY,
                user_id TEXT,
                event_type TEXT NOT NULL,
                event_data TEXT,
                ip_address TEXT,
                user_agent TEXT,
                timestamp TEXT NOT NULL,
                severity TEXT DEFAULT 'INFO',
                metadata TEXT
            );
            
            -- Create indexes for performance
            CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
            CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone_number);
            CREATE INDEX IF NOT EXISTS idx_users_anonymous ON users(is_anonymous);
            CREATE INDEX IF NOT EXISTS idx_oauth_accounts_user_id ON oauth_accounts(user_id);
            CREATE INDEX IF NOT EXISTS idx_oauth_accounts_provider ON oauth_accounts(provider);
            CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
            CREATE INDEX IF NOT EXISTS idx_sessions_token_hash ON sessions(token_hash);
            CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);
            CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
            CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs(event_type);
            CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp);
        """.trimIndent()
    }
    
    /**
     * Gets the SQL for rolling back the initial schema.
     * 
     * @return SQL string for rolling back initial schema
     */
    private fun getInitialSchemaRollbackSql(): String {
        return """
            -- Drop indexes
            DROP INDEX IF EXISTS idx_audit_logs_timestamp;
            DROP INDEX IF EXISTS idx_audit_logs_event_type;
            DROP INDEX IF EXISTS idx_audit_logs_user_id;
            DROP INDEX IF EXISTS idx_sessions_expires_at;
            DROP INDEX IF EXISTS idx_sessions_token_hash;
            DROP INDEX IF EXISTS idx_sessions_user_id;
            DROP INDEX IF EXISTS idx_oauth_accounts_provider;
            DROP INDEX IF EXISTS idx_oauth_accounts_user_id;
            DROP INDEX IF EXISTS idx_users_anonymous;
            DROP INDEX IF EXISTS idx_users_phone;
            DROP INDEX IF EXISTS idx_users_email;
            
            -- Drop tables
            DROP TABLE IF EXISTS audit_logs;
            DROP TABLE IF EXISTS sessions;
            DROP TABLE IF EXISTS oauth_accounts;
            DROP TABLE IF EXISTS users;
        """.trimIndent()
    }
}

/**
 * Information about a database migration.
 */
data class MigrationInfo(
    val version: Int,
    val description: String,
    val sql: String,
    val rollbackSql: String
)

/**
 * Entry in the migration history.
 */
data class MigrationHistoryEntry(
    val version: Int,
    val description: String,
    val executedAt: String,
    val rolledBackAt: String?
)

/**
 * Data about a migration execution.
 */
@Serializable
data class MigrationData(
    val version: Int,
    val description: String,
    val timestamp: Long
)