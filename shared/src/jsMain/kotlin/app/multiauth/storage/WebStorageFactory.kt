package app.multiauth.storage

import app.multiauth.util.Logger

/**
 * Web-specific storage factory that provides real secure storage implementations.
 * This file should be included in the JavaScript build to provide actual functionality.
 */
object WebStorageFactory {
    
    private val logger = Logger.getLogger(this::class)
    
    /**
     * Creates a Web-specific secure storage implementation.
     * 
     * @return WebSecureStorage instance
     */
    fun createWebStorage(): SecureStorage {
        logger.info("Creating Web secure storage")
        return WebSecureStorage()
    }
    
    /**
     * Gets information about the Web storage implementation.
     * 
     * @return WebStorageInfo with implementation details
     */
    fun getStorageInfo(): WebStorageInfo {
        val storage = WebSecureStorage()
        return storage.getWebStorageInfo()
    }
    
    /**
     * Checks if the browser supports Web Crypto API.
     * 
     * @return true if supported, false otherwise
     */
    fun isWebCryptoSupported(): Boolean {
        val storage = WebSecureStorage()
        return storage.isWebCryptoSupported()
    }
    
    /**
     * Checks if the browser supports IndexedDB.
     * 
     * @return true if supported, false otherwise
     */
    fun isIndexedDBSupported(): Boolean {
        val storage = WebSecureStorage()
        return storage.isIndexedDBSupported()
    }
    
    /**
     * Checks if the current browser environment supports secure storage.
     * 
     * @return true if secure storage is supported, false otherwise
     */
    fun isSecureStorageSupported(): Boolean {
        val storage = WebSecureStorage()
        return storage.isWebCryptoSupported() && storage.isIndexedDBSupported()
    }
    
    /**
     * Gets a detailed compatibility report for the current browser.
     * 
     * @return WebCompatibilityReport with detailed browser support information
     */
    fun getCompatibilityReport(): WebCompatibilityReport {
        val storage = WebSecureStorage()
        val webCryptoSupported = storage.isWebCryptoSupported()
        val indexedDBSupported = storage.isIndexedDBSupported()
        
        return WebCompatibilityReport(
            webCryptoSupported = webCryptoSupported,
            indexedDBSupported = indexedDBSupported,
            secureStorageSupported = webCryptoSupported && indexedDBSupported,
            recommendations = generateRecommendations(webCryptoSupported, indexedDBSupported)
        )
    }
    
    private fun generateRecommendations(
        webCryptoSupported: Boolean,
        indexedDBSupported: Boolean
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!webCryptoSupported) {
            recommendations.add("Web Crypto API is not supported. Consider using a modern browser.")
        }
        
        if (!indexedDBSupported) {
            recommendations.add("IndexedDB is not supported. Consider using a modern browser.")
        }
        
        if (webCryptoSupported && indexedDBSupported) {
            recommendations.add("Browser fully supports secure storage. All features available.")
        }
        
        return recommendations
    }
}

/**
 * Information about the Web storage implementation.
 */
data class WebStorageInfo(
    val isWebCryptoSupported: Boolean,
    val isIndexedDBSupported: Boolean,
    val databaseName: String,
    val databaseVersion: Int,
    val storeName: String
)

/**
 * Detailed compatibility report for the current browser.
 */
data class WebCompatibilityReport(
    val webCryptoSupported: Boolean,
    val indexedDBSupported: Boolean,
    val secureStorageSupported: Boolean,
    val recommendations: List<String>
)