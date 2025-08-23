package app.multiauth.testing

import app.multiauth.mfa.MfaManager
import app.multiauth.mfa.MfaMethod
import app.multiauth.mfa.TotpGenerator
import app.multiauth.models.User
import app.multiauth.security.RateLimiter
import app.multiauth.security.SecurityAuditLogger
import app.multiauth.security.SecurityEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Performance testing utilities for the Multi-Auth system.
 * Provides benchmarks and performance measurements for critical operations.
 */
class PerformanceTest {
    
    companion object {
        private const val WARMUP_ITERATIONS = 100
        private const val BENCHMARK_ITERATIONS = 1000
        private const val PERFORMANCE_THRESHOLD_MS = 100.0 // 100ms threshold for most operations
        private const val CRITICAL_OPERATION_THRESHOLD_MS = 50.0 // 50ms for critical operations
    }
    
    @Test
    fun `test TOTP generation performance`() = runTest {
        // Given
        val totpGenerator = TotpGenerator()
        val secret = totpGenerator.generateSecret()
        
        // Warmup
        repeat(WARMUP_ITERATIONS) {
            totpGenerator.generateTotp(secret)
        }
        
        // Benchmark
        val startTime = System.nanoTime()
        repeat(BENCHMARK_ITERATIONS) {
            totpGenerator.generateTotp(secret)
        }
        val endTime = System.nanoTime()
        
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val averageTimeMs = totalTimeMs / BENCHMARK_ITERATIONS
        
        println("TOTP Generation Performance:")
        println("  Total time: ${String.format("%.2f", totalTimeMs)}ms")
        println("  Average time: ${String.format("%.4f", averageTimeMs)}ms")
        println("  Operations per second: ${String.format("%.0f", BENCHMARK_ITERATIONS / (totalTimeMs / 1000))}")
        
        // Assertions
        assertTrue(averageTimeMs < CRITICAL_OPERATION_THRESHOLD_MS, 
            "TOTP generation should be fast: ${String.format("%.4f", averageTimeMs)}ms")
        assertTrue(totalTimeMs < PERFORMANCE_THRESHOLD_MS * BENCHMARK_ITERATIONS,
            "Total TOTP generation time should be reasonable: ${String.format("%.2f", totalTimeMs)}ms")
    }
    
    @Test
    fun `test TOTP validation performance`() = runTest {
        // Given
        val totpGenerator = TotpGenerator()
        val secret = totpGenerator.generateSecret()
        val totp = totpGenerator.generateTotp(secret)
        
        // Warmup
        repeat(WARMUP_ITERATIONS) {
            totpGenerator.validateTotp(secret, totp)
        }
        
        // Benchmark
        val startTime = System.nanoTime()
        repeat(BENCHMARK_ITERATIONS) {
            totpGenerator.validateTotp(secret, totp)
        }
        val endTime = System.nanoTime()
        
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val averageTimeMs = totalTimeMs / BENCHMARK_ITERATIONS
        
        println("TOTP Validation Performance:")
        println("  Total time: ${String.format("%.2f", totalTimeMs)}ms")
        println("  Average time: ${String.format("%.4f", averageTimeMs)}ms")
        println("  Operations per second: ${String.format("%.0f", BENCHMARK_ITERATIONS / (totalTimeMs / 1000))}")
        
        // Assertions
        assertTrue(averageTimeMs < CRITICAL_OPERATION_THRESHOLD_MS,
            "TOTP validation should be fast: ${String.format("%.4f", averageTimeMs)}ms")
    }
    
    @Test
    fun `test rate limiter performance`() = runTest {
        // Given
        val rateLimiter = RateLimiter()
        val identifier = "test@example.com"
        
        // Warmup
        repeat(WARMUP_ITERATIONS) {
            rateLimiter.checkLoginAttempt(identifier)
        }
        
        // Benchmark
        val startTime = System.nanoTime()
        repeat(BENCHMARK_ITERATIONS) {
            rateLimiter.checkLoginAttempt(identifier)
        }
        val endTime = System.nanoTime()
        
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val averageTimeMs = totalTimeMs / BENCHMARK_ITERATIONS
        
        println("Rate Limiter Performance:")
        println("  Total time: ${String.format("%.2f", totalTimeMs)}ms")
        println("  Average time: ${String.format("%.4f", averageTimeMs)}ms")
        println("  Operations per second: ${String.format("%.0f", BENCHMARK_ITERATIONS / (totalTimeMs / 1000))}")
        
        // Assertions
        assertTrue(averageTimeMs < CRITICAL_OPERATION_THRESHOLD_MS,
            "Rate limiter checks should be fast: ${String.format("%.4f", averageTimeMs)}ms")
    }
    
    @Test
    fun `test security audit logging performance`() = runTest {
        // Given
        val auditLogger = SecurityAuditLogger()
        val user = createTestUser()
        
        // Warmup
        repeat(WARMUP_ITERATIONS) {
            auditLogger.logSecurityEvent(SecurityEvent.AUTHENTICATION_SUCCESS, user)
        }
        
        // Benchmark
        val startTime = System.nanoTime()
        repeat(BENCHMARK_ITERATIONS) {
            auditLogger.logSecurityEvent(SecurityEvent.AUTHENTICATION_SUCCESS, user)
        }
        val endTime = System.nanoTime()
        
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val averageTimeMs = totalTimeMs / BENCHMARK_ITERATIONS
        
        println("Security Audit Logging Performance:")
        println("  Total time: ${String.format("%.2f", totalTimeMs)}ms")
        println("  Average time: ${String.format("%.4f", averageTimeMs)}ms")
        println("  Operations per second: ${String.format("%.0f", BENCHMARK_ITERATIONS / (totalTimeMs / 1000))}")
        
        // Assertions
        assertTrue(averageTimeMs < PERFORMANCE_THRESHOLD_MS,
            "Security audit logging should be reasonably fast: ${String.format("%.4f", averageTimeMs)}ms")
    }
    
    @Test
    fun `test MFA manager performance`() = runTest {
        // Given
        val mfaManager = MfaManager(MockAuthEngine(), MockEventBus())
        val user = createTestUser()
        
        // Warmup
        repeat(WARMUP_ITERATIONS / 10) { // Fewer iterations due to longer operation time
            mfaManager.enableMfa(user, MfaMethod.TOTP)
        }
        
        // Benchmark
        val startTime = System.nanoTime()
        repeat(BENCHMARK_ITERATIONS / 10) { // Fewer iterations due to longer operation time
            mfaManager.enableMfa(user, MfaMethod.TOTP)
        }
        val endTime = System.nanoTime()
        
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val averageTimeMs = totalTimeMs / (BENCHMARK_ITERATIONS / 10)
        
        println("MFA Manager Performance:")
        println("  Total time: ${String.format("%.2f", totalTimeMs)}ms")
        println("  Average time: ${String.format("%.4f", averageTimeMs)}ms")
        println("  Operations per second: ${String.format("%.0f", (BENCHMARK_ITERATIONS / 10) / (totalTimeMs / 1000))}")
        
        // Assertions
        assertTrue(averageTimeMs < PERFORMANCE_THRESHOLD_MS,
            "MFA operations should be reasonably fast: ${String.format("%.4f", averageTimeMs)}ms")
    }
    
    @Test
    fun `test concurrent operations performance`() = runTest {
        // Given
        val totpGenerator = TotpGenerator()
        val secret = totpGenerator.generateSecret()
        val concurrentOperations = 100
        
        // Benchmark concurrent operations
        val startTime = System.nanoTime()
        
        val jobs = List(concurrentOperations) {
            kotlinx.coroutines.launch {
                repeat(10) {
                    totpGenerator.generateTotp(secret)
                }
            }
        }
        
        jobs.forEach { it.join() }
        
        val endTime = System.nanoTime()
        
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val totalOperations = concurrentOperations * 10
        val averageTimeMs = totalTimeMs / totalOperations
        
        println("Concurrent Operations Performance:")
        println("  Concurrent operations: $concurrentOperations")
        println("  Total operations: $totalOperations")
        println("  Total time: ${String.format("%.2f", totalTimeMs)}ms")
        println("  Average time: ${String.format("%.4f", averageTimeMs)}ms")
        println("  Operations per second: ${String.format("%.0f", totalOperations / (totalTimeMs / 1000))}")
        
        // Assertions
        assertTrue(averageTimeMs < PERFORMANCE_THRESHOLD_MS,
            "Concurrent operations should be reasonably fast: ${String.format("%.4f", averageTimeMs)}ms")
    }
    
    @Test
    fun `test memory usage during high load`() = runTest {
        // Given
        val totpGenerator = TotpGenerator()
        val secrets = List(1000) { totpGenerator.generateSecret() }
        
        // Measure memory before
        val runtime = Runtime.getRuntime()
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()
        
        // Generate many TOTPs
        val totps = secrets.map { secret ->
            List(100) { totpGenerator.generateTotp(secret) }
        }
        
        // Measure memory after
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsed = memoryAfter - memoryBefore
        
        println("Memory Usage Test:")
        println("  Memory before: ${String.format("%.2f", memoryBefore / 1024.0 / 1024.0)}MB")
        println("  Memory after: ${String.format("%.2f", memoryAfter / 1024.0 / 1024.0)}MB")
        println("  Memory used: ${String.format("%.2f", memoryUsed / 1024.0 / 1024.0)}MB")
        println("  Total TOTPs generated: ${totps.flatten().size}")
        
        // Assertions
        assertTrue(memoryUsed < 100 * 1024 * 1024, // Less than 100MB
            "Memory usage should be reasonable: ${String.format("%.2f", memoryUsed / 1024.0 / 1024.0)}MB")
        
        // Verify all TOTPs are valid
        val validTotps = totps.flatten().count { it.length == 6 && it.all { char -> char.isDigit() } }
        assertEquals(totps.flatten().size, validTotps, "All generated TOTPs should be valid")
    }
    
    @Test
    fun `test performance under sustained load`() = runTest {
        // Given
        val totpGenerator = TotpGenerator()
        val secret = totpGenerator.generateSecret()
        val sustainedOperations = 10000
        
        // Measure performance over sustained load
        val startTime = System.nanoTime()
        
        repeat(sustainedOperations) {
            totpGenerator.generateTotp(secret)
            
            // Add some variation to simulate real-world usage
            if (it % 1000 == 0) {
                kotlinx.coroutines.delay(1) // Small delay every 1000 operations
            }
        }
        
        val endTime = System.nanoTime()
        
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val averageTimeMs = totalTimeMs / sustainedOperations
        
        println("Sustained Load Performance:")
        println("  Total operations: $sustainedOperations")
        println("  Total time: ${String.format("%.2f", totalTimeMs)}ms")
        println("  Average time: ${String.format("%.4f", averageTimeMs)}ms")
        println("  Operations per second: ${String.format("%.0f", sustainedOperations / (totalTimeMs / 1000))}")
        
        // Assertions
        assertTrue(averageTimeMs < PERFORMANCE_THRESHOLD_MS,
            "Performance should remain consistent under sustained load: ${String.format("%.4f", averageTimeMs)}ms")
        assertTrue(totalTimeMs < PERFORMANCE_THRESHOLD_MS * sustainedOperations,
            "Total time should be reasonable for sustained load: ${String.format("%.2f", totalTimeMs)}ms")
    }
    
    @Test
    fun `test performance regression detection`() = runTest {
        // Given
        val totpGenerator = TotpGenerator()
        val secret = totpGenerator.generateSecret()
        val iterations = 1000
        
        // Run multiple performance tests to detect regressions
        val performanceResults = List(5) { testRun ->
            val startTime = System.nanoTime()
            repeat(iterations) {
                totpGenerator.generateTotp(secret)
            }
            val endTime = System.nanoTime()
            
            val totalTimeMs = (endTime - startTime) / 1_000_000.0
            val averageTimeMs = totalTimeMs / iterations
            
            println("Performance Test Run ${testRun + 1}: ${String.format("%.4f", averageTimeMs)}ms")
            averageTimeMs
        }
        
        val minTime = performanceResults.minOrNull() ?: 0.0
        val maxTime = performanceResults.maxOrNull() ?: 0.0
        val avgTime = performanceResults.average()
        val variance = performanceResults.map { (it - avgTime) * (it - avgTime) }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        
        println("Performance Regression Analysis:")
        println("  Min time: ${String.format("%.4f", minTime)}ms")
        println("  Max time: ${String.format("%.4f", maxTime)}ms")
        println("  Average time: ${String.format("%.4f", avgTime)}ms")
        println("  Standard deviation: ${String.format("%.4f", standardDeviation)}ms")
        println("  Coefficient of variation: ${String.format("%.2f", (standardDeviation / avgTime) * 100)}%")
        
        // Assertions
        assertTrue(maxTime - minTime < avgTime * 0.5, // Max variation should be less than 50% of average
            "Performance should be consistent across runs")
        assertTrue(standardDeviation < avgTime * 0.2, // Standard deviation should be less than 20% of average
            "Performance should be stable")
        assertTrue(avgTime < CRITICAL_OPERATION_THRESHOLD_MS,
            "Average performance should meet critical operation threshold: ${String.format("%.4f", avgTime)}ms")
    }
    
    private fun createTestUser(): User {
        return User(
            id = "test-user-${System.currentTimeMillis()}",
            email = "test@example.com",
            displayName = "Test User",
            isEmailVerified = true,
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now()
        )
    }
}