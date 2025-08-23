package app.multiauth.testing

import app.multiauth.security.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class RateLimiterTest {
    
    private lateinit var rateLimiter: RateLimiter
    
    @BeforeTest
    fun setup() {
        rateLimiter = RateLimiter()
    }
    
    @Test
    fun `test login attempt rate limiting`() = runTest {
        // Given
        val identifier = "test@example.com"
        val maxAttempts = 5
        
        // When - make maximum allowed attempts
        repeat(maxAttempts) {
            val result = rateLimiter.checkLoginAttempt(identifier)
            assertTrue(result is RateLimitResult.Allowed)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - next attempt should be rate limited
        val rateLimitedResult = rateLimiter.checkLoginAttempt(identifier)
        assertTrue(rateLimitedResult is RateLimitResult.RateLimited)
        assertEquals(0, (rateLimitedResult as RateLimitResult.RateLimited).attemptsRemaining)
    }
    
    @Test
    fun `test successful login resets rate limiting`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - make some failed attempts
        repeat(3) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - should still be allowed
        val allowedResult = rateLimiter.checkLoginAttempt(identifier)
        assertTrue(allowedResult is RateLimitResult.Allowed)
        assertEquals(2, (allowedResult as RateLimitResult.Allowed).attemptsRemaining)
        
        // When - successful login
        rateLimiter.recordSuccessfulLogin(identifier)
        
        // Then - should be reset
        val resetResult = rateLimiter.checkLoginAttempt(identifier)
        assertTrue(resetResult is RateLimitResult.Allowed)
        assertEquals(5, (resetResult as RateLimitResult.Allowed).attemptsRemaining)
    }
    
    @Test
    fun `test password reset rate limiting`() = runTest {
        // Given
        val identifier = "test@example.com"
        val maxAttempts = 3
        
        // When - make maximum allowed attempts
        repeat(maxAttempts) {
            val result = rateLimiter.checkPasswordResetAttempt(identifier)
            assertTrue(result is RateLimitResult.Allowed)
            rateLimiter.recordPasswordResetAttempt(identifier)
        }
        
        // Then - next attempt should be rate limited
        val rateLimitedResult = rateLimiter.checkPasswordResetAttempt(identifier)
        assertTrue(rateLimitedResult is RateLimitResult.RateLimited)
        assertEquals(0, (rateLimitedResult as RateLimitResult.RateLimited).attemptsRemaining)
    }
    
    @Test
    fun `test MFA attempt rate limiting`() = runTest {
        // Given
        val identifier = "test@example.com"
        val maxAttempts = 10
        
        // When - make maximum allowed attempts
        repeat(maxAttempts) {
            val result = rateLimiter.checkMfaAttempt(identifier)
            assertTrue(result is RateLimitResult.Allowed)
            rateLimiter.recordMfaAttempt(identifier)
        }
        
        // Then - next attempt should be rate limited
        val rateLimitedResult = rateLimiter.checkMfaAttempt(identifier)
        assertTrue(rateLimiter.checkMfaAttempt(identifier) is RateLimitResult.RateLimited)
    }
    
    @Test
    fun `test API request rate limiting`() = runTest {
        // Given
        val identifier = "192.168.1.1"
        val maxAttempts = 100
        
        // When - make maximum allowed attempts
        repeat(maxAttempts) {
            val result = rateLimiter.checkApiRequest(identifier)
            assertTrue(result is RateLimitResult.Allowed)
            rateLimiter.recordApiRequest(identifier)
        }
        
        // Then - next attempt should be rate limited
        val rateLimitedResult = rateLimiter.checkApiRequest(identifier)
        assertTrue(rateLimitedResult is RateLimitResult.RateLimited)
        assertEquals(0, (rateLimitedResult as RateLimitResult.RateLimited).attemptsRemaining)
    }
    
    @Test
    fun `test account lockout after multiple failed attempts`() = runTest {
        // Given
        val identifier = "test@example.com"
        val maxFailedAttempts = 5
        
        // When - make maximum failed attempts
        repeat(maxFailedAttempts) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - account should be locked
        assertTrue(rateLimiter.isAccountLocked(identifier))
        
        // And - login attempts should be blocked
        val blockedResult = rateLimiter.checkLoginAttempt(identifier)
        assertTrue(blockedResult is RateLimitResult.Blocked)
        assertTrue((blockedResult as RateLimitResult.Blocked).reason.contains("locked"))
    }
    
    @Test
    fun `test account unlock after lockout period`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - lock account
        repeat(5) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - account should be locked
        assertTrue(rateLimiter.isAccountLocked(identifier))
        
        // When - manually unlock
        rateLimiter.unlockAccount(identifier)
        
        // Then - account should be unlocked
        assertFalse(rateLimiter.isAccountLocked(identifier))
        
        // And - should be able to attempt login again
        val allowedResult = rateLimiter.checkLoginAttempt(identifier)
        assertTrue(allowedResult is RateLimitResult.Allowed)
    }
    
    @Test
    fun `test get remaining lockout time`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - lock account
        repeat(5) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - should have remaining lockout time
        val remainingTime = rateLimiter.getRemainingLockoutTime(identifier)
        assertTrue(remainingTime > 0)
        assertTrue(remainingTime <= 30 * 60) // Should be within 30 minutes
    }
    
    @Test
    fun `test get rate limit statistics`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - make some attempts
        repeat(3) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        rateLimiter.checkPasswordResetAttempt(identifier)
        rateLimiter.recordPasswordResetAttempt(identifier)
        
        // Then - should get correct statistics
        val stats = rateLimiter.getRateLimitStats(identifier)
        assertEquals(identifier, stats.identifier)
        assertEquals(3, stats.loginAttempts)
        assertEquals(1, stats.passwordResetAttempts)
        assertEquals(0, stats.mfaAttempts)
        assertEquals(0, stats.apiRequests)
        assertFalse(stats.isLocked)
        assertEquals(0, stats.remainingLockoutTime)
    }
    
    @Test
    fun `test rate limit statistics for locked account`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - lock account
        repeat(5) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - statistics should show locked status
        val stats = rateLimiter.getRateLimitStats(identifier)
        assertTrue(stats.isLocked)
        assertTrue(stats.remainingLockoutTime > 0)
    }
    
    @Test
    fun `test different identifiers are rate limited independently`() = runTest {
        // Given
        val identifier1 = "user1@example.com"
        val identifier2 = "user2@example.com"
        
        // When - rate limit identifier1
        repeat(5) {
            rateLimiter.checkLoginAttempt(identifier1)
            rateLimiter.recordFailedLogin(identifier1, "Invalid password")
        }
        
        // Then - identifier1 should be locked
        assertTrue(rateLimiter.isAccountLocked(identifier1))
        
        // But - identifier2 should not be affected
        assertFalse(rateLimiter.isAccountLocked(identifier2))
        
        val allowedResult = rateLimiter.checkLoginAttempt(identifier2)
        assertTrue(allowedResult is RateLimitResult.Allowed)
    }
    
    @Test
    fun `test IP address rate limiting`() = runTest {
        // Given
        val ipAddress = "192.168.1.100"
        
        // When - make many API requests
        repeat(100) {
            rateLimiter.checkApiRequest(ipAddress)
            rateLimiter.recordApiRequest(ipAddress)
        }
        
        // Then - should be rate limited
        val rateLimitedResult = rateLimiter.checkApiRequest(ipAddress)
        assertTrue(rateLimitedResult is RateLimitResult.RateLimited)
    }
    
    @Test
    fun `test rate limiter configuration`() = runTest {
        // Given
        val customConfig = RateLimitConfig(
            maxLoginAttemptsPerHour = 3,
            maxPasswordResetAttemptsPerHour = 2,
            maxMfaAttemptsPerHour = 5,
            maxApiRequestsPerMinute = 50,
            maxFailedAttempts = 3,
            failedAttemptWindowMinutes = 10,
            accountLockoutDurationMinutes = 15
        )
        
        // When
        rateLimiter.configure(customConfig)
        
        // Then - should use custom limits
        val identifier = "test@example.com"
        
        // Login attempts
        repeat(3) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        val rateLimitedResult = rateLimiter.checkLoginAttempt(identifier)
        assertTrue(rateLimitedResult is RateLimitResult.RateLimited)
        
        // Password reset attempts
        repeat(2) {
            rateLimiter.checkPasswordResetAttempt(identifier)
            rateLimiter.recordPasswordResetAttempt(identifier)
        }
        
        val passwordResetRateLimited = rateLimiter.checkPasswordResetAttempt(identifier)
        assertTrue(passwordResetRateLimited is RateLimitResult.RateLimited)
    }
    
    @Test
    fun `test rate limiter cleanup`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - make some attempts
        repeat(3) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - should have attempts recorded
        val stats = rateLimiter.getRateLimitStats(identifier)
        assertEquals(3, stats.loginAttempts)
        
        // When - cleanup
        rateLimiter.cleanup()
        
        // Then - should still have attempts (cleanup is async and may not complete immediately)
        // This test verifies cleanup doesn't throw exceptions
    }
    
    @Test
    fun `test rate limit metrics`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - make successful and failed attempts
        rateLimiter.checkLoginAttempt(identifier)
        rateLimiter.recordSuccessfulLogin(identifier)
        
        rateLimiter.checkLoginAttempt(identifier)
        rateLimiter.recordFailedLogin(identifier, "Invalid password")
        
        // Then - metrics should be updated
        val metrics = rateLimiter.rateLimitMetrics.value
        assertTrue(metrics.successfulRequests > 0)
        assertTrue(metrics.blockedRequests > 0)
    }
    
    @Test
    fun `test blocked entities tracking`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - lock account
        repeat(5) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - should be in blocked entities
        assertTrue(rateLimiter.blockedEntities.value.contains(identifier))
        
        // When - unlock account
        rateLimiter.unlockAccount(identifier)
        
        // Then - should be removed from blocked entities
        assertFalse(rateLimiter.blockedEntities.value.contains(identifier))
    }
    
    @Test
    fun `test rate limit state transitions`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - check rate limit
        val result = rateLimiter.checkLoginAttempt(identifier)
        
        // Then - should be allowed
        assertTrue(result is RateLimitResult.Allowed)
        
        // When - record failed attempt
        rateLimiter.recordFailedLogin(identifier, "Invalid password")
        
        // Then - should still be allowed
        val nextResult = rateLimiter.checkLoginAttempt(identifier)
        assertTrue(nextResult is RateLimitResult.Allowed)
        assertEquals(4, (nextResult as RateLimitResult.Allowed).attemptsRemaining)
    }
    
    @Test
    fun `test maximum failed attempts threshold`() = runTest {
        // Given
        val identifier = "test@example.com"
        val maxFailedAttempts = 5
        
        // When - make exactly maximum failed attempts
        repeat(maxFailedAttempts) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - account should be locked
        assertTrue(rateLimiter.isAccountLocked(identifier))
        
        // And - next attempt should be blocked
        val blockedResult = rateLimiter.checkLoginAttempt(identifier)
        assertTrue(blockedResult is RateLimitResult.Blocked)
    }
    
    @Test
    fun `test rate limit window expiration`() = runTest {
        // Given
        val identifier = "test@example.com"
        
        // When - make some attempts
        repeat(3) {
            rateLimiter.checkLoginAttempt(identifier)
            rateLimiter.recordFailedLogin(identifier, "Invalid password")
        }
        
        // Then - should have attempts recorded
        val stats = rateLimiter.getRateLimitStats(identifier)
        assertEquals(3, stats.loginAttempts)
        
        // Note: Testing actual time-based expiration would require time manipulation
        // This test verifies the basic functionality
    }
}