package app.multiauth.security

import app.multiauth.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limiter for preventing brute force attacks and controlling request frequency.
 * Provides configurable rate limiting for different types of operations.
 */
class RateLimiter {
    
    private val logger = Logger.getLogger(this::class)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        // Default rate limiting configurations
        private const val DEFAULT_LOGIN_ATTEMPTS_PER_HOUR = 5
        private const val DEFAULT_PASSWORD_RESET_ATTEMPTS_PER_HOUR = 3
        private const val DEFAULT_MFA_ATTEMPTS_PER_HOUR = 10
        private const val DEFAULT_API_REQUESTS_PER_MINUTE = 100
        
        // Account lockout configurations
        private const val DEFAULT_ACCOUNT_LOCKOUT_DURATION_MINUTES = 30L
        private const val DEFAULT_MAX_FAILED_ATTEMPTS = 5
        private const val DEFAULT_FAILED_ATTEMPT_WINDOW_MINUTES = 15L
    }
    
    private val _rateLimitState = MutableStateFlow<RateLimitState>(RateLimitState.Idle)
    val rateLimitState: StateFlow<RateLimitState> = _rateLimitState.asStateFlow()
    
    private val _blockedEntities = MutableStateFlow<Set<String>>(emptySet())
    val blockedEntities: StateFlow<Set<String>> = _blockedEntities.asStateFlow()
    
    private val _rateLimitMetrics = MutableStateFlow(RateLimitMetrics())
    val rateLimitMetrics: StateFlow<RateLimitMetrics> = _rateLimitMetrics.asStateFlow()
    
    // Rate limiting storage
    private val _loginAttempts = ConcurrentHashMap<String, MutableList<Instant>>()
    private val _passwordResetAttempts = ConcurrentHashMap<String, MutableList<Instant>>()
    private val _mfaAttempts = ConcurrentHashMap<String, MutableList<Instant>>()
    private val _apiRequests = ConcurrentHashMap<String, MutableList<Instant>>()
    
    // Account lockout storage
    private val _lockedAccounts = ConcurrentHashMap<String, AccountLockout>()
    private val _failedAttempts = ConcurrentHashMap<String, MutableList<FailedAttempt>>()
    
    // Configuration
    private var config = RateLimitConfig()
    
    /**
     * Configures the rate limiter with custom settings.
     * 
     * @param newConfig The new configuration
     */
    fun configure(newConfig: RateLimitConfig) {
        config = newConfig
        logger.info("security", "Rate limiter configured with new settings: $newConfig")
    }
    
    /**
     * Checks if a login attempt is allowed for the given identifier.
     * 
     * @param identifier The identifier (email, username, or IP address)
     * @return RateLimitResult indicating if the attempt is allowed
     */
    fun checkLoginAttempt(identifier: String): RateLimitResult {
        return checkRateLimit(
            identifier = identifier,
            attemptType = AttemptType.LOGIN,
            maxAttempts = config.maxLoginAttemptsPerHour,
            windowMinutes = 60L,
            storage = _loginAttempts
        )
    }
    
    /**
     * Records a successful login attempt.
     * 
     * @param identifier The identifier that successfully logged in
     */
    fun recordSuccessfulLogin(identifier: String) {
        recordSuccessfulAttempt(identifier, AttemptType.LOGIN, _loginAttempts)
        unlockAccount(identifier)
        logger.info("security", "Successful login recorded for: $identifier")
    }
    
    /**
     * Records a failed login attempt.
     * 
     * @param identifier The identifier that failed to log in
     * @param reason The reason for the failure
     */
    fun recordFailedLogin(identifier: String, reason: String) {
        recordFailedAttempt(identifier, AttemptType.LOGIN, _loginAttempts, reason)
        checkForAccountLockout(identifier)
        logger.warn("Failed login attempt recorded for: $identifier, reason: $reason")
    }
    
    /**
     * Checks if a password reset attempt is allowed.
     * 
     * @param identifier The identifier requesting password reset
     * @return RateLimitResult indicating if the attempt is allowed
     */
    fun checkPasswordResetAttempt(identifier: String): RateLimitResult {
        return checkRateLimit(
            identifier = identifier,
            attemptType = AttemptType.PASSWORD_RESET,
            maxAttempts = config.maxPasswordResetAttemptsPerHour,
            windowMinutes = 60L,
            storage = _passwordResetAttempts
        )
    }
    
    /**
     * Records a password reset attempt.
     * 
     * @param identifier The identifier that requested password reset
     */
    fun recordPasswordResetAttempt(identifier: String) {
        recordAttempt(identifier, _passwordResetAttempts)
        logger.info("security", "Password reset attempt recorded for: $identifier")
    }
    
    /**
     * Checks if an MFA attempt is allowed.
     * 
     * @param identifier The identifier attempting MFA
     * @return RateLimitResult indicating if the attempt is allowed
     */
    fun checkMfaAttempt(identifier: String): RateLimitResult {
        return checkRateLimit(
            identifier = identifier,
            attemptType = AttemptType.MFA,
            maxAttempts = config.maxMfaAttemptsPerHour,
            windowMinutes = 60L,
            storage = _mfaAttempts
        )
    }
    
    /**
     * Records an MFA attempt.
     * 
     * @param identifier The identifier that attempted MFA
     */
    fun recordMfaAttempt(identifier: String) {
        recordAttempt(identifier, _mfaAttempts)
        logger.info("security", "MFA attempt recorded for: $identifier")
    }
    
    /**
     * Checks if an API request is allowed.
     * 
     * @param identifier The identifier making the API request
     * @return RateLimitResult indicating if the request is allowed
     */
    fun checkApiRequest(identifier: String): RateLimitResult {
        return checkRateLimit(
            identifier = identifier,
            attemptType = AttemptType.API_REQUEST,
            maxAttempts = config.maxApiRequestsPerMinute,
            windowMinutes = 1L,
            storage = _apiRequests
        )
    }
    
    /**
     * Records an API request.
     * 
     * @param identifier The identifier that made the API request
     */
    fun recordApiRequest(identifier: String) {
        recordAttempt(identifier, _apiRequests)
        logger.debug("security", "API request recorded for: $identifier")
    }
    
    /**
     * Checks if an account is locked.
     * 
     * @param identifier The identifier to check
     * @return true if the account is locked, false otherwise
     */
    fun isAccountLocked(identifier: String): Boolean {
        val lockout = _lockedAccounts[identifier]
        if (lockout == null) return false
        
        // Check if lockout has expired
        if (Instant.now().isAfter(lockout.expiresAt)) {
            _lockedAccounts.remove(identifier)
            return false
        }
        
        return true
    }
    
    /**
     * Gets the remaining lockout time for an account.
     * 
     * @param identifier The identifier to check
     * @return Remaining lockout time in seconds, or 0 if not locked
     */
    fun getRemainingLockoutTime(identifier: String): Long {
        val lockout = _lockedAccounts[identifier] ?: return 0
        
        if (Instant.now().isAfter(lockout.expiresAt)) {
            _lockedAccounts.remove(identifier)
            return 0
        }
        
        return ChronoUnit.SECONDS.between(Instant.now(), lockout.expiresAt)
    }
    
    /**
     * Manually unlocks an account.
     * 
     * @param identifier The identifier to unlock
     */
    fun unlockAccount(identifier: String) {
        _lockedAccounts.remove(identifier)
        _failedAttempts.remove(identifier)
        _blockedEntities.value = _blockedEntities.value - identifier
        
        logger.info("security", "Account manually unlocked for: $identifier")
    }
    
    /**
     * Gets rate limit statistics for an identifier.
     * 
     * @param identifier The identifier to get stats for
     * @return RateLimitStats for the identifier
     */
    fun getRateLimitStats(identifier: String): RateLimitStats {
        val now = Instant.now()
        
        val loginAttempts = getRecentAttempts(_loginAttempts[identifier], now, 60L)
        val passwordResetAttempts = getRecentAttempts(_passwordResetAttempts[identifier], now, 60L)
        val mfaAttempts = getRecentAttempts(_mfaAttempts[identifier], now, 60L)
        val apiRequests = getRecentAttempts(_apiRequests[identifier], now, 1L)
        
        val isLocked = isAccountLocked(identifier)
        val remainingLockoutTime = getRemainingLockoutTime(identifier)
        
        return RateLimitStats(
            identifier = identifier,
            loginAttempts = loginAttempts,
            passwordResetAttempts = passwordResetAttempts,
            mfaAttempts = mfaAttempts,
            apiRequests = apiRequests,
            isLocked = isLocked,
            remainingLockoutTime = remainingLockoutTime,
            lastUpdated = now
        )
    }
    
    /**
     * Cleans up expired rate limit data.
     */
    fun cleanup() {
        scope.launch {
            try {
                val now = Instant.now()
                
                // Clean up expired attempts
                cleanupExpiredAttempts(_loginAttempts, now, 60L)
                cleanupExpiredAttempts(_passwordResetAttempts, now, 60L)
                cleanupExpiredAttempts(_mfaAttempts, now, 60L)
                cleanupExpiredAttempts(_apiRequests, now, 1L)
                
                // Clean up expired lockouts
                cleanupExpiredLockouts(now)
                
                // Clean up expired failed attempts
                cleanupExpiredFailedAttempts(now)
                
                logger.debug("security", "Rate limiter cleanup completed")
            } catch (e: Exception) {
                logger.error("Failed to cleanup rate limiter data", e)
            }
        }
    }
    
    // Private implementation methods
    
    private fun checkRateLimit(
        identifier: String,
        attemptType: AttemptType,
        maxAttempts: Int,
        windowMinutes: Long,
        storage: ConcurrentHashMap<String, MutableList<Instant>>
    ): RateLimitResult {
        // Check if entity is blocked
        if (_blockedEntities.value.contains(identifier)) {
            return RateLimitResult.Blocked(
                reason = "Entity is blocked due to suspicious activity",
                retryAfter = null
            )
        }
        
        // Check if account is locked
        if (isAccountLocked(identifier)) {
            val remainingTime = getRemainingLockoutTime(identifier)
            return RateLimitResult.Blocked(
                reason = "Account is temporarily locked due to multiple failed attempts",
                retryAfter = remainingTime
            )
        }
        
        // Check rate limit
        val attempts = storage.getOrPut(identifier) { mutableListOf() }
        val now = Instant.now()
        val cutoffTime = now.minus(windowMinutes, ChronoUnit.MINUTES)
        
        // Remove old attempts
        attempts.removeAll { it.isBefore(cutoffTime) }
        
        if (attempts.size >= maxAttempts) {
            val oldestAttempt = attempts.minOrNull()
            val retryAfter = if (oldestAttempt != null) {
                ChronoUnit.SECONDS.between(now, oldestAttempt.plus(windowMinutes, ChronoUnit.MINUTES))
            } else 0L
            
            return RateLimitResult.RateLimited(
                reason = "Rate limit exceeded for $attemptType",
                retryAfter = retryAfter,
                attemptsRemaining = 0
            )
        }
        
        return RateLimitResult.Allowed(
            attemptsRemaining = maxAttempts - attempts.size
        )
    }
    
    private fun recordAttempt(identifier: String, storage: ConcurrentHashMap<String, MutableList<Instant>>) {
        val attempts = storage.getOrPut(identifier) { mutableListOf() }
        attempts.add(Instant.now())
        
        // Update metrics
        updateMetrics(identifier, true)
    }
    
    private fun recordSuccessfulAttempt(identifier: String, attemptType: AttemptType, storage: ConcurrentHashMap<String, MutableList<Instant>>) {
        // Clear failed attempts for successful authentication
        storage.remove(identifier)
        updateMetrics(identifier, true)
    }
    
    private fun recordFailedAttempt(identifier: String, attemptType: AttemptType, storage: ConcurrentHashMap<String, MutableList<Instant>>, reason: String) {
        recordAttempt(identifier, storage)
        
        // Record failed attempt for lockout tracking
        val failedAttempts = _failedAttempts.getOrPut(identifier) { mutableListOf() }
        failedAttempts.add(FailedAttempt(Instant.now(), attemptType, reason))
        
        updateMetrics(identifier, false)
    }
    
    private fun checkForAccountLockout(identifier: String) {
        val failedAttempts = _failedAttempts[identifier] ?: return
        val now = Instant.now()
        val cutoffTime = now.minus(config.failedAttemptWindowMinutes, ChronoUnit.MINUTES)
        
        // Get recent failed attempts
        val recentFailedAttempts = failedAttempts.filter { it.timestamp.isAfter(cutoffTime) }
        
        if (recentFailedAttempts.size >= config.maxFailedAttempts) {
            // Lock the account
            val lockout = AccountLockout(
                identifier = identifier,
                lockedAt = now,
                expiresAt = now.plus(config.accountLockoutDurationMinutes, ChronoUnit.MINUTES),
                reason = "Multiple failed authentication attempts"
            )
            
            _lockedAccounts[identifier] = lockout
            _blockedEntities.value = _blockedEntities.value + identifier
            
            logger.warn("security", "Account locked for: $identifier due to multiple failed attempts")
            
            // Dispatch lockout event
            // eventBus.dispatch(AuthEvent.Security.AccountLocked(identifier, lockout))
        }
    }
    
    private fun getRecentAttempts(attempts: MutableList<Instant>?, now: Instant, windowMinutes: Long): Int {
        if (attempts == null) return 0
        
        val cutoffTime = now.minus(windowMinutes, ChronoUnit.MINUTES)
        return attempts.count { it.isAfter(cutoffTime) }
    }
    
    private fun cleanupExpiredAttempts(storage: ConcurrentHashMap<String, MutableList<Instant>>, now: Instant, windowMinutes: Long) {
        val cutoffTime = now.minus(windowMinutes, ChronoUnit.MINUTES)
        
        storage.forEach { (identifier, attempts) ->
            attempts.removeAll { it.isBefore(cutoffTime) }
            if (attempts.isEmpty()) {
                storage.remove(identifier)
            }
        }
    }
    
    private fun cleanupExpiredLockouts(now: Instant) {
        _lockedAccounts.entries.removeIf { (_, lockout) ->
            now.isAfter(lockout.expiresAt)
        }
    }
    
    private fun cleanupExpiredFailedAttempts(now: Instant) {
        val cutoffTime = now.minus(config.failedAttemptWindowMinutes, ChronoUnit.MINUTES)
        
        _failedAttempts.forEach { (identifier, attempts) ->
            attempts.removeAll { it.isBefore(cutoffTime) }
            if (attempts.isEmpty()) {
                _failedAttempts.remove(identifier)
            }
        }
    }
    
    private fun updateMetrics(identifier: String, success: Boolean) {
        val currentMetrics = _rateLimitMetrics.value
        
        val updatedMetrics = if (success) {
            currentMetrics.copy(
                successfulRequests = currentMetrics.successfulRequests + 1
            )
        } else {
            currentMetrics.copy(
                blockedRequests = currentMetrics.blockedRequests + 1
            )
        }
        
        _rateLimitMetrics.value = updatedMetrics
    }
}

/**
 * Represents the result of a rate limit check.
 */
sealed class RateLimitResult {
    data class Allowed(val attemptsRemaining: Int) : RateLimitResult()
    data class RateLimited(val reason: String, val retryAfter: Long, val attemptsRemaining: Int) : RateLimitResult()
    data class Blocked(val reason: String, val retryAfter: Long?) : RateLimitResult()
}

/**
 * Represents the state of the rate limiter.
 */
sealed class RateLimitState {
    object Idle : RateLimitState()
    object Checking : RateLimitState()
    data class Blocked(val reason: String) : RateLimitState()
    data class RateLimited(val retryAfter: Long) : RateLimitState()
}

/**
 * Represents rate limiting metrics.
 */
data class RateLimitMetrics(
    val successfulRequests: Long = 0,
    val blockedRequests: Long = 0,
    val lastUpdated: Instant = Instant.now()
)

/**
 * Represents rate limit statistics for an identifier.
 */
data class RateLimitStats(
    val identifier: String,
    val loginAttempts: Int,
    val passwordResetAttempts: Int,
    val mfaAttempts: Int,
    val apiRequests: Int,
    val isLocked: Boolean,
    val remainingLockoutTime: Long,
    val lastUpdated: Instant
)

/**
 * Represents the type of attempt being rate limited.
 */
enum class AttemptType {
    LOGIN,
    PASSWORD_RESET,
    MFA,
    API_REQUEST
}

/**
 * Represents a failed authentication attempt.
 */
data class FailedAttempt(
    val timestamp: Instant,
    val type: AttemptType,
    val reason: String
)

/**
 * Represents an account lockout.
 */
data class AccountLockout(
    val identifier: String,
    val lockedAt: Instant,
    val expiresAt: Instant,
    val reason: String
)

/**
 * Configuration for rate limiting.
 */
data class RateLimitConfig(
    val maxLoginAttemptsPerHour: Int = DEFAULT_LOGIN_ATTEMPTS_PER_HOUR,
    val maxPasswordResetAttemptsPerHour: Int = DEFAULT_PASSWORD_RESET_ATTEMPTS_PER_HOUR,
    val maxMfaAttemptsPerHour: Int = DEFAULT_MFA_ATTEMPTS_PER_HOUR,
    val maxApiRequestsPerMinute: Int = DEFAULT_API_REQUESTS_PER_MINUTE,
    val maxFailedAttempts: Int = DEFAULT_MAX_FAILED_ATTEMPTS,
    val failedAttemptWindowMinutes: Long = DEFAULT_FAILED_ATTEMPT_WINDOW_MINUTES,
    val accountLockoutDurationMinutes: Long = DEFAULT_ACCOUNT_LOCKOUT_DURATION_MINUTES
)