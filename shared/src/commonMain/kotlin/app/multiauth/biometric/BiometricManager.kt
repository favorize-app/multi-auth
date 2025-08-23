package app.multiauth.biometric

import app.multiauth.core.AuthEngine
import app.multiauth.events.AuthEvent
import app.multiauth.events.EventBus
import app.multiauth.models.User
import app.multiauth.platform.Platform
import app.multiauth.platform.PlatformUtils
import app.multiauth.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manager for biometric authentication across different platforms.
 * Supports fingerprint, face recognition, and other biometric methods.
 */
class BiometricManager(
    private val authEngine: AuthEngine,
    private val eventBus: EventBus = EventBus.getInstance()
) {
    
    private val logger = Logger.getLogger(this::class)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _biometricState = MutableStateFlow<BiometricState>(BiometricState.Idle)
    val biometricState: StateFlow<BiometricState> = _biometricState.asStateFlow()
    
    private val _isBiometricAvailable = MutableStateFlow<Boolean>(false)
    val isBiometricAvailable: StateFlow<Boolean> = _isBiometricAvailable.asStateFlow()
    
    private val _biometricType = MutableStateFlow<BiometricType?>(null)
    val biometricType: StateFlow<BiometricType?> = _biometricType.asStateFlow()
    
    init {
        scope.launch {
            checkBiometricAvailability()
        }
    }
    
    /**
     * Checks if biometric authentication is available on the current device.
     * 
     * @return Result indicating availability and supported types
     */
    suspend fun checkBiometricAvailability(): Result<BiometricAvailability> {
        return try {
            logger.info("biometrics", "Checking biometric availability")
            
            if (!PlatformUtils.supportsFeature(app.multiauth.platform.PlatformFeature.BIOMETRICS)) {
                logger.info("Biometrics not supported on current platform: ${PlatformUtils.currentPlatform}")
                _isBiometricAvailable.value = false
                _biometricType.value = null
                return Result.success(BiometricAvailability.NotSupported)
            }
            
            val platformBiometric = getPlatformBiometricImplementation()
            val availability = platformBiometric.checkAvailability()
            
            availability.onSuccess { result ->
                _isBiometricAvailable.value = result.isAvailable
                _biometricType.value = result.supportedTypes.firstOrNull()
                
                logger.info("Biometric availability: ${result.isAvailable}, types: ${result.supportedTypes}")
                
                if (result.isAvailable) {
                    eventBus.dispatch(AuthEvent.Biometric.BiometricAvailable(result.supportedTypes))
                }
            }.onFailure { error ->
                logger.error("Failed to check biometric availability", error)
                _isBiometricAvailable.value = false
                _biometricType.value = null
            }
            
            availability
            
        } catch (e: Exception) {
            logger.error("Unexpected error checking biometric availability", e)
            _isBiometricAvailable.value = false
            _biometricType.value = null
            Result.failure(e)
        }
    }
    
    /**
     * Authenticates a user using biometric credentials.
     * 
     * @param promptMessage Optional custom prompt message
     * @param cancelMessage Optional custom cancel message
     * @return Result with the authenticated user
     */
    suspend fun authenticateWithBiometric(
        promptMessage: String = "Authenticate using biometrics",
        cancelMessage: String = "Cancel"
    ): Result<User> {
        return try {
            logger.info("biometrics", "Starting biometric authentication")
            
            if (!_isBiometricAvailable.value) {
                logger.warn("Biometric authentication not available")
                return Result.failure(IllegalStateException("Biometric authentication not available"))
            }
            
            _biometricState.value = BiometricState.Authenticating
            
            val platformBiometric = getPlatformBiometricImplementation()
            val result = platformBiometric.authenticate(promptMessage, cancelMessage)
            
            result.onSuccess { user ->
                logger.info("biometrics", "Biometric authentication successful for user: ${user.displayName}")
                _biometricState.value = BiometricState.Success(user)
                _biometricState.value = BiometricState.Idle
                
                // Dispatch success event
                eventBus.dispatch(AuthEvent.Biometric.BiometricAuthenticationCompleted(user))
                
                Result.success(user)
            }.onFailure { error ->
                logger.error("Biometric authentication failed", error)
                _biometricState.value = BiometricState.Error(error)
                _biometricState.value = BiometricState.Idle
                
                // Dispatch failure event
                eventBus.dispatch(AuthEvent.Biometric.BiometricAuthenticationFailed(error))
                
                Result.failure(error)
            }
            
        } catch (e: Exception) {
            logger.error("Unexpected error during biometric authentication", e)
            _biometricState.value = BiometricState.Error(e)
            _biometricState.value = BiometricState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Enables biometric authentication for the current user.
     * 
     * @param user The user to enable biometric authentication for
     * @return Result indicating success or failure
     */
    suspend fun enableBiometric(user: User): Result<Unit> {
        return try {
            logger.info("biometrics", "Enabling biometric authentication for user: ${user.displayName}")
            
            if (!_isBiometricAvailable.value) {
                logger.warn("Biometric authentication not available")
                return Result.failure(IllegalStateException("Biometric authentication not available"))
            }
            
            _biometricState.value = BiometricState.Enabling
            
            val platformBiometric = getPlatformBiometricImplementation()
            val result = platformBiometric.enableBiometric(user)
            
            result.onSuccess {
                logger.info("Biometric authentication enabled successfully for user: ${user.displayName}")
                _biometricState.value = BiometricState.Idle
                
                // Dispatch success event
                eventBus.dispatch(AuthEvent.Biometric.BiometricEnabled(user))
                
                Result.success(Unit)
            }.onFailure { error ->
                logger.error("Failed to enable biometric authentication", error)
                _biometricState.value = BiometricState.Error(error)
                _biometricState.value = BiometricState.Idle
                
                // Dispatch failure event
                eventBus.dispatch(AuthEvent.Biometric.BiometricEnableFailed(error))
                
                Result.failure(error)
            }
            
        } catch (e: Exception) {
            logger.error("Unexpected error enabling biometric authentication", e)
            _biometricState.value = BiometricState.Error(e)
            _biometricState.value = BiometricState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Disables biometric authentication for the current user.
     * 
     * @param user The user to disable biometric authentication for
     * @return Result indicating success or failure
     */
    suspend fun disableBiometric(user: User): Result<Unit> {
        return try {
            logger.info("biometrics", "Disabling biometric authentication for user: ${user.displayName}")
            
            _biometricState.value = BiometricState.Disabling
            
            val platformBiometric = getPlatformBiometricImplementation()
            val result = platformBiometric.disableBiometric(user)
            
            result.onSuccess {
                logger.info("Biometric authentication disabled successfully for user: ${user.displayName}")
                _biometricState.value = BiometricState.Idle
                
                // Dispatch success event
                eventBus.dispatch(AuthEvent.Biometric.BiometricDisabled(user))
                
                Result.success(Unit)
            }.onFailure { error ->
                logger.error("Failed to disable biometric authentication", error)
                _biometricState.value = BiometricState.Error(error)
                _biometricState.value = BiometricState.Idle
                
                // Dispatch failure event
                eventBus.dispatch(AuthEvent.Biometric.BiometricDisableFailed(error))
                
                Result.failure(error)
            }
            
        } catch (e: Exception) {
            logger.error("Unexpected error disabling biometric authentication", e)
            _biometricState.value = BiometricState.Error(e)
            _biometricState.value = BiometricState.Idle
            Result.failure(e)
        }
    }
    
    /**
     * Gets the platform-specific biometric implementation.
     * 
     * @return PlatformBiometric implementation
     */
    private fun getPlatformBiometricImplementation(): PlatformBiometric {
        return when (PlatformUtils.currentPlatform) {
            Platform.ANDROID -> AndroidBiometricProvider()
            Platform.IOS -> IOSBiometricProvider()
            Platform.WEB -> WebBiometricProvider()
            Platform.DESKTOP -> DesktopBiometricProvider()
            Platform.UNKNOWN -> throw UnsupportedOperationException("Unknown platform")
        }
    }
}

/**
 * Represents the state of biometric authentication.
 */
sealed class BiometricState {
    object Idle : BiometricState()
    object Authenticating : BiometricState()
    object Enabling : BiometricState()
    object Disabling : BiometricState()
    data class Success(val user: User) : BiometricState()
    data class Error(val error: Throwable) : BiometricState()
}

/**
 * Represents the type of biometric authentication available.
 */
enum class BiometricType {
    FINGERPRINT,
    FACE_RECOGNITION,
    IRIS_SCAN,
    VOICE_RECOGNITION,
    UNKNOWN
}

/**
 * Represents the availability of biometric authentication.
 */
data class BiometricAvailability(
    val isAvailable: Boolean,
    val supportedTypes: List<BiometricType>,
    val errorMessage: String? = null
) {
    companion object {
        val NotSupported = BiometricAvailability(
            isAvailable = false,
            supportedTypes = emptyList(),
            errorMessage = "Biometric authentication not supported on this platform"
        )
    }
}

/**
 * Interface for platform-specific biometric implementations.
 */
interface PlatformBiometric {
    
    /**
     * Checks if biometric authentication is available on the device.
     * 
     * @return Result with biometric availability information
     */
    suspend fun checkAvailability(): Result<BiometricAvailability>
    
    /**
     * Authenticates a user using biometric credentials.
     * 
     * @param promptMessage Custom prompt message
     * @param cancelMessage Custom cancel message
     * @return Result with the authenticated user
     */
    suspend fun authenticate(
        promptMessage: String,
        cancelMessage: String
    ): Result<User>
    
    /**
     * Enables biometric authentication for a user.
     * 
     * @param user The user to enable biometric authentication for
     * @return Result indicating success or failure
     */
    suspend fun enableBiometric(user: User): Result<Unit>
    
    /**
     * Disables biometric authentication for a user.
     * 
     * @param user The user to disable biometric authentication for
     * @return Result indicating success or failure
     */
    suspend fun disableBiometric(user: User): Result<Unit>
}

/**
 * Android-specific biometric implementation.
 * This will be implemented in the Android-specific source set.
 */
class AndroidBiometricProvider : PlatformBiometric {
    
    override suspend fun checkAvailability(): Result<BiometricAvailability> {
        throw UnsupportedOperationException("Android biometric not implemented in common module")
    }
    
    override suspend fun authenticate(
        promptMessage: String,
        cancelMessage: String
    ): Result<User> {
        throw UnsupportedOperationException("Android biometric not implemented in common module")
    }
    
    override suspend fun enableBiometric(user: User): Result<Unit> {
        throw UnsupportedOperationException("Android biometric not implemented in common module")
    }
    
    override suspend fun disableBiometric(user: User): Result<Unit> {
        throw UnsupportedOperationException("Android biometric not implemented in common module")
    }
}

/**
 * iOS-specific biometric implementation.
 * This will be implemented in the iOS-specific source set.
 */
class IOSBiometricProvider : PlatformBiometric {
    
    override suspend fun checkAvailability(): Result<BiometricAvailability> {
        throw UnsupportedOperationException("iOS biometric not implemented in common module")
    }
    
    override suspend fun authenticate(
        promptMessage: String,
        cancelMessage: String
    ): Result<User> {
        throw UnsupportedOperationException("iOS biometric not implemented in common module")
    }
    
    override suspend fun enableBiometric(user: User): Result<Unit> {
        throw UnsupportedOperationException("iOS biometric not implemented in common module")
    }
    
    override suspend fun disableBiometric(user: User): Result<Unit> {
        throw UnsupportedOperationException("iOS biometric not implemented in common module")
    }
}

/**
 * Web-specific biometric implementation.
 * This will be implemented in the JavaScript-specific source set.
 */
class WebBiometricProvider : PlatformBiometric {
    
    override suspend fun checkAvailability(): Result<BiometricAvailability> {
        throw UnsupportedOperationException("Web biometric not implemented in common module")
    }
    
    override suspend fun authenticate(
        promptMessage: String,
        cancelMessage: String
    ): Result<User> {
        throw UnsupportedOperationException("Web biometric not implemented in common module")
    }
    
    override suspend fun enableBiometric(user: User): Result<Unit> {
        throw UnsupportedOperationException("Web biometric not implemented in common module")
    }
    
    override suspend fun disableBiometric(user: User): Result<Unit> {
        throw UnsupportedOperationException("Web biometric not implemented in common module")
    }
}

/**
 * Desktop-specific biometric implementation.
 * This will be implemented in the JVM-specific source set.
 */
class DesktopBiometricProvider : PlatformBiometric {
    
    override suspend fun checkAvailability(): Result<BiometricAvailability> {
        throw UnsupportedOperationException("Desktop biometric not implemented in common module")
    }
    
    override suspend fun authenticate(
        promptMessage: String,
        cancelMessage: String
    ): Result<User> {
        throw UnsupportedOperationException("Desktop biometric not implemented in common module")
    }
    
    override suspend fun enableBiometric(user: User): Result<Unit> {
        throw UnsupportedOperationException("Desktop biometric not implemented in common module")
    }
    
    override suspend fun disableBiometric(user: User): Result<Unit> {
        throw UnsupportedOperationException("Desktop biometric not implemented in common module")
    }
}