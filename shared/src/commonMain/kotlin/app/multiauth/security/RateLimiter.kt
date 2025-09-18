@file:OptIn(ExperimentalTime::class)

package app.multiauth.security



import kotlin.time.Duration
import app.multiauth.util.TimeoutConstants
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Clock

/**
 * Simple rate limiter to prevent brute force attacks.
 * Tracks failed authentication attempts by identifier (email, IP, etc.).
 */
class RateLimiter(
    private val maxAttempts: Int = 5,
    private val windowDuration: Duration = TimeoutConstants.RATE_LIMIT_WINDOW,
    private val lockoutDuration: Duration = TimeoutConstants.RATE_LIMIT_LOCKOUT
) {

    private val attemptHistory = mutableMapOf<String, MutableList<Instant>>()
    private val lockedUntil = mutableMapOf<String, Instant>()

    /**
     * Checks if an identifier is currently rate limited.
     *
     * @param identifier The identifier to check (email, IP address, etc.)
     * @return RateLimitResult indicating the current status
     */
    fun checkRateLimit(identifier: String): RateLimitResult {
        val now = Clock.System.now()

        // Check if currently locked out
        val lockoutEnd = lockedUntil[identifier]
        if (lockoutEnd != null && now < lockoutEnd) {
            val remainingLockout = lockoutEnd - now
            return RateLimitResult.RateLimited(remainingLockout)
        }

        // Clean up expired lockouts
        if (lockoutEnd != null && now >= lockoutEnd) {
            lockedUntil.remove(identifier)
            attemptHistory.remove(identifier) // Reset attempts after lockout
        }

        // Get recent attempts within the window
        val attempts = attemptHistory[identifier] ?: mutableListOf()
        val windowStart = now - windowDuration

        // Remove attempts outside the window
        attempts.removeAll { it < windowStart }

        // Check if within rate limit
        if (attempts.size >= maxAttempts) {
            // Lock out the identifier
            val lockoutEnd = now + lockoutDuration
            lockedUntil[identifier] = lockoutEnd
            return RateLimitResult.RateLimited(lockoutDuration)
        }

        return RateLimitResult.Allowed(attemptsRemaining = maxAttempts - attempts.size)
    }

    /**
     * Records a failed authentication attempt.
     *
     * @param identifier The identifier that failed authentication
     */
    fun recordFailedAttempt(identifier: String) {
        val now = Clock.System.now()
        val attempts = attemptHistory.getOrPut(identifier) { mutableListOf() }
        attempts.add(now)

        // Keep only attempts within the window
        val windowStart = now - windowDuration
        attempts.removeAll { it < windowStart }
    }

    /**
     * Records a successful authentication, which resets the failure count.
     *
     * @param identifier The identifier that successfully authenticated
     */
    fun recordSuccessfulAttempt(identifier: String) {
        // Clear failed attempts on successful authentication
        attemptHistory.remove(identifier)
        lockedUntil.remove(identifier)
    }

    /**
     * Manually clears rate limiting for an identifier (admin override).
     *
     * @param identifier The identifier to clear
     */
    fun clearRateLimit(identifier: String) {
        attemptHistory.remove(identifier)
        lockedUntil.remove(identifier)
    }

    /**
     * Gets current rate limit status for debugging/monitoring.
     */
    fun getRateLimitStatus(identifier: String): RateLimitStatus {
        val now = Clock.System.now()
        val attempts = attemptHistory[identifier] ?: emptyList()
        val lockoutEnd = lockedUntil[identifier]

        return RateLimitStatus(
            identifier = identifier,
            currentAttempts = attempts.size,
            maxAttempts = maxAttempts,
            isLocked = lockoutEnd != null && now < lockoutEnd,
            lockoutEndsAt = lockoutEnd,
            windowDuration = windowDuration,
            lockoutDuration = lockoutDuration
        )
    }

    /**
     * Cleans up old entries to prevent memory leaks.
     * Should be called periodically.
     */
    fun cleanup() {
        val now = Clock.System.now()
        val windowStart = now - windowDuration

        // Clean up attempt history
        attemptHistory.entries.removeAll { (_, attempts) ->
            attempts.removeAll { it < windowStart }
            attempts.isEmpty()
        }

        // Clean up expired lockouts
        lockedUntil.entries.removeAll { (_, lockoutEnd) ->
            now >= lockoutEnd
        }
    }
}

/**
 * Result of a rate limit check.
 */
sealed class RateLimitResult {
    /**
     * Request is allowed to proceed.
     */
    data class Allowed(val attemptsRemaining: Int) : RateLimitResult()

    /**
     * Request is rate limited.
     */
    data class RateLimited(val retryAfter: Duration) : RateLimitResult()
}

/**
 * Current rate limit status for monitoring/debugging.
 */
data class RateLimitStatus(
    val identifier: String,
    val currentAttempts: Int,
    val maxAttempts: Int,
    val isLocked: Boolean,
    val lockoutEndsAt: Instant?,
    val windowDuration: Duration,
    val lockoutDuration: Duration
)
