@file:OptIn(ExperimentalTime::class)

package app.multiauth.models

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlin.time.ExperimentalTime

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    @Contextual
    val expiresAt: Instant
)
