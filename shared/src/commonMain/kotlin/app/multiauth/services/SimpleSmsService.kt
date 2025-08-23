package app.multiauth.services

import app.multiauth.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Simple SMS service implementation for development and testing.
 * Logs SMS messages to console and can optionally save them to files.
 */
class SimpleSmsService(
    private val config: SmsConfig = SmsConfig()
) : SmsService {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    private var isInitialized = false
    private val smsQueue = mutableListOf<QueuedSms>()
    private val deliveryStatuses = mutableMapOf<String, SmsDeliveryStatus>()
    private val rateLimits = mutableMapOf<String, SmsRateLimit>()
    
    override suspend fun initialize(): Boolean {
        return try {
            logger.info("Initializing Simple SMS Service")
            isInitialized = true
            logger.info("Simple SMS Service initialized successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to initialize Simple SMS Service: ${e.message}")
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
        metadata: Map<String, String>?
    ): SmsSendResult {
        return sendSms(
            phoneNumber = phoneNumber,
            message = message,
            type = SmsType.CUSTOM,
            metadata = metadata ?: emptyMap()
        )
    }
    
    override suspend fun getDeliveryStatus(smsId: String): SmsDeliveryStatus? {
        return deliveryStatuses[smsId]
    }
    
    override suspend fun getSmsStats(): SmsStats {
        val totalSent = smsQueue.size
        val delivered = deliveryStatuses.values.count { it.status == SmsDeliveryStatus.DeliveryStatus.DELIVERED }
        val failed = deliveryStatuses.values.count { it.status == SmsDeliveryStatus.DeliveryStatus.FAILED }
        val pending = totalSent - delivered - failed
        
        return SmsStats(
            totalSmsSent = totalSent.toLong(),
            deliveredSms = delivered.toLong(),
            failedSms = failed.toLong(),
            pendingSms = pending.toLong(),
            deliveryRate = if (totalSent > 0) (delivered.toDouble() / totalSent) * 100 else 0.0,
            averageDeliveryTime = calculateAverageDeliveryTime(),
            smsByType = calculateSmsByType()
        )
    }
    
    override suspend fun isReady(): Boolean = isInitialized
    
    override suspend fun getServiceInfo(): SmsServiceInfo {
        return SmsServiceInfo(
            provider = SmsProvider.SIMPLE,
            isInitialized = isInitialized,
            supportsTemplates = true,
            supportsUnicode = true,
            maxMessageLength = 160,
            rateLimit = null,
            features = listOf(
                "SMS logging",
                "Template support",
                "Delivery tracking",
                "File output",
                "Rate limiting"
            )
        )
    }
    
    override suspend fun validatePhoneNumber(phoneNumber: String): PhoneNumberValidationResult {
        return try {
            // Simple validation - check if it's a valid phone number format
            val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
            
            if (cleaned.length < 10 || cleaned.length > 15) {
                PhoneNumberValidationResult(
                    isValid = false,
                    formattedNumber = null,
                    countryCode = null,
                    errorMessage = "Phone number must be between 10 and 15 digits"
                )
            } else {
                PhoneNumberValidationResult(
                    isValid = true,
                    formattedNumber = cleaned,
                    countryCode = extractCountryCode(cleaned),
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            PhoneNumberValidationResult(
                isValid = false,
                formattedNumber = null,
                countryCode = null,
                errorMessage = "Validation failed: ${e.message}"
            )
        }
    }
    
    override suspend fun formatPhoneNumber(phoneNumber: String, countryCode: String?): String {
        return try {
            val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
            
            when {
                cleaned.startsWith("+") -> cleaned
                countryCode != null && !cleaned.startsWith(countryCode) -> "+$countryCode$cleaned"
                else -> cleaned
            }
        } catch (e: Exception) {
            logger.error("Failed to format phone number: ${e.message}")
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
            return SmsSendResult(
                success = false,
                smsId = null,
                error = "SMS service not initialized"
            )
        }
        
        // Check rate limiting
        if (isRateLimited(phoneNumber, type)) {
            return SmsSendResult(
                success = false,
                smsId = null,
                error = "Rate limit exceeded for $phoneNumber"
            )
        }
        
        val smsId = generateSmsId()
        val timestamp = System.currentTimeMillis()
        
        val sms = QueuedSms(
            id = smsId,
            phoneNumber = phoneNumber,
            message = message,
            type = type,
            metadata = metadata,
            timestamp = timestamp
        )
        
        try {
            // Add to queue
            smsQueue.add(sms)
            
            // Log SMS to console
            logSmsToConsole(sms)
            
            // Save SMS to file if configured
            if (config.saveToFile) {
                saveSmsToFile(sms)
            }
            
            // Mark as delivered (since this is a simple service)
            val deliveryStatus = SmsDeliveryStatus(
                smsId = smsId,
                status = SmsDeliveryStatus.DeliveryStatus.DELIVERED,
                deliveredAt = timestamp,
                attempts = 1,
                lastAttemptAt = timestamp,
                errorMessage = null
            )
            deliveryStatuses[smsId] = deliveryStatus
            
            // Update rate limiting
            updateRateLimit(phoneNumber, type)
            
            logger.info("SMS sent successfully: $smsId to $phoneNumber")
            
            SmsSendResult(
                success = true,
                smsId = smsId,
                error = null
            )
            
        } catch (e: Exception) {
            logger.error("Failed to send SMS: ${e.message}")
            
            val deliveryStatus = SmsDeliveryStatus(
                smsId = smsId,
                status = SmsDeliveryStatus.DeliveryStatus.FAILED,
                deliveredAt = null,
                attempts = 1,
                lastAttemptAt = timestamp,
                errorMessage = e.message
            )
            deliveryStatuses[smsId] = deliveryStatus
            
            SmsSendResult(
                success = false,
                smsId = smsId,
                error = e.message
            )
        }
    }
    
    private fun buildVerificationCodeMessage(
        verificationCode: String,
        template: SmsTemplate?,
        variables: SmsTemplateVariables?
    ): String {
        val defaultMessage = "Your verification code is: $verificationCode. Valid for 10 minutes."
        
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
        val defaultMessage = "Your MFA code is: $mfaCode. Valid for 5 minutes."
        
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
        var message = template.message
        
        variables?.variables?.forEach { (key, value) ->
            message = message.replace("{{$key}}", value)
        }
        
        return message
    }
    
    private fun logSmsToConsole(sms: QueuedSms) {
        logger.info("""
            ===== SMS SENT =====
            ID: ${sms.id}
            To: ${sms.phoneNumber}
            Type: ${sms.type}
            Message: ${sms.message}
            Timestamp: ${java.time.Instant.ofEpochMilli(sms.timestamp)}
            ===================
        """.trimIndent())
    }
    
    private fun saveSmsToFile(sms: QueuedSms) {
        try {
            val smsDir = java.io.File("sms")
            if (!smsDir.exists()) {
                smsDir.mkdirs()
            }
            
            val smsFile = java.io.File(smsDir, "${sms.id}.json")
            val smsData = SmsFileData(
                id = sms.id,
                phoneNumber = sms.phoneNumber,
                message = sms.message,
                type = sms.type.name,
                metadata = sms.metadata,
                timestamp = sms.timestamp
            )
            
            smsFile.writeText(json.encodeToString(SmsFileData.serializer(), smsData))
            logger.debug("SMS saved to file: ${smsFile.absolutePath}")
            
        } catch (e: Exception) {
            logger.error("Failed to save SMS to file: ${e.message}")
        }
    }
    
    private fun generateSmsId(): String {
        return "sms_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
    
    private fun calculateAverageDeliveryTime(): Double {
        val deliveredSms = deliveryStatuses.values.filter { it.status == SmsDeliveryStatus.DeliveryStatus.DELIVERED }
        if (deliveredSms.isEmpty()) return 0.0
        
        val totalTime = deliveredSms.sumOf { it.deliveredAt!! - it.timestamp }
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
        
        val now = System.currentTimeMillis()
        val timeWindow = when (type) {
            SmsType.VERIFICATION -> 60000L // 1 minute
            SmsType.MFA_CODE -> 30000L // 30 seconds
            SmsType.SECURITY_ALERT -> 300000L // 5 minutes
            SmsType.LOGIN_NOTIFICATION -> 600000L // 10 minutes
            SmsType.CUSTOM -> 60000L // 1 minute
        }
        
        return (now - rateLimit.lastSent) < timeWindow
    }
    
    private fun updateRateLimit(phoneNumber: String, type: SmsType) {
        val key = "${phoneNumber}_${type.name}"
        val now = System.currentTimeMillis()
        
        rateLimits[key] = SmsRateLimit(
            phoneNumber = phoneNumber,
            type = type.name,
            lastSent = now,
            count = (rateLimits[key]?.count ?: 0) + 1
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
 * Internal data class for queued SMS messages.
 */
@Serializable
private data class QueuedSms(
    val id: String,
    val phoneNumber: String,
    val message: String,
    val type: SmsType,
    val metadata: Map<String, String>,
    val timestamp: Long
)

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

/**
 * SMS types supported by the simple service.
 */
private enum class SmsType {
    VERIFICATION,
    SECURITY_ALERT,
    MFA_CODE,
    LOGIN_NOTIFICATION,
    CUSTOM
}