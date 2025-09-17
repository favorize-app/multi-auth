@file:OptIn(ExperimentalTime::class)

package app.multiauth.models

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

/**
 * Represents a user session in the multi-auth system.
 * Contains information about active user sessions and their state.
 */
@Serializable
data class Session(
    /**
     * Unique session identifier
     */
    val id: String,

    /**
     * ID of the user this session belongs to
     */
    val userId: String,

    /**
     * Session token (JWT or similar)
     */
    val token: String,

    /**
     * Refresh token for extending session
     */
    val refreshToken: String?,

    /**
     * When this session was created
     */
    @Contextual
    val createdAt: Instant,

    /**
     * When this session was last accessed/used
     */
    @Contextual
    val lastAccessedAt: Instant,

    /**
     * When this session expires
     */
    @Contextual
    val expiresAt: Instant,

    /**
     * IP address where session was created
     */
    val ipAddress: String?,

    /**
     * User agent string from the client
     */
    val userAgent: String?,

    /**
     * Device information
     */
    val deviceInfo: DeviceInfo?,

    /**
     * Geographic location information
     */
    val locationInfo: LocationInfo?,

    /**
     * Session status
     */
    val status: SessionStatus = SessionStatus.ACTIVE,

    /**
     * Reason for session termination (if applicable)
     */
    val terminationReason: String? = null,

    /**
     * When session was terminated (if applicable)
     */
    @Contextual
    val terminatedAt: Instant? = null,

    /**
     * Whether this session was created via MFA
     */
    val isMfaVerified: Boolean = false,

    /**
     * OAuth provider used for this session (if applicable)
     */
    val oauthProviderId: String? = null,

    /**
     * Additional session metadata
     */
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Checks if the session is currently active
     */
    fun isActive(): Boolean {
        return status == SessionStatus.ACTIVE &&
               Clock.System.now().epochSeconds < expiresAt.epochSeconds
    }

    /**
     * Checks if the session is expired
     */
    fun isExpired(): Boolean {
        return Clock.System.now().epochSeconds >= expiresAt.epochSeconds
    }

    /**
     * Gets remaining session time in milliseconds
     */
    fun getRemainingTime(): Long {
        return maxOf(0, expiresAt.epochSeconds - Clock.System.now().epochSeconds)
    }

    /**
     * Creates a copy with updated last accessed time
     */
    fun withUpdatedAccess(): Session {
        return copy(lastAccessedAt = Clock.System.now())
    }

    /**
     * Creates a copy with terminated status
     */
    fun withTermination(reason: String): Session {
        return copy(
            status = SessionStatus.TERMINATED,
            terminationReason = reason,
            terminatedAt = Clock.System.now()
        )
    }

    /**
     * Gets a safe string representation without sensitive data
     */
    fun toSafeString(): String {
        return "Session(id=$id, userId=$userId, status=$status, createdAt=$createdAt, expiresAt=$expiresAt, ipAddress=$ipAddress, isExpired=${isExpired()})"
    }
}

/**
 * Session status enumeration
 */
@Serializable
enum class SessionStatus {
    /**
     * Session is active and valid
     */
    ACTIVE,

    /**
     * Session has been terminated/logged out
     */
    TERMINATED,

    /**
     * Session has expired
     */
    EXPIRED,

    /**
     * Session has been revoked (security reasons)
     */
    REVOKED,

    /**
     * Session is suspended temporarily
     */
    SUSPENDED
}

/**
 * Device information for session tracking
 */
@Serializable
data class DeviceInfo(
    val deviceId: String?,
    val deviceName: String?,
    val deviceType: DeviceType,
    val operatingSystem: String?,
    val osVersion: String?,
    val browser: String?,
    val browserVersion: String?,
    val isMobile: Boolean = false,
    val isTablet: Boolean = false
)

/**
 * Device type enumeration
 */
@Serializable
enum class DeviceType {
    DESKTOP,
    MOBILE,
    TABLET,
    TV,
    WATCH,
    UNKNOWN
}

/**
 * Location information for session tracking
 */
@Serializable
data class LocationInfo(
    val country: String?,
    val region: String?,
    val city: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String?,
    val isp: String?
)

/**
 * Session creation request
 */
@Serializable
data class SessionCreateRequest(
    val userId: String,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceInfo: DeviceInfo?,
    val locationInfo: LocationInfo?,
    val isMfaVerified: Boolean = false,
    val oauthProviderId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Session validation result
 */
sealed class SessionValidationResult {
    object Valid : SessionValidationResult()
    object Expired : SessionValidationResult()
    object Revoked : SessionValidationResult()
    object NotFound : SessionValidationResult()
    data class Invalid(val reason: String) : SessionValidationResult()
}
