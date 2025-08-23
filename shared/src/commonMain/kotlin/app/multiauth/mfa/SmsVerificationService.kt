package app.multiauth.mfa

import app.multiauth.models.User
import app.multiauth.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Service for handling SMS-based MFA verification.
 * Manages SMS code generation, sending, and validation.
 */
class SmsVerificationService {
    
    private val logger = Logger.getLogger(this::class)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        private const val SMS_CODE_LENGTH = 6
        private const val SMS_CODE_EXPIRY_MINUTES = 10L
        private const val SMS_RESEND_COOLDOWN_SECONDS = 60L
        private const val MAX_ATTEMPTS = 3
    }
    
    private val _verificationState = MutableStateFlow<SmsVerificationState>(SmsVerificationState.Idle)
    val verificationState: StateFlow<SmsVerificationState> = _verificationState.asStateFlow()
    
    private val _pendingVerifications = mutableMapOf<String, SmsVerification>()
    private val _userAttempts = mutableMapOf<String, Int>()
    
    /**
     * Initiates SMS verification for a user.
     * 
     * @param user The user to send SMS verification to
     * @param phoneNumber The phone number to send the SMS to
     * @return Result indicating success or failure
     */
    suspend fun initiateVerification(user: User, phoneNumber: String): Result<Unit> {
        return try {
            logger.info("Initiating SMS verification for user: ${user.displayName}, phone: $phoneNumber")
            
            // Check if user has exceeded attempt limits
            val attempts = _userAttempts[user.id] ?: 0
            if (attempts >= MAX_ATTEMPTS) {
                return Result.failure(SmsVerificationException("Maximum verification attempts exceeded"))
            }
            
            _verificationState.value = SmsVerificationState.SendingCode
            
            // Generate verification code
            val code = generateSmsCode()
            val expiryTime = Instant.now().plus(SmsVerificationService.SMS_CODE_EXPIRY_MINUTES, ChronoUnit.MINUTES)
            
            val verification = SmsVerification(
                userId = user.id,
                phoneNumber = phoneNumber,
                code = code,
                expiryTime = expiryTime,
                attempts = 0,
                createdAt = Instant.now()
            )
            
            _pendingVerifications[user.id] = verification
            
            // Simulate sending SMS
            val sendResult = sendSms(phoneNumber, code)
            
            sendResult.onSuccess {
                _verificationState.value = SmsVerificationState.CodeSent(phoneNumber)
                logger.info("SMS verification code sent successfully to: $phoneNumber")
            }.onFailure { error ->
                _verificationState.value = SmsVerificationState.Error(error)
                _pendingVerifications.remove(user.id)
                logger.error("Failed to send SMS verification code", error)
            }
            
            sendResult
            
        } catch (e: Exception) {
            logger.error("Unexpected error during SMS verification initiation", e)
            _verificationState.value = SmsVerificationState.Error(e)
            Result.failure(e)
        }
    }
    
    /**
     * Verifies an SMS code for a user.
     * 
     * @param user The user to verify the code for
     * @param code The SMS verification code
     * @return Result indicating success or failure
     */
    suspend fun verifyCode(user: User, code: String): Result<Unit> {
        return try {
            logger.info("Verifying SMS code for user: ${user.displayName}")
            
            val verification = _pendingVerifications[user.id]
            if (verification == null) {
                return Result.failure(SmsVerificationException("No pending verification found"))
            }
            
            // Check if code has expired
            if (Instant.now().isAfter(verification.expiryTime)) {
                _pendingVerifications.remove(user.id)
                return Result.failure(SmsVerificationException("Verification code has expired"))
            }
            
            // Check if maximum attempts exceeded
            if (verification.attempts >= MAX_ATTEMPTS) {
                _pendingVerifications.remove(user.id)
                _userAttempts[user.id] = (_userAttempts[user.id] ?: 0) + 1
                return Result.failure(SmsVerificationException("Maximum verification attempts exceeded"))
            }
            
            // Increment attempt counter
            verification.attempts++
            
            // Verify the code
            if (verification.code == code) {
                // Success - remove verification and reset attempts
                _pendingVerifications.remove(user.id)
                _userAttempts.remove(user.id)
                
                _verificationState.value = SmsVerificationState.VerificationSuccessful
                logger.info("SMS verification successful for user: ${user.displayName}")
                
                Result.success(Unit)
            } else {
                // Failed attempt
                if (verification.attempts >= MAX_ATTEMPTS) {
                    _pendingVerifications.remove(user.id)
                    _userAttempts[user.id] = (_userAttempts[user.id] ?: 0) + 1
                    _verificationState.value = SmsVerificationState.Error(
                        SmsVerificationException("Maximum verification attempts exceeded")
                    )
                } else {
                    _verificationState.value = SmsVerificationState.CodeSent(verification.phoneNumber)
                }
                
                Result.failure(SmsVerificationException("Invalid verification code"))
            }
            
        } catch (e: Exception) {
            logger.error("Unexpected error during SMS code verification", e)
            _verificationState.value = SmsVerificationState.Error(e)
            Result.failure(e)
        }
    }
    
    /**
     * Resends the SMS verification code.
     * 
     * @param user The user to resend the code to
     * @return Result indicating success or failure
     */
    suspend fun resendCode(user: User): Result<Unit> {
        return try {
            logger.info("Resending SMS verification code for user: ${user.displayName}")
            
            val verification = _pendingVerifications[user.id]
            if (verification == null) {
                return Result.failure(SmsVerificationException("No pending verification found"))
            }
            
            // Check cooldown period
            val timeSinceLastSend = ChronoUnit.SECONDS.between(verification.createdAt, Instant.now())
            if (timeSinceLastSend < SMS_RESEND_COOLDOWN_SECONDS) {
                val remainingTime = SMS_RESEND_COOLDOWN_SECONDS - timeSinceLastSend
                return Result.failure(SmsVerificationException("Please wait $remainingTime seconds before requesting another code"))
            }
            
            _verificationState.value = SmsVerificationState.SendingCode
            
            // Generate new code
            val newCode = generateSmsCode()
            val newExpiryTime = Instant.now().plus(SMS_CODE_EXPIRY_MINUTES, ChronoUnit.MINUTES)
            
            verification.code = newCode
            verification.expiryTime = newExpiryTime
            verification.createdAt = Instant.now()
            
            // Simulate sending SMS
            val sendResult = sendSms(verification.phoneNumber, newCode)
            
            sendResult.onSuccess {
                _verificationState.value = SmsVerificationState.CodeSent(verification.phoneNumber)
                logger.info("SMS verification code resent successfully to: ${verification.phoneNumber}")
            }.onFailure { error ->
                _verificationState.value = SmsVerificationState.Error(error)
                logger.error("Failed to resend SMS verification code", error)
            }
            
            sendResult
            
        } catch (e: Exception) {
            logger.error("Unexpected error during SMS code resend", e)
            _verificationState.value = SmsVerificationState.Error(e)
            Result.failure(e)
        }
    }
    
    /**
     * Cancels the current SMS verification for a user.
     * 
     * @param user The user to cancel verification for
     */
    fun cancelVerification(user: User) {
        logger.info("Cancelling SMS verification for user: ${user.displayName}")
        _pendingVerifications.remove(user.id)
        _verificationState.value = SmsVerificationState.Idle
    }
    
    /**
     * Gets the remaining time until the verification code expires.
     * 
     * @param user The user to check
     * @return Seconds remaining until expiry, or null if no pending verification
     */
    fun getTimeRemaining(user: User): Long? {
        val verification = _pendingVerifications[user.id] ?: return null
        
        val remaining = ChronoUnit.SECONDS.between(Instant.now(), verification.expiryTime)
        return if (remaining > 0) remaining else 0
    }
    
    /**
     * Gets the remaining attempts for a user.
     * 
     * @param user The user to check
     * @return Remaining attempts, or null if no pending verification
     */
    fun getRemainingAttempts(user: User): Int? {
        val verification = _pendingVerifications[user.id] ?: return null
        return MAX_ATTEMPTS - verification.attempts
    }
    
    /**
     * Checks if a user has a pending verification.
     * 
     * @param user The user to check
     * @return true if there's a pending verification, false otherwise
     */
    fun hasPendingVerification(user: User): Boolean {
        return _pendingVerifications.containsKey(user.id)
    }
    
    // Private implementation methods
    
    private fun generateSmsCode(): String {
        val random = SecureRandom()
        return buildString {
            repeat(SMS_CODE_LENGTH) {
                append(random.nextInt(10))
            }
        }
    }
    
    private suspend fun sendSms(phoneNumber: String, code: String): Result<Unit> {
        return try {
            // Simulate SMS sending delay
            delay(1000)
            
            // In a real implementation, this would:
            // 1. Integrate with SMS service provider (Twilio, AWS SNS, etc.)
            // 2. Send the actual SMS message
            // 3. Handle delivery status and errors
            
            logger.info("SMS sent to $phoneNumber with code: $code")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("Failed to send SMS to $phoneNumber", e)
            Result.failure(e)
        }
    }
}

/**
 * Represents the state of SMS verification.
 */
sealed class SmsVerificationState {
    object Idle : SmsVerificationState()
    object SendingCode : SmsVerificationState()
    data class CodeSent(val phoneNumber: String) : SmsVerificationState()
    object VerificationSuccessful : SmsVerificationState()
    data class Error(val error: Throwable) : SmsVerificationState()
}

/**
 * Represents an SMS verification session.
 */
data class SmsVerification(
    val userId: String,
    val phoneNumber: String,
    var code: String,
    val expiryTime: Instant,
    var attempts: Int,
    val createdAt: Instant
)

/**
 * Exception thrown during SMS verification operations.
 */
class SmsVerificationException(message: String) : Exception(message)