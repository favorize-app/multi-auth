package app.multiauth.storage

import app.multiauth.util.Logger

/**
 * iOS-specific storage factory that provides real secure storage implementations.
 * This file should be included in the iOS build to provide actual functionality.
 */
object IosStorageFactory {
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Creates an iOS-specific secure storage implementation.
     * 
     * @return IosSecureStorage instance
     */
    fun createIosStorage(): SecureStorage {
        logger.info("Creating iOS secure storage")
        return IosSecureStorage()
    }
    
    /**
     * Gets information about the iOS storage implementation.
     * 
     * @return IosStorageInfo with implementation details
     */
    fun getStorageInfo(): IosStorageInfo {
        val storage = IosSecureStorage()
        return IosStorageInfo(
            isHardwareBacked = storage.isHardwareBacked(),
            keychainInfo = storage.getKeychainInfo()
        )
    }
    
    /**
     * Checks if the device supports hardware-backed keychain.
     * 
     * @return true if hardware-backed, false otherwise
     */
    fun isHardwareBacked(): Boolean {
        val storage = IosSecureStorage()
        return storage.isHardwareBacked()
    }
    
    /**
     * Gets detailed keychain information.
     * 
     * @return KeychainInfo with detailed implementation information
     */
    fun getKeychainInfo(): KeychainInfo {
        val storage = IosSecureStorage()
        return storage.getKeychainInfo()
    }
}

/**
 * Information about the iOS storage implementation.
 */
data class IosStorageInfo(
    val isHardwareBacked: Boolean,
    val keychainInfo: KeychainInfo
)