package app.multiauth.services

import kotlinx.datetime.Clock
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
     * Sends an email verification email to a user's address.
     *
     * This function constructs and dispatches an email containing a verification code
     * required to confirm the user's email address. It can use a predefined template
     * or generate a default email if no template is provided.
     *
     * @param to The recipient's email address.
     * @param verificationCode The unique code for the user to verify their email.
     * @param userId The ID of the user for tracking and logging purposes.
     * @param template An optional [EmailTemplate] to use for the email body and subject.
     *                 If null, a default verification email format will be used.
     * @param variables Optional [EmailTemplateVariables] to personalize the template.
     *                  These variables will be substituted into the template content.
     * @return An [EmailSendResult] indicating the outcome of the send operation,
     *         which can be [EmailSendResult.Success], [EmailSendResult.Failure], or
     *         [EmailSendResult.RateLimited].
     */
    suspend fun sendVerificationEmail(
        to: String,
        verificationCode: String,
        userId: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): EmailSendResult
    
    /**
     * Sends a password reset email to a user, optionally using a template.
     * This function can be used to send a standard password reset link or a more complex,
     * templated email with additional variables for personalization.
     *
     * @param email The recipient's email address.
     * @param resetToken The unique token generated for the password reset request.
     * @param userId The ID of the user requesting the reset, for logging and tracking.
     * @param template An optional [EmailTemplate] to use for the email body and subject.
     *                 If null, a default, non-templated email will be sent.
     * @param variables Optional [EmailTemplateVariables] for personalizing the template.
     *                  These are only used if a `template` is also provided.
     * @return An [EmailSendResult] indicating the outcome of the send operation,
     *         which can be [EmailSendResult.Success], [EmailSendResult.Failure], or [EmailSendResult.RateLimited].
     */
    suspend fun sendPasswordResetEmail(
        to: String,
        resetToken: String,
        userId: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): EmailSendResult
    
    /**
     * Sends a welcome email to a newly registered user.
     *
     * This function is typically called after a user successfully completes the registration process.
     * It sends a friendly welcome message, which can include helpful links or next steps for the user.
     *
     * @param email The recipient's email address.
     * @param username The user's chosen name or username to personalize the email.
     * @param userId A unique identifier for the user, used for logging and tracking purposes.
     * @return `true` if the email was successfully dispatched, `false` otherwise.
     */
    suspend fun sendWelcomeEmail(
        to: String,
        displayName: String,
        userId: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): EmailSendResult
    
    /**
     * Dispatches a security alert email to a user's registered address.
     * This is used to notify the user about important, security-sensitive events
     * related to their account, such as a password change or a login from a new device.
     *
     * @param email The recipient's email address.
     * @param alertType The specific type of security alert, defined by the [SecurityAlertType] enum.
     * @param details A string containing specific, contextual information about the alert (e.g., IP address, location).
     * @param userId The unique identifier of the user account for logging and tracking purposes.
     * @return `true` if the email was successfully dispatched, `false` otherwise.
     */
    suspend fun sendSecurityAlertEmail(
        to: String,
        alertType: SecurityAlertType,
        alertDetails: String,
        userId: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): EmailSendResult
    
    /**
     * Sends a custom email with specified HTML content and subject.
     *
     * This function is useful for sending one-off or ad-hoc emails that do not
     * have a predefined template. It allows for full control over the email's
     * body and subject line.
     *
     * @param email The recipient's email address.
     * @param subject The subject line of the email.
     * @param htmlContent The main body of the email in HTML format.
     * @param userId A unique identifier for the user, used for logging and tracking purposes.
     * @return `true` if the email was sent successfully, `false` otherwise.
     */
    suspend fun sendCustomEmail(
        to: String,
        subject: String,
        body: String,
        metadata: Map<String, String>?
    ): EmailSendResult
    
    /**
     * Gets the delivery status of an email.
     * 
     * @param emailId The email ID to check
     * @return EmailDeliveryStatus for the email
     */
    suspend fun getDeliveryStatus(emailId: String): EmailDeliveryStatus
    
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
    val timestamp: Long = Clock.System.now().epochSeconds,
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