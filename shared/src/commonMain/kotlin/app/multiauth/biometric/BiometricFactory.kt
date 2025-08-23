package app.multiauth.biometric

import app.multiauth.platform.Platform
import app.multiauth.platform.PlatformUtils
import app.multiauth.util.Logger

/**
 * Factory for creating platform-specific biometric implementations.
 * Uses platform detection to provide the appropriate biometric provider.
 */
object BiometricFactory {
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Creates and returns the appropriate biometric implementation for the current platform.
     * 
     * @return A PlatformBiometric implementation for the current platform
     * @throws UnsupportedOperationException if the platform is not supported
     */
    fun createBiometricProvider(): PlatformBiometric {
        return when (PlatformUtils.currentPlatform) {
            Platform.ANDROID -> createAndroidBiometric()
            Platform.IOS -> createIOSBiometric()
            Platform.WEB -> createWebBiometric()
            Platform.DESKTOP -> createDesktopBiometric()
            Platform.UNKNOWN -> throw UnsupportedOperationException("Unknown platform detected")
        }
    }
    
    /**
     * Creates an Android-specific biometric implementation.
     * This will be implemented in the Android-specific source set.
     */
    private fun createAndroidBiometric(): PlatformBiometric {
        logger.info("biometrics", "Creating Android biometric provider")
        // This will be implemented in androidMain
        throw UnsupportedOperationException("Android biometric not implemented in common module")
    }
    
    /**
     * Creates an iOS-specific biometric implementation.
     * This will be implemented in the iOS-specific source set.
     */
    private fun createIOSBiometric(): PlatformBiometric {
        logger.info("Creating iOS biometric provider")
        // This will be implemented in iosMain
        throw UnsupportedOperationException("iOS biometric not implemented in common module")
    }
    
    /**
     * Creates a Web-specific biometric implementation.
     * This will be implemented in the JavaScript-specific source set.
     */
    private fun createWebBiometric(): PlatformBiometric {
        logger.info("Creating Web biometric provider")
        // This will be implemented in jsMain
        throw UnsupportedOperationException("Web biometric not implemented in common module")
    }
    
    /**
     * Creates a Desktop-specific biometric implementation.
     * This will be implemented in the JVM-specific source set.
     */
    private fun createDesktopBiometric(): PlatformBiometric {
        logger.info("Creating Desktop biometric provider")
        // This will be implemented in jvmMain
        throw UnsupportedOperationException("Desktop biometric not implemented in common module")
    }
    
    /**
     * Creates a mock biometric provider for testing purposes.
     * 
     * @return A MockBiometricProvider instance
     */
    fun createMockBiometricProvider(): PlatformBiometric {
        logger.info("Creating mock biometric provider for testing")
        return MockBiometricProvider()
    }
    
    /**
     * Checks if the current platform supports biometric authentication.
     * 
     * @return true if biometric authentication is supported, false otherwise
     */
    fun isBiometricSupported(): Boolean {
        return PlatformUtils.supportsFeature(app.multiauth.platform.PlatformFeature.BIOMETRICS)
    }
    
    /**
     * Gets the supported biometric types for the current platform.
     * 
     * @return List of supported biometric types
     */
    fun getSupportedBiometricTypes(): List<BiometricType> {
        return when (PlatformUtils.currentPlatform) {
            Platform.ANDROID -> listOf(
                BiometricType.FINGERPRINT,
                BiometricType.FACE_RECOGNITION,
                BiometricType.IRIS_SCAN
            )
            Platform.IOS -> listOf(
                BiometricType.FINGERPRINT,
                BiometricType.FACE_RECOGNITION
            )
            Platform.WEB -> listOf(
                BiometricType.FINGERPRINT,
                BiometricType.FACE_RECOGNITION
            )
            Platform.DESKTOP -> listOf(
                BiometricType.FINGERPRINT,
                BiometricType.FACE_RECOGNITION
            )
            Platform.UNKNOWN -> emptyList()
        }
    }
}

/**
 * Mock implementation of PlatformBiometric for testing purposes.
 * Simulates biometric authentication without requiring actual hardware.
 */
class MockBiometricProvider : PlatformBiometric {
    
    private val logger = app.multiauth.util.Logger.getLogger(this::class)
    private var isEnabled = false
    private var mockUser: app.multiauth.models.User? = null
    
    override suspend fun checkAvailability(): Result<BiometricAvailability> {
        return try {
            logger.debug("biometrics", "Mock biometric: checking availability")
            
            // Mock biometric is always available for testing
            val availability = BiometricAvailability(
                isAvailable = true,
                supportedTypes = listOf(
                    BiometricType.FINGERPRINT,
                    BiometricType.FACE_RECOGNITION
                )
            )
            
            logger.debug("biometrics", "Mock biometric: availability check successful")
            Result.success(availability)
            
        } catch (e: Exception) {
            logger.error("Mock biometric: availability check failed", e)
            Result.failure(e)
        }
    }
    
    override suspend fun authenticate(
        promptMessage: String,
        cancelMessage: String
    ): Result<app.multiauth.models.User> {
        return try {
            logger.debug("biometrics", "Mock biometric: starting authentication")
            
            if (!isEnabled) {
                logger.warn("Mock biometric: authentication not enabled")
                return Result.failure(IllegalStateException("Biometric authentication not enabled"))
            }
            
            // Simulate authentication delay
            kotlinx.coroutines.delay(1000)
            
            // Mock successful authentication
            val user = mockUser ?: app.multiauth.models.User(
                id = "mock_user_001",
                email = "mock@example.com",
                displayName = "Mock User",
                isEmailVerified = true,
                createdAt = System.currentTimeMillis(),
                lastSignInAt = System.currentTimeMillis()
            )
            
            logger.debug("biometrics", "Mock biometric: authentication successful for user: ${user.displayName}")
            Result.success(user)
            
        } catch (e: Exception) {
            logger.error("Mock biometric: authentication failed", e)
            Result.failure(e)
        }
    }
    
    override suspend fun enableBiometric(user: app.multiauth.models.User): Result<Unit> {
        return try {
            logger.debug("biometrics", "Mock biometric: enabling for user: ${user.displayName}")
            
            isEnabled = true
            mockUser = user
            
            logger.debug("Mock biometric: enabled successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("Mock biometric: failed to enable", e)
            Result.failure(e)
        }
    }
    
    override suspend fun disableBiometric(user: app.multiauth.models.User): Result<Unit> {
        return try {
            logger.debug("biometrics", "Mock biometric: disabling for user: ${user.displayName}")
            
            isEnabled = false
            mockUser = null
            
            logger.debug("Mock biometric: disabled successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("Mock biometric: failed to disable", e)
            Result.failure(e)
        }
    }
}