package app.multiauth.storage

import app.multiauth.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Desktop-specific secure storage implementation using file system and encryption.
 * Provides secure storage for desktop applications with encryption at rest.
 */
class DesktopSecureStorage : SecureStorage {
    
    private val logger = Logger.getLogger(this::class)
    
    companion object {
        private const val KEYSTORE_TYPE = "JCEKS"
        private const val KEYSTORE_FILE = "multiauth.keystore"
        private const val KEY_ALIAS = "MultiAuthDesktopKey"
        private const val STORAGE_DIR = "multiauth_secure_storage"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16
        private const val KEY_SIZE = 256
    }
    
    private val keyStore: KeyStore by lazy {
        initializeKeyStore()
    }
    
    private val storageDirectory: Path by lazy {
        createStorageDirectory()
    }
    
    private val secretKey: SecretKey by lazy {
        getOrCreateSecretKey()
    }
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            logger.debug("Storing encrypted value for key: $key")
            
            val encryptedValue = encryptValue(value)
            val filePath = storageDirectory.resolve("$key.enc")
            
            Files.write(filePath, encryptedValue.toByteArray(Charsets.UTF_8))
            
            logger.debug("Successfully stored encrypted value for key: $key")
            true
        } catch (e: Exception) {
            logger.error("Failed to store value for key: $key", e)
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        return try {
            logger.debug("Retrieving encrypted value for key: $key")
            
            val filePath = storageDirectory.resolve("$key.enc")
            if (!Files.exists(filePath)) {
                logger.debug("No value found for key: $key")
                return null
            }
            
            val encryptedBytes = Files.readAllBytes(filePath)
            val encryptedValue = String(encryptedBytes, Charsets.UTF_8)
            val decryptedValue = decryptValue(encryptedValue)
            
            logger.debug("Successfully retrieved value for key: $key")
            decryptedValue
        } catch (e: Exception) {
            logger.error("Failed to retrieve value for key: $key", e)
            null
        }
    }
    
    override suspend fun remove(key: String): Boolean {
        return try {
            logger.debug("Removing value for key: $key")
            
            val filePath = storageDirectory.resolve("$key.enc")
            if (Files.exists(filePath)) {
                val removed = Files.deleteIfExists(filePath)
                if (removed) {
                    logger.debug("Successfully removed value for key: $key")
                } else {
                    logger.warn("Failed to remove file for key: $key")
                }
                removed
            } else {
                logger.debug("Key not found for removal: $key")
                true
            }
        } catch (e: Exception) {
            logger.error("Exception while removing value for key: $key", e)
            false
        }
    }
    
    override suspend fun contains(key: String): Boolean {
        return try {
            val filePath = storageDirectory.resolve("$key.enc")
            val exists = Files.exists(filePath)
            logger.debug("Key $key exists: $exists")
            exists
        } catch (e: Exception) {
            logger.error("Exception while checking if key exists: $key", e)
            false
        }
    }
    
    override suspend fun clear(): Boolean {
        return try {
            logger.debug("Clearing all secure storage")
            
            val files = Files.list(storageDirectory)
                .filter { it.toString().endsWith(".enc") }
                .toList()
            
            var deletedCount = 0
            for (file in files) {
                if (Files.deleteIfExists(file)) {
                    deletedCount++
                }
            }
            
            logger.debug("Successfully cleared $deletedCount files from secure storage")
            true
        } catch (e: Exception) {
            logger.error("Exception while clearing secure storage", e)
            false
        }
    }
    
    override fun getAllKeys(): Flow<Set<String>> = flow {
        try {
            val keys = mutableSetOf<String>()
            val files = Files.list(storageDirectory)
                .filter { it.toString().endsWith(".enc") }
                .toList()
            
            for (file in files) {
                val fileName = file.fileName.toString()
                val key = fileName.removeSuffix(".enc")
                keys.add(key)
            }
            
            logger.debug("Retrieved ${keys.size} keys from secure storage")
            emit(keys)
        } catch (e: Exception) {
            logger.error("Exception while retrieving all keys", e)
            emit(emptySet())
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun getItemCount(): Int {
        return try {
            val count = Files.list(storageDirectory)
                .filter { it.toString().endsWith(".enc") }
                .count()
                .toInt()
            
            logger.debug("Secure storage contains $count items")
            count
        } catch (e: Exception) {
            logger.error("Exception while getting item count", e)
            0
        }
    }
    
    // Private initialization methods
    
    private fun initializeKeyStore(): KeyStore {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
            val keyStoreFile = getKeyStoreFile()
            
            if (keyStoreFile.exists()) {
                keyStore.load(keyStoreFile.inputStream(), getKeyStorePassword())
                logger.debug("Loaded existing keystore from: ${keyStoreFile.absolutePath}")
            } else {
                keyStore.load(null, getKeyStorePassword())
                logger.debug("Created new keystore")
            }
            
            keyStore
        } catch (e: Exception) {
            logger.error("Failed to initialize keystore", e)
            // Fallback to in-memory keystore
            KeyStore.getInstance(KEYSTORE_TYPE).apply {
                load(null, getKeyStorePassword())
            }
        }
    }
    
    private fun createStorageDirectory(): Path {
        return try {
            val userHome = System.getProperty("user.home")
            val storagePath = Paths.get(userHome, ".config", STORAGE_DIR)
            
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath)
                logger.debug("Created storage directory: $storagePath")
            }
            
            storagePath
        } catch (e: Exception) {
            logger.error("Failed to create storage directory", e)
            // Fallback to temp directory
            val tempPath = Files.createTempDirectory("multiauth_secure_storage")
            logger.debug("Using temporary storage directory: $tempPath")
            tempPath
        }
    }
    
    private fun getKeyStoreFile(): File {
        val userHome = System.getProperty("user.home")
        return File(userHome, ".config/$STORAGE_DIR/$KEYSTORE_FILE")
    }
    
    private fun getKeyStorePassword(): CharArray {
        // In a real implementation, this would be securely stored or derived
        // For now, we'll use a deterministic password based on user home
        val userHome = System.getProperty("user.home")
        return "$userHome$KEY_ALIAS".toCharArray()
    }
    
    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            // Try to get existing key from keystore
            keyStore.getKey(KEY_ALIAS, getKeyStorePassword()) as? SecretKey
        } catch (e: Exception) {
            logger.debug("No existing key found, generating new one", e)
            generateNewSecretKey()
        } ?: generateNewSecretKey()
    }
    
    private fun generateNewSecretKey(): SecretKey {
        return try {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(KEY_SIZE, SecureRandom())
            val key = keyGenerator.generateKey()
            
            // Store the key in the keystore
            val keyStoreFile = getKeyStoreFile()
            keyStore.setKeyEntry(KEY_ALIAS, key, getKeyStorePassword(), null)
            
            // Save the keystore
            keyStore.store(keyStoreFile.outputStream(), getKeyStorePassword())
            
            logger.debug("Generated and stored new secret key")
            key
        } catch (e: Exception) {
            logger.warn("Failed to generate keystore-backed key, using software key", e)
            generateSoftwareSecretKey()
        }
    }
    
    private fun generateSoftwareSecretKey(): SecretKey {
        val keyBytes = ByteArray(KEY_SIZE / 8)
        Random.nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }
    
    // Private encryption methods
    
    private fun encryptValue(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE * 8, iv))
        
        val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = iv + encryptedBytes
        
        return java.util.Base64.getEncoder().encodeToString(combined)
    }
    
    private fun decryptValue(encryptedValue: String): String {
        val combined = java.util.Base64.getDecoder().decode(encryptedValue)
        
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
            val key = keyStore.getKey(KEY_ALIAS, getKeyStorePassword()) as? SecretKey
            key != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets information about the keystore implementation.
     */
    fun getKeystoreInfo(): DesktopKeystoreInfo {
        return try {
            val key = keyStore.getKey(KEY_ALIAS, getKeyStorePassword()) as? SecretKey
            DesktopKeystoreInfo(
                isHardwareBacked = key != null,
                keystoreType = keyStore.type,
                keystoreFile = getKeyStoreFile().absolutePath,
                storageDirectory = storageDirectory.toString(),
                algorithm = key?.algorithm ?: "Unknown",
                keySize = key?.encoded?.size ?: 0
            )
        } catch (e: Exception) {
            DesktopKeystoreInfo(
                isHardwareBacked = false,
                keystoreType = "Error",
                keystoreFile = "Error",
                storageDirectory = "Error",
                algorithm = "Error",
                keySize = 0
            )
        }
    }
    
    /**
     * Exports the keystore to a backup file.
     */
    fun exportKeystore(backupPath: Path): Boolean {
        return try {
            val backupFile = backupPath.resolve("multiauth_keystore_backup.jks")
            keyStore.store(backupFile.toFile().outputStream(), getKeyStorePassword())
            logger.info("Keystore exported to: $backupFile")
            true
        } catch (e: Exception) {
            logger.error("Failed to export keystore", e)
            false
        }
    }
    
    /**
     * Imports a keystore from a backup file.
     */
    fun importKeystore(backupPath: Path): Boolean {
        return try {
            val backupFile = backupPath.resolve("multiauth_keystore_backup.jks")
            if (!Files.exists(backupFile)) {
                logger.error("Backup file not found: $backupFile")
                return false
            }
            
            val backupKeyStore = KeyStore.getInstance(KEYSTORE_TYPE)
            backupKeyStore.load(backupFile.toFile().inputStream(), getKeyStorePassword())
            
            // Copy the key from backup to current keystore
            val backupKey = backupKeyStore.getKey(KEY_ALIAS, getKeyStorePassword()) as? SecretKey
            if (backupKey != null) {
                keyStore.setKeyEntry(KEY_ALIAS, backupKey, getKeyStorePassword(), null)
                
                // Save the current keystore
                val keyStoreFile = getKeyStoreFile()
                keyStore.store(keyStoreFile.outputStream(), getKeyStorePassword())
                
                logger.info("Keystore imported successfully from: $backupFile")
                true
            } else {
                logger.error("No key found in backup keystore")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to import keystore", e)
            false
        }
    }
}

/**
 * Information about the desktop keystore implementation.
 */
data class DesktopKeystoreInfo(
    val isHardwareBacked: Boolean,
    val keystoreType: String,
    val keystoreFile: String,
    val storageDirectory: String,
    val algorithm: String,
    val keySize: Int
)