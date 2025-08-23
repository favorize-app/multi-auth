package app.multiauth.storage

import android.content.Context
import app.multiauth.util.Logger

/**
 * Android-specific storage factory that provides real secure storage implementations.
 * This file should be included in the Android build to provide actual functionality.
 */
object AndroidStorageFactory {
    
    private val logger = Logger.getLogger(this::class)
    private var applicationContext: Context? = null
    
    /**
     * Initializes the storage factory with the application context.
     * This should be called from the Application class or MainActivity.
     * 
     * @param context The application context
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        logger.info("storage", "Android storage factory initialized with context: ${context.packageName}")
    }
    
    /**
     * Creates an Android-specific secure storage implementation.
     * 
     * @return AndroidSecureStorage instance
     * @throws IllegalStateException if the factory is not initialized
     */
    fun createAndroidStorage(): SecureStorage {
        val context = applicationContext ?: throw IllegalStateException(
            "AndroidStorageFactory not initialized. Call initialize() first."
        )
        
        logger.info("storage", "Creating Android secure storage with context: ${context.packageName}")
        return AndroidSecureStorage(context)
    }
    
    /**
     * Checks if the factory is properly initialized.
     * 
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean {
        return applicationContext != null
    }
    
    /**
     * Gets information about the Android storage implementation.
     * 
     * @return AndroidStorageInfo with implementation details
     */
    fun getStorageInfo(): AndroidStorageInfo {
        val context = applicationContext
        return if (context != null) {
            val storage = AndroidSecureStorage(context)
            AndroidStorageInfo(
                isInitialized = true,
                packageName = context.packageName,
                isHardwareBacked = storage.isHardwareBacked(),
                keystoreInfo = storage.getKeystoreInfo()
            )
        } else {
            AndroidStorageInfo(
                isInitialized = false,
                packageName = "Not initialized",
                isHardwareBacked = false,
                keystoreInfo = null
            )
        }
    }
}

/**
 * Information about the Android storage implementation.
 */
data class AndroidStorageInfo(
    val isInitialized: Boolean,
    val packageName: String,
    val isHardwareBacked: Boolean,
    val keystoreInfo: KeystoreInfo?
)