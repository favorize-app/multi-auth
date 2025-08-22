# Multi-Auth System

A standalone, event-driven authentication system built with Kotlin Multiplatform and Compose Multiplatform. This system supports multiple authentication methods (Google OAuth, Email/Password, Phone/SMS) with pluggable providers.

## Features

- **Multi-Platform Support**: iOS, Android, and Web
- **Multiple Authentication Methods**: Email/Password, Phone/SMS, OAuth (Google, Apple, Facebook, etc.)
- **Event-Driven Architecture**: Decoupled event handling for better testability
- **Pluggable Providers**: Easy to swap email, SMS, and OAuth providers
- **Secure Token Management**: JWT-based authentication with refresh tokens
- **Compose Multiplatform UI**: Shared UI components across platforms
- **gRPC Compatibility**: Designed to work with existing gRPC backends

## Architecture

The system follows an event-driven architecture with the following key components:

- **Event System**: Central event bus for all authentication operations
- **Provider Interfaces**: Pluggable interfaces for email, SMS, and OAuth services
- **Authentication Engine**: Core authentication logic and state management
- **Secure Storage**: Platform-specific secure storage for tokens and sensitive data
- **UI Components**: Compose Multiplatform components for authentication flows

## Project Structure

```
multi-auth/
├── shared/                          # Shared Kotlin Multiplatform code
│   ├── src/
│   │   ├── commonMain/             # Common code for all platforms
│   │   ├── androidMain/            # Android-specific code
│   │   ├── iosMain/                # iOS-specific code
│   │   └── jsMain/                 # Web-specific code
│   └── build.gradle.kts
├── composeApp/                      # Compose Multiplatform UI
│   ├── src/
│   │   ├── commonMain/             # Shared UI components
│   │   ├── androidMain/            # Android-specific UI
│   │   ├── iosMain/                # iOS-specific UI
│   │   └── jsMain/                 # Web-specific UI
│   └── build.gradle.kts
├── docs/                           # Project documentation
├── gradle/                         # Gradle configuration
├── build.gradle.kts                # Root build file
└── settings.gradle.kts             # Project settings
```

## Getting Started

### Prerequisites

- Kotlin 1.9.20+
- Android Studio Arctic Fox or later
- Xcode 13+ (for iOS development)
- Node.js 16+ (for web development)

### Setup

1. Clone the repository:
```bash
git clone https://github.com/favorize-app/multi-auth.git
cd multi-auth
```

2. Open the project in Android Studio or your preferred IDE

3. Sync Gradle files and build the project

### Building

#### Android
```bash
./gradlew :composeApp:assembleDebug
```

#### iOS
```bash
./gradlew :shared:linkDebugFrameworkIosArm64
```

#### Web
```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

## Usage

### Basic Authentication

```kotlin
// Get the authentication engine
val authEngine = AuthEngine.getInstance()

// Sign in with email and password
val result = authEngine.signInWithEmail("user@example.com", "password")
result.onSuccess { user ->
    println("Signed in as: ${user.displayName}")
}.onFailure { error ->
    println("Sign in failed: ${error.message}")
}
```

### OAuth Authentication

```kotlin
// Sign in with Google OAuth
val result = authEngine.signInWithOAuth(OAuthProvider.GOOGLE)
result.onSuccess { user ->
    println("OAuth sign in successful: ${user.displayName}")
}.onFailure { error ->
    println("OAuth sign in failed: ${error.message}")
}
```

### Phone Verification

```kotlin
// Send SMS verification code
val result = authEngine.sendPhoneVerification("+1234567890")
result.onSuccess { sessionId ->
    println("Verification code sent. Session ID: $sessionId")
}.onFailure { error ->
    println("Failed to send verification code: ${error.message}")
}
```

## Provider Configuration

### Email Provider

```kotlin
// Configure SendGrid email provider
val emailProvider = SendGridEmailProvider(
    EmailProviderConfig(
        apiKey = "your_sendgrid_api_key",
        fromEmail = "noreply@yourapp.com",
        fromName = "Your App"
    )
)

// Set as the default email provider
AuthEngine.getInstance().setEmailProvider(emailProvider)
```

### SMS Provider

```kotlin
// Configure Twilio SMS provider
val smsProvider = TwilioSmsProvider(
    SmsProviderConfig(
        accountSid = "your_twilio_account_sid",
        authToken = "your_twilio_auth_token",
        fromNumber = "+1234567890"
    )
)

// Set as the default SMS provider
AuthEngine.getInstance().setSmsProvider(smsProvider)
```

### OAuth Provider

```kotlin
// Configure Google OAuth provider
val oauthProvider = GoogleOAuthProvider(
    OAuthProviderConfig(
        clientId = "your_google_client_id",
        clientSecret = "your_google_client_secret",
        redirectUris = listOf("com.yourapp://oauth/callback")
    )
)

// Set as the default OAuth provider
AuthEngine.getInstance().setOAuthProvider(oauthProvider)
```

## Event System

The system uses an event-driven architecture where all authentication operations dispatch events:

```kotlin
// Subscribe to authentication events
EventBus.getInstance().subscribe<AuthEvent.Authentication> { event, metadata ->
    when (event) {
        is AuthEvent.Authentication.SignInCompleted -> {
            println("User signed in: ${event.user.displayName}")
        }
        is AuthEvent.Authentication.SignInFailed -> {
            println("Sign in failed: ${event.error.message}")
        }
        else -> { /* Handle other events */ }
    }
}
```

## Security Features

- **JWT Tokens**: Secure, stateless authentication
- **Token Refresh**: Automatic token renewal
- **Secure Storage**: Platform-specific secure storage for sensitive data
- **Input Validation**: Comprehensive validation for all inputs
- **Rate Limiting**: Protection against brute force attacks
- **OAuth Security**: PKCE flow for mobile apps

## Testing

### Unit Tests

```bash
./gradlew :shared:test
```

### Platform Tests

```bash
# Android
./gradlew :shared:androidUnitTest

# iOS
./gradlew :shared:iosTest

# Web
./gradlew :shared:jsTest
```

### UI Tests

```bash
./gradlew :composeApp:connectedDebugAndroidTest
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support and questions:

- Create an issue on GitHub
- Check the [documentation](docs/)
- Review the [development plan](docs/MULTI_AUTH_DEVELOPMENT_PLAN.md)

## Roadmap

See [MULTI_AUTH_DEVELOPMENT_PLAN.md](docs/MULTI_AUTH_DEVELOPMENT_PLAN.md) for the current development status and roadmap.

## Compatibility

This system is designed to be compatible with:

- **Favorize App**: Existing gRPC backend and user models
- **Existing Systems**: Can be integrated with current authentication systems
- **Multiple Platforms**: iOS, Android, and Web applications
- **Various Providers**: Firebase, SendGrid, Twilio, Google, Apple, etc.