@file:OptIn(ExperimentalTime::class)

package app.multiauth.models

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class User(
    val id: String,
    val email: String?,
    val displayName: String?,
    val emailVerified: Boolean = false,
    val phoneNumber: String? = null,
    val phoneVerified: Boolean = false,
    val isAnonymous: Boolean = false,
    val anonymousSessionId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastSignInAt: Instant? = null,
    val authMethods: List<AuthMethod> = emptyList()
)
