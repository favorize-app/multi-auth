package app.multiauth.storage

import app.multiauth.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.browser.localStorage
import kotlinx.browser.window

/**
 * Web-specific secure storage implementation using browser localStorage.
 * For production use, this should be enhanced with encryption.
 * Note: localStorage is not encrypted but provides persistent storage.
 */
class WebSecureStorage : SecureStorage {
    
    private val logger = Logger.getLogger(this::class)
    
    companion object {
        private const val KEY_PREFIX = "multiauth_secure_"
    }
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            logger.debug("WebSecureStorage", "Storing value for key: $key")
            val prefixedKey = KEY_PREFIX + key
            localStorage.setItem(prefixedKey, value)
            logger.debug("WebSecureStorage", "Successfully stored key: $key")
            true
        } catch (e: Exception) {
            logger.error("WebSecureStorage", "Error storing key: $key", e)
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        return try {
            logger.debug("WebSecureStorage", "Retrieving value for key: $key")
            val prefixedKey = KEY_PREFIX + key
            val value = localStorage.getItem(prefixedKey)
            if (value != null) {
                logger.debug("WebSecureStorage", "Successfully retrieved key: $key")
            } else {
                logger.debug("WebSecureStorage", "Key not found: $key")
            }
            value
        } catch (e: Exception) {
            logger.error("WebSecureStorage", "Error retrieving key: $key", e)
            null
        }
    }
    
    override suspend fun remove(key: String): Boolean {
        return try {
            logger.debug("WebSecureStorage", "Removing key: $key")
            val prefixedKey = KEY_PREFIX + key
            localStorage.removeItem(prefixedKey)
            logger.debug("WebSecureStorage", "Successfully removed key: $key")
            true
        } catch (e: Exception) {
            logger.error("WebSecureStorage", "Error removing key: $key", e)
            false
        }
    }
    
    override suspend fun contains(key: String): Boolean {
        return try {
            val prefixedKey = KEY_PREFIX + key
            localStorage.getItem(prefixedKey) != null
        } catch (e: Exception) {
            logger.error("WebSecureStorage", "Error checking key: $key", e)
            false
        }
    }
    
    override suspend fun clear(): Boolean {
        return try {
            logger.debug("WebSecureStorage", "Clearing all stored data")
            
            // Get all keys with our prefix and remove them
            val keysToRemove = mutableListOf<String>()
            for (i in 0 until localStorage.length) {
                val key = localStorage.key(i)
                if (key != null && key.startsWith(KEY_PREFIX)) {
                    keysToRemove.add(key)
                }
            }
            
            keysToRemove.forEach { localStorage.removeItem(it) }
            
            logger.debug("WebSecureStorage", "Successfully cleared ${keysToRemove.size} items")
            true
            
        } catch (e: Exception) {
            logger.error("WebSecureStorage", "Error clearing data", e)
            false
        }
    }
    
    override fun getAllKeys(): Flow<Set<String>> = flow {
        try {
            logger.debug("WebSecureStorage", "Getting all keys")
            
            val keys = mutableSetOf<String>()
            for (i in 0 until localStorage.length) {
                val key = localStorage.key(i)
                if (key != null && key.startsWith(KEY_PREFIX)) {
                    // Remove the prefix to get the original key
                    keys.add(key.removePrefix(KEY_PREFIX))
                }
            }
            
            logger.debug("WebSecureStorage", "Retrieved ${keys.size} keys")
            emit(keys)
            
        } catch (e: Exception) {
            logger.error("WebSecureStorage", "Error getting all keys", e)
            emit(emptySet())
        }
    }
    
    override suspend fun getItemCount(): Int {
        return try {
            val keys = getAllKeys()
            keys.first().size
        } catch (e: Exception) {
            logger.error("WebSecureStorage", "Error getting item count", e)
            0
        }
    }
    
    /**
     * Gets web storage information for debugging and monitoring.
     */
    fun getWebStorageInfo(): WebStorageInfo {
        return WebStorageInfo(
            isWebCryptoSupported = isWebCryptoSupported(),
            isIndexedDBSupported = isIndexedDBSupported(),
            databaseName = "localStorage",
            databaseVersion = 1,
            storeName = "localStorage"
        )
    }
    
    /**
     * Checks if the browser supports Web Crypto API.
     */
    fun isWebCryptoSupported(): Boolean {
        return try {
            js("typeof window !== 'undefined' && 'crypto' in window && 'subtle' in window.crypto") as Boolean
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Checks if the browser supports IndexedDB.
     */
    fun isIndexedDBSupported(): Boolean {
        return try {
            js("typeof window !== 'undefined' && 'indexedDB' in window") as Boolean
        } catch (e: Exception) {
            false
        }
    }
}