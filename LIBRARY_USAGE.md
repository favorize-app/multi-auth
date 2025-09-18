# Multi-Auth Library Usage Guide

This guide explains how to use the Multi-Auth library in your Kotlin Multiplatform project.

## Installation

### 1. Add Repository

Add the GitHub Packages repository to your `build.gradle.kts`:

```kotlin
repositories {
    google()
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/favorize-app/multi-auth")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

### 2. Add Dependency

Add the Multi-Auth library to your dependencies:

```kotlin
dependencies {
    implementation("app.favorize.multiauth:shared:1.0.0")
}
```

### 3. Configure GitHub Token (Required)

You need a GitHub Personal Access Token with `read:packages` permission to access the library.

#### Option A: Environment Variable (Recommended for CI/CD)
```bash
export GITHUB_TOKEN=your_github_token_here
```

#### Option B: Gradle Properties (For local development)
Create or update `gradle.properties`:
```properties
gpr.user=your-github-username
gpr.key=your-github-token
```

## Quick Start

### Basic Setup

```kotlin
import app.multiauth.core.AuthEngine
import app.multiauth.core.AuthStateManager
import app.multiauth.oauth.OAuthManager
import app.multiauth.models.AuthState

class AuthViewModel {
    private val authEngine = AuthEngine()
    private val authStateManager = AuthStateManager()
    private val oauthManager = OAuthManager()
    
    suspend fun initializeAuth() {
        // Initialize authentication system
        authEngine.initialize()
        
        // Check current auth state
        val currentState = authStateManager.getCurrentState()
        when (currentState) {
            is AuthState.Authenticated -> {
                // User is logged in
                handleAuthenticatedUser(currentState.user)
            }
            is AuthState.Unauthenticated -> {
                // User needs to log in
                showLoginScreen()
            }
        }
    }
}
```

### OAuth Authentication

```kotlin
import app.multiauth.oauth.clients.GoogleOAuthClient
import app.multiauth.oauth.OAuthConfig

class OAuthExample {
    private val googleClient = GoogleOAuthClient(
        OAuthConfig(
            clientId = "your-google-client-id",
            clientSecret = "your-google-client-secret",
            redirectUri = "your-app://oauth/callback"
        )
    )
    
    suspend fun signInWithGoogle() {
        try {
            val result = googleClient.authenticate()
            if (result.isSuccess) {
                val user = result.getOrNull()
                // Handle successful authentication
                println("User authenticated: ${user?.email}")
            } else {
                // Handle authentication error
                println("Authentication failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            println("OAuth error: ${e.message}")
        }
    }
}
```

### Multi-Factor Authentication (MFA)

```kotlin
import app.multiauth.mfa.MfaManager
import app.multiauth.mfa.TotpGenerator

class MfaExample {
    private val mfaManager = MfaManager()
    private val totpGenerator = TotpGenerator()
    
    suspend fun setupMfa(userId: String) {
        // Generate TOTP secret
        val secret = totpGenerator.generateSecret()
        
        // Save secret for user
        mfaManager.saveTotpSecret(userId, secret)
        
        // Generate QR code data for authenticator app
        val qrCodeData = totpGenerator.generateQrCodeData(
            secret = secret,
            userEmail = "user@example.com",
            issuer = "Your App"
        )
        
        // Display QR code to user
        showQrCode(qrCodeData)
    }
    
    suspend fun verifyTotpCode(userId: String, code: String): Boolean {
        return mfaManager.verifyTotpCode(userId, code)
    }
}
```

### Biometric Authentication

```kotlin
import app.multiauth.biometric.BiometricManager
import app.multiauth.biometric.BiometricFactory

class BiometricExample {
    private val biometricManager = BiometricFactory.createManager()
    
    suspend fun authenticateWithBiometrics(): Boolean {
        return try {
            biometricManager.authenticate()
        } catch (e: Exception) {
            println("Biometric authentication failed: ${e.message}")
            false
        }
    }
    
    suspend fun isBiometricAvailable(): Boolean {
        return biometricManager.isAvailable()
    }
}
```

### Session Management

```kotlin
import app.multiauth.core.SessionManager
import app.multiauth.models.Session

class SessionExample {
    private val sessionManager = SessionManager()
    
    suspend fun createSession(userId: String, token: String) {
        val session = Session(
            userId = userId,
            accessToken = token,
            refreshToken = "refresh_token_here",
            expiresAt = System.currentTimeMillis() + 3600000 // 1 hour
        )
        
        sessionManager.saveSession(session)
    }
    
    suspend fun getCurrentSession(): Session? {
        return sessionManager.getCurrentSession()
    }
    
    suspend fun refreshSession(): Boolean {
        return sessionManager.refreshSession()
    }
    
    suspend fun logout() {
        sessionManager.clearSession()
    }
}
```

## Platform-Specific Configuration

### Android

Add to your `androidMain` source set:

```kotlin
// androidMain/kotlin/YourApp.kt
import app.multiauth.platform.Platform

class AndroidApp {
    fun initialize() {
        Platform.initialize(
            context = androidContext,
            packageName = "com.yourapp.package"
        )
    }
}
```

### iOS

Add to your `iosMain` source set:

```kotlin
// iosMain/kotlin/YourApp.kt
import app.multiauth.platform.Platform

class IOSApp {
    fun initialize() {
        Platform.initialize(
            context = null, // iOS doesn't need context
            packageName = "com.yourapp.package"
        )
    }
}
```

### Desktop (JVM)

Add to your `desktopMain` source set:

```kotlin
// desktopMain/kotlin/YourApp.kt
import app.multiauth.platform.Platform

class DesktopApp {
    fun initialize() {
        Platform.initialize(
            context = null, // Desktop doesn't need context
            packageName = "com.yourapp.package"
        )
    }
}
```

### Web (JS)

Add to your `jsMain` source set:

```kotlin
// jsMain/kotlin/YourApp.kt
import app.multiauth.platform.Platform

class WebApp {
    fun initialize() {
        Platform.initialize(
            context = null, // Web doesn't need context
            packageName = "com.yourapp.package"
        )
    }
}
```

## Advanced Features

### Custom OAuth Providers

```kotlin
import app.multiauth.oauth.OAuthClient
import app.multiauth.oauth.OAuthConfig
import app.multiauth.oauth.OAuthResult

class CustomOAuthClient(
    private val config: OAuthConfig
) : OAuthClient {
    
    override suspend fun authenticate(): OAuthResult {
        // Implement your custom OAuth flow
        // This is a simplified example
        return OAuthResult.Success(
            user = OAuthUserInfo(
                id = "user_id",
                email = "user@example.com",
                name = "User Name",
                avatarUrl = "https://example.com/avatar.jpg"
            )
        )
    }
}
```

### Event Handling

```kotlin
import app.multiauth.events.EventBus
import app.multiauth.events.AuthEvent

class EventExample {
    private val eventBus = EventBus()
    
    fun setupEventHandlers() {
        eventBus.subscribe<AuthEvent.UserLoggedIn> { event ->
            println("User logged in: ${event.user.email}")
        }
        
        eventBus.subscribe<AuthEvent.UserLoggedOut> { event ->
            println("User logged out: ${event.userId}")
        }
        
        eventBus.subscribe<AuthEvent.SessionExpired> { event ->
            println("Session expired for user: ${event.userId}")
            // Redirect to login screen
        }
    }
}
```

### Security Features

```kotlin
import app.multiauth.security.JwtTokenManager
import app.multiauth.security.RateLimiter
import app.multiauth.security.ThreatDetection

class SecurityExample {
    private val jwtManager = JwtTokenManager()
    private val rateLimiter = RateLimiter()
    private val threatDetection = ThreatDetection()
    
    suspend fun validateToken(token: String): Boolean {
        return jwtManager.validateToken(token)
    }
    
    suspend fun isRateLimited(identifier: String): Boolean {
        return rateLimiter.isRateLimited(identifier)
    }
    
    suspend fun detectThreats(userId: String, action: String): Boolean {
        return threatDetection.analyzeBehavior(userId, action)
    }
}
```

## Error Handling

```kotlin
import app.multiauth.models.AuthError
import app.multiauth.models.AuthResult

class ErrorHandlingExample {
    suspend fun handleAuthResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> {
                // Handle success
                println("Authentication successful")
            }
            is AuthResult.Failure -> {
                when (result.error) {
                    is AuthError.NetworkError -> {
                        println("Network error: ${result.error.message}")
                    }
                    is AuthError.InvalidCredentials -> {
                        println("Invalid credentials")
                    }
                    is AuthError.TokenExpired -> {
                        println("Token expired, please refresh")
                    }
                    is AuthError.MfaRequired -> {
                        println("MFA required: ${result.error.mfaType}")
                    }
                    else -> {
                        println("Unknown error: ${result.error.message}")
                    }
                }
            }
        }
    }
}
```

## Troubleshooting

### Common Issues

1. **Authentication fails with "Invalid credentials"**
   - Check your OAuth client configuration
   - Verify redirect URIs match exactly
   - Ensure client secrets are correct

2. **GitHub Packages access denied**
   - Verify your GitHub token has `read:packages` permission
   - Check that the token is correctly set in environment variables or gradle.properties

3. **Biometric authentication not available**
   - Check if the device supports biometrics
   - Verify platform-specific permissions are granted

4. **MFA setup fails**
   - Ensure the TOTP secret is properly generated
   - Check that the authenticator app is correctly configured

### Getting Help

- Check the [API Documentation](API_DOCUMENTATION.md)
- Review the [Architecture Guide](ARCHITECTURE.md)
- Open an issue on [GitHub](https://github.com/favorize-app/multi-auth/issues)

## License

This library is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
