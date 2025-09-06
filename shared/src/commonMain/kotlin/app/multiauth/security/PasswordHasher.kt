package app.multiauth.security

import org.kotlincrypto.hash.sha2.SHA256
import kotlin.random.Random

/**
 * Secure password hashing utility using PBKDF2-SHA256.
 * Provides secure password hashing and verification for authentication.
 */
object PasswordHasher {
    
    private const val SALT_LENGTH = 16
    private const val HASH_LENGTH = 32
    private const val ITERATIONS = 100000
    
    /**
     * Hashes a password with a random salt using PBKDF2-SHA256.
     * 
     * @param password The plain text password to hash
     * @return HashedPassword containing the hash and salt
     */
    fun hashPassword(password: String): HashedPassword {
        val salt = generateSalt()
        val hash = pbkdf2(password, salt, ITERATIONS, HASH_LENGTH)
        return HashedPassword(
            hash = hash.toHexString(),
            salt = salt.toHexString(),
            iterations = ITERATIONS
        )
    }
    
    /**
     * Verifies a password against a stored hash.
     * 
     * @param password The plain text password to verify
     * @param hashedPassword The stored hashed password
     * @return true if password matches, false otherwise
     */
    fun verifyPassword(password: String, hashedPassword: HashedPassword): Boolean {
        val salt = hashedPassword.salt.hexToByteArray()
        val expectedHash = pbkdf2(password, salt, hashedPassword.iterations, HASH_LENGTH)
        val actualHash = hashedPassword.hash.hexToByteArray()
        
        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(expectedHash, actualHash)
    }
    
    /**
     * Generates a cryptographically secure random salt.
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        Random.nextBytes(salt)
        return salt
    }
    
    /**
     * PBKDF2 implementation using SHA256.
     * 
     * @param password The password to hash
     * @param salt The salt bytes
     * @param iterations Number of iterations
     * @param keyLength Length of derived key in bytes
     * @return The derived key
     */
    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val passwordBytes = password.encodeToByteArray()
        val result = ByteArray(keyLength)
        val blockCount = (keyLength + 31) / 32 // SHA256 produces 32 bytes
        
        for (i in 1..blockCount) {
            val block = pbkdf2Block(passwordBytes, salt, iterations, i)
            val offset = (i - 1) * 32
            val length = minOf(32, keyLength - offset)
            block.copyInto(result, offset, 0, length)
        }
        
        return result
    }
    
    /**
     * Computes a single block for PBKDF2.
     */
    private fun pbkdf2Block(password: ByteArray, salt: ByteArray, iterations: Int, blockIndex: Int): ByteArray {
        // Create initial block: salt + block index (big endian)
        val initialBlock = salt + byteArrayOf(
            (blockIndex shr 24).toByte(),
            (blockIndex shr 16).toByte(),
            (blockIndex shr 8).toByte(),
            blockIndex.toByte()
        )
        
        // First iteration: HMAC-SHA256(password, salt || block_index)
        var u = hmacSha256(password, initialBlock)
        val result = u.copyOf()
        
        // Remaining iterations
        for (i in 2..iterations) {
            u = hmacSha256(password, u)
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        
        return result
    }
    
    /**
     * HMAC-SHA256 implementation.
     */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val blockSize = 64 // SHA256 block size
        val outputSize = 32 // SHA256 output size
        
        // Prepare key
        val actualKey = when {
            key.size > blockSize -> {
                val sha256 = SHA256()
                sha256.update(key)
                sha256.digest()
            }
            key.size < blockSize -> key + ByteArray(blockSize - key.size)
            else -> key
        }
        
        // Create inner and outer padded keys
        val innerPadded = ByteArray(blockSize)
        val outerPadded = ByteArray(blockSize)
        
        for (i in 0 until blockSize) {
            innerPadded[i] = (actualKey[i].toInt() xor 0x36).toByte()
            outerPadded[i] = (actualKey[i].toInt() xor 0x5C).toByte()
        }
        
        // Inner hash: SHA256(key XOR ipad || data)
        val sha256Inner = SHA256()
        sha256Inner.update(innerPadded)
        sha256Inner.update(data)
        val innerHash = sha256Inner.digest()
        
        // Outer hash: SHA256(key XOR opad || inner_hash)
        val sha256Outer = SHA256()
        sha256Outer.update(outerPadded)
        sha256Outer.update(innerHash)
        return sha256Outer.digest()
    }
    
    /**
     * Constant-time byte array comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
    
    /**
     * Converts byte array to hex string.
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
    
    /**
     * Converts hex string to byte array.
     */
    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/**
 * Represents a hashed password with its salt and parameters.
 */
data class HashedPassword(
    val hash: String,
    val salt: String,
    val iterations: Int
)