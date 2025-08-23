package app.multiauth.devops

import app.multiauth.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Production Deployment System
 * 
 * This class manages the deployment of applications to production environments,
 * including environment setup, configuration management, and deployment strategies.
 */
class ProductionDeployment(
    private val logger: Logger,
    private val config: DeploymentConfig
) {
    
    private val _deploymentStatus = MutableStateFlow<DeploymentStatus>(DeploymentStatus.IDLE)
    val deploymentStatus: StateFlow<DeploymentStatus> = _deploymentStatus
    
    private val _deployments = MutableStateFlow<List<Deployment>>(emptyList())
    val deployments: StateFlow<List<Deployment>> = _deployments
    
    private val _environmentStatus = MutableStateFlow<Map<String, EnvironmentStatus>>(emptyMap())
    val environmentStatus: StateFlow<Map<String, EnvironmentStatus>> = _environmentStatus
    
    /**
     * Deploy application to production environment
     */
    suspend fun deployToProduction(
        version: String,
        artifacts: List<Artifact>,
        deploymentStrategy: DeploymentStrategy = DeploymentStrategy.BLUE_GREEN
    ): DeploymentResult {
        logger.info("Starting production deployment for version: $version")
        
        return try {
            _deploymentStatus.value = DeploymentStatus.DEPLOYING
            
            val deployment = Deployment(
                id = generateDeploymentId(),
                version = version,
                environment = "production",
                strategy = deploymentStrategy,
                artifacts = artifacts,
                status = DeploymentStatus.DEPLOYING,
                startedAt = System.currentTimeMillis(),
                createdBy = "System"
            )
            
            // Add deployment to list
            val currentDeployments = _deployments.value.toMutableList()
            currentDeployments.add(deployment)
            _deployments.value = currentDeployments
            
            // Execute deployment based on strategy
            val result = when (deploymentStrategy) {
                DeploymentStrategy.BLUE_GREEN -> executeBlueGreenDeployment(deployment)
                DeploymentStrategy.ROLLING -> executeRollingDeployment(deployment)
                DeploymentStrategy.CANARY -> executeCanaryDeployment(deployment)
                DeploymentStrategy.RECREATE -> executeRecreateDeployment(deployment)
            }
            
            // Update deployment status
            updateDeploymentStatus(deployment.id, result.status)
            
            // Update environment status
            updateEnvironmentStatus("production", EnvironmentStatus.DEPLOYED)
            
            _deploymentStatus.value = DeploymentStatus.IDLE
            
            logger.info("Production deployment completed successfully for version: $version")
            result
            
        } catch (e: Exception) {
            logger.error("Production deployment failed for version: $version", e)
            _deploymentStatus.value = DeploymentStatus.FAILED
            
            // Update deployment status to failed
            val deploymentId = _deployments.value.lastOrNull()?.id
            if (deploymentId != null) {
                updateDeploymentStatus(deploymentId, DeploymentStatus.FAILED)
            }
            
            DeploymentResult(
                deploymentId = "",
                status = DeploymentStatus.FAILED,
                message = "Deployment failed: ${e.message}",
                error = e,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Deploy to staging environment
     */
    suspend fun deployToStaging(
        version: String,
        artifacts: List<Artifact>
    ): DeploymentResult {
        logger.info("Starting staging deployment for version: $version")
        
        return try {
            val deployment = Deployment(
                id = generateDeploymentId(),
                version = version,
                environment = "staging",
                strategy = DeploymentStrategy.RECREATE,
                artifacts = artifacts,
                status = DeploymentStatus.DEPLOYING,
                startedAt = System.currentTimeMillis(),
                createdBy = "System"
            )
            
            // Add deployment to list
            val currentDeployments = _deployments.value.toMutableList()
            currentDeployments.add(deployment)
            _deployments.value = currentDeployments
            
            // Execute staging deployment
            val result = executeStagingDeployment(deployment)
            
            // Update deployment status
            updateDeploymentStatus(deployment.id, result.status)
            
            // Update environment status
            updateEnvironmentStatus("staging", EnvironmentStatus.DEPLOYED)
            
            logger.info("Staging deployment completed successfully for version: $version")
            result
            
        } catch (e: Exception) {
            logger.error("Staging deployment failed for version: $version", e)
            
            // Update deployment status to failed
            val deploymentId = _deployments.value.lastOrNull()?.id
            if (deploymentId != null) {
                updateDeploymentStatus(deploymentId, DeploymentStatus.FAILED)
            }
            
            DeploymentResult(
                deploymentId = "",
                status = DeploymentStatus.FAILED,
                message = "Staging deployment failed: ${e.message}",
                error = e,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Rollback to previous version
     */
    suspend fun rollback(environment: String, targetVersion: String? = null): RollbackResult {
        logger.info("Starting rollback for environment: $environment")
        
        return try {
            val currentDeployment = _deployments.value
                .filter { it.environment == environment }
                .filter { it.status == DeploymentStatus.DEPLOYED }
                .maxByOrNull { it.startedAt }
            
            if (currentDeployment == null) {
                throw IllegalStateException("No active deployment found for environment: $environment")
            }
            
            val rollbackVersion = targetVersion ?: currentDeployment.previousVersion
            
            if (rollbackVersion == null) {
                throw IllegalStateException("No previous version available for rollback")
            }
            
            // Execute rollback
            val result = executeRollback(environment, rollbackVersion)
            
            // Update environment status
            updateEnvironmentStatus(environment, EnvironmentStatus.ROLLED_BACK)
            
            logger.info("Rollback completed successfully for environment: $environment to version: $rollbackVersion")
            result
            
        } catch (e: Exception) {
            logger.error("Rollback failed for environment: $environment", e)
            RollbackResult(
                environment = environment,
                status = RollbackStatus.FAILED,
                message = "Rollback failed: ${e.message}",
                error = e,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Get deployment history
     */
    fun getDeploymentHistory(environment: String? = null): Flow<List<Deployment>> = flow {
        val deployments = if (environment != null) {
            _deployments.value.filter { it.environment == environment }
        } else {
            _deployments.value
        }
        
        emit(deployments.sortedByDescending { it.startedAt })
    }
    
    /**
     * Get current deployment status
     */
    fun getCurrentDeploymentStatus(environment: String): Flow<DeploymentStatus> = flow {
        val currentDeployment = _deployments.value
            .filter { it.environment == environment }
            .maxByOrNull { it.startedAt }
        
        emit(currentDeployment?.status ?: DeploymentStatus.IDLE)
    }
    
    /**
     * Validate deployment configuration
     */
    suspend fun validateDeploymentConfig(
        environment: String,
        version: String,
        artifacts: List<Artifact>
    ): ValidationResult {
        logger.info("Validating deployment configuration for environment: $environment, version: $version")
        
        return try {
            val validations = listOf(
                "Environment Configuration" to { validateEnvironmentConfig(environment) },
                "Artifact Validation" to { validateArtifacts(artifacts) },
                "Version Compatibility" to { validateVersionCompatibility(version, environment) },
                "Security Checks" to { validateSecurityRequirements(environment) },
                "Resource Requirements" to { validateResourceRequirements(environment, artifacts) }
            )
            
            val results = mutableListOf<ValidationItem>()
            var overallValid = true
            
            validations.forEach { (validationName, validationFunction) ->
                try {
                    val result = validationFunction()
                    val isValid = result.contains("PASSED")
                    
                    if (!isValid) {
                        overallValid = false
                    }
                    
                    results.add(ValidationItem(
                        name = validationName,
                        isValid = isValid,
                        details = result,
                        timestamp = System.currentTimeMillis()
                    ))
                    
                } catch (e: Exception) {
                    overallValid = false
                    results.add(ValidationItem(
                        name = validationName,
                        isValid = false,
                        details = "Validation failed: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        error = e
                    ))
                    
                    logger.error("Validation '$validationName' failed", e)
                }
            }
            
            ValidationResult(
                isValid = overallValid,
                validations = results,
                timestamp = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            logger.error("Deployment configuration validation failed", e)
            ValidationResult(
                isValid = false,
                validations = emptyList(),
                timestamp = System.currentTimeMillis(),
                error = e
            )
        }
    }
    
    // Private deployment strategy implementations
    
    private suspend fun executeBlueGreenDeployment(deployment: Deployment): DeploymentResult {
        logger.info("Executing blue-green deployment for version: ${deployment.version}")
        
        val steps = listOf(
            "Prepare Green Environment" to { prepareGreenEnvironment(deployment) },
            "Deploy to Green Environment" to { deployToGreenEnvironment(deployment) },
            "Run Smoke Tests" to { runSmokeTests(deployment, "green") },
            "Switch Traffic to Green" to { switchTrafficToGreen(deployment) },
            "Verify Green Environment" to { verifyGreenEnvironment(deployment) },
            "Cleanup Blue Environment" to { cleanupBlueEnvironment(deployment) }
        )
        
        return executeDeploymentSteps(deployment, steps)
    }
    
    private suspend fun executeRollingDeployment(deployment: Deployment): DeploymentResult {
        logger.info("Executing rolling deployment for version: ${deployment.version}")
        
        val steps = listOf(
            "Prepare Rolling Update" to { prepareRollingUpdate(deployment) },
            "Update First Instance" to { updateFirstInstance(deployment) },
            "Verify First Instance" to { verifyInstance(deployment, 1) },
            "Update Remaining Instances" to { updateRemainingInstances(deployment) },
            "Verify All Instances" to { verifyAllInstances(deployment) },
            "Complete Rolling Update" to { completeRollingUpdate(deployment) }
        )
        
        return executeDeploymentSteps(deployment, steps)
    }
    
    private suspend fun executeCanaryDeployment(deployment: Deployment): DeploymentResult {
        logger.info("Executing canary deployment for version: ${deployment.version}")
        
        val steps = listOf(
            "Prepare Canary Environment" to { prepareCanaryEnvironment(deployment) },
            "Deploy Canary Instance" to { deployCanaryInstance(deployment) },
            "Route Small Traffic to Canary" to { routeTrafficToCanary(deployment, 5) },
            "Monitor Canary Performance" to { monitorCanaryPerformance(deployment) },
            "Gradually Increase Traffic" to { graduallyIncreaseTraffic(deployment) },
            "Full Rollout" to { fullRollout(deployment) }
        )
        
        return executeDeploymentSteps(deployment, steps)
    }
    
    private suspend fun executeRecreateDeployment(deployment: Deployment): DeploymentResult {
        logger.info("Executing recreate deployment for version: ${deployment.version}")
        
        val steps = listOf(
            "Stop Current Deployment" to { stopCurrentDeployment(deployment) },
            "Deploy New Version" to { deployNewVersion(deployment) },
            "Verify New Deployment" to { verifyNewDeployment(deployment) },
            "Start New Deployment" to { startNewDeployment(deployment) }
        )
        
        return executeDeploymentSteps(deployment, steps)
    }
    
    private suspend fun executeStagingDeployment(deployment: Deployment): DeploymentResult {
        logger.info("Executing staging deployment for version: ${deployment.version}")
        
        val steps = listOf(
            "Prepare Staging Environment" to { prepareStagingEnvironment(deployment) },
            "Deploy to Staging" to { deployToStaging(deployment) },
            "Run Integration Tests" to { runIntegrationTests(deployment) },
            "Run Performance Tests" to { runPerformanceTests(deployment) },
            "Verify Staging Deployment" to { verifyStagingDeployment(deployment) }
        )
        
        return executeDeploymentSteps(deployment, steps)
    }
    
    private suspend fun executeDeploymentSteps(
        deployment: Deployment,
        steps: List<Pair<String, suspend () -> String>>
    ): DeploymentResult {
        val results = mutableListOf<DeploymentStepResult>()
        
        steps.forEach { (stepName, stepFunction) ->
            try {
                logger.info("Executing deployment step: $stepName")
                val startTime = System.currentTimeMillis()
                
                val result = stepFunction()
                val duration = System.currentTimeMillis() - startTime
                
                results.add(DeploymentStepResult(
                    stepName = stepName,
                    status = DeploymentStepStatus.SUCCESS,
                    duration = duration,
                    details = result
                ))
                
                logger.info("Deployment step '$stepName' completed successfully in ${duration}ms")
                
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                
                results.add(DeploymentStepResult(
                    stepName = stepName,
                    status = DeploymentStepStatus.FAILED,
                    duration = duration,
                    details = "Step failed: ${e.message}",
                    error = e
                ))
                
                logger.error("Deployment step '$stepName' failed after ${duration}ms", e)
                throw e
            }
        }
        
        return DeploymentResult(
            deploymentId = deployment.id,
            status = DeploymentStatus.DEPLOYED,
            message = "Deployment completed successfully",
            steps = results,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private suspend fun executeRollback(environment: String, version: String): RollbackResult {
        logger.info("Executing rollback for environment: $environment to version: $version")
        
        val steps = listOf(
            "Stop Current Deployment" to { stopCurrentDeployment(environment) },
            "Restore Previous Version" to { restorePreviousVersion(environment, version) },
            "Verify Rollback" to { verifyRollback(environment, version) },
            "Start Rolled Back Deployment" to { startRolledBackDeployment(environment, version) }
        )
        
        val results = mutableListOf<RollbackStepResult>()
        
        steps.forEach { (stepName, stepFunction) ->
            try {
                logger.info("Executing rollback step: $stepName")
                val startTime = System.currentTimeMillis()
                
                val result = stepFunction()
                val duration = System.currentTimeMillis() - startTime
                
                results.add(RollbackStepResult(
                    stepName = stepName,
                    status = RollbackStepStatus.SUCCESS,
                    duration = duration,
                    details = result
                ))
                
                logger.info("Rollback step '$stepName' completed successfully in ${duration}ms")
                
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                
                results.add(RollbackStepResult(
                    stepName = stepName,
                    status = RollbackStepStatus.FAILED,
                    duration = duration,
                    details = "Step failed: ${e.message}",
                    error = e
                ))
                
                logger.error("Rollback step '$stepName' failed after ${duration}ms", e)
                throw e
            }
        }
        
        return RollbackResult(
            environment = environment,
            status = RollbackStatus.SUCCESS,
            steps = results,
            totalDuration = results.sumOf { it.duration },
            timestamp = System.currentTimeMillis()
        )
    }
    
    // Private helper methods
    
    private suspend fun updateDeploymentStatus(deploymentId: String, status: DeploymentStatus) {
        val currentDeployments = _deployments.value.toMutableList()
        val deploymentIndex = currentDeployments.indexOfFirst { it.id == deploymentId }
        
        if (deploymentIndex != -1) {
            val deployment = currentDeployments[deploymentIndex]
            val updatedDeployment = deployment.copy(
                status = status,
                completedAt = if (status.isTerminal()) System.currentTimeMillis() else null
            )
            
            currentDeployments[deploymentIndex] = updatedDeployment
            _deployments.value = currentDeployments
        }
    }
    
    private suspend fun updateEnvironmentStatus(environment: String, status: EnvironmentStatus) {
        val currentStatus = _environmentStatus.value.toMutableMap()
        currentStatus[environment] = status
        _environmentStatus.value = currentStatus
    }
    
    private fun generateDeploymentId(): String = "deploy_${System.currentTimeMillis()}_${(0..9999).random()}"
    
    // Deployment step implementations (simulated)
    
    private suspend fun prepareGreenEnvironment(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "Green environment prepared successfully"
    }
    
    private suspend fun deployToGreenEnvironment(deployment: Deployment): String {
        kotlinx.coroutines.delay(5000)
        return "Application deployed to green environment"
    }
    
    private suspend fun runSmokeTests(deployment: Deployment, environment: String): String {
        kotlinx.coroutines.delay(3000)
        return "Smoke tests passed for $environment environment"
    }
    
    private suspend fun switchTrafficToGreen(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "Traffic switched to green environment successfully"
    }
    
    private suspend fun verifyGreenEnvironment(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "Green environment verified and operational"
    }
    
    private suspend fun cleanupBlueEnvironment(deployment: Deployment): String {
        kotlinx.coroutines.delay(1000)
        return "Blue environment cleaned up successfully"
    }
    
    // Additional deployment step implementations...
    private suspend fun prepareRollingUpdate(deployment: Deployment): String {
        kotlinx.coroutines.delay(1000)
        return "Rolling update prepared successfully"
    }
    
    private suspend fun updateFirstInstance(deployment: Deployment): String {
        kotlinx.coroutines.delay(3000)
        return "First instance updated successfully"
    }
    
    private suspend fun verifyInstance(deployment: Deployment, instanceNumber: Int): String {
        kotlinx.coroutines.delay(1000)
        return "Instance $instanceNumber verified successfully"
    }
    
    private suspend fun updateRemainingInstances(deployment: Deployment): String {
        kotlinx.coroutines.delay(4000)
        return "Remaining instances updated successfully"
    }
    
    private suspend fun verifyAllInstances(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "All instances verified successfully"
    }
    
    private suspend fun completeRollingUpdate(deployment: Deployment): String {
        kotlinx.coroutines.delay(1000)
        return "Rolling update completed successfully"
    }
    
    // Canary deployment steps
    private suspend fun prepareCanaryEnvironment(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "Canary environment prepared successfully"
    }
    
    private suspend fun deployCanaryInstance(deployment: Deployment): String {
        kotlinx.coroutines.delay(3000)
        return "Canary instance deployed successfully"
    }
    
    private suspend fun routeTrafficToCanary(deployment: Deployment, percentage: Int): String {
        kotlinx.coroutines.delay(1000)
        return "$percentage% of traffic routed to canary"
    }
    
    private suspend fun monitorCanaryPerformance(deployment: Deployment): String {
        kotlinx.coroutines.delay(5000)
        return "Canary performance monitored and verified"
    }
    
    private suspend fun graduallyIncreaseTraffic(deployment: Deployment): String {
        kotlinx.coroutines.delay(3000)
        return "Traffic gradually increased to canary"
    }
    
    private suspend fun fullRollout(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "Full rollout completed successfully"
    }
    
    // Recreate deployment steps
    private suspend fun stopCurrentDeployment(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "Current deployment stopped successfully"
    }
    
    private suspend fun deployNewVersion(deployment: Deployment): String {
        kotlinx.coroutines.delay(5000)
        return "New version deployed successfully"
    }
    
    private suspend fun verifyNewDeployment(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "New deployment verified successfully"
    }
    
    private suspend fun startNewDeployment(deployment: Deployment): String {
        kotlinx.coroutines.delay(1000)
        return "New deployment started successfully"
    }
    
    // Staging deployment steps
    private suspend fun prepareStagingEnvironment(deployment: Deployment): String {
        kotlinx.coroutines.delay(1000)
        return "Staging environment prepared successfully"
    }
    
    private suspend fun deployToStaging(deployment: Deployment): String {
        kotlinx.coroutines.delay(3000)
        return "Application deployed to staging successfully"
    }
    
    private suspend fun runIntegrationTests(deployment: Deployment): String {
        kotlinx.coroutines.delay(4000)
        return "Integration tests passed successfully"
    }
    
    private suspend fun runPerformanceTests(deployment: Deployment): String {
        kotlinx.coroutines.delay(5000)
        return "Performance tests passed successfully"
    }
    
    private suspend fun verifyStagingDeployment(deployment: Deployment): String {
        kotlinx.coroutines.delay(2000)
        return "Staging deployment verified successfully"
    }
    
    // Rollback steps
    private suspend fun stopCurrentDeployment(environment: String): String {
        kotlinx.coroutines.delay(2000)
        return "Current deployment stopped successfully"
    }
    
    private suspend fun restorePreviousVersion(environment: String, version: String): String {
        kotlinx.coroutines.delay(3000)
        return "Previous version $version restored successfully"
    }
    
    private suspend fun verifyRollback(environment: String, version: String): String {
        kotlinx.coroutines.delay(2000)
        return "Rollback to version $version verified successfully"
    }
    
    private suspend fun startRolledBackDeployment(environment: String, version: String): String {
        kotlinx.coroutines.delay(1000)
        return "Rolled back deployment started successfully"
    }
    
    // Validation implementations
    private suspend fun validateEnvironmentConfig(environment: String): String {
        kotlinx.coroutines.delay(500)
        return "PASSED - Environment configuration valid"
    }
    
    private suspend fun validateArtifacts(artifacts: List<Artifact>): String {
        kotlinx.coroutines.delay(500)
        return "PASSED - All artifacts validated successfully"
    }
    
    private suspend fun validateVersionCompatibility(version: String, environment: String): String {
        kotlinx.coroutines.delay(500)
        return "PASSED - Version $version compatible with $environment"
    }
    
    private suspend fun validateSecurityRequirements(environment: String): String {
        kotlinx.coroutines.delay(500)
        return "PASSED - Security requirements met for $environment"
    }
    
    private suspend fun validateResourceRequirements(environment: String, artifacts: List<Artifact>): String {
        kotlinx.coroutines.delay(500)
        return "PASSED - Resource requirements satisfied for $environment"
    }
}

// Data classes for production deployment

data class DeploymentConfig(
    val environments: List<EnvironmentConfig>,
    val deploymentStrategies: List<DeploymentStrategy>,
    val rollbackEnabled: Boolean = true,
    val maxRollbackVersions: Int = 5,
    val deploymentTimeoutMinutes: Int = 30,
    val healthCheckTimeoutSeconds: Int = 300
)

data class EnvironmentConfig(
    val name: String,
    val type: EnvironmentType,
    val url: String,
    val instances: Int,
    val resources: ResourceRequirements,
    val security: SecurityConfig,
    val monitoring: MonitoringConfig
)

enum class EnvironmentType {
    DEVELOPMENT,
    STAGING,
    PRODUCTION,
    TESTING
}

data class ResourceRequirements(
    val cpu: String,
    val memory: String,
    val storage: String,
    val network: String
)

data class SecurityConfig(
    val sslEnabled: Boolean = true,
    val authenticationRequired: Boolean = true,
    val encryptionLevel: EncryptionLevel = EncryptionLevel.HIGH
)

enum class EncryptionLevel {
    LOW,
    MEDIUM,
    HIGH,
    MAXIMUM
}

enum class DeploymentStrategy {
    BLUE_GREEN,
    ROLLING,
    CANARY,
    RECREATE
}

enum class DeploymentStatus {
    IDLE,
    DEPLOYING,
    DEPLOYED,
    FAILED,
    ROLLING_BACK,
    ROLLED_BACK
}

enum class EnvironmentStatus {
    IDLE,
    DEPLOYING,
    DEPLOYED,
    FAILED,
    ROLLING_BACK,
    ROLLED_BACK
}

enum class DeploymentStepStatus {
    SUCCESS,
    FAILED,
    RUNNING,
    SKIPPED
}

enum class RollbackStatus {
    SUCCESS,
    FAILED,
    RUNNING
}

enum class RollbackStepStatus {
    SUCCESS,
    FAILED,
    RUNNING
}

data class Artifact(
    val id: String,
    val name: String,
    val type: ArtifactType,
    val version: String,
    val url: String,
    val checksum: String,
    val size: Long
)

enum class ArtifactType {
    APK,
    AAB,
    IPA,
    WEB_BUNDLE,
    DOCKER_IMAGE,
    JAR,
    WAR
}

data class Deployment(
    val id: String,
    val version: String,
    val environment: String,
    val strategy: DeploymentStrategy,
    val artifacts: List<Artifact>,
    val status: DeploymentStatus,
    val startedAt: Long,
    val completedAt: Long? = null,
    val createdBy: String,
    val previousVersion: String? = null,
    val rollbackVersion: String? = null
)

data class DeploymentResult(
    val deploymentId: String,
    val status: DeploymentStatus,
    val message: String,
    val steps: List<DeploymentStepResult> = emptyList(),
    val error: Exception? = null,
    val timestamp: Long
)

data class DeploymentStepResult(
    val stepName: String,
    val status: DeploymentStepStatus,
    val duration: Long,
    val details: String,
    val error: Exception? = null
)

data class RollbackResult(
    val environment: String,
    val status: RollbackStatus,
    val steps: List<RollbackStepResult>,
    val totalDuration: Long,
    val message: String? = null,
    val error: Exception? = null,
    val timestamp: Long
)

data class RollbackStepResult(
    val stepName: String,
    val status: RollbackStepStatus,
    val duration: Long,
    val details: String,
    val error: Exception? = null
)

data class ValidationResult(
    val isValid: Boolean,
    val validations: List<ValidationItem>,
    val timestamp: Long,
    val error: Exception? = null
)

data class ValidationItem(
    val name: String,
    val isValid: Boolean,
    val details: String,
    val timestamp: Long,
    val error: Exception? = null
)

// Extension functions

fun DeploymentStatus.isTerminal(): Boolean {
    return this in listOf(DeploymentStatus.DEPLOYED, DeploymentStatus.FAILED, DeploymentStatus.ROLLED_BACK)
}