package app.multiauth.mfa

import app.multiauth.core.AuthEngine
import app.multiauth.events.AuthEvent
import app.multiauth.events.EventBus
import app.multiauth.events.EventBusInstance
import app.multiauth.events.EventMetadata
import app.multiauth.models.User
import app.multiauth.models.AuthError
import app.multiauth.events.Mfa as AuthEventMfa
import app.multiauth.models.Validation
import app.multiauth.models.RateLimitResult
import app.multiauth.util.Logger
import app.multiauth.storage.SecureStorage
import app.multiauth.security.PasswordHasher
import app.multiauth.providers.SmsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
// Platform-specific implementation required
import kotlin.math.pow

/**
 * Manager for Multi-Factor Authentication (MFA) operations.
 * Supports TOTP, SMS verification, and backup codes.
 */
class MfaManager(
    private val authEngine: AuthEngine,
    private val secureStorage: SecureStorage,
    private val smsProvider: SmsProvider,
    private val eventBus: EventBus = EventBusInstance()
) {
    
    private val totpGenerator = TotpGenerator()
    
    private val logger = Logger.getLogger(this::class)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _mfaState = MutableStateFlow<MfaState>(MfaState.Idle)
    val mfaState: StateFlow<MfaState> = _mfaState.asStateFlow()
    
    private val _enabledMethods = MutableStateFlow<Set<MfaMethod>>(emptySet())
    val enabledMethods: StateFlow<Set<MfaMethod>> = _enabledMethods.asStateFlow()
    
    private val _backupCodes = MutableStateFlow<List<String>>(emptyList())
    val backupCodes: StateFlow<List<String>> = _backupCodes.asStateFlow()
    
    /**
     * Enables MFA for a user with the specified method.
     * 
     * @param user The user to enable MFA for
     * @param method The MFA method to enable
     * @return Result indicating success or failure
     */
    suspend fun enableMfa(user: User, method: MfaMethod): Result<Unit> {
        return try {
            logger.info("mfa", "Enabling MFA method ${method.name} for user: ${user.displayName}")
            
            _mfaState.value = MfaState.Enabling(method)
            
            val result = when (method) {
                MfaMethod.TOTP -> enableTotp(user)
                MfaMethod.SMS -> enableSms(user)
                MfaMethod.BACKUP_CODES -> enableBackupCodes(user)
            }
            
            result.onSuccess {
                _enabledMethods.value = _enabledMethods.value + method
                _mfaState.value = MfaState.Idle
                
                // Dispatch success event
                val metadata = EventMetadata(source = "MfaManager")
                eventBus.dispatch(AuthEventMfa.MfaMethodEnabled(user, method), metadata)
                
                logger.info("mfa", "MFA method ${method.name} enabled successfully for user: ${user.displayName}")
            }.onFailure { error ->
                logger.error("mfa", "Failed to enable MFA method ${method.name}", error)
                _mfaState.value = MfaState.Error(error)
                _mfaState.value = MfaState.Idle
                
                // Dispatch failure event
                val authError = if (error is AuthError) error else AuthError.UnknownError(error.message ?: "MFA enable failed", error)
                val metadata = EventMetadata(source = "MfaManager")
                eventBus.dispatch(AuthEventMfa.MfaMethodEnabledFailed(user, method, authError), metadata)
            }
            
            result
            
        } catch (e: Exception) {
            logger.error("mfa", "Unexpected error enabling MFA", e)
            _mfaState.value = MfaState.Error(e)
            _mfaState.value = MfaState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Disables MFA for a user with the specified method.
     * 
     * @param user The user to disable MFA for
     * @param method The MFA method to disable
     * @return Result indicating success or failure
     */
    suspend fun disableMfa(user: User, method: MfaMethod): Result<Unit> {
        return try {
            logger.info("mfa", "Disabling MFA method ${method.name} for user: ${user.displayName}")
            
            _mfaState.value = MfaState.Disabling(method)
            
            val result = when (method) {
                MfaMethod.TOTP -> disableTotp(user)
                MfaMethod.SMS -> disableSms(user)
                MfaMethod.BACKUP_CODES -> disableBackupCodes(user)
            }
            
            result.onSuccess {
                _enabledMethods.value = _enabledMethods.value - method
                _mfaState.value = MfaState.Idle
                
                // Dispatch success event
                val metadata = EventMetadata(source = "MfaManager")
                eventBus.dispatch(AuthEventMfa.MfaMethodDisabled(user, method), metadata)
                
                logger.info("mfa", "MFA method ${method.name} disabled successfully for user: ${user.displayName}")
            }.onFailure { error ->
                logger.error("mfa", "Failed to disable MFA method ${method.name}", error)
                _mfaState.value = MfaState.Error(error)
                _mfaState.value = MfaState.Idle
                
                // Dispatch failure event
                val authError = if (error is AuthError) error else AuthError.UnknownError(error.message ?: "MFA disable failed", error)
                val metadata = EventMetadata(source = "MfaManager")
                eventBus.dispatch(AuthEventMfa.MfaMethodDisabledFailed(user, method, authError), metadata)
            }
            
            result
            
        } catch (e: Exception) {
            logger.error("mfa", "Unexpected error disabling MFA", e)
            _mfaState.value = MfaState.Error(e)
            _mfaState.value = MfaState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Verifies an MFA code for the specified method.
     * 
     * @param user The user to verify MFA for
     * @param method The MFA method to verify
     * @param code The verification code
     * @return Result indicating success or failure
     */
    suspend fun verifyMfaCode(
        user: User,
        method: MfaMethod,
        code: String
    ): Result<Unit> {
        return try {
            logger.info("mfa", "Verifying MFA code for method ${method.name} and user: ${user.displayName}")
            
            _mfaState.value = MfaState.Verifying(method)
            
            val result = when (method) {
                MfaMethod.TOTP -> verifyTotpCode(user, code)
                MfaMethod.SMS -> verifySmsCode(user, code)
                MfaMethod.BACKUP_CODES -> verifyBackupCode(user, code)
            }
            
            result.onSuccess {
                _mfaState.value = MfaState.Idle
                
                // Dispatch success event
                val metadata = EventMetadata(source = "MfaManager")
                eventBus.dispatch(AuthEventMfa.MfaVerificationCompleted(user, method), metadata)
                
                logger.info("mfa", "MFA verification successful for method ${method.name} and user: ${user.displayName}")
            }.onFailure { error ->
                logger.error("mfa", "MFA verification failed for method ${method.name}", error)
                _mfaState.value = MfaState.Error(error)
                _mfaState.value = MfaState.Idle
                
                // Dispatch failure event
                val authError = if (error is AuthError) error else AuthError.UnknownError(error.message ?: "MFA verification failed", error)
                val metadata = EventMetadata(source = "MfaManager")
                eventBus.dispatch(AuthEventMfa.MfaVerificationFailed(user, method, authError), metadata)
            }
            
            result
            
        } catch (e: Exception) {
            logger.error("mfa", "Unexpected error during MFA verification", e)
            _mfaState.value = MfaState.Error(e)
            _mfaState.value = MfaState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Generates new backup codes for a user.
     * 
     * @param user The user to generate backup codes for
     * @return Result with the new backup codes
     */
    suspend fun generateBackupCodes(user: User): Result<List<String>> {
        return try {
            logger.info("mfa", "Generating new backup codes for user: ${user.displayName}")
            
            _mfaState.value = MfaState.GeneratingBackupCodes
            
            val codes = generateSecureBackupCodes()
            _backupCodes.value = codes
            
            _mfaState.value = MfaState.Idle
            
            // Dispatch success event
            val metadata = EventMetadata(source = "MfaManager")
            eventBus.dispatch(AuthEventMfa.MfaBackupCodesGenerated(user, codes), metadata)
            
            logger.info("mfa", "Backup codes generated successfully for user: ${user.displayName}")
            Result.success(codes)
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to generate backup codes", e)
            _mfaState.value = MfaState.Error(e)
            _mfaState.value = MfaState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Checks if MFA is required for a user.
     * 
     * @param user The user to check
     * @return true if MFA is required, false otherwise
     */
    fun isMfaRequired(user: User): Boolean {
        return _enabledMethods.value.isNotEmpty()
    }
    
    /**
     * Gets the available MFA methods for a user.
     * 
     * @param user The user to get methods for
     * @return List of available MFA methods
     */
    fun getAvailableMfaMethods(user: User): List<MfaMethod> {
        return MfaMethod.values().toList()
    }
    
    // Private implementation methods
    
    private suspend fun enableTotp(user: User): Result<Unit> {
        return try {
            // 1. Generate a secret key
            val secret = totpGenerator.generateSecret()
            
            // 2. Store the secret securely
            val totpSettings = UserTotpSettings(
                userId = user.id,
                secret = secret,
                algorithm = "HmacSHA1",
                digits = 6,
                period = 30,
                enabled = true
            )
            
            val storageKey = "totp_settings_${user.id}"
            val stored = secureStorage.store(storageKey, Json.encodeToString(totpSettings))
            
            if (!stored) {
                return Result.failure(Exception("Failed to store TOTP settings securely"))
            }
            
            // 3. Update enabled methods
            val currentMethods = _enabledMethods.value.toMutableSet()
            currentMethods.add(MfaMethod.TOTP)
            _enabledMethods.value = currentMethods
            
            logger.info("mfa", "TOTP enabled successfully for user: ${user.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to enable TOTP", e)
            Result.failure(e)
        }
    }
    
    private suspend fun enableSms(user: User): Result<Unit> {
        return try {
            // 1. Verify the user has a phone number
            val phoneNumber = user.phoneNumber
            if (phoneNumber == null) {
                return Result.failure(IllegalStateException("User must have a verified phone number to enable SMS MFA"))
            }
            
            // 2. Send a verification SMS to confirm the phone number
            when (val result = smsProvider.sendVerificationCode(phoneNumber)) {
                is app.multiauth.models.AuthResult.Success -> {
                    val sessionId = result.data
                    
                    // 3. Store the SMS MFA session
                    val smsSession = SmsMfaSession(
                        userId = user.id,
                        sessionId = sessionId,
                        phoneNumber = phoneNumber
                    )
                    
                    val sessionKey = "mfa_sms_session_${user.id}"
                    val stored = secureStorage.store(sessionKey, Json.encodeToString(smsSession))
                    
                    if (!stored) {
                        return Result.failure(Exception("Failed to store SMS MFA session"))
                    }
                    
                    // Update enabled methods
                    val currentMethods = _enabledMethods.value.toMutableSet()
                    currentMethods.add(MfaMethod.SMS)
                    _enabledMethods.value = currentMethods
                    
                    logger.info("mfa", "SMS MFA enabled successfully for user: ${user.id}")
                    Result.success(Unit)
                }
                is app.multiauth.models.AuthResult.Failure -> {
                    logger.error("mfa", "Failed to send SMS verification for MFA setup: ${result.error.message}")
                    Result.failure(Exception("SMS verification failed: ${result.error.message}"))
                }
            }
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to enable SMS MFA", e)
            Result.failure(e)
        }
    }
    
    private suspend fun enableBackupCodes(user: User): Result<Unit> {
        return try {
            // 1. Generate secure backup codes
            val codes = generateSecureBackupCodes()
            
            // 2. Hash and store them securely
            val hashedCodes = codes.map { simpleHash(it) }
            val backupCodeData = UserBackupCodes(
                userId = user.id,
                hashedCodes = hashedCodes
            )
            
            val storageKey = "backup_codes_${user.id}"
            val stored = secureStorage.store(storageKey, Json.encodeToString(backupCodeData))
            
            if (!stored) {
                return Result.failure(Exception("Failed to store backup codes securely"))
            }
            
            // 3. Present them to the user once (store in memory temporarily)
            _backupCodes.value = codes
            
            // Update enabled methods
            val currentMethods = _enabledMethods.value.toMutableSet()
            currentMethods.add(MfaMethod.BACKUP_CODES)
            _enabledMethods.value = currentMethods
            
            logger.info("mfa", "Backup codes enabled successfully for user: ${user.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to enable backup codes", e)
            Result.failure(e)
        }
    }
    
    private suspend fun disableTotp(user: User): Result<Unit> {
        return try {
            // 1. Remove the TOTP secret from secure storage
            val storageKey = "totp_settings_${user.id}"
            val removed = secureStorage.remove(storageKey)
            
            if (!removed) {
                logger.warn("mfa", "TOTP settings not found for user: ${user.id}")
            }
            
            // 2. Update enabled methods
            val currentMethods = _enabledMethods.value.toMutableSet()
            currentMethods.remove(MfaMethod.TOTP)
            _enabledMethods.value = currentMethods
            
            logger.info("mfa", "TOTP disabled successfully for user: ${user.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to disable TOTP", e)
            Result.failure(e)
        }
    }
    
    private suspend fun disableSms(user: User): Result<Unit> {
        return try {
            // 1. Remove any active SMS MFA session
            val sessionKey = "mfa_sms_session_${user.id}"
            val removed = secureStorage.remove(sessionKey)
            
            if (!removed) {
                logger.warn("mfa", "No SMS MFA session found for user: ${user.id}")
            }
            
            // 2. Update enabled methods
            val currentMethods = _enabledMethods.value.toMutableSet()
            currentMethods.remove(MfaMethod.SMS)
            _enabledMethods.value = currentMethods
            
            logger.info("mfa", "SMS MFA disabled successfully for user: ${user.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to disable SMS MFA", e)
            Result.failure(e)
        }
    }
    
    private suspend fun disableBackupCodes(user: User): Result<Unit> {
        return try {
            // 1. Remove all backup codes from secure storage
            val storageKey = "backup_codes_${user.id}"
            val removed = secureStorage.remove(storageKey)
            
            if (!removed) {
                logger.warn("mfa", "No backup codes found for user: ${user.id}")
            }
            
            // 2. Clear in-memory state
            _backupCodes.value = emptyList()
            
            // 3. Update enabled methods
            val currentMethods = _enabledMethods.value.toMutableSet()
            currentMethods.remove(MfaMethod.BACKUP_CODES)
            _enabledMethods.value = currentMethods
            
            logger.info("mfa", "Backup codes disabled successfully for user: ${user.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("mfa", "Failed to disable backup codes", e)
            Result.failure(e)
        }
    }
    
    private suspend fun verifyTotpCode(user: User, code: String): Result<Unit> {
        return try {
            // 1. Retrieve the user's TOTP secret
            val storageKey = "totp_settings_${user.id}"
            val settingsJson = secureStorage.retrieve(storageKey)
            
            if (settingsJson == null) {
                return Result.failure(IllegalStateException("TOTP not enabled for this user"))
            }
            
            val totpSettings = Json.decodeFromString<UserTotpSettings>(settingsJson)
            
            if (!totpSettings.enabled) {
                return Result.failure(IllegalStateException("TOTP is disabled for this user"))
            }
            
            // 2. Validate the provided TOTP code using the real generator
            val isValid = totpGenerator.validateTotp(totpSettings.secret, code, totpSettings.algorithm)
            
            if (isValid) {
                logger.info("mfa", "TOTP verification successful for user: ${user.id}")
                Result.success(Unit)
            } else {
                logger.warn("mfa", "TOTP verification failed for user: ${user.id}")
                Result.failure(IllegalArgumentException("Invalid TOTP code"))
            }
            
        } catch (e: Exception) {
            logger.error("mfa", "TOTP verification error", e)
            Result.failure(e)
        }
    }
    
    private suspend fun verifySmsCode(user: User, code: String): Result<Unit> {
        return try {
            // Get user's phone number
            val phoneNumber = user.phoneNumber
            if (phoneNumber == null) {
                return Result.failure(IllegalStateException("No phone number associated with this user"))
            }
            
            // 1. Retrieve the stored SMS verification session
            val sessionKey = "mfa_sms_session_${user.id}"
            val sessionJson = secureStorage.retrieve(sessionKey)
            
            if (sessionJson == null) {
                return Result.failure(IllegalStateException("No active SMS MFA session found"))
            }
            
            val smsSession = Json.decodeFromString<SmsMfaSession>(sessionJson)
            
            // 2. Use the real SMS provider to verify the code
            when (val result = smsProvider.verifySmsCode(phoneNumber, code, smsSession.sessionId)) {
                is app.multiauth.models.AuthResult.Success -> {
                    // Remove the used session
                    secureStorage.remove(sessionKey)
                    
                    logger.info("mfa", "SMS MFA verification successful for user: ${user.id}")
                    Result.success(Unit)
                }
                is app.multiauth.models.AuthResult.Failure -> {
                    logger.warn("mfa", "SMS MFA verification failed for user: ${user.id}")
                    Result.failure(Exception("SMS verification failed: ${result.error.message}"))
                }
            }
            
        } catch (e: Exception) {
            logger.error("mfa", "SMS MFA verification error", e)
            Result.failure(e)
        }
    }
    
    private suspend fun verifyBackupCode(user: User, code: String): Result<Unit> {
        return try {
            // 1. Retrieve stored backup codes
            val storageKey = "backup_codes_${user.id}"
            val codesJson = secureStorage.retrieve(storageKey)
            
            if (codesJson == null) {
                return Result.failure(IllegalStateException("No backup codes found for this user"))
            }
            
            val backupCodeData = Json.decodeFromString<UserBackupCodes>(codesJson)
            
            // 2. Hash the provided backup code and compare with stored hashes
            val providedCodeHash = simpleHash(code)
            
            var codeFound = false
            val remainingCodes = backupCodeData.hashedCodes.filter { storedHash ->
                if (storedHash == providedCodeHash) {
                    codeFound = true
                    false // Remove this code from the list
                } else {
                    true // Keep this code
                }
            }
            
            if (codeFound) {
                // 3. Update stored backup codes (remove the used one)
                val updatedBackupCodes = backupCodeData.copy(hashedCodes = remainingCodes)
                secureStorage.store(storageKey, Json.encodeToString(updatedBackupCodes))
                
                // Update in-memory state
                _backupCodes.value = remainingCodes.map { "***USED***" } // Don't expose actual codes
                
                logger.info("mfa", "Backup code verification successful for user: ${user.id}")
                Result.success(Unit)
            } else {
                logger.warn("mfa", "Backup code verification failed for user: ${user.id}")
                Result.failure(IllegalArgumentException("Invalid backup code"))
            }
            
        } catch (e: Exception) {
            logger.error("mfa", "Backup code verification error", e)
            Result.failure(e)
        }
    }
    
    private fun generateSecureBackupCodes(): List<String> {
        val codes = mutableListOf<String>()
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        
        repeat(10) {
            val code = buildString {
                repeat(8) {
                    append(charset.random())
                }
            }
            codes.add(code)
        }
        
        return codes
    }
    
    /**
     * Simple hash function for backup codes.
     */
    private fun simpleHash(input: String): String {
        // Use SHA256 for backup code hashing
        val sha256 = org.kotlincrypto.hash.sha2.SHA256()
        sha256.update(input.encodeToByteArray())
        val hash = sha256.digest()
        return hash.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}

/**
 * Represents the state of MFA operations.
 */
sealed class MfaState {
    object Idle : MfaState()
    data class Enabling(val method: MfaMethod) : MfaState()
    data class Disabling(val method: MfaMethod) : MfaState()
    data class Verifying(val method: MfaMethod) : MfaState()
    object GeneratingBackupCodes : MfaState()
    data class Error(val error: Throwable) : MfaState()
}

/**
 * Represents available MFA methods.
 */
enum class MfaMethod {
    TOTP,
    SMS,
    BACKUP_CODES
}

/**
 * TOTP settings stored securely for each user.
 */
@Serializable
data class UserTotpSettings(
    val userId: String,
    val secret: String,
    val algorithm: String = "HmacSHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val enabled: Boolean = true,
    val createdAt: @kotlinx.serialization.Contextual kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
)

/**
 * Backup codes stored securely for each user.
 */
@Serializable
data class UserBackupCodes(
    val userId: String,
    val hashedCodes: List<String>, // Store as hashed strings for simplicity
    val createdAt: @kotlinx.serialization.Contextual kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now(),
    val lastUsedAt: @kotlinx.serialization.Contextual kotlinx.datetime.Instant? = null
)

/**
 * SMS MFA session data.
 */
@Serializable
data class SmsMfaSession(
    val userId: String,
    val sessionId: String,
    val phoneNumber: String,
    val createdAt: @kotlinx.serialization.Contextual kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
)