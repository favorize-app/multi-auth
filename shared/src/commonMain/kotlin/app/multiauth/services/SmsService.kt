package app.multiauth.services

import app.multiauth.util.Logger
import kotlinx.coroutines.flow.Flow

/**
 * Service for sending SMS messages in the Multi-Auth system.
 * Handles verification codes, security alerts, and notifications.
 */
interface SmsService {
    
    /**
     * Initializes the SMS service with configuration.
     * 
     * @param config SMS service configuration
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(config: SmsConfig): Boolean
    
    /**
     * Sends a verification code via SMS.
     * 
     * @param phoneNumber The recipient phone number
     * @param verificationCode The verification code
     * @param userId The user ID for tracking
     * @return true if SMS sent successfully, false otherwise
     */
    suspend fun sendVerificationCode(
        phoneNumber: String,
        verificationCode: String,
        userId: String
    ): Boolean
    
    /**
     * Sends a security alert via SMS.
     * 
     * @param phoneNumber The recipient phone number
     * @param alertType The type of security alert
     * @param details Additional details about the alert
     * @param userId The user ID for tracking
     * @return true if SMS sent successfully, false otherwise
     */
    suspend fun sendSecurityAlert(
        phoneNumber: String,
        alertType: SecurityAlertType,
        details: String,
        userId: String
    ): Boolean
    
    /**
     * Sends a custom SMS message.
     * 
     * @param phoneNumber The recipient phone number
     * @param message The SMS message content
     * @param userId The user ID for tracking
     * @return true if SMS sent successfully, false otherwise
     */
    suspend fun sendCustomMessage(
        phoneNumber: String,
        message: String,
        userId: String
    ): Boolean
    
    /**
     * Sends a two-factor authentication code via SMS.
     * 
     * @param phoneNumber The recipient phone number
     * @param mfaCode The MFA code
     * @param userId The user ID for tracking
     * @return true if SMS sent successfully, false otherwise
     */
    suspend fun sendMfaCode(
        phoneNumber: String,
        mfaCode: String,
        userId: String
    ): Boolean
    
    /**
     * Sends a login notification via SMS.
     * 
     * @param phoneNumber The recipient phone number
     * @param loginLocation The location of the login
     * @param deviceInfo Information about the device
     * @param userId The user ID for tracking
     * @return true if SMS sent successfully, false otherwise
     */
    suspend fun sendLoginNotification(
        phoneNumber: String,
        loginLocation: String,
        deviceInfo: String,
        userId: String
    ): Boolean
    
    /**
     * Gets the delivery status of an SMS.
     * 
     * @param smsId The SMS ID to check
     * @return SmsDeliveryStatus for the SMS
     */
    suspend fun getSmsDeliveryStatus(smsId: String): SmsDeliveryStatus?
    
    /**
     * Gets SMS statistics and metrics.
     * 
     * @return SmsStats with delivery statistics
     */
    suspend fun getSmsStats(): SmsStats
    
    /**
     * Checks if the SMS service is ready to send messages.
     * 
     * @return true if ready, false otherwise
     */
    suspend fun isReady(): Boolean
    
    /**
     * Gets information about the SMS service configuration.
     * 
     * @return SmsServiceInfo with service details
     */
    suspend fun getServiceInfo(): SmsServiceInfo
    
    /**
     * Validates a phone number format.
     * 
     * @param phoneNumber The phone number to validate
     * @return true if valid format, false otherwise
     */
    suspend fun validatePhoneNumber(phoneNumber: String): Boolean
    
    /**
     * Formats a phone number to international format.
     * 
     * @param phoneNumber The phone number to format
     * @return Formatted phone number or null if invalid
     */
    suspend fun formatPhoneNumber(phoneNumber: String): String?
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
    CUSTOM("Custom", false)
}

/**
 * SMS delivery status information.
 */
data class SmsDeliveryStatus(
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
 * SMS message template for different types of messages.
 */
data class SmsTemplate(
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
    val timestamp: Long = System.currentTimeMillis(),
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