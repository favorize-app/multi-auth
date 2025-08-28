package app.multiauth.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Interface for secure storage operations across different platforms.
 * Provides methods for storing and retrieving sensitive data like authentication tokens.
 */
interface SecureStorage {
    
    /**
     * Stores a value securely with the given key.
     * 
     * @param key The key to store the value under
     * @param value The value to store
     * @return true if the operation was successful, false otherwise
     */
    suspend fun store(key: String, value: String): Boolean
    
    /**
     * Retrieves a value by its key.
     * 
     * @param key The key to retrieve the value for
     * @return The stored value, or null if not found or error occurred
     */
    suspend fun retrieve(key: String): String?
    
    /**
     * Removes a value by its key.
     * 
     * @param key The key to remove
     * @return true if the operation was successful, false otherwise
     */
    suspend fun remove(key: String): Boolean
    
    /**
     * Checks if a key exists in storage.
     * 
     * @param key The key to check
     * @return true if the key exists, false otherwise
     */
    suspend fun contains(key: String): Boolean
    
    /**
     * Clears all stored data.
     * 
     * @return true if the operation was successful, false otherwise
     */
    suspend fun clear(): Boolean
    
    /**
     * Gets a flow of all stored keys.
     * 
     * @return Flow of all keys currently in storage
     */
    fun getAllKeys(): Flow<Set<String>>
    
    /**
     * Gets the total number of stored items.
     * 
     * @return The count of stored items
     */
    suspend fun getItemCount(): Int
}

/**
 * Base implementation of SecureStorage with common functionality.
 * Platform-specific implementations should extend this class.
 */
abstract class BaseSecureStorage : SecureStorage {
    
    protected val logger = app.multiauth.util.LoggerLogger(this::class)
    
    override suspend fun contains(key: String): Boolean {
        return retrieve(key) != null
    }
    
    override suspend fun getItemCount(): Int {
        return getAllKeys().first().size
    }
    
    protected fun validateKey(key: String): Boolean {
        if (key.isBlank()) {
            logger.error("BaseSecureStorage", "Storage key cannot be blank")
            return false
        }
        return true
    }
    
    protected fun validateValue(value: String): Boolean {
        if (value.isBlank()) {
            logger.error("BaseSecureStorage", "Storage value cannot be blank")
            return false
        }
        return true
    }
}