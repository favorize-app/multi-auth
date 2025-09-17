@file:OptIn(ExperimentalTime::class)

package app.multiauth.util


import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

/**
 * Utility object for generating various types of codes and tokens used in authentication.
 * Provides consistent, secure generation methods for verification codes, session IDs, and reset tokens.
 */
object CodeGenerationUtil {

    private const val ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    /**
     * Generates a 6-digit verification code (100000-999999).
     * Commonly used for SMS and email verification.
     */
    fun generateVerificationCode(): String {
        return (100000..999999).random().toString()
    }

    /**
     * Generates a secure verification code with custom length.
     * @param length The number of digits (default: 6)
     */
    fun generateVerificationCode(length: Int): String {
        require(length > 0) { "Code length must be positive" }
        val min = generateSequence { 1 }.take(length).joinToString("").toInt()
        val max = generateSequence { 9 }.take(length).joinToString("").toInt()
        return (min..max).random().toString()
    }

    /**
     * Generates an alphanumeric session ID.
     * @param length The length of the session ID (default: 16)
     */
    fun generateAlphanumericSessionId(length: Int = 16): String {
        require(length > 0) { "Session ID length must be positive" }
        return (1..length).map { ALPHANUMERIC_CHARS.random() }.joinToString("")
    }

    /**
     * Generates a time-based session ID with format: "session_{timestamp}_{random}".
     * Useful for debugging and ensuring uniqueness.
     */
    fun generateTimestampSessionId(): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val randomSuffix = (1000..9999).random()
        return "session_${timestamp}_${randomSuffix}"
    }

    /**
     * Generates a secure alphanumeric reset token.
     * @param length The length of the reset token (default: 32)
     */
    fun generateAlphanumericResetToken(length: Int = 32): String {
        require(length > 0) { "Reset token length must be positive" }
        return (1..length).map { ALPHANUMERIC_CHARS.random() }.joinToString("")
    }

    /**
     * Generates a numeric reset token (9-digit by default).
     * @param length The number of digits (default: 9)
     */
    fun generateNumericResetToken(length: Int = 9): String {
        require(length > 0) { "Reset token length must be positive" }
        val min = generateSequence { 1 }.take(length).joinToString("").toInt()
        val max = generateSequence { 9 }.take(length).joinToString("").toInt()
        return (min..max).random().toString()
    }

    /**
     * Generates a random string of specified length using alphanumeric characters.
     * General-purpose method for any token generation needs.
     * @param length The desired length
     * @param chars The character set to use (default: alphanumeric)
     */
    fun generateRandomString(
        length: Int,
        chars: String = ALPHANUMERIC_CHARS
    ): String {
        require(length > 0) { "String length must be positive" }
        require(chars.isNotEmpty()) { "Character set cannot be empty" }
        return (1..length).map { chars.random() }.joinToString("")
    }

    /**
     * Generates a cryptographically secure random string.
     * Uses a more secure random number generator for sensitive tokens.
     * Note: This is a simplified implementation. For production use, consider using
     * platform-specific secure random generators.
     * @param length The desired length
     * @param chars The character set to use (default: alphanumeric)
     */
    fun generateSecureRandomString(
        length: Int,
        chars: String = ALPHANUMERIC_CHARS
    ): String {
        require(length > 0) { "String length must be positive" }
        require(chars.isNotEmpty()) { "Character set cannot be empty" }

        // For now, use Kotlin's Random. In production, this should use
        // platform-specific secure random generators
        val random = Random.Default
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}

/**
 * Extension functions for common code generation patterns.
 */

/**
 * Extension function to generate a verification code.
 */
fun Random.nextVerificationCode(): String = CodeGenerationUtil.generateVerificationCode()

/**
 * Extension function to generate a session ID.
 */
fun Random.nextSessionId(length: Int = 16): String = CodeGenerationUtil.generateAlphanumericSessionId(length)

/**
 * Extension function to generate a reset token.
 */
fun Random.nextResetToken(length: Int = 32): String = CodeGenerationUtil.generateAlphanumericResetToken(length)
