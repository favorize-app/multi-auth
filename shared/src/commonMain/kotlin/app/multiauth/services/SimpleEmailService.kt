package app.multiauth.services

import app.multiauth.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Simple email service implementation for development and testing.
 * Logs emails to console and can optionally save them to files.
 */
class SimpleEmailService(
    private val config: EmailConfig = EmailConfig()
) : EmailService {
    
    private val logger = LoggerLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    private var isInitialized = false
    private val emailQueue = mutableListOf<QueuedEmail>()
    private val deliveryStatuses = mutableMapOf<String, EmailDeliveryStatus>()
    
    override suspend fun initialize(config: EmailConfig): Boolean {
        return try {
            logger.info("SimpleEmailService", "Initializing Simple Email Service")
            isInitialized = true
            logger.info("SimpleEmailService", "Simple Email Service initialized successfully")
            true
        } catch (e: Exception) {
            logger.error("SimpleEmailService", "Failed to initialize Simple Email Service: ${e.message}")
            false
        }
    }
    
    override suspend fun sendVerificationEmail(
        to: String,
        verificationCode: String,
        userId: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): EmailSendResult {
        return sendEmail(
            to = to,
            subject = "Verify Your Email",
            body = buildVerificationEmailBody(verificationCode, template, variables),
            type = EmailType.VERIFICATION,
            metadata = mapOf(
                "userId" to userId,
                "verificationCode" to verificationCode,
                "template" to (template?.id ?: "default")
            )
        )
    }
    
    override suspend fun sendPasswordResetEmail(
        to: String,
        resetToken: String,
        userId: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): EmailSendResult {
        return sendEmail(
            to = to,
            subject = "Reset Your Password",
            body = buildPasswordResetEmailBody(resetToken, template, variables),
            type = EmailType.PASSWORD_RESET,
            metadata = mapOf(
                "userId" to userId,
                "resetToken" to resetToken,
                "template" to (template?.id ?: "default")
            )
        )
    }
    
    override suspend fun sendWelcomeEmail(
        to: String,
        displayName: String,
        userId: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): EmailSendResult {
        return sendEmail(
            to = to,
            subject = "Welcome to Multi-Auth!",
            body = buildWelcomeEmailBody(displayName, template, variables),
            type = EmailType.WELCOME,
            metadata = mapOf(
                "userId" to userId,
                "displayName" to displayName,
                "template" to (template?.id ?: "default")
            )
        )
    }
    
    override suspend fun sendSecurityAlertEmail(
        to: String,
        alertType: SecurityAlertType,
        alertDetails: String,
        userId: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): EmailSendResult {
        return sendEmail(
            to = to,
            subject = "Security Alert: ${alertType.displayName}",
            body = buildSecurityAlertEmailBody(alertType, alertDetails, template, variables),
            type = EmailType.SECURITY_ALERT,
            metadata = mapOf(
                "userId" to userId,
                "alertType" to alertType.name,
                "alertDetails" to alertDetails,
                "template" to (template?.id ?: "default")
            )
        )
    }
    
    override suspend fun sendCustomEmail(
        to: String,
        subject: String,
        body: String,
        metadata: Map<String, String>?
    ): EmailSendResult {
        return sendEmail(
            to = to,
            subject = subject,
            body = body,
            type = EmailType.CUSTOM,
            metadata = metadata ?: emptyMap()
        )
    }
    
    override suspend fun getDeliveryStatus(emailId: String): EmailDeliveryStatus? {
        return deliveryStatuses[emailId]
    }
    
    override suspend fun getEmailStats(): EmailStats {
        val totalSent = emailQueue.size
        val delivered = deliveryStatuses.values.count { it.status == DeliveryStatus.DELIVERED }
        val failed = deliveryStatuses.values.count { it.status == DeliveryStatus.FAILED }

        return EmailStats(
            totalSent = totalSent.toLong(),
            totalDelivered = delivered.toLong(),
            totalFailed = failed.toLong(),
            deliveryRate = if (totalSent > 0) (delivered.toDouble() / totalSent) * 100 else 0.0,
            averageDeliveryTimeMs = calculateAverageDeliveryTime(),
            totalOpened = TODO(),
            totalClicked = TODO(),
            totalBounced = TODO(),
            openRate = TODO(),
            clickRate = TODO(),
            bounceRate = TODO(),
            lastSentAt = TODO(),
            lastDeliveredAt = TODO()
        )
    }
    
    override suspend fun isReady(): Boolean = isInitialized
    
    override suspend fun getServiceInfo(): EmailServiceInfo {
        return EmailServiceInfo(
            provider = EmailProvider.SIMPLE,
            isInitialized = isInitialized,
            supportsTemplates = true,
            supportsAttachments = false,
            maxRecipients = 1,
            rateLimit = null,
            features = listOf(
                "Email logging",
                "Template support",
                "Delivery tracking",
                "File output"
            )
        )
    }
    
    private suspend fun sendEmail(
        to: String,
        subject: String,
        body: String,
        type: EmailType,
        metadata: Map<String, String>
    ): EmailSendResult {
        if (!isInitialized) {
            return EmailSendResult(
                success = false,
                emailId = null,
                error = "Email service not initialized"
            )
        }
        
        val emailId = generateEmailId()
        val timestamp = Clock.System.now().epochSeconds()
        
        val email = QueuedEmail(
            id = emailId,
            to = to,
            subject = subject,
            body = body,
            type = type,
            metadata = metadata,
            timestamp = timestamp
        )
        
        try {
            // Add to queue
            emailQueue.add(email)
            
            // Log email to console
            logEmailToConsole(email)
            
            // Save email to file if configured
            if (config.saveToFile) {
                saveEmailToFile(email)
            }
            
            // Mark as delivered (since this is a simple service)
            val deliveryStatus = EmailDeliveryStatus(
                emailId = emailId,
                status = DeliveryStatus.DELIVERED,
                deliveredAt = timestamp,
                attempts = 1,
                lastAttemptAt = timestamp,
                errorMessage = null
            )
            deliveryStatuses[emailId] = deliveryStatus
            
            logger.info("services", "Email sent successfully: $emailId to $to")
            
            EmailSendResult(
                success = true,
                emailId = emailId,
                error = null
            )
            
        } catch (e: Exception) {
            logger.error("services", "Failed to send email: ${e.message}")
            
            val deliveryStatus = EmailDeliveryStatus(
                emailId = emailId,
                status = DeliveryStatus.FAILED,
                deliveredAt = null,
                attempts = 1,
                lastAttemptAt = timestamp,
                errorMessage = e.message
            )
            deliveryStatuses[emailId] = deliveryStatus
            
            EmailSendResult(
                success = false,
                emailId = emailId,
                error = e.message
            )
        }
    }
    
    private fun buildVerificationEmailBody(
        verificationCode: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): String {
        val defaultBody = """
            <html>
            <body>
                <h2>Verify Your Email</h2>
                <p>Your verification code is: <strong>$verificationCode</strong></p>
                <p>This code will expire in 10 minutes.</p>
                <p>If you didn't request this verification, please ignore this email.</p>
            </body>
            </html>
        """.trimIndent()
        
        return template?.let { applyTemplate(it, variables) } ?: defaultBody
    }
    
    private fun buildPasswordResetEmailBody(
        resetToken: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): String {
        val defaultBody = """
            <html>
            <body>
                <h2>Reset Your Password</h2>
                <p>Click the link below to reset your password:</p>
                <p><a href="${config.baseUrl}/reset-password?token=$resetToken">Reset Password</a></p>
                <p>This link will expire in 1 hour.</p>
                <p>If you didn't request a password reset, please ignore this email.</p>
            </body>
            </html>
        """.trimIndent()
        
        return template?.let { applyTemplate(it, variables) } ?: defaultBody
    }
    
    private fun buildWelcomeEmailBody(
        displayName: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): String {
        val defaultBody = """
            <html>
            <body>
                <h2>Welcome to Multi-Auth, $displayName!</h2>
                <p>Thank you for joining us. Your account has been created successfully.</p>
                <p>You can now sign in and start using our services.</p>
                <p>If you have any questions, please don't hesitate to contact support.</p>
            </body>
            </html>
        """.trimIndent()
        
        return template?.let { applyTemplate(it, variables) } ?: defaultBody
    }
    
    private fun buildSecurityAlertEmailBody(
        alertType: SecurityAlertType,
        alertDetails: String,
        template: EmailTemplate?,
        variables: EmailTemplateVariables?
    ): String {
        val defaultBody = """
            <html>
            <body>
                <h2>Security Alert: ${alertType.displayName}</h2>
                <p><strong>Alert Type:</strong> ${alertType.displayName}</p>
                <p><strong>Details:</strong> $alertDetails</p>
                <p><strong>Time:</strong> ${java.time.Clock.System.now()()}</p>
                <p>If this activity was not authorized by you, please contact support immediately.</p>
            </body>
            </html>
        """.trimIndent()
        
        return template?.let { applyTemplate(it, variables) } ?: defaultBody
    }
    
    private fun applyTemplate(template: EmailTemplate, variables: EmailTemplateVariables?): String {
        var body = template.htmlBody
        
        variables?.variables?.forEach { (key, value) ->
            body = body.replace("{{$key}}", value)
        }
        
        return body
    }
    
    private fun logEmailToConsole(email: QueuedEmail) {
        logger.info("services", """
            ===== EMAIL SENT =====
            ID: ${email.id}
            To: ${email.to}
            Subject: ${email.subject}
            Type: ${email.type}
            Timestamp: ${java.time.Instant.ofEpochMilli(email.timestamp)}
            Body: ${email.body.take(200)}${if (email.body.length > 200) "..." else ""}
            =====================
        """.trimIndent())
    }
    
    private fun saveEmailToFile(email: QueuedEmail) {
        try {
            val emailDir = java.io.File("emails")
            if (!emailDir.exists()) {
                emailDir.mkdirs()
            }
            
            val emailFile = java.io.File(emailDir, "${email.id}.json")
            val emailData = EmailFileData(
                id = email.id,
                to = email.to,
                subject = email.subject,
                body = email.body,
                type = email.type.name,
                metadata = email.metadata,
                timestamp = email.timestamp
            )
            
            emailFile.writeText(json.encodeToString(EmailFileData.serializer(), emailData))
            logger.debug("services", "Email saved to file: ${emailFile.absolutePath}")
            
        } catch (e: Exception) {
            logger.error("services", "Failed to save email to file: ${e.message}")
        }
    }
    
    private fun generateEmailId(): String {
        return "email_${Clock.System.now().epochSeconds()}_${(0..9999).random()}"
    }
    
    private fun calculateAverageDeliveryTime(): Long {
        val deliveredEmails = deliveryStatuses.values.filter { it.status == DeliveryStatus.DELIVERED }
        if (deliveredEmails.isEmpty()) return 0
        
        val totalTime = deliveredEmails.sumOf { it.deliveredAt!! - it.sentAt }
        return totalTime / deliveredEmails.size
    }
    
    private fun calculateEmailsByType(): Map<String, Long> {
        return emailQueue.groupBy { it.type.name }
            .mapValues { it.value.size.toLong() }
    }
    
    private fun getEmailTimestamp(emailId: String): Long {
        return emailQueue.find { it.id == emailId }?.timestamp ?: 0L
    }
}

/**
 * Internal data class for queued emails.
 */
@Serializable
private data class QueuedEmail(
    val id: String,
    val to: String,
    val subject: String,
    val body: String,
    val type: EmailType,
    val metadata: Map<String, String>,
    val timestamp: Long
)

/**
 * Internal data class for email file storage.
 */
@Serializable
private data class EmailFileData(
    val id: String,
    val to: String,
    val subject: String,
    val body: String,
    val type: String,
    val metadata: Map<String, String>,
    val timestamp: Long
)

/**
 * Email types supported by the simple service.
 */
private enum class EmailType {
    VERIFICATION,
    PASSWORD_RESET,
    WELCOME,
    SECURITY_ALERT,
    CUSTOM
}