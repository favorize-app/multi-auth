package app.multiauth.security

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals

/**
 * Tests for Phase 1 security implementations:
 * - Password hashing
 * - JWT token generation and validation
 * - Rate limiting
 */
class Phase1SecurityTest {
    
    @Test
    fun testPasswordHashing() {
        val password = "mySecurePassword123!"
        
        // Hash the password
        val hashedPassword = PasswordHasher.hashPassword(password)
        
        // Verify the hash is not empty and contains expected fields
        assertNotNull(hashedPassword.hash)
        assertNotNull(hashedPassword.salt)
        assertTrue(hashedPassword.hash.isNotEmpty())
        assertTrue(hashedPassword.salt.isNotEmpty())
        assertTrue(hashedPassword.iterations > 0)
        
        // Verify correct password validates
        assertTrue(PasswordHasher.verifyPassword(password, hashedPassword))
        
        // Verify wrong password fails
        assertFalse(PasswordHasher.verifyPassword("wrongPassword", hashedPassword))
        
        // Verify different hashes for same password (due to random salt)
        val hashedPassword2 = PasswordHasher.hashPassword(password)
        assertNotEquals(hashedPassword.hash, hashedPassword2.hash)
        assertNotEquals(hashedPassword.salt, hashedPassword2.salt)
        
        // But both should validate the same password
        assertTrue(PasswordHasher.verifyPassword(password, hashedPassword2))
    }
    
    @Test
    fun testJwtTokenGeneration() {
        val jwtManager = JwtTokenManager()
        val userId = "test_user_123"
        val email = "test@example.com"
        
        // Create an access token
        val accessToken = jwtManager.createAccessToken(userId, email)
        assertNotNull(accessToken)
        assertTrue(accessToken.isNotEmpty())
        assertTrue(accessToken.contains(".")) // JWT has dots
        
        // Create a refresh token
        val refreshToken = jwtManager.createRefreshToken(userId)
        assertNotNull(refreshToken)
        assertTrue(refreshToken.isNotEmpty())
        assertNotEquals(accessToken, refreshToken) // Should be different
        
        // Validate the access token
        when (val result = jwtManager.validateToken(accessToken)) {
            is TokenValidationResult.Valid -> {
                assertTrue(result.payload.sub == userId)
                assertTrue(result.payload.email == email)
                assertTrue(result.payload.tokenType == "access")
            }
            else -> throw AssertionError("Token validation should succeed")
        }
        
        // Validate the refresh token
        when (val result = jwtManager.validateToken(refreshToken)) {
            is TokenValidationResult.Valid -> {
                assertTrue(result.payload.sub == userId)
                assertTrue(result.payload.tokenType == "refresh")
            }
            else -> throw AssertionError("Refresh token validation should succeed")
        }
    }
    
    @Test
    fun testJwtTokenValidation() {
        val jwtManager = JwtTokenManager()
        
        // Test empty token
        when (val result = jwtManager.validateToken("")) {
            is TokenValidationResult.Invalid -> assertTrue(result.reason.contains("empty"))
            else -> throw AssertionError("Empty token should be invalid")
        }
        
        // Test malformed token
        when (val result = jwtManager.validateToken("invalid.token")) {
            is TokenValidationResult.Invalid -> assertTrue(result.reason.isNotEmpty())
            else -> throw AssertionError("Malformed token should be invalid")
        }
        
        // Test completely invalid token
        when (val result = jwtManager.validateToken("totally.invalid.token")) {
            is TokenValidationResult.Invalid -> assertTrue(result.reason.isNotEmpty())
            else -> throw AssertionError("Invalid token should be invalid")
        }
    }
    
    @Test
    fun testRateLimiting() {
        val rateLimiter = RateLimiter(maxAttempts = 3)
        val identifier = "test@example.com"
        
        // Should allow initial attempts
        when (val result = rateLimiter.checkRateLimit(identifier)) {
            is RateLimitResult.Allowed -> assertTrue(result.attemptsRemaining == 3)
            else -> throw AssertionError("Should allow initial attempt")
        }
        
        // Record failed attempts
        rateLimiter.recordFailedAttempt(identifier)
        rateLimiter.recordFailedAttempt(identifier)
        
        // Should still allow one more
        when (val result = rateLimiter.checkRateLimit(identifier)) {
            is RateLimitResult.Allowed -> assertTrue(result.attemptsRemaining == 1)
            else -> throw AssertionError("Should still allow attempts")
        }
        
        // Record one more failed attempt
        rateLimiter.recordFailedAttempt(identifier)
        
        // Now should be rate limited
        when (val result = rateLimiter.checkRateLimit(identifier)) {
            is RateLimitResult.RateLimited -> assertTrue(result.retryAfter.inWholeSeconds > 0)
            else -> throw AssertionError("Should be rate limited")
        }
        
        // Test successful attempt resets rate limiting
        rateLimiter.recordSuccessfulAttempt(identifier)
        when (val result = rateLimiter.checkRateLimit(identifier)) {
            is RateLimitResult.Allowed -> assertTrue(result.attemptsRemaining > 0)
            else -> throw AssertionError("Successful attempt should reset rate limiting")
        }
    }
    
    @Test
    fun testRateLimiterStatus() {
        val rateLimiter = RateLimiter(maxAttempts = 5)
        val identifier = "status@example.com"
        
        // Check initial status
        val initialStatus = rateLimiter.getRateLimitStatus(identifier)
        assertTrue(initialStatus.currentAttempts == 0)
        assertTrue(initialStatus.maxAttempts == 5)
        assertFalse(initialStatus.isLocked)
        
        // Record some failed attempts
        rateLimiter.recordFailedAttempt(identifier)
        rateLimiter.recordFailedAttempt(identifier)
        
        val statusAfterFailures = rateLimiter.getRateLimitStatus(identifier)
        assertTrue(statusAfterFailures.currentAttempts == 2)
        assertFalse(statusAfterFailures.isLocked)
    }
}