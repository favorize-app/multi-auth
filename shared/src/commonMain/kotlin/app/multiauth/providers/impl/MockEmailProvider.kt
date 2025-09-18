@file:OptIn(ExperimentalTime::class)

package app.multiauth.providers.impl


import app.multiauth.models.AuthResult
import app.multiauth.providers.*
import app.multiauth.util.Logger
import app.multiauth.util.CodeGenerationUtil
import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

/**
 * Mock email provider for testing and development purposes.
 * This provider simulates email operations without actually sending emails.
 */
class MockEmailProvider(
    private val config: EmailProviderConfig = EmailProviderConfig(),
    private val simulateDelay: Boolean = true,
    private val failureRate: Double = 0.0
) : EmailProvider {

    private val sentEmails = mutableListOf<MockEmail>()
    private val verificationCodes = mutableMapOf<String, String>()

    override suspend fun sendVerificationEmail(email: String): AuthResult<Unit> {
        Logger.debug("MockEmailProvider", "Sending verification email to: $email")

        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock email provider failure",
                    "MockEmailProvider"
                )
            )
        }

        if (simulateDelay) {
            delay(1000) // Simulate network delay
        }

        val code = CodeGenerationUtil.generateVerificationCode()
        verificationCodes[email] = code

        val mockEmail = MockEmail(
            to = email,
            subject = "Email Verification",
            body = "Your verification code is: $code",
            type = EmailType.VERIFICATION
        )
        sentEmails.add(mockEmail)

        Logger.info("MockEmailProvider", "Verification email sent to $email with code: $code")

        return AuthResult.Success(Unit)
    }

    override suspend fun verifyEmailCode(email: String, code: String): AuthResult<Unit> {
        Logger.debug("MockEmailProvider", "Verifying code for: $email")

        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock email provider failure",
                    "MockEmailProvider"
                )
            )
        }

        if (simulateDelay) {
            delay(500) // Simulate verification delay
        }

        val expectedCode = verificationCodes[email]
        if (expectedCode == null) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "No verification code found for this email",
                    "email"
                )
            )
        }

        if (expectedCode != code) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "Invalid verification code",
                    "code"
                )
            )
        }

        // Remove the used code
        verificationCodes.remove(email)

        Logger.info("MockEmailProvider", "Email verified successfully for: $email")

        return AuthResult.Success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        Logger.debug("MockEmailProvider", "Sending password reset email to: $email")

        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock email provider failure",
                    "MockEmailProvider"
                )
            )
        }

        if (simulateDelay) {
            delay(1000)
        }

        val resetToken = CodeGenerationUtil.generateNumericResetToken()
        val mockEmail = MockEmail(
            to = email,
            subject = "Password Reset",
            body = "Your password reset token is: $resetToken",
            type = EmailType.PASSWORD_RESET
        )
        sentEmails.add(mockEmail)

        Logger.info("MockEmailProvider", "Password reset email sent to: $email")

        return AuthResult.Success(Unit)
    }

    override suspend fun sendWelcomeEmail(email: String, displayName: String?): AuthResult<Unit> {
        Logger.debug("MockEmailProvider", "Sending welcome email to: $email")

        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock email provider failure",
                    "MockEmailProvider"
                )
            )
        }

        if (simulateDelay) {
            delay(800)
        }

        val name = displayName ?: "User"
        val mockEmail = MockEmail(
            to = email,
            subject = "Welcome to Our App!",
            body = "Welcome $name! We're excited to have you on board.",
            type = EmailType.WELCOME
        )
        sentEmails.add(mockEmail)

        Logger.info("MockEmailProvider", "Welcome email sent to: $email")

        return AuthResult.Success(Unit)
    }

    override suspend fun sendSecurityAlert(
        email: String,
        alertType: SecurityAlertType,
        details: Map<String, String>
    ): AuthResult<Unit> {
        Logger.debug("MockEmailProvider", "Sending security alert to: $email")

        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock email provider failure",
                    "MockEmailProvider"
                )
            )
        }

        if (simulateDelay) {
            delay(600)
        }

        val alertEmail = MockEmail(
            to = email,
            subject = "Security Alert: ${alertType.name}",
            body = "Security alert: ${alertType.name}. Details: ${details.entries.joinToString { "${it.key}=${it.value}" }}",
            type = EmailType.SECURITY_ALERT
        )
        sentEmails.add(alertEmail)

        Logger.info("MockEmailProvider", "Security alert email sent to: $email")

        return AuthResult.Success(Unit)
    }

    override suspend fun validateEmail(email: String): AuthResult<Boolean> {
        Logger.debug("MockEmailProvider", "Validating email: $email")

        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock email provider failure",
                    "MockEmailProvider"
                )
            )
        }

        if (simulateDelay) {
            delay(200)
        }

        // Simple email validation
        val isValid = email.contains("@") && email.contains(".") && email.length > 5

        Logger.info("MockEmailProvider", "Email validation result for $email: $isValid")

        return AuthResult.Success(isValid)
    }

    override fun getProviderInfo(): EmailProviderInfo {
        return EmailProviderInfo(
            name = "Mock Email Provider",
            version = "1.0.0",
            supportsVerification = true,
            supportsPasswordReset = true,
            supportsWelcomeEmails = true,
            supportsSecurityAlerts = true,
            maxEmailsPerHour = 1000,
            maxEmailsPerDay = 10000,
            features = listOf("Mock", "Testing", "Development")
        )
    }

    // Helper methods for testing

    fun getSentEmails(): List<MockEmail> = sentEmails.toList()

    fun getVerificationCodes(): Map<String, String> = verificationCodes.toMap()

    fun clearSentEmails() {
        sentEmails.clear()
    }

    fun clearVerificationCodes() {
        verificationCodes.clear()
    }

    fun addVerificationCode(email: String, code: String) {
        verificationCodes[email] = code
    }


    private fun shouldFail(): Boolean {
        return kotlin.random.Random.nextDouble() < failureRate
    }
}

/**
 * Mock email representation for testing.
 */
data class MockEmail(
    val to: String,
    val subject: String,
    val body: String,
    val type: EmailType,
            val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Types of emails that can be sent.
 */
enum class EmailType {
    VERIFICATION,
    PASSWORD_RESET,
    WELCOME,
    SECURITY_ALERT
}
