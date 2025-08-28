package app.multiauth.performance

import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import app.multiauth.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/**
 * Comprehensive load testing framework for performance benchmarking and stress testing.
 * Provides JMeter-like capabilities for testing authentication system performance.
 */
class LoadTestingFramework {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        // Test types
        const val TEST_TYPE_LOAD = "LOAD"
        const val TEST_TYPE_STRESS = "STRESS"
        const val TEST_TYPE_SPIKE = "SPIKE"
        const val TEST_TYPE_ENDURANCE = "ENDURANCE"
        
        // Default thresholds
        const val DEFAULT_RESPONSE_TIME_THRESHOLD_MS = 100
        const val DEFAULT_THROUGHPUT_THRESHOLD_RPS = 1000
        const val DEFAULT_ERROR_RATE_THRESHOLD = 0.01 // 1%
        
        // Performance targets
        const val TARGET_RESPONSE_TIME_MS = 50
        const val TARGET_THROUGHPUT_RPS = 5000
        const val TARGET_CONCURRENT_USERS = 10000
    }
    
    private val testResults = mutableListOf<TestResult>()
    private val performanceMetrics = mutableMapOf<String, PerformanceMetrics>()
    private val loadGenerators = mutableMapOf<String, LoadGenerator>()
    private val testScenarios = mutableMapOf<String, TestScenario>()
    
    /**
     * Executes a load test with specified parameters.
     * 
     * @param scenario The test scenario to execute
     * @return Load test result
     */
    suspend fun executeLoadTest(scenario: LoadTestScenario): LoadTestResult {
        return try {
            logger.info("performance", "Starting load test: ${scenario.name}")
            
            val startTime = Clock.System.now()
            val results = mutableListOf<TestResult>()
            
            // Create load generators
            val generators = createLoadGenerators(scenario)
            
            // Execute test phases
            scenario.phases.forEach { phase ->
                logger.info("performance", "Executing phase: ${phase.name}")
                val phaseResults = executeTestPhase(phase, generators)
                results.addAll(phaseResults)
                
                // Wait between phases if specified
                if (phase.delayBetweenPhases > 0) {
                    delay(phase.delayBetweenPhases * 1000L)
                }
            }
            
            val endTime = Clock.System.now()
            val duration = // Duration calculation required(startTime, endTime)
            
            // Calculate overall metrics
            val overallMetrics = calculateOverallMetrics(results)
            
            // Generate performance report
            val report = generatePerformanceReport(scenario, results, overallMetrics, duration)
            
            // Store results
            testResults.addAll(results)
            performanceMetrics[scenario.name] = overallMetrics
            
            logger.info("performance", "Load test completed: ${scenario.name}")
            
            LoadTestResult(
                scenarioName = scenario.name,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                totalRequests = results.size.toLong(),
                successfulRequests = results.count { it.isSuccessful }.toLong(),
                failedRequests = results.count { !it.isSuccessful }.toLong(),
                metrics = overallMetrics,
                report = report,
                timestamp = Clock.System.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Load test failed: ${e.message}")
            throw LoadTestException("Load test execution failed", e)
        }
    }
    
    /**
     * Executes a stress test to find system breaking points.
     * 
     * @param scenario The stress test scenario
     * @return Stress test result
     */
    suspend fun executeStressTest(scenario: StressTestScenario): StressTestResult {
        return try {
            logger.info("performance", "Starting stress test: ${scenario.name}")
            
            val startTime = Clock.System.now()
            val results = mutableListOf<TestResult>()
            var currentLoad = scenario.initialLoad
            var breakingPoint = 0
            var isSystemStable = true
            
            while (isSystemStable && currentLoad <= scenario.maxLoad) {
                logger.info("performance", "Testing with load: $currentLoad concurrent users")
                
                // Create stress test scenario
                val stressScenario = LoadTestScenario(
                    name = "${scenario.name}_stress_$currentLoad",
                    description = "Stress test with $currentLoad users",
                    phases = listOf(
                        TestPhase(
                            name = "ramp_up",
                            duration = scenario.rampUpDuration,
                            targetUsers = currentLoad,
                            rampUpTime = scenario.rampUpDuration / 2
                        ),
                        TestPhase(
                            name = "sustained_load",
                            duration = scenario.sustainedLoadDuration,
                            targetUsers = currentLoad,
                            rampUpTime = 0
                        )
                    )
                )
                
                // Execute stress test
                val stressResult = executeLoadTest(stressScenario)
                results.addAll(stressResult.report.detailedResults)
                
                // Check if system is still stable
                isSystemStable = isSystemStable(stressResult.metrics, scenario.stabilityThresholds)
                
                if (isSystemStable) {
                    breakingPoint = currentLoad
                    currentLoad += scenario.loadIncrement
                }
            }
            
            val endTime = Clock.System.now()
            val duration = // Duration calculation required(startTime, endTime)
            
            val result = StressTestResult(
                scenarioName = scenario.name,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                breakingPoint = breakingPoint,
                maxTestedLoad = currentLoad - scenario.loadIncrement,
                totalRequests = results.size.toLong(),
                successfulRequests = results.count { it.isSuccessful }.toLong(),
                failedRequests = results.count { !it.isSuccessful }.toLong(),
                timestamp = Clock.System.now()
            )
            
            logger.info("performance", "Stress test completed. Breaking point: $breakingPoint users")
            result
            
        } catch (e: Exception) {
            logger.error("performance", "Stress test failed: ${e.message}")
            throw LoadTestException("Stress test execution failed", e)
        }
    }
    
    /**
     * Executes a spike test to test system behavior under sudden load.
     * 
     * @param scenario The spike test scenario
     * @return Spike test result
     */
    suspend fun executeSpikeTest(scenario: SpikeTestScenario): SpikeTestResult {
        return try {
            logger.info("performance", "Starting spike test: ${scenario.name}")
            
            val startTime = Clock.System.now()
            
            // Create spike test scenario
            val spikeScenario = LoadTestScenario(
                name = "${scenario.name}_spike",
                description = "Spike test with sudden load increase",
                phases = listOf(
                    TestPhase(
                        name = "baseline",
                        duration = scenario.baselineDuration,
                        targetUsers = scenario.baselineLoad,
                        rampUpTime = scenario.baselineDuration / 4
                    ),
                    TestPhase(
                        name = "spike",
                        duration = scenario.spikeDuration,
                        targetUsers = scenario.spikeLoad,
                        rampUpTime = 0 // Immediate spike
                    ),
                    TestPhase(
                        name = "recovery",
                        duration = scenario.recoveryDuration,
                        targetUsers = scenario.baselineLoad,
                        rampUpTime = scenario.recoveryDuration / 4
                    )
                )
            )
            
            // Execute spike test
            val spikeResult = executeLoadTest(spikeScenario)
            
            // Analyze spike behavior
            val spikeAnalysis = analyzeSpikeBehavior(spikeResult)
            
            val endTime = Clock.System.now()
            val duration = // Duration calculation required(startTime, endTime)
            
            val result = SpikeTestResult(
                scenarioName = scenario.name,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                baselineLoad = scenario.baselineLoad,
                spikeLoad = scenario.spikeLoad,
                recoveryTime = spikeAnalysis.recoveryTime,
                performanceDegradation = spikeAnalysis.performanceDegradation,
                recoveryEfficiency = spikeAnalysis.recoveryEfficiency,
                timestamp = Clock.System.now()
            )
            
            logger.info("performance", "Spike test completed")
            result
            
        } catch (e: Exception) {
            logger.error("performance", "Spike test failed: ${e.message}")
            throw LoadTestException("Spike test execution failed", e)
        }
    }
    
    /**
     * Executes an endurance test for long-term stability.
     * 
     * @param scenario The endurance test scenario
     * @return Endurance test result
     */
    suspend fun executeEnduranceTest(scenario: EnduranceTestScenario): EnduranceTestResult {
        return try {
            logger.info("performance", "Starting endurance test: ${scenario.name}")
            
            val startTime = Clock.System.now()
            val results = mutableListOf<TestResult>()
            val checkpoints = mutableListOf<CheckpointResult>()
            
            // Execute test in intervals
            val intervalDuration = scenario.testDuration / scenario.checkpointIntervals
            var currentInterval = 0
            
            while (currentInterval < scenario.checkpointIntervals) {
                val intervalStart = Clock.System.now()
                
                // Execute interval test
                val intervalScenario = LoadTestScenario(
                    name = "${scenario.name}_interval_$currentInterval",
                    description = "Endurance test interval $currentInterval",
                    phases = listOf(
                        TestPhase(
                            name = "interval_load",
                            duration = intervalDuration,
                            targetUsers = scenario.targetLoad,
                            rampUpTime = intervalDuration / 10
                        )
                    )
                )
                
                val intervalResult = executeLoadTest(intervalScenario)
                results.addAll(intervalResult.report.detailedResults)
                
                // Create checkpoint
                val checkpoint = CheckpointResult(
                    intervalNumber = currentInterval,
                    timestamp = Clock.System.now(),
                    metrics = intervalResult.metrics,
                    isStable = isSystemStable(intervalResult.metrics, scenario.stabilityThresholds)
                )
                checkpoints.add(checkpoint)
                
                currentInterval++
                
                // Wait for next interval
                if (currentInterval < scenario.checkpointIntervals) {
                    delay(scenario.intervalDelay * 1000L)
                }
            }
            
            val endTime = Clock.System.now()
            val duration = // Duration calculation required(startTime, endTime)
            
            // Analyze endurance results
            val enduranceAnalysis = analyzeEnduranceResults(checkpoints)
            
            val result = EnduranceTestResult(
                scenarioName = scenario.name,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                targetLoad = scenario.targetLoad,
                totalRequests = results.size.toLong(),
                successfulRequests = results.count { it.isSuccessful }.toLong(),
                failedRequests = results.count { !it.isSuccessful }.toLong(),
                checkpoints = checkpoints,
                analysis = enduranceAnalysis,
                timestamp = Clock.System.now()
            )
            
            logger.info("performance", "Endurance test completed")
            result
            
        } catch (e: Exception) {
            logger.error("performance", "Endurance test failed: ${e.message}")
            throw LoadTestException("Endurance test execution failed", e)
        }
    }
    
    /**
     * Generates comprehensive performance report.
     * 
     * @param scenario The test scenario
     * @param results Test results
     * @param metrics Performance metrics
     * @param duration Test duration
     * @return Performance report
     */
    private fun generatePerformanceReport(
        scenario: LoadTestScenario,
        results: List<TestResult>,
        metrics: PerformanceMetrics,
        duration: Long
    ): PerformanceReport {
        val responseTimeDistribution = calculateResponseTimeDistribution(results)
        val throughputOverTime = calculateThroughputOverTime(results, duration)
        val errorAnalysis = analyzeErrors(results)
        val recommendations = generateRecommendations(metrics, scenario)
        
        return PerformanceReport(
            scenarioName = scenario.name,
            testType = scenario.testType,
            startTime = Clock.System.now().minus(duration, ChronoUnit.MILLIS),
            endTime = Clock.System.now(),
            duration = duration,
            totalRequests = results.size.toLong(),
            successfulRequests = results.count { it.isSuccessful }.toLong(),
            failedRequests = results.count { !it.isSuccessful }.toLong(),
            metrics = metrics,
            responseTimeDistribution = responseTimeDistribution,
            throughputOverTime = throughputOverTime,
            errorAnalysis = errorAnalysis,
            recommendations = recommendations,
            detailedResults = results,
            timestamp = Clock.System.now()
        )
    }
    
    // Private helper methods
    
    private suspend fun executeTestPhase(phase: TestPhase, generators: List<LoadGenerator>): List<TestResult> {
        val results = mutableListOf<TestResult>()
        val startTime = Clock.System.now()
        
        // Calculate user ramp-up
        val usersPerSecond = if (phase.rampUpTime > 0) {
            phase.targetUsers.toDouble() / phase.rampUpTime
        } else {
            phase.targetUsers.toDouble()
        }
        
        var currentUsers = 0
        var elapsedTime = 0L
        
        while (elapsedTime < phase.duration * 1000 && currentUsers < phase.targetUsers) {
            val phaseElapsed = // Duration calculation required(startTime, Clock.System.now())
            
            // Ramp up users
            if (phase.rampUpTime > 0) {
                currentUsers = (usersPerSecond * (phaseElapsed / 1000.0)).roundToInt()
                currentUsers = currentUsers.coerceAtMost(phase.targetUsers)
            } else {
                currentUsers = phase.targetUsers
            }
            
            // Execute requests with current user load
            val batchResults = executeUserBatch(currentUsers, generators)
            results.addAll(batchResults)
            
            // Wait for next batch
            delay(100) // 100ms intervals
            elapsedTime = // Duration calculation required(startTime, Clock.System.now())
        }
        
        return results
    }
    
    private suspend fun executeUserBatch(userCount: Int, generators: List<LoadGenerator>): List<TestResult> {
        val results = mutableListOf<TestResult>()
        val jobs = mutableListOf<Job>()
        
        // Distribute users across generators
        val usersPerGenerator = userCount / generators.size
        val remainingUsers = userCount % generators.size
        
        generators.forEachIndexed { index, generator ->
            val usersForGenerator = usersPerGenerator + if (index < remainingUsers) 1 else 0
            if (usersForGenerator > 0) {
                val job = launch {
                    val generatorResults = generator.generateLoad(usersForGenerator)
                    results.addAll(generatorResults)
                }
                jobs.add(job)
            }
        }
        
        // Wait for all generators to complete
        jobs.forEach { it.join() }
        
        return results
    }
    
    private fun createLoadGenerators(scenario: LoadTestScenario): List<LoadGenerator> {
        val generators = mutableListOf<LoadGenerator>()
        
        // Create multiple generators for distributed load
        val generatorCount = (scenario.phases.maxOfOrNull { it.targetUsers } ?: 100) / 1000 + 1
        
        repeat(generatorCount) { index ->
            val generator = LoadGenerator(
                id = "generator_$index",
                scenario = scenario,
                timestamp = Clock.System.now()
            )
            generators.add(generator)
            loadGenerators[generator.id] = generator
        }
        
        return generators
    }
    
    private fun calculateOverallMetrics(results: List<TestResult>): PerformanceMetrics {
        if (results.isEmpty()) {
            return PerformanceMetrics(
                responseTime = PerformanceMetrics.ResponseTimeMetrics(),
                throughput = PerformanceMetrics.ThroughputMetrics(),
                errorRate = 0.0,
                concurrentUsers = 0,
                timestamp = Clock.System.now()
            )
        }
        
        val responseTimes = results.mapNotNull { it.responseTime }
        val successfulRequests = results.filter { it.isSuccessful }
        val failedRequests = results.filter { !it.isSuccessful }
        
        val avgResponseTime = responseTimes.average()
        val minResponseTime = responseTimes.minOrNull() ?: 0.0
        val maxResponseTime = responseTimes.maxOrNull() ?: 0.0
        val p95ResponseTime = calculatePercentile(responseTimes, 95.0)
        val p99ResponseTime = calculatePercentile(responseTimes, 99.0)
        
        val totalDuration = // Duration calculation required(
            results.minOfOrNull { it.timestamp } ?: Clock.System.now(),
            results.maxOfOrNull { it.timestamp } ?: Clock.System.now()
        )
        
        val requestsPerSecond = if (totalDuration > 0) {
            results.size.toDouble() / totalDuration
        } else {
            0.0
        }
        
        val errorRate = if (results.isNotEmpty()) {
            failedRequests.size.toDouble() / results.size
        } else {
            0.0
        }
        
        return PerformanceMetrics(
            responseTime = PerformanceMetrics.ResponseTimeMetrics(
                average = avgResponseTime,
                minimum = minResponseTime,
                maximum = maxResponseTime,
                p95 = p95ResponseTime,
                p99 = p99ResponseTime
            ),
            throughput = PerformanceMetrics.ThroughputMetrics(
                requestsPerSecond = requestsPerSecond,
                totalRequests = results.size.toLong(),
                successfulRequests = successfulRequests.size.toLong(),
                failedRequests = failedRequests.size.toLong()
            ),
            errorRate = errorRate,
            concurrentUsers = results.groupBy { it.userId }.size,
            timestamp = Clock.System.now()
        )
    }
    
    private fun calculateResponseTimeDistribution(results: List<TestResult>): ResponseTimeDistribution {
        val responseTimes = results.mapNotNull { it.responseTime }
        
        val buckets = mutableMapOf<String, Int>()
        buckets["0-50ms"] = responseTimes.count { it <= 50 }
        buckets["50-100ms"] = responseTimes.count { it > 50 && it <= 100 }
        buckets["100-200ms"] = responseTimes.count { it > 100 && it <= 200 }
        buckets["200-500ms"] = responseTimes.count { it > 200 && it <= 500 }
        buckets["500ms+"] = responseTimes.count { it > 500 }
        
        return ResponseTimeDistribution(buckets)
    }
    
    private fun calculateThroughputOverTime(results: List<TestResult>, duration: Long): List<ThroughputPoint> {
        val throughputPoints = mutableListOf<ThroughputPoint>()
        val intervalSize = duration / 10 // 10 intervals
        
        repeat(10) { interval ->
            val intervalStart = interval * intervalSize
            val intervalEnd = (interval + 1) * intervalSize
            
            val intervalResults = results.filter { result ->
                val resultTime = // Duration calculation required(
                    results.minOfOrNull { it.timestamp } ?: Clock.System.now(),
                    result.timestamp
                )
                resultTime >= intervalStart && resultTime < intervalEnd
            }
            
            val throughput = if (intervalSize > 0) {
                intervalResults.size.toDouble() / (intervalSize / 1000.0)
            } else {
                0.0
            }
            
            throughputPoints.add(
                ThroughputPoint(
                    interval = interval,
                    timestamp = Clock.System.now().minus(duration - intervalEnd, ChronoUnit.MILLIS),
                    throughput = throughput
                )
            )
        }
        
        return throughputPoints
    }
    
    private fun analyzeErrors(results: List<TestResult>): ErrorAnalysis {
        val errors = results.filter { !it.isSuccessful }
        val errorTypes = errors.groupBy { it.errorType }.mapValues { it.value.size }
        
        return ErrorAnalysis(
            totalErrors = errors.size.toLong(),
            errorRate = if (results.isNotEmpty()) errors.size.toDouble() / results.size else 0.0,
            errorTypes = errorTypes,
            mostCommonError = errorTypes.maxByOrNull { it.value }?.key ?: "UNKNOWN"
        )
    }
    
    private fun generateRecommendations(metrics: PerformanceMetrics, scenario: LoadTestScenario): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Response time recommendations
        if (metrics.responseTime.average > TARGET_RESPONSE_TIME_MS) {
            recommendations.add("Response time exceeds target. Consider optimizing database queries and adding caching.")
        }
        
        // Throughput recommendations
        if (metrics.throughput.requestsPerSecond < TARGET_THROUGHPUT_RPS) {
            recommendations.add("Throughput below target. Consider horizontal scaling and load balancing.")
        }
        
        // Error rate recommendations
        if (metrics.errorRate > DEFAULT_ERROR_RATE_THRESHOLD) {
            recommendations.add("Error rate is high. Investigate system stability and error handling.")
        }
        
        // Concurrent users recommendations
        if (metrics.concurrentUsers < TARGET_CONCURRENT_USERS) {
            recommendations.add("System may not handle target concurrent users. Consider performance optimization.")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Performance meets targets. Continue monitoring for degradation.")
        }
        
        return recommendations
    }
    
    private fun isSystemStable(metrics: PerformanceMetrics, thresholds: StabilityThresholds): Boolean {
        return metrics.responseTime.average <= thresholds.maxResponseTime &&
               metrics.throughput.requestsPerSecond >= thresholds.minThroughput &&
               metrics.errorRate <= thresholds.maxErrorRate
    }
    
    private fun analyzeSpikeBehavior(result: LoadTestResult): SpikeAnalysis {
        // Analyze how system behaves during spike and recovery
        val baselineMetrics = result.report.metrics // Simplified for now
        val recoveryTime = 0L // Calculate based on metrics
        val performanceDegradation = 0.0 // Calculate degradation during spike
        val recoveryEfficiency = 1.0 // Calculate recovery efficiency
        
        return SpikeAnalysis(
            recoveryTime = recoveryTime,
            performanceDegradation = performanceDegradation,
            recoveryEfficiency = recoveryEfficiency
        )
    }
    
    private fun analyzeEnduranceResults(checkpoints: List<CheckpointResult>): EnduranceAnalysis {
        val stableCheckpoints = checkpoints.count { it.isStable }
        val totalCheckpoints = checkpoints.size
        val stabilityRate = if (totalCheckpoints > 0) {
            stableCheckpoints.toDouble() / totalCheckpoints
        } else {
            0.0
        }
        
        return EnduranceAnalysis(
            totalCheckpoints = totalCheckpoints.toLong(),
            stableCheckpoints = stableCheckpoints.toLong(),
            stabilityRate = stabilityRate,
            isEnduranceStable = stabilityRate >= 0.9 // 90% stability required
        )
    }
    
    private fun calculatePercentile(values: List<Double>, percentile: Double): Double {
        if (values.isEmpty()) return 0.0
        
        val sortedValues = values.sorted()
        val index = (percentile / 100.0 * (sortedValues.size - 1)).roundToInt()
        return sortedValuesOrNull(index) ?: 0.0
    }
}

// Data classes for load testing framework

@Serializable
data class LoadTestScenario(
    val name: String,
    val description: String,
    val testType: String = TEST_TYPE_LOAD,
    val phases: List<TestPhase>,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class TestPhase(
    val name: String,
    val duration: Int, // seconds
    val targetUsers: Int,
    val rampUpTime: Int = 0, // seconds
    val delayBetweenPhases: Int = 0 // seconds
)

@Serializable
data class LoadTestResult(
    val scenarioName: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Long,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val metrics: PerformanceMetrics,
    val report: PerformanceReport,
    val timestamp: Instant
)

@Serializable
data class StressTestScenario(
    val name: String,
    val description: String,
    val initialLoad: Int,
    val maxLoad: Int,
    val loadIncrement: Int,
    val rampUpDuration: Int,
    val sustainedLoadDuration: Int,
    val stabilityThresholds: StabilityThresholds
)

@Serializable
data class StressTestResult(
    val scenarioName: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Long,
    val breakingPoint: Int,
    val maxTestedLoad: Int,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val timestamp: Instant
)

@Serializable
data class SpikeTestScenario(
    val name: String,
    val description: String,
    val baselineLoad: Int,
    val spikeLoad: Int,
    val baselineDuration: Int,
    val spikeDuration: Int,
    val recoveryDuration: Int
)

@Serializable
data class SpikeTestResult(
    val scenarioName: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Long,
    val baselineLoad: Int,
    val spikeLoad: Int,
    val recoveryTime: Long,
    val performanceDegradation: Double,
    val recoveryEfficiency: Double,
    val timestamp: Instant
)

@Serializable
data class EnduranceTestScenario(
    val name: String,
    val description: String,
    val targetLoad: Int,
    val testDuration: Int, // seconds
    val checkpointIntervals: Int,
    val intervalDelay: Int = 0 // seconds
)

@Serializable
data class EnduranceTestResult(
    val scenarioName: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Long,
    val targetLoad: Int,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val checkpoints: List<CheckpointResult>,
    val analysis: EnduranceAnalysis,
    val timestamp: Instant
)

@Serializable
data class TestResult(
    val id: String,
    val userId: String,
    val timestamp: Instant,
    val responseTime: Double?,
    val isSuccessful: Boolean,
    val errorType: String?,
    val errorMessage: String?,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
// data class PerformanceMetrics(
//     val responseTime: ResponseTimeMetrics,
//     val throughput: ThroughputMetrics,
//     val errorRate: Double,
//     val concurrentUsers: Int,
//     val timestamp: Instant
// ) {
//     @Serializable
//     data class ResponseTimeMetrics(
//         val average: Double = 0.0,
//         val minimum: Double = 0.0,
//         val maximum: Double = 0.0,
//         val p95: Double = 0.0,
//         val p99: Double = 0.0
//     )
//     
//     @Serializable
//     data class ThroughputMetrics(
//         val requestsPerSecond: Double = 0.0,
//         val totalRequests: Long = 0L,
//         val successfulRequests: Long = 0L,
//         val failedRequests: Long = 0L
//     )
// }

@Serializable
data class PerformanceReport(
    val scenarioName: String,
    val testType: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Long,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val metrics: PerformanceMetrics,
    val responseTimeDistribution: ResponseTimeDistribution,
    val throughputOverTime: List<ThroughputPoint>,
    val errorAnalysis: ErrorAnalysis,
    val recommendations: List<String>,
    val detailedResults: List<TestResult>,
    val timestamp: Instant
)

@Serializable
data class ResponseTimeDistribution(
    val buckets: Map<String, Int>
)

@Serializable
data class ThroughputPoint(
    val interval: Int,
    val timestamp: Instant,
    val throughput: Double
)

@Serializable
data class ErrorAnalysis(
    val totalErrors: Long,
    val errorRate: Double,
    val errorTypes: Map<String, Int>,
    val mostCommonError: String
)

@Serializable
data class StabilityThresholds(
    val maxResponseTime: Double = DEFAULT_RESPONSE_TIME_THRESHOLD_MS.toDouble(),
    val minThroughput: Double = DEFAULT_THROUGHPUT_THRESHOLD_RPS.toDouble(),
    val maxErrorRate: Double = DEFAULT_ERROR_RATE_THRESHOLD
)

@Serializable
data class SpikeAnalysis(
    val recoveryTime: Long,
    val performanceDegradation: Double,
    val recoveryEfficiency: Double
)

@Serializable
data class CheckpointResult(
    val intervalNumber: Int,
    val timestamp: Instant,
    val metrics: PerformanceMetrics,
    val isStable: Boolean
)

@Serializable
data class EnduranceAnalysis(
    val totalCheckpoints: Long,
    val stableCheckpoints: Long,
    val stabilityRate: Double,
    val isEnduranceStable: Boolean
)

@Serializable
data class LoadGenerator(
    val id: String,
    val scenario: LoadTestScenario,
    val timestamp: Instant
) {
    suspend fun generateLoad(userCount: Int): List<TestResult> {
        // Simulate load generation
        val results = mutableListOf<TestResult>()
        
        repeat(userCount) { userIndex ->
            val startTime = Clock.System.now()
            
            // Simulate authentication request
            delay((100..500).random().toLong()) // Random delay
            
            val endTime = Clock.System.now()
            val responseTime = // Duration calculation required(startTime, endTime)
            
            val isSuccessful = (0..100).random() > 5 // 95% success rate
            
            val result = TestResult(
                id = "test_${Clock.System.now().epochSeconds}_$userIndex",
                userId = "user_$userIndex",
                timestamp = endTime,
                responseTime = responseTime.toDouble(),
                isSuccessful = isSuccessful,
                errorType = if (!isSuccessful) "AUTH_FAILURE" else null,
                errorMessage = if (!isSuccessful) "Authentication failed" else null
            )
            
            results.add(result)
        }
        
        return results
    }
}

/**
 * Exception thrown when load testing operations fail.
 */
class LoadTestException(message: String, cause: Throwable? = null) : Exception(message, cause)