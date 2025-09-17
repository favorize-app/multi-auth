@file:OptIn(ExperimentalTime::class)

package app.multiauth.models

import kotlin.time.Instant
import app.multiauth.oauth.OAuthProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlin.time.ExperimentalTime

@Serializable
sealed class AuthMethod {
    @Serializable
    data class Email(
        val email: String,
        val verified: Boolean,
        @Contextual
        val verifiedAt: Instant? = null
    ) : AuthMethod()

    @Serializable
    data class Phone(
        val phoneNumber: String,
        val verified: Boolean,
        @Contextual
        val verifiedAt: Instant? = null
    ) : AuthMethod()

    @Serializable
    data class OAuth(
        val provider: OAuthProvider,
        val providerId: String,
        val verified: Boolean,
        @Contextual
        val verifiedAt: Instant
    ) : AuthMethod()
}
