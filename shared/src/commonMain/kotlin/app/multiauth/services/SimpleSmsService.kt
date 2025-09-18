@file:OptIn(ExperimentalTime::class)

package app.multiauth.services



import app.multiauth.util.Logger
import app.multiauth.util.TimeoutConstants
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Clock

/**
 * Simple SMS service implementation for development and testing.
 * Logs SMS messages to console and can optionally save them to files.
 */
class SimpleSmsService(
    private val config: SmsConfig = SmsConfig(
        provider = SmsProvider.CUSTOM,
        apiKey = "simple-dev-key"
    )
) : SmsService {

    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }

    private var isInitialized = false
    private val smsQueue = mutableListOf<QueuedSms>()
    private val deliveryStatuses = mutableMapOf<String, SmsDeliveryStatusInfo>()
    private val rateLimits = mutableMapOf<String, SmsRateLimit>()

    override suspend fun initialize(): Boolean {
        return try {
            logger.info("SimpleSmsService", "Initializing Simple SMS Service")
            isInitialized = true
            logger.info("SimpleSmsService", "Simple SMS Service initialized successfully")
            true
        } catch (e: Exception) {
            logger.error("SimpleSmsService", "Failed to initialize Simple SMS Service: ${e.message}")
            false
        }
    }

    override suspend fun sendVerificationCode(
        phoneNumber: String,
        verificationCode: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult {
        return sendSms(
            phoneNumber = phoneNumber,
            message = buildVerificationCodeMessage(verificationCode, template, variables),
            type = SmsType.VERIFICATION,
            metadata = mapOf(
                "userId" to (userId ?: "unknown"),
                "verificationCode" to verificationCode,
                "template" to (template?.id ?: "default")
            )
        )
    }

    override suspend fun sendSecurityAlert(
        phoneNumber: String,
        alertType: String,
        alertDetails: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult {
        return sendSms(
            phoneNumber = phoneNumber,
            message = buildSecurityAlertMessage(alertType, alertDetails, template, variables),
            type = SmsType.SECURITY_ALERT,
            metadata = mapOf(
                "userId" to (userId ?: "unknown"),
                "alertType" to alertType,
                "alertDetails" to alertDetails,
                "template" to (template?.id ?: "default")
            )
        )
    }

    override suspend fun sendMfaCode(
        phoneNumber: String,
        mfaCode: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult {
        return sendSms(
            phoneNumber = phoneNumber,
            message = buildMfaCodeMessage(mfaCode, template, variables),
            type = SmsType.MFA_CODE,
            metadata = mapOf(
                "userId" to (userId ?: "unknown"),
                "mfaCode" to mfaCode,
                "template" to (template?.id ?: "default")
            )
        )
    }

    override suspend fun sendLoginNotification(
        phoneNumber: String,
        loginDetails: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult {
        return sendSms(
            phoneNumber = phoneNumber,
            message = buildLoginNotificationMessage(loginDetails, template, variables),
            type = SmsType.LOGIN_NOTIFICATION,
            metadata = mapOf(
                "userId" to (userId ?: "unknown"),
                "loginDetails" to loginDetails,
                "template" to (template?.id ?: "default")
            )
        )
    }

    override suspend fun sendCustomSms(
        phoneNumber: String,
        message: String,
        userId: String?,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): SmsSendResult {
        return sendSms(
            phoneNumber = phoneNumber,
            message = message,
            type = SmsType.CUSTOM,
            metadata = mapOf(
                "userId" to (userId ?: "unknown"),
                "template" to (template?.id ?: "default")
            )
        )
    }

    override suspend fun getDeliveryStatus(smsId: String): SmsDeliveryStatusInfo? {
        return deliveryStatuses[smsId]
    }

    override suspend fun getSmsStats(): SmsStats {
        val totalSent = deliveryStatuses.values.size.toLong()
        val totalDelivered = deliveryStatuses.values.count { it.status == SmsDeliveryStatus.DELIVERED }.toLong()
        val totalFailed = deliveryStatuses.values.count { it.status == SmsDeliveryStatus.FAILED }.toLong()
        val totalRejected = deliveryStatuses.values.count { it.status == SmsDeliveryStatus.REJECTED }.toLong()

        return SmsStats(
            totalSent = totalSent,
            totalDelivered = totalDelivered,
            totalFailed = totalFailed,
            totalRejected = totalRejected,
            deliveryRate = if (totalSent > 0) totalDelivered.toDouble() / totalSent else 0.0,
            failureRate = if (totalSent > 0) (totalFailed + totalRejected).toDouble() / totalSent else 0.0,
            averageDeliveryTimeMs = null,
            totalCost = 0.0,
            lastSentAt = deliveryStatuses.values.maxOfOrNull { it.sentAt },
            lastDeliveredAt = deliveryStatuses.values.maxOfOrNull { it.deliveredAt ?: 0L },
            messagesByCountry = emptyMap(),
            messagesByProvider = emptyMap()
        )
    }

    override suspend fun isReady(): Boolean = isInitialized

    override suspend fun getServiceInfo(): SmsServiceInfo {
        return SmsServiceInfo(
            provider = SmsProvider.SIMPLE,
            isInitialized = isInitialized,
            fromNumber = null,
            fromName = null,
            supportsDeliveryReports = false,
            supportsAnalytics = false,
            maxRetries = 3,
            timeoutMs = 30000,
            defaultCountryCode = "US",
            maxMessageLength = 160,
            lastTestAt = null
        )
    }

    override suspend fun validatePhoneNumber(phoneNumber: String): PhoneNumberValidationResult {
        return try {
            // Simple validation - just check if it's not empty and has some digits
            val hasDigits = phoneNumber.any { it.isDigit() }
            val isValid = phoneNumber.isNotBlank() && hasDigits

            PhoneNumberValidationResult(
                isValid = isValid,
                formattedNumber = if (isValid) phoneNumber else null,
                countryCode = null,
                nationalNumber = null,
                isValidForRegion = isValid,
                region = null,
                carrier = null
            )
        } catch (e: Exception) {
            PhoneNumberValidationResult(
                isValid = false,
                formattedNumber = null,
                countryCode = null,
                nationalNumber = null,
                isValidForRegion = false,
                region = null,
                carrier = null
            )
        }
    }

    override suspend fun formatPhoneNumber(phoneNumber: String, countryCode: String?): String {
        return try {
            // Simple formatting - just clean up the number
            phoneNumber.replace(Regex("[^\\d+]"), "")
        } catch (e: Exception) {
            phoneNumber
        }
    }

    private suspend fun sendSms(
        phoneNumber: String,
        message: String,
        type: SmsType,
        metadata: Map<String, String>
    ): SmsSendResult {
        if (!isInitialized) {
            return SmsSendResult.Failure(
                error = "SMS service not initialized",
                errorCode = "NOT_INITIALIZED",
                retryable = false,
                attemptedAt = Clock.System.now().epochSeconds
            )
        }

        // Check rate limiting
        if (isRateLimited(phoneNumber, type)) {
            return SmsSendResult.RateLimited(
                retryAfterMs = 60000, // 1 minute
                attemptedAt = Clock.System.now().epochSeconds
            )
        }

        val smsId = generateSmsId()
        val timestamp = Clock.System.now().epochSeconds

        val sms = QueuedSms(
            id = smsId,
            phoneNumber = phoneNumber,
            message = message,
            type = type,
            metadata = metadata,
            queuedAt = timestamp
        )

        try {
            // Add to queue
            smsQueue.add(sms)

            // Log SMS to console
            logSmsToConsole(sms)

            // Save SMS to file if configured
            if (config.enableAnalytics) {
                saveSmsToFile(sms)
            }

            // Mark as delivered (since this is a simple service)
            val deliveryStatus = SmsDeliveryStatusInfo(
                smsId = smsId,
                status = SmsDeliveryStatus.DELIVERED,
                sentAt = timestamp,
                deliveredAt = timestamp,
                failedAt = null,
                failureReason = null,
                recipientPhoneNumber = phoneNumber,
                message = message,
                providerMessageId = null,
                cost = null,
                segments = 1
            )
            deliveryStatuses[smsId] = deliveryStatus

            // Update rate limiting
            updateRateLimit(phoneNumber, type)

            logger.info("SimpleSmsService", "SMS sent successfully: $smsId to $phoneNumber")

            return SmsSendResult.Success(
                smsId = smsId,
                providerMessageId = null,
                sentAt = timestamp,
                cost = null,
                segments = 1
            )

        } catch (e: Exception) {
            logger.error("SimpleSmsService", "Failed to send SMS: ${e.message}")

            val deliveryStatus = SmsDeliveryStatusInfo(
                smsId = smsId,
                status = SmsDeliveryStatus.FAILED,
                sentAt = timestamp,
                deliveredAt = null,
                failedAt = timestamp,
                failureReason = e.message,
                recipientPhoneNumber = phoneNumber,
                message = message,
                providerMessageId = null,
                cost = null,
                segments = 1
            )
            deliveryStatuses[smsId] = deliveryStatus

            return SmsSendResult.Failure(
                error = e.message ?: "Unknown error",
                errorCode = "SEND_FAILED",
                retryable = true,
                attemptedAt = timestamp
            )
        }
    }

    private fun buildVerificationCodeMessage(
        verificationCode: String,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): String {
        val defaultMessage = TimeoutConstants.getSmsVerificationMessage(verificationCode).replace("\n\n", ". ")

        return template?.let { applyTemplate(it, variables) } ?: defaultMessage
    }

    private fun buildSecurityAlertMessage(
        alertType: String,
        alertDetails: String,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): String {
        val defaultMessage = "SECURITY ALERT: $alertType - $alertDetails. Contact support if unauthorized."

        return template?.let { applyTemplate(it, variables) } ?: defaultMessage
    }

    private fun buildMfaCodeMessage(
        mfaCode: String,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): String {
        val defaultMessage = TimeoutConstants.getMfaCodeMessage(mfaCode)

        return template?.let { applyTemplate(it, variables) } ?: defaultMessage
    }

    private fun buildLoginNotificationMessage(
        loginDetails: String,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): String {
        val defaultMessage = "New login detected: $loginDetails. Contact support if unauthorized."

        return template?.let { applyTemplate(it, variables) } ?: defaultMessage
    }

    private fun applyTemplate(template: SmsTemplate, variables: SmsTemplateVariables?): String {
        var message = template.content

        variables?.let { vars ->
            // Apply common variables
            message = message.replace("{{userId}}", vars.userId)
            vars.username?.let { message = message.replace("{{username}}", it) }
            message = message.replace("{{phoneNumber}}", vars.phoneNumber)
            vars.verificationCode?.let { message = message.replace("{{verificationCode}}", it) }
            vars.mfaCode?.let { message = message.replace("{{mfaCode}}", it) }
            vars.alertType?.let { message = message.replace("{{alertType}}", it.name) }
            vars.alertDetails?.let { message = message.replace("{{alertDetails}}", it) }
            vars.loginLocation?.let { message = message.replace("{{loginLocation}}", it) }
            vars.deviceInfo?.let { message = message.replace("{{deviceInfo}}", it) }
            message = message.replace("{{timestamp}}", vars.timestamp.toString())
            vars.supportPhone?.let { message = message.replace("{{supportPhone}}", it) }
            message = message.replace("{{appName}}", vars.appName)
        }

        return message
    }

    private fun logSmsToConsole(sms: QueuedSms) {
                    logger.info("SimpleSmsService", """
            ===== SMS SENT =====
            ID: ${sms.id}
            To: ${sms.phoneNumber}
            Type: ${sms.type}
            Message: ${sms.message}
            Timestamp: ${Instant.fromEpochMilliseconds(sms.queuedAt)}
            ===================
        """.trimIndent())
    }

    private fun saveSmsToFile(sms: QueuedSms) {
        try {
            // File operations are platform-specific in KMP
            // This would need platform-specific implementation
            logger.debug("services", "SMS file saving is not implemented in KMP common code")

            // TODO: Implement proper file storage for multiplatform
            // For now, just log the SMS data
            logger.debug("sms", "SMS data: ${sms.id}, ${sms.phoneNumber}, ${sms.message}")
            val smsData = SmsFileData(
                id = sms.id,
                phoneNumber = sms.phoneNumber,
                message = sms.message,
                type = sms.type.name,
                metadata = sms.metadata,
                timestamp = sms.queuedAt
            )

            // File writing removed for multiplatform compatibility
            logger.debug("SimpleSmsService", "SMS data logged: ${sms.id}")
        } catch (e: Exception) {
            logger.error("SimpleSmsService", "Failed to save SMS to file: ${e.message}")
        }
    }

    private fun generateSmsId(): String {
        return "sms_${Clock.System.now().epochSeconds}_${(0..9999).random()}"
    }

    private fun calculateAverageDeliveryTime(): Double {
        val deliveredSms = deliveryStatuses.values.filter { it.status == SmsDeliveryStatus.DELIVERED }
        if (deliveredSms.isEmpty()) return 0.0

        val totalTime = deliveredSms.sumOf { (it.deliveredAt ?: 0L) - it.sentAt }
        return totalTime.toDouble() / deliveredSms.size
    }

    private fun calculateSmsByType(): Map<String, Long> {
        return smsQueue.groupBy { it.type.name }
            .mapValues { it.value.size.toLong() }
    }

    private fun isRateLimited(phoneNumber: String, type: SmsType): Boolean {
        val key = "${phoneNumber}_${type.name}"
        val rateLimit = rateLimits[key]

        if (rateLimit == null) return false

        val now = Clock.System.now().epochSeconds
        val timeWindow = when (type) {
            SmsType.VERIFICATION -> 60000L // 1 minute
            SmsType.MFA_CODE -> 30000L // 30 seconds
            SmsType.SECURITY_ALERT -> TimeoutConstants.SECURITY_ALERT_TIMEOUT.inWholeMilliseconds
            SmsType.LOGIN_NOTIFICATION -> TimeoutConstants.LOGIN_NOTIFICATION_TIMEOUT.inWholeMilliseconds
            SmsType.CUSTOM -> 60000L // 1 minute
            else -> 60000L // 1 minute default
        }

        return (now - rateLimit.nextResetTime) < timeWindow
    }

    private fun updateRateLimit(phoneNumber: String, type: SmsType) {
        val key = "${phoneNumber}_${type.name}"
        val now = Clock.System.now().epochSeconds

        val currentLimit = rateLimits[key]
        val messagesThisHour = (currentLimit?.messagesSentThisHour ?: 0) + 1
        val messagesThisDay = (currentLimit?.messagesSentThisDay ?: 0) + 1

        rateLimits[key] = SmsRateLimit(
            phoneNumber = phoneNumber,
            maxMessagesPerHour = 10,
            maxMessagesPerDay = 100,
            messagesSentThisHour = messagesThisHour,
            messagesSentThisDay = messagesThisDay,
            nextResetTime = now + 3600000, // 1 hour from now
            isRateLimited = messagesThisHour > 10 || messagesThisDay > 100
        )
    }

    private fun extractCountryCode(phoneNumber: String): String? {
        return when {
            phoneNumber.startsWith("+1") -> "US"
            phoneNumber.startsWith("+44") -> "GB"
            phoneNumber.startsWith("+49") -> "DE"
            phoneNumber.startsWith("+33") -> "FR"
            phoneNumber.startsWith("+39") -> "IT"
            phoneNumber.startsWith("+34") -> "ES"
            phoneNumber.startsWith("+81") -> "JP"
            phoneNumber.startsWith("+86") -> "CN"
            phoneNumber.startsWith("+91") -> "IN"
            phoneNumber.startsWith("+55") -> "BR"
            else -> null
        }
    }
}

/**
 * Internal data class for SMS file storage.
 */
@Serializable
private data class SmsFileData(
    val id: String,
    val phoneNumber: String,
    val message: String,
    val type: String,
    val metadata: Map<String, String>,
    val timestamp: Long
)
