# Multi-Auth API Documentation

## Overview

The Multi-Auth system provides a comprehensive authentication and authorization solution with support for multiple authentication methods, multi-factor authentication (MFA), OAuth integration, and advanced security features.

## Table of Contents

1. [Authentication API](#authentication-api)
2. [MFA API](#mfa-api)
3. [OAuth API](#oauth-api)
4. [Security API](#security-api)
5. [Storage API](#storage-api)
6. [gRPC API](#grpc-api)
7. [Error Handling](#error-handling)
8. [Rate Limiting](#rate-limiting)
9. [Security Considerations](#security-considerations)

## Authentication API

### Core Authentication

#### Sign In with Email/Password

```kotlin
suspend fun signInWithEmail(email: String, password: String): Result<User>
```

**Parameters:**
- `email`: User's email address
- `password`: User's password

**Returns:**
- `Result<User>`: Success with user object or failure with error

**Example:**
```kotlin
val result = authEngine.signInWithEmail("user@example.com", "password123")
result.onSuccess { user ->
    println("Signed in as: ${user.displayName}")
}.onFailure { error ->
    println("Sign in failed: ${error.message}")
}
```

#### Sign Up

```kotlin
suspend fun signUp(displayName: String, email: String, password: String): Result<User>
```

**Parameters:**
- `displayName`: User's display name
- `email`: User's email address
- `password`: User's password (minimum 8 characters)

**Returns:**
- `Result<User>`: Success with created user object or failure with error

#### Sign Out

```kotlin
suspend fun signOut(): Result<Unit>
```

**Returns:**
- `Result<Unit>`: Success or failure

#### Get Current User

```kotlin
fun getCurrentUser(): User?
```

**Returns:**
- `User?`: Current authenticated user or null if not signed in

### Password Management

#### Change Password

```kotlin
suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
```

**Parameters:**
- `currentPassword`: User's current password
- `newPassword`: New password (minimum 8 characters)

#### Reset Password

```kotlin
suspend fun resetPassword(email: String): Result<Unit>
```

**Parameters:**
- `email`: User's email address

**Returns:**
- `Result<Unit>`: Success if reset email sent, failure with error

## MFA API

### Multi-Factor Authentication

#### Enable MFA Method

```kotlin
suspend fun enableMfa(user: User, method: MfaMethod): Result<Unit>
```

**Parameters:**
- `user`: User to enable MFA for
- `method`: MFA method (TOTP, SMS, BACKUP_CODES)

**Returns:**
- `Result<Unit>`: Success or failure

**Example:**
```kotlin
val result = mfaManager.enableMfa(user, MfaMethod.TOTP)
result.onSuccess {
    println("TOTP MFA enabled successfully")
}.onFailure { error ->
    println("Failed to enable TOTP: ${error.message}")
}
```

#### Disable MFA Method

```kotlin
suspend fun disableMfa(user: User, method: MfaMethod): Result<Unit>
```

**Parameters:**
- `user`: User to disable MFA for
- `method`: MFA method to disable

#### Verify MFA Code

```kotlin
suspend fun verifyMfaCode(user: User, method: MfaMethod, code: String): Result<Unit>
```

**Parameters:**
- `user`: User to verify code for
- `method`: MFA method being verified
- `code`: Verification code

#### Generate Backup Codes

```kotlin
suspend fun generateBackupCodes(user: User): Result<List<String>>
```

**Returns:**
- `Result<List<String>>`: Success with list of backup codes or failure

### TOTP (Time-based One-Time Password)

#### Generate TOTP Secret

```kotlin
fun generateSecret(algorithm: String = "HmacSHA1"): String
```

**Parameters:**
- `algorithm`: HMAC algorithm (HmacSHA1, HmacSHA256, HmacSHA512)

**Returns:**
- `String`: Base32 encoded secret key

#### Generate TOTP Code

```kotlin
fun generateTotp(secret: String, algorithm: String = "HmacSHA1"): String
```

**Parameters:**
- `secret`: Base32 encoded secret key
- `algorithm`: HMAC algorithm

**Returns:**
- `String`: 6-digit TOTP code

#### Validate TOTP Code

```kotlin
fun validateTotp(secret: String, code: String, algorithm: String = "HmacSHA1"): Boolean
```

**Parameters:**
- `secret`: Base32 encoded secret key
- `code`: TOTP code to validate
- `algorithm`: HMAC algorithm

**Returns:**
- `Boolean`: true if valid, false otherwise

### SMS Verification

#### Initiate SMS Verification

```kotlin
suspend fun initiateVerification(user: User, phoneNumber: String): Result<Unit>
```

**Parameters:**
- `user`: User to send SMS to
- `phoneNumber`: Phone number to send SMS to

#### Verify SMS Code

```kotlin
suspend fun verifyCode(user: User, code: String): Result<Unit>
```

**Parameters:**
- `user`: User to verify code for
- `code`: SMS verification code

#### Resend SMS Code

```kotlin
suspend fun resendCode(user: User): Result<Unit>
```

## OAuth API

### OAuth Authentication

#### Sign In with OAuth

```kotlin
suspend fun signInWithOAuth(provider: OAuthProvider, redirectUri: String? = null): Result<User>
```

**Parameters:**
- `provider`: OAuth provider (Google, Apple, Facebook, etc.)
- `redirectUri`: Optional redirect URI for web flows

**Returns:**
- `Result<User>`: Success with user object or failure

### Enhanced OAuth Features

#### Link OAuth Account

```kotlin
suspend fun linkAccount(user: User, provider: OAuthProvider, oauthData: OAuthData): Result<Unit>
```

**Parameters:**
- `user`: User to link account to
- `provider`: OAuth provider
- `oauthData`: OAuth data from provider

#### Unlink OAuth Account

```kotlin
suspend fun unlinkAccount(user: User, provider: OAuthProvider): Result<Unit>
```

**Parameters:**
- `user`: User to unlink account from
- `provider`: OAuth provider to unlink

#### Get Linked Accounts

```kotlin
fun getLinkedAccounts(user: User): List<LinkedAccount>
```

**Returns:**
- `List<LinkedAccount>`: List of linked OAuth accounts

#### Refresh OAuth Tokens

```kotlin
suspend fun refreshTokens(user: User, provider: OAuthProvider): Result<Unit>
```

**Parameters:**
- `user`: User to refresh tokens for
- `provider`: OAuth provider

## Security API

### Security Audit Logging

#### Log Security Event

```kotlin
fun logSecurityEvent(event: SecurityEvent, user: User? = null, metadata: Map<String, Any> = emptyMap())
```

**Parameters:**
- `event`: Security event type
- `user`: User associated with event (optional)
- `metadata`: Additional event metadata

**Example:**
```kotlin
auditLogger.logSecurityEvent(
    SecurityEvent.AUTHENTICATION_SUCCESS,
    user,
    mapOf(
        "ipAddress" to "192.168.1.100",
        "userAgent" to "Mozilla/5.0...",
        "sessionId" to "session123"
    )
)
```

#### Log Failed Authentication

```kotlin
fun logFailedAuthentication(userEmail: String, reason: String, metadata: Map<String, Any> = emptyMap())
```

**Parameters:**
- `userEmail`: Email used in failed attempt
- `reason`: Reason for failure
- `metadata`: Additional metadata

#### Get Audit Entries

```kotlin
fun getUserAuditEntries(userId: String, limit: Int = 100): List<SecurityAuditEntry>
fun getAuditEntriesInRange(startTime: Instant, endTime: Instant, limit: Int = 1000): List<SecurityAuditEntry>
fun getAuditEntriesBySeverity(severity: SecuritySeverity, limit: Int = 1000): List<SecurityAuditEntry>
```

#### Export Audit Data

```kotlin
fun exportAuditData(format: ExportFormat, filters: AuditFilters? = null): String
```

**Parameters:**
- `format`: Export format (CSV, JSON, XML)
- `filters`: Optional filters to apply

### Rate Limiting

#### Check Login Attempt

```kotlin
fun checkLoginAttempt(identifier: String): RateLimitResult
```

**Parameters:**
- `identifier`: Email, username, or IP address

**Returns:**
- `RateLimitResult`: Allowed, RateLimited, or Blocked

**Example:**
```kotlin
val result = rateLimiter.checkLoginAttempt("user@example.com")
when (result) {
    is RateLimitResult.Allowed -> {
        // Proceed with login
        println("Attempts remaining: ${result.attemptsRemaining}")
    }
    is RateLimitResult.RateLimited -> {
        // Wait before retrying
        println("Rate limited: ${result.reason}")
        println("Retry after: ${result.retryAfter} seconds")
    }
    is RateLimitResult.Blocked -> {
        // Account is locked or blocked
        println("Blocked: ${result.reason}")
    }
}
```

#### Record Authentication Attempts

```kotlin
fun recordSuccessfulLogin(identifier: String)
fun recordFailedLogin(identifier: String, reason: String)
fun recordPasswordResetAttempt(identifier: String)
fun recordMfaAttempt(identifier: String)
fun recordApiRequest(identifier: String)
```

#### Account Lockout Management

```kotlin
fun isAccountLocked(identifier: String): Boolean
fun getRemainingLockoutTime(identifier: String): Long
fun unlockAccount(identifier: String)
```

#### Get Rate Limit Statistics

```kotlin
fun getRateLimitStats(identifier: String): RateLimitStats
```

**Returns:**
- `RateLimitStats`: Comprehensive statistics for the identifier

#### Configure Rate Limiting

```kotlin
fun configure(newConfig: RateLimitConfig)
```

**Example:**
```kotlin
val config = RateLimitConfig(
    maxLoginAttemptsPerHour = 3,
    maxPasswordResetAttemptsPerHour = 2,
    maxMfaAttemptsPerHour = 5,
    maxApiRequestsPerMinute = 50,
    maxFailedAttempts = 3,
    failedAttemptWindowMinutes = 10,
    accountLockoutDurationMinutes = 15
)
rateLimiter.configure(config)
```

## Storage API

### Secure Storage

#### Store Data

```kotlin
suspend fun store(key: String, value: String): Boolean
```

**Parameters:**
- `key`: Storage key
- `value`: Value to store

**Returns:**
- `Boolean`: true if successful, false otherwise

#### Retrieve Data

```kotlin
suspend fun retrieve(key: String): String?
```

**Parameters:**
- `key`: Storage key

**Returns:**
- `String?`: Stored value or null if not found

#### Remove Data

```kotlin
suspend fun remove(key: String): Boolean
```

**Parameters:**
- `key`: Storage key to remove

#### Check Data Exists

```kotlin
suspend fun contains(key: String): Boolean
```

**Parameters:**
- `key`: Storage key to check

#### Clear All Data

```kotlin
suspend fun clear(): Boolean
```

#### Get All Keys

```kotlin
fun getAllKeys(): Flow<Set<String>>
```

**Returns:**
- `Flow<Set<String>>`: Flow of all storage keys

#### Get Item Count

```kotlin
suspend fun getItemCount(): Int
```

**Returns:**
- `Int`: Number of stored items

### Storage Factory

#### Get Platform Storage

```kotlin
fun getSecureStorage(): SecureStorage
```

**Returns:**
- `SecureStorage`: Platform-specific secure storage implementation

## gRPC API

### Client Interface

#### Connect to Server

```kotlin
suspend fun connect(host: String, port: Int, useTls: Boolean = true): Result<Unit>
```

**Parameters:**
- `host`: Server hostname
- `port`: Server port
- `useTls`: Whether to use TLS encryption

#### Disconnect

```kotlin
suspend fun disconnect(): Result<Unit>
```

#### Check Connection Status

```kotlin
fun isConnected(): Boolean
fun getConnectionStatus(): Flow<ConnectionStatus>
```

#### Set Credentials

```kotlin
suspend fun setCredentials(accessToken: String, refreshToken: String? = null): Result<Unit>
```

**Parameters:**
- `accessToken`: Access token for authentication
- `refreshToken`: Optional refresh token

### Service Clients

#### Authentication Service

```kotlin
fun getAuthenticationService(): AuthenticationServiceClient
```

**Available Methods:**
- `signInWithEmail(request: SignInWithEmailRequest): AuthenticationResponse`
- `signUp(request: SignUpRequest): AuthenticationResponse`
- `refreshToken(request: RefreshTokenRequest): AuthenticationResponse`
- `signOut(request: SignOutRequest): SignOutResponse`

#### User Management Service

```kotlin
fun getUserManagementService(): UserManagementServiceClient
```

**Available Methods:**
- `getUserProfile(request: GetUserProfileRequest): UserProfileResponse`
- `updateUserProfile(request: UpdateUserProfileRequest): UserProfileResponse`
- `deleteUser(request: DeleteUserRequest): DeleteUserResponse`

#### Token Validation Service

```kotlin
fun getTokenValidationService(): TokenValidationServiceClient
```

**Available Methods:**
- `validateToken(request: ValidateTokenRequest): TokenValidationResponse`
- `revokeToken(request: RevokeTokenRequest): RevokeTokenResponse`

## Error Handling

### Error Types

The system uses `Result<T>` for error handling, which provides:

- **Success**: `Result.success(value)`
- **Failure**: `Result.failure(exception)`

### Common Exceptions

#### AuthenticationException
Thrown when authentication fails.

```kotlin
class AuthenticationException(message: String) : Exception(message)
```

#### OAuthException
Thrown when OAuth operations fail.

```kotlin
class OAuthException(message: String) : Exception(message)
```

#### SmsVerificationException
Thrown when SMS verification fails.

```kotlin
class SmsVerificationException(message: String) : Exception(message)
```

#### AuditExportException
Thrown when audit data export fails.

```kotlin
class AuditExportException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

### Error Handling Example

```kotlin
val result = authEngine.signInWithEmail("user@example.com", "password123")
result.onSuccess { user ->
    // Handle successful authentication
    println("Welcome, ${user.displayName}!")
}.onFailure { error ->
    when (error) {
        is AuthenticationException -> {
            println("Authentication failed: ${error.message}")
        }
        is IllegalArgumentException -> {
            println("Invalid input: ${error.message}")
        }
        else -> {
            println("Unexpected error: ${error.message}")
        }
    }
}
```

## Rate Limiting

### Rate Limit Results

#### Allowed
```kotlin
data class Allowed(val attemptsRemaining: Int)
```

#### Rate Limited
```kotlin
data class RateLimited(
    val reason: String,
    val retryAfter: Long,
    val attemptsRemaining: Int
)
```

#### Blocked
```kotlin
data class Blocked(
    val reason: String,
    val retryAfter: Long?
)
```

### Rate Limiting Configuration

```kotlin
data class RateLimitConfig(
    val maxLoginAttemptsPerHour: Int = 5,
    val maxPasswordResetAttemptsPerHour: Int = 3,
    val maxMfaAttemptsPerHour: Int = 10,
    val maxApiRequestsPerMinute: Int = 100,
    val maxFailedAttempts: Int = 5,
    val failedAttemptWindowMinutes: Long = 15,
    val accountLockoutDurationMinutes: Long = 30
)
```

## Security Considerations

### Best Practices

1. **Always use HTTPS** in production environments
2. **Implement proper session management** with secure session tokens
3. **Use strong password policies** (minimum 8 characters, complexity requirements)
4. **Enable MFA** for all user accounts
5. **Monitor and log** all authentication attempts
6. **Implement rate limiting** to prevent brute force attacks
7. **Regular security audits** of the system
8. **Keep dependencies updated** to patch security vulnerabilities

### Security Features

- **Multi-Factor Authentication (MFA)**: TOTP, SMS, and backup codes
- **Rate Limiting**: Prevents brute force attacks
- **Account Lockout**: Automatic lockout after multiple failed attempts
- **Security Audit Logging**: Comprehensive logging of all security events
- **Secure Storage**: Platform-specific secure storage for sensitive data
- **OAuth Integration**: Secure third-party authentication
- **Token Management**: Secure token generation, validation, and refresh

### Compliance

The system is designed to support various compliance requirements:

- **GDPR**: Data protection and user consent
- **SOC 2**: Security controls and monitoring
- **PCI DSS**: Secure handling of authentication data
- **HIPAA**: Healthcare data protection (when configured appropriately)

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests MfaManagerTest

# Run performance tests
./gradlew test --tests PerformanceTest
```

### Test Coverage

The system includes comprehensive test coverage:

- **Unit Tests**: Individual component testing
- **Integration Tests**: Component interaction testing
- **Performance Tests**: Performance benchmarking and regression detection
- **Security Tests**: Security feature validation

### Mock Implementations

For testing purposes, the system provides mock implementations:

- `MockAuthEngine`: Mock authentication engine
- `MockEventBus`: Mock event bus
- `MockSecureStorage`: Mock secure storage
- `MockGrpcClient`: Mock gRPC client

## Support

For technical support and questions:

1. **Documentation**: Check this API documentation
2. **Issues**: Report bugs and feature requests through the project repository
3. **Security**: Report security vulnerabilities through responsible disclosure

---

*This documentation is maintained as part of the Multi-Auth project. For the latest updates, please refer to the project repository.*