package app.multiauth.database

import app.multiauth.util.Logger

/**
 * Factory for creating database implementations based on configuration.
 * Provides a centralized way to instantiate the appropriate database.
 */
object DatabaseFactory {
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Creates a database instance based on the configuration.
     * 
     * @param config The database configuration
     * @return A Database implementation
     * @throws IllegalArgumentException if the database type is not supported
     */
    fun createDatabase(config: DatabaseConfig): Database {
        logger.info("Creating database with configuration: ${config.name}")
        
        // For now, we only support SQLite
        // In the future, this could support PostgreSQL, MySQL, etc.
        return SqliteDatabase(config)
    }
    
    /**
     * Creates a database with default configuration.
     * 
     * @return A Database implementation with default settings
     */
    fun createDefaultDatabase(): Database {
        val defaultConfig = DatabaseConfig()
        logger.info("Creating default database: ${defaultConfig.name}")
        return createDatabase(defaultConfig)
    }
    
    /**
     * Creates a database optimized for development/testing.
     * 
     * @return A Database implementation optimized for development
     */
    fun createDevelopmentDatabase(): Database {
        val devConfig = DatabaseConfig(
            name = "multiauth_dev.db",
            version = 1,
            enableForeignKeys = true,
            enableWAL = false, // Disable WAL for development
            journalMode = "DELETE", // Use DELETE mode for development
            synchronous = "OFF", // Disable synchronous writes for development
            cacheSize = -1000, // 1MB cache
            tempStore = "MEMORY",
            mmapSize = 67108864, // 64MB
            pageSize = 4096,
            autoVacuum = "NONE"
        )
        
        logger.info("Creating development database: ${devConfig.name}")
        return createDatabase(devConfig)
    }
    
    /**
     * Creates a database optimized for production.
     * 
     * @return A Database implementation optimized for production
     */
    fun createProductionDatabase(): Database {
        val prodConfig = DatabaseConfig(
            name = "multiauth_prod.db",
            version = 1,
            enableForeignKeys = true,
            enableWAL = true, // Enable WAL for production
            journalMode = "WAL", // Use WAL mode for production
            synchronous = "NORMAL", // Normal synchronous writes for production
            cacheSize = -20000, // 20MB cache
            tempStore = "FILE", // Use file storage for production
            mmapSize = 268435456, // 256MB
            pageSize = 4096,
            autoVacuum = "INCREMENTAL"
        )
        
        logger.info("Creating production database: ${prodConfig.name}")
        return createDatabase(prodConfig)
    }
    
    /**
     * Creates a database optimized for high-performance scenarios.
     * 
     * @return A Database implementation optimized for high performance
     */
    fun createHighPerformanceDatabase(): Database {
        val perfConfig = DatabaseConfig(
            name = "multiauth_perf.db",
            version = 1,
            enableForeignKeys = true,
            enableWAL = true,
            journalMode = "WAL",
            synchronous = "OFF", // Disable synchronous writes for maximum performance
            cacheSize = -50000, // 50MB cache
            tempStore = "MEMORY", // Use memory for temporary storage
            mmapSize = 536870912, // 512MB
            pageSize = 8192, // Larger page size for better performance
            autoVacuum = "INCREMENTAL"
        )
        
        logger.info("Creating high-performance database: ${perfConfig.name}")
        return createDatabase(perfConfig)
    }
    
    /**
     * Creates a database optimized for embedded/mobile scenarios.
     * 
     * @return A Database implementation optimized for embedded use
     */
    fun createEmbeddedDatabase(): Database {
        val embeddedConfig = DatabaseConfig(
            name = "multiauth_embedded.db",
            version = 1,
            enableForeignKeys = true,
            enableWAL = false, // Disable WAL for embedded systems
            journalMode = "DELETE", // Use DELETE mode for embedded systems
            synchronous = "NORMAL", // Normal synchronous writes
            cacheSize = -500, // 500KB cache for embedded systems
            tempStore = "MEMORY",
            mmapSize = 33554432, // 32MB
            pageSize = 4096,
            autoVacuum = "NONE" // Disable auto-vacuum for embedded systems
        )
        
        logger.info("Creating embedded database: ${embeddedConfig.name}")
        return createDatabase(embeddedConfig)
    }
    
    /**
     * Creates a database with custom configuration.
     * 
     * @param name Database name
     * @param version Database version
     * @param enableForeignKeys Whether to enable foreign key constraints
     * @param enableWAL Whether to enable Write-Ahead Logging
     * @param journalMode Journal mode (DELETE, WAL, TRUNCATE, PERSIST, MEMORY, OFF)
     * @param synchronous Synchronous mode (OFF, NORMAL, FULL, EXTRA)
     * @param cacheSize Cache size in pages (negative values indicate KB)
     * @param tempStore Temporary storage location (FILE, MEMORY)
     * @param mmapSize Memory-mapped I/O size in bytes
     * @param pageSize Page size in bytes
     * @param autoVacuum Auto-vacuum mode (NONE, FULL, INCREMENTAL)
     * @return A Database implementation with custom settings
     */
    fun createCustomDatabase(
        name: String = "multiauth_custom.db",
        version: Int = 1,
        enableForeignKeys: Boolean = true,
        enableWAL: Boolean = true,
        journalMode: String = "WAL",
        synchronous: String = "NORMAL",
        cacheSize: Int = -2000,
        tempStore: String = "MEMORY",
        mmapSize: Long = 268435456,
        pageSize: Int = 4096,
        autoVacuum: String = "INCREMENTAL"
    ): Database {
        val customConfig = DatabaseConfig(
            name = name,
            version = version,
            enableForeignKeys = enableForeignKeys,
            enableWAL = enableWAL,
            journalMode = journalMode,
            synchronous = synchronous,
            cacheSize = cacheSize,
            tempStore = tempStore,
            mmapSize = mmapSize,
            pageSize = pageSize,
            autoVacuum = autoVacuum
        )
        
        logger.info("Creating custom database: ${customConfig.name}")
        return createDatabase(customConfig)
    }
    
    /**
     * Gets information about supported database types.
     * 
     * @return List of supported database information
     */
    fun getSupportedDatabases(): List<DatabaseTypeInfo> {
        return listOf(
            DatabaseTypeInfo(
                name = "SQLite",
                description = "Lightweight, serverless database engine",
                supportsTransactions = true,
                supportsForeignKeys = true,
                supportsFullTextSearch = false,
                bestFor = listOf("Embedded applications", "Mobile apps", "Development", "Small to medium scale")
            )
            // Future database types can be added here
        )
    }
    
    /**
     * Validates database configuration.
     * 
     * @param config The database configuration to validate
     * @return Validation result with any issues found
     */
    fun validateConfiguration(config: DatabaseConfig): DatabaseValidationResult {
        val issues = mutableListOf<String>()
        
        // Validate database name
        if (config.name.isBlank()) {
            issues.add("Database name cannot be blank")
        }
        
        if (!config.name.endsWith(".db")) {
            issues.add("Database name should end with .db extension")
        }
        
        // Validate version
        if (config.version < 1) {
            issues.add("Database version must be at least 1")
        }
        
        // Validate cache size
        if (config.cacheSize == 0) {
            issues.add("Cache size cannot be 0")
        }
        
        // Validate page size
        if (config.pageSize !in listOf(512, 1024, 2048, 4096, 8192, 16384, 32768)) {
            issues.add("Page size must be a power of 2 between 512 and 32768")
        }
        
        // Validate journal mode
        val validJournalModes = listOf("DELETE", "WAL", "TRUNCATE", "PERSIST", "MEMORY", "OFF")
        if (config.journalMode !in validJournalModes) {
            issues.add("Invalid journal mode: ${config.journalMode}. Valid modes: $validJournalModes")
        }
        
        // Validate synchronous mode
        val validSynchronousModes = listOf("OFF", "NORMAL", "FULL", "EXTRA")
        if (config.synchronous !in validSynchronousModes) {
            issues.add("Invalid synchronous mode: ${config.synchronous}. Valid modes: $validSynchronousModes")
        }
        
        // Validate auto-vacuum mode
        val validAutoVacuumModes = listOf("NONE", "FULL", "INCREMENTAL")
        if (config.autoVacuum !in validAutoVacuumModes) {
            issues.add("Invalid auto-vacuum mode: ${config.autoVacuum}. Valid modes: $validAutoVacuumModes")
        }
        
        return DatabaseValidationResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
}

/**
 * Information about a supported database type.
 */
data class DatabaseTypeInfo(
    val name: String,
    val description: String,
    val supportsTransactions: Boolean,
    val supportsForeignKeys: Boolean,
    val supportsFullTextSearch: Boolean,
    val bestFor: List<String>
)

/**
 * Result of database configuration validation.
 */
data class DatabaseValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)