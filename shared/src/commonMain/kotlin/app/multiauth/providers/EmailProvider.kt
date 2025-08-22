package app.multiauth.providers

import app.multiauth.models.AuthResult

/**
 * Interface for email-based authentication providers.
 * This allows for pluggable email services (Firebase, SendGrid, etc.).
 */
interface EmailProvider {
    
    /**
     * Sends a verification email to the specified email address.
     * @param email The email address to send verification to
     * @return Result indicating success or failure
     */
    suspend fun sendVerificationEmail(email: String): AuthResult<Unit>
    
    /**
     * Verifies an email verification code.
     * @param email The email address being verified
     * @param code The verification code
     * @return Result indicating success or failure
     */
    suspend fun verifyEmailCode(email: String, code: String): AuthResult<Unit>
    
    /**
     * Sends a password reset email.
     * @param email The email address to send password reset to
     * @return Result indicating success or failure
     */
    suspend fun sendPasswordReset(email: String): AuthResult<Unit>
    
    /**
     * Sends a welcome email to a newly registered user.
     * @param email The email address to send welcome email to
     * @param displayName Optional display name of the user
     * @return Result indicating success or failure
     */
    suspend fun sendWelcomeEmail(email: String, displayName: String? = null): AuthResult<Unit>
    
    /**
     * Sends a security alert email (e.g., for suspicious login attempts).
     * @param email The email address to send alert to
     * @param alertType The type of security alert
     * @param details Additional details about the alert
     * @return Result indicating success or failure
     */
    suspend fun sendSecurityAlert(
        email: String, 
        alertType: SecurityAlertType, 
        details: Map<String, String> = emptyMap()
    ): AuthResult<Unit>
    
    /**
     * Checks if an email address is valid and deliverable.
     * @param email The email address to validate
     * @return Result indicating if the email is valid
     */
    suspend fun validateEmail(email: String): AuthResult<Boolean>
    
    /**
     * Gets the provider's configuration and capabilities.
     * @return Provider configuration information
     */
    fun getProviderInfo(): EmailProviderInfo
}

/**
 * Types of security alerts that can be sent via email.
 */
enum class SecurityAlertType {
    SUSPICIOUS_LOGIN,
    PASSWORD_CHANGED,
    NEW_DEVICE_LOGIN,
    ACCOUNT_LOCKED,
    UNUSUAL_ACTIVITY
}

/**
 * Information about an email provider's capabilities and configuration.
 */
data class EmailProviderInfo(
    val name: String,
    val version: String,
    val supportsVerification: Boolean = true,
    val supportsPasswordReset: Boolean = true,
    val supportsWelcomeEmails: Boolean = true,
    val supportsSecurityAlerts: Boolean = true,
    val maxEmailsPerHour: Int? = null,
    val maxEmailsPerDay: Int? = null,
    val features: List<String> = emptyList()
)

/**
 * Configuration for email providers.
 */
data class EmailProviderConfig(
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val fromEmail: String? = null,
    val fromName: String? = null,
    val replyToEmail: String? = null,
    val webhookUrl: String? = null,
    val maxRetries: Int = 3,
    val timeoutSeconds: Int = 30,
    val customHeaders: Map<String, String> = emptyMap()
)

/**
 * Result of an email operation.
 */
sealed class EmailResult {
    data class Success(val messageId: String? = null) : EmailResult()
    data class Failure(
        val error: String,
        val errorCode: String? = null,
        val retryable: Boolean = false
    ) : EmailResult()
}

/**
 * Email template for different types of emails.
 */
data class EmailTemplate(
    val subject: String,
    val htmlBody: String,
    val textBody: String,
    val variables: Map<String, String> = emptyMap()
)

/**
 * Rate limiting information for email operations.
 */
data class RateLimitInfo(
    val remaining: Int,
    val resetTime: Long,
    val limit: Int
)