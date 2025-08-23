package app.multiauth.services

import app.multiauth.util.Logger
import kotlinx.coroutines.flow.Flow

/**
 * Service for sending emails in the Multi-Auth system.
 * Handles verification emails, password resets, and notifications.
 */
interface EmailService {
    
    /**
     * Initializes the email service with configuration.
     * 
     * @param config Email service configuration
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(config: EmailConfig): Boolean
    
    /**
     * Sends a verification email to a user.
     * 
     * @param email The recipient email address
     * @param verificationCode The verification code
     * @param userId The user ID for tracking
     * @return true if email sent successfully, false otherwise
     */
    suspend fun sendVerificationEmail(
        email: String,
        verificationCode: String,
        userId: String
    ): Boolean
    
    /**
     * Sends a password reset email to a user.
     * 
     * @param email The recipient email address
     * @param resetToken The password reset token
     * @param userId The user ID for tracking
     * @return true if email sent successfully, false otherwise
     */
    suspend fun sendPasswordResetEmail(
        email: String,
        resetToken: String,
        userId: String
    ): Boolean
    
    /**
     * Sends a welcome email to a new user.
     * 
     * @param email The recipient email address
     * @param username The username
     * @param userId The user ID for tracking
     * @return true if email sent successfully, false otherwise
     */
    suspend fun sendWelcomeEmail(
        email: String,
        username: String,
        userId: String
    ): Boolean
    
    /**
     * Sends a security alert email.
     * 
     * @param email The recipient email address
     * @param alertType The type of security alert
     * @param details Additional details about the alert
     * @param userId The user ID for tracking
     * @return true if email sent successfully, false otherwise
     */
    suspend fun sendSecurityAlertEmail(
        email: String,
        alertType: SecurityAlertType,
        details: String,
        userId: String
    ): Boolean
    
    /**
     * Sends a custom email with HTML content.
     * 
     * @param email The recipient email address
     * @param subject The email subject
     * @param htmlContent The HTML content of the email
     * @param userId The user ID for tracking
     * @return true if email sent successfully, false otherwise
     */
    suspend fun sendCustomEmail(
        email: String,
        subject: String,
        htmlContent: String,
        userId: String
    ): Boolean
    
    /**
     * Gets the delivery status of an email.
     * 
     * @param emailId The email ID to check
     * @return EmailDeliveryStatus for the email
     */
    suspend fun getEmailDeliveryStatus(emailId: String): EmailDeliveryStatus?
    
    /**
     * Gets email statistics and metrics.
     * 
     * @return EmailStats with delivery statistics
     */
    suspend fun getEmailStats(): EmailStats
    
    /**
     * Checks if the email service is ready to send emails.
     * 
     * @return true if ready, false otherwise
     */
    suspend fun isReady(): Boolean
    
    /**
     * Gets information about the email service configuration.
     * 
     * @return EmailServiceInfo with service details
     */
    suspend fun getServiceInfo(): EmailServiceInfo
}

/**
 * Email service configuration.
 */
data class EmailConfig(
    val provider: EmailProvider,
    val apiKey: String,
    val fromEmail: String,
    val fromName: String,
    val replyToEmail: String? = null,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 5000,
    val timeoutMs: Long = 30000,
    val enableTracking: Boolean = true,
    val enableAnalytics: Boolean = true,
    val templateDirectory: String? = null,
    val defaultLanguage: String = "en"
)

/**
 * Supported email service providers.
 */
enum class EmailProvider(val displayName: String, val supportsTemplates: Boolean) {
    SENDGRID("SendGrid", true),
    MAILGUN("Mailgun", true),
    AMAZON_SES("Amazon SES", true),
    SMTP("SMTP", false),
    RESEND("Resend", true),
    POSTMARK("Postmark", true),
    MAILCHIMP("Mailchimp", true),
    CUSTOM("Custom", false)
}

/**
 * Types of security alerts that can be sent via email.
 */
enum class SecurityAlertType(val displayName: String, val priority: AlertPriority) {
    LOGIN_ATTEMPT("Login Attempt", AlertPriority.LOW),
    SUSPICIOUS_ACTIVITY("Suspicious Activity", AlertPriority.MEDIUM),
    ACCOUNT_LOCKOUT("Account Lockout", AlertPriority.HIGH),
    PASSWORD_CHANGE("Password Change", AlertPriority.MEDIUM),
    DEVICE_ADDED("New Device Added", AlertPriority.MEDIUM),
    LOCATION_CHANGE("Location Change", AlertPriority.MEDIUM),
    MULTI_FACTOR_ENABLED("Multi-Factor Authentication Enabled", AlertPriority.LOW),
    MULTI_FACTOR_DISABLED("Multi-Factor Authentication Disabled", AlertPriority.HIGH),
    OAUTH_LINKED("OAuth Account Linked", AlertPriority.LOW),
    OAUTH_UNLINKED("OAuth Account Unlinked", AlertPriority.MEDIUM)
}

/**
 * Alert priority levels.
 */
enum class AlertPriority(val displayName: String, val color: String) {
    LOW("Low", "#28a745"),
    MEDIUM("Medium", "#ffc107"),
    HIGH("High", "#dc3545"),
    CRITICAL("Critical", "#721c24")
}

/**
 * Email delivery status information.
 */
data class EmailDeliveryStatus(
    val emailId: String,
    val status: DeliveryStatus,
    val sentAt: Long,
    val deliveredAt: Long?,
    val openedAt: Long?,
    val clickedAt: Long?,
    val bouncedAt: Long?,
    val bounceReason: String?,
    val recipientEmail: String,
    val subject: String,
    val providerMessageId: String?
)

/**
 * Email delivery status values.
 */
enum class DeliveryStatus(val displayName: String) {
    SENT("Sent"),
    DELIVERED("Delivered"),
    OPENED("Opened"),
    CLICKED("Clicked"),
    BOUNCED("Bounced"),
    FAILED("Failed"),
    PENDING("Pending")
}

/**
 * Email service statistics and metrics.
 */
data class EmailStats(
    val totalSent: Long,
    val totalDelivered: Long,
    val totalOpened: Long,
    val totalClicked: Long,
    val totalBounced: Long,
    val totalFailed: Long,
    val deliveryRate: Double,
    val openRate: Double,
    val clickRate: Double,
    val bounceRate: Double,
    val lastSentAt: Long?,
    val lastDeliveredAt: Long?,
    val averageDeliveryTimeMs: Long?
)

/**
 * Information about the email service configuration and status.
 */
data class EmailServiceInfo(
    val provider: EmailProvider,
    val isInitialized: Boolean,
    val fromEmail: String,
    val fromName: String,
    val supportsTemplates: Boolean,
    val supportsTracking: Boolean,
    val supportsAnalytics: Boolean,
    val maxRetries: Int,
    val timeoutMs: Long,
    val lastTestAt: Long?
)

/**
 * Email template for different types of emails.
 */
data class EmailTemplate(
    val name: String,
    val subject: String,
    val htmlContent: String,
    val textContent: String?,
    val variables: List<String>,
    val language: String = "en"
)

/**
 * Email template variables for personalization.
 */
data class EmailTemplateVariables(
    val userId: String,
    val username: String?,
    val email: String,
    val verificationCode: String? = null,
    val resetToken: String? = null,
    val alertType: SecurityAlertType? = null,
    val alertDetails: String? = null,
    val loginLocation: String? = null,
    val deviceInfo: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val supportEmail: String? = null,
    val appName: String = "Multi-Auth",
    val appUrl: String? = null
)

/**
 * Email sending result with detailed information.
 */
sealed class EmailSendResult {
    data class Success(
        val emailId: String,
        val providerMessageId: String?,
        val sentAt: Long
    ) : EmailSendResult()
    
    data class Failure(
        val error: String,
        val errorCode: String?,
        val retryable: Boolean,
        val attemptedAt: Long
    ) : EmailSendResult()
    
    data class RateLimited(
        val retryAfterMs: Long,
        val attemptedAt: Long
    ) : EmailSendResult()
}