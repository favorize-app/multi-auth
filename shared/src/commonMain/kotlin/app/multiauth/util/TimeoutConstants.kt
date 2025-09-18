package app.multiauth.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Centralized timeout constants for authentication operations.
 * Provides configurable timeout values used across all authentication providers and services.
 */
object TimeoutConstants {
    
    // ============================================================================
    // VERIFICATION CODE TIMEOUTS
    // ============================================================================
    
    /**
     * Default timeout for SMS verification codes.
     * Used by TwilioSmsProvider and other SMS providers.
     */
    val SMS_VERIFICATION_CODE_TIMEOUT: Duration = 10.minutes
    
    /**
     * Default timeout for email verification codes.
     * Used by SmtpEmailProvider and other email providers.
     */
    val EMAIL_VERIFICATION_CODE_TIMEOUT: Duration = 15.minutes
    
    /**
     * Default timeout for mock SMS verification (shorter for testing).
     * Used by MockSmsProvider for faster testing cycles.
     */
    val MOCK_SMS_VERIFICATION_TIMEOUT: Duration = 5.minutes
    
    /**
     * Default timeout for MFA codes.
     * Used by MFA services for time-based authentication codes.
     */
    val MFA_CODE_TIMEOUT: Duration = 5.minutes
    
    // ============================================================================
    // SESSION AND TOKEN TIMEOUTS
    // ============================================================================
    
    /**
     * Default session timeout for user sessions.
     * Used by SessionManager and AuthEngine for token expiration.
     */
    val SESSION_TIMEOUT: Duration = 30.minutes
    
    /**
     * Buffer time for OAuth token refresh.
     * Tokens are refreshed this amount of time before they expire.
     */
    val OAUTH_TOKEN_REFRESH_BUFFER: Duration = 5.minutes
    
    /**
     * Default JWT access token timeout.
     * Used by JwtTokenManager for access token expiration.
     */
    val JWT_ACCESS_TOKEN_TIMEOUT: Duration = 30.minutes
    
    /**
     * Threshold for token refresh operations.
     * Tokens are refreshed when they expire within this timeframe.
     */
    val TOKEN_REFRESH_THRESHOLD: Duration = 5.minutes
    
    // ============================================================================
    // RATE LIMITING TIMEOUTS
    // ============================================================================
    
    /**
     * Time window for rate limiting operations.
     * Used by RateLimiter to track request counts within this window.
     */
    val RATE_LIMIT_WINDOW: Duration = 15.minutes
    
    /**
     * Lockout duration after rate limit is exceeded.
     * Users are locked out for this duration after too many failed attempts.
     */
    val RATE_LIMIT_LOCKOUT: Duration = 30.minutes
    
    /**
     * Window for detecting suspicious activity.
     * Used by security audit systems to detect patterns.
     */
    val SUSPICIOUS_ACTIVITY_WINDOW: Duration = 15.minutes
    
    // ============================================================================
    // MONITORING AND MAINTENANCE TIMEOUTS
    // ============================================================================
    
    /**
     * Interval for session monitoring and cleanup.
     * How often the system checks for expired sessions.
     */
    val SESSION_MONITORING_INTERVAL: Duration = 5.minutes
    
    /**
     * Timeout for various security alerts and notifications.
     */
    val SECURITY_ALERT_TIMEOUT: Duration = 5.minutes
    
    /**
     * Timeout for login notifications.
     */
    val LOGIN_NOTIFICATION_TIMEOUT: Duration = 10.minutes
    
    // ============================================================================
    // UTILITY METHODS
    // ============================================================================
    
    /**
     * Converts duration to milliseconds for compatibility with timestamp-based systems.
     */
    fun Duration.toEpochMillis(): Long = this.inWholeMilliseconds
    
    /**
     * Creates a human-readable timeout message for verification codes.
     */
    fun getVerificationTimeoutMessage(timeout: Duration): String {
        val minutes = timeout.inWholeMinutes
        return "This code will expire in $minutes minute${if (minutes != 1L) "s" else ""}."
    }
    
    /**
     * Creates a human-readable timeout message for SMS verification codes.
     */
    fun getSmsVerificationMessage(code: String): String {
        val minutes = SMS_VERIFICATION_CODE_TIMEOUT.inWholeMinutes
        return "Your verification code is: $code\n\nThis code will expire in $minutes minutes."
    }
    
    /**
     * Creates a human-readable timeout message for email verification codes.
     */
    fun getEmailVerificationMessage(code: String): String {
        val minutes = EMAIL_VERIFICATION_CODE_TIMEOUT.inWholeMinutes
        return "Your verification code is: $code\n\nThis code will expire in $minutes minutes."
    }
    
    /**
     * Creates a human-readable timeout message for MFA codes.
     */
    fun getMfaCodeMessage(code: String): String {
        val minutes = MFA_CODE_TIMEOUT.inWholeMinutes
        return "Your MFA code is: $code. Valid for $minutes minutes."
    }
    
    /**
     * Gets timeout in milliseconds for compatibility with legacy systems.
     */
    fun getSmsVerificationTimeoutMs(): Long = SMS_VERIFICATION_CODE_TIMEOUT.toEpochMillis()
    
    /**
     * Gets email verification timeout in milliseconds.
     */
    fun getEmailVerificationTimeoutMs(): Long = EMAIL_VERIFICATION_CODE_TIMEOUT.toEpochMillis()
    
    /**
     * Gets mock SMS timeout in milliseconds (for testing).
     */
    fun getMockSmsTimeoutMs(): Long = MOCK_SMS_VERIFICATION_TIMEOUT.toEpochMillis()
}

/**
 * Configuration class for customizing timeout values.
 * Allows different environments (dev, staging, prod) to have different timeout settings.
 */
data class TimeoutConfig(
    val smsVerificationTimeout: Duration = TimeoutConstants.SMS_VERIFICATION_CODE_TIMEOUT,
    val emailVerificationTimeout: Duration = TimeoutConstants.EMAIL_VERIFICATION_CODE_TIMEOUT,
    val sessionTimeout: Duration = TimeoutConstants.SESSION_TIMEOUT,
    val mfaCodeTimeout: Duration = TimeoutConstants.MFA_CODE_TIMEOUT,
    val rateLimitWindow: Duration = TimeoutConstants.RATE_LIMIT_WINDOW,
    val rateLimitLockout: Duration = TimeoutConstants.RATE_LIMIT_LOCKOUT
) {
    companion object {
        /**
         * Development configuration with shorter timeouts for faster testing.
         */
        val DEVELOPMENT = TimeoutConfig(
            smsVerificationTimeout = 2.minutes,
            emailVerificationTimeout = 3.minutes,
            sessionTimeout = 10.minutes,
            mfaCodeTimeout = 2.minutes,
            rateLimitWindow = 5.minutes,
            rateLimitLockout = 10.minutes
        )
        
        /**
         * Production configuration with standard security timeouts.
         */
        val PRODUCTION = TimeoutConfig(
            smsVerificationTimeout = 10.minutes,
            emailVerificationTimeout = 15.minutes,
            sessionTimeout = 30.minutes,
            mfaCodeTimeout = 5.minutes,
            rateLimitWindow = 15.minutes,
            rateLimitLockout = 30.minutes
        )
        
        /**
         * Testing configuration with very short timeouts for unit tests.
         */
        val TESTING = TimeoutConfig(
            smsVerificationTimeout = 30.seconds,
            emailVerificationTimeout = 1.minutes,
            sessionTimeout = 2.minutes,
            mfaCodeTimeout = 30.seconds,
            rateLimitWindow = 1.minutes,
            rateLimitLockout = 2.minutes
        )
    }
}