package app.multiauth.providers.impl

import app.multiauth.models.AuthResult
import app.multiauth.providers.*
import app.multiauth.providers.VerificationSession
import app.multiauth.util.Logger
import kotlinx.coroutines.delay

/**
 * Mock SMS provider for testing and development purposes.
 * This provider simulates SMS operations without actually sending SMS messages.
 */
class MockSmsProvider(
    private val config: SmsProviderConfig = SmsProviderConfig(),
    private val simulateDelay: Boolean = true,
    private val failureRate: Double = 0.0
) : SmsProvider {
    
    private val sentSms = mutableListOf<MockSms>()
    private val verificationSessions = mutableMapOf<String, VerificationSession>()
    private val verificationCodes = mutableMapOf<String, String>()
    
    override suspend fun sendVerificationCode(phoneNumber: String): AuthResult<String> {
        Logger.debug("MockSmsProvider", "Sending verification code to: $phoneNumber")
        
        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock SMS provider failure",
                    "MockSmsProvider"
                )
            )
        }
        
        if (simulateDelay) {
            delay(1500) // Simulate SMS delivery delay
        }
        
        val code = generateVerificationCode()
        val sessionId = generateSessionId()
        
        verificationCodes[phoneNumber] = code
        
        val session = VerificationSession(
            sessionId = sessionId,
            phoneNumber = phoneNumber,
            expiresAt = Clock.System.now().toEpochMilliseconds() + (5 * 60 * 1000), // 5 minutes
            attemptsRemaining = 3,
            maxAttempts = 3
        )
        verificationSessions[sessionId] = session
        
        val sms = MockSms(
            to = phoneNumber,
            message = "Your verification code is: $code",
            type = SmsType.VERIFICATION,
            sessionId = sessionId
        )
        sentSms.add(sms)
        
        Logger.info("MockSmsProvider", "Verification SMS sent to $phoneNumber with code: $code")
        
        return AuthResult.Success(sessionId)
    }
    
    override suspend fun verifySmsCode(
        phoneNumber: String, 
        code: String, 
        sessionId: String
    ): AuthResult<Unit> {
        Logger.debug("MockSmsProvider", "Verifying SMS code for: $phoneNumber")
        
        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock SMS provider failure",
                    "MockSmsProvider"
                )
            )
        }
        
        if (simulateDelay) {
            delay(800) // Simulate verification delay
        }
        
        val session = verificationSessions[sessionId]
        if (session == null) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "Invalid session ID",
                    "sessionId"
                )
            )
        }
        
        if (session.phoneNumber != phoneNumber) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "Phone number mismatch",
                    "phoneNumber"
                )
            )
        }
        
        if (Clock.System.now().toEpochMilliseconds() > session.expiresAt) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "Verification session expired",
                    "session"
                )
            )
        }
        
        if (session.attemptsRemaining <= 0) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.RateLimitExceeded(
                    "Too many verification attempts"
                )
            )
        }
        
        val expectedCode = verificationCodes[phoneNumber]
        if (expectedCode == null) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "No verification code found for this phone number",
                    "phoneNumber"
                )
            )
        }
        
        if (expectedCode != code) {
            // Decrease attempts remaining - create new instance since properties are immutable
            val updatedSession = session.copy(attemptsRemaining = session.attemptsRemaining - 1)
            verificationSessions[sessionId] = updatedSession
            
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "Invalid verification code. ${updatedSession.attemptsRemaining} attempts remaining.",
                    "code"
                )
            )
        }
        
        // Verification successful - clean up
        verificationCodes.remove(phoneNumber)
        verificationSessions.remove(sessionId)
        
        Logger.info("MockSmsProvider", "SMS verified successfully for: $phoneNumber")
        
        return AuthResult.Success(Unit)
    }
    
    override suspend fun sendSecurityAlert(
        phoneNumber: String,
        alertType: SmsSecurityAlertType,
        details: Map<String, String>
    ): AuthResult<Unit> {
        Logger.debug("MockSmsProvider", "Sending security alert SMS to: $phoneNumber")
        
        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock SMS provider failure",
                    "MockSmsProvider"
                )
            )
        }
        
        if (simulateDelay) {
            delay(1000)
        }
        
        val alertSms = MockSms(
            to = phoneNumber,
            message = "Security Alert: ${alertType.name}. ${details.entries.joinToString { "${it.key}=${it.value}" }}",
            type = SmsType.SECURITY_ALERT
        )
        sentSms.add(alertSms)
        
        Logger.info("MockSmsProvider", "Security alert SMS sent to: $phoneNumber")
        
        return AuthResult.Success(Unit)
    }
    
    override suspend fun validatePhoneNumber(phoneNumber: String): AuthResult<Boolean> {
        Logger.debug("MockSmsProvider", "Validating phone number: $phoneNumber")
        
        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock SMS provider failure",
                    "MockSmsProvider"
                )
            )
        }
        
        if (simulateDelay) {
            delay(300)
        }
        
        // Simple phone number validation (E.164 format)
        val isValid = phoneNumber.startsWith("+") && phoneNumber.length >= 10 && phoneNumber.length <= 15
        
        Logger.info("MockSmsProvider", "Phone number validation result for $phoneNumber: $isValid")
        
        return AuthResult.Success(isValid)
    }
    
    override fun getProviderInfo(): SmsProviderInfo {
        return SmsProviderInfo(
            name = "Mock SMS Provider",
            version = "1.0.0",
            supportsVerification = true,
            supportsSecurityAlerts = true,
            supportsInternationalNumbers = true,
            maxSmsPerHour = 100,
            maxSmsPerDay = 1000,
            supportedCountries = listOf("US", "CA", "GB", "DE", "FR", "JP", "AU"),
            features = listOf("Mock", "Testing", "Development")
        )
    }
    
    override suspend fun getRateLimitInfo(phoneNumber: String): AuthResult<RateLimitInfo> {
        Logger.debug("MockSmsProvider", "Getting rate limit info for: $phoneNumber")
        
        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock SMS provider failure",
                    "MockSmsProvider"
                )
            )
        }
        
        if (simulateDelay) {
            delay(200)
        }
        
        // Mock rate limit info
        val rateLimitInfo = RateLimitInfo(
            remaining = 10,
            resetTime = Clock.System.now().toEpochMilliseconds() + (60 * 60 * 1000), // 1 hour
            limit = 10
        )
        
        Logger.info("MockSmsProvider", "Rate limit info for $phoneNumber: $rateLimitInfo")
        
        return AuthResult.Success(rateLimitInfo)
    }
    
    override suspend fun resendVerificationCode(
        phoneNumber: String, 
        sessionId: String
    ): AuthResult<String> {
        Logger.debug("MockSmsProvider", "Resending verification code to: $phoneNumber")
        
        if (shouldFail()) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ProviderError(
                    "Mock SMS provider failure",
                    "MockSmsProvider"
                )
            )
        }
        
        if (simulateDelay) {
            delay(1200)
        }
        
        val session = verificationSessions[sessionId]
        if (session == null) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "Invalid session ID",
                    "sessionId"
                )
            )
        }
        
        if (session.phoneNumber != phoneNumber) {
            return AuthResult.Failure(
                app.multiauth.models.AuthError.ValidationError(
                    "Phone number mismatch",
                    "phoneNumber"
                )
            )
        }
        
        // Generate new code
        val newCode = generateVerificationCode()
        verificationCodes[phoneNumber] = newCode
        
        // Update session - create new instance since properties are immutable
        val updatedSession = session.copy(
            attemptsRemaining = 3,
            expiresAt = Clock.System.now().toEpochMilliseconds() + (5 * 60 * 1000)
        )
        verificationSessions[sessionId] = updatedSession
        
        val sms = MockSms(
            to = phoneNumber,
            message = "Your new verification code is: $newCode",
            type = SmsType.VERIFICATION,
            sessionId = sessionId
        )
        sentSms.add(sms)
        
        Logger.info("MockSmsProvider", "Verification code resent to $phoneNumber: $newCode")
        
        return AuthResult.Success(sessionId)
    }
    
    // Helper methods for testing
    
    fun getSentSms(): List<MockSms> = sentSms.toList()
    
    fun getVerificationSessions(): Map<String, VerificationSession> = verificationSessions.toMap()
    
    fun getVerificationCodes(): Map<String, String> = verificationCodes.toMap()
    
    fun clearSentSms() {
        sentSms.clear()
    }
    
    fun clearVerificationSessions() {
        verificationSessions.clear()
    }
    
    fun clearVerificationCodes() {
        verificationCodes.clear()
    }
    
    fun addVerificationCode(phoneNumber: String, code: String) {
        verificationCodes[phoneNumber] = code
    }
    
    fun addVerificationSession(session: VerificationSession) {
        verificationSessions[session.sessionId] = session
    }
    
    private fun generateVerificationCode(): String {
        return (100000..999999).random().toString()
    }
    
    private fun generateSessionId(): String {
        return "session_${Clock.System.now().toEpochMilliseconds()}_${(1000..9999).random()}"
    }
    
    private fun shouldFail(): Boolean {
        return kotlin.random.Random.nextDouble() < failureRate
    }
}

/**
 * Mock SMS representation for testing.
 */
data class MockSms(
    val to: String,
    val message: String,
    val type: SmsType,
    val sessionId: String? = null,
            val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Types of SMS messages that can be sent.
 */
enum class SmsType {
    VERIFICATION,
    SECURITY_ALERT
}