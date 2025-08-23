# Multi-Auth User Guide

## Overview

Welcome to the Multi-Auth system! This guide will help you understand how to use the system, from basic authentication to advanced security features.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Basic Authentication](#basic-authentication)
3. [Multi-Factor Authentication (MFA)](#multi-factor-authentication-mfa)
4. [OAuth Integration](#oauth-integration)
5. [Security Features](#security-features)
6. [Advanced Configuration](#advanced-configuration)
7. [Troubleshooting](#troubleshooting)
8. [Best Practices](#best-practices)

## Getting Started

### Prerequisites

- Kotlin Multiplatform project
- Gradle build system
- Target platforms: Android, iOS, Web, Desktop

### Installation

1. **Add the dependency to your project:**

```kotlin
// build.gradle.kts
dependencies {
    implementation("app.multiauth:shared:1.0.0")
}
```

2. **Initialize the system:**

```kotlin
// Initialize core components
val authEngine = AuthEngine()
val eventBus = EventBus.getInstance()
val mfaManager = MfaManager(authEngine, eventBus)
val rateLimiter = RateLimiter()
val auditLogger = SecurityAuditLogger()
```

3. **Configure platform-specific storage:**

```kotlin
// Get platform-specific secure storage
val secureStorage = StorageFactory.getSecureStorage()
```

## Basic Authentication

### User Registration

```kotlin
// Create a new user account
val result = authEngine.signUp(
    displayName = "John Doe",
    email = "john@example.com",
    password = "SecurePassword123!"
)

result.onSuccess { user ->
    println("User created: ${user.displayName}")
    // User is now signed in
}.onFailure { error ->
    println("Registration failed: ${error.message}")
}
```

### User Sign In

```kotlin
// Sign in with email and password
val result = authEngine.signInWithEmail(
    email = "john@example.com",
    password = "SecurePassword123!"
)

result.onSuccess { user ->
    println("Welcome back, ${user.displayName}!")
    // User is now authenticated
}.onFailure { error ->
    println("Sign in failed: ${error.message}")
}
```

### Sign Out

```kotlin
// Sign out the current user
val result = authEngine.signOut()
result.onSuccess {
    println("Successfully signed out")
}.onFailure { error ->
    println("Sign out failed: ${error.message}")
}
```

### Check Authentication Status

```kotlin
// Check if user is currently signed in
val currentUser = authEngine.getCurrentUser()
if (currentUser != null) {
    println("Signed in as: ${currentUser.displayName}")
} else {
    println("Not signed in")
}
```

### Password Management

```kotlin
// Change password
val result = authEngine.changePassword(
    currentPassword = "OldPassword123!",
    newPassword = "NewSecurePassword456!"
)

result.onSuccess {
    println("Password changed successfully")
}.onFailure { error ->
    println("Password change failed: ${error.message}")
}

// Reset password (sends email)
val resetResult = authEngine.resetPassword("john@example.com")
resetResult.onSuccess {
    println("Password reset email sent")
}.onFailure { error ->
    println("Password reset failed: ${error.message}")
}
```

## Multi-Factor Authentication (MFA)

### Enable MFA

#### TOTP (Authenticator App)

```kotlin
// Enable TOTP MFA
val result = mfaManager.enableMfa(user, MfaMethod.TOTP)
result.onSuccess {
    println("TOTP MFA enabled successfully")
    // In a real app, you would show the user a QR code or secret key
    // to add to their authenticator app
}.onFailure { error ->
    println("Failed to enable TOTP: ${error.message}")
}
```

#### SMS Verification

```kotlin
// Enable SMS MFA
val result = mfaManager.enableMfa(user, MfaMethod.SMS)
result.onSuccess {
    println("SMS MFA enabled successfully")
    // User will receive SMS codes for verification
}.onFailure { error ->
    println("Failed to enable SMS: ${error.message}")
}
```

#### Backup Codes

```kotlin
// Enable backup codes
val result = mfaManager.enableMfa(user, MfaMethod.BACKUP_CODES)
result.onSuccess {
    val backupCodes = mfaManager.backupCodes.value
    println("Backup codes generated: ${backupCodes.size} codes")
    // IMPORTANT: Show these codes to the user and store them securely
    // They can only be viewed once!
}.onFailure { error ->
    println("Failed to generate backup codes: ${error.message}")
}
```

### Verify MFA Codes

#### TOTP Verification

```kotlin
// User enters code from authenticator app
val code = "123456" // 6-digit code from app
val result = mfaManager.verifyMfaCode(user, MfaMethod.TOTP, code)

result.onSuccess {
    println("TOTP verification successful")
}.onFailure { error ->
    println("TOTP verification failed: ${error.message}")
}
```

#### SMS Verification

```kotlin
// User enters SMS code
val code = "123456" // 6-digit code from SMS
val result = mfaManager.verifyMfaCode(user, MfaMethod.SMS, code)

result.onSuccess {
    println("SMS verification successful")
}.onFailure { error ->
    println("SMS verification failed: ${error.message}")
}
```

#### Backup Code Verification

```kotlin
// User enters backup code
val backupCode = "ABC12345" // 8-character backup code
val result = mfaManager.verifyMfaCode(user, MfaMethod.BACKUP_CODES, backupCode)

result.onSuccess {
    println("Backup code verification successful")
    // Note: Backup codes are consumed after use
}.onFailure { error ->
    println("Backup code verification failed: ${error.message}")
}
```

### Manage MFA Settings

```kotlin
// Check MFA status
val isMfaRequired = mfaManager.isMfaRequired(user)
val enabledMethods = mfaManager.enabledMethods.value
val availableMethods = mfaManager.getAvailableMfaMethods(user)

println("MFA required: $isMfaRequired")
println("Enabled methods: ${enabledMethods.map { it.name }}")
println("Available methods: ${availableMethods.map { it.name }}")

// Disable MFA method
val disableResult = mfaManager.disableMfa(user, MfaMethod.SMS)
disableResult.onSuccess {
    println("SMS MFA disabled")
}.onFailure { error ->
    println("Failed to disable SMS: ${error.message}")
}

// Generate new backup codes
val newCodesResult = mfaManager.generateBackupCodes(user)
newCodesResult.onSuccess { codes ->
    println("New backup codes generated: ${codes.size} codes")
    // Show new codes to user
}.onFailure { error ->
    println("Failed to generate new codes: ${error.message}")
}
```

## OAuth Integration

### Sign In with OAuth

```kotlin
// Sign in with Google
val result = authEngine.signInWithOAuth(OAuthProvider.GOOGLE)
result.onSuccess { user ->
    println("Signed in with Google: ${user.displayName}")
}.onFailure { error ->
    println("OAuth sign in failed: ${error.message}")
}

// Sign in with Apple
val appleResult = authEngine.signInWithOAuth(OAuthProvider.APPLE)
appleResult.onSuccess { user ->
    println("Signed in with Apple: ${user.displayName}")
}.onFailure { error ->
    println("Apple sign in failed: ${error.message}")
}
```

### Enhanced OAuth Features

#### Link OAuth Account

```kotlin
// Link a new OAuth provider to existing account
val oauthData = OAuthData(
    providerUserId = "oauth_user_123",
    email = "user@example.com",
    displayName = "John Doe",
    profilePicture = "https://example.com/avatar.jpg",
    accessToken = "access_token_here",
    refreshToken = "refresh_token_here",
    tokenExpiry = Instant.now().plus(1, ChronoUnit.HOURS)
)

val linkResult = enhancedOAuthManager.linkAccount(user, OAuthProvider.GOOGLE, oauthData)
linkResult.onSuccess {
    println("Google account linked successfully")
}.onFailure { error ->
    println("Failed to link account: ${error.message}")
}
```

#### Get Linked Accounts

```kotlin
// Get all linked OAuth accounts
val linkedAccounts = enhancedOAuthManager.getLinkedAccounts(user)
println("Linked accounts: ${linkedAccounts.size}")

linkedAccounts.forEach { account ->
    println("Provider: ${account.provider.name}")
    println("Email: ${account.email}")
    println("Linked at: ${account.linkedAt}")
}
```

#### Unlink OAuth Account

```kotlin
// Unlink an OAuth provider
val unlinkResult = enhancedOAuthManager.unlinkAccount(user, OAuthProvider.FACEBOOK)
unlinkResult.onSuccess {
    println("Facebook account unlinked")
}.onFailure { error ->
    println("Failed to unlink account: ${error.message}")
}
```

#### Refresh OAuth Tokens

```kotlin
// Refresh OAuth tokens
val refreshResult = enhancedOAuthManager.refreshTokens(user, OAuthProvider.GOOGLE)
refreshResult.onSuccess {
    println("OAuth tokens refreshed")
}.onFailure { error ->
    println("Failed to refresh tokens: ${error.message}")
}
```

## Security Features

### Rate Limiting

#### Check Rate Limits

```kotlin
// Check if login attempt is allowed
val result = rateLimiter.checkLoginAttempt("user@example.com")
when (result) {
    is RateLimitResult.Allowed -> {
        println("Login attempt allowed. Remaining: ${result.attemptsRemaining}")
        // Proceed with login
    }
    is RateLimitResult.RateLimited -> {
        println("Rate limited: ${result.reason}")
        println("Retry after: ${result.retryAfter} seconds")
        // Show user they need to wait
    }
    is RateLimitResult.Blocked -> {
        println("Blocked: ${result.reason}")
        if (result.retryAfter != null) {
            println("Account locked. Unlocks in: ${result.retryAfter} seconds")
        }
        // Show user account is locked
    }
}
```

#### Record Authentication Attempts

```kotlin
// Record successful login
rateLimiter.recordSuccessfulLogin("user@example.com")

// Record failed login
rateLimiter.recordFailedLogin("user@example.com", "Invalid password")

// Record other attempts
rateLimiter.recordPasswordResetAttempt("user@example.com")
rateLimiter.recordMfaAttempt("user@example.com")
rateLimiter.recordApiRequest("192.168.1.100")
```

#### Account Lockout Management

```kotlin
// Check if account is locked
val isLocked = rateLimiter.isAccountLocked("user@example.com")
if (isLocked) {
    val remainingTime = rateLimiter.getRemainingLockoutTime("user@example.com")
    println("Account locked. Unlocks in: $remainingTime seconds")
}

// Manually unlock account (admin function)
rateLimiter.unlockAccount("user@example.com")
```

#### Get Rate Limit Statistics

```kotlin
// Get comprehensive statistics
val stats = rateLimiter.getRateLimitStats("user@example.com")
println("Identifier: ${stats.identifier}")
println("Login attempts: ${stats.loginAttempts}")
println("Password reset attempts: ${stats.passwordResetAttempts}")
println("MFA attempts: ${stats.mfaAttempts}")
println("API requests: ${stats.apiRequests}")
println("Is locked: ${stats.isLocked}")
println("Remaining lockout time: ${stats.remainingLockoutTime}")
```

### Security Audit Logging

#### Log Security Events

```kotlin
// Log successful authentication
auditLogger.logSecurityEvent(
    SecurityEvent.AUTHENTICATION_SUCCESS,
    user,
    mapOf(
        "ipAddress" to "192.168.1.100",
        "userAgent" to "Mozilla/5.0...",
        "sessionId" to "session123",
        "method" to "email_password"
    )
)

// Log failed authentication
auditLogger.logFailedAuthentication(
    userEmail = "user@example.com",
    reason = "Invalid password",
    metadata = mapOf(
        "ipAddress" to "192.168.1.100",
        "userAgent" to "Mozilla/5.0..."
    )
)

// Log other security events
auditLogger.logSecurityEvent(SecurityEvent.PASSWORD_CHANGED, user)
auditLogger.logSecurityEvent(SecurityEvent.MFA_ENABLED, user)
auditLogger.logSecurityEvent(SecurityEvent.ACCOUNT_LOCKED, user)
```

#### Query Audit Logs

```kotlin
// Get user's audit entries
val userEntries = auditLogger.getUserAuditEntries(user.id, limit = 50)
println("User audit entries: ${userEntries.size}")

// Get entries by time range
val startTime = Instant.now().minus(7, ChronoUnit.DAYS)
val endTime = Instant.now()
val recentEntries = auditLogger.getAuditEntriesInRange(startTime, endTime, limit = 1000)
println("Recent entries: ${recentEntries.size}")

// Get entries by severity
val highSeverityEntries = auditLogger.getAuditEntriesBySeverity(SecuritySeverity.HIGH, limit = 100)
println("High severity entries: ${highSeverityEntries.size}")

// Export audit data
val csvData = auditLogger.exportAuditData(ExportFormat.CSV)
val jsonData = auditLogger.exportAuditData(ExportFormat.JSON)
val xmlData = auditLogger.exportAuditData(ExportFormat.XML)
```

#### Monitor Security Metrics

```kotlin
// Get security metrics
val metrics = auditLogger.securityMetrics.value
println("Successful authentications: ${metrics.successfulAuthentications}")
println("Failed authentications: ${metrics.failedAuthentications}")
println("Accounts locked: ${metrics.accountsLocked}")
println("Suspicious activities: ${metrics.suspiciousActivitiesDetected}")

// Monitor suspicious activities
val suspiciousActivities = auditLogger.suspiciousActivities.value
suspiciousActivities.forEach { activity ->
    println("Suspicious activity: ${activity.description}")
    println("Type: ${activity.type}")
    println("Risk level: ${activity.riskScore}")
    println("Timestamp: ${activity.timestamp}")
}
```

### Secure Storage

#### Store Sensitive Data

```kotlin
// Store authentication tokens
val storeResult = secureStorage.store("access_token", "eyJhbGciOiJIUzI1NiIs...")
storeResult.onSuccess {
    println("Access token stored securely")
}.onFailure { error ->
    println("Failed to store token: ${error.message}")
}

// Store user preferences
secureStorage.store("user_preferences", "{\"theme\":\"dark\",\"language\":\"en\"}")
```

#### Retrieve Stored Data

```kotlin
// Retrieve stored data
val accessToken = secureStorage.retrieve("access_token")
if (accessToken != null) {
    println("Retrieved access token")
    // Use token for API calls
} else {
    println("No access token found")
}

// Check if data exists
val hasToken = secureStorage.contains("access_token")
println("Has access token: $hasToken")
```

#### Manage Storage

```kotlin
// Get storage statistics
val itemCount = secureStorage.getItemCount()
println("Stored items: $itemCount")

// Get all keys
secureStorage.getAllKeys().collect { keys ->
    println("Storage keys: ${keys.size}")
    keys.forEach { key ->
        println("  - $key")
    }
}

// Remove specific data
secureStorage.remove("access_token")

// Clear all data (use with caution!)
secureStorage.clear()
```

## Advanced Configuration

### Rate Limiter Configuration

```kotlin
// Configure custom rate limiting
val customConfig = RateLimitConfig(
    maxLoginAttemptsPerHour = 3,
    maxPasswordResetAttemptsPerHour = 2,
    maxMfaAttemptsPerHour = 5,
    maxApiRequestsPerMinute = 50,
    maxFailedAttempts = 3,
    failedAttemptWindowMinutes = 10,
    accountLockoutDurationMinutes = 15
)

rateLimiter.configure(customConfig)
```

### Security Audit Configuration

```kotlin
// The audit logger automatically manages:
// - Maximum audit entries (10,000)
// - Retention period (90 days)
// - Suspicious activity thresholds
// - Export formats (CSV, JSON, XML)

// Clean up old entries
auditLogger.cleanup()
```

### Platform-Specific Configuration

```kotlin
// Android-specific configuration
if (PlatformUtils.isAndroid()) {
    // Configure Android-specific settings
    println("Running on Android")
}

// iOS-specific configuration
if (PlatformUtils.isIOS()) {
    // Configure iOS-specific settings
    println("Running on iOS")
}

// Web-specific configuration
if (PlatformUtils.isWeb()) {
    // Configure web-specific settings
    println("Running on Web")
}

// Desktop-specific configuration
if (PlatformUtils.isDesktop()) {
    // Configure desktop-specific settings
    println("Running on Desktop")
}
```

## Troubleshooting

### Common Issues

#### Authentication Failures

**Problem**: User cannot sign in despite correct credentials

**Solutions**:
1. Check if account is locked due to multiple failed attempts
2. Verify rate limiting is not blocking the attempt
3. Check if MFA is required and properly configured
4. Ensure secure storage is working correctly

```kotlin
// Check account status
val isLocked = rateLimiter.isAccountLocked("user@example.com")
val stats = rateLimiter.getRateLimitStats("user@example.com")

if (isLocked) {
    val remainingTime = rateLimiter.getRemainingLockoutTime("user@example.com")
    println("Account locked for $remainingTime more seconds")
} else {
    println("Account is not locked")
    println("Recent login attempts: ${stats.loginAttempts}")
}
```

#### MFA Issues

**Problem**: TOTP codes are not working

**Solutions**:
1. Ensure device time is synchronized
2. Check if TOTP secret is correctly configured
3. Verify the authenticator app is working
4. Use backup codes as fallback

```kotlin
// Check MFA status
val enabledMethods = mfaManager.enabledMethods.value
val hasBackupCodes = enabledMethods.contains(MfaMethod.BACKUP_CODES)

if (enabledMethods.contains(MfaMethod.TOTP)) {
    println("TOTP is enabled")
} else {
    println("TOTP is not enabled")
}

if (hasBackupCodes) {
    println("Backup codes available")
} else {
    println("No backup codes available")
}
```

#### OAuth Problems

**Problem**: OAuth sign-in fails

**Solutions**:
1. Verify OAuth provider configuration
2. Check network connectivity
3. Ensure redirect URIs are correct
4. Verify OAuth app credentials

```kotlin
// Check OAuth configuration
val linkedAccounts = enhancedOAuthManager.getLinkedAccounts(user)
println("Linked OAuth accounts: ${linkedAccounts.size}")

linkedAccounts.forEach { account ->
    println("Provider: ${account.provider.name}")
    println("Last used: ${account.lastUsed}")
    println("Token expires: ${account.tokenExpiry}")
}
```

### Performance Issues

#### Slow Authentication

**Problem**: Authentication takes too long

**Solutions**:
1. Check network latency
2. Verify server performance
3. Monitor rate limiting impact
4. Check secure storage performance

```kotlin
// Monitor performance
val startTime = System.nanoTime()
val result = authEngine.signInWithEmail("user@example.com", "password")
val endTime = System.nanoTime()

val durationMs = (endTime - startTime) / 1_000_000.0
println("Authentication took: ${String.format("%.2f", durationMs)}ms")

if (durationMs > 1000) {
    println("Authentication is slow - investigate performance")
}
```

#### Memory Issues

**Problem**: High memory usage

**Solutions**:
1. Clean up old audit logs
2. Monitor storage usage
3. Check for memory leaks
4. Optimize data structures

```kotlin
// Clean up old data
auditLogger.cleanup()
rateLimiter.cleanup()

// Monitor storage usage
val itemCount = secureStorage.getItemCount()
println("Stored items: $itemCount")

if (itemCount > 1000) {
    println("High storage usage - consider cleanup")
}
```

## Best Practices

### Security Best Practices

1. **Always enable MFA** for production applications
2. **Use strong passwords** with complexity requirements
3. **Implement rate limiting** to prevent brute force attacks
4. **Log all security events** for monitoring and compliance
5. **Regular security audits** of the system
6. **Keep dependencies updated** to patch vulnerabilities

### Performance Best Practices

1. **Monitor performance metrics** regularly
2. **Clean up old data** periodically
3. **Use appropriate rate limiting** thresholds
4. **Optimize storage operations** for your use case
5. **Test under load** to ensure scalability

### Development Best Practices

1. **Use the testing framework** for all components
2. **Mock external dependencies** in tests
3. **Handle errors gracefully** with proper user feedback
4. **Follow the event-driven architecture** for loose coupling
5. **Use platform-specific features** when available

### User Experience Best Practices

1. **Provide clear error messages** for authentication failures
2. **Guide users through MFA setup** with clear instructions
3. **Offer multiple authentication methods** for flexibility
4. **Implement progressive security** (start simple, add complexity)
5. **Provide recovery options** (backup codes, account recovery)

---

*This user guide is maintained as part of the Multi-Auth project. For the latest updates and additional resources, please refer to the project repository.*