package app.multiauth.performance

import app.multiauth.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicDouble
import kotlin.math.sqrt

/**
 * Comprehensive performance monitoring and metrics collection system.
 * Provides real-time performance tracking, alerting, and historical analysis.
 */
class PerformanceMonitoring {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        // Metric types
        const val METRIC_TYPE_COUNTER = "COUNTER"
        const val METRIC_TYPE_GAUGE = "GAUGE"
        const val METRIC_TYPE_HISTOGRAM = "HISTOGRAM"
        const val METRIC_TYPE_TIMER = "TIMER"
        
        // Alert severity levels
        const val ALERT_SEVERITY_LOW = "LOW"
        const val ALERT_SEVERITY_MEDIUM = "MEDIUM"
        const val ALERT_SEVERITY_HIGH = "HIGH"
        const val ALERT_SEVERITY_CRITICAL = "CRITICAL"
        
        // Default thresholds
        const val DEFAULT_CPU_THRESHOLD = 80.0
        const val DEFAULT_MEMORY_THRESHOLD = 85.0
        const val DEFAULT_RESPONSE_TIME_THRESHOLD = 200L
        const val DEFAULT_ERROR_RATE_THRESHOLD = 5.0
        const val DEFAULT_THROUGHPUT_THRESHOLD = 1000.0
        
        // Sampling intervals
        const val DEFAULT_SAMPLING_INTERVAL = 1000L // 1 second
        const val DEFAULT_AGGREGATION_INTERVAL = 60000L // 1 minute
        const val DEFAULT_RETENTION_PERIOD = 86400000L // 24 hours
    }
    
    private val metricsCollector = MetricsCollector()
    private val alertManager = AlertManager()
    private val performanceAnalyzer = PerformanceAnalyzer()
    private val resourceMonitor = ResourceMonitor()
    private val scheduledExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    
    // Performance tracking
    private val requestTracker = RequestTracker()
    private val errorTracker = ErrorTracker()
    private val resourceTracker = ResourceTracker()
    
    // Configuration
    private val monitoringConfig = MonitoringConfig()
    
    init {
        startMonitoringServices()
    }
    
    /**
     * Records a performance metric.
     * 
     * @param metric The metric to record
     * @return Recording result
     */
    suspend fun recordMetric(metric: PerformanceMetric): MetricRecordingResult {
        return try {
            logger.debug("performance", "Recording metric: ${metric.name}")
            
            // Validate metric
            val validationResult = validateMetric(metric)
            if (!validationResult.isValid) {
                logger.warn("performance", "Metric validation failed: ${validationResult.issues}")
                return MetricRecordingResult(
                    metricId = metric.id,
                    success = false,
                    issues = validationResult.issues,
                    timestamp = Instant.now()
                )
            }
            
            // Record metric
            metricsCollector.record(metric)
            
            // Check for alerts
            checkMetricAlerts(metric)
            
            // Update performance analysis
            performanceAnalyzer.updateAnalysis(metric)
            
            logger.debug("performance", "Metric recorded successfully: ${metric.name}")
            
            MetricRecordingResult(
                metricId = metric.id,
                success = true,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Failed to record metric: ${e.message}")
            MetricRecordingResult(
                metricId = metric.id,
                success = false,
                issues = listOf("Recording failed: ${e.message}"),
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Records a request performance measurement.
     * 
     * @param requestId Request identifier
     * @param startTime Request start time
     * @param endTime Request end time
     * @param success Whether request was successful
     * @param metadata Additional request metadata
     * @return Recording result
     */
    suspend fun recordRequestPerformance(
        requestId: String,
        startTime: Instant,
        endTime: Instant,
        success: Boolean,
        metadata: Map<String, String> = emptyMap()
    ): RequestPerformanceResult {
        return try {
            val duration = ChronoUnit.MILLIS.between(startTime, endTime)
            
            // Create timer metric
            val timerMetric = PerformanceMetric(
                id = generateMetricId(),
                name = "request_duration",
                type = METRIC_TYPE_TIMER,
                value = duration.toDouble(),
                unit = "milliseconds",
                timestamp = endTime,
                tags = mapOf(
                    "request_id" to requestId,
                    "success" to success.toString()
                ) + metadata
            )
            
            // Record timer metric
            recordMetric(timerMetric)
            
            // Track request statistics
            requestTracker.trackRequest(requestId, duration, success, metadata)
            
            // Check for performance alerts
            if (duration > DEFAULT_RESPONSE_TIME_THRESHOLD) {
                alertManager.createAlert(
                    Alert(
                        id = generateAlertId(),
                        type = "HIGH_RESPONSE_TIME",
                        severity = ALERT_SEVERITY_MEDIUM,
                        message = "Request duration exceeded threshold: ${duration}ms",
                        metric = timerMetric,
                        timestamp = Instant.now()
                    )
                )
            }
            
            RequestPerformanceResult(
                requestId = requestId,
                duration = duration,
                recorded = true,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Failed to record request performance: ${e.message}")
            RequestPerformanceResult(
                requestId = requestId,
                duration = 0,
                recorded = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Records an error occurrence.
     * 
     * @param error Error information
     * @return Recording result
     */
    suspend fun recordError(error: ErrorEvent): ErrorRecordingResult {
        return try {
            logger.debug("performance", "Recording error: ${error.type}")
            
            // Record error
            errorTracker.recordError(error)
            
            // Create error rate metric
            val errorRateMetric = PerformanceMetric(
                id = generateMetricId(),
                name = "error_rate",
                type = METRIC_TYPE_COUNTER,
                value = 1.0,
                unit = "count",
                timestamp = Instant.now(),
                tags = mapOf(
                    "error_type" to error.type,
                    "severity" to error.severity
                )
            )
            
            // Record error metric
            recordMetric(errorRateMetric)
            
            // Check error rate alerts
            val currentErrorRate = errorTracker.getErrorRate()
            if (currentErrorRate > DEFAULT_ERROR_RATE_THRESHOLD) {
                alertManager.createAlert(
                    Alert(
                        id = generateAlertId(),
                        type = "HIGH_ERROR_RATE",
                        severity = ALERT_SEVERITY_HIGH,
                        message = "Error rate exceeded threshold: ${currentErrorRate}%",
                        metric = errorRateMetric,
                        timestamp = Instant.now()
                    )
                )
            }
            
            ErrorRecordingResult(
                errorId = error.id,
                recorded = true,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Failed to record error: ${e.message}")
            ErrorRecordingResult(
                errorId = error.id,
                recorded = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Records resource usage metrics.
     * 
     * @param resourceType Type of resource
     * @param usage Current usage value
     * @param capacity Total capacity
     * @param unit Unit of measurement
     * @return Recording result
     */
    suspend fun recordResourceUsage(
        resourceType: String,
        usage: Double,
        capacity: Double,
        unit: String
    ): ResourceUsageResult {
        return try {
            val utilization = (usage / capacity) * 100
            
            // Create resource metric
            val resourceMetric = PerformanceMetric(
                id = generateMetricId(),
                name = "resource_utilization",
                type = METRIC_TYPE_GAUGE,
                value = utilization,
                unit = "percentage",
                timestamp = Instant.now(),
                tags = mapOf(
                    "resource_type" to resourceType,
                    "usage" to usage.toString(),
                    "capacity" to capacity.toString(),
                    "unit" to unit
                )
            )
            
            // Record resource metric
            recordMetric(resourceMetric)
            
            // Track resource usage
            resourceTracker.trackResourceUsage(resourceType, usage, capacity, unit)
            
            // Check resource alerts
            when (resourceType.lowercase()) {
                "cpu" -> {
                    if (utilization > DEFAULT_CPU_THRESHOLD) {
                        alertManager.createAlert(
                            Alert(
                                id = generateAlertId(),
                                type = "HIGH_CPU_USAGE",
                                severity = ALERT_SEVERITY_MEDIUM,
                                message = "CPU usage exceeded threshold: ${utilization}%",
                                metric = resourceMetric,
                                timestamp = Instant.now()
                            )
                        )
                    }
                }
                "memory" -> {
                    if (utilization > DEFAULT_MEMORY_THRESHOLD) {
                        alertManager.createAlert(
                            Alert(
                                id = generateAlertId(),
                                type = "HIGH_MEMORY_USAGE",
                                severity = ALERT_SEVERITY_MEDIUM,
                                message = "Memory usage exceeded threshold: ${utilization}%",
                                metric = resourceMetric,
                                timestamp = Instant.now()
                            )
                        )
                    }
                }
            }
            
            ResourceUsageResult(
                resourceType = resourceType,
                utilization = utilization,
                recorded = true,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Failed to record resource usage: ${e.message}")
            ResourceUsageResult(
                resourceType = resourceType,
                utilization = 0.0,
                recorded = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Gets current performance metrics.
     * 
     * @param metricNames Optional list of metric names to retrieve
     * @return Current performance metrics
     */
    suspend fun getCurrentMetrics(metricNames: List<String>? = null): CurrentMetricsResult {
        return try {
            logger.debug("performance", "Retrieving current metrics")
            
            val metrics = if (metricNames != null) {
                metricsCollector.getMetrics(metricNames)
            } else {
                metricsCollector.getAllMetrics()
            }
            
            val summary = generateMetricsSummary(metrics)
            
            CurrentMetricsResult(
                metrics = metrics,
                summary = summary,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Failed to retrieve current metrics: ${e.message}")
            throw PerformanceMonitoringException("Failed to retrieve metrics", e)
        }
    }
    
    /**
     * Gets performance metrics for a specific time range.
     * 
     * @param timeRange Time range for metrics
     * @param aggregation Aggregation interval
     * @return Historical metrics
     */
    suspend fun getHistoricalMetrics(
        timeRange: TimeRange,
        aggregation: Long = DEFAULT_AGGREGATION_INTERVAL
    ): HistoricalMetricsResult {
        return try {
            logger.debug("performance", "Retrieving historical metrics for range: ${timeRange.start} to ${timeRange.end}")
            
            val metrics = metricsCollector.getMetricsInRange(timeRange)
            val aggregatedMetrics = aggregateMetrics(metrics, aggregation)
            val trends = performanceAnalyzer.analyzeTrends(aggregatedMetrics)
            
            HistoricalMetricsResult(
                timeRange = timeRange,
                metrics = aggregatedMetrics,
                trends = trends,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Failed to retrieve historical metrics: ${e.message}")
            throw PerformanceMonitoringException("Failed to retrieve historical metrics", e)
        }
    }
    
    /**
     * Gets current system performance status.
     * 
     * @return Performance status report
     */
    suspend fun getPerformanceStatus(): PerformanceStatusReport {
        return try {
            logger.info("performance", "Generating performance status report")
            
            val currentMetrics = getCurrentMetrics()
            val alerts = alertManager.getActiveAlerts()
            val resourceStatus = resourceTracker.getResourceStatus()
            val requestStatus = requestTracker.getRequestStatus()
            val errorStatus = errorTracker.getErrorStatus()
            
            val overallHealth = calculateOverallPerformanceHealth(
                currentMetrics.summary,
                alerts,
                resourceStatus,
                requestStatus,
                errorStatus
            )
            
            val report = PerformanceStatusReport(
                currentMetrics = currentMetrics,
                alerts = alerts,
                resourceStatus = resourceStatus,
                requestStatus = requestStatus,
                errorStatus = errorStatus,
                overallHealth = overallHealth,
                recommendations = generatePerformanceRecommendations(
                    currentMetrics.summary,
                    alerts,
                    resourceStatus,
                    requestStatus,
                    errorStatus
                ),
                timestamp = Instant.now()
            )
            
            logger.info("performance", "Performance status report generated successfully")
            report
            
        } catch (e: Exception) {
            logger.error("performance", "Failed to generate performance status report: ${e.message}")
            throw PerformanceMonitoringException("Status report generation failed", e)
        }
    }
    
    /**
     * Configures monitoring settings.
     * 
     * @param config Monitoring configuration
     * @return Configuration result
     */
    suspend fun configureMonitoring(config: MonitoringConfig): ConfigurationResult {
        return try {
            logger.info("performance", "Configuring performance monitoring")
            
            monitoringConfig.updateFrom(config)
            
            // Update component configurations
            metricsCollector.configure(config.metricsConfig)
            alertManager.configure(config.alertConfig)
            performanceAnalyzer.configure(config.analysisConfig)
            
            logger.info("performance", "Performance monitoring configured successfully")
            
            ConfigurationResult(
                component = "PerformanceMonitoring",
                success = true,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Performance monitoring configuration failed: ${e.message}")
            
            ConfigurationResult(
                component = "PerformanceMonitoring",
                success = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Clears old metrics based on retention policy.
     * 
     * @return Cleanup result
     */
    suspend fun cleanupOldMetrics(): CleanupResult {
        return try {
            logger.info("performance", "Cleaning up old metrics")
            
            val cutoffTime = Instant.now().minus(monitoringConfig.retentionPeriod, ChronoUnit.MILLIS)
            val removedCount = metricsCollector.removeMetricsBefore(cutoffTime)
            
            logger.info("performance", "Cleaned up $removedCount old metrics")
            
            CleanupResult(
                removedCount = removedCount,
                cutoffTime = cutoffTime,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("performance", "Metrics cleanup failed: ${e.message}")
            throw PerformanceMonitoringException("Metrics cleanup failed", e)
        }
    }
    
    // Private helper methods
    
    private fun startMonitoringServices() {
        // Start metrics collection
        scheduledExecutor.scheduleAtFixedRate({
            try {
                collectSystemMetrics()
            } catch (e: Exception) {
                logger.error("performance", "System metrics collection failed: ${e.message}")
            }
        }, 0, monitoringConfig.samplingInterval, TimeUnit.MILLISECONDS)
        
        // Start metrics aggregation
        scheduledExecutor.scheduleAtFixedRate({
            try {
                aggregateMetrics()
            } catch (e: Exception) {
                logger.error("performance", "Metrics aggregation failed: ${e.message}")
            }
        }, 0, monitoringConfig.aggregationInterval, TimeUnit.MILLISECONDS)
        
        // Start cleanup
        scheduledExecutor.scheduleAtFixedRate({
            try {
                cleanupOldMetrics()
            } catch (e: Exception) {
                logger.error("performance", "Metrics cleanup failed: ${e.message}")
            }
        }, 3600000, 3600000, TimeUnit.MILLISECONDS) // Every hour
    }
    
    private suspend fun collectSystemMetrics() {
        // Collect system resource metrics
        val cpuUsage = resourceMonitor.getCpuUsage()
        val memoryUsage = resourceMonitor.getMemoryUsage()
        val diskUsage = resourceMonitor.getDiskUsage()
        
        // Record resource metrics
        recordResourceUsage("cpu", cpuUsage, 100.0, "percentage")
        recordResourceUsage("memory", memoryUsage, 100.0, "percentage")
        recordResourceUsage("disk", diskUsage, 100.0, "percentage")
    }
    
    private suspend fun aggregateMetrics() {
        // Aggregate metrics for analysis
        performanceAnalyzer.aggregateMetrics()
    }
    
    private fun validateMetric(metric: PerformanceMetric): MetricValidationResult {
        val issues = mutableListOf<String>()
        
        if (metric.name.isBlank()) {
            issues.add("Metric name cannot be blank")
        }
        
        if (metric.value < 0 && metric.type != METRIC_TYPE_GAUGE) {
            issues.add("Metric value cannot be negative for type: ${metric.type}")
        }
        
        if (metric.timestamp.isAfter(Instant.now().plus(1, ChronoUnit.MINUTES))) {
            issues.add("Metric timestamp cannot be in the future")
        }
        
        return MetricValidationResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
    
    private suspend fun checkMetricAlerts(metric: PerformanceMetric) {
        // Check if metric exceeds any alert thresholds
        val thresholds = monitoringConfig.alertConfig.thresholds
        thresholds.forEach { threshold ->
            if (threshold.metricName == metric.name && metric.value > threshold.value) {
                alertManager.createAlert(
                    Alert(
                        id = generateAlertId(),
                        type = "METRIC_THRESHOLD_EXCEEDED",
                        severity = threshold.severity,
                        message = "Metric ${metric.name} exceeded threshold: ${metric.value} > ${threshold.value}",
                        metric = metric,
                        timestamp = Instant.now()
                    )
                )
            }
        }
    }
    
    private fun generateMetricsSummary(metrics: List<PerformanceMetric>): MetricsSummary {
        if (metrics.isEmpty()) {
            return MetricsSummary()
        }
        
        val counters = metrics.filter { it.type == METRIC_TYPE_COUNTER }
        val gauges = metrics.filter { it.type == METRIC_TYPE_GAUGE }
        val timers = metrics.filter { it.type == METRIC_TYPE_TIMER }
        
        return MetricsSummary(
            totalMetrics = metrics.size.toLong(),
            counterMetrics = counters.size.toLong(),
            gaugeMetrics = gauges.size.toLong(),
            timerMetrics = timers.size.toLong(),
            averageValue = metrics.map { it.value }.average(),
            minValue = metrics.map { it.value }.minOrNull() ?: 0.0,
            maxValue = metrics.map { it.value }.maxOrNull() ?: 0.0,
            timestamp = Instant.now()
        )
    }
    
    private suspend fun aggregateMetrics(
        metrics: List<PerformanceMetric>,
        interval: Long
    ): List<AggregatedMetric> {
        val groupedMetrics = metrics.groupBy { metric ->
            val timestamp = metric.timestamp.toEpochMilli()
            val bucket = timestamp - (timestamp % interval)
            Instant.ofEpochMilli(bucket)
        }
        
        return groupedMetrics.map { (timestamp, metricGroup) ->
            val values = metricGroup.map { it.value }
            AggregatedMetric(
                timestamp = timestamp,
                count = metricGroup.size.toLong(),
                sum = values.sum(),
                average = values.average(),
                min = values.minOrNull() ?: 0.0,
                max = values.maxOrNull() ?: 0.0,
                standardDeviation = calculateStandardDeviation(values)
            )
        }.sortedBy { it.timestamp }
    }
    
    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
    
    private fun calculateOverallPerformanceHealth(
        metricsSummary: MetricsSummary,
        alerts: List<Alert>,
        resourceStatus: ResourceStatus,
        requestStatus: RequestStatus,
        errorStatus: ErrorStatus
    ): OverallPerformanceHealth {
        var score = 100.0
        
        // Deduct points for alerts
        alerts.forEach { alert ->
            score -= when (alert.severity) {
                ALERT_SEVERITY_LOW -> 5.0
                ALERT_SEVERITY_MEDIUM -> 15.0
                ALERT_SEVERITY_HIGH -> 30.0
                ALERT_SEVERITY_CRITICAL -> 50.0
                else -> 10.0
            }
        }
        
        // Deduct points for resource issues
        if (resourceStatus.cpuUtilization > 90.0) score -= 20.0
        if (resourceStatus.memoryUtilization > 90.0) score -= 20.0
        if (resourceStatus.diskUtilization > 90.0) score -= 15.0
        
        // Deduct points for request issues
        if (requestStatus.errorRate > 10.0) score -= 25.0
        if (requestStatus.averageResponseTime > 500) score -= 20.0
        
        // Deduct points for error issues
        if (errorStatus.errorRate > 5.0) score -= 20.0
        
        return OverallPerformanceHealth(
            score = score.coerceIn(0.0, 100.0),
            grade = calculatePerformanceGrade(score),
            status = if (score >= 80.0) "HEALTHY" else if (score >= 60.0) "DEGRADED" else "UNHEALTHY"
        )
    }
    
    private fun calculatePerformanceGrade(score: Double): String {
        return when {
            score >= 90.0 -> "A"
            score >= 80.0 -> "B"
            score >= 70.0 -> "C"
            score >= 60.0 -> "D"
            else -> "F"
        }
    }
    
    private fun generatePerformanceRecommendations(
        metricsSummary: MetricsSummary,
        alerts: List<Alert>,
        resourceStatus: ResourceStatus,
        requestStatus: RequestStatus,
        errorStatus: ErrorStatus
    ): List<PerformanceRecommendation> {
        val recommendations = mutableListOf<PerformanceRecommendation>()
        
        // Resource recommendations
        if (resourceStatus.cpuUtilization > 80.0) {
            recommendations.add(
                PerformanceRecommendation(
                    category = "Resource Management",
                    description = "High CPU usage detected - consider scaling or optimization",
                    priority = if (resourceStatus.cpuUtilization > 90.0) RecommendationPriority.HIGH else RecommendationPriority.MEDIUM,
                    impact = "High"
                )
            )
        }
        
        if (resourceStatus.memoryUtilization > 80.0) {
            recommendations.add(
                PerformanceRecommendation(
                    category = "Resource Management",
                    description = "High memory usage detected - check for memory leaks",
                    priority = if (resourceStatus.memoryUtilization > 90.0) RecommendationPriority.HIGH else RecommendationPriority.MEDIUM,
                    impact = "High"
                )
            )
        }
        
        // Request performance recommendations
        if (requestStatus.errorRate > 5.0) {
            recommendations.add(
                PerformanceRecommendation(
                    category = "Request Performance",
                    description = "High error rate detected - investigate error patterns",
                    priority = RecommendationPriority.HIGH,
                    impact = "High"
                )
            )
        }
        
        if (requestStatus.averageResponseTime > 200) {
            recommendations.add(
                PerformanceRecommendation(
                    category = "Request Performance",
                    description = "High response time detected - optimize database queries or add caching",
                    priority = RecommendationPriority.MEDIUM,
                    impact = "Medium"
                )
            )
        }
        
        // Error recommendations
        if (errorStatus.errorRate > 2.0) {
            recommendations.add(
                PerformanceRecommendation(
                    category = "Error Handling",
                    description = "High error rate detected - review error handling and logging",
                    priority = RecommendationPriority.MEDIUM,
                    impact = "Medium"
                )
            )
        }
        
        return recommendations
    }
    
    private fun generateMetricId(): String = "metric_${System.currentTimeMillis()}_${(0..9999).random()}"
    private fun generateAlertId(): String = "alert_${System.currentTimeMillis()}_${(0..9999).random()}"
}

// Data classes for performance monitoring

@Serializable
data class PerformanceMetric(
    val id: String,
    val name: String,
    val type: String,
    val value: Double,
    val unit: String,
    val timestamp: Instant,
    val tags: Map<String, String> = emptyMap()
)

@Serializable
data class MetricRecordingResult(
    val metricId: String,
    val success: Boolean,
    val issues: List<String> = emptyList(),
    val timestamp: Instant
)

@Serializable
data class RequestPerformanceResult(
    val requestId: String,
    val duration: Long,
    val recorded: Boolean,
    val error: String? = null,
    val timestamp: Instant
)

@Serializable
data class ErrorRecordingResult(
    val errorId: String,
    val recorded: Boolean,
    val error: String? = null,
    val timestamp: Instant
)

@Serializable
data class ResourceUsageResult(
    val resourceType: String,
    val utilization: Double,
    val recorded: Boolean,
    val error: String? = null,
    val timestamp: Instant
)

@Serializable
data class CurrentMetricsResult(
    val metrics: List<PerformanceMetric>,
    val summary: MetricsSummary,
    val timestamp: Instant
)

@Serializable
data class HistoricalMetricsResult(
    val timeRange: TimeRange,
    val metrics: List<AggregatedMetric>,
    val trends: List<PerformanceTrend>,
    val timestamp: Instant
)

@Serializable
data class PerformanceStatusReport(
    val currentMetrics: CurrentMetricsResult,
    val alerts: List<Alert>,
    val resourceStatus: ResourceStatus,
    val requestStatus: RequestStatus,
    val errorStatus: ErrorStatus,
    val overallHealth: OverallPerformanceHealth,
    val recommendations: List<PerformanceRecommendation>,
    val timestamp: Instant
)

@Serializable
data class ConfigurationResult(
    val component: String,
    val success: Boolean,
    val error: String? = null,
    val timestamp: Instant
)

@Serializable
data class CleanupResult(
    val removedCount: Long,
    val cutoffTime: Instant,
    val timestamp: Instant
)

@Serializable
data class MetricsSummary(
    val totalMetrics: Long = 0,
    val counterMetrics: Long = 0,
    val gaugeMetrics: Long = 0,
    val timerMetrics: Long = 0,
    val averageValue: Double = 0.0,
    val minValue: Double = 0.0,
    val maxValue: Double = 0.0,
    val timestamp: Instant = Instant.now()
)

@Serializable
data class AggregatedMetric(
    val timestamp: Instant,
    val count: Long,
    val sum: Double,
    val average: Double,
    val min: Double,
    val max: Double,
    val standardDeviation: Double
)

@Serializable
data class OverallPerformanceHealth(
    val score: Double,
    val grade: String,
    val status: String
)

@Serializable
data class PerformanceRecommendation(
    val category: String,
    val description: String,
    val priority: RecommendationPriority,
    val impact: String
)

@Serializable
data class MetricValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)

@Serializable
data class TimeRange(
    val start: Instant,
    val end: Instant
)

@Serializable
data class ErrorEvent(
    val id: String,
    val type: String,
    val message: String,
    val severity: String,
    val timestamp: Instant,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class Alert(
    val id: String,
    val type: String,
    val severity: String,
    val message: String,
    val metric: PerformanceMetric? = null,
    val timestamp: Instant
)

@Serializable
data class PerformanceTrend(
    val metricName: String,
    val direction: String,
    val change: Double,
    val confidence: Double
)

// Placeholder classes for monitoring components

class MetricsCollector {
    suspend fun record(metric: PerformanceMetric) {
        // Placeholder implementation
    }
    
    suspend fun getMetrics(names: List<String>): List<PerformanceMetric> {
        // Placeholder implementation
        return emptyList()
    }
    
    suspend fun getAllMetrics(): List<PerformanceMetric> {
        // Placeholder implementation
        return emptyList()
    }
    
    suspend fun getMetricsInRange(timeRange: TimeRange): List<PerformanceMetric> {
        // Placeholder implementation
        return emptyList()
    }
    
    suspend fun removeMetricsBefore(cutoffTime: Instant): Long {
        // Placeholder implementation
        return 0
    }
    
    fun configure(config: MetricsConfig) {
        // Placeholder implementation
    }
}

class AlertManager {
    suspend fun createAlert(alert: Alert) {
        // Placeholder implementation
    }
    
    fun getActiveAlerts(): List<Alert> {
        // Placeholder implementation
        return emptyList()
    }
    
    fun configure(config: AlertConfig) {
        // Placeholder implementation
    }
}

class PerformanceAnalyzer {
    fun updateAnalysis(metric: PerformanceMetric) {
        // Placeholder implementation
    }
    
    suspend fun analyzeTrends(metrics: List<AggregatedMetric>): List<PerformanceTrend> {
        // Placeholder implementation
        return emptyList()
    }
    
    fun aggregateMetrics() {
        // Placeholder implementation
    }
    
    fun configure(config: AnalysisConfig) {
        // Placeholder implementation
    }
}

class ResourceMonitor {
    fun getCpuUsage(): Double {
        // Placeholder implementation
        return 0.0
    }
    
    fun getMemoryUsage(): Double {
        // Placeholder implementation
        return 0.0
    }
    
    fun getDiskUsage(): Double {
        // Placeholder implementation
        return 0.0
    }
}

class RequestTracker {
    fun trackRequest(requestId: String, duration: Long, success: Boolean, metadata: Map<String, String>) {
        // Placeholder implementation
    }
    
    fun getRequestStatus(): RequestStatus {
        // Placeholder implementation
        return RequestStatus()
    }
}

class ErrorTracker {
    fun recordError(error: ErrorEvent) {
        // Placeholder implementation
    }
    
    fun getErrorRate(): Double {
        // Placeholder implementation
        return 0.0
    }
    
    fun getErrorStatus(): ErrorStatus {
        // Placeholder implementation
        return ErrorStatus()
    }
}

class ResourceTracker {
    fun trackResourceUsage(resourceType: String, usage: Double, capacity: Double, unit: String) {
        // Placeholder implementation
    }
    
    fun getResourceStatus(): ResourceStatus {
        // Placeholder implementation
        return ResourceStatus()
    }
}

// Placeholder data classes

@Serializable
data class RequestStatus(
    val totalRequests: Long = 0,
    val successfulRequests: Long = 0,
    val failedRequests: Long = 0,
    val errorRate: Double = 0.0,
    val averageResponseTime: Long = 0
)

@Serializable
data class ErrorStatus(
    val totalErrors: Long = 0,
    val errorRate: Double = 0.0,
    val lastErrorTime: Instant? = null
)

@Serializable
data class ResourceStatus(
    val cpuUtilization: Double = 0.0,
    val memoryUtilization: Double = 0.0,
    val diskUtilization: Double = 0.0
)

@Serializable
data class MonitoringConfig(
    val samplingInterval: Long = DEFAULT_SAMPLING_INTERVAL,
    val aggregationInterval: Long = DEFAULT_AGGREGATION_INTERVAL,
    val retentionPeriod: Long = DEFAULT_RETENTION_PERIOD,
    val metricsConfig: MetricsConfig = MetricsConfig(),
    val alertConfig: AlertConfig = AlertConfig(),
    val analysisConfig: AnalysisConfig = AnalysisConfig()
) {
    fun updateFrom(config: MonitoringConfig) {
        // Placeholder implementation
    }
}

@Serializable
data class MetricsConfig(
    val enabled: Boolean = true,
    val maxMetrics: Long = 100000
)

@Serializable
data class AlertConfig(
    val enabled: Boolean = true,
    val thresholds: List<AlertThreshold> = emptyList()
)

@Serializable
data class AnalysisConfig(
    val enabled: Boolean = true,
    val trendAnalysisEnabled: Boolean = true
)

@Serializable
data class AlertThreshold(
    val metricName: String,
    val value: Double,
    val severity: String
)

/**
 * Exception thrown when performance monitoring operations fail.
 */
class PerformanceMonitoringException(message: String, cause: Throwable? = null) : Exception(message, cause)