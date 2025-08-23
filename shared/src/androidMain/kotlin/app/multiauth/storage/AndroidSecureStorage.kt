package app.multiauth.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.multiauth.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Android-specific secure storage implementation using Android Keystore and encryption.
 * Provides hardware-backed security when available, falling back to software encryption.
 */
class AndroidSecureStorage(
    private val context: Context
) : SecureStorage {
    
    private val logger = Logger.getLogger(this::class)
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "MultiAuthSecureStorage"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "secure_storage"
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16
        private const val KEY_SIZE = 256
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val secretKey: SecretKey by lazy {
        getOrCreateSecretKey()
    }
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            logger.debug("secure storage", "Storing encrypted value for key: $key")
            
            val encryptedValue = encryptValue(value)
            sharedPreferences.edit()
                .putString(key, encryptedValue)
                .apply()
            
            logger.debug("secure storage", "Successfully stored encrypted value for key: $key")
            true
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to store value for key: $key", e)
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        return try {
            logger.debug("secure storage", "Retrieving encrypted value for key: $key")
            
            val encryptedValue = sharedPreferences.getString(key, null)
            if (encryptedValue == null) {
                logger.debug("secure storage", "No value found for key: $key")
                return null
            }
            
            val decryptedValue = decryptValue(encryptedValue)
            logger.debug("secure storage", "Successfully retrieved value for key: $key")
            decryptedValue
        } catch (e: Exception) {
            logger.error("secure storage", "Failed to retrieve value for key: $key", e)
            null
        }
    }
    
    override suspend fun remove(key: String): Boolean {
        return try {
            logger.debug("secure storage", "Removing value for key: $key")
            
            val removed = sharedPreferences.edit()
                .remove(key)
                .commit()
            
            if (removed) {
                logger.debug("secure storage", "Successfully removed value for key: $key")
            } else {
                logger.warn("secure storage", "Failed to remove value for key: $key")
            }
            
            removed
        } catch (e: Exception) {
            logger.error("secure storage", "Exception while removing value for key: $key", e)
            false
        }
    }
    
    override suspend fun contains(key: String): Boolean {
        return try {
            val exists = sharedPreferences.contains(key)
            logger.debug("secure storage", "Key $key exists: $exists")
            exists
        } catch (e: Exception) {
            logger.error("secure storage", "Exception while checking if key exists: $key", e)
            false
        }
    }
    
    override suspend fun clear(): Boolean {
        return try {
            logger.debug("secure storage", "Clearing all secure storage")
            
            val cleared = sharedPreferences.edit()
                .clear()
                .commit()
            
            if (cleared) {
                logger.debug("secure storage", "Successfully cleared all secure storage")
            } else {
                logger.warn("secure storage", "Failed to clear secure storage")
            }
            
            cleared
        } catch (e: Exception) {
            logger.error("secure storage", "Exception while clearing secure storage", e)
            false
        }
    }
    
    override fun getAllKeys(): Flow<Set<String>> = flow {
        try {
            val allKeys = sharedPreferences.all.keys
            logger.debug("secure storage", "Retrieved ${allKeys.size} keys from secure storage")
            emit(allKeys)
        } catch (e: Exception) {
            logger.error("secure storage", "Exception while retrieving all keys", e)
            emit(emptySet())
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun getItemCount(): Int {
        return try {
            val count = sharedPreferences.all.size
            logger.debug("secure storage", "Secure storage contains $count items")
            count
        } catch (e: Exception) {
            logger.error("secure storage", "Exception while getting item count", e)
            0
        }
    }
    
    // Private encryption methods
    
    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            // Try to get existing key from keystore
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            logger.debug("No existing key found, generating new one", e)
            generateNewSecretKey()
        } ?: generateNewSecretKey()
    }
    
    private fun generateNewSecretKey(): SecretKey {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).apply {
                setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                setKeySize(KEY_SIZE)
                setUserAuthenticationRequired(false)
                setUserAuthenticationValidityDurationSeconds(-1)
            }.build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            logger.warn("Failed to generate hardware-backed key, using software key", e)
            generateSoftwareSecretKey()
        }
    }
    
    private fun generateSoftwareSecretKey(): SecretKey {
        val keyBytes = ByteArray(KEY_SIZE / 8)
        Random.nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES)
    }
    
    private fun encryptValue(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE)
        Random.nextBytes(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE * 8, iv))
        
        val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = iv + encryptedBytes
        
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }
    
    private fun decryptValue(encryptedValue: String): String {
        val combined = android.util.Base64.decode(encryptedValue, android.util.Base64.NO_WRAP)
        
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encryptedBytes = combined.copyOfRange(IV_SIZE, combined.size)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE * 8, iv))
        
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
    
    /**
     * Checks if hardware-backed keystore is available.
     */
    fun isHardwareBacked(): Boolean {
        return try {
            val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            key != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets information about the keystore implementation.
     */
    fun getKeystoreInfo(): KeystoreInfo {
        return try {
            val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            KeystoreInfo(
                isHardwareBacked = key != null,
                provider = keyStore.provider?.name ?: "Unknown",
                algorithm = key?.algorithm ?: "Unknown",
                keySize = key?.encoded?.size ?: 0
            )
        } catch (e: Exception) {
            KeystoreInfo(
                isHardwareBacked = false,
                provider = "Error",
                algorithm = "Error",
                keySize = 0
            )
        }
    }
}

/**
 * Information about the keystore implementation.
 */
data class KeystoreInfo(
    val isHardwareBacked: Boolean,
    val provider: String,
    val algorithm: String,
    val keySize: Int
)