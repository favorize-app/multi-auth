@file:OptIn(ExperimentalTime::class)

package app.multiauth.models

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

/**
 * Represents an audit log entry in the multi-auth system.
 * Tracks security events, user actions, and system changes for compliance and monitoring.
 */
@Serializable
data class AuditLog(
    /**
     * Unique identifier for this audit log entry
     */
    val id: String,

    /**
     * ID of the user who performed the action (null for system events)
     */
    val userId: String?,

    /**
     * Session ID associated with this event (if applicable)
     */
    val sessionId: String?,

    /**
     * Type of event that occurred
     */
    val eventType: AuditEventType,

    /**
     * Detailed description of the event
     */
    val eventDescription: String,

    /**
     * Result of the action (success, failure, etc.)
     */
    val result: AuditResult,

    /**
     * Severity level of this event
     */
    val severity: AuditSeverity,

    /**
     * When this event occurred
     */
    val timestamp: Instant,

    /**
     * IP address where the event originated
     */
    val ipAddress: String?,

    /**
     * User agent of the client (if applicable)
     */
    val userAgent: String?,

    /**
     * Geographic location information
     */
    val locationInfo: LocationInfo?,

    /**
     * Resource that was affected by this action
     */
    val resourceType: String?,

    /**
     * ID of the specific resource affected
     */
    val resourceId: String?,

    /**
     * Additional contextual information
     */
    val context: Map<String, String> = emptyMap(),

    /**
     * Risk score associated with this event (0-100)
     */
    val riskScore: Int = 0,

    /**
     * Whether this event triggered any security alerts
     */
    val triggeredAlerts: List<String> = emptyList(),

    /**
     * Correlation ID for grouping related events
     */
    val correlationId: String?,

    /**
     * Previous value (for update operations)
     */
    val previousValue: String?,

    /**
     * New value (for update operations)
     */
    val newValue: String?,

    /**
     * Error message (if result was failure)
     */
    val errorMessage: String?
) {
    /**
     * Checks if this is a security-sensitive event
     */
    fun isSecuritySensitive(): Boolean {
        return eventType in SECURITY_SENSITIVE_EVENTS ||
               severity in listOf(AuditSeverity.HIGH, AuditSeverity.CRITICAL) ||
               riskScore >= 70
    }

    /**
     * Checks if this event indicates suspicious activity
     */
    fun isSuspicious(): Boolean {
        return riskScore >= 80 ||
               triggeredAlerts.isNotEmpty() ||
               result == AuditResult.BLOCKED
    }

    /**
     * Gets a safe string representation for logging
     */
    fun toSafeString(): String {
        return "AuditLog(id=$id, userId=$userId, eventType=$eventType, result=$result, severity=$severity, timestamp=$timestamp, riskScore=$riskScore)"
    }

    companion object {
        private val SECURITY_SENSITIVE_EVENTS = setOf(
            AuditEventType.LOGIN_SUCCESS,
            AuditEventType.LOGIN_FAILURE,
            AuditEventType.LOGOUT,
            AuditEventType.PASSWORD_CHANGE,
            AuditEventType.PASSWORD_RESET,
            AuditEventType.MFA_ENABLED,
            AuditEventType.MFA_DISABLED,
            AuditEventType.OAUTH_LINK,
            AuditEventType.OAUTH_UNLINK,
            AuditEventType.ACCOUNT_LOCKED,
            AuditEventType.ACCOUNT_UNLOCKED,
            AuditEventType.PERMISSION_GRANTED,
            AuditEventType.PERMISSION_REVOKED,
            AuditEventType.SUSPICIOUS_ACTIVITY
        )
    }
}

/**
 * Types of events that can be audited
 */
@Serializable
enum class AuditEventType {
    // Authentication events
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    SESSION_CREATED,
    SESSION_EXPIRED,
    SESSION_TERMINATED,

    // Account management
    ACCOUNT_CREATED,
    ACCOUNT_UPDATED,
    ACCOUNT_DELETED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    ACCOUNT_VERIFIED,

    // Password management
    PASSWORD_CHANGE,
    PASSWORD_RESET,
    PASSWORD_RESET_REQUESTED,

    // Multi-factor authentication
    MFA_ENABLED,
    MFA_DISABLED,
    MFA_SUCCESS,
    MFA_FAILURE,
    MFA_BACKUP_CODE_USED,

    // OAuth events
    OAUTH_LINK,
    OAUTH_UNLINK,
    OAUTH_TOKEN_REFRESH,

    // Permission and access control
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    ROLE_ASSIGNED,
    ROLE_REMOVED,

    // Security events
    SUSPICIOUS_ACTIVITY,
    RATE_LIMIT_EXCEEDED,
    BRUTE_FORCE_DETECTED,
    ACCOUNT_TAKEOVER_ATTEMPT,

    // System events
    SYSTEM_STARTUP,
    SYSTEM_SHUTDOWN,
    DATABASE_MIGRATION,
    CONFIG_CHANGE,

    // Data events
    DATA_EXPORT,
    DATA_IMPORT,
    DATA_DELETION,

    // Generic events
    OTHER
}

/**
 * Result of an audited action
 */
@Serializable
enum class AuditResult {
    SUCCESS,
    FAILURE,
    PARTIAL_SUCCESS,
    BLOCKED,
    PENDING,
    CANCELLED
}

/**
 * Severity levels for audit events
 */
@Serializable
enum class AuditSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Audit log query parameters for filtering and searching
 */
@Serializable
data class AuditLogQuery(
    val userId: String? = null,
    val sessionId: String? = null,
    val eventTypes: List<AuditEventType>? = null,
    val results: List<AuditResult>? = null,
    val severities: List<AuditSeverity>? = null,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val ipAddress: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val minRiskScore: Int? = null,
    val maxRiskScore: Int? = null,
    val correlationId: String? = null,
    val hasAlerts: Boolean? = null,
    val limit: Int = 100,
    val offset: Int = 0
)

/**
 * Audit log statistics for reporting and monitoring
 */
@Serializable
data class AuditLogStats(
    val totalEvents: Long,
    val eventsByType: Map<AuditEventType, Long>,
    val eventsByResult: Map<AuditResult, Long>,
    val eventsBySeverity: Map<AuditSeverity, Long>,
    val suspiciousEventCount: Long,
    val highRiskEventCount: Long,
    val alertTriggeredCount: Long,
    val uniqueUsersCount: Long,
    val uniqueIpAddressesCount: Long,
    val timeRange: Pair<Instant, Instant>?
)
