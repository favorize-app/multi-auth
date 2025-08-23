package app.multiauth.security

import app.multiauth.models.User
import app.multiauth.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Security audit logger for tracking security-related events and activities.
 * Provides comprehensive logging and monitoring capabilities.
 */
class SecurityAuditLogger {
    
    private val logger = Logger.getLogger(this::class)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        private const val MAX_AUDIT_ENTRIES = 10000
        private const val RETENTION_DAYS = 90L
        private const val SUSPICIOUS_ACTIVITY_THRESHOLD = 5
        private const val SUSPICIOUS_ACTIVITY_WINDOW_MINUTES = 15L
    }
    
    private val _auditEntries = MutableStateFlow<List<SecurityAuditEntry>>(emptyList())
    val auditEntries: StateFlow<List<SecurityAuditEntry>> = _auditEntries.asStateFlow()
    
    private val _securityMetrics = MutableStateFlow(SecurityMetrics())
    val securityMetrics: StateFlow<SecurityMetrics> = _securityMetrics.asStateFlow()
    
    private val _suspiciousActivities = MutableStateFlow<List<SuspiciousActivity>>(emptyList())
    val suspiciousActivities: StateFlow<List<SuspiciousActivity>> = _suspiciousActivities.asStateFlow()
    
    private val _userActivityCounts = ConcurrentHashMap<String, MutableList<Instant>>()
    private val _ipActivityCounts = ConcurrentHashMap<String, MutableList<Instant>>()
    
    /**
     * Logs a security event.
     * 
     * @param event The security event to log
     * @param user The user associated with the event (if applicable)
     * @param metadata Additional metadata about the event
     */
    fun logSecurityEvent(
        event: SecurityEvent,
        user: User? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        scope.launch {
            try {
                val entry = SecurityAuditEntry(
                    timestamp = Instant.now(),
                    event = event,
                    userId = user?.id,
                    userEmail = user?.email,
                    ipAddress = metadata["ipAddress"] as? String,
                    userAgent = metadata["userAgent"] as? String,
                    sessionId = metadata["sessionId"] as? String,
                    metadata = metadata,
                    severity = event.severity
                )
                
                addAuditEntry(entry)
                updateSecurityMetrics(event, user)
                checkForSuspiciousActivity(user?.id, metadata["ipAddress"] as? String)
                
                logger.info("security", "Security event logged: ${event.name} for user: ${user?.displayName ?: "unknown"}")
                
            } catch (e: Exception) {
                logger.error("security", "Failed to log security event", e)
            }
        }
    }
    
    /**
     * Logs a failed authentication attempt.
     * 
     * @param userEmail The email that was used in the failed attempt
     * @param reason The reason for the failure
     * @param metadata Additional metadata
     */
    fun logFailedAuthentication(
        userEmail: String,
        reason: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val event = SecurityEvent.AUTHENTICATION_FAILED
        val entry = SecurityAuditEntry(
            timestamp = Instant.now(),
            event = event,
            userId = null,
            userEmail = userEmail,
            ipAddress = metadata["ipAddress"] as? String,
            userAgent = metadata["userAgent"] as? String,
            sessionId = metadata["sessionId"] as? String,
            metadata = metadata + ("reason" to reason),
            severity = event.severity
        )
        
        addAuditEntry(entry)
        updateSecurityMetrics(event, null)
        checkForSuspiciousActivity(null, metadata["ipAddress"] as? String)
        
        logger.warn("Failed authentication attempt for email: $userEmail, reason: $reason")
    }
    
    /**
     * Logs a successful authentication.
     * 
     * @param user The user who authenticated successfully
     * @param method The authentication method used
     * @param metadata Additional metadata
     */
    fun logSuccessfulAuthentication(
        user: User,
        method: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val event = SecurityEvent.AUTHENTICATION_SUCCESS
        val entry = SecurityAuditEntry(
            timestamp = Instant.now(),
            event = event,
            userId = user.id,
            userEmail = user.email,
            ipAddress = metadata["ipAddress"] as? String,
            userAgent = metadata["userAgent"] as? String,
            sessionId = metadata["sessionId"] as? String,
            metadata = metadata + ("method" to method),
            severity = event.severity
        )
        
        addAuditEntry(entry)
        updateSecurityMetrics(event, user)
        
        logger.info("Successful authentication for user: ${user.displayName}, method: $method")
    }
    
    /**
     * Logs a suspicious activity detection.
     * 
     * @param activity The suspicious activity detected
     * @param user The user associated with the activity (if applicable)
     */
    fun logSuspiciousActivity(
        activity: SuspiciousActivity,
        user: User? = null
    ) {
        val event = SecurityEvent.SUSPICIOUS_ACTIVITY_DETECTED
        val entry = SecurityAuditEntry(
            timestamp = Instant.now(),
            event = event,
            userId = user?.id,
            userEmail = user?.email,
            ipAddress = activity.ipAddress,
            userAgent = null,
            sessionId = null,
            metadata = mapOf(
                "activityType" to activity.type.name,
                "description" to activity.description,
                "riskScore" to activity.riskScore
            ),
            severity = event.severity
        )
        
        addAuditEntry(entry)
        addSuspiciousActivity(activity)
        
        logger.warn("security", "Suspicious activity detected: ${activity.description} for user: ${user?.displayName ?: "unknown"}")
    }
    
    /**
     * Gets audit entries for a specific user.
     * 
     * @param userId The user ID to get entries for
     * @param limit Maximum number of entries to return
     * @return List of audit entries
     */
    fun getUserAuditEntries(userId: String, limit: Int = 100): List<SecurityAuditEntry> {
        return _auditEntries.value
            .filter { it.userId == userId }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Gets audit entries for a specific time range.
     * 
     * @param startTime Start time for the range
     * @param endTime End time for the range
     * @param limit Maximum number of entries to return
     * @return List of audit entries
     */
    fun getAuditEntriesInRange(
        startTime: Instant,
        endTime: Instant,
        limit: Int = 1000
    ): List<SecurityAuditEntry> {
        return _auditEntries.value
            .filter { it.timestamp in startTime..endTime }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Gets audit entries by severity level.
     * 
     * @param severity The severity level to filter by
     * @param limit Maximum number of entries to return
     * @return List of audit entries
     */
    fun getAuditEntriesBySeverity(
        severity: SecuritySeverity,
        limit: Int = 1000
    ): List<SecurityAuditEntry> {
        return _auditEntries.value
            .filter { it.severity == severity }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Cleans up old audit entries based on retention policy.
     */
    fun cleanupOldEntries() {
        scope.launch {
            try {
                val cutoffTime = Instant.now().minusSeconds(RETENTION_DAYS * 24 * 60 * 60)
                val currentEntries = _auditEntries.value
                val filteredEntries = currentEntries.filter { it.timestamp.isAfter(cutoffTime) }
                
                if (filteredEntries.size != currentEntries.size) {
                    _auditEntries.value = filteredEntries
                    logger.info("security", "Cleaned up ${currentEntries.size - filteredEntries.size} old audit entries")
                }
            } catch (e: Exception) {
                logger.error("security", "Failed to cleanup old audit entries", e)
            }
        }
    }
    
    /**
     * Exports audit data for analysis.
     * 
     * @param format The export format (CSV, JSON, etc.)
     * @param filters Optional filters to apply
     * @return Exported audit data
     */
    fun exportAuditData(format: ExportFormat, filters: AuditFilters? = null): String {
        return try {
            val entries = applyFilters(_auditEntries.value, filters)
            
            when (format) {
                ExportFormat.CSV -> exportToCsv(entries)
                ExportFormat.JSON -> exportToJson(entries)
                ExportFormat.XML -> exportToXml(entries)
            }
        } catch (e: Exception) {
            logger.error("security", "Failed to export audit data", e)
            throw AuditExportException("Failed to export audit data", e)
        }
    }
    
    // Private implementation methods
    
    private fun addAuditEntry(entry: SecurityAuditEntry) {
        val currentEntries = _auditEntries.value.toMutableList()
        currentEntries.add(0, entry)
        
        // Limit the number of entries
        if (currentEntries.size > MAX_AUDIT_ENTRIES) {
            currentEntries.removeAt(currentEntries.lastIndex)
        }
        
        _auditEntries.value = currentEntries
    }
    
    private fun updateSecurityMetrics(event: SecurityEvent, user: User?) {
        val currentMetrics = _securityMetrics.value
        
        val updatedMetrics = when (event) {
            SecurityEvent.AUTHENTICATION_SUCCESS -> currentMetrics.copy(
                successfulAuthentications = currentMetrics.successfulAuthentications + 1
            )
            SecurityEvent.AUTHENTICATION_FAILED -> currentMetrics.copy(
                failedAuthentications = currentMetrics.failedAuthentications + 1
            )
            SecurityEvent.ACCOUNT_LOCKED -> currentMetrics.copy(
                accountsLocked = currentMetrics.accountsLocked + 1
            )
            SecurityEvent.SUSPICIOUS_ACTIVITY_DETECTED -> currentMetrics.copy(
                suspiciousActivitiesDetected = currentMetrics.suspiciousActivitiesDetected + 1
            )
            else -> currentMetrics
        }
        
        _securityMetrics.value = updatedMetrics
    }
    
    private fun checkForSuspiciousActivity(userId: String?, ipAddress: String?) {
        // Check user activity
        userId?.let { checkUserActivity(it) }
        
        // Check IP activity
        ipAddress?.let { checkIpActivity(it) }
    }
    
    private fun checkUserActivity(userId: String) {
        val userActivities = _userActivityCounts.getOrPut(userId) { mutableListOf() }
        val now = Instant.now()
        val cutoffTime = now.minusSeconds(SUSPICIOUS_ACTIVITY_WINDOW_MINUTES * 60)
        
        // Remove old activities
        userActivities.removeAll { it.isBefore(cutoffTime) }
        
        // Add current activity
        userActivities.add(now)
        
        // Check if suspicious
        if (userActivities.size >= SUSPICIOUS_ACTIVITY_THRESHOLD) {
            val suspiciousActivity = SuspiciousActivity(
                type = SuspiciousActivityType.HIGH_FREQUENCY_ACTIVITY,
                description = "High frequency activity detected for user: $userId",
                userId = userId,
                ipAddress = null,
                riskScore = SuspiciousActivityRisk.MEDIUM,
                timestamp = now,
                activityCount = userActivities.size
            )
            
            addSuspiciousActivity(suspiciousActivity)
        }
    }
    
    private fun checkIpActivity(ipAddress: String) {
        val ipActivities = _ipActivityCounts.getOrPut(ipAddress) { mutableListOf() }
        val now = Instant.now()
        val cutoffTime = now.minusSeconds(SUSPICIOUS_ACTIVITY_WINDOW_MINUTES * 60)
        
        // Remove old activities
        ipActivities.removeAll { it.isBefore(cutoffTime) }
        
        // Add current activity
        ipActivities.add(now)
        
        // Check if suspicious
        if (ipActivities.size >= SUSPICIOUS_ACTIVITY_THRESHOLD) {
            val suspiciousActivity = SuspiciousActivity(
                type = SuspiciousActivityType.HIGH_FREQUENCY_ACTIVITY,
                description = "High frequency activity detected from IP: $ipAddress",
                userId = null,
                ipAddress = ipAddress,
                riskScore = SuspiciousActivityRisk.MEDIUM,
                timestamp = now,
                activityCount = ipActivities.size
            )
            
            addSuspiciousActivity(suspiciousActivity)
        }
    }
    
    private fun addSuspiciousActivity(activity: SuspiciousActivity) {
        val currentActivities = _suspiciousActivities.value.toMutableList()
        currentActivities.add(0, activity)
        
        // Keep only recent suspicious activities
        if (currentActivities.size > 100) {
            currentActivities.removeAt(currentActivities.lastIndex)
        }
        
        _suspiciousActivities.value = currentActivities
    }
    
    private fun applyFilters(entries: List<SecurityAuditEntry>, filters: AuditFilters?): List<SecurityAuditEntry> {
        if (filters == null) return entries
        
        return entries.filter { entry ->
            var matches = true
            
            filters.userId?.let { if (entry.userId != it) matches = false }
            filters.severity?.let { if (entry.severity != it) matches = false }
            filters.eventType?.let { if (entry.event != it) matches = false }
            filters.startTime?.let { if (entry.timestamp.isBefore(it)) matches = false }
            filters.endTime?.let { if (entry.timestamp.isAfter(it)) matches = false }
            
            matches
        }
    }
    
    private fun exportToCsv(entries: List<SecurityAuditEntry>): String {
        val csv = StringBuilder()
        csv.appendLine("Timestamp,Event,UserId,UserEmail,IPAddress,Severity,Metadata")
        
        entries.forEach { entry ->
            csv.appendLine("${entry.timestamp},${entry.event.name},${entry.userId ?: ""},${entry.userEmail ?: ""},${entry.ipAddress ?: ""},${entry.severity.name},${entry.metadata}")
        }
        
        return csv.toString()
    }
    
    private fun exportToJson(entries: List<SecurityAuditEntry>): String {
        // In a real implementation, this would use a proper JSON library
        return entries.joinToString("\n") { entry ->
            """{"timestamp":"${entry.timestamp}","event":"${entry.event.name}","userId":"${entry.userId ?: ""}","severity":"${entry.severity.name}"}"""
        }
    }
    
    private fun exportToXml(entries: List<SecurityAuditEntry>): String {
        val xml = StringBuilder()
        xml.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        xml.appendLine("<auditLog>")
        
        entries.forEach { entry ->
            xml.appendLine("  <entry>")
            xml.appendLine("    <timestamp>${entry.timestamp}</timestamp>")
            xml.appendLine("    <event>${entry.event.name}</event>")
            xml.appendLine("    <userId>${entry.userId ?: ""}</userId>")
            xml.appendLine("    <severity>${entry.severity.name}</severity>")
            xml.appendLine("  </entry>")
        }
        
        xml.appendLine("</auditLog>")
        return xml.toString()
    }
}

/**
 * Represents a security audit entry.
 */
data class SecurityAuditEntry(
    val timestamp: Instant,
    val event: SecurityEvent,
    val userId: String?,
    val userEmail: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val sessionId: String?,
    val metadata: Map<String, Any>,
    val severity: SecuritySeverity
)

/**
 * Represents security metrics.
 */
data class SecurityMetrics(
    val successfulAuthentications: Long = 0,
    val failedAuthentications: Long = 0,
    val accountsLocked: Long = 0,
    val suspiciousActivitiesDetected: Long = 0,
    val lastUpdated: Instant = Instant.now()
)

/**
 * Represents a suspicious activity.
 */
data class SuspiciousActivity(
    val type: SuspiciousActivityType,
    val description: String,
    val userId: String?,
    val ipAddress: String?,
    val riskScore: SuspiciousActivityRisk,
    val timestamp: Instant,
    val activityCount: Int = 1
)

/**
 * Represents the type of suspicious activity.
 */
enum class SuspiciousActivityType {
    HIGH_FREQUENCY_ACTIVITY,
    MULTIPLE_FAILED_LOGINS,
    UNUSUAL_LOCATION,
    UNUSUAL_TIME,
    SUSPICIOUS_IP,
    ACCOUNT_TAKEOVER_ATTEMPT
}

/**
 * Represents the risk level of suspicious activity.
 */
enum class SuspiciousActivityRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Represents security severity levels.
 */
enum class SecuritySeverity {
    INFO,
    WARNING,
    HIGH,
    CRITICAL
}

/**
 * Represents security event types.
 */
enum class SecurityEvent(val severity: SecuritySeverity) {
    AUTHENTICATION_SUCCESS(SecuritySeverity.INFO),
    AUTHENTICATION_FAILED(SecuritySeverity.WARNING),
    ACCOUNT_LOCKED(SecuritySeverity.HIGH),
    ACCOUNT_UNLOCKED(SecuritySeverity.INFO),
    PASSWORD_CHANGED(SecuritySeverity.INFO),
    PASSWORD_RESET(SecuritySeverity.INFO),
    MFA_ENABLED(SecuritySeverity.INFO),
    MFA_DISABLED(SecuritySeverity.WARNING),
    SUSPICIOUS_ACTIVITY_DETECTED(SecuritySeverity.HIGH),
    SESSION_CREATED(SecuritySeverity.INFO),
    SESSION_EXPIRED(SecuritySeverity.INFO),
    SESSION_TERMINATED(SecuritySeverity.INFO)
}

/**
 * Represents export formats.
 */
enum class ExportFormat {
    CSV,
    JSON,
    XML
}

/**
 * Represents audit filters.
 */
data class AuditFilters(
    val userId: String? = null,
    val severity: SecuritySeverity? = null,
    val eventType: SecurityEvent? = null,
    val startTime: Instant? = null,
    val endTime: Instant? = null
)

/**
 * Exception thrown during audit export operations.
 */
class AuditExportException(message: String, cause: Throwable? = null) : Exception(message, cause)