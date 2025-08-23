package app.multiauth.storage

import app.multiauth.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Web-specific secure storage implementation using Web Crypto API and IndexedDB.
 * Provides client-side encryption with secure key derivation.
 */
class WebSecureStorage : SecureStorage {
    
    private val logger = Logger.getLogger(this::class)
    
    companion object {
        private const val DATABASE_NAME = "MultiAuthSecureStorage"
        private const val DATABASE_VERSION = 1
        private const val STORE_NAME = "secure_data"
        private const val KEY_NAME = "encryption_key"
        private const val SALT = "MultiAuthWebStorageSalt"
        private const val ITERATIONS = 100000
        private const val KEY_LENGTH = 256
    }
    
    private var database: IDBDatabase? = null
    private var encryptionKey: CryptoKey? = null
    
    init {
        initializeDatabase()
        generateEncryptionKey()
    }
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            logger.debug("WebSecureStorage", "Storing encrypted value for key: $key")

            val encryptedValue = encryptValue(value)
            val result = storeInIndexedDB(key, encryptedValue)
            
            if (result) {
                logger.debug("WebSecureStorage", "Successfully stored encrypted value for key: $key")
            } else {
                logger.error("WebSecureStorage", "Failed to store value in IndexedDB for key: $key")
            }
            
            result
        } catch (e: Exception) {
            logger.error("Failed to store value for key: $key", e)
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        return try {
            logger.debug("WebSecureStorage", "Retrieving encrypted value for key: $key")

            val encryptedValue = retrieveFromIndexedDB(key)
            if (encryptedValue != null) {
                val decryptedValue = decryptValue(encryptedValue)
                logger.debug("WebSecureStorage", "Successfully retrieved value for key: $key")
                decryptedValue
            } else {
                logger.debug("WebSecureStorage", "No value found for key: $key")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to retrieve value for key: $key", e)
            null
        }
    }
    
    override suspend fun remove(key: String): Boolean {
        return try {
            logger.debug("WebSecureStorage", "Removing value for key: $key")

            val result = removeFromIndexedDB(key)
            if (result) {
                logger.debug("WebSecureStorage", "Successfully removed value for key: $key")
            } else {
                logger.warn("WebSecureStorage", "Failed to remove value for key: $key")
            }
            
            result
        } catch (e: Exception) {
            logger.error("Exception while removing value for key: $key", e)
            false
        }
    }
    
    override suspend fun contains(key: String): Boolean {
        return try {
            val exists = retrieveFromIndexedDB(key) != null
            logger.debug("WebSecureStorage", "Key $key exists: $exists")
            exists
        } catch (e: Exception) {
            logger.error("Exception while checking if key exists: $key", e)
            false
        }
    }
    
    override suspend fun clear(): Boolean {
        return try {
            logger.debug("WebSecureStorage", "Clearing all secure storage")

            val result = clearIndexedDB()
            if (result) {
                logger.debug("WebSecureStorage", "Successfully cleared all secure storage")
            } else {
                logger.warn("WebSecureStorage", "Failed to clear secure storage")
            }
            
            result
        } catch (e: Exception) {
            logger.error("Exception while clearing secure storage", e)
            false
        }
    }
    
    override fun getAllKeys(): Flow<Set<String>> = flow {
        try {
            val keys = getAllKeysFromIndexedDB()
            logger.debug("WebSecureStorage", "Retrieved ${keys.size} keys from secure storage")
            emit(keys)
        } catch (e: Exception) {
            logger.error("Exception while retrieving all keys", e)
            emit(emptySet())
        }
    }.flowOn(Dispatchers.Default)
    
    override suspend fun getItemCount(): Int {
        return try {
            val count = getAllKeysFromIndexedDB().size
            logger.debug("WebSecureStorage", "Secure storage contains $count items")
            count
        } catch (e: Exception) {
            logger.error("Exception while getting item count", e)
            0
        }
    }
    
    // Private initialization methods
    
    private fun initializeDatabase() {
        val request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
        
        request.onupgradeneeded = { event ->
            val db = (event.target as IDBOpenDBRequest).result
            if (!db.objectStoreNames.contains(STORE_NAME)) {
                db.createObjectStore(STORE_NAME, js("{}"))
            }
        }
        
        request.onsuccess = { event ->
            database = (event.target as IDBOpenDBRequest).result
            logger.debug("WebSecureStorage", "IndexedDB initialized successfully")
        }
        
        request.onerror = { event ->
            logger.error("WebSecureStorage", "Failed to initialize IndexedDB", event)
        }
    }
    
    private suspend fun generateEncryptionKey() {
        try {
            // Generate a key from a password using PBKDF2
            val password = "MultiAuthWebStoragePassword"
            val salt = SALT.toByteArray(Charsets.UTF_8)
            
            val keyMaterial = awaitCryptoOperation {
                crypto.subtle.importKey(
                    "raw",
                    password.toByteArray(Charsets.UTF_8).toUint8Array(),
                    "PBKDF2",
                    false,
                    arrayOf("deriveBits", "deriveKey")
                )
            }
            
            encryptionKey = awaitCryptoOperation {
                crypto.subtle.deriveKey(
                    js("{}").apply {
                        name = "PBKDF2"
                        salt = salt.toUint8Array()
                        iterations = ITERATIONS
                        hash = "SHA-256"
                    },
                    keyMaterial,
                    js("{}").apply {
                        name = "AES-GCM"
                        length = KEY_LENGTH
                    },
                    false,
                    arrayOf("encrypt", "decrypt")
                )
            }
            
            logger.debug("WebSecureStorage", "Encryption key generated successfully")
        } catch (e: Exception) {
            logger.error("WebSecureStorage", "Failed to generate encryption key", e)
            throw e
        }
    }
    
    // Private encryption methods
    
    private suspend fun encryptValue(value: String): String {
        val key = encryptionKey ?: throw IllegalStateException("Encryption key not initialized")
        
        // Generate IV
        val iv = crypto.getRandomValues(Uint8Array(12))
        
        // Encrypt the value
        val encryptedData = awaitCryptoOperation {
            crypto.subtle.encrypt(
                js("{}").apply {
                    name = "AES-GCM"
                    iv = iv
                },
                key,
                value.toByteArray(Charsets.UTF_8).toUint8Array()
            )
        }
        
        // Combine IV and encrypted data
        val combined = Uint8Array(iv.length + encryptedData.byteLength)
        combined.set(iv, 0)
        combined.set(Uint8Array(encryptedData), iv.length)
        
        // Return base64 encoded string
        return arrayBufferToBase64(combined.buffer)
    }
    
    private suspend fun decryptValue(encryptedValue: String): String {
        val key = encryptionKey ?: throw IllegalStateException("Encryption key not initialized")
        
        // Decode base64 string
        val combined = base64ToArrayBuffer(encryptedValue)
        val combinedArray = Uint8Array(combined)
        
        // Extract IV and encrypted data
        val iv = combinedArray.slice(0, 12)
        val encryptedData = combinedArray.slice(12)
        
        // Decrypt the data
        val decryptedData = awaitCryptoOperation {
            crypto.subtle.decrypt(
                js("{}").apply {
                    name = "AES-GCM"
                    iv = iv
                },
                key,
                encryptedData
            )
        }
        
        return Uint8Array(decryptedData).toByteArray().toString(Charsets.UTF_8)
    }
    
    // Private IndexedDB methods
    
    private suspend fun storeInIndexedDB(key: String, value: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val db = database ?: run {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            val transaction = db.transaction(arrayOf(STORE_NAME), "readwrite")
            val store = transaction.objectStore(STORE_NAME)
            
            val request = store.put(value, key)
            
            request.onsuccess = {
                continuation.resume(true)
            }
            
            request.onerror = {
                logger.error("Failed to store in IndexedDB", request.error)
                continuation.resume(false)
            }
            
            continuation.invokeOnCancellation {
                transaction.abort()
            }
        }
    }
    
    private suspend fun retrieveFromIndexedDB(key: String): String? {
        return suspendCancellableCoroutine { continuation ->
            val db = database ?: run {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            val transaction = db.transaction(arrayOf(STORE_NAME), "readonly")
            val store = transaction.objectStore(STORE_NAME)
            
            val request = store.get(key)
            
            request.onsuccess = {
                val result = request.result
                continuation.resume(if (result != undefined) result as String else null)
            }
            
            request.onerror = {
                logger.error("Failed to retrieve from IndexedDB", request.error)
                continuation.resume(null)
            }
            
            continuation.invokeOnCancellation {
                transaction.abort()
            }
        }
    }
    
    private suspend fun removeFromIndexedDB(key: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val db = database ?: run {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            val transaction = db.transaction(arrayOf(STORE_NAME), "readwrite")
            val store = transaction.objectStore(STORE_NAME)
            
            val request = store.delete(key)
            
            request.onsuccess = {
                continuation.resume(true)
            }
            
            request.onerror = {
                logger.error("Failed to remove from IndexedDB", request.error)
                continuation.resume(false)
            }
            
            continuation.invokeOnCancellation {
                transaction.abort()
            }
        }
    }
    
    private suspend fun clearIndexedDB(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val db = database ?: run {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            val transaction = db.transaction(arrayOf(STORE_NAME), "readwrite")
            val store = transaction.objectStore(STORE_NAME)
            
            val request = store.clear()
            
            request.onsuccess = {
                continuation.resume(true)
            }
            
            request.onerror = {
                logger.error("Failed to clear IndexedDB", request.error)
                continuation.resume(false)
            }
            
            continuation.invokeOnCancellation {
                transaction.abort()
            }
        }
    }
    
    private suspend fun getAllKeysFromIndexedDB(): Set<String> {
        return suspendCancellableCoroutine { continuation ->
            val db = database ?: run {
                continuation.resume(emptySet())
                return@suspendCancellableCoroutine
            }
            
            val transaction = db.transaction(arrayOf(STORE_NAME), "readonly")
            val store = transaction.objectStore(STORE_NAME)
            
            val request = store.getAllKeys()
            
            request.onsuccess = {
                val keys = request.result
                val keySet = keys.map { it as String }.toSet()
                continuation.resume(keySet)
            }
            
            request.onerror = {
                logger.error("Failed to get all keys from IndexedDB", request.error)
                continuation.resume(emptySet())
            }
            
            continuation.invokeOnCancellation {
                transaction.abort()
            }
        }
    }
    
    // Private utility methods
    
    private suspend fun <T> awaitCryptoOperation(operation: () -> Promise<T>): T {
        return suspendCancellableCoroutine { continuation ->
            operation().then(
                { result -> continuation.resume(result) },
                { error -> continuation.resumeWithException(error) }
            )
        }
    }
    
    private fun arrayBufferToBase64(buffer: ArrayBuffer): String {
        val bytes = Uint8Array(buffer)
        val binary = bytes.map { it.toChar() }.joinToString("")
        return btoa(binary)
    }
    
    private fun base64ToArrayBuffer(base64: String): ArrayBuffer {
        val binary = atob(base64)
        val bytes = Uint8Array(binary.length)
        for (i in 0 until binary.length) {
            bytes[i] = binary[i].code.toUByte()
        }
        return bytes.buffer
    }
    
    private fun Uint8Array.toByteArray(): ByteArray {
        return ByteArray(length) { i -> this[i].toByte() }
    }
    
    private fun ByteArray.toUint8Array(): Uint8Array {
        return Uint8Array(size) { i -> this[i].toUByte() }
    }
    
    /**
     * Checks if the browser supports Web Crypto API.
     */
    fun isWebCryptoSupported(): Boolean {
        return try {
            crypto.subtle != undefined
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Checks if the browser supports IndexedDB.
     */
    fun isIndexedDBSupported(): Boolean {
        return try {
            indexedDB != undefined
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets information about the web storage implementation.
     */
    fun getWebStorageInfo(): WebStorageInfo {
        return try {
            WebStorageInfo(
                isWebCryptoSupported = isWebCryptoSupported(),
                isIndexedDBSupported = isIndexedDBSupported(),
                databaseName = DATABASE_NAME,
                databaseVersion = DATABASE_VERSION,
                storeName = STORE_NAME
            )
        } catch (e: Exception) {
            WebStorageInfo(
                isWebCryptoSupported = false,
                isIndexedDBSupported = false,
                databaseName = "Error",
                databaseVersion = 0,
                storeName = "Error"
            )
        }
    }
}

/**
 * Information about the web storage implementation.
 */
data class WebStorageInfo(
    val isWebCryptoSupported: Boolean,
    val isIndexedDBSupported: Boolean,
    val databaseName: String,
    val databaseVersion: Int,
    val storeName: String
)