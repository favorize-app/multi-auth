# Real-World Examples & Usage Guide

This guide provides comprehensive examples of how to use the Multi-Auth system in real-world applications, covering various authentication scenarios and integration patterns.

## Table of Contents

1. [Basic Authentication Flow](#basic-authentication-flow)
2. [OAuth Integration Examples](#oauth-integration-examples)
3. [Anonymous User Management](#anonymous-user-management)
4. [Multi-Factor Authentication](#multi-factor-authentication)
5. [Enterprise Integration](#enterprise-integration)
6. [Mobile App Integration](#mobile-app-integration)
7. [Web App Integration](#web-app-integration)
8. [API Integration](#api-integration)
9. [Security Best Practices](#security-best-practices)
10. [Troubleshooting Common Issues](#troubleshooting-common-issues)

## Basic Authentication Flow

### 1. User Registration Flow

```kotlin
// Initialize the authentication engine
val authEngine = AuthEngine.getInstance()
val eventBus = EventBus.getInstance()

// Subscribe to authentication events
eventBus.subscribe<AuthEvent.Authentication> { event, metadata ->
    when (event) {
        is AuthEvent.Authentication.SignUpCompleted -> {
            println("User registered successfully: ${event.user.displayName}")
            // Navigate to email verification screen
            navigateToEmailVerification()
        }
        is AuthEvent.Authentication.SignUpFailed -> {
            println("Registration failed: ${event.error.message}")
            // Show error message to user
            showErrorMessage(event.error.message)
        }
        else -> { /* Handle other events */ }
    }
}

// Register a new user
suspend fun registerUser(email: String, password: String, displayName: String) {
    val result = authEngine.signUpWithEmail(email, password, displayName)
    
    result.onSuccess { user ->
        println("User registered: ${user.displayName}")
        // User will receive verification email automatically
    }.onFailure { error ->
        println("Registration failed: ${error.message}")
        // Handle error (show message, retry, etc.)
    }
}
```

### 2. User Login Flow

```kotlin
// Subscribe to login events
eventBus.subscribe<AuthEvent.Authentication> { event, metadata ->
    when (event) {
        is AuthEvent.Authentication.SignInCompleted -> {
            println("User logged in: ${event.user.displayName}")
            // Navigate to main app
            navigateToMainApp()
        }
        is AuthEvent.Authentication.SignInFailed -> {
            println("Login failed: ${event.error.message}")
            // Show error message
            showErrorMessage(event.error.message)
        }
        else -> { /* Handle other events */ }
    }
}

// Login with email and password
suspend fun loginUser(email: String, password: String) {
    val result = authEngine.signInWithEmail(email, password)
    
    result.onSuccess { user ->
        println("User logged in: ${user.displayName}")
        // Check if MFA is required
        if (user.authMethods.any { it is AuthMethod.MFA }) {
            navigateToMfaScreen()
        } else {
            navigateToMainApp()
        }
    }.onFailure { error ->
        println("Login failed: ${error.message}")
        // Handle error
    }
}
```

## OAuth Integration Examples

### 1. Google OAuth Integration

```kotlin
// Configure Google OAuth
val googleOAuthConfig = OAuthProviderConfig(
    id = "google",
    name = "Google",
    displayName = "Google",
    authUrl = "https://accounts.google.com/oauth/authorize",
    tokenUrl = "https://oauth2.googleapis.com/token",
    userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo",
    defaultScopes = listOf("openid", "profile", "email"),
    supportedScopes = listOf("openid", "profile", "email", "https://www.googleapis.com/auth/calendar"),
    color = Color(0xFF4285F4),
    icon = "google_icon",
    requiresPkce = true
)

// Sign in with Google
suspend fun signInWithGoogle() {
    val oauthManager = OAuthManager(authEngine)
    val result = oauthManager.signInWithOAuth(OAuthProvider.GOOGLE)
    
    result.onSuccess { user ->
        println("Google OAuth successful: ${user.displayName}")
        // User is now authenticated with Google
    }.onFailure { error ->
        println("Google OAuth failed: ${error.message}")
        // Handle error
    }
}
```

### 2. GitHub OAuth Integration

```kotlin
// Configure GitHub OAuth
val githubOAuthConfig = OAuthProviderConfig(
    id = "github",
    name = "GitHub",
    displayName = "GitHub",
    authUrl = "https://github.com/login/oauth/authorize",
    tokenUrl = "https://github.com/login/oauth/access_token",
    userInfoUrl = "https://api.github.com/user",
    defaultScopes = listOf("read:user", "user:email"),
    supportedScopes = listOf("read:user", "user:email", "repo", "workflow"),
    color = Color(0xFF333333),
    icon = "github_icon",
    requiresPkce = false
)

// Sign in with GitHub
suspend fun signInWithGitHub() {
    val oauthManager = OAuthManager(authEngine)
    val result = oauthManager.signInWithOAuth(OAuthProvider.GITHUB)
    
    result.onSuccess { user ->
        println("GitHub OAuth successful: ${user.displayName}")
        // User is now authenticated with GitHub
    }.onFailure { error ->
        println("GitHub OAuth failed: ${error.message}")
        // Handle error
    }
}
```

### 3. Multiple OAuth Providers

```kotlin
// Enhanced OAuth manager for multiple providers
val enhancedOAuthManager = EnhancedOAuthManager(authEngine)

// Link multiple OAuth accounts to existing user
suspend fun linkMultipleAccounts(user: User) {
    // Link Google account
    val googleResult = enhancedOAuthManager.linkAccount(
        user = user,
        provider = OAuthProvider.GOOGLE,
        oauthData = getGoogleOAuthData()
    )
    
    // Link GitHub account
    val githubResult = enhancedOAuthManager.linkAccount(
        user = user,
        provider = OAuthProvider.GITHUB,
        oauthData = getGitHubOAuthData()
    )
    
    // User can now sign in with either provider
    println("Multiple accounts linked successfully")
}

// Get all linked accounts for a user
fun getLinkedAccounts(user: User): List<LinkedAccount> {
    return enhancedOAuthManager.getLinkedAccounts(user)
}
```

## Anonymous User Management

### 1. Creating Anonymous Sessions

```kotlin
// Initialize anonymous authentication manager
val anonymousManager = AnonymousAuthManager(authEngine)

// Create anonymous user session
suspend fun createAnonymousSession() {
    val result = anonymousManager.createAnonymousSession(
        deviceId = getDeviceId(),
        metadata = mapOf(
            "appVersion" to "1.0.0",
            "platform" to "android",
            "referrer" to "app_store"
        )
    )
    
    result.onSuccess { user ->
        println("Anonymous session created: ${user.id}")
        // User can now use the app without registration
        navigateToMainApp()
    }.onFailure { error ->
        println("Failed to create anonymous session: ${error.message}")
        // Handle error
    }
}
```

### 2. Converting Anonymous Users

```kotlin
// Convert anonymous user to permanent account
suspend fun convertAnonymousUser(
    anonymousUser: User,
    email: String,
    password: String,
    displayName: String
) {
    val result = anonymousManager.convertToPermanentAccount(
        anonymousUser = anonymousUser,
        email = email,
        password = password,
        displayName = displayName
    )
    
    result.onSuccess { permanentUser ->
        println("User converted successfully: ${permanentUser.displayName}")
        // Transfer anonymous user data to permanent account
        transferUserData(anonymousUser.id, permanentUser.id)
        // Navigate to main app
        navigateToMainApp()
    }.onFailure { error ->
        println("Conversion failed: ${error.message}")
        // Handle error
    }
}
```

### 3. Anonymous User Analytics

```kotlin
// Get anonymous user statistics
fun getAnonymousStats() {
    val stats = anonymousManager.getAnonymousUserStats()
    
    println("Total anonymous users: ${stats.totalUsers}")
    println("Active sessions: ${stats.activeUsers}")
    println("Conversion rate: ${stats.conversionRate * 100}%")
    
    // Use stats for business intelligence
    if (stats.conversionRate < 0.1) {
        // Implement conversion optimization strategies
        optimizeConversionFunnel()
    }
}

// Get conversion analytics
fun getConversionAnalytics() {
    val analytics = anonymousManager.getConversionAnalytics()
    
    println("Sessions created: ${analytics.sessionsCreated}")
    println("Accounts converted: ${analytics.accountsConverted}")
    println("Sessions extended: ${analytics.sessionsExtended}")
}
```

## Multi-Factor Authentication

### 1. TOTP Setup

```kotlin
// Initialize MFA manager
val mfaManager = MfaManager(authEngine)

// Enable TOTP for user
suspend fun enableTotp(user: User) {
    val result = mfaManager.enableMfa(user, MfaMethod.TOTP)
    
    result.onSuccess {
        println("TOTP enabled successfully")
        // Generate QR code for authenticator app
        val qrCode = generateTotpQrCode(user)
        showQrCodeToUser(qrCode)
    }.onFailure { error ->
        println("Failed to enable TOTP: ${error.message}")
        // Handle error
    }
}

// Verify TOTP code
suspend fun verifyTotpCode(user: User, code: String) {
    val result = mfaManager.verifyMfaCode(user, MfaMethod.TOTP, code)
    
    result.onSuccess {
        println("TOTP verification successful")
        // Complete authentication flow
        completeAuthentication()
    }.onFailure { error ->
        println("TOTP verification failed: ${error.message}")
        // Handle error
    }
}
```

### 2. SMS Verification

```kotlin
// Initialize SMS verification service
val smsService = SmsVerificationService()

// Send SMS verification code
suspend fun sendSmsVerification(user: User, phoneNumber: String) {
    val result = smsService.initiateVerification(user, phoneNumber)
    
    result.onSuccess {
        println("SMS verification code sent")
        // Show verification code input screen
        showSmsVerificationScreen()
    }.onFailure { error ->
        println("Failed to send SMS: ${error.message}")
        // Handle error
    }
}

// Verify SMS code
suspend fun verifySmsCode(user: User, code: String) {
    val result = smsService.verifyCode(user, code)
    
    result.onSuccess {
        println("SMS verification successful")
        // Complete authentication flow
        completeAuthentication()
    }.onFailure { error ->
        println("SMS verification failed: ${error.message}")
        // Handle error
    }
}
```

### 3. Backup Codes

```kotlin
// Generate backup codes
suspend fun generateBackupCodes(user: User) {
    val result = mfaManager.generateBackupCodes(user)
    
    result.onSuccess { codes ->
        println("Backup codes generated: ${codes.size}")
        // Show codes to user (only once)
        showBackupCodesToUser(codes)
        // Store codes securely
        storeBackupCodesSecurely(codes)
    }.onFailure { error ->
        println("Failed to generate backup codes: ${error.message}")
        // Handle error
    }
}

// Verify backup code
suspend fun verifyBackupCode(user: User, code: String) {
    val result = mfaManager.verifyMfaCode(user, MfaMethod.BACKUP_CODES, code)
    
    result.onSuccess {
        println("Backup code verification successful")
        // Complete authentication flow
        completeAuthentication()
    }.onFailure { error ->
        println("Backup code verification failed: ${error.message}")
        // Handle error
    }
}
```

## Enterprise Integration

### 1. gRPC Backend Integration

```kotlin
// Initialize gRPC client
val grpcClient = BaseGrpcClient()

// Connect to backend
suspend fun connectToBackend() {
    val result = grpcClient.connect(
        host = "api.yourapp.com",
        port = 443,
        useTls = true
    )
    
    result.onSuccess {
        println("Connected to backend successfully")
        // Set authentication credentials
        grpcClient.setCredentials(accessToken, refreshToken)
    }.onFailure { error ->
        println("Failed to connect to backend: ${error.message}")
        // Handle connection error
    }
}

// Use authentication service
suspend fun authenticateWithBackend(email: String, password: String) {
    val authService = grpcClient.getAuthenticationService()
    val result = authService.signInWithEmail(
        AuthenticationService.SignInWithEmailRequest(email, password)
    )
    
    result.onSuccess { response ->
        println("Backend authentication successful")
        // Handle successful authentication
        handleSuccessfulAuth(response)
    }.onFailure { error ->
        println("Backend authentication failed: ${error.message}")
        // Handle error
    }
}
```

### 2. Security Audit Logging

```kotlin
// Initialize security audit logger
val auditLogger = SecurityAuditLogger()

// Log authentication events
fun logAuthenticationEvent(user: User, method: String, success: Boolean) {
    if (success) {
        auditLogger.logSuccessfulAuthentication(
            user = user,
            method = method,
            metadata = mapOf(
                "ipAddress" to getClientIpAddress(),
                "userAgent" to getUserAgent(),
                "sessionId" to getSessionId()
            )
        )
    } else {
        auditLogger.logFailedAuthentication(
            userEmail = user.email ?: "unknown",
            reason = "Invalid credentials",
            metadata = mapOf(
                "ipAddress" to getClientIpAddress(),
                "userAgent" to getUserAgent()
            )
        )
    }
}

// Export audit data
fun exportAuditData() {
    val csvData = auditLogger.exportAuditData(
        format = ExportFormat.CSV,
        filters = AuditFilters(
            startTime = Instant.now().minus(30, ChronoUnit.DAYS),
            endTime = Instant.now()
        )
    )
    
    // Save to file or send to external system
    saveAuditDataToFile(csvData, "audit_log_${Instant.now()}.csv")
}
```

### 3. Rate Limiting

```kotlin
// Initialize rate limiter
val rateLimiter = RateLimiter()

// Configure rate limiting
rateLimiter.configure(
    RateLimitConfig(
        maxLoginAttemptsPerHour = 5,
        maxPasswordResetAttemptsPerHour = 3,
        maxMfaAttemptsPerHour = 10,
        maxApiRequestsPerMinute = 100,
        accountLockoutDurationMinutes = 30,
        maxFailedAttempts = 5
    )
)

// Check rate limits before operations
fun checkRateLimits(identifier: String, operation: String): Boolean {
    return when (operation) {
        "login" -> rateLimiter.checkLoginAttempt(identifier).isAllowed
        "password_reset" -> rateLimiter.checkPasswordResetAttempt(identifier).isAllowed
        "mfa" -> rateLimiter.checkMfaAttempt(identifier).isAllowed
        "api" -> rateLimiter.checkApiRequest(identifier).isAllowed
        else -> false
    }
}

// Record successful operations
fun recordSuccessfulOperation(identifier: String, operation: String) {
    when (operation) {
        "login" -> rateLimiter.recordSuccessfulLogin(identifier)
        "password_reset" -> rateLimiter.recordSuccessfulPasswordReset(identifier)
        "mfa" -> rateLimiter.recordSuccessfulMfaAttempt(identifier)
        "api" -> rateLimiter.recordSuccessfulApiRequest(identifier)
    }
}
```

## Mobile App Integration

### 1. Android Integration

```kotlin
// Android-specific secure storage
class AndroidSecureStorage : SecureStorage {
    private val context = getApplicationContext()
    private val keyStore = KeyStore.getInstance("AndroidKeyStore")
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            val encryptedValue = encryptValue(value)
            val sharedPrefs = context.getSharedPreferences("secure_storage", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString(key, encryptedValue).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        return try {
            val sharedPrefs = context.getSharedPreferences("secure_storage", Context.MODE_PRIVATE)
            val encryptedValue = sharedPrefs.getString(key, null)
            encryptedValue?.let { decryptValue(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    // ... other methods
}

// Biometric authentication
class AndroidBiometricProvider : PlatformBiometric {
    private val biometricManager = BiometricManager.from(context)
    
    override suspend fun authenticateWithBiometric(
        promptMessage: String,
        cancelMessage: String
    ): Result<User> {
        return try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle(promptMessage)
                .setNegativeButtonText(cancelMessage)
                .build()
            
            // Show biometric prompt
            showBiometricPrompt(promptInfo)
            Result.success(getCurrentUser())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 2. iOS Integration

```kotlin
// iOS-specific secure storage
class IosSecureStorage : SecureStorage {
    private val keychain = KeychainWrapper.standard
    
    override suspend fun store(key: String, value: String): Boolean {
        return try {
            keychain.set(value, forKey: key)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        return try {
            keychain.string(forKey: key)
        } catch (e: Exception) {
            null
        }
    }
    
    // ... other methods
}

// Face ID / Touch ID authentication
class IosBiometricProvider : PlatformBiometric {
    private val context = LAContext()
    
    override suspend fun authenticateWithBiometric(
        promptMessage: String,
        cancelMessage: String
    ): Result<User> {
        return try {
            val reason = promptMessage
            context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: reason
            ) { success, error in
                if (success {
                    // Authentication successful
                    Result.success(getCurrentUser())
                } else {
                    // Authentication failed
                    Result.failure(error ?? NSError())
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## Web App Integration

### 1. React/TypeScript Integration

```typescript
// React hook for authentication
import { useState, useEffect } from 'react';
import { AuthEngine, EventBus, AuthEvent } from '@multiauth/core';

export const useAuth = () => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const authEngine = AuthEngine.getInstance();
        const eventBus = EventBus.getInstance();

        // Subscribe to authentication events
        const unsubscribe = eventBus.subscribe<AuthEvent.Authentication>((event) => {
            switch (event.type) {
                case 'SignInCompleted':
                    setUser(event.user);
                    setLoading(false);
                    break;
                case 'SignOutCompleted':
                    setUser(null);
                    setLoading(false);
                    break;
                case 'SignInFailed':
                    setError(event.error.message);
                    setLoading(false);
                    break;
            }
        });

        // Check current authentication state
        const checkAuth = async () => {
            try {
                const currentUser = await authEngine.getCurrentUser();
                setUser(currentUser);
            } catch (error) {
                setError(error.message);
            } finally {
                setLoading(false);
            }
        };

        checkAuth();

        return unsubscribe;
    }, []);

    const signIn = async (email: string, password: string) => {
        try {
            setLoading(true);
            setError(null);
            await authEngine.signInWithEmail(email, password);
        } catch (error) {
            setError(error.message);
        }
    };

    const signOut = async () => {
        try {
            setLoading(true);
            await authEngine.signOut();
        } catch (error) {
            setError(error.message);
        }
    };

    return { user, loading, error, signIn, signOut };
};
```

### 2. Vue.js Integration

```typescript
// Vue composable for authentication
import { ref, onMounted, onUnmounted } from 'vue';
import { AuthEngine, EventBus, AuthEvent } from '@multiauth/core';

export const useAuth = () => {
    const user = ref(null);
    const loading = ref(true);
    const error = ref(null);

    let unsubscribe: (() => void) | null = null;

    onMounted(async () => {
        const authEngine = AuthEngine.getInstance();
        const eventBus = EventBus.getInstance();

        // Subscribe to authentication events
        unsubscribe = eventBus.subscribe<AuthEvent.Authentication>((event) => {
            switch (event.type) {
                case 'SignInCompleted':
                    user.value = event.user;
                    loading.value = false;
                    break;
                case 'SignOutCompleted':
                    user.value = null;
                    loading.value = false;
                    break;
                case 'SignInFailed':
                    error.value = event.error.message;
                    loading.value = false;
                    break;
            }
        });

        // Check current authentication state
        try {
            const currentUser = await authEngine.getCurrentUser();
            user.value = currentUser;
        } catch (err) {
            error.value = err.message;
        } finally {
            loading.value = false;
        }
    });

    onUnmounted(() => {
        if (unsubscribe) {
            unsubscribe();
        }
    });

    const signIn = async (email: string, password: string) => {
        try {
            loading.value = true;
            error.value = null;
            const authEngine = AuthEngine.getInstance();
            await authEngine.signInWithEmail(email, password);
        } catch (err) {
            error.value = err.message;
        }
    };

    const signOut = async () => {
        try {
            loading.value = true;
            const authEngine = AuthEngine.getInstance();
            await authEngine.signOut();
        } catch (err) {
            error.value = err.message;
        }
    };

    return {
        user: readonly(user),
        loading: readonly(loading),
        error: readonly(error),
        signIn,
        signOut
    };
};
```

## API Integration

### 1. REST API Integration

```kotlin
// API client with authentication
class ApiClient(
    private val baseUrl: String,
    private val authEngine: AuthEngine
) {
    private val httpClient = HttpClient()
    
    // Authenticated request
    suspend fun <T> authenticatedRequest(
        endpoint: String,
        method: HttpMethod = HttpMethod.GET,
        body: Any? = null
    ): T {
        val user = authEngine.getCurrentUser()
        val tokens = authEngine.getCurrentTokens()
        
        if (user == null || tokens == null) {
            throw UnauthorizedException("User not authenticated")
        }
        
        val request = HttpRequest(method, "$baseUrl$endpoint") {
            headers {
                append("Authorization", "Bearer ${tokens.accessToken}")
                append("Content-Type", "application/json")
            }
            
            if (body != null) {
                setBody(body)
            }
        }
        
        val response = httpClient.execute(request)
        
        if (response.status == HttpStatusCode.Unauthorized) {
            // Token expired, try to refresh
            val refreshResult = authEngine.refreshToken()
            if (refreshResult.isSuccess) {
                // Retry request with new token
                return retryRequest(endpoint, method, body)
            } else {
                throw UnauthorizedException("Token refresh failed")
            }
        }
        
        return response.body()
    }
    
    // Retry request with new token
    private suspend fun <T> retryRequest(
        endpoint: String,
        method: HttpMethod,
        body: Any?
    ): T {
        val tokens = authEngine.getCurrentTokens()
        val request = HttpRequest(method, "$baseUrl$endpoint") {
            headers {
                append("Authorization", "Bearer ${tokens?.accessToken}")
                append("Content-Type", "application/json")
            }
            
            if (body != null) {
                setBody(body)
            }
        }
        
        val response = httpClient.execute(request)
        return response.body()
    }
}
```

### 2. GraphQL Integration

```kotlin
// GraphQL client with authentication
class GraphQLClient(
    private val endpoint: String,
    private val authEngine: AuthEngine
) {
    private val httpClient = HttpClient()
    
    // Execute GraphQL query with authentication
    suspend fun <T> executeQuery(
        query: String,
        variables: Map<String, Any>? = null
    ): GraphQLResponse<T> {
        val user = authEngine.getCurrentUser()
        val tokens = authEngine.getCurrentTokens()
        
        if (user == null || tokens == null) {
            throw UnauthorizedException("User not authenticated")
        }
        
        val requestBody = GraphQLRequest(
            query = query,
            variables = variables
        )
        
        val request = HttpRequest(HttpMethod.POST, endpoint) {
            headers {
                append("Authorization", "Bearer ${tokens.accessToken}")
                append("Content-Type", "application/json")
            }
            setBody(requestBody)
        }
        
        val response = httpClient.execute(request)
        
        if (response.status == HttpStatusCode.Unauthorized) {
            // Token expired, try to refresh
            val refreshResult = authEngine.refreshToken()
            if (refreshResult.isSuccess) {
                // Retry query with new token
                return retryQuery(query, variables)
            } else {
                throw UnauthorizedException("Token refresh failed")
            }
        }
        
        return response.body()
    }
    
    // Retry query with new token
    private suspend fun <T> retryQuery(
        query: String,
        variables: Map<String, Any>?
    ): GraphQLResponse<T> {
        val tokens = authEngine.getCurrentTokens()
        val requestBody = GraphQLRequest(
            query = query,
            variables = variables
        )
        
        val request = HttpRequest(HttpMethod.POST, endpoint) {
            headers {
                append("Authorization", "Bearer ${tokens?.accessToken}")
                append("Content-Type", "application/json")
            }
            setBody(requestBody)
        }
        
        val response = httpClient.execute(request)
        return response.body()
    }
}

// GraphQL request/response models
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any>? = null
)

data class GraphQLResponse<T>(
    val data: T?,
    val errors: List<GraphQLError>? = null
)

data class GraphQLError(
    val message: String,
    val locations: List<GraphQLLocation>? = null,
    val path: List<String>? = null
)

data class GraphQLLocation(
    val line: Int,
    val column: Int
)
```

## Security Best Practices

### 1. Token Management

```kotlin
// Secure token storage and management
class SecureTokenManager(
    private val secureStorage: SecureStorage,
    private val authEngine: AuthEngine
) {
    companion object {
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val TOKEN_EXPIRY_KEY = "token_expiry"
    }
    
    // Store tokens securely
    suspend fun storeTokens(tokens: TokenPair) {
        secureStorage.store(ACCESS_TOKEN_KEY, tokens.accessToken)
        tokens.refreshToken?.let { refreshToken ->
            secureStorage.store(REFRESH_TOKEN_KEY, refreshToken)
        }
        
        val expiryTime = Instant.now().plus(tokens.expiresIn, ChronoUnit.SECONDS)
        secureStorage.store(TOKEN_EXPIRY_KEY, expiryTime.toString())
    }
    
    // Retrieve tokens securely
    suspend fun retrieveTokens(): TokenPair? {
        val accessToken = secureStorage.retrieve(ACCESS_TOKEN_KEY)
        val refreshToken = secureStorage.retrieve(REFRESH_TOKEN_KEY)
        val expiryString = secureStorage.retrieve(TOKEN_EXPIRY_KEY)
        
        if (accessToken == null || expiryString == null) {
            return null
        }
        
        val expiry = Instant.parse(expiryString)
        if (Instant.now().isAfter(expiry)) {
            // Token expired, try to refresh
            return refreshTokens(refreshToken)
        }
        
        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = ChronoUnit.SECONDS.between(Instant.now(), expiry)
        )
    }
    
    // Refresh tokens
    private suspend fun refreshTokens(refreshToken: String?): TokenPair? {
        if (refreshToken == null) {
            return null
        }
        
        return try {
            val result = authEngine.refreshToken(refreshToken)
            result.onSuccess { tokens ->
                storeTokens(tokens)
                tokens
            }.onFailure {
                null
            }.getOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    // Clear tokens
    suspend fun clearTokens() {
        secureStorage.remove(ACCESS_TOKEN_KEY)
        secureStorage.remove(REFRESH_TOKEN_KEY)
        secureStorage.remove(TOKEN_EXPIRY_KEY)
    }
}
```

### 2. Input Validation

```kotlin
// Comprehensive input validation
object InputValidator {
    
    // Email validation
    fun validateEmail(email: String): ValidationResult {
        if (email.isBlank()) {
            return ValidationResult.Error("Email cannot be empty")
        }
        
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
        if (!emailRegex.matches(email)) {
            return ValidationResult.Error("Invalid email format")
        }
        
        if (email.length > 254) {
            return ValidationResult.Error("Email too long")
        }
        
        return ValidationResult.Success
    }
    
    // Password validation
    fun validatePassword(password: String): ValidationResult {
        if (password.length < 8) {
            return ValidationResult.Error("Password must be at least 8 characters")
        }
        
        if (password.length > 128) {
            return ValidationResult.Error("Password too long")
        }
        
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }
        
        if (!hasUpperCase || !hasLowerCase || !hasDigit || !hasSpecialChar) {
            return ValidationResult.Error("Password must contain uppercase, lowercase, digit, and special character")
        }
        
        return ValidationResult.Success
    }
    
    // Phone number validation
    fun validatePhoneNumber(phoneNumber: String): ValidationResult {
        if (phoneNumber.isBlank()) {
            return ValidationResult.Error("Phone number cannot be empty")
        }
        
        // Remove all non-digit characters
        val digitsOnly = phoneNumber.replace(Regex("[^\\d]"), "")
        
        if (digitsOnly.length < 10 || digitsOnly.length > 15) {
            return ValidationResult.Error("Invalid phone number length")
        }
        
        return ValidationResult.Success
    }
    
    // Display name validation
    fun validateDisplayName(displayName: String): ValidationResult {
        if (displayName.isBlank()) {
            return ValidationResult.Error("Display name cannot be empty")
        }
        
        if (displayName.length < 2) {
            return ValidationResult.Error("Display name must be at least 2 characters")
        }
        
        if (displayName.length > 50) {
            return ValidationResult.Error("Display name too long")
        }
        
        // Check for potentially harmful characters
        val harmfulChars = Regex("[<>\"'&]")
        if (harmfulChars.containsMatchIn(displayName)) {
            return ValidationResult.Error("Display name contains invalid characters")
        }
        
        return ValidationResult.Success
    }
}

// Validation result
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
```

## Troubleshooting Common Issues

### 1. Authentication Failures

```kotlin
// Common authentication error handling
object AuthenticationErrorHandler {
    
    fun handleAuthenticationError(error: AuthError): String {
        return when (error) {
            is AuthError.InvalidCredentials -> "Invalid email or password"
            is AuthError.UserNotFound -> "User account not found"
            is AuthError.UserDisabled -> "Account has been disabled"
            is AuthError.TooManyAttempts -> "Too many failed attempts. Please try again later"
            is AuthError.NetworkError -> "Network error. Please check your connection"
            is AuthError.ServerError -> "Server error. Please try again later"
            is AuthError.InvalidToken -> "Session expired. Please sign in again"
            is AuthError.TokenExpired -> "Session expired. Please sign in again"
            is AuthError.InvalidRefreshToken -> "Session expired. Please sign in again"
            is AuthError.MfaRequired -> "Multi-factor authentication required"
            is AuthError.MfaInvalid -> "Invalid MFA code"
            is AuthError.EmailNotVerified -> "Please verify your email address"
            is AuthError.PhoneNotVerified -> "Please verify your phone number"
            else -> "Authentication failed. Please try again"
        }
    }
    
    fun shouldRetry(error: AuthError): Boolean {
        return when (error) {
            is AuthError.NetworkError -> true
            is AuthError.ServerError -> true
            is AuthError.TooManyAttempts -> false
            is AuthError.UserDisabled -> false
            is AuthError.InvalidCredentials -> false
            else -> false
        }
    }
    
    fun getRetryDelay(error: AuthError): Long {
        return when (error) {
            is AuthError.TooManyAttempts -> 300000L // 5 minutes
            is AuthError.NetworkError -> 5000L // 5 seconds
            is AuthError.ServerError -> 10000L // 10 seconds
            else -> 0L
        }
    }
}
```

### 2. OAuth Issues

```kotlin
// OAuth error handling and troubleshooting
object OAuthErrorHandler {
    
    fun handleOAuthError(error: AuthError): String {
        return when (error) {
            is AuthError.OAuthProviderUnavailable -> "OAuth provider is currently unavailable"
            is AuthError.OAuthUserDenied -> "Access was denied by the user"
            is AuthError.OAuthInvalidState -> "Invalid OAuth state. Please try again"
            is AuthError.OAuthCodeExchangeFailed -> "Failed to exchange authorization code"
            is AuthError.OAuthInvalidToken -> "Invalid OAuth token"
            is AuthError.OAuthTokenExpired -> "OAuth token has expired"
            is AuthError.OAuthScopeDenied -> "Required permissions were denied"
            is AuthError.OAuthAccountAlreadyLinked -> "Account is already linked to another user"
            is AuthError.OAuthProviderError -> "OAuth provider error: ${error.providerMessage}"
            else -> "OAuth authentication failed. Please try again"
        }
    }
    
    fun getOAuthTroubleshootingSteps(error: AuthError): List<String> {
        return when (error) {
            is AuthError.OAuthProviderUnavailable -> listOf(
                "Check if the OAuth provider is working",
                "Verify your OAuth app configuration",
                "Check your internet connection"
            )
            is AuthError.OAuthUserDenied -> listOf(
                "User must grant required permissions",
                "Try signing in again",
                "Check OAuth app permissions"
            )
            is AuthError.OAuthInvalidState -> listOf(
                "Clear browser cookies and cache",
                "Try signing in again",
                "Check for browser extensions that might interfere"
            )
            is AuthError.OAuthCodeExchangeFailed -> listOf(
                "Check OAuth app configuration",
                "Verify redirect URI settings",
                "Check server logs for details"
            )
            else -> listOf(
                "Try signing in again",
                "Check your internet connection",
                "Contact support if the issue persists"
            )
        }
    }
}
```

### 3. Performance Issues

```kotlin
// Performance monitoring and optimization
object PerformanceMonitor {
    
    private val performanceMetrics = mutableMapOf<String, PerformanceMetric>()
    
    // Track operation performance
    fun trackOperation(operation: String, startTime: Long) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        val metric = performanceMetrics.getOrPut(operation) { PerformanceMetric() }
        metric.addMeasurement(duration)
        
        // Log slow operations
        if (duration > 1000) { // 1 second threshold
            Logger.getLogger(this::class).warn("Slow operation detected: $operation took ${duration}ms")
        }
    }
    
    // Get performance statistics
    fun getPerformanceStats(): Map<String, PerformanceStats> {
        return performanceMetrics.mapValues { (_, metric) ->
            PerformanceStats(
                operation = metric.operation,
                averageTime = metric.getAverageTime(),
                minTime = metric.getMinTime(),
                maxTime = metric.getMaxTime(),
                totalOperations = metric.getTotalOperations(),
                slowOperations = metric.getSlowOperations()
            )
        }
    }
    
    // Performance optimization recommendations
    fun getOptimizationRecommendations(): List<OptimizationRecommendation> {
        val recommendations = mutableListOf<OptimizationRecommendation>()
        
        performanceMetrics.forEach { (operation, metric) ->
            val avgTime = metric.getAverageTime()
            
            when {
                avgTime > 5000 -> recommendations.add(
                    OptimizationRecommendation(
                        operation = operation,
                        severity = OptimizationSeverity.HIGH,
                        description = "Operation is very slow (${avgTime}ms average)",
                        suggestions = listOf(
                            "Optimize database queries",
                            "Implement caching",
                            "Consider async processing"
                        )
                    )
                )
                avgTime > 1000 -> recommendations.add(
                    OptimizationRecommendation(
                        operation = operation,
                        severity = OptimizationSeverity.MEDIUM,
                        description = "Operation is slow (${avgTime}ms average)",
                        suggestions = listOf(
                            "Review implementation",
                            "Add performance monitoring",
                            "Consider optimization"
                        )
                    )
                )
            }
        }
        
        return recommendations
    }
}

// Performance models
data class PerformanceMetric(
    val operation: String = "",
    private val measurements: MutableList<Long> = mutableListOf()
) {
    fun addMeasurement(duration: Long) {
        measurements.add(duration)
    }
    
    fun getAverageTime(): Long = measurements.average().toLong()
    fun getMinTime(): Long = measurements.minOrNull() ?: 0
    fun getMaxTime(): Long = measurements.maxOrNull() ?: 0
    fun getTotalOperations(): Int = measurements.size
    fun getSlowOperations(): Int = measurements.count { it > 1000 }
}

data class PerformanceStats(
    val operation: String,
    val averageTime: Long,
    val minTime: Long,
    val maxTime: Long,
    val totalOperations: Int,
    val slowOperations: Int
)

data class OptimizationRecommendation(
    val operation: String,
    val severity: OptimizationSeverity,
    val description: String,
    val suggestions: List<String>
)

enum class OptimizationSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}
```

## Conclusion

This comprehensive guide demonstrates how to integrate and use the Multi-Auth system in various real-world scenarios. The examples cover:

- **Basic authentication flows** for user registration and login
- **OAuth integration** with multiple providers
- **Anonymous user management** for conversion optimization
- **Multi-factor authentication** with TOTP, SMS, and backup codes
- **Enterprise integration** with gRPC backends and security features
- **Mobile and web app integration** with platform-specific implementations
- **API integration** for REST and GraphQL services
- **Security best practices** for token management and input validation
- **Troubleshooting** common authentication issues
- **Performance monitoring** and optimization

The Multi-Auth system is designed to be flexible, secure, and easy to integrate into any application. By following these examples and best practices, you can build robust authentication systems that scale with your application's needs.

For additional support and advanced usage patterns, refer to the [API Documentation](API_DOCUMENTATION.md) and [User Guide](USER_GUIDE.md).