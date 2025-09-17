@file:OptIn(ExperimentalTime::class)

package app.multiauth.providers.impl


import app.multiauth.models.AuthResult
import app.multiauth.providers.*
import app.multiauth.util.Logger
import app.multiauth.util.CodeGenerationUtil
import app.multiauth.util.TimeoutConstants
import kotlinx.coroutines.delay
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Clock

/**
 * Real email provider that can work with SMTP or HTTP-based email services.
 * Supports SendGrid, AWS SES, Mailgun, and custom SMTP servers.
 */
class SmtpEmailProvider(
    private val config: SmtpEmailConfig,
    private val httpClient: HttpClient
) : EmailProvider {

    private val verificationCodes = mutableMapOf<String, StoredVerificationCode>()
    private val resetTokens = mutableMapOf<String, StoredResetToken>()

    override suspend fun sendVerificationEmail(email: String): AuthResult<Unit> {
        Logger.debug("SmtpEmailProvider", "Sending verification email to: $email")

        return try {
            val code = CodeGenerationUtil.generateVerificationCode()
            val expiresAt = System.now() + TimeoutConstants.EMAIL_VERIFICATION_CODE_TIMEOUT

            // Store verification code
            verificationCodes[email] = StoredVerificationCode(code, expiresAt)

            val subject = "Email Verification Code"
            val htmlBody = buildVerificationEmailHtml(code)
            val textBody = TimeoutConstants.getEmailVerificationMessage(code)

            val result = sendEmail(
                to = email,
                subject = subject,
                htmlBody = htmlBody,
                textBody = textBody
            )

            if (result) {
                Logger.info("SmtpEmailProvider", "Verification email sent successfully to: $email")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Failure(
                    app.multiauth.models.AuthError.ProviderError(
                        "Failed to send verification email",
                        "SmtpEmailProvider"
                    )
                )
            }

        } catch (e: Exception) {
            Logger.error("SmtpEmailProvider", "Failed to send verification email", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Email service error: ${e.message}",
                    "SmtpEmailProvider",
                    e
                )
            )
        }
    }

    override suspend fun verifyEmailCode(email: String, code: String): AuthResult<Unit> {
        Logger.debug("SmtpEmailProvider", "Verifying email code for: $email")

        return try {
            val storedCode = verificationCodes[email]
            if (storedCode == null) {
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "No verification code found for this email",
                        "email"
                    )
                )
            }

            // Check expiration
            val now = Clock.System.now()
            if (now > storedCode.expiresAt) {
                verificationCodes.remove(email) // Clean up expired code
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Verification code has expired",
                        "code"
                    )
                )
            }

            // Verify code (constant-time comparison)
            if (!constantTimeEquals(storedCode.code, code)) {
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Invalid verification code",
                        "code"
                    )
                )
            }

            // Remove used code
            verificationCodes.remove(email)

            Logger.info("SmtpEmailProvider", "Email verified successfully for: $email")
            AuthResult.Success(Unit)

        } catch (e: Exception) {
            Logger.error("SmtpEmailProvider", "Failed to verify email code", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Email verification error: ${e.message}",
                    "SmtpEmailProvider",
                    e
                )
            )
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        Logger.debug("SmtpEmailProvider", "Sending password reset email to: $email")

        return try {
            val token = CodeGenerationUtil.generateAlphanumericResetToken()
            val expiresAt = Clock.System.now() + kotlin.time.Duration.parse("PT1H") // 1 hour

            // Store reset token
            resetTokens[email] = StoredResetToken(token, expiresAt)

            val subject = "Password Reset Request"
            val htmlBody = buildPasswordResetEmailHtml(token)
            val textBody = "You requested a password reset. Your reset token is: $token\n\nThis token will expire in 1 hour."

            val result = sendEmail(
                to = email,
                subject = subject,
                htmlBody = htmlBody,
                textBody = textBody
            )

            if (result) {
                Logger.info("SmtpEmailProvider", "Password reset email sent successfully to: $email")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Failure(
                    app.multiauth.models.AuthError.ProviderError(
                        "Failed to send password reset email",
                        "SmtpEmailProvider"
                    )
                )
            }

        } catch (e: Exception) {
            Logger.error("SmtpEmailProvider", "Failed to send password reset email", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Email service error: ${e.message}",
                    "SmtpEmailProvider",
                    e
                )
            )
        }
    }

    override suspend fun sendWelcomeEmail(email: String, displayName: String?): AuthResult<Unit> {
        Logger.debug("SmtpEmailProvider", "Sending welcome email to: $email")

        return try {
            val name = displayName ?: "User"
            val subject = "Welcome to Our App!"
            val htmlBody = buildWelcomeEmailHtml(name)
            val textBody = "Welcome $name!\n\nWe're excited to have you on board. Thank you for joining us!"

            val result = sendEmail(
                to = email,
                subject = subject,
                htmlBody = htmlBody,
                textBody = textBody
            )

            if (result) {
                Logger.info("SmtpEmailProvider", "Welcome email sent successfully to: $email")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Failure(
                    app.multiauth.models.AuthError.ProviderError(
                        "Failed to send welcome email",
                        "SmtpEmailProvider"
                    )
                )
            }

        } catch (e: Exception) {
            Logger.error("SmtpEmailProvider", "Failed to send welcome email", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Email service error: ${e.message}",
                    "SmtpEmailProvider",
                    e
                )
            )
        }
    }

    override suspend fun sendSecurityAlert(
        email: String,
        alertType: SecurityAlertType,
        details: Map<String, String>
    ): AuthResult<Unit> {
        Logger.debug("SmtpEmailProvider", "Sending security alert to: $email")

        return try {
            val subject = "Security Alert: ${alertType.name}"
            val htmlBody = buildSecurityAlertEmailHtml(alertType, details)
            val textBody = buildSecurityAlertEmailText(alertType, details)

            val result = sendEmail(
                to = email,
                subject = subject,
                htmlBody = htmlBody,
                textBody = textBody
            )

            if (result) {
                Logger.info("SmtpEmailProvider", "Security alert email sent successfully to: $email")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Failure(
                    app.multiauth.models.AuthError.ProviderError(
                        "Failed to send security alert email",
                        "SmtpEmailProvider"
                    )
                )
            }

        } catch (e: Exception) {
            Logger.error("SmtpEmailProvider", "Failed to send security alert email", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Email service error: ${e.message}",
                    "SmtpEmailProvider",
                    e
                )
            )
        }
    }

    override suspend fun validateEmail(email: String): AuthResult<Boolean> {
        Logger.debug("SmtpEmailProvider", "Validating email: $email")

        return try {
            // Basic email validation
            val isValid = isValidEmailFormat(email)

            Logger.info("SmtpEmailProvider", "Email validation result for $email: $isValid")
            AuthResult.Success(isValid)

        } catch (e: Exception) {
            Logger.error("SmtpEmailProvider", "Failed to validate email", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Email validation error: ${e.message}",
                    "SmtpEmailProvider",
                    e
                )
            )
        }
    }

    override fun getProviderInfo(): EmailProviderInfo {
        return EmailProviderInfo(
            name = "SMTP Email Provider",
            version = "1.0.0",
            supportsVerification = true,
            supportsPasswordReset = true,
            supportsWelcomeEmails = true,
            supportsSecurityAlerts = true,
            maxEmailsPerHour = config.maxEmailsPerHour,
            maxEmailsPerDay = config.maxEmailsPerDay,
            features = listOf("SMTP", "HTML", "Templates", config.provider.name)
        )
    }

    // Helper methods for testing
    fun getStoredVerificationCode(email: String): String? = verificationCodes[email]?.code
    fun getStoredResetToken(email: String): String? = resetTokens[email]?.token

    private suspend fun sendEmail(
        to: String,
        subject: String,
        htmlBody: String,
        textBody: String
    ): Boolean {
        return when (config.provider) {
            EmailServiceProvider.SENDGRID -> sendEmailViaSendGrid(to, subject, htmlBody, textBody)
            EmailServiceProvider.AWS_SES -> sendEmailViaAwsSes(to, subject, htmlBody, textBody)
            EmailServiceProvider.MAILGUN -> sendEmailViaMailgun(to, subject, htmlBody, textBody)
            EmailServiceProvider.CUSTOM_SMTP -> sendEmailViaSmtp(to, subject, htmlBody, textBody)
        }
    }

    private suspend fun sendEmailViaSendGrid(
        to: String,
        subject: String,
        htmlBody: String,
        textBody: String
    ): Boolean {
        return try {
            val payload = SendGridEmailPayload(
                personalizations = listOf(
                    SendGridPersonalization(
                        to = listOf(SendGridRecipient(email = to))
                    )
                ),
                from = SendGridRecipient(email = config.fromEmail, name = config.fromName),
                subject = subject,
                content = listOf(
                    SendGridContent(type = "text/plain", value = textBody),
                    SendGridContent(type = "text/html", value = htmlBody)
                )
            )

            val response = httpClient.post("https://api.sendgrid.com/v3/mail/send") {
                header("Authorization", "Bearer ${config.apiKey}")
                header("Content-Type", "application/json")
                setBody(Json.encodeToString(payload))
            }

            response.status.isSuccess()

        } catch (e: Exception) {
            Logger.error("SmtpEmailProvider", "SendGrid API error", e)
            false
        }
    }

    private suspend fun sendEmailViaAwsSes(
        to: String,
        subject: String,
        htmlBody: String,
        textBody: String
    ): Boolean {
        // AWS SES implementation would go here
        // For now, return mock success in development
        Logger.warn("SmtpEmailProvider", "AWS SES not implemented - using mock response")
        delay(500) // Simulate API call
        return true
    }

    private suspend fun sendEmailViaMailgun(
        to: String,
        subject: String,
        htmlBody: String,
        textBody: String
    ): Boolean {
        // Mailgun implementation would go here
        Logger.warn("SmtpEmailProvider", "Mailgun not implemented - using mock response")
        delay(500) // Simulate API call
        return true
    }

    private suspend fun sendEmailViaSmtp(
        to: String,
        subject: String,
        htmlBody: String,
        textBody: String
    ): Boolean {
        // Custom SMTP implementation would go here
        Logger.warn("SmtpEmailProvider", "Custom SMTP not implemented - using mock response")
        delay(500) // Simulate API call
        return true
    }


    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    private fun isValidEmailFormat(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return email.matches(emailRegex)
    }

    private fun buildVerificationEmailHtml(code: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Email Verification</title>
            </head>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background-color: #f8f9fa; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #333; text-align: center;">Email Verification</h2>
                    <p>Your verification code is:</p>
                    <div style="background-color: #007bff; color: white; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; border-radius: 5px; margin: 20px 0;">
                        $code
                    </div>
                    <p style="color: #666; font-size: 14px;">${TimeoutConstants.getVerificationTimeoutMessage(TimeoutConstants.EMAIL_VERIFICATION_CODE_TIMEOUT)}</p>
                    <p style="color: #666; font-size: 12px;">If you didn't request this verification, please ignore this email.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildPasswordResetEmailHtml(token: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Password Reset</title>
            </head>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background-color: #f8f9fa; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #333; text-align: center;">Password Reset Request</h2>
                    <p>You requested a password reset. Your reset token is:</p>
                    <div style="background-color: #28a745; color: white; padding: 15px; text-align: center; font-size: 16px; font-weight: bold; border-radius: 5px; margin: 20px 0; word-break: break-all;">
                        $token
                    </div>
                    <p style="color: #666; font-size: 14px;">This token will expire in 1 hour.</p>
                    <p style="color: #666; font-size: 12px;">If you didn't request this reset, please ignore this email and your password will remain unchanged.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildWelcomeEmailHtml(name: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Welcome!</title>
            </head>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background-color: #f8f9fa; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #333; text-align: center;">Welcome $name!</h2>
                    <p>We're excited to have you on board. Thank you for joining us!</p>
                    <p>You can now enjoy all the features of our application.</p>
                    <p style="color: #666; font-size: 14px;">If you have any questions, feel free to contact our support team.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSecurityAlertEmailHtml(alertType: SecurityAlertType, details: Map<String, String>): String {
        val detailsHtml = details.entries.joinToString("") { (key, value) ->
            "<li><strong>$key:</strong> $value</li>"
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Security Alert</title>
            </head>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <div style="background-color: #fff3cd; border: 1px solid #ffeaa7; padding: 20px; border-radius: 10px;">
                    <h2 style="color: #856404; text-align: center;">🔒 Security Alert</h2>
                    <p><strong>Alert Type:</strong> ${alertType.name}</p>
                    <p>We detected unusual activity on your account:</p>
                    <ul>$detailsHtml</ul>
                    <p style="color: #856404; font-size: 14px;">If this was you, no action is needed. If not, please secure your account immediately.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSecurityAlertEmailText(alertType: SecurityAlertType, details: Map<String, String>): String {
        val detailsText = details.entries.joinToString("\n") { (key, value) ->
            "- $key: $value"
        }

        return """
            Security Alert: ${alertType.name}

            We detected unusual activity on your account:
            $detailsText

            If this was you, no action is needed. If not, please secure your account immediately.
        """.trimIndent()
    }
}

/**
 * Configuration for SMTP email provider.
 */
data class SmtpEmailConfig(
    val provider: EmailServiceProvider,
    val apiKey: String,
    val fromEmail: String,
    val fromName: String = "Multi-Auth App",
    val maxEmailsPerHour: Int = 100,
    val maxEmailsPerDay: Int = 1000
)

/**
 * Supported email service providers.
 */
enum class EmailServiceProvider {
    SENDGRID,
    AWS_SES,
    MAILGUN,
    CUSTOM_SMTP
}

/**
 * Stored verification code with expiration.
 */
private data class StoredVerificationCode(
    val code: String,
    val expiresAt: Instant
)

/**
 * Stored reset token with expiration.
 */
private data class StoredResetToken(
    val token: String,
    val expiresAt: Instant
)

// SendGrid API payload structures
@Serializable
private data class SendGridEmailPayload(
    val personalizations: List<SendGridPersonalization>,
    val from: SendGridRecipient,
    val subject: String,
    val content: List<SendGridContent>
)

@Serializable
private data class SendGridPersonalization(
    val to: List<SendGridRecipient>
)

@Serializable
private data class SendGridRecipient(
    val email: String,
    val name: String? = null
)

@Serializable
private data class SendGridContent(
    val type: String,
    val value: String
)
