package app.multiauth.models

import kotlinx.datetime.Instant
import app.multiauth.oauth.OAuthProvider
import kotlinx.serialization.Serializable

@Serializable
sealed class AuthMethod {
    @Serializable
    data class Email(
        val email: String,
        val verified: Boolean,
        val verifiedAt: Instant? = null
    ) : AuthMethod()

    @Serializable
    data class Phone(
        val phoneNumber: String,
        val verified: Boolean,
        val verifiedAt: Instant? = null
    ) : AuthMethod()

    @Serializable
    data class OAuth(
        val provider: OAuthProvider,
        val providerId: String,
        val verified: Boolean,
        val verifiedAt: Instant
    ) : AuthMethod()
}
