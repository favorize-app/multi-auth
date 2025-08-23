# OpenID Connect (OIDC) Documentation

## Overview

The Multi-Auth system now includes comprehensive OpenID Connect (OIDC) support, extending the existing OAuth 2.0 implementation with standardized identity layer and JWT-based ID tokens. OIDC provides a more secure and standardized way to handle user authentication and identity verification.

## What is OpenID Connect?

OpenID Connect is an identity layer built on top of OAuth 2.0 that provides:

- **Standardized Identity**: JWT-based ID tokens with standardized claims
- **Enhanced Security**: Nonce validation, PKCE support, and proper token validation
- **User Information**: Standardized user profile information
- **Session Management**: Proper session handling and logout flows
- **Compliance**: Industry-standard authentication protocol

## Architecture

The OIDC system follows the same event-driven architecture as the rest of the Multi-Auth system:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   OIDC Client  │    │  OIDC Manager   │    │ OIDC Provider   │
│   (Your App)   │◄──►│   (Core Logic)  │◄──►│ (Google, etc.)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Event Bus     │    │   State Flow    │    │   HTTP Client   │
│  (Events)       │    │  (UI Updates)   │    │  (API Calls)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Core Components

### 1. OIDC Models (`OIDCModels.kt`)

The system defines comprehensive models for OIDC operations:

- **`OIDCIDToken`**: JWT ID token with standardized claims
- **`OIDCConfiguration`**: Provider configuration and capabilities
- **`OIDCAuthResult`**: Authentication result with tokens and user info
- **`OIDCUserInfo`**: Standardized user profile information
- **`OIDCProviderConfig`**: Client configuration for providers

### 2. OIDC Provider Interface (`OIDCProvider.kt`)

The core interface that all OIDC providers must implement:

```kotlin
interface OIDCProvider {
    suspend fun getConfiguration(): OIDCConfiguration
    suspend fun initiateAuth(config: OIDCProviderConfig, ...): AuthResult<OIDCAuthRequest>
    suspend fun completeAuth(config: OIDCProviderConfig, ...): AuthResult<OIDCAuthResult>
    suspend fun refreshToken(config: OIDCProviderConfig, ...): AuthResult<OIDCTokenResponse>
    suspend fun revokeToken(config: OIDCProviderConfig, ...): AuthResult<Unit>
    suspend fun getUserInfo(config: OIDCProviderConfig, ...): AuthResult<OIDCUserInfo>
    suspend fun validateIdToken(config: OIDCProviderConfig, ...): AuthResult<OIDCIDToken>
    suspend fun endSession(config: OIDCProviderConfig, ...): AuthResult<Unit>
}
```

### 3. OIDC Manager (`OIDCManager.kt`)

The main orchestrator for OIDC operations:

```kotlin
class OIDCManager {
    suspend fun signInWithOIDC(provider: OIDCProvider, config: OIDCProviderConfig, ...): Result<User>
    suspend fun completeOIDCAuth(provider: OIDCProvider, config: OIDCProviderConfig, ...): Result<User>
    suspend fun refreshOIDCToken(provider: OIDCProvider, config: OIDCProviderConfig, ...): Result<OIDCTokenResponse>
    suspend fun endOIDCSession(provider: OIDCProvider, config: OIDCProviderConfig, ...): Result<Unit>
    suspend fun getUserInfo(provider: OIDCProvider, config: OIDCProviderConfig, ...): Result<OIDCUserInfo>
}
```

### 4. OIDC Provider Factory (`OIDCProviderFactory.kt`)

Factory for creating OIDC provider instances:

```kotlin
object OIDCProviderFactory {
    fun createProvider(provider: OIDCProvider): OIDCProvider
    fun getSupportedProviders(): List<OIDCProviderInfo>
    fun isProviderSupported(provider: OIDCProvider): Boolean
    fun getImplementedProviders(): List<OIDCProvider>
}
```

## Supported Providers

### Currently Implemented

1. **Google OIDC** (`GoogleOIDCProvider`)
   - Full OIDC compliance
   - PKCE and nonce support
   - User info and token refresh
   - Token revocation

### Planned Implementation

2. **Microsoft Azure AD**
3. **Apple Sign In**
4. **Auth0**
5. **Okta**
6. **Keycloak**
7. **Custom OIDC**

## Quick Start

### 1. Basic Setup

```kotlin
// Create an OIDC manager
val oidcManager = OIDCManager()

// Create a Google OIDC provider
val googleProvider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)

// Configure the provider
val config = OIDCProviderConfig(
    provider = OIDCProvider.GOOGLE,
    clientId = "your_google_client_id",
    clientSecret = "your_google_client_secret",
    redirectUris = listOf("com.yourapp://oauth/callback"),
    scopes = listOf("openid", "profile", "email")
)
```

### 2. Sign In Flow

```kotlin
// Start OIDC sign-in
val result = oidcManager.signInWithOIDC(
    provider = googleProvider,
    config = config,
    redirectUri = "com.yourapp://oauth/callback"
)

result.onSuccess { user ->
    println("OIDC sign-in initiated for: ${user.displayName}")
    // Handle the authorization request (open browser, redirect user)
}.onFailure { error ->
    println("OIDC sign-in failed: ${error.message}")
}
```

### 3. Complete Authentication

```kotlin
// After user returns from OAuth flow with authorization code
val authResult = oidcManager.completeOIDCAuth(
    provider = googleProvider,
    config = config,
    authorizationCode = "authorization_code_from_callback",
    redirectUri = "com.yourapp://oauth/callback"
)

authResult.onSuccess { user ->
    println("OIDC authentication completed for: ${user.displayName}")
    // User is now authenticated
}.onFailure { error ->
    println("OIDC authentication failed: ${error.message}")
}
```

## Configuration

### Provider Configuration

Each OIDC provider requires specific configuration:

```kotlin
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
```

### Google OIDC Configuration

For Google OIDC, you need to:

1. **Create a Google Cloud Project**
2. **Enable OAuth 2.0 API**
3. **Create OAuth 2.0 Credentials**
4. **Configure OAuth Consent Screen**
5. **Add Redirect URIs**

```kotlin
val googleConfig = OIDCProviderConfig(
    provider = OIDCProvider.GOOGLE,
    clientId = "your_google_client_id.apps.googleusercontent.com",
    clientSecret = "your_google_client_secret",
    redirectUris = listOf(
        "com.yourapp://oauth/callback",
        "https://yourapp.com/oauth/callback"
    ),
    scopes = listOf("openid", "profile", "email", "address")
)
```

## Authentication Flow

### 1. Authorization Request

```kotlin
// Generate PKCE and nonce
val authRequest = oidcManager.signInWithOIDC(
    provider = googleProvider,
    config = config,
    redirectUri = "com.yourapp://oauth/callback"
)
```

### 2. User Authorization

The user is redirected to the OIDC provider's authorization page where they:
- Sign in with their credentials
- Grant permissions to your application
- Are redirected back with an authorization code

### 3. Token Exchange

```kotlin
// Exchange authorization code for tokens
val authResult = oidcManager.completeOIDCAuth(
    provider = googleProvider,
    config = config,
    authorizationCode = "code_from_callback",
    redirectUri = "com.yourapp://oauth/callback"
)
```

### 4. Token Validation

The system automatically:
- Validates the ID token signature
- Checks token expiration
- Verifies the nonce
- Validates the issuer and audience

### 5. User Creation

A user object is created with:
- Information from the ID token
- User profile from the user info endpoint
- OIDC-specific metadata

## Token Management

### Access Token

Used for API calls to the OIDC provider:

```kotlin
// Get user info using access token
val userInfo = oidcManager.getUserInfo(
    provider = googleProvider,
    config = config,
    accessToken = "access_token_from_auth_result"
)
```

### Refresh Token

Used to get new access tokens:

```kotlin
// Refresh access token
val newTokens = oidcManager.refreshOIDCToken(
    provider = googleProvider,
    config = config,
    refreshToken = "refresh_token_from_auth_result"
)
```

### ID Token

Contains user identity information and is validated for security:

```kotlin
// ID token is automatically validated during authentication
// Contains claims like: sub, iss, aud, exp, iat, email, name, picture
```

## Security Features

### PKCE (Proof Key for Code Exchange)

- Prevents authorization code interception attacks
- Automatically generated for each authentication flow
- Uses SHA-256 for code challenge generation

### Nonce Validation

- Prevents replay attacks
- Unique nonce generated for each authentication request
- Validated against the ID token

### Token Validation

- JWT signature verification
- Expiration time validation
- Issuer and audience validation
- Nonce validation

### Secure Storage

- Tokens stored securely using platform-specific storage
- Automatic token refresh
- Secure token revocation

## Event System

The OIDC system integrates with the existing event bus:

```kotlin
// Subscribe to OIDC events
eventBus.subscribe<AuthEvent.OIDC> { event, metadata ->
    when (event) {
        is AuthEvent.OIDC.OIDCAuthorizationRequested -> {
            // Handle authorization request
        }
        is AuthEvent.OIDC.OIDCSignInCompleted -> {
            // Handle successful sign-in
        }
        is AuthEvent.OIDC.OIDCSignInFailed -> {
            // Handle sign-in failure
        }
        // ... other events
    }
}
```

### Available Events

- `OIDCAuthorizationRequested`: Authorization flow started
- `OIDCSignInCompleted`: Authentication completed successfully
- `OIDCSignInFailed`: Authentication failed
- `OIDCTokenRefreshed`: Token refreshed successfully
- `OIDCTokenRefreshFailed`: Token refresh failed
- `OIDCSessionEnded`: Session ended successfully
- `OIDCSessionEndFailed`: Session end failed
- `OIDCUserInfoRetrieved`: User info retrieved successfully
- `OIDCUserInfoRetrievalFailed`: User info retrieval failed
- `OIDCConfigurationRetrieved`: Provider configuration retrieved
- `OIDCConfigurationFailed`: Configuration retrieval failed
- `OIDCIDTokenValidated`: ID token validated successfully
- `OIDCIDTokenValidationFailed`: ID token validation failed

## State Management

The OIDC manager provides state flows for UI updates:

```kotlin
// Observe OIDC state
oidcManager.oidcState.collect { state ->
    when (state) {
        is OIDCState.Idle -> {
            // No active operation
        }
        is OIDCState.Initiating -> {
            // Starting authentication
        }
        is OIDCState.AuthorizationPending -> {
            // Waiting for user authorization
        }
        is OIDCState.ExchangingCode -> {
            // Exchanging authorization code for tokens
        }
        is OIDCState.CreatingSession -> {
            // Creating user session
        }
        is OIDCState.Success -> {
            // Authentication successful
            val user = state.user
        }
        is OIDCState.Error -> {
            // Authentication failed
            val error = state.error
        }
    }
}
```

## Error Handling

The system provides comprehensive error handling:

```kotlin
// Handle authentication errors
result.onFailure { error ->
    when (error) {
        is AuthError.ValidationError -> {
            // Handle validation errors
        }
        is AuthError.AuthenticationError -> {
            // Handle authentication errors
        }
        is AuthError.NetworkError -> {
            // Handle network errors
        }
        is AuthError.UnknownError -> {
            // Handle unknown errors
        }
    }
}
```

## Platform Integration

### Android

```kotlin
// Android-specific redirect URI
val redirectUri = "com.yourapp://oauth/callback"

// Handle OAuth callback in your activity
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    
    val uri = intent?.data
    if (uri?.scheme == "com.yourapp" && uri.host == "oauth") {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            // Complete OIDC authentication
            lifecycleScope.launch {
                oidcManager.completeOIDCAuth(
                    provider = googleProvider,
                    config = config,
                    authorizationCode = code,
                    redirectUri = redirectUri
                )
            }
        }
    }
}
```

### iOS

```kotlin
// iOS-specific redirect URI
val redirectUri = "com.yourapp://oauth/callback"

// Handle OAuth callback in your app delegate
func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
    if url.scheme == "com.yourapp" && url.host == "oauth" {
        // Extract authorization code and complete authentication
        return true
    }
    return false
}
```

### Web

```kotlin
// Web-specific redirect URI
val redirectUri = "https://yourapp.com/oauth/callback"

// Handle OAuth callback in your web app
window.addEventListener('message', function(event) {
    if (event.data.type === 'oauth_callback') {
        const code = event.data.code;
        // Complete OIDC authentication
    }
});
```

## Testing

### Unit Tests

```kotlin
@Test
fun `test Google OIDC provider creation`() {
    val provider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)
    assertNotNull(provider)
    assertEquals("Google", provider.getProviderInfo().name)
}

@Test
fun `test OIDC configuration`() {
    val config = OIDCProviderConfig(
        provider = OIDCProvider.GOOGLE,
        clientId = "test_client_id",
        redirectUris = listOf("test://callback")
    )
    assertEquals("test_client_id", config.clientId)
}
```

### Integration Tests

```kotlin
@Test
fun `test complete OIDC flow`() = runTest {
    val oidcManager = OIDCManager()
    val provider = OIDCProviderFactory.createProvider(OIDCProvider.GOOGLE)
    
    // Test complete authentication flow
    // This would require mock OIDC provider responses
}
```

## Best Practices

### 1. Security

- Always use PKCE for mobile and web applications
- Validate nonces to prevent replay attacks
- Store tokens securely using platform-specific secure storage
- Implement proper token refresh logic
- Validate ID tokens thoroughly

### 2. User Experience

- Provide clear error messages for authentication failures
- Implement proper loading states during authentication
- Handle network errors gracefully
- Provide fallback authentication methods

### 3. Configuration

- Use environment-specific configuration
- Validate provider configuration at startup
- Implement proper error handling for misconfiguration
- Use secure storage for sensitive configuration

### 4. Monitoring

- Log authentication events for debugging
- Monitor authentication success/failure rates
- Track token refresh patterns
- Monitor for suspicious authentication patterns

## Troubleshooting

### Common Issues

1. **Invalid Redirect URI**
   - Ensure redirect URI matches exactly what's configured in the OIDC provider
   - Check for trailing slashes and protocol differences

2. **Client ID/Secret Mismatch**
   - Verify client ID and secret are correct
   - Check if credentials are for the right environment (dev/prod)

3. **Scope Issues**
   - Ensure "openid" scope is always included
   - Check if requested scopes are supported by the provider

4. **Token Validation Failures**
   - Verify ID token signature
   - Check token expiration
   - Validate nonce and audience

5. **Network Issues**
   - Check internet connectivity
   - Verify OIDC provider endpoints are accessible
   - Check for firewall or proxy issues

### Debug Mode

Enable debug logging to troubleshoot issues:

```kotlin
// Set logger level to debug
Logger.setLevel(LogLevel.DEBUG)
```

## Migration from OAuth 2.0

If you're currently using OAuth 2.0, migrating to OIDC provides:

1. **Enhanced Security**: ID token validation, nonce support
2. **Standardized Claims**: Consistent user information format
3. **Better Compliance**: Industry-standard authentication protocol
4. **Improved User Experience**: More reliable authentication flows

### Migration Steps

1. **Update Provider Configuration**
   - Add OIDC-specific endpoints
   - Include "openid" scope
   - Configure nonce validation

2. **Update Authentication Flow**
   - Use OIDC manager instead of OAuth manager
   - Handle ID token validation
   - Implement proper error handling

3. **Update User Model**
   - Use OIDC user info
   - Store OIDC-specific metadata
   - Handle new authentication methods

## Future Enhancements

### Planned Features

1. **Additional Providers**
   - Microsoft Azure AD
   - Apple Sign In
   - Auth0
   - Okta
   - Keycloak

2. **Advanced Security**
   - Hardware security module (HSM) support
   - Biometric authentication integration
   - Advanced threat detection

3. **Performance Improvements**
   - Token caching
   - Batch operations
   - Async processing

4. **Developer Experience**
   - Configuration validation
   - Auto-discovery
   - Testing utilities

## Support

For OIDC-related issues and questions:

1. **Check the documentation** for common solutions
2. **Review the examples** for implementation patterns
3. **Enable debug logging** for troubleshooting
4. **Create an issue** on GitHub for bugs or feature requests

## References

- [OpenID Connect Core 1.0 Specification](https://openid.net/specs/openid-connect-core-1_0.html)
- [OAuth 2.0 Specification](https://tools.ietf.org/html/rfc6749)
- [JWT Specification](https://tools.ietf.org/html/rfc7519)
- [PKCE Specification](https://tools.ietf.org/html/rfc7636)
- [Google OIDC Documentation](https://developers.google.com/identity/protocols/oauth2/openid-connect)
- [Microsoft OIDC Documentation](https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-protocols-oidc)