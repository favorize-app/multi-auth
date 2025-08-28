package app.multiauth.security

import app.multiauth.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * AI-powered threat detection system for real-time security monitoring.
 * Provides behavioral analysis, anomaly detection, and automated threat response.
 */
class ThreatDetection {
    
    private val logger = LoggerLogger(this::class)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        // Threat levels
        const val THREAT_LEVEL_LOW = "LOW"
        const val THREAT_LEVEL_MEDIUM = "MEDIUM"
        const val THREAT_LEVEL_HIGH = "HIGH"
        const val THREAT_LEVEL_CRITICAL = "CRITICAL"
        
        // Anomaly thresholds
        const val DEFAULT_ANOMALY_THRESHOLD = 2.0
        const val DEFAULT_BEHAVIOR_THRESHOLD = 3.0
        
        // Time windows for analysis
        const val SHORT_TERM_WINDOW_MINUTES = 5
        const val MEDIUM_TERM_WINDOW_MINUTES = 30
        const val LONG_TERM_WINDOW_HOURS = 24
    }
    
    private val securityEvents = mutableListOf<SecurityEvent>()
    private val userBehaviorProfiles = mutableMapOf<String, UserBehaviorProfile>()
    private val threatPatterns = mutableListOf<ThreatPattern>()
    private val anomalyDetectors = mutableMapOf<String, AnomalyDetector>()
    
    /**
     * Analyzes a security event for potential threats.
     * 
     * @param event The security event to analyze
     * @return Threat analysis result
     */
    suspend fun analyzeSecurityEvent(event: SecurityEvent): ThreatAnalysisResult {
        return try {
            logger.debug("security", "Analyzing security event: ${event.id}")
            
            // Add event to tracking
            securityEvents.add(event)
            
            // Update user behavior profile
            updateUserBehaviorProfile(event)
            
            // Detect anomalies
            val anomalies = detectAnomalies(event)
            
            // Check for threat patterns
            val threatPatterns = detectThreatPatterns(event)
            
            // Calculate threat score
            val threatScore = calculateThreatScore(event, anomalies, threatPatterns)
            
            // Determine threat level
            val threatLevel = determineThreatLevel(threatScore)
            
            // Generate recommendations
            val recommendations = generateThreatRecommendations(event, anomalies, threatPatterns)
            
            // Take automated action if needed
            val automatedAction = takeAutomatedAction(event, threatLevel)
            
            val result = ThreatAnalysisResult(
                eventId = event.id,
                threatLevel = threatLevel,
                threatScore = threatScore,
                anomalies = anomalies,
                threatPatterns = threatPatterns,
                recommendations = recommendations,
                automatedAction = automatedAction,
                timestamp = Clock.System.now()()
            )
            
            logger.info("security", "Threat analysis completed for event ${event.id}: $threatLevel")
            result
            
        } catch (e: Exception) {
            logger.error("secure storage", "Threat analysis failed for event ${event.id}: ${e.message}")
            ThreatAnalysisResult(
                eventId = event.id,
                threatLevel = THREAT_LEVEL_HIGH, // Default to high if analysis fails
                threatScore = 0.0,
                anomalies = emptyList(),
                threatPatterns = emptyList(),
                recommendations = listOf("Investigate analysis failure"),
                automatedAction = null,
                timestamp = Clock.System.now()()
            )
        }
    }
    
    /**
     * Detects anomalies in user behavior and system events.
     * 
     * @param event The current security event
     * @return List of detected anomalies
     */
    private suspend fun detectAnomalies(event: SecurityEvent): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        try {
            // Get user behavior profile
            val userProfile = userBehaviorProfiles[event.userId]
            
            if (userProfile != null) {
                // Check for behavioral anomalies
                val behavioralAnomalies = detectBehavioralAnomalies(event, userProfile)
                anomalies.addAll(behavioralAnomalies)
                
                // Check for temporal anomalies
                val temporalAnomalies = detectTemporalAnomalies(event, userProfile)
                anomalies.addAll(temporalAnomalies)
                
                // Check for geographic anomalies
                val geographicAnomalies = detectGeographicAnomalies(event, userProfile)
                anomalies.addAll(geographicAnomalies)
            }
            
            // Check for system-level anomalies
            val systemAnomalies = detectSystemAnomalies(event)
            anomalies.addAll(systemAnomalies)
            
            // Check for rate-based anomalies
            val rateAnomalies = detectRateAnomalies(event)
            anomalies.addAll(rateAnomalies)
            
        } catch (e: Exception) {
            logger.error("security", "Anomaly detection failed: ${e.message}")
        }
        
        return anomalies
    }
    
    /**
     * Detects behavioral anomalies based on user patterns.
     */
    private fun detectBehavioralAnomalies(
        event: SecurityEvent,
        profile: UserBehaviorProfile
    ): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Check device usage patterns
        if (event.deviceInfo != null && profile.commonDevices.isNotEmpty()) {
            val isKnownDevice = profile.commonDevices.any { device ->
                device.contains(event.deviceInfo!!, ignoreCase = true)
            }
            
            if (!isKnownDevice) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.UNKNOWN_DEVICE,
                        severity = AnomalySeverity.MEDIUM,
                        description = "User accessed from unknown device: ${event.deviceInfo}",
                        confidence = 0.8,
                        metadata = mapOf(
                            "deviceInfo" to (event.deviceInfo ?: "unknown"),
                            "knownDevices" to profile.commonDevices.joinToString(", ")
                        )
                    )
                )
            }
        }
        
        // Check time-based patterns
        val eventHour = event.timestamp.hour
        val isUsualTime = profile.typicalUsageHours.contains(eventHour)
        
        if (!isUsualTime) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.UNUSUAL_TIME,
                    severity = AnomalySeverity.LOW,
                    description = "User activity at unusual time: ${eventHour}:00",
                    confidence = 0.6,
                    metadata = mapOf(
                        "eventHour" to eventHour.toString(),
                        "typicalHours" to profile.typicalUsageHours.joinToString(", ")
                    )
                )
            )
        }
        
        // Check action patterns
        val actionFrequency = profile.actionFrequencies[event.eventType] ?: 0
        val currentFrequency = getRecentActionFrequency(event.userId, event.eventType)
        
        if (currentFrequency > actionFrequency * 2) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.UNUSUAL_ACTIVITY_FREQUENCY,
                    severity = AnomalySeverity.MEDIUM,
                    description = "Unusual frequency of ${event.eventType} actions",
                    confidence = 0.7,
                    metadata = mapOf(
                        "eventType" to event.eventType,
                        "expectedFrequency" to actionFrequency.toString(),
                        "currentFrequency" to currentFrequency.toString()
                    )
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Detects temporal anomalies in user behavior.
     */
    private fun detectTemporalAnomalies(
        event: SecurityEvent,
        profile: UserBehaviorProfile
    ): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Check for rapid successive actions
        val recentEvents = getRecentEvents(event.userId, SHORT_TERM_WINDOW_MINUTES)
        if (recentEvents.size > profile.maxActionsPerMinute * 2) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.RAPID_ACTIONS,
                    severity = AnomalySeverity.HIGH,
                    description = "Rapid successive actions detected",
                    confidence = 0.8,
                    metadata = mapOf(
                        "recentActions" to recentEvents.size.toString(),
                        "maxExpected" to (profile.maxActionsPerMinute * 2).toString()
                    )
                )
            )
        }
        
        // Check for unusual time gaps
        val lastEvent = getLastEvent(event.userId)
        if (lastEvent != null) {
            val timeGap = // Duration calculation required(lastEvent.timestamp, event.timestamp)
            val isUnusualGap = timeGap < profile.minTimeBetweenActions || timeGap > profile.maxTimeBetweenActions
            
            if (isUnusualGap) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.UNUSUAL_TIME_GAP,
                        severity = AnomalySeverity.LOW,
                        description = "Unusual time gap between actions: ${timeGap} minutes",
                        confidence = 0.5,
                        metadata = mapOf(
                            "timeGap" to timeGap.toString(),
                            "expectedRange" to "${profile.minTimeBetweenActions}-${profile.maxTimeBetweenActions} minutes"
                        )
                    )
                )
            }
        }
        
        return anomalies
    }
    
    /**
     * Detects geographic anomalies in user access patterns.
     */
    private fun detectGeographicAnomalies(
        event: SecurityEvent,
        profile: UserBehaviorProfile
    ): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        if (event.ipAddress != null && profile.commonLocations.isNotEmpty()) {
            val isKnownLocation = profile.commonLocations.any { location ->
                location.contains(event.ipAddress!!, ignoreCase = true)
            }
            
            if (!isKnownLocation) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.UNKNOWN_LOCATION,
                        severity = AnomalySeverity.HIGH,
                        description = "User access from unknown location: ${event.ipAddress}",
                        confidence = 0.9,
                        metadata = mapOf(
                            "ipAddress" to event.ipAddress,
                            "knownLocations" to profile.commonLocations.joinToString(", ")
                        )
                    )
                )
            }
        }
        
        return anomalies
    }
    
    /**
     * Detects system-level anomalies.
     */
    private fun detectSystemAnomalies(event: SecurityEvent): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Check for failed authentication attempts
        if (event.eventType == "AUTH_FAILURE") {
            val recentFailures = getRecentEvents(event.userId, SHORT_TERM_WINDOW_MINUTES)
                .count { it.eventType == "AUTH_FAILURE" }
            
            if (recentFailures > 5) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.MULTIPLE_AUTH_FAILURES,
                        severity = AnomalySeverity.HIGH,
                        description = "Multiple authentication failures detected",
                        confidence = 0.9,
                        metadata = mapOf(
                            "failureCount" to recentFailures.toString(),
                            "timeWindow" to "${SHORT_TERM_WINDOW_MINUTES} minutes"
                        )
                    )
                )
            }
        }
        
        // Check for unusual event types
        val eventTypeFrequency = getEventTypeFrequency(event.eventType)
        if (eventTypeFrequency < 0.01) { // Less than 1% of all events
            anomalies.add(
                Anomaly(
                    type = AnomalyType.RARE_EVENT_TYPE,
                    severity = AnomalySeverity.MEDIUM,
                    description = "Rare event type detected: ${event.eventType}",
                    confidence = 0.6,
                    metadata = mapOf(
                        "eventType" to event.eventType,
                        "frequency" to "${eventTypeFrequency * 100}%"
                    )
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Detects rate-based anomalies.
     */
    private fun detectRateAnomalies(event: SecurityEvent): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Check global event rate
        val globalEventRate = getGlobalEventRate()
        val currentRate = getCurrentEventRate()
        
        if (currentRate > globalEventRate * 3) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.HIGH_EVENT_RATE,
                    severity = AnomalySeverity.MEDIUM,
                    description = "Unusually high event rate detected",
                    confidence = 0.7,
                    metadata = mapOf(
                        "currentRate" to currentRate.toString(),
                        "expectedRate" to globalEventRate.toString(),
                        "ratio" to (currentRate / globalEventRate).toString()
                    )
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Detects threat patterns based on historical data.
     */
    private suspend fun detectThreatPatterns(event: SecurityEvent): List<ThreatPattern> {
        val patterns = mutableListOf<ThreatPattern>()
        
        try {
            // Check for brute force patterns
            val bruteForcePattern = detectBruteForcePattern(event)
            if (bruteForcePattern != null) {
                patterns.add(bruteForcePattern)
            }
            
            // Check for credential stuffing patterns
            val credentialStuffingPattern = detectCredentialStuffingPattern(event)
            if (credentialStuffingPattern != null) {
                patterns.add(credentialStuffingPattern)
            }
            
            // Check for session hijacking patterns
            val sessionHijackingPattern = detectSessionHijackingPattern(event)
            if (sessionHijackingPattern != null) {
                patterns.add(sessionHijackingPattern)
            }
            
            // Check for data exfiltration patterns
            val dataExfiltrationPattern = detectDataExfiltrationPattern(event)
            if (dataExfiltrationPattern != null) {
                patterns.add(dataExfiltrationPattern)
            }
            
        } catch (e: Exception) {
            logger.error("security", "Threat pattern detection failed: ${e.message}")
        }
        
        return patterns
    }
    
    /**
     * Detects brute force attack patterns.
     */
    private fun detectBruteForcePattern(event: SecurityEvent): ThreatPattern? {
        val recentFailures = getRecentEvents(event.userId, SHORT_TERM_WINDOW_MINUTES)
            .filter { it.eventType == "AUTH_FAILURE" }
        
        if (recentFailures.size >= 10) {
            return ThreatPattern(
                type = ThreatPatternType.BRUTE_FORCE,
                severity = ThreatPatternSeverity.HIGH,
                description = "Potential brute force attack detected",
                confidence = 0.9,
                indicators = listOf(
                    "Multiple authentication failures",
                    "Short time window",
                    "Same user account"
                ),
                metadata = mapOf(
                    "failureCount" to recentFailures.size.toString(),
                    "timeWindow" to "${SHORT_TERM_WINDOW_MINUTES} minutes",
                    "userId" to event.userId
                )
            )
        }
        
        return null
    }
    
    /**
     * Detects credential stuffing attack patterns.
     */
    private fun detectCredentialStuffingPattern(event: SecurityEvent): ThreatPattern? {
        val recentFailures = getRecentEvents(null, SHORT_TERM_WINDOW_MINUTES)
            .filter { it.eventType == "AUTH_FAILURE" }
        
        val uniqueUsers = recentFailures.map { it.userId }.distinct()
        
        if (recentFailures.size >= 50 && uniqueUsers.size >= 20) {
            return ThreatPattern(
                type = ThreatPatternType.CREDENTIAL_STUFFING,
                severity = ThreatPatternSeverity.HIGH,
                description = "Potential credential stuffing attack detected",
                confidence = 0.8,
                indicators = listOf(
                    "Multiple authentication failures",
                    "Multiple user accounts",
                    "Short time window"
                ),
                metadata = mapOf(
                    "failureCount" to recentFailures.size.toString(),
                    "uniqueUsers" to uniqueUsers.size.toString(),
                    "timeWindow" to "${SHORT_TERM_WINDOW_MINUTES} minutes"
                )
            )
        }
        
        return null
    }
    
    /**
     * Detects session hijacking patterns.
     */
    private fun detectSessionHijackingPattern(event: SecurityEvent): ThreatPattern? {
        val recentSessions = getRecentEvents(event.userId, MEDIUM_TERM_WINDOW_MINUTES)
            .filter { it.eventType == "SESSION_CREATED" }
        
        val uniqueDevices = recentSessions.mapNotNull { it.deviceInfo }.distinct()
        val uniqueLocations = recentSessions.mapNotNull { it.ipAddress }.distinct()
        
        if (uniqueDevices.size > 3 || uniqueLocations.size > 3) {
            return ThreatPattern(
                type = ThreatPatternType.SESSION_HIJACKING,
                severity = ThreatPatternSeverity.MEDIUM,
                description = "Potential session hijacking detected",
                confidence = 0.7,
                indicators = listOf(
                    "Multiple devices",
                    "Multiple locations",
                    "Short time window"
                ),
                metadata = mapOf(
                    "uniqueDevices" to uniqueDevices.size.toString(),
                    "uniqueLocations" to uniqueLocations.size.toString(),
                    "timeWindow" to "${MEDIUM_TERM_WINDOW_MINUTES} minutes"
                )
            )
        }
        
        return null
    }
    
    /**
     * Detects data exfiltration patterns.
     */
    private fun detectDataExfiltrationPattern(event: SecurityEvent): ThreatPattern? {
        val recentDataAccess = getRecentEvents(event.userId, MEDIUM_TERM_WINDOW_MINUTES)
            .filter { it.eventType in listOf("DATA_ACCESS", "DATA_EXPORT", "DATA_DOWNLOAD") }
        
        if (recentDataAccess.size > 100) {
            return ThreatPattern(
                type = ThreatPatternType.DATA_EXFILTRATION,
                severity = ThreatPatternSeverity.HIGH,
                description = "Potential data exfiltration detected",
                confidence = 0.8,
                indicators = listOf(
                    "High volume data access",
                    "Multiple data operations",
                    "Short time window"
                ),
                metadata = mapOf(
                    "dataOperations" to recentDataAccess.size.toString(),
                    "timeWindow" to "${MEDIUM_TERM_WINDOW_MINUTES} minutes"
                )
            )
        }
        
        return null
    }
    
    /**
     * Calculates overall threat score based on event and anomalies.
     */
    private fun calculateThreatScore(
        event: SecurityEvent,
        anomalies: List<Anomaly>,
        threatPatterns: List<ThreatPattern>
    ): Double {
        var score = 0.0
        
        // Base score from event type
        score += getEventTypeThreatScore(event.eventType)
        
        // Add anomaly scores
        anomalies.forEach { anomaly ->
            score += getAnomalyThreatScore(anomaly)
        }
        
        // Add threat pattern scores
        threatPatterns.forEach { pattern ->
            score += getThreatPatternScore(pattern)
        }
        
        // Normalize score to 0-100 range
        return (score / 10.0).coerceIn(0.0, 100.0)
    }
    
    /**
     * Determines threat level based on threat score.
     */
    private fun determineThreatLevel(threatScore: Double): String {
        return when {
            threatScore >= 80 -> THREAT_LEVEL_CRITICAL
            threatScore >= 60 -> THREAT_LEVEL_HIGH
            threatScore >= 40 -> THREAT_LEVEL_MEDIUM
            threatScore >= 20 -> THREAT_LEVEL_LOW
            else -> THREAT_LEVEL_LOW
        }
    }
    
    /**
     * Generates threat response recommendations.
     */
    private fun generateThreatRecommendations(
        event: SecurityEvent,
        anomalies: List<Anomaly>,
        threatPatterns: List<ThreatPattern>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Add recommendations based on anomalies
        anomalies.forEach { anomaly ->
            when (anomaly.type) {
                AnomalyType.UNKNOWN_DEVICE -> {
                    recommendations.add("Verify device ownership and consider additional authentication")
                    recommendations.add("Monitor for additional suspicious activity")
                }
                AnomalyType.UNKNOWN_LOCATION -> {
                    recommendations.add("Verify location authenticity and consider geographic restrictions")
                    recommendations.add("Enable location-based authentication if available")
                }
                AnomalyType.MULTIPLE_AUTH_FAILURES -> {
                    recommendations.add("Implement account lockout after multiple failures")
                    recommendations.add("Enable CAPTCHA or additional verification")
                }
                AnomalyType.RAPID_ACTIONS -> {
                    recommendations.add("Implement rate limiting for user actions")
                    recommendations.add("Add additional verification for rapid actions")
                }
                else -> {
                    recommendations.add("Investigate anomaly: ${anomaly.description}")
                }
            }
        }
        
        // Add recommendations based on threat patterns
        threatPatterns.forEach { pattern ->
            when (pattern.type) {
                ThreatPatternType.BRUTE_FORCE -> {
                    recommendations.add("Implement progressive delays between login attempts")
                    recommendations.add("Consider temporary account suspension")
                }
                ThreatPatternType.CREDENTIAL_STUFFING -> {
                    recommendations.add("Implement CAPTCHA for multiple failed attempts")
                    recommendations.add("Monitor for credential dumps and enforce password changes")
                }
                ThreatPatternType.SESSION_HIJACKING -> {
                    recommendations.add("Implement device fingerprinting")
                    recommendations.add("Add location-based session validation")
                }
                ThreatPatternType.DATA_EXFILTRATION -> {
                    recommendations.add("Implement data access monitoring and alerts")
                    recommendations.add("Add approval workflows for bulk data operations")
                }
            }
        }
        
        // Add general recommendations
        if (recommendations.isEmpty()) {
            recommendations.add("Continue monitoring for additional indicators")
            recommendations.add("Review user activity patterns for unusual behavior")
        }
        
        return recommendations
    }
    
    /**
     * Takes automated action based on threat level.
     */
    private suspend fun takeAutomatedAction(
        event: SecurityEvent,
        threatLevel: String
    ): AutomatedAction? {
        return when (threatLevel) {
            THREAT_LEVEL_CRITICAL -> {
                logger.warn("security", "Taking critical threat action for event ${event.id}")
                AutomatedAction(
                    type = ActionType.ACCOUNT_LOCKOUT,
                    description = "Account locked due to critical threat",
                    severity = ActionSeverity.CRITICAL,
                    timestamp = Clock.System.now()()
                )
            }
            THREAT_LEVEL_HIGH -> {
                logger.info("security", "Taking high threat action for event ${event.id}")
                AutomatedAction(
                    type = ActionType.ADDITIONAL_VERIFICATION,
                    description = "Additional verification required",
                    severity = ActionSeverity.HIGH,
                    timestamp = Clock.System.now()()
                )
            }
            THREAT_LEVEL_MEDIUM -> {
                logger.info("security", "Taking medium threat action for event ${event.id}")
                AutomatedAction(
                    type = ActionType.MONITORING_ENHANCED,
                    description = "Enhanced monitoring enabled",
                    severity = ActionSeverity.MEDIUM,
                    timestamp = Clock.System.now()()
                )
            }
            else -> null
        }
    }
    
    // Helper methods for data retrieval and analysis
    
    private fun updateUserBehaviorProfile(event: SecurityEvent) {
        val profile = userBehaviorProfilesOrPut(event.userId) {
            UserBehaviorProfile(
                userId = event.userId,
                commonDevices = mutableListOf(),
                commonLocations = mutableListOf(),
                typicalUsageHours = mutableSetOf(),
                actionFrequencies = mutableMapOf(),
                maxActionsPerMinute = 10,
                minTimeBetweenActions = 1,
                maxTimeBetweenActions = 1440 // 24 hours
            )
        }
        
        // Update device info
        if (event.deviceInfo != null && !profile.commonDevices.contains(event.deviceInfo)) {
            profile.commonDevices.add(event.deviceInfo)
        }
        
        // Update location info
        if (event.ipAddress != null && !profile.commonLocations.contains(event.ipAddress)) {
            profile.commonLocations.add(event.ipAddress)
        }
        
        // Update usage hours
        profile.typicalUsageHours.add(event.timestamp.hour)
        
        // Update action frequencies
        val currentFreq = profile.actionFrequencies[event.eventType] ?: 0
        profile.actionFrequencies[event.eventType] = currentFreq + 1
    }
    
    private fun getRecentEvents(userId: String?, windowMinutes: Int): List<SecurityEvent> {
        val cutoffTime = Clock.System.now()().minus(windowMinutes.toLong(), ChronoUnit.MINUTES)
        return securityEvents.filter { event ->
            event.timestamp.isAfter(cutoffTime) && (userId == null || event.userId == userId)
        }
    }
    
    private fun getLastEvent(userId: String): SecurityEvent? {
        return securityEvents.filter { it.userId == userId }
            .maxByOrNull { it.timestamp }
    }
    
    private fun getRecentActionFrequency(userId: String, eventType: String): Int {
        return getRecentEvents(userId, SHORT_TERM_WINDOW_MINUTES)
            .count { it.eventType == eventType }
    }
    
    private fun getEventTypeFrequency(eventType: String): Double {
        if (securityEvents.isEmpty()) return 0.0
        val count = securityEvents.count { it.eventType == eventType }
        return count.toDouble() / securityEvents.size
    }
    
    private fun getGlobalEventRate(): Double {
        val recentEvents = getRecentEvents(null, SHORT_TERM_WINDOW_MINUTES)
        return recentEvents.size.toDouble() / SHORT_TERM_WINDOW_MINUTES
    }
    
    private fun getCurrentEventRate(): Double {
        val recentEvents = getRecentEvents(null, 1) // Last minute
        return recentEvents.size.toDouble()
    }
    
    private fun getEventTypeThreatScore(eventType: String): Double {
        return when (eventType) {
            "AUTH_FAILURE" -> 10.0
            "ACCOUNT_LOCKED" -> 15.0
            "PASSWORD_CHANGED" -> 5.0
            "SESSION_CREATED" -> 2.0
            "DATA_ACCESS" -> 3.0
            "DATA_EXPORT" -> 8.0
            else -> 1.0
        }
    }
    
    private fun getAnomalyThreatScore(anomaly: Anomaly): Double {
        return when (anomaly.severity) {
            AnomalySeverity.LOW -> 5.0
            AnomalySeverity.MEDIUM -> 10.0
            AnomalySeverity.HIGH -> 20.0
            AnomalySeverity.CRITICAL -> 30.0
        } * anomaly.confidence
    }
    
    private fun getThreatPatternScore(pattern: ThreatPattern): Double {
        return when (pattern.severity) {
            ThreatPatternSeverity.LOW -> 15.0
            ThreatPatternSeverity.MEDIUM -> 25.0
            ThreatPatternSeverity.HIGH -> 40.0
            ThreatPatternSeverity.CRITICAL -> 60.0
        } * pattern.confidence
    }
}

// Data classes for threat detection

@Serializable
data class SecurityEvent(
    val id: String,
    val userId: String,
    val eventType: String,
    val timestamp: Instant,
    val ipAddress: String?,
    val deviceInfo: String?,
    val userAgent: String?,
    val metadata: Map<String, String>?
)

@Serializable
data class UserBehaviorProfile(
    val userId: String,
    val commonDevices: MutableList<String>,
    val commonLocations: MutableList<String>,
    val typicalUsageHours: MutableSet<Int>,
    val actionFrequencies: MutableMap<String, Int>,
    val maxActionsPerMinute: Int,
    val minTimeBetweenActions: Int,
    val maxTimeBetweenActions: Int
)

@Serializable
data class Anomaly(
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val description: String,
    val confidence: Double,
    val metadata: Map<String, String>
)

@Serializable
data class ThreatPattern(
    val type: ThreatPatternType,
    val severity: ThreatPatternSeverity,
    val description: String,
    val confidence: Double,
    val indicators: List<String>,
    val metadata: Map<String, String>
)

@Serializable
data class ThreatAnalysisResult(
    val eventId: String,
    val threatLevel: String,
    val threatScore: Double,
    val anomalies: List<Anomaly>,
    val threatPatterns: List<ThreatPattern>,
    val recommendations: List<String>,
    val automatedAction: AutomatedAction?,
    val timestamp: Instant
)

@Serializable
data class AutomatedAction(
    val type: ActionType,
    val description: String,
    val severity: ActionSeverity,
    val timestamp: Instant
)

// Enums for threat detection

// enum class AnomalyType {
//     UNKNOWN_DEVICE,
//     UNKNOWN_LOCATION,
//     UNUSUAL_TIME,
//     UNUSUAL_ACTIVITY_FREQUENCY,
//     RAPID_ACTIONS,
//     UNUSUAL_TIME_GAP,
//     MULTIPLE_AUTH_FAILURES,
//     RARE_EVENT_TYPE,
//     HIGH_EVENT_RATE
// }

enum class AnomalySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class ThreatPatternType {
    BRUTE_FORCE,
    CREDENTIAL_STUFFING,
    SESSION_HIJACKING,
    DATA_EXFILTRATION
}

enum class ThreatPatternSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class ActionType {
    ACCOUNT_LOCKOUT,
    ADDITIONAL_VERIFICATION,
    MONITORING_ENHANCED,
    ALERT_SENT
}

enum class ActionSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}