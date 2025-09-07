package app.multiauth.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant
)
