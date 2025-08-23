package app.multiauth.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.datetime.Instant

/**
 * OpenID Connect ID Token containing user identity information.
 * Based on OIDC Core 1.0 specification.
 */
@Serializable
data class OIDCIDToken(
    val iss: String, // Issuer
    val sub: String, // Subject (user ID)
    val aud: String, // Audience (client ID)
    val exp: Long, // Expiration time
    val iat: Long, // Issued at time
    val authTime: Long? = null, // Authentication time
    val nonce: String? = null, // Nonce for replay protection
    val acr: String? = null, // Authentication context class reference
    val amr: List<String>? = null, // Authentication methods references
    val azp: String? = null, // Authorized party
    val atHash: String? = null, // Access token hash
    val cHash: String? = null, // Code hash
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val picture: String? = null,
    val locale: String? = null,
    val updatedAt: Long? = null,
    val customClaims: Map<String, String> = emptyMap()
)

/**
 * OpenID Connect configuration for a provider.
 * Contains endpoints and supported features.
 */
@Serializable
data class OIDCConfiguration(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String,
    val jwksUri: String,
    val endSessionEndpoint: String? = null,
    val introspectionEndpoint: String? = null,
    val revocationEndpoint: String? = null,
    val registrationEndpoint: String? = null,
    val scopesSupported: List<String> = emptyList(),
    val responseTypesSupported: List<String> = emptyList(),
    val responseModesSupported: List<String> = emptyList(),
    val grantTypesSupported: List<String> = emptyList(),
    val tokenEndpointAuthMethodsSupported: List<String> = emptyList(),
    val tokenEndpointAuthSigningAlgValuesSupported: List<String> = emptyList(),
    val displayValuesSupported: List<String> = emptyList(),
    val claimTypesSupported: List<String> = emptyList(),
    val claimsSupported: List<String> = emptyList(),
    val serviceDocumentation: String? = null,
    val claimsLocalesSupported: List<String> = emptyList(),
    val uiLocalesSupported: List<String> = emptyList(),
    val claimsParameterSupported: Boolean = false,
    val requestParameterSupported: Boolean = false,
    val requestUriParameterSupported: Boolean = false,
    val requireRequestUriRegistration: Boolean = false,
    val opPolicyUri: String? = null,
    val opTosUri: String? = null
)

/**
 * OIDC authentication result containing tokens and user information.
 */
@Serializable
data class OIDCAuthResult(
    val accessToken: String,
    val idToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresIn: Long? = null,
    val scope: String? = null,
    val userInfo: OIDCUserInfo? = null,
    val decodedIdToken: OIDCIDToken? = null
)

/**
 * User information from OIDC provider.
 */
@Serializable
data class OIDCUserInfo(
    val sub: String,
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val nickname: String? = null,
    val preferredUsername: String? = null,
    val profile: String? = null,
    val picture: String? = null,
    val website: String? = null,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val gender: String? = null,
    val birthdate: String? = null,
    val zoneinfo: String? = null,
    val locale: String? = null,
    val phoneNumber: String? = null,
    val phoneNumberVerified: Boolean? = null,
    val address: OIDCAddress? = null,
    val updatedAt: Long? = null,
    val customClaims: Map<String, String> = emptyMap()
)

/**
 * Address information from OIDC user info.
 */
@Serializable
data class OIDCAddress(
    val formatted: String? = null,
    val streetAddress: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null
)

/**
 * OIDC authentication request parameters.
 */
@Serializable
data class OIDCAuthRequest(
    val clientId: String,
    val redirectUri: String,
    val responseType: String = "code",
    val scope: String = "openid",
    val state: String? = null,
    val nonce: String? = null,
    val display: String? = null,
    val prompt: String? = null,
    val maxAge: Long? = null,
    val uiLocales: List<String> = emptyList(),
    val claimsLocales: List<String> = emptyList(),
    val idTokenHint: String? = null,
    val loginHint: String? = null,
    val acrValues: List<String> = emptyList(),
    val responseMode: String? = null,
    val request: String? = null,
    val requestUri: String? = null,
    val registration: String? = null
)

/**
 * OIDC token request for exchanging authorization code.
 */
@Serializable
data class OIDCTokenRequest(
    val grantType: String = "authorization_code",
    val clientId: String,
    val clientSecret: String? = null,
    val code: String,
    val redirectUri: String,
    val codeVerifier: String? = null,
    val scope: String? = null
)

/**
 * OIDC token response from provider.
 */
@Serializable
data class OIDCTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long? = null,
    val refreshToken: String? = null,
    val scope: String? = null,
    val idToken: String? = null
)

/**
 * OIDC error response.
 */
@Serializable
data class OIDCError(
    val error: String,
    val errorDescription: String? = null,
    val errorUri: String? = null,
    val state: String? = null
)

/**
 * Supported OIDC providers.
 */
@Serializable
enum class OIDCProvider {
    GOOGLE,
    MICROSOFT,
    APPLE,
    AUTH0,
    OKTA,
    KEYCLOAK,
    CUSTOM
}

/**
 * OIDC provider configuration.
 */
@Serializable
data class OIDCProviderConfig(
    val provider: OIDCProvider,
    val clientId: String,
    val clientSecret: String? = null,
    val redirectUris: List<String>,
    val scopes: List<String> = listOf("openid", "profile", "email"),
    val issuer: String? = null,
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null,
    val userInfoEndpoint: String? = null,
    val jwksUri: String? = null,
    val endSessionEndpoint: String? = null,
    val customClaims: Map<String, String> = emptyMap()
)