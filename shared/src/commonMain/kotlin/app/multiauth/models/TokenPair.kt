@file:OptIn(ExperimentalTime::class)

package app.multiauth.models

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant
)
