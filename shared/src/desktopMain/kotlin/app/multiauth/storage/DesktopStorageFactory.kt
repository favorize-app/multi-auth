package app.multiauth.storage

import app.multiauth.util.Logger
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Desktop-specific storage factory that provides real secure storage implementations.
 * This file should be included in the JVM build to provide actual functionality.
 */
object DesktopStorageFactory {
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Creates a Desktop-specific secure storage implementation.
     * 
     * @return DesktopSecureStorage instance
     */
    fun createDesktopStorage(): SecureStorage {
        logger.info("DesktopStorageFactory", "Creating Desktop secure storage")
        return DesktopSecureStorage()
    }
    
    /**
     * Gets information about the Desktop storage implementation.
     * 
     * @return DesktopStorageInfo with implementation details
     */
    fun getStorageInfo(): DesktopStorageInfo {
        val storage = DesktopSecureStorage()
        return DesktopStorageInfo(
            isHardwareBacked = storage.isHardwareBacked(),
            keystoreInfo = storage.getKeystoreInfo()
        )
    }
    
    /**
     * Checks if the system supports hardware-backed keystore.
     * 
     * @return true if hardware-backed, false otherwise
     */
    fun isHardwareBacked(): Boolean {
        val storage = DesktopSecureStorage()
        return storage.isHardwareBacked()
    }
    
    /**
     * Gets detailed keystore information.
     * 
     * @return DesktopKeystoreInfo with detailed implementation information
     */
    fun getKeystoreInfo(): DesktopKeystoreInfo {
        val storage = DesktopSecureStorage()
        return storage.getKeystoreInfo()
    }
    
    /**
     * Exports the keystore to a backup location.
     * 
     * @param backupPath The path to export the keystore to
     * @return true if export successful, false otherwise
     */
    fun exportKeystore(backupPath: Path): Boolean {
        val storage = DesktopSecureStorage()
        return storage.exportKeystore(backupPath)
    }
    
    /**
     * Exports the keystore to the default backup location.
     * 
     * @return true if export successful, false otherwise
     */
    fun exportKeystoreToDefault(): Boolean {
        val userHome = System.getProperty("user.home")
        val backupPath = Paths.get(userHome, "Downloads", "multiauth_backup")
        
        if (!backupPath.toFile().exists()) {
            backupPath.toFile().mkdirs()
        }
        
        return exportKeystore(backupPath)
    }
    
    /**
     * Imports a keystore from a backup location.
     * 
     * @param backupPath The path to import the keystore from
     * @return true if import successful, false otherwise
     */
    fun importKeystore(backupPath: Path): Boolean {
        val storage = DesktopSecureStorage()
        return storage.importKeystore(backupPath)
    }
    
    /**
     * Gets the default storage directory path.
     * 
     * @return Path to the default storage directory
     */
    fun getDefaultStoragePath(): Path {
        val userHome = System.getProperty("user.home")
        return Paths.get(userHome, ".config", "multiauth_secure_storage")
    }
    
    /**
     * Gets the default keystore file path.
     * 
     * @return Path to the default keystore file
     */
    fun getDefaultKeystorePath(): Path {
        return getDefaultStoragePath().resolve("multiauth.keystore")
    }
    
    /**
     * Checks if the default storage directory exists.
     * 
     * @return true if exists, false otherwise
     */
    fun isDefaultStorageDirectoryExists(): Boolean {
        return getDefaultStoragePath().toFile().exists()
    }
    
    /**
     * Creates the default storage directory if it doesn't exist.
     * 
     * @return true if created or already exists, false if creation failed
     */
    fun createDefaultStorageDirectory(): Boolean {
        return try {
            val path = getDefaultStoragePath()
            if (!path.toFile().exists()) {
                path.toFile().mkdirs()
                logger.info("DesktopStorageFactory", "Created default storage directory: $path")
            }
            true
        } catch (e: Exception) {
            logger.error("DesktopStorageFactory", "Failed to create default storage directory", e)
            false
        }
    }
    
    /**
     * Gets a comprehensive storage status report.
     * 
     * @return DesktopStorageStatusReport with detailed status information
     */
    fun getStorageStatusReport(): DesktopStorageStatusReport {
        val storage = DesktopSecureStorage()
        val defaultPath = getDefaultStoragePath()
        val keystorePath = getDefaultKeystorePath()
        
        return DesktopStorageStatusReport(
            isHardwareBacked = storage.isHardwareBacked(),
            keystoreInfo = storage.getKeystoreInfo(),
            defaultStoragePath = defaultPath.toString(),
            defaultKeystorePath = keystorePath.toString(),
            storageDirectoryExists = defaultPath.toFile().exists(),
            keystoreFileExists = keystorePath.toFile().exists(),
            canCreateStorageDirectory = createDefaultStorageDirectory(),
            recommendations = generateRecommendations(storage, defaultPath, keystorePath)
        )
    }
    
    private fun generateRecommendations(
        storage: DesktopSecureStorage,
        storagePath: Path,
        keystorePath: Path
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!storagePath.toFile().exists()) {
            recommendations.add("Storage directory does not exist. Consider creating it.")
        }
        
        if (!keystorePath.toFile().exists()) {
            recommendations.add("Keystore file does not exist. A new one will be created on first use.")
        }
        
        if (!storage.isHardwareBacked()) {
            recommendations.add("Hardware-backed keystore not available. Using software encryption.")
        }
        
        if (storagePath.toFile().exists() && keystorePath.toFile().exists()) {
            recommendations.add("Storage system is properly configured and ready for use.")
        }
        
        return recommendations
    }
}

/**
 * Information about the Desktop storage implementation.
 */
data class DesktopStorageInfo(
    val isHardwareBacked: Boolean,
    val keystoreInfo: DesktopKeystoreInfo
)

/**
 * Comprehensive status report for the Desktop storage system.
 */
data class DesktopStorageStatusReport(
    val isHardwareBacked: Boolean,
    val keystoreInfo: DesktopKeystoreInfo,
    val defaultStoragePath: String,
    val defaultKeystorePath: String,
    val storageDirectoryExists: Boolean,
    val keystoreFileExists: Boolean,
    val canCreateStorageDirectory: Boolean,
    val recommendations: List<String>
)