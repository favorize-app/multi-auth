package app.multiauth.providers

import app.multiauth.models.AuthResult

/**
 * Interface for SMS-based authentication providers.
 * This allows for pluggable SMS services (Twilio, Firebase, etc.).
 */
interface SmsProvider {
    
    /**
     * Sends a verification code via SMS to the specified phone number.
     * @param phoneNumber The phone number to send verification to (in E.164 format)
     * @return Result containing the verification session ID
     */
    suspend fun sendVerificationCode(phoneNumber: String): AuthResult<String>
    
    /**
     * Verifies an SMS verification code.
     * @param phoneNumber The phone number being verified
     * @param code The verification code
     * @param sessionId The verification session ID
     * @return Result indicating success or failure
     */
    suspend fun verifySmsCode(phoneNumber: String, code: String, sessionId: String): AuthResult<Unit>
    
    /**
     * Sends a security alert SMS (e.g., for suspicious login attempts).
     * @param phoneNumber The phone number to send alert to
     * @param alertType The type of security alert
     * @param details Additional details about the alert
     * @return Result indicating success or failure
     */
    suspend fun sendSecurityAlert(
        phoneNumber: String,
        alertType: SmsSecurityAlertType,
        details: Map<String, String> = emptyMap()
    ): AuthResult<Unit>
    
    /**
     * Validates a phone number format and checks if it's deliverable.
     * @param phoneNumber The phone number to validate
     * @return Result indicating if the phone number is valid
     */
    suspend fun validatePhoneNumber(phoneNumber: String): AuthResult<Boolean>
    
    /**
     * Gets the provider's configuration and capabilities.
     * @return Provider configuration information
     */
    fun getProviderInfo(): SmsProviderInfo
    
    /**
     * Checks the current rate limiting status for a phone number.
     * @param phoneNumber The phone number to check
     * @return Rate limiting information
     */
    suspend fun getRateLimitInfo(phoneNumber: String): AuthResult<RateLimitInfo>
    
    /**
     * Resends a verification code if allowed by rate limiting.
     * @param phoneNumber The phone number to resend to
     * @param sessionId The existing session ID
     * @return Result indicating success or failure
     */
    suspend fun resendVerificationCode(phoneNumber: String, sessionId: String): AuthResult<String>
}

/**
 * Types of security alerts that can be sent via SMS.
 */
enum class SmsSecurityAlertType {
    SUSPICIOUS_LOGIN,
    NEW_DEVICE_LOGIN,
    ACCOUNT_LOCKED,
    UNUSUAL_ACTIVITY,
    PASSWORD_CHANGED
}

/**
 * Information about an SMS provider's capabilities and configuration.
 */
data class SmsProviderInfo(
    val name: String,
    val version: String,
    val supportsVerification: Boolean = true,
    val supportsSecurityAlerts: Boolean = true,
    val supportsInternationalNumbers: Boolean = true,
    val maxSmsPerHour: Int? = null,
    val maxSmsPerDay: Int? = null,
    val supportedCountries: List<String> = emptyList(),
    val features: List<String> = emptyList()
)

/**
 * Configuration for SMS providers.
 */
data class SmsProviderConfig(
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val accountSid: String? = null,
    val authToken: String? = null,
    val fromNumber: String? = null,
    val webhookUrl: String? = null,
    val maxRetries: Int = 3,
    val timeoutSeconds: Int = 30,
    val customHeaders: Map<String, String> = emptyMap()
)

/**
 * Result of an SMS operation.
 */
sealed class SmsResult {
    data class Success(
        val messageId: String? = null,
        val sessionId: String? = null
    ) : SmsResult()
    
    data class Failure(
        val error: String,
        val errorCode: String? = null,
        val retryable: Boolean = false
    ) : SmsResult()
}

/**
 * SMS template for different types of messages.
 */
data class SmsTemplate(
    val message: String,
    val variables: Map<String, String> = emptyMap(),
    val maxLength: Int = 160
)

/**
 * Rate limiting information for SMS operations.
 */
data class SmsRateLimitInfo(
    val remaining: Int,
    val resetTime: Long,
    val limit: Int,
    val cooldownPeriod: Long? = null
)

/**
 * Verification session information.
 */
data class VerificationSession(
    val sessionId: String,
    val phoneNumber: String,
    val expiresAt: Long,
    val attemptsRemaining: Int,
    val maxAttempts: Int = 3
)