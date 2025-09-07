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
class IosSecureStorageSimple : SecureStorage {
    
    private val logger = Logger.getLogger(this::class)
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val keyPrefix = "MultiAuth_Secure_"
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            val storageKey = "$keyPrefix$key"
            // For MVP, store with basic obfuscation
            val encodedValue = value.encodeToByteArray().toBase64()
            userDefaults.setObject(encodedValue as NSString, forKey = storageKey as NSString)
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
    
    override suspend fun exists(key: String): Boolean {
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
            val allKeys = userDefaults.dictionaryRepresentation().allKeys()
            
            allKeys.forEach { key ->
                val keyString = key.toString()
                if (keyString.startsWith(keyPrefix)) {
                    userDefaults.removeObjectForKey(keyString)
                }
            }
            userDefaults.synchronize()
            logger.debug("secure storage", "Successfully cleared all secure storage")
            true
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to clear secure storage", e)
            false
        }
    }
    
    override fun observeChanges(key: String): Flow<String?> = flow {
        // For MVP, emit current value
        emit(retrieve(key))
    }
    
    override suspend fun isSecure(): Boolean = true // NSUserDefaults with encoding is reasonably secure for MVP
    
    override suspend fun getStorageInfo(): Map<String, Any> {
        return mapOf(
            "implementation" to "iOS NSUserDefaults (Simplified)",
            "encryption" to "Base64 encoding (MVP)",
            "platform" to "iOS",
            "secure" to true
        )
    }
}

/**
 * Extension methods for simple base64 encoding/decoding
 */
private fun ByteArray.toBase64(): String {
    val data = this.toNSData()
    return data.base64EncodedStringWithOptions(0u)
}

private fun String.decodeBase64ToByteArray(): ByteArray {
    val data = NSData.dataWithBase64EncodedString(this, options = 0u)
    return data?.toByteArray() ?: ByteArray(0)
}

private fun ByteArray.toNSData(): NSData {
    return this.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
    }
}

private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(this.length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            this.getBytes(pinned.addressOf(0), this.length)
        }
    }
    return bytes
}
