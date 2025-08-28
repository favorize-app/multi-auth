package app.multiauth.models

import kotlinx.serialization.Serializable

/**
 * Represents a user in the multi-auth system.
 * This model is designed to be compatible with Favorize's existing user structure.
 */
@Serializable
data class User(
    val id: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val emailVerified: Boolean = false,
    val phoneNumber: String? = null,
    val phoneVerified: Boolean = false,
    val isAnonymous: Boolean = false,
    val anonymousSessionId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val lastSignInAt: Instant? = null,
    val authMethods: List<AuthMethod> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Represents the authentication methods used by a user.
 */
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
        val providerUserId: String,
        val verified: Boolean = true,
        val verifiedAt: Instant? = null
    ) : AuthMethod()
    
    @Serializable
    data class Anonymous(
        val createdAt: Instant
    ) : AuthMethod()
}

/**
 * Supported OAuth providers.
 */
@Serializable
enum class OAuthProvider {
    GOOGLE,
    APPLE,
    FACEBOOK,
    TWITTER,
    GITHUB
}

/**
 * User creation request.
 */
@Serializable
data class CreateUserRequest(
    val email: String? = null,
    val phoneNumber: String? = null,
    val displayName: String? = null,
    val password: String? = null,
    val oauthProvider: OAuthProvider? = null,
    val oauthToken: String? = null
)

/**
 * User update request.
 */
@Serializable
data class UpdateUserRequest(
    val displayName: String? = null,
    val photoUrl: String? = null,
    val metadata: Map<String, String>? = null
)