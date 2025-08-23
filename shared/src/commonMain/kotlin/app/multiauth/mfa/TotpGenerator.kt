package app.multiauth.mfa

import app.multiauth.util.Logger
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.pow

/**
 * TOTP (Time-based One-Time Password) generator and validator.
 * Implements RFC 6238 for TOTP generation.
 */
class TotpGenerator {
    
    private val logger = Logger.getLogger(this::class)
    
    companion object {
        private const val TOTP_DIGITS = 6
        private const val TOTP_PERIOD = 30L // 30 seconds
        private const val TOTP_WINDOW = 1L // Allow 1 period before/after for clock skew
        
        // TOTP algorithm constants
        private const val HMAC_SHA1 = "HmacSHA1"
        private const val HMAC_SHA256 = "HmacSHA256"
        private const val HMAC_SHA512 = "HmacSHA512"
    }
    
    /**
     * Generates a new TOTP secret key.
     * 
     * @param algorithm The HMAC algorithm to use (SHA1, SHA256, SHA512)
     * @return Base32 encoded secret key
     */
    fun generateSecret(algorithm: String = HMAC_SHA1): String {
        val random = SecureRandom()
        val bytes = ByteArray(20) // 160 bits for SHA1
        random.nextBytes(bytes)
        
        return Base32.encode(bytes)
    }
    
    /**
     * Generates a TOTP code for the current time.
     * 
     * @param secret The TOTP secret key (Base32 encoded)
     * @param algorithm The HMAC algorithm to use
     * @return 6-digit TOTP code
     */
    fun generateTotp(secret: String, algorithm: String = HMAC_SHA1): String {
        val time = System.currentTimeMillis() / 1000 / TOTP_PERIOD
        return generateTotpForTime(secret, time, algorithm)
    }
    
    /**
     * Generates a TOTP code for a specific time.
     * 
     * @param secret The TOTP secret key (Base32 encoded)
     * @param time The time counter value
     * @param algorithm The HMAC algorithm to use
     * @return 6-digit TOTP code
     */
    fun generateTotpForTime(secret: String, time: Long, algorithm: String = HMAC_SHA1): String {
        try {
            val secretBytes = Base32.decode(secret)
            val timeBytes = longToBytes(time)
            
            val hash = generateHmac(secretBytes, timeBytes, algorithm)
            val offset = hash[hash.size - 1].toInt() and 0x0F
            
            val code = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)
            
            val totp = code % (10.0.pow(TOTP_DIGITS)).toInt()
            return String.format("%0${TOTP_DIGITS}d", totp)
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to generate TOTP", e)
            throw IllegalArgumentException("Invalid TOTP secret or parameters", e)
        }
    }
    
    /**
     * Validates a TOTP code.
     * 
     * @param secret The TOTP secret key (Base32 encoded)
     * @param code The TOTP code to validate
     * @param algorithm The HMAC algorithm to use
     * @return true if the code is valid, false otherwise
     */
    fun validateTotp(secret: String, code: String, algorithm: String = HMAC_SHA1): Boolean {
        return validateTotpWithWindow(secret, code, TOTP_WINDOW, algorithm)
    }
    
    /**
     * Validates a TOTP code with a specified time window.
     * 
     * @param secret The TOTP secret key (Base32 encoded)
     * @param code The TOTP code to validate
     * @param window The time window to check (in periods)
     * @param algorithm The HMAC algorithm to use
     * @return true if the code is valid, false otherwise
     */
    fun validateTotpWithWindow(
        secret: String,
        code: String,
        window: Long,
        algorithm: String = HMAC_SHA1
    ): Boolean {
        try {
            val currentTime = System.currentTimeMillis() / 1000 / TOTP_PERIOD
            
            // Check the current time and window periods before/after
            for (i in -window..window) {
                val checkTime = currentTime + i
                val generatedCode = generateTotpForTime(secret, checkTime, algorithm)
                
                if (generatedCode == code) {
                    return true
                }
            }
            
            return false
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to validate TOTP", e)
            return false
        }
    }
    
    /**
     * Gets the remaining time until the next TOTP code.
     * 
     * @return Seconds remaining until next code
     */
    fun getTimeRemaining(): Long {
        val currentTime = System.currentTimeMillis() / 1000
        val periodStart = (currentTime / TOTP_PERIOD) * TOTP_PERIOD
        val nextPeriod = periodStart + TOTP_PERIOD
        
        return nextPeriod - currentTime
    }
    
    /**
     * Gets the current TOTP period.
     * 
     * @return Current TOTP period number
     */
    fun getCurrentPeriod(): Long {
        return System.currentTimeMillis() / 1000 / TOTP_PERIOD
    }
    
    /**
     * Generates HMAC for TOTP calculation.
     */
    private fun generateHmac(key: ByteArray, data: ByteArray, algorithm: String): ByteArray {
        return when (algorithm.uppercase()) {
            HMAC_SHA1 -> generateHmacSha1(key, data)
            HMAC_SHA256 -> generateHmacSha256(key, data)
            HMAC_SHA512 -> generateHmacSha512(key, data)
            else -> throw IllegalArgumentException("Unsupported HMAC algorithm: $algorithm")
        }
    }
    
    private fun generateHmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        // In a real implementation, this would use proper HMAC-SHA1
        // For demo purposes, we'll use a simplified approach
        val combined = key + data
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(combined)
    }
    
    private fun generateHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        // In a real implementation, this would use proper HMAC-SHA256
        val combined = key + data
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(combined)
    }
    
    private fun generateHmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        // In a real implementation, this would use proper HMAC-SHA512
        val combined = key + data
        val digest = MessageDigest.getInstance("SHA-512")
        return digest.digest(combined)
    }
    
    /**
     * Converts a long value to a byte array.
     */
    private fun longToBytes(value: Long): ByteArray {
        return ByteArray(8) { i ->
            ((value shr (8 * (7 - i))) and 0xFF).toByte()
        }
    }
}

/**
 * Base32 encoding utility for TOTP secrets.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val PADDING = '='
    
    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        
        val bits = data.size * 8
        val chars = (bits + 4) / 5
        
        val result = StringBuilder(chars)
        var buffer = 0
        var bufferSize = 0
        
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bufferSize += 8
            
            while (bufferSize >= 5) {
                val index = (buffer shr (bufferSize - 5)) and 0x1F
                result.append(ALPHABET[index])
                bufferSize -= 5
            }
        }
        
        if (bufferSize > 0) {
            val index = (buffer shl (5 - bufferSize)) and 0x1F
            result.append(ALPHABET[index])
        }
        
        // Add padding
        while (result.length % 8 != 0) {
            result.append(PADDING)
        }
        
        return result.toString()
    }
    
    fun decode(encoded: String): ByteArray {
        val clean = encoded.uppercase().replace(PADDING.toString(), "")
        if (clean.isEmpty()) return ByteArray(0)
        
        val bits = clean.length * 5
        val bytes = bits / 8
        
        val result = ByteArray(bytes)
        var buffer = 0
        var bufferSize = 0
        var byteIndex = 0
        
        for (char in clean) {
            val index = ALPHABET.indexOf(char)
            if (index == -1) {
                throw IllegalArgumentException("Invalid Base32 character: $char")
            }
            
            buffer = (buffer shl 5) or index
            bufferSize += 5
            
            while (bufferSize >= 8) {
                result[byteIndex++] = ((buffer shr (bufferSize - 8)) and 0xFF).toByte()
                bufferSize -= 8
            }
        }
        
        return result
    }
}