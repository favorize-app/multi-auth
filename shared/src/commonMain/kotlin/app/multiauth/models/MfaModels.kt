@file:OptIn(ExperimentalTime::class)

package app.multiauth.models

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

/**
 * Multi-factor authentication related models and events.
 */

/**
 * MFA event for the event system.
 */
@Serializable
sealed class Mfa {
    @Serializable
    data class TotpEnabled(
        val userId: String,
        val backupCodes: List<String>,
        val metadata: Map<String, String> = emptyMap()
    ) : Mfa()

    @Serializable
    data class TotpDisabled(
        val userId: String,
        val metadata: Map<String, String> = emptyMap()
    ) : Mfa()

    @Serializable
    data class SmsEnabled(
        val userId: String,
        val phoneNumber: String,
        val metadata: Map<String, String> = emptyMap()
    ) : Mfa()

    @Serializable
    data class SmsDisabled(
        val userId: String,
        val metadata: Map<String, String> = emptyMap()
    ) : Mfa()

    @Serializable
    data class BackupCodeUsed(
        val userId: String,
        val codeUsed: String,
        val remainingCodes: Int,
        val metadata: Map<String, String> = emptyMap()
    ) : Mfa()

    @Serializable
    data class VerificationSuccess(
        val userId: String,
        val method: String,
        val metadata: Map<String, String> = emptyMap()
    ) : Mfa()

    @Serializable
    data class VerificationFailed(
        val userId: String,
        val method: String,
        val reason: String,
        val metadata: Map<String, String> = emptyMap()
    ) : Mfa()
}

/**
 * MFA validation result.
 */
@Serializable
sealed class Validation {
    @Serializable
    data class Success(
        val userId: String,
        val method: String,
        val timestamp: Instant
    ) : Validation()

    @Serializable
    data class Failure(
        val userId: String,
        val method: String,
        val reason: String,
        val timestamp: Instant
    ) : Validation()

    @Serializable
    data class Pending(
        val userId: String,
        val method: String,
        val expiresAt: Instant
    ) : Validation()
}

/**
 * Rate limiting result.
 */
@Serializable
sealed class RateLimitResult {
    @Serializable
    data class Allowed(val attemptsRemaining: Int) : RateLimitResult()

    @Serializable
    data class RateLimitExceeded(
        val retryAfterSeconds: Long,
        val attemptsRemaining: Int = 0
    ) : RateLimitResult()
}
