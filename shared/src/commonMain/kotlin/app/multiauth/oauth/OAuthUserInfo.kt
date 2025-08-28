package app.multiauth.oauth

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * User information returned from OAuth providers.
 * Contains standard user profile fields that are commonly available.
 */
@Serializable
data class OAuthUserInfo(
    val id: String,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val displayName: String? = null,
    val picture: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val provider: String,
    val providerId: String,
    val rawData: Map<String, String> = emptyMap(),
    val timestamp: @kotlinx.serialization.Contextual Instant = kotlinx.datetime.Clock.System.now()
) {
    
    /**
     * Gets the best available display name.
     */
    val bestDisplayName: String
        get() = displayName ?: name ?: givenName?.let { given ->
            familyName?.let { family -> "$given $family" } ?: given
        } ?: email ?: id
    
    /**
     * Gets the best available full name.
     */
    val bestFullName: String
        get() = name ?: givenName?.let { given ->
            familyName?.let { family -> "$given $family" } ?: given
        } ?: displayName ?: id
    
    /**
     * Checks if the user has a verified email.
     */
    val hasVerifiedEmail: Boolean
        get() = emailVerified == true
    
    /**
     * Gets a specific field from raw data.
     */
    fun getRawField(key: String): String? = rawData[key]
    
    /**
     * Gets all raw data fields.
     */
    fun getAllRawFields(): Map<String, String> = rawData.toMap()
    
    companion object {
        /**
         * Creates OAuthUserInfo from a map of user data.
         * Useful for parsing provider-specific responses.
         */
        fun fromMap(
            data: Map<String, Any?>,
            provider: String,
            providerId: String
        ): OAuthUserInfo {
            return OAuthUserInfo(
                id = data["id"]?.toString() ?: data["sub"]?.toString() ?: "",
                email = data["email"]?.toString(),
                emailVerified = data["email_verified"]?.toString()?.toBooleanOrNull(),
                name = data["name"]?.toString(),
                givenName = data["given_name"]?.toString(),
                familyName = data["family_name"]?.toString(),
                displayName = data["display_name"]?.toString() ?: data["username"]?.toString(),
                picture = data["picture"]?.toString() ?: data["avatar_url"]?.toString(),
                locale = data["locale"]?.toString(),
                timezone = data["timezone"]?.toString(),
                provider = provider,
                providerId = providerId,
                rawData = data.mapValues { it.value?.toString() ?: "" }
            )
        }
        
        /**
         * Creates OAuthUserInfo for a specific provider with common field mappings.
         */
        fun forProvider(
            data: Map<String, Any?>,
            provider: String
        ): OAuthUserInfo {
            val providerId = when (provider.lowercase()) {
                "google" -> data["sub"]?.toString() ?: data["id"]?.toString() ?: ""
                "github" -> data["id"]?.toString() ?: ""
                "discord" -> data["id"]?.toString() ?: ""
                "microsoft" -> data["id"]?.toString() ?: data["sub"]?.toString() ?: ""
                "linkedin" -> data["id"]?.toString() ?: ""
                "twitter" -> data["id"]?.toString() ?: data["username"]?.toString() ?: ""
                else -> data["id"]?.toString() ?: ""
            }
            
            return fromMap(data, provider, providerId)
        }
    }
}

/**
 * Extended user information with additional OAuth-specific fields.
 */
@Serializable
data class ExtendedOAuthUserInfo(
    val basic: OAuthUserInfo,
    val permissions: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val organizations: List<OAuthOrganization> = emptyList(),
    val socialConnections: Map<String, String> = emptyMap(),
    val preferences: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Organization information from OAuth providers.
 */
@Serializable
data class OAuthOrganization(
    val id: String,
    val name: String,
    val role: String? = null,
    val permissions: List<String> = emptyList()
)
