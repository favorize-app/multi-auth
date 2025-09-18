@file:OptIn(ExperimentalTime::class)

package app.multiauth.services



import kotlin.time.ExperimentalTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Service for sending SMS messages in the Multi-Auth system.
 * Handles verification codes, security alerts, and notifications.
 */
interface SmsService {

    /**
     * Initializes the SMS service.
     *
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean

    /**
     * Sends a verification code via SMS.
     *
     * @param phoneNumber The recipient phone number
     * @param verificationCode The verification code
     * @param userId The user ID for tracking
     * @param template Optional SMS template
     * @param variables Optional template variables
     * @return SmsSendResult with detailed information
     */
    suspend fun sendVerificationCode(
        phoneNumber: String,
        verificationCode: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult

    /**
     * Sends a security alert via SMS.
     *
     * @param phoneNumber The recipient phone number
     * @param alertType The type of security alert
     * @param alertDetails Additional details about the alert
     * @param userId The user ID for tracking
     * @param template Optional SMS template
     * @param variables Optional template variables
     * @return SmsSendResult with detailed information
     */
    suspend fun sendSecurityAlert(
        phoneNumber: String,
        alertType: String,
        alertDetails: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult

    /**
     * Sends a custom SMS message.
     *
     * @param phoneNumber The recipient phone number
     * @param message The SMS message content
     * @param userId The user ID for tracking
     * @param template Optional SMS template
     * @param variables Optional template variables
     * @return SmsSendResult with detailed information
     */
    suspend fun sendCustomSms(
        phoneNumber: String,
        message: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult

    /**
     * Sends a two-factor authentication code via SMS.
     *
     * @param phoneNumber The recipient phone number
     * @param mfaCode The MFA code
     * @param userId The user ID for tracking
     * @param template Optional SMS template
     * @param variables Optional template variables
     * @return SmsSendResult with detailed information
     */
    suspend fun sendMfaCode(
        phoneNumber: String,
        mfaCode: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult

    /**
     * Sends a login notification via SMS.
     *
     * @param phoneNumber The recipient phone number
     * @param loginDetails The login details
     * @param userId The user ID for tracking
     * @param template Optional SMS template
     * @param variables Optional template variables
     * @return SmsSendResult with detailed information
     */
    suspend fun sendLoginNotification(
        phoneNumber: String,
        loginDetails: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult

    /**
     * Gets the delivery status of an SMS.
     *
     * @param smsId The SMS ID to check
     * @return SmsDeliveryStatusInfo for the SMS
     */
    suspend fun getDeliveryStatus(smsId: String): SmsDeliveryStatusInfo?

    /**
     * Gets SMS service statistics.
     *
     * @return SmsStats with service metrics
     */
    suspend fun getSmsStats(): SmsStats

    /**
     * Checks if the service is ready to send SMS.
     *
     * @return true if service is ready, false otherwise
     */
    suspend fun isReady(): Boolean

    /**
     * Gets information about the SMS service.
     *
     * @return SmsServiceInfo with service details
     */
    suspend fun getServiceInfo(): SmsServiceInfo

    /**
     * Validates a phone number.
     *
     * @param phoneNumber The phone number to validate
     * @return PhoneNumberValidationResult with validation details
     */
    suspend fun validatePhoneNumber(phoneNumber: String): PhoneNumberValidationResult

    /**
     * Formats a phone number.
     *
     * @param phoneNumber The phone number to format
     * @param countryCode Optional country code
     * @return Formatted phone number string
     */
    suspend fun formatPhoneNumber(phoneNumber: String, countryCode: String?): String
}

/**
 * SMS service configuration.
 */
data class SmsConfig(
    val provider: SmsProvider,
    val apiKey: String,
    val accountSid: String? = null,
    val authToken: String? = null,
    val fromNumber: String? = null,
    val fromName: String? = null,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 5000,
    val timeoutMs: Long = 30000,
    val enableDeliveryReports: Boolean = true,
    val enableAnalytics: Boolean = true,
    val defaultCountryCode: String = "US",
    val maxMessageLength: Int = 160,
    val enableUnicode: Boolean = true
)

/**
 * Supported SMS service providers.
 */
enum class SmsProvider(val displayName: String, val supportsDeliveryReports: Boolean) {
    TWILIO("Twilio", true),
    AWS_SNS("AWS SNS", true),
    SENDGRID("SendGrid", true),
    MESSAGEBIRD("MessageBird", true),
    VONAGE("Vonage", true),
    INFOBIP("Infobip", true),
    PLIVO("Plivo", true),
    SIMPLE("Simple", false),
    CUSTOM("Custom", false)
}

/**
 * SMS delivery status values.
 */
enum class SmsDeliveryStatus(val displayName: String) {
    SENT("Sent"),
    DELIVERED("Delivered"),
    FAILED("Failed"),
    PENDING("Pending"),
    QUEUED("Queued"),
    REJECTED("Rejected"),
    UNDELIVERED("Undelivered")
}

/**
 * SMS delivery status information.
 */
data class SmsDeliveryStatusInfo(
    val smsId: String,
    val status: SmsDeliveryStatus,
    val sentAt: Long,
    val deliveredAt: Long?,
    val failedAt: Long?,
    val failureReason: String?,
    val recipientPhoneNumber: String,
    val message: String,
    val providerMessageId: String?,
    val cost: Double?,
    val segments: Int = 1
)

/**
 * SMS service statistics and metrics.
 */
data class SmsStats(
    val totalSent: Long,
    val totalDelivered: Long,
    val totalFailed: Long,
    val totalRejected: Long,
    val deliveryRate: Double,
    val failureRate: Double,
    val averageDeliveryTimeMs: Long?,
    val totalCost: Double,
    val lastSentAt: Long?,
    val lastDeliveredAt: Long?,
    val messagesByCountry: Map<String, Long>,
    val messagesByProvider: Map<String, Long>
)

/**
 * Information about the SMS service configuration and status.
 */
data class SmsServiceInfo(
    val provider: SmsProvider,
    val isInitialized: Boolean,
    val fromNumber: String?,
    val fromName: String?,
    val supportsDeliveryReports: Boolean,
    val supportsAnalytics: Boolean,
    val maxRetries: Int,
    val timeoutMs: Long,
    val defaultCountryCode: String,
    val maxMessageLength: Int,
    val lastTestAt: Long?
)

/**
 * SMS message types.
 */
enum class SmsType(val displayName: String) {
    VERIFICATION("Verification"),
    SECURITY_ALERT("Security Alert"),
    MFA_CODE("MFA Code"),
    LOGIN_NOTIFICATION("Login Notification"),
    CUSTOM("Custom"),
    PROMOTIONAL("Promotional"),
    TRANSACTIONAL("Transactional")
}

/**
 * SMS message template for different types of messages.
 */
data class SmsTemplate(
    val id: String,
    val name: String,
    val content: String,
    val variables: List<String>,
    val language: String = "en",
    val maxLength: Int = 160
)

/**
 * SMS template variables for personalization.
 */
data class SmsTemplateVariables(
    val userId: String,
    val username: String?,
    val phoneNumber: String,
    val verificationCode: String? = null,
    val mfaCode: String? = null,
    val alertType: SecurityAlertType? = null,
    val alertDetails: String? = null,
    val loginLocation: String? = null,
    val deviceInfo: String? = null,
    val timestamp: Long = Clock.System.now().epochSeconds,
    val supportPhone: String? = null,
    val appName: String = "Multi-Auth"
)

/**
 * SMS sending result with detailed information.
 */
sealed class SmsSendResult {
    data class Success(
        val smsId: String,
        val providerMessageId: String?,
        val sentAt: Long,
        val cost: Double?,
        val segments: Int
    ) : SmsSendResult()

    data class Failure(
        val error: String,
        val errorCode: String?,
        val retryable: Boolean,
        val attemptedAt: Long
    ) : SmsSendResult()

    data class RateLimited(
        val retryAfterMs: Long,
        val attemptedAt: Long
    ) : SmsSendResult()

    data class InvalidPhoneNumber(
        val phoneNumber: String,
        val reason: String,
        val attemptedAt: Long
    ) : SmsSendResult()
}

/**
 * Phone number validation result.
 */
data class PhoneNumberValidationResult(
    val isValid: Boolean,
    val formattedNumber: String?,
    val countryCode: String?,
    val nationalNumber: String?,
    val isValidForRegion: Boolean,
    val region: String?,
    val carrier: String?
)

/**
 * SMS rate limiting information.
 */
data class SmsRateLimit(
    val phoneNumber: String,
    val maxMessagesPerHour: Int,
    val maxMessagesPerDay: Int,
    val messagesSentThisHour: Int,
    val messagesSentThisDay: Int,
    val nextResetTime: Long,
    val isRateLimited: Boolean
)

/**
 * SMS delivery report from the provider.
 */
data class SmsDeliveryReport(
    val providerMessageId: String,
    val status: SmsDeliveryStatus,
    val deliveredAt: Long?,
    val failureReason: String?,
    val cost: Double?,
    val segments: Int,
    val metadata: Map<String, String>
)

/**
 * Queued SMS message for processing.
 */
data class QueuedSms(
    val id: String,
    val phoneNumber: String,
    val message: String,
    val type: SmsType,
    val metadata: Map<String, String>,
    val queuedAt: Long,
    val priority: Int = 0,
    val retryCount: Int = 0
)
