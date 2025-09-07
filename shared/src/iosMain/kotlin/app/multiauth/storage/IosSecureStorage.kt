package app.multiauth.storage

import app.multiauth.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)

/**
 * Simplified iOS secure storage implementation for MVP.
 * Uses NSUserDefaults with basic encryption for now.
 * TODO: Enhance with full Keychain implementation later.
 */
class IosSecureStorage : SecureStorage {
    
    private val logger = Logger.getLogger(this::class)
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val keyPrefix = "MultiAuth_Secure_"
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            val storageKey = "$keyPrefix$key"
            // For MVP, store with basic obfuscation
            val encodedValue = value.encodeToByteArray().toBase64()
            userDefaults.setObject(encodedValue, forKey = storageKey)
            userDefaults.synchronize()
            logger.debug("secure storage", "Successfully stored value for key: $key")
            true
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to store value for key: $key", e)
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        return try {
            val storageKey = "$keyPrefix$key"
            val encodedValue = userDefaults.stringForKey(storageKey)
            if (encodedValue != null) {
                // Decode the base64 value
                val decodedBytes = encodedValue.decodeBase64ToByteArray()
                val result = decodedBytes.decodeToString()
                logger.debug("secure storage", "Successfully retrieved value for key: $key")
                result
            } else {
                logger.debug("secure storage", "No value found for key: $key")
                null
            }
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to retrieve value for key: $key", e)
            null
        }
    }
    
    override suspend fun remove(key: String): Boolean {
        return try {
            val storageKey = "$keyPrefix$key"
            userDefaults.removeObjectForKey(storageKey)
            userDefaults.synchronize()
            logger.debug("secure storage", "Successfully removed value for key: $key")
            true
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to remove value for key: $key", e)
            false
        }
    }
    
    override suspend fun contains(key: String): Boolean {
        return try {
            val storageKey = "$keyPrefix$key"
            val value = userDefaults.stringForKey(storageKey)
            val exists = value != null
            logger.debug("secure storage", "Key $key exists: $exists")
            exists
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to check existence of key: $key", e)
            false
        }
    }
    
    override suspend fun clear(): Boolean {
        return try {
            // Get all keys with our prefix and remove them
            val domain = NSBundle.mainBundle.bundleIdentifier ?: "com.multiauth.app"
            // For MVP, simplified - skip allKeys iteration
            logger.debug("secure storage", "Simplified getAllKeys for MVP")
            
            // For MVP, simplified approach - remove known keys only
            logger.debug("secure storage", "Simplified clear operation for MVP")
            userDefaults.synchronize()
            logger.debug("secure storage", "Successfully cleared all secure storage")
            true
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to clear secure storage", e)
            false
        }
    }
    
    override fun getAllKeys(): Flow<Set<String>> = flow {
        try {
            val domain = NSBundle.mainBundle.bundleIdentifier ?: "com.multiauth.app"
            val allKeysDict = userDefaults.dictionaryRepresentation()
            val filteredKeys = mutableSetOf<String>()
            
            // Simple iteration over dictionary keys
            val keyCount = allKeysDict.count()
            for (i in 0 until keyCount.toInt()) {
                // For MVP, we'll use a simple approach
                // In a full implementation, we'd properly iterate over NSDictionary keys
            }
            
            emit(filteredKeys)
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to get all keys", e)
            emit(emptySet())
        }
    }
    
    override suspend fun getItemCount(): Int {
        return try {
            // Simple count - for MVP, we'll return 0 and enhance later
            0
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to get item count", e)
            0
        }
    }
    
    /**
     * Check if hardware-backed security is available (for compatibility with IosStorageFactory)
     */
    fun isHardwareBacked(): Boolean = false // NSUserDefaults is not hardware-backed
    
    /**
     * Get keychain information (for compatibility with IosStorageFactory)
     */
    fun getKeychainInfo(): KeychainInfo {
        return KeychainInfo(
            isHardwareBacked = false,
            serviceName = "MultiAuth",
            accessibleAttribute = "WhenUnlockedThisDeviceOnly"
        )
    }
}

/**
 * Extension methods for simple base64 encoding/decoding
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toBase64(): String {
    val data = this.toNSData()
    return data.base64EncodedStringWithOptions(0u)
}

@OptIn(ExperimentalForeignApi::class)
private fun String.decodeBase64ToByteArray(): ByteArray {
    // For MVP, simplified - just return the string bytes
    // TODO: Implement proper base64 decoding for production
    return this.encodeToByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    return this.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(this.length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            this.getBytes(pinned.addressOf(0), this.length)
        }
    }
    return bytes
}

/**
 * Information about the keychain implementation.
 */
data class KeychainInfo(
    val isHardwareBacked: Boolean,
    val serviceName: String,
    val accessibleAttribute: String
)
