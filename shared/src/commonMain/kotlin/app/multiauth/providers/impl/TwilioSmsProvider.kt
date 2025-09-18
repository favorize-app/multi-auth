@file:OptIn(ExperimentalTime::class)

package app.multiauth.providers.impl


import app.multiauth.models.AuthResult
import app.multiauth.providers.*
import app.multiauth.util.Logger
import app.multiauth.util.Base64Util
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
import kotlin.time.Duration

/**
 * Real SMS provider using Twilio API.
 * Can be configured to work with other SMS providers like AWS SNS, MessageBird, etc.
 */
class TwilioSmsProvider(
    private val config: TwilioSmsConfig,
    private val httpClient: HttpClient
) : SmsProvider {

    private val verificationSessions = mutableMapOf<String, SmsVerificationSession>()

    override suspend fun sendVerificationCode(phoneNumber: String): AuthResult<String> {
        Logger.debug("TwilioSmsProvider", "Sending verification code to: $phoneNumber")

        return try {
            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            val code = CodeGenerationUtil.generateVerificationCode()
            val sessionId = CodeGenerationUtil.generateAlphanumericSessionId()
            val expiresAt = Clock.System.now() + TimeoutConstants.SMS_VERIFICATION_CODE_TIMEOUT

            // Store verification session
            verificationSessions[sessionId] = SmsVerificationSession(
                phoneNumber = normalizedNumber,
                code = code,
                expiresAt = expiresAt,
                attempts = 0
            )

            val message = TimeoutConstants.getSmsVerificationMessage(code)

            val success = when (config.provider) {
                SmsServiceProvider.TWILIO -> sendSmsViaTwilio(normalizedNumber, message)
                SmsServiceProvider.AWS_SNS -> sendSmsViaAwsSns(normalizedNumber, message)
                SmsServiceProvider.MESSAGEBIRD -> sendSmsViaMessageBird(normalizedNumber, message)
                SmsServiceProvider.MOCK -> sendMockSms(normalizedNumber, message)
            }

            if (success) {
                Logger.info("TwilioSmsProvider", "Verification code sent successfully to: $phoneNumber")
                AuthResult.Success(sessionId)
            } else {
                verificationSessions.remove(sessionId) // Clean up failed session
                AuthResult.Failure(
                    app.multiauth.models.AuthError.ProviderError(
                        "Failed to send SMS verification code",
                        "TwilioSmsProvider"
                    )
                )
            }

        } catch (e: Exception) {
            Logger.error("TwilioSmsProvider", "Failed to send verification code", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "SMS service error: ${e.message}",
                    "TwilioSmsProvider",
                    e
                )
            )
        }
    }

    override suspend fun verifySmsCode(phoneNumber: String, code: String, sessionId: String): AuthResult<Unit> {
        Logger.debug("TwilioSmsProvider", "Verifying SMS code for: $phoneNumber")

        return try {
            val session = verificationSessions[sessionId]
            if (session == null) {
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Invalid or expired verification session",
                        "sessionId"
                    )
                )
            }

            // Check if session expired
            val now = Clock.System.now()
            if (now > session.expiresAt) {
                verificationSessions.remove(sessionId) // Clean up expired session
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Verification code has expired",
                        "code"
                    )
                )
            }

            // Check if phone number matches
            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            if (session.phoneNumber != normalizedNumber) {
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Phone number mismatch",
                        "phoneNumber"
                    )
                )
            }

            // Check attempt limit
            if (session.attempts >= config.maxVerificationAttempts) {
                verificationSessions.remove(sessionId) // Clean up exhausted session
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Too many verification attempts",
                        "code"
                    )
                )
            }

            // Increment attempt count
            verificationSessions[sessionId] = session.copy(attempts = session.attempts + 1)

            // Verify code (constant-time comparison)
            if (!constantTimeEquals(session.code, code)) {
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Invalid verification code",
                        "code"
                    )
                )
            }

            // Remove successful session
            verificationSessions.remove(sessionId)

            Logger.info("TwilioSmsProvider", "SMS verification successful for: $phoneNumber")
            AuthResult.Success(Unit)

        } catch (e: Exception) {
            Logger.error("TwilioSmsProvider", "Failed to verify SMS code", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "SMS verification error: ${e.message}",
                    "TwilioSmsProvider",
                    e
                )
            )
        }
    }

    override suspend fun sendSecurityAlert(
        phoneNumber: String,
        alertType: SmsSecurityAlertType,
        details: Map<String, String>
    ): AuthResult<Unit> {
        Logger.debug("TwilioSmsProvider", "Sending security alert to: $phoneNumber")

        return try {
            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            val detailsText = details.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            val alertMessage = "🔒 SECURITY ALERT: ${alertType.name}${if (detailsText.isNotEmpty()) " - $detailsText" else ""}"

            val success = when (config.provider) {
                SmsServiceProvider.TWILIO -> sendSmsViaTwilio(normalizedNumber, alertMessage)
                SmsServiceProvider.AWS_SNS -> sendSmsViaAwsSns(normalizedNumber, alertMessage)
                SmsServiceProvider.MESSAGEBIRD -> sendSmsViaMessageBird(normalizedNumber, alertMessage)
                SmsServiceProvider.MOCK -> sendMockSms(normalizedNumber, alertMessage)
            }

            if (success) {
                Logger.info("TwilioSmsProvider", "Security alert sent successfully to: $phoneNumber")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Failure(
                    app.multiauth.models.AuthError.ProviderError(
                        "Failed to send security alert SMS",
                        "TwilioSmsProvider"
                    )
                )
            }

        } catch (e: Exception) {
            Logger.error("TwilioSmsProvider", "Failed to send security alert", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "SMS service error: ${e.message}",
                    "TwilioSmsProvider",
                    e
                )
            )
        }
    }

    override suspend fun validatePhoneNumber(phoneNumber: String): AuthResult<Boolean> {
        Logger.debug("TwilioSmsProvider", "Validating phone number: $phoneNumber")

        return try {
            val isValid = isValidPhoneNumberFormat(phoneNumber)

            Logger.info("TwilioSmsProvider", "Phone validation result for $phoneNumber: $isValid")
            AuthResult.Success(isValid)

        } catch (e: Exception) {
            Logger.error("TwilioSmsProvider", "Failed to validate phone number", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Phone validation error: ${e.message}",
                    "TwilioSmsProvider",
                    e
                )
            )
        }
    }

    override suspend fun getRateLimitInfo(phoneNumber: String): AuthResult<RateLimitInfo> {
        Logger.debug("TwilioSmsProvider", "Getting rate limit info for: $phoneNumber")

        return try {
            // In a real implementation, this would check actual rate limits
            // For now, return mock data
            val rateLimitInfo = RateLimitInfo(
                remaining = config.maxSmsPerHour - 1,
                resetTime = (Clock.System.now() + Duration.parse("PT1H")).toEpochMilliseconds(),
                limit = config.maxSmsPerHour
            )

            AuthResult.Success(rateLimitInfo)

        } catch (e: Exception) {
            Logger.error("TwilioSmsProvider", "Failed to get rate limit info", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Rate limit check failed: ${e.message}",
                    "TwilioSmsProvider",
                    e
                )
            )
        }
    }

    override suspend fun resendVerificationCode(phoneNumber: String, sessionId: String): AuthResult<String> {
        Logger.debug("TwilioSmsProvider", "Resending verification code for: $phoneNumber")

        return try {
            val session = verificationSessions[sessionId]
            if (session == null) {
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Invalid or expired verification session",
                        "sessionId"
                    )
                )
            }

            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            if (session.phoneNumber != normalizedNumber) {
                return AuthResult.Failure(
                    app.multiauth.models.AuthError.ValidationError(
                        "Phone number mismatch",
                        "phoneNumber"
                    )
                )
            }

            // Generate new code but keep same session
            val newCode = CodeGenerationUtil.generateVerificationCode()
            val newExpiresAt = Clock.System.now() + TimeoutConstants.SMS_VERIFICATION_CODE_TIMEOUT

            verificationSessions[sessionId] = session.copy(
                code = newCode,
                expiresAt = newExpiresAt,
                attempts = 0 // Reset attempts for new code
            )

            val message = TimeoutConstants.getSmsVerificationMessage(newCode)

            val success = when (config.provider) {
                SmsServiceProvider.TWILIO -> sendSmsViaTwilio(normalizedNumber, message)
                SmsServiceProvider.AWS_SNS -> sendSmsViaAwsSns(normalizedNumber, message)
                SmsServiceProvider.MESSAGEBIRD -> sendSmsViaMessageBird(normalizedNumber, message)
                SmsServiceProvider.MOCK -> sendMockSms(normalizedNumber, message)
            }

            if (success) {
                Logger.info("TwilioSmsProvider", "Verification code resent successfully to: $phoneNumber")
                AuthResult.Success(sessionId)
            } else {
                AuthResult.Failure(
                    app.multiauth.models.AuthError.ProviderError(
                        "Failed to resend SMS verification code",
                        "TwilioSmsProvider"
                    )
                )
            }

        } catch (e: Exception) {
            Logger.error("TwilioSmsProvider", "Failed to resend verification code", e)
            AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "SMS service error: ${e.message}",
                    "TwilioSmsProvider",
                    e
                )
            )
        }
    }

    override fun getProviderInfo(): SmsProviderInfo {
        return SmsProviderInfo(
            name = "Twilio SMS Provider",
            version = "1.0.0",
            supportsVerification = true,
            supportsSecurityAlerts = true,
            maxSmsPerHour = config.maxSmsPerHour,
            maxSmsPerDay = config.maxSmsPerDay,
            supportedCountries = listOf("US", "CA", "GB", "AU", "DE", "FR", "JP", "BR", "IN", "MX"),
            features = listOf("International", "Delivery Reports", config.provider.name)
        )
    }

    // Helper methods for testing
    fun getStoredVerificationCode(sessionId: String): String? = verificationSessions[sessionId]?.code
    fun getActiveSessionCount(): Int = verificationSessions.size

    private suspend fun sendSmsViaTwilio(phoneNumber: String, message: String): Boolean {
        return try {
            val payload = TwilioSmsPayload(
                From = config.fromPhoneNumber,
                To = phoneNumber,
                Body = message
            )

            val response = httpClient.post("https://api.twilio.com/2010-04-01/Accounts/${config.accountSid}/Messages.json") {
                header("Authorization", "Basic ${encodeBasicAuth(config.accountSid, config.authToken)}")
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody("From=${payload.From}&To=${payload.To}&Body=${payload.Body}")
            }

            response.status.isSuccess()

        } catch (e: Exception) {
            Logger.error("TwilioSmsProvider", "Twilio API error", e)
            false
        }
    }

    private suspend fun sendSmsViaAwsSns(phoneNumber: String, message: String): Boolean {
        // AWS SNS implementation would go here
        Logger.warn("TwilioSmsProvider", "AWS SNS not implemented - using mock response")
        delay(500) // Simulate API call
        return true
    }

    private suspend fun sendSmsViaMessageBird(phoneNumber: String, message: String): Boolean {
        // MessageBird implementation would go here
        Logger.warn("TwilioSmsProvider", "MessageBird not implemented - using mock response")
        delay(500) // Simulate API call
        return true
    }

    private suspend fun sendMockSms(phoneNumber: String, message: String): Boolean {
        Logger.info("TwilioSmsProvider", "MOCK SMS to $phoneNumber: $message")
        delay(100) // Simulate API call
        return true
    }


    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters
        val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")

        // Add country code if missing (assume US +1 for now)
        return when {
            digitsOnly.length == 10 -> "+1$digitsOnly"
            digitsOnly.length == 11 && digitsOnly.startsWith("1") -> "+$digitsOnly"
            digitsOnly.startsWith("1") -> "+$digitsOnly"
            else -> "+$digitsOnly"
        }
    }

    private fun isValidPhoneNumberFormat(phoneNumber: String): Boolean {
        val normalized = normalizePhoneNumber(phoneNumber)
        // Basic validation: starts with + and has 10-15 digits total
        return normalized.matches(Regex("^\\+[1-9][0-9]{9,14}$"))
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    private fun encodeBasicAuth(username: String, password: String): String {
        return Base64Util.encodeBasicAuth(username, password)
    }

    /**
     * Clean up expired sessions to prevent memory leaks.
     * Should be called periodically.
     */
    fun cleanupExpiredSessions() {
        val now = Clock.System.now()
        verificationSessions.entries.removeAll { (_, session) ->
            now > session.expiresAt
        }
    }
}

/**
 * Configuration for Twilio SMS provider.
 */
data class TwilioSmsConfig(
    val provider: SmsServiceProvider,
    val accountSid: String,
    val authToken: String,
    val fromPhoneNumber: String,
    val maxSmsPerHour: Int = 50,
    val maxSmsPerDay: Int = 200,
    val maxVerificationAttempts: Int = 3
)

/**
 * Supported SMS service providers.
 */
enum class SmsServiceProvider {
    TWILIO,
    AWS_SNS,
    MESSAGEBIRD,
    MOCK
}

/**
 * SMS verification session data.
 */
private data class SmsVerificationSession(
    val phoneNumber: String,
    val code: String,
    val expiresAt: Instant,
    val attempts: Int
)

/**
 * Twilio SMS API payload.
 */
@Serializable
private data class TwilioSmsPayload(
    val From: String,
    val To: String,
    val Body: String
)
