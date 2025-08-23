package app.multiauth.test

import app.multiauth.auth.AuthEngineTest
import app.multiauth.auth.TOTPTest
import app.multiauth.database.DatabaseTest
import app.multiauth.events.EventBusTest
import app.multiauth.oauth.clients.GoogleOAuthClientTest
import app.multiauth.storage.SecureStorageTest
import kotlin.test.Test

/**
 * Test Runner Configuration
 * 
 * This file provides a centralized way to run all tests and configure test suites.
 * It can be used by IDEs and CI/CD systems to discover and execute tests.
 */

class TestRunner {
    
    /**
     * Main test suite that includes all test classes
     */
    @Test
    fun runAllTests() {
        // This method serves as a test suite entry point
        // Individual test classes will be discovered and run automatically
        println("Test Runner: All tests will be discovered and executed")
    }
    
    /**
     * Get all test classes for manual test execution
     */
    fun getAllTestClasses(): List<Class<*>> {
        return listOf(
            AuthEngineTest::class.java,
            TOTPTest::class.java,
            DatabaseTest::class.java,
            EventBusTest::class.java,
            GoogleOAuthClientTest::class.java,
            SecureStorageTest::class.java
        )
    }
    
    /**
     * Get test classes by category
     */
    fun getTestClassesByCategory(category: TestCategory): List<Class<*>> {
        return when (category) {
            TestCategory.AUTH -> listOf(
                AuthEngineTest::class.java,
                TOTPTest::class.java
            )
            TestCategory.DATABASE -> listOf(
                DatabaseTest::class.java
            )
            TestCategory.EVENTS -> listOf(
                EventBusTest::class.java
            )
            TestCategory.OAUTH -> listOf(
                GoogleOAuthClientTest::class.java
            )
            TestCategory.STORAGE -> listOf(
                SecureStorageTest::class.java
            )
            TestCategory.ALL -> getAllTestClasses()
        }
    }
    
    /**
     * Get test statistics
     */
    fun getTestStatistics(): TestStatistics {
        val allTests = getAllTestClasses()
        return TestStatistics(
            totalTestClasses = allTests.size,
            categories = TestCategory.values().size,
            estimatedTestMethods = allTests.size * 20 // Rough estimate
        )
    }
}

/**
 * Test categories for organizing tests
 */
enum class TestCategory {
    AUTH,       // Authentication related tests
    DATABASE,   // Database and storage tests
    EVENTS,     // Event system tests
    OAUTH,      // OAuth provider tests
    STORAGE,    // Secure storage tests
    ALL         // All tests
}

/**
 * Test statistics information
 */
data class TestStatistics(
    val totalTestClasses: Int,
    val categories: Int,
    val estimatedTestMethods: Int
)

/**
 * Test configuration constants
 */
object TestConfig {
    const val TIMEOUT_MS = 30000L
    const val MAX_CONCURRENT_TESTS = 4
    const val RETRY_COUNT = 2
    
    // Test data constants
    const val TEST_USER_EMAIL = "test@example.com"
    const val TEST_USER_PASSWORD = "testPassword123!"
    const val TEST_USER_DISPLAY_NAME = "Test User"
    
    // OAuth test constants
    const val TEST_OAUTH_CLIENT_ID = "test-client-id"
    const val TEST_OAUTH_CLIENT_SECRET = "test-client-secret"
    const val TEST_OAUTH_REDIRECT_URI = "https://example.com/callback"
    
    // Database test constants
    const val TEST_DATABASE_NAME = "test_multiauth.db"
    const val TEST_DATABASE_VERSION = 1
    
    // Performance test constants
    const val PERFORMANCE_ITERATIONS = 1000
    const val PERFORMANCE_TIMEOUT_MS = 5000L
}

/**
 * Test utilities for common test operations
 */
object TestUtils {
    
    /**
     * Generate a unique test ID
     */
    fun generateTestId(prefix: String = "test"): String {
        return "${prefix}_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
    
    /**
     * Generate test email
     */
    fun generateTestEmail(prefix: String = "test"): String {
        val timestamp = System.currentTimeMillis()
        val random = (0..9999).random()
        return "${prefix}_${timestamp}_${random}@example.com"
    }
    
    /**
     * Generate test user data
     */
    fun generateTestUser(
        id: String = generateTestId("user"),
        email: String = generateTestEmail("user"),
        displayName: String = "Test User $id"
    ) = app.multiauth.models.User(
        id = id,
        email = email,
        displayName = displayName
    )
    
    /**
     * Generate test OAuth account data
     */
    fun generateTestOAuthAccount(
        id: String = generateTestId("oauth"),
        userId: String = generateTestId("user"),
        provider: String = "google",
        providerUserId: String = generateTestId("provider")
    ) = app.multiauth.models.OAuthAccount(
        id = id,
        userId = userId,
        provider = provider,
        providerUserId = providerUserId,
        accessToken = "access_token_$id",
        refreshToken = "refresh_token_$id",
        expiresAt = System.currentTimeMillis() + 3600000
    )
    
    /**
     * Generate test session data
     */
    fun generateTestSession(
        id: String = generateTestId("session"),
        userId: String = generateTestId("user"),
        token: String = "session_token_$id"
    ) = app.multiauth.models.Session(
        id = id,
        userId = userId,
        token = token,
        expiresAt = System.currentTimeMillis() + 86400000,
        createdAt = System.currentTimeMillis()
    )
    
    /**
     * Generate test audit log data
     */
    fun generateTestAuditLog(
        id: String = generateTestId("log"),
        userId: String = generateTestId("user"),
        action: String = "test_action",
        details: String = "Test audit log entry"
    ) = app.multiauth.database.AuditLog(
        id = id,
        userId = userId,
        action = action,
        details = details,
        ipAddress = "192.168.1.1",
        userAgent = "Test User Agent",
        timestamp = System.currentTimeMillis()
    )
    
    /**
     * Wait for a specified duration
     */
    suspend fun waitFor(durationMs: Long) {
        kotlinx.coroutines.delay(durationMs)
    }
    
    /**
     * Retry a test operation
     */
    suspend fun <T> retry(
        maxAttempts: Int = TestConfig.RETRY_COUNT,
        delayMs: Long = 100,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    waitFor(delayMs * (attempt + 1))
                }
            }
        }
        
        throw lastException ?: RuntimeException("Operation failed after $maxAttempts attempts")
    }
    
    /**
     * Measure execution time of an operation
     */
    suspend fun <T> measureTime(operation: suspend () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = operation()
        val endTime = System.currentTimeMillis()
        return result to (endTime - startTime)
    }
    
    /**
     * Assert that an operation completes within a timeout
     */
    suspend fun <T> assertTimeout(
        timeoutMs: Long = TestConfig.PERFORMANCE_TIMEOUT_MS,
        operation: suspend () -> T
    ): T {
        val (result, executionTime) = measureTime(operation)
        assertTrue(executionTime < timeoutMs, "Operation took ${executionTime}ms, expected less than ${timeoutMs}ms")
        return result
    }
}