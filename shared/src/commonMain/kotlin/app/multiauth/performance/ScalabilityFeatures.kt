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

/**
 * Scalability features layer for horizontal scaling and load balancing.
 * Provides microservices architecture support and performance monitoring.
 */
class ScalabilityFeatures {
    
    private val logger = Logger.getLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        // Scaling strategies
        const val SCALING_STRATEGY_HORIZONTAL = "HORIZONTAL"
        const val SCALING_STRATEGY_VERTICAL = "VERTICAL"
        const val SCALING_STRATEGY_AUTO = "AUTO"
        
        // Load balancing algorithms
        const val LB_ALGORITHM_ROUND_ROBIN = "ROUND_ROBIN"
        const val LB_ALGORITHM_LEAST_CONNECTIONS = "LEAST_CONNECTIONS"
        const val LB_ALGORITHM_WEIGHTED_ROUND_ROBIN = "WEIGHTED_ROUND_ROBIN"
        const val LB_ALGORITHM_IP_HASH = "IP_HASH"
        
        // Health check intervals
        const val DEFAULT_HEALTH_CHECK_INTERVAL = 30000 // 30 seconds
        const val DEFAULT_HEALTH_CHECK_TIMEOUT = 5000 // 5 seconds
        
        // Scaling thresholds
        const val DEFAULT_CPU_THRESHOLD = 80.0 // 80% CPU usage
        const val DEFAULT_MEMORY_THRESHOLD = 85.0 // 85% memory usage
        const val DEFAULT_RESPONSE_TIME_THRESHOLD = 200 // 200ms response time
    }
    
    private val loadBalancer = LoadBalancer()
    private val scalingManager = ScalingManager()
    private val healthMonitor = HealthMonitor()
    private val performanceTracker = PerformanceTracker()
    private val scheduledExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(3)
    
    // Service registry and discovery
    private val serviceRegistry = ServiceRegistry()
    private val serviceDiscovery = ServiceDiscovery()
    
    // Circuit breaker for fault tolerance
    private val circuitBreaker = CircuitBreaker()
    
    init {
        startScalabilityServices()
    }
    
    /**
     * Registers a service instance for load balancing and scaling.
     * 
     * @param serviceInfo Service information
     * @return Registration result
     */
    suspend fun registerService(serviceInfo: ServiceInfo): ServiceRegistrationResult {
        return try {
            logger.info("Registering service: ${serviceInfo.name}")
            
            // Register with service registry
            val registration = serviceRegistry.register(serviceInfo)
            
            // Add to load balancer
            loadBalancer.addService(serviceInfo)
            
            // Start health monitoring
            healthMonitor.startMonitoring(serviceInfo.id)
            
            logger.info("Service registered successfully: ${serviceInfo.name}")
            
            ServiceRegistrationResult(
                serviceId = serviceInfo.id,
                success = true,
                registration = registration,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("Service registration failed: ${e.message}")
            ServiceRegistrationResult(
                serviceId = serviceInfo.id,
                success = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Unregisters a service instance.
     * 
     * @param serviceId Service ID to unregister
     * @return Unregistration result
     */
    suspend fun unregisterService(serviceId: String): ServiceUnregistrationResult {
        return try {
            logger.info("Unregistering service: $serviceId")
            
            // Remove from load balancer
            loadBalancer.removeService(serviceId)
            
            // Stop health monitoring
            healthMonitor.stopMonitoring(serviceId)
            
            // Unregister from service registry
            val unregistration = serviceRegistry.unregister(serviceId)
            
            logger.info("Service unregistered successfully: $serviceId")
            
            ServiceUnregistrationResult(
                serviceId = serviceId,
                success = true,
                unregistration = unregistration,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("Service unregistration failed: ${e.message}")
            ServiceUnregistrationResult(
                serviceId = serviceId,
                success = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Routes a request through the load balancer.
     * 
     * @param request The request to route
     * @param algorithm Load balancing algorithm to use
     * @return Routing result
     */
    suspend fun routeRequest(request: ServiceRequest, algorithm: String = LB_ALGORITHM_ROUND_ROBIN): RoutingResult {
        return try {
            logger.debug("Routing request: ${request.id}")
            
            // Check circuit breaker status
            if (circuitBreaker.isOpen()) {
                logger.warn("Circuit breaker is open, request rejected")
                return RoutingResult(
                    requestId = request.id,
                    success = false,
                    error = "Circuit breaker is open",
                    timestamp = Instant.now()
                )
            }
            
            // Route through load balancer
            val route = loadBalancer.route(request, algorithm)
            
            if (route != null) {
                // Track performance
                performanceTracker.trackRequest(request, route)
                
                logger.debug("Request routed successfully to: ${route.serviceId}")
                
                RoutingResult(
                    requestId = request.id,
                    success = true,
                    route = route,
                    timestamp = Instant.now()
                )
            } else {
                logger.warn("No available service for request: ${request.id}")
                
                RoutingResult(
                    requestId = request.id,
                    success = false,
                    error = "No available service",
                    timestamp = Instant.now()
                )
            }
            
        } catch (e: Exception) {
            logger.error("Request routing failed: ${e.message}")
            
            // Update circuit breaker
            circuitBreaker.recordFailure()
            
            RoutingResult(
                requestId = request.id,
                success = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Scales the system based on current performance metrics.
     * 
     * @param strategy Scaling strategy to use
     * @return Scaling result
     */
    suspend fun scaleSystem(strategy: String = SCALING_STRATEGY_AUTO): ScalingResult {
        return try {
            logger.info("Starting system scaling with strategy: $strategy")
            
            val startTime = Instant.now()
            
            // Get current performance metrics
            val metrics = performanceTracker.getCurrentMetrics()
            
            // Determine scaling action
            val scalingAction = scalingManager.determineScalingAction(metrics, strategy)
            
            if (scalingAction != null) {
                // Execute scaling action
                val executionResult = scalingManager.executeScalingAction(scalingAction)
                
                val executionTime = ChronoUnit.MILLIS.between(startTime, Instant.now())
                
                val result = ScalingResult(
                    strategy = strategy,
                    action = scalingAction,
                    executed = executionResult.success,
                    executionTime = executionTime,
                    newInstances = executionResult.newInstances,
                    removedInstances = executionResult.removedInstances,
                    timestamp = Instant.now()
                )
                
                logger.info("System scaling completed: ${executionResult.newInstances} new instances")
                result
                
            } else {
                logger.info("No scaling action required")
                
                ScalingResult(
                    strategy = strategy,
                    action = null,
                    executed = false,
                    executionTime = 0,
                    newInstances = 0,
                    removedInstances = 0,
                    timestamp = Instant.now()
                )
            }
            
        } catch (e: Exception) {
            logger.error("System scaling failed: ${e.message}")
            throw ScalabilityException("System scaling failed", e)
        }
    }
    
    /**
     * Gets comprehensive scalability status and metrics.
     * 
     * @return Scalability status report
     */
    suspend fun getScalabilityStatus(): ScalabilityStatusReport {
        return try {
            logger.info("Generating scalability status report")
            
            val serviceStatus = serviceRegistry.getServiceStatus()
            val loadBalancerStatus = loadBalancer.getStatus()
            val scalingStatus = scalingManager.getStatus()
            val healthStatus = healthMonitor.getOverallHealth()
            val performanceStatus = performanceTracker.getStatus()
            
            val report = ScalabilityStatusReport(
                serviceStatus = serviceStatus,
                loadBalancerStatus = loadBalancerStatus,
                scalingStatus = scalingStatus,
                healthStatus = healthStatus,
                performanceStatus = performanceStatus,
                overallHealth = calculateOverallHealth(serviceStatus, loadBalancerStatus, scalingStatus, healthStatus, performanceStatus),
                recommendations = generateScalabilityRecommendations(serviceStatus, loadBalancerStatus, scalingStatus, healthStatus, performanceStatus),
                timestamp = Instant.now()
            )
            
            logger.info("Scalability status report generated successfully")
            report
            
        } catch (e: Exception) {
            logger.error("Failed to generate scalability status report: ${e.message}")
            throw ScalabilityException("Status report generation failed", e)
        }
    }
    
    /**
     * Configures load balancing settings.
     * 
     * @param config Load balancer configuration
     * @return Configuration result
     */
    suspend fun configureLoadBalancer(config: LoadBalancerConfig): ConfigurationResult {
        return try {
            logger.info("Configuring load balancer")
            
            loadBalancer.configure(config)
            
            logger.info("Load balancer configured successfully")
            
            ConfigurationResult(
                component = "LoadBalancer",
                success = true,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("Load balancer configuration failed: ${e.message}")
            
            ConfigurationResult(
                component = "LoadBalancer",
                success = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Configures auto-scaling settings.
     * 
     * @param config Auto-scaling configuration
     * @return Configuration result
     */
    suspend fun configureAutoScaling(config: AutoScalingConfig): ConfigurationResult {
        return try {
            logger.info("Configuring auto-scaling")
            
            scalingManager.configure(config)
            
            logger.info("Auto-scaling configured successfully")
            
            ConfigurationResult(
                component = "AutoScaling",
                success = true,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("Auto-scaling configuration failed: ${e.message}")
            
            ConfigurationResult(
                component = "AutoScaling",
                success = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    /**
     * Performs health check on all registered services.
     * 
     * @return Health check result
     */
    suspend fun performHealthCheck(): HealthCheckResult {
        return try {
            logger.info("Performing health check on all services")
            
            val startTime = Instant.now()
            val healthResults = mutableListOf<ServiceHealthResult>()
            
            // Get all registered services
            val services = serviceRegistry.getAllServices()
            
            // Perform health check on each service
            services.forEach { service ->
                val healthResult = healthMonitor.checkServiceHealth(service.id)
                healthResults.add(healthResult)
            }
            
            val executionTime = ChronoUnit.MILLIS.between(startTime, Instant.now())
            val healthyServices = healthResults.count { it.isHealthy }
            val unhealthyServices = healthResults.size - healthyServices
            
            val result = HealthCheckResult(
                totalServices = services.size.toLong(),
                healthyServices = healthyServices.toLong(),
                unhealthyServices = unhealthyServices.toLong(),
                executionTime = executionTime,
                results = healthResults,
                timestamp = Instant.now()
            )
            
            logger.info("Health check completed: $healthyServices healthy, $unhealthyServices unhealthy")
            result
            
        } catch (e: Exception) {
            logger.error("Health check failed: ${e.message}")
            throw ScalabilityException("Health check failed", e)
        }
    }
    
    /**
     * Enables or disables circuit breaker for fault tolerance.
     * 
     * @param enabled Whether to enable circuit breaker
     * @param config Circuit breaker configuration
     * @return Circuit breaker result
     */
    suspend fun configureCircuitBreaker(enabled: Boolean, config: CircuitBreakerConfig): CircuitBreakerResult {
        return try {
            logger.info("Configuring circuit breaker: enabled=$enabled")
            
            circuitBreaker.configure(enabled, config)
            
            logger.info("Circuit breaker configured successfully")
            
            CircuitBreakerResult(
                enabled = enabled,
                success = true,
                timestamp = Instant.now()
            )
            
        } catch (e: Exception) {
            logger.error("Circuit breaker configuration failed: ${e.message}")
            
            CircuitBreakerResult(
                enabled = enabled,
                success = false,
                error = e.message,
                timestamp = Instant.now()
            )
        }
    }
    
    // Private helper methods
    
    private fun startScalabilityServices() {
        // Start health monitoring
        scheduledExecutor.scheduleAtFixedRate({
            try {
                healthMonitor.performPeriodicHealthCheck()
            } catch (e: Exception) {
                logger.error("Periodic health check failed: ${e.message}")
            }
        }, 30, 30, TimeUnit.SECONDS)
        
        // Start performance monitoring
        scheduledExecutor.scheduleAtFixedRate({
            try {
                performanceTracker.updateMetrics()
            } catch (e: Exception) {
                logger.error("Performance metrics update failed: ${e.message}")
            }
        }, 60, 60, TimeUnit.SECONDS)
        
        // Start auto-scaling evaluation
        scheduledExecutor.scheduleAtFixedRate({
            try {
                evaluateAutoScaling()
            } catch (e: Exception) {
                logger.error("Auto-scaling evaluation failed: ${e.message}")
            }
        }, 300, 300, TimeUnit.SECONDS) // Every 5 minutes
    }
    
    private suspend fun evaluateAutoScaling() {
        val metrics = performanceTracker.getCurrentMetrics()
        
        // Check if scaling is needed
        if (shouldScale(metrics)) {
            logger.info("Auto-scaling triggered by performance metrics")
            scaleSystem(SCALING_STRATEGY_AUTO)
        }
    }
    
    private fun shouldScale(metrics: PerformanceMetrics): Boolean {
        return metrics.cpuUsage > DEFAULT_CPU_THRESHOLD ||
               metrics.memoryUsage > DEFAULT_MEMORY_THRESHOLD ||
               metrics.averageResponseTime > DEFAULT_RESPONSE_TIME_THRESHOLD
    }
    
    private fun calculateOverallHealth(
        serviceStatus: ServiceStatus,
        loadBalancerStatus: LoadBalancerStatus,
        scalingStatus: ScalingStatus,
        healthStatus: HealthStatus,
        performanceStatus: PerformanceStatus
    ): OverallHealth {
        val serviceHealth = if (serviceStatus.totalServices > 0) {
            serviceStatus.healthyServices.toDouble() / serviceStatus.totalServices
        } else 1.0
        
        val loadBalancerHealth = if (loadBalancerStatus.totalRequests > 0) {
            loadBalancerStatus.successfulRequests.toDouble() / loadBalancerStatus.totalRequests
        } else 1.0
        
        val scalingHealth = if (scalingStatus.lastScalingSuccessful) 1.0 else 0.5
        val healthCheckHealth = healthStatus.overallHealth
        val performanceHealth = if (performanceStatus.isWithinThresholds) 1.0 else 0.7
        
        val overallScore = (serviceHealth + loadBalancerHealth + scalingHealth + healthCheckHealth + performanceHealth) / 5.0
        
        return OverallHealth(
            score = overallScore,
            grade = calculateHealthGrade(overallScore),
            status = if (overallScore >= 0.8) "HEALTHY" else if (overallScore >= 0.6) "DEGRADED" else "UNHEALTHY"
        )
    }
    
    private fun calculateHealthGrade(score: Double): String {
        return when {
            score >= 0.9 -> "A"
            score >= 0.8 -> "B"
            score >= 0.7 -> "C"
            score >= 0.6 -> "D"
            else -> "F"
        }
    }
    
    private fun generateScalabilityRecommendations(
        serviceStatus: ServiceStatus,
        loadBalancerStatus: LoadBalancerStatus,
        scalingStatus: ScalingStatus,
        healthStatus: HealthStatus,
        performanceStatus: PerformanceStatus
    ): List<ScalabilityRecommendation> {
        val recommendations = mutableListOf<ScalabilityRecommendation>()
        
        // Service health recommendations
        if (serviceStatus.healthyServices < serviceStatus.totalServices * 0.8) {
            recommendations.add(
                ScalabilityRecommendation(
                    category = "Service Health",
                    description = "Some services are unhealthy - investigate and restart if needed",
                    priority = RecommendationPriority.HIGH,
                    impact = "High"
                )
            )
        }
        
        // Load balancer recommendations
        if (loadBalancerStatus.successfulRequests < loadBalancerStatus.totalRequests * 0.9) {
            recommendations.add(
                ScalabilityRecommendation(
                    category = "Load Balancer",
                    description = "High failure rate - check service health and circuit breaker settings",
                    priority = RecommendationPriority.HIGH,
                    impact = "High"
                )
            )
        }
        
        // Scaling recommendations
        if (!scalingStatus.lastScalingSuccessful) {
            recommendations.add(
                ScalabilityRecommendation(
                    category = "Auto-scaling",
                    description = "Last scaling operation failed - review scaling configuration",
                    priority = RecommendationPriority.MEDIUM,
                    impact = "Medium"
                )
            )
        }
        
        // Performance recommendations
        if (!performanceStatus.isWithinThresholds) {
            recommendations.add(
                ScalabilityRecommendation(
                    category = "Performance",
                    description = "Performance metrics outside thresholds - consider scaling",
                    priority = RecommendationPriority.MEDIUM,
                    impact = "Medium"
                )
            )
        }
        
        return recommendations
    }
}

// Data classes for scalability features

@Serializable
data class ServiceInfo(
    val id: String,
    val name: String,
    val version: String,
    val endpoint: String,
    val healthCheckUrl: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ServiceRegistrationResult(
    val serviceId: String,
    val success: Boolean,
    val registration: ServiceRegistration? = null,
    val error: String? = null,
    val timestamp: Instant
)

@Serializable
data class ServiceUnregistrationResult(
    val serviceId: String,
    val success: Boolean,
    val unregistration: ServiceUnregistration? = null,
    val error: String? = null,
    val timestamp: Instant
)

@Serializable
data class ServiceRequest(
    val id: String,
    val type: String,
    val payload: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class RoutingResult(
    val requestId: String,
    val success: Boolean,
    val route: ServiceRoute? = null,
    val error: String? = null,
    val timestamp: Instant
)

@Serializable
data class ScalingResult(
    val strategy: String,
    val action: ScalingAction?,
    val executed: Boolean,
    val executionTime: Long,
    val newInstances: Int,
    val removedInstances: Int,
    val timestamp: Instant
)

@Serializable
data class ScalabilityStatusReport(
    val serviceStatus: ServiceStatus,
    val loadBalancerStatus: LoadBalancerStatus,
    val scalingStatus: ScalingStatus,
    val healthStatus: HealthStatus,
    val performanceStatus: PerformanceStatus,
    val overallHealth: OverallHealth,
    val recommendations: List<ScalabilityRecommendation>,
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
data class HealthCheckResult(
    val totalServices: Long,
    val healthyServices: Long,
    val unhealthyServices: Long,
    val executionTime: Long,
    val results: List<ServiceHealthResult>,
    val timestamp: Instant
)

@Serializable
data class CircuitBreakerResult(
    val enabled: Boolean,
    val success: Boolean,
    val error: String? = null,
    val timestamp: Instant
)

// Placeholder classes for scalability components

class LoadBalancer {
    fun addService(serviceInfo: ServiceInfo) {
        // Placeholder implementation
    }
    
    fun removeService(serviceId: String) {
        // Placeholder implementation
    }
    
    suspend fun route(request: ServiceRequest, algorithm: String): ServiceRoute? {
        // Placeholder implementation
        return ServiceRoute("service_1", "endpoint_1")
    }
    
    fun getStatus(): LoadBalancerStatus {
        return LoadBalancerStatus()
    }
    
    fun configure(config: LoadBalancerConfig) {
        // Placeholder implementation
    }
}

class ScalingManager {
    fun determineScalingAction(metrics: PerformanceMetrics, strategy: String): ScalingAction? {
        // Placeholder implementation
        return null
    }
    
    suspend fun executeScalingAction(action: ScalingAction): ScalingExecutionResult {
        // Placeholder implementation
        return ScalingExecutionResult(true, 0, 0)
    }
    
    fun getStatus(): ScalingStatus {
        return ScalingStatus()
    }
    
    fun configure(config: AutoScalingConfig) {
        // Placeholder implementation
    }
}

class HealthMonitor {
    fun startMonitoring(serviceId: String) {
        // Placeholder implementation
    }
    
    fun stopMonitoring(serviceId: String) {
        // Placeholder implementation
    }
    
    suspend fun checkServiceHealth(serviceId: String): ServiceHealthResult {
        // Placeholder implementation
        return ServiceHealthResult(serviceId, true, "OK")
    }
    
    fun getOverallHealth(): HealthStatus {
        return HealthStatus()
    }
    
    fun performPeriodicHealthCheck() {
        // Placeholder implementation
    }
}

class PerformanceTracker {
    fun trackRequest(request: ServiceRequest, route: ServiceRoute) {
        // Placeholder implementation
    }
    
    fun getCurrentMetrics(): PerformanceMetrics {
        return PerformanceMetrics()
    }
    
    fun getStatus(): PerformanceStatus {
        return PerformanceStatus()
    }
    
    fun updateMetrics() {
        // Placeholder implementation
    }
}

class ServiceRegistry {
    suspend fun register(serviceInfo: ServiceInfo): ServiceRegistration {
        // Placeholder implementation
        return ServiceRegistration(serviceInfo.id, Instant.now())
    }
    
    suspend fun unregister(serviceId: String): ServiceUnregistration {
        // Placeholder implementation
        return ServiceUnregistration(serviceId, Instant.now())
    }
    
    fun getServiceStatus(): ServiceStatus {
        return ServiceStatus()
    }
    
    fun getAllServices(): List<ServiceInfo> {
        return emptyList()
    }
}

class ServiceDiscovery {
    suspend fun discoverServices(serviceName: String): List<ServiceInfo> {
        // Placeholder implementation
        return emptyList()
    }
}

class CircuitBreaker {
    fun isOpen(): Boolean {
        // Placeholder implementation
        return false
    }
    
    fun recordFailure() {
        // Placeholder implementation
    }
    
    fun configure(enabled: Boolean, config: CircuitBreakerConfig) {
        // Placeholder implementation
    }
}

// Placeholder data classes

@Serializable
data class ServiceRegistration(
    val serviceId: String,
    val timestamp: Instant
)

@Serializable
data class ServiceUnregistration(
    val serviceId: String,
    val timestamp: Instant
)

@Serializable
data class ServiceRoute(
    val serviceId: String,
    val endpoint: String
)

@Serializable
data class ScalingAction(
    val type: String,
    val target: Int,
    val reason: String
)

@Serializable
data class ScalingExecutionResult(
    val success: Boolean,
    val newInstances: Int,
    val removedInstances: Int
)

@Serializable
data class ServiceStatus(
    val totalServices: Long = 0,
    val healthyServices: Long = 0,
    val unhealthyServices: Long = 0
)

@Serializable
data class LoadBalancerStatus(
    val totalRequests: Long = 0,
    val successfulRequests: Long = 0,
    val failedRequests: Long = 0
)

@Serializable
data class ScalingStatus(
    val lastScalingSuccessful: Boolean = true,
    val lastScalingTime: Instant? = null
)

@Serializable
data class HealthStatus(
    val overallHealth: Double = 1.0
)

@Serializable
data class PerformanceStatus(
    val isWithinThresholds: Boolean = true
)

@Serializable
data class OverallHealth(
    val score: Double,
    val grade: String,
    val status: String
)

@Serializable
data class ScalabilityRecommendation(
    val category: String,
    val description: String,
    val priority: RecommendationPriority,
    val impact: String
)

@Serializable
data class ServiceHealthResult(
    val serviceId: String,
    val isHealthy: Boolean,
    val status: String
)

@Serializable
data class LoadBalancerConfig(
    val algorithm: String = LB_ALGORITHM_ROUND_ROBIN,
    val healthCheckInterval: Long = DEFAULT_HEALTH_CHECK_INTERVAL,
    val healthCheckTimeout: Long = DEFAULT_HEALTH_CHECK_TIMEOUT
)

@Serializable
data class AutoScalingConfig(
    val enabled: Boolean = true,
    val minInstances: Int = 1,
    val maxInstances: Int = 10,
    val cpuThreshold: Double = DEFAULT_CPU_THRESHOLD,
    val memoryThreshold: Double = DEFAULT_MEMORY_THRESHOLD,
    val responseTimeThreshold: Long = DEFAULT_RESPONSE_TIME_THRESHOLD
)

@Serializable
data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val recoveryTimeout: Long = 60000, // 1 minute
    val halfOpenMaxRequests: Int = 3
)

@Serializable
data class PerformanceMetrics(
    val cpuUsage: Double = 0.0,
    val memoryUsage: Double = 0.0,
    val averageResponseTime: Long = 0,
    val requestRate: Double = 0.0
)

/**
 * Exception thrown when scalability operations fail.
 */
class ScalabilityException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Priority levels for scalability recommendations.
 */
enum class RecommendationPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}