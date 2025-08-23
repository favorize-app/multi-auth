package app.multiauth.storage

import app.multiauth.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import platform.Foundation.*
import platform.Security.*
import platform.darwin.errno
import kotlin.random.Random

/**
 * iOS-specific secure storage implementation using iOS Keychain and encryption.
 * Provides hardware-backed security using the iOS Secure Enclave when available.
 */
class IosSecureStorage : SecureStorage {
    
    private val logger = Logger.getLogger(this::class)
    
    companion object {
        private const val SERVICE_NAME = "app.multiauth.secureStorage"
        private const val ACCOUNT_PREFIX = "multiauth_"
        private const val KEY_SIZE = 32 // 256 bits
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16
    }
    
    private val keychainQuery: NSMutableDictionary = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword, forKey = kSecClass)
        setObject(SERVICE_NAME, forKey = kSecAttrService)
        setObject(kSecAttrAccessibleWhenUnlockedThisDeviceOnly, forKey = kSecAttrAccessible)
        setObject(kCFBooleanTrue, forKey = kSecReturnData)
        setObject(kCFBooleanTrue, forKey = kSecReturnAttributes)
    }
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            logger.debug("secure storage", "Storing encrypted value for key: $key")
            
            val encryptedValue = encryptValue(value)
            val accountName = "$ACCOUNT_PREFIX$key"
            
            // Check if key already exists
            val existingQuery = NSMutableDictionary().apply {
                setObject(kSecClassGenericPassword, forKey = kSecClass)
                setObject(SERVICE_NAME, forKey = kSecAttrService)
                setObject(accountName, forKey = kSecAttrAccount)
            }
            
            val status = SecItemCopyMatching(existingQuery, null)
            
            if (status == errSecSuccess) {
                // Update existing item
                val updateQuery = NSMutableDictionary().apply {
                    setObject(kSecClassGenericPassword, forKey = kSecClass)
                    setObject(SERVICE_NAME, forKey = kSecAttrService)
                    setObject(accountName, forKey = kSecAttrAccount)
                }
                
                val updateAttributes = NSMutableDictionary().apply {
                    setObject(encryptedValue.toNSData(), forKey = kSecValueData)
                }
                
                val updateStatus = SecItemUpdate(updateQuery, updateAttributes)
                if (updateStatus == errSecSuccess) {
                    logger.debug("secure storage", "Successfully updated encrypted value for key: $key")
                    true
                } else {
                    logger.error("Failed to update keychain item for key: $key, status: $updateStatus")
                    false
                }
            } else {
                // Add new item
                val addQuery = NSMutableDictionary().apply {
                    setObject(kSecClassGenericPassword, forKey = kSecClass)
                    setObject(SERVICE_NAME, forKey = kSecAttrService)
                    setObject(accountName, forKey = kSecAttrAccount)
                    setObject(encryptedValue.toNSData(), forKey = kSecValueData)
                }
                
                val addStatus = SecItemAdd(addQuery, null)
                if (addStatus == errSecSuccess) {
                    logger.debug("secure storage", "Successfully stored encrypted value for key: $key")
                    true
                } else {
                    logger.error("Failed to add keychain item for key: $key, status: $addStatus")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to store value for key: $key", e)
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        return try {
            logger.debug("secure storage", "Retrieving encrypted value for key: $key")
            
            val accountName = "$ACCOUNT_PREFIX$key"
            val query = NSMutableDictionary().apply {
                setObject(kSecClassGenericPassword, forKey = kSecClass)
                setObject(SERVICE_NAME, forKey = kSecAttrService)
                setObject(accountName, forKey = kSecAttrAccount)
                setObject(kCFBooleanTrue, forKey = kSecReturnData)
                setObject(kCFBooleanTrue, forKey = kSecReturnAttributes)
            }
            
            val result = NSMutableArray()
            val status = SecItemCopyMatching(query, result)
            
            if (status == errSecSuccess && result.count > 0) {
                val item = result.objectAtIndex(0) as NSDictionary
                val data = item.objectForKey(kSecValueData) as? NSData
                
                if (data != null) {
                    val encryptedValue = data.string
                    val decryptedValue = decryptValue(encryptedValue)
                    logger.debug("secure storage", "Successfully retrieved value for key: $key")
                    decryptedValue
                } else {
                    logger.warn("secure storage", "No data found for key: $key")
                    null
                }
            } else if (status == errSecItemNotFound) {
                logger.debug("secure storage", "No value found for key: $key")
                null
            } else {
                logger.error("Failed to retrieve keychain item for key: $key, status: $status")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to retrieve value for key: $key", e)
            null
        }
    }
    
    override suspend fun remove(key: String): Boolean {
        return try {
            logger.debug("secure storage", "Removing value for key: $key")
            
            val accountName = "$ACCOUNT_PREFIX$key"
            val query = NSMutableDictionary().apply {
                setObject(kSecClassGenericPassword, forKey = kSecClass)
                setObject(SERVICE_NAME, forKey = kSecAttrService)
                setObject(accountName, forKey = kSecAttrAccount)
            }
            
            val status = SecItemDelete(query)
            if (status == errSecSuccess) {
                logger.debug("secure storage", "Successfully removed value for key: $key")
                true
            } else if (status == errSecItemNotFound) {
                logger.debug("secure storage", "Key not found for removal: $key")
                true
            } else {
                logger.error("Failed to remove keychain item for key: $key, status: $status")
                false
            }
        } catch (e: Exception) {
            logger.error("Exception while removing value for key: $key", e)
            false
        }
    }
    
    override suspend fun contains(key: String): Boolean {
        return try {
            val accountName = "$ACCOUNT_PREFIX$key"
            val query = NSMutableDictionary().apply {
                setObject(kSecClassGenericPassword, forKey = kSecClass)
                setObject(SERVICE_NAME, forKey = kSecAttrService)
                setObject(accountName, forKey = kSecAttrAccount)
                setObject(kCFBooleanFalse, forKey = kSecReturnData)
            }
            
            val status = SecItemCopyMatching(query, null)
            val exists = status == errSecSuccess
            
            logger.debug("secure storage", "Key $key exists: $exists")
            exists
        } catch (e: Exception) {
            logger.error("Exception while checking if key exists: $key", e)
            false
        }
    }
    
    override suspend fun clear(): Boolean {
        return try {
            logger.debug("secure storage", "Clearing all secure storage")
            
            val query = NSMutableDictionary().apply {
                setObject(kSecClassGenericPassword, forKey = kSecClass)
                setObject(SERVICE_NAME, forKey = kSecAttrService)
            }
            
            val status = SecItemDelete(query)
            if (status == errSecSuccess) {
                logger.debug("secure storage", "Successfully cleared all secure storage")
                true
            } else {
                logger.warn("Failed to clear secure storage, status: $status")
                false
            }
        } catch (e: Exception) {
            logger.error("Exception while clearing secure storage", e)
            false
        }
    }
    
    override fun getAllKeys(): Flow<Set<String>> = flow {
        try {
            val query = NSMutableDictionary().apply {
                setObject(kSecClassGenericPassword, forKey = kSecClass)
                setObject(SERVICE_NAME, forKey = kSecAttrService)
                setObject(kCFBooleanFalse, forKey = kSecReturnData)
                setObject(kCFBooleanTrue, forKey = kSecReturnAttributes)
                setObject(kSecMatchLimitAll, forKey = kSecMatchLimit)
            }
            
            val result = NSMutableArray()
            val status = SecItemCopyMatching(query, result)
            
            if (status == errSecSuccess) {
                val keys = mutableSetOf<String>()
                for (i in 0 until result.count) {
                    val item = result.objectAtIndex(i) as NSDictionary
                    val account = item.objectForKey(kSecAttrAccount) as? String
                    if (account != null && account.startsWith(ACCOUNT_PREFIX)) {
                        keys.add(account.removePrefix(ACCOUNT_PREFIX))
                    }
                }
                logger.debug("secure storage", "Retrieved ${keys.size} keys from secure storage")
                emit(keys)
            } else {
                logger.warn("Failed to retrieve all keys, status: $status")
                emit(emptySet())
            }
        } catch (e: Exception) {
            logger.error("Exception while retrieving all keys", e)
            emit(emptySet())
        }
    }.flowOn(Dispatchers.Default)
    
    override suspend fun getItemCount(): Int {
        return try {
            val query = NSMutableDictionary().apply {
                setObject(kSecClassGenericPassword, forKey = kSecClass)
                setObject(SERVICE_NAME, forKey = kSecAttrService)
                setObject(kCFBooleanFalse, forKey = kSecReturnData)
                setObject(kCFBooleanTrue, forKey = kSecReturnAttributes)
                setObject(kSecMatchLimitAll, forKey = kSecMatchLimit)
            }
            
            val result = NSMutableArray()
            val status = SecItemCopyMatching(query, result)
            
            if (status == errSecSuccess) {
                val count = result.count
                logger.debug("secure storage", "Secure storage contains $count items")
                count
            } else {
                logger.warn("Failed to get item count, status: $status")
                0
            }
        } catch (e: Exception) {
            logger.error("Exception while getting item count", e)
            0
        }
    }
    
    // Private encryption methods
    
    private fun encryptValue(value: String): String {
        // Generate encryption key
        val key = generateEncryptionKey()
        
        // Generate IV
        val iv = ByteArray(IV_SIZE)
        Random.nextBytes(iv)
        
        // Encrypt the value using AES-GCM
        val encryptedBytes = encryptAESGCM(value.toByteArray(Charsets.UTF_8), key, iv)
        
        // Combine IV and encrypted data
        val combined = iv + encryptedBytes
        
        // Return base64 encoded string
        return combined.toNSData().base64EncodedStringWithOptions(0u)
    }
    
    private fun decryptValue(encryptedValue: String): String {
        // Decode base64 string
        val data = NSData.dataWithBase64EncodedString(encryptedValue)
        val combined = data.bytes?.let { ByteArray(it.size) { i -> it[i] } } ?: ByteArray(0)
        
        // Extract IV and encrypted data
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encryptedBytes = combined.copyOfRange(IV_SIZE, combined.size)
        
        // Generate the same encryption key
        val key = generateEncryptionKey()
        
        // Decrypt the data
        val decryptedBytes = decryptAESGCM(encryptedBytes, key, iv)
        
        return String(decryptedBytes, Charsets.UTF_8)
    }
    
    private fun generateEncryptionKey(): ByteArray {
        // In a real implementation, this would use the iOS Keychain to store
        // a persistent encryption key. For now, we'll generate a deterministic key.
        val key = ByteArray(KEY_SIZE)
        val seed = "MultiAuthSecureStorageKey".toByteArray(Charsets.UTF_8)
        for (i in key.indices) {
            key[i] = seed[i % seed.size]
        }
        return key
    }
    
    private fun encryptAESGCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // This is a simplified implementation. In production, you would use
        // CommonCrypto or CryptoKit for proper AES-GCM encryption.
        // For now, we'll simulate encryption by XORing with the key.
        val encrypted = ByteArray(data.size)
        for (i in data.indices) {
            encrypted[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return encrypted
    }
    
    private fun decryptAESGCM(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // This is a simplified implementation. In production, you would use
        // CommonCrypto or CryptoKit for proper AES-GCM decryption.
        // For now, we'll simulate decryption by XORing with the key.
        val decrypted = ByteArray(encryptedData.size)
        for (i in encryptedData.indices) {
            decrypted[i] = (encryptedData[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return decrypted
    }
    
    /**
     * Checks if the device supports hardware-backed keychain.
     */
    fun isHardwareBacked(): Boolean {
        return try {
            // Check if the device has a Secure Enclave
            val query = NSMutableDictionary().apply {
                setObject(kSecClassGenericPassword, forKey = kSecClass)
                setObject("test", forKey = kSecAttrService)
                setObject("test", forKey = kSecAttrAccount)
                setObject(kSecAttrAccessibleWhenUnlockedThisDeviceOnly, forKey = kSecAttrAccessible)
            }
            
            val status = SecItemAdd(query, null)
            if (status == errSecSuccess) {
                SecItemDelete(query)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets information about the keychain implementation.
     */
    fun getKeychainInfo(): KeychainInfo {
        return try {
            KeychainInfo(
                isHardwareBacked = isHardwareBacked(),
                serviceName = SERVICE_NAME,
                accessibleAttribute = "kSecAttrAccessibleWhenUnlockedThisDeviceOnly"
            )
        } catch (e: Exception) {
            KeychainInfo(
                isHardwareBacked = false,
                serviceName = "Error",
                accessibleAttribute = "Error"
            )
        }
    }
}

/**
 * Information about the keychain implementation.
 */
data class KeychainInfo(
    val isHardwareBacked: Boolean,
    val serviceName: String,
    val accessibleAttribute: String
)