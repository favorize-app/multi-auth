# Multi-Auth Quick Start Guide

This guide shows you how to quickly set up authentication with the Multi-Auth library using Gradle configuration. Multi-Auth supports OAuth, email, SMS, and other providers.

## 1. Add the Plugin

Add the Multi-Auth plugin to your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.application") // or other plugins
    id("app.favorize.multiauth") version "1.0.1"
}
```

## 2. Configure Providers

Add provider configuration to your `build.gradle.kts`:

```kotlin
multiauth {
    oauth {
        google {
            clientId = "your-google-client-id"
            clientSecret = "your-google-client-secret"
            redirectUri = "com.example.yourapp://oauth/callback"
            scopes = listOf("openid", "email", "profile")
        }
        github {
            clientId = "your-github-client-id"
            clientSecret = "your-github-client-secret"
            redirectUri = "com.example.yourapp://oauth/callback"
            scopes = listOf("user:email")
        }
    }
    // Example for other providers (if supported in your plugin implementation):
    // email {
    //     smtpServer = "smtp.example.com"
    //     username = "user@example.com"
    //     password = project.findProperty("email.password") as? String
    // }
    // sms {
    //     provider = "twilio"
    //     apiKey = project.findProperty("sms.apiKey") as? String
    // }
}
```

## 3. Initialize the Library

In your application code, initialize the Multi-Auth library:

```kotlin
import app.multiauth.MultiAuth
import app.multiauth.oauth.OAuthProvider

class MyApp {
    fun initializeAuth() {
        // Initialize the library
        MultiAuth.initialize()
        // Now you can use authentication
        val oauthManager = MultiAuth.getOAuthManager()
        if (MultiAuth.isProviderEnabled(OAuthProvider.GOOGLE)) {
            println("Google OAuth is configured!")
        }
    }
}
```

## 4. Use Authentication

Here's a simple example of using OAuth authentication:

```kotlin
import app.multiauth.MultiAuth
import app.multiauth.oauth.OAuthProvider

class AuthService {
    private val oauthManager = MultiAuth.getOAuthManager()
    suspend fun signInWithGoogle(): Result<User> {
        return oauthManager.signInWithOAuth(OAuthProvider.GOOGLE)
    }
    suspend fun signInWithGitHub(): Result<User> {
        return oauthManager.signInWithOAuth(OAuthProvider.GITHUB)
    }
}
```

## 5. Security Best Practices

### Use gradle.properties for Secrets

Create a `gradle.properties` file (don't commit it to version control):

```properties
oauth.google.clientId=your-actual-google-client-id
oauth.google.clientSecret=your-actual-google-client-secret
oauth.github.clientId=your-actual-github-client-id
oauth.github.clientSecret=your-actual-github-client-secret
```

Then update your build script:

```kotlin
multiauth {
    oauth {
        google {
            clientId = project.findProperty("oauth.google.clientId") as? String ?: ""
            clientSecret = project.findProperty("oauth.google.clientSecret") as? String
            redirectUri = "com.example.yourapp://oauth/callback"
            scopes = listOf("openid", "email", "profile")
        }
        github {
            clientId = project.findProperty("oauth.github.clientId") as? String ?: ""
            clientSecret = project.findProperty("oauth.github.clientSecret") as? String
            redirectUri = "com.example.yourapp://oauth/callback"
            scopes = listOf("user:email")
        }
    }
}
```

## 6. Complete Example

Here's a complete example for an Android app:

### build.gradle.kts
```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("app.favorize.multiauth") version "1.0.1"
}

multiauth {
    oauth {
        google {
            clientId = project.findProperty("oauth.google.clientId") as? String ?: ""
            clientSecret = project.findProperty("oauth.google.clientSecret") as? String
            redirectUri = "com.example.myapp://oauth/callback"
            scopes = listOf("openid", "email", "profile")
        }
    }
    // email { ... } // Example for other providers
    // sms { ... }
}
```

### MainActivity.kt (Android)
```kotlin
import androidx.compose.runtime.*
import app.multiauth.MultiAuth
import app.multiauth.oauth.OAuthProvider

@Composable
fun LoginScreen() {
    var isLoading by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf<User?>(null) }
    LaunchedEffect(Unit) {
        MultiAuth.initialize()
    }
    Column {
        if (isLoading) {
            Text("Signing in...")
        } else if (user != null) {
            Text("Welcome, ${user.displayName}!")
        } else {
            Button(
                onClick = {
                    isLoading = true
                    // Sign in with Google
                }
            ) {
                Text("Sign in with Google")
            }
        }
    }
}
```

## Next Steps
- Read the [Gradle Configuration Guide](GRADLE_CONFIGURATION_GUIDE.md) for advanced configuration options
- Check out the [API Documentation](API_DOCUMENTATION.md) for detailed API reference
- See [Real World Examples](REAL_WORLD_EXAMPLES.md) for more complex use cases

## Troubleshooting

### Build Issues
- Make sure you've applied the `app.favorize.multiauth` plugin
- Check that your provider names are correct (e.g., "google", not "Google")

### Runtime Issues
- Ensure you call `MultiAuth.initialize()` before using any authentication features
- Verify your provider configuration matches what you've set up with the provider

### OAuth Flow Issues
- Double-check your redirect URI matches exactly what's configured with your OAuth provider
- Make sure your client ID and secret are correct
- Verify that your app is properly configured to handle the redirect URI

For more help, check the [Troubleshooting Guide](TROUBLESHOOTING_GUIDE.md).
