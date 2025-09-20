# Gradle Configuration Guide

This guide explains how to configure OAuth providers for the Multi-Auth library using Gradle build scripts.

## Overview

The Multi-Auth library provides a Gradle plugin that allows you to configure OAuth providers directly in your `build.gradle.kts` file. This approach offers several benefits:

- **Type-safe configuration**: IDE support with autocompletion
- **Build-time validation**: Configuration errors are caught during build
- **Environment-specific settings**: Easy to configure different providers for different environments
- **Security**: Sensitive data can be externalized to properties files or environment variables

## Setup

### 1. Apply the Plugin

Add the Multi-Auth plugin to your project:

```kotlin
plugins {
    kotlin("multiplatform")
    // ... other plugins
    id("multiauth")
}
```

### 2. Configure OAuth Providers

Use the `multiauth` configuration block to set up your OAuth providers:

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
}
```

## Supported Providers

The library supports the following OAuth providers out of the box:

- **Google** (`google`)
- **GitHub** (`github`)
- **Discord** (`discord`)
- **Microsoft** (`microsoft`)
- **LinkedIn** (`linkedin`)
- **Twitter** (`twitter`)
- **Twitch** (`twitch`)
- **Reddit** (`reddit`)
- **Spotify** (`spotify`)
- **Apple** (`apple`)

### Provider Configuration

Each provider supports the following configuration options:

```kotlin
providerName {
    clientId = "required"                    // OAuth client ID
    clientSecret = "optional"                // OAuth client secret (optional for some providers)
    redirectUri = "required"                 // Redirect URI for OAuth callback
    scopes = listOf("scope1", "scope2")      // OAuth scopes to request
    customAuthUrl = "optional"               // Override default authorization URL
    customTokenUrl = "optional"              // Override default token URL
    customUserInfoUrl = "optional"           // Override default user info URL
    customRevokeUrl = "optional"             // Override default token revocation URL
    usePKCE = true                           // Enable PKCE (recommended)
    additionalParams = mapOf(...)            // Additional OAuth parameters
    isEnabled = true                         // Enable/disable this provider
}
```

## Custom Providers

You can also configure custom OAuth providers:

```kotlin
multiauth {
    oauth {
        custom("my-custom-provider") {
            clientId = "your-client-id"
            clientSecret = "your-client-secret"
            redirectUri = "com.example.yourapp://oauth/callback"
            customAuthUrl = "https://your-provider.com/oauth/authorize"
            customTokenUrl = "https://your-provider.com/oauth/token"
            customUserInfoUrl = "https://your-provider.com/api/user"
            scopes = listOf("read", "write")
        }
    }
}
```

## Environment-Specific Configuration

### Using gradle.properties

Store sensitive configuration in `gradle.properties`:

```properties
# gradle.properties
oauth.google.clientId=your-google-client-id
oauth.google.clientSecret=your-google-client-secret
oauth.google.redirectUri=com.example.yourapp://oauth/callback

oauth.github.clientId=your-github-client-id
oauth.github.clientSecret=your-github-client-secret
oauth.github.redirectUri=com.example.yourapp://oauth/callback
```

Then reference them in your build script:

```kotlin
multiauth {
    oauth {
        google {
            clientId = project.findProperty("oauth.google.clientId") as? String ?: ""
            clientSecret = project.findProperty("oauth.google.clientSecret") as? String
            redirectUri = project.findProperty("oauth.google.redirectUri") as? String ?: ""
            scopes = listOf("openid", "email", "profile")
        }
        
        github {
            clientId = project.findProperty("oauth.github.clientId") as? String ?: ""
            clientSecret = project.findProperty("oauth.github.clientSecret") as? String
            redirectUri = project.findProperty("oauth.github.redirectUri") as? String ?: ""
            scopes = listOf("user:email")
        }
    }
}
```

### Using Environment Variables

You can also use environment variables:

```kotlin
multiauth {
    oauth {
        google {
            clientId = System.getenv("GOOGLE_CLIENT_ID") ?: ""
            clientSecret = System.getenv("GOOGLE_CLIENT_SECRET")
            redirectUri = System.getenv("GOOGLE_REDIRECT_URI") ?: ""
            scopes = listOf("openid", "email", "profile")
        }
    }
}
```

## Build Tasks

The plugin adds several Gradle tasks:

- `generateOAuthConfig`: Generates the OAuth configuration JSON file
- `generateAndroidOAuthResources`: Generates Android string resources (for Android projects)
- `compileKotlin`: Automatically runs configuration generation before compilation

## Generated Files

The plugin generates the following files:

- `build/generated/resources/oauth-config.json`: JSON configuration file
- `build/generated/kotlin/OAuthConfig.kt`: Kotlin configuration object
- `build/generated/res/values/oauth_config.xml`: Android string resources (Android projects only)

## Using the Configuration in Code

After configuring your providers, you can use them in your application:

```kotlin
import app.multiauth.MultiAuth
import app.multiauth.oauth.OAuthProvider

class MyAuthService {
    
    fun initializeAuth() {
        // Initialize the library
        MultiAuth.initialize()
        
        // Get OAuth manager
        val oauthManager = MultiAuth.getOAuthManager()
        
        // Check if a provider is configured
        if (MultiAuth.isProviderEnabled(OAuthProvider.GOOGLE)) {
            // Use Google OAuth
            // ...
        }
    }
}
```

## Validation

The plugin validates your configuration during build:

- Required fields are checked
- Redirect URI format is validated
- Provider-specific requirements are enforced

If validation fails, the build will fail with descriptive error messages.

## Best Practices

### Security

1. **Never commit secrets**: Use `gradle.properties` or environment variables for sensitive data
2. **Use PKCE**: Enable PKCE for all providers that support it
3. **Validate redirect URIs**: Ensure your redirect URIs are properly configured with your OAuth provider

### Configuration Management

1. **Environment separation**: Use different configurations for development, staging, and production
2. **Default scopes**: Set appropriate default scopes for each provider
3. **Error handling**: Always provide fallback values for optional configuration

### Performance

1. **Minimal scopes**: Only request the scopes you actually need
2. **Enable only required providers**: Set `isEnabled = false` for unused providers
3. **Cache configuration**: The configuration is loaded once at startup

## Troubleshooting

### Common Issues

1. **Build fails with "Unknown provider"**: Make sure you're using the correct provider name
2. **OAuth flow fails**: Check that your redirect URI matches exactly what's configured with your OAuth provider
3. **Missing client secret**: Some providers require client secrets even for public applications

### Debug Information

Enable debug logging to see configuration details:

```kotlin
// The configuration manager logs detailed information during initialization
// Check your logs for "OAuth Configuration Summary" messages
```

## Migration from Manual Configuration

If you're migrating from manual OAuth configuration:

1. Remove your existing configuration code
2. Add the `multiauth` plugin to your build script
3. Move your configuration to the `multiauth` block
4. Update your initialization code to use `MultiAuth.initialize()`
5. Replace direct OAuth client usage with `MultiAuth.getOAuthManager()`

## Examples

### Complete Android App Configuration

```kotlin
// app/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("multiauth")
}

multiauth {
    oauth {
        google {
            clientId = project.findProperty("oauth.google.clientId") as? String ?: ""
            clientSecret = project.findProperty("oauth.google.clientSecret") as? String
            redirectUri = "com.example.myapp://oauth/callback"
            scopes = listOf("openid", "email", "profile")
        }
        
        github {
            clientId = project.findProperty("oauth.github.clientId") as? String ?: ""
            clientSecret = project.findProperty("oauth.github.clientSecret") as? String
            redirectUri = "com.example.myapp://oauth/callback"
            scopes = listOf("user:email")
        }
    }
}
```

### Multi-Environment Setup

```kotlin
// Different configurations for different build types
multiauth {
    oauth {
        google {
            clientId = when (project.name) {
                "debug" -> project.findProperty("oauth.google.debug.clientId") as? String ?: ""
                "release" -> project.findProperty("oauth.google.release.clientId") as? String ?: ""
                else -> project.findProperty("oauth.google.clientId") as? String ?: ""
            }
            // ... rest of configuration
        }
    }
}
```

This configuration system provides a powerful and flexible way to manage OAuth providers in your Multi-Auth applications while maintaining security and ease of use.
