package app.multiauth.security

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.days

/**
 * JWT token manager for creating and validating authentication tokens.
 * Uses HMAC-SHA256 for signing tokens.
 */
class JwtTokenManager(
    private val secretKey: String = generateDefaultSecret()
) {
    
    companion object {
        private const val ALGORITHM = "HS256"
        private const val TOKEN_TYPE = "JWT"
        
        /**
         * Generates a default secret key for development.
         * In production, this should be loaded from secure configuration.
         */
        private fun generateDefaultSecret(): String {
            return "multi_auth_jwt_secret_key_change_in_production_${Clock.System.now().toEpochMilliseconds()}"
        }
    }
    
    /**
     * Creates an access token for a user.
     */
    fun createAccessToken(
        userId: String,
        email: String? = null,
        roles: List<String> = emptyList(),
        expirationDuration: Duration = 30.minutes
    ): String {
        val now = Clock.System.now()
        val expiration = now + expirationDuration
        
        val payload = JwtPayload(
            sub = userId,
            email = email,
            roles = roles,
            iat = now.epochSeconds,
            exp = expiration.epochSeconds,
            iss = "multi-auth-system",
            tokenType = "access"
        )
        
        return createToken(payload)
    }
    
    /**
     * Creates a refresh token for a user.
     */
    fun createRefreshToken(
        userId: String,
        expirationDuration: Duration = 7.days
    ): String {
        val now = Clock.System.now()
        val expiration = now + expirationDuration
        
        val payload = JwtPayload(
            sub = userId,
            iat = now.epochSeconds,
            exp = expiration.epochSeconds,
            iss = "multi-auth-system",
            tokenType = "refresh"
        )
        
        return createToken(payload)
    }
    
    /**
     * Validates and decodes a JWT token.
     */
    fun validateToken(token: String): TokenValidationResult {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                return TokenValidationResult.Invalid("Invalid token format")
            }
            
            val header = parts[0]
            val payload = parts[1]
            val signature = parts[2]
            
            // Verify signature
            val expectedSignature = createSignature("$header.$payload")
            if (!constantTimeEquals(signature, expectedSignature)) {
                return TokenValidationResult.Invalid("Invalid signature")
            }
            
            // Decode payload
            val decodedPayload = base64UrlDecode(payload)
            val jwtPayload = Json.decodeFromString<JwtPayload>(decodedPayload)
            
            // Check expiration
            val now = Clock.System.now().epochSeconds
            if (jwtPayload.exp < now) {
                return TokenValidationResult.Expired
            }
            
            TokenValidationResult.Valid(jwtPayload)
            
        } catch (e: Exception) {
            TokenValidationResult.Invalid("Token parsing failed: ${e.message}")
        }
    }
    
    /**
     * Extracts user ID from a valid token without full validation.
     * Use this only for non-security-critical operations.
     */
    fun extractUserId(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            
            val payloadJson = base64UrlDecode(parts[1])
            val payload = Json.decodeFromString<JwtPayload>(payloadJson)
            payload.sub
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Creates a complete JWT token from payload.
     */
    private fun createToken(payload: JwtPayload): String {
        val header = JwtHeader(alg = ALGORITHM, typ = TOKEN_TYPE)
        
        val encodedHeader = base64UrlEncode(Json.encodeToString(header))
        val encodedPayload = base64UrlEncode(Json.encodeToString(payload))
        val signature = createSignature("$encodedHeader.$encodedPayload")
        
        return "$encodedHeader.$encodedPayload.$signature"
    }
    
    /**
     * Creates HMAC-SHA256 signature for token data.
     */
    private fun createSignature(data: String): String {
        val keyBytes = secretKey.encodeToByteArray()
        val dataBytes = data.encodeToByteArray()
        val hmac = hmacSha256(keyBytes, dataBytes)
        return base64UrlEncode(hmac)
    }
    
    /**
     * HMAC-SHA256 implementation.
     */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val blockSize = 64 // SHA256 block size
        
        // Prepare key
        val actualKey = when {
            key.size > blockSize -> {
                val sha256 = org.kotlincrypto.hash.sha2.SHA256()
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
        val sha256Inner = org.kotlincrypto.hash.sha2.SHA256()
        sha256Inner.update(innerPadded)
        sha256Inner.update(data)
        val innerHash = sha256Inner.digest()
        
        // Outer hash: SHA256(key XOR opad || inner_hash)
        val sha256Outer = org.kotlincrypto.hash.sha2.SHA256()
        sha256Outer.update(outerPadded)
        sha256Outer.update(innerHash)
        return sha256Outer.digest()
    }
    
    /**
     * Base64URL encoding (RFC 4648).
     */
    private fun base64UrlEncode(data: String): String = base64UrlEncode(data.encodeToByteArray())
    
    private fun base64UrlEncode(data: ByteArray): String {
        val base64 = data.encodeBase64()
        return base64.replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
    }
    
    /**
     * Base64URL decoding.
     */
    private fun base64UrlDecode(data: String): String {
        val padded = when (data.length % 4) {
            2 -> data + "=="
            3 -> data + "="
            else -> data
        }
        val base64 = padded.replace("-", "+").replace("_", "/")
        return base64.decodeBase64().decodeToString()
    }
    
    /**
     * Constant-time string comparison.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}

/**
 * JWT header structure.
 */
@Serializable
private data class JwtHeader(
    val alg: String,
    val typ: String
)

/**
 * JWT payload structure.
 */
@Serializable
data class JwtPayload(
    val sub: String, // Subject (user ID)
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val iat: Long, // Issued at
    val exp: Long, // Expiration time
    val iss: String, // Issuer
    val tokenType: String // "access" or "refresh"
)

/**
 * Result of token validation.
 */
sealed class TokenValidationResult {
    data class Valid(val payload: JwtPayload) : TokenValidationResult()
    data object Expired : TokenValidationResult()
    data class Invalid(val reason: String) : TokenValidationResult()
}

/**
 * Simple Base64 encoding for multiplatform compatibility.
 */
private fun ByteArray.encodeBase64(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder()
    
    var i = 0
    while (i < size) {
        val b1 = this[i].toInt() and 0xFF
        val b2 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
        val b3 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0
        
        val bitmap = (b1 shl 16) or (b2 shl 8) or b3
        
        result.append(chars[(bitmap shr 18) and 0x3F])
        result.append(chars[(bitmap shr 12) and 0x3F])
        result.append(if (i + 1 < size) chars[(bitmap shr 6) and 0x3F] else '=')
        result.append(if (i + 2 < size) chars[bitmap and 0x3F] else '=')
        
        i += 3
    }
    
    return result.toString()
}

/**
 * Simple Base64 decoding for multiplatform compatibility.
 */
private fun String.decodeBase64(): ByteArray {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val charMap = chars.mapIndexed { index, c -> c to index }.toMap()
    
    val result = mutableListOf<Byte>()
    var i = 0
    
    while (i < length) {
        val c1 = charMap[this[i]] ?: 0
        val c2 = charMap[this[i + 1]] ?: 0
        val c3 = if (this[i + 2] != '=') charMap[this[i + 2]] ?: 0 else 0
        val c4 = if (this[i + 3] != '=') charMap[this[i + 3]] ?: 0 else 0
        
        val bitmap = (c1 shl 18) or (c2 shl 12) or (c3 shl 6) or c4
        
        result.add(((bitmap shr 16) and 0xFF).toByte())
        if (this[i + 2] != '=') result.add(((bitmap shr 8) and 0xFF).toByte())
        if (this[i + 3] != '=') result.add((bitmap and 0xFF).toByte())
        
        i += 4
    }
    
    return result.toByteArray()
}