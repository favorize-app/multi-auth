package app.multiauth.storage

import app.multiauth.platform.Platform
import app.multiauth.platform.PlatformUtils
import app.multiauth.util.Logger

/**
 * Factory for creating platform-specific secure storage implementations.
 * Uses platform detection to provide the appropriate storage provider.
 */
object StorageFactory {
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Creates and returns the appropriate secure storage implementation for the current platform.
     * 
     * @return A SecureStorage implementation for the current platform
     * @throws UnsupportedOperationException if the platform is not supported
     */
    fun createSecureStorage(): SecureStorage {
        return when (PlatformUtils.currentPlatform) {
            Platform.ANDROID -> createAndroidStorage()
            Platform.IOS -> createIOSStorage()
            Platform.WEB -> createWebStorage()
            Platform.DESKTOP -> createDesktopStorage()
            Platform.UNKNOWN -> throw UnsupportedOperationException("Unknown platform detected")
        }
    }
    
    /**
     * Creates an Android-specific secure storage implementation.
     * This will be implemented in the Android-specific source set.
     */
    private fun createAndroidStorage(): SecureStorage {
        logger.info("StorageFactory", "Creating Android secure storage")
        // This will be implemented in androidMain
        throw UnsupportedOperationException("Android storage not implemented in common module")
    }
    
    /**
     * Creates an iOS-specific secure storage implementation.
     * This will be implemented in the iOS-specific source set.
     */
    private fun createIOSStorage(): SecureStorage {
        logger.info("StorageFactory", "Creating iOS secure storage")
        // This will be implemented in iosMain
        throw UnsupportedOperationException("iOS storage not implemented in common module")
    }
    
    /**
     * Creates a Web-specific secure storage implementation.
     * This will be implemented in the JavaScript-specific source set.
     */
    private fun createWebStorage(): SecureStorage {
        logger.info("StorageFactory", "Creating Web secure storage")
        // This will be implemented in jsMain
        throw UnsupportedOperationException("Web storage not implemented in common module")
    }
    
    /**
     * Creates a Desktop-specific secure storage implementation.
     * This will be implemented in the JVM-specific source set.
     */
    private fun createDesktopStorage(): SecureStorage {
        logger.info("StorageFactory", "Creating Desktop secure storage")
        // This will be implemented in jvmMain
        throw UnsupportedOperationException("Desktop storage not implemented in common module")
    }
    
    /**
     * Creates a mock secure storage for testing purposes.
     * 
     * @return A MockSecureStorage instance
     */
    fun createMockStorage(): SecureStorage {
        logger.info("StorageFactory", "Creating mock secure storage for testing")
        return MockSecureStorage()
    }
    
    /**
     * Checks if the current platform supports secure storage.
     * 
     * @return true if secure storage is supported, false otherwise
     */
    fun isSecureStorageSupported(): Boolean {
        return PlatformUtils.supportsFeature(app.multiauth.platform.PlatformFeature.SECURE_STORAGE)
    }
}

/**
 * Mock implementation of SecureStorage for testing purposes.
 * Stores data in memory and is not secure.
 */
class MockSecureStorage : BaseSecureStorage() {
    
    private val storage = mutableMapOf<String, String>()
    
    override suspend fun store(key: String, value: String): Boolean {
        if (!validateKey(key) || !validateValue(value)) {
            return false
        }
        
        return try {
            storage[key] = value
            logger.debug("MockSecureStorage", "Mock storage: stored key '$key'")
            true
        } catch (e: Exception) {
            logger.error("MockSecureStorage", "Mock storage: failed to store key '$key': ${e.message}")
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        if (!validateKey(key)) {
            return null
        }
        
        return try {
            val value = storage[key]
            logger.debug("MockSecureStorage", "Mock storage: retrieved key '$key' -> ${if (value != null) "found" else "not found"}")
            value
        } catch (e: Exception) {
            logger.error("MockSecureStorage", "Mock storage: failed to retrieve key '$key': ${e.message}")
            null
        }
    }
    
    override suspend fun remove(key: String): Boolean {
        if (!validateKey(key)) {
            return false
        }
        
        return try {
            val removed = storage.remove(key) != null
            logger.debug("MockSecureStorage", "Mock storage: removed key '$key' -> $removed")
            removed
        } catch (e: Exception) {
            logger.error("MockSecureStorage", "Mock storage: failed to remove key '$key': ${e.message}")
            false
        }
    }
    
    override suspend fun clear(): Boolean {
        return try {
            val size = storage.size
            storage.clear()
            logger.debug("MockSecureStorage", "Mock storage: cleared $size items")
            true
        } catch (e: Exception) {
            logger.error("MockSecureStorage", "Mock storage: failed to clear storage: ${e.message}")
            false
        }
    }
    
    override fun getAllKeys(): kotlinx.coroutines.flow.Flow<Set<String>> {
        return kotlinx.coroutines.flow.flowOf(storage.keys.toSet())
    }
    
    override suspend fun getItemCount(): Int {
        return storage.size
    }
}