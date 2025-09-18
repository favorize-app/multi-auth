package app.multiauth.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Utility object for Base64 encoding and decoding operations.
 * Uses Kotlin's native Base64 implementation for consistent multiplatform support.
 */
@OptIn(ExperimentalEncodingApi::class)
object Base64Util {
    
    /**
     * Encodes a byte array to Base64 string.
     */
    fun encode(data: ByteArray): String {
        return Base64.encode(data)
    }
    
    /**
     * Encodes a string to Base64 string.
     */
    fun encode(data: String): String {
        return Base64.encode(data.encodeToByteArray())
    }
    
    /**
     * Decodes a Base64 string to byte array.
     */
    fun decode(data: String): ByteArray {
        return Base64.decode(data)
    }
    
    /**
     * Decodes a Base64 string to string.
     */
    fun decodeToString(data: String): String {
        return Base64.decode(data).decodeToString()
    }
    
    /**
     * Encodes Basic Authentication credentials to Base64.
     * @param username The username
     * @param password The password
     * @return Base64 encoded "username:password" string
     */
    fun encodeBasicAuth(username: String, password: String): String {
        val credentials = "$username:$password"
        return encode(credentials)
    }
    
    /**
     * Encodes data to Base64URL format (RFC 4648).
     * Base64URL replaces '+' with '-', '/' with '_', and removes padding '='.
     */
    fun encodeBase64Url(data: ByteArray): String {
        val base64 = encode(data)
        return base64.replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
    }
    
    /**
     * Encodes string to Base64URL format (RFC 4648).
     */
    fun encodeBase64Url(data: String): String {
        return encodeBase64Url(data.encodeToByteArray())
    }
    
    /**
     * Decodes Base64URL string to byte array.
     */
    fun decodeBase64Url(data: String): ByteArray {
        val padded = when (data.length % 4) {
            2 -> data + "=="
            3 -> data + "="
            else -> data
        }
        val base64 = padded.replace("-", "+").replace("_", "/")
        return decode(base64)
    }
    
    /**
     * Decodes Base64URL string to string.
     */
    fun decodeBase64UrlToString(data: String): String {
        return decodeBase64Url(data).decodeToString()
    }
}

/**
 * Extension function for ByteArray to encode to Base64 string.
 */
@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.encodeBase64(): String = Base64Util.encode(this)

/**
 * Extension function for String to encode to Base64 string.
 */
fun String.encodeBase64(): String = Base64Util.encode(this)

/**
 * Extension function for String to decode from Base64 to ByteArray.
 */
@OptIn(ExperimentalEncodingApi::class)
fun String.decodeBase64(): ByteArray = Base64Util.decode(this)

/**
 * Extension function for String to decode from Base64 to String.
 */
fun String.decodeBase64ToString(): String = Base64Util.decodeToString(this)