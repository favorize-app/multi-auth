package app.multiauth.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Enum representing the different platforms supported by the multi-auth system.
 */
enum class Platform {
    ANDROID,
    IOS,
    WEB,
    DESKTOP,
    UNKNOWN
}

/**
 * Utility object for platform detection and platform-specific operations.
 */
object PlatformUtils {
    
    /**
     * The current platform the code is running on.
     */
    val currentPlatform: Platform
        get() = when {
            isAndroid() -> Platform.ANDROID
            isIOS() -> Platform.IOS
            isWeb() -> Platform.WEB
            isDesktop() -> Platform.DESKTOP
            else -> Platform.UNKNOWN
        }
    
    /**
     * Checks if the current platform is Android.
     */
    fun isAndroid(): Boolean {
        return try {
            // This will only compile and run on Android
            android.os.Build.VERSION.SDK_INT > 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Checks if the current platform is iOS.
     */
    fun isIOS(): Boolean {
        return try {
            // This will only compile and run on iOS
            platform.Foundation.NSProcessInfo.processInfo.operatingSystemVersionString.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Checks if the current platform is Web (JavaScript).
     */
    fun isWeb(): Boolean {
        return try {
            // This will only compile and run on JavaScript
            kotlinx.browser.window.navigator.userAgent.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Checks if the current platform is Desktop (JVM).
     */
    fun isDesktop(): Boolean {
        return try {
            // This will only compile and run on JVM
            // Platform-specific implementation required("os.name").isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets a human-readable name for the current platform.
     */
    fun getPlatformName(): String {
        return when (currentPlatform) {
            Platform.ANDROID -> "Android"
            Platform.IOS -> "iOS"
            Platform.WEB -> "Web"
            Platform.DESKTOP -> "Desktop"
            Platform.UNKNOWN -> "Unknown"
        }
    }
    
    /**
     * Gets the platform version information.
     */
    fun getPlatformVersion(): String {
        return when (currentPlatform) {
            Platform.ANDROID -> try {
                "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            } catch (e: Exception) {
                "Android Unknown"
            }
            Platform.IOS -> try {
                "iOS ${platform.Foundation.NSProcessInfo.processInfo.operatingSystemVersionString}"
            } catch (e: Exception) {
                "iOS Unknown"
            }
            Platform.WEB -> try {
                "Web ${kotlinx.browser.window.navigator.userAgent}"
            } catch (e: Exception) {
                "Web Unknown"
            }
            Platform.DESKTOP -> try {
                "${// Platform-specific implementation required("os.name")} ${// Platform-specific implementation required("os.version")}"
            } catch (e: Exception) {
                "Desktop Unknown"
            }
            Platform.UNKNOWN -> "Unknown Platform"
        }
    }
    
    /**
     * Checks if the current platform supports a specific feature.
     */
    fun supportsFeature(feature: PlatformFeature): Boolean {
        return when (feature) {
            PlatformFeature.BIOMETRICS -> currentPlatform in listOf(Platform.ANDROID, Platform.IOS)
            PlatformFeature.SECURE_STORAGE -> currentPlatform in listOf(Platform.ANDROID, Platform.IOS, Platform.WEB)
            PlatformFeature.OAUTH -> currentPlatform in listOf(Platform.ANDROID, Platform.IOS, Platform.WEB)
            PlatformFeature.PUSH_NOTIFICATIONS -> currentPlatform in listOf(Platform.ANDROID, Platform.IOS)
            PlatformFeature.CAMERA -> currentPlatform in listOf(Platform.ANDROID, Platform.IOS, Platform.WEB)
            PlatformFeature.LOCATION -> currentPlatform in listOf(Platform.ANDROID, Platform.IOS, Platform.WEB)
        }
    }
}

/**
 * Enum representing platform-specific features.
 */
enum class PlatformFeature {
    BIOMETRICS,
    SECURE_STORAGE,
    OAUTH,
    PUSH_NOTIFICATIONS,
    CAMERA,
    LOCATION
}

/**
 * Extension function to get platform information as a flow.
 */
fun Platform.asFlow(): Flow<Platform> = flowOf(this)