# Multi-Auth Plugin DSL Examples

This document shows the different DSL approaches available for the Multi-Auth plugin.

## Current Implementation (Method-based DSL)

The current plugin uses individual methods for each OAuth provider:

```kotlin
// build.gradle.kts
plugins {
    id("multiauth")
}

multiauth {
    oauth {
        google {
            clientId = "your-google-client-id-here"
            clientSecret = "your-google-client-secret-here"
            redirectUri = "com.example.multiauth://oauth/callback"
            scopes = listOf("openid", "email", "profile")
        }
        
        github {
            clientId = "your-github-client-id-here"
            clientSecret = "your-github-client-secret-here"
            redirectUri = "com.example.multiauth://oauth/callback"
            scopes = listOf("user:email")
        }
        
        custom("my-custom-provider") {
            clientId = "your-custom-client-id"
            clientSecret = "your-custom-client-secret"
            redirectUri = "com.example.multiauth://oauth/callback"
            customAuthUrl = "https://your-custom-provider.com/oauth/authorize"
            customTokenUrl = "https://your-custom-provider.com/oauth/token"
            customUserInfoUrl = "https://your-custom-provider.com/api/user"
            scopes = listOf("read", "write")
        }
    }
}
```

## Improved Implementation (NamedDomainObjectContainer)

The improved implementation would enable this more idiomatic syntax:

```kotlin
// build.gradle.kts
plugins {
    id("multiauth")
}

multiauth {
    oauth {
        providers {
            google {
                clientId = "your-google-client-id-here"
                clientSecret = "your-google-client-secret-here"
                redirectUri = "com.example.multiauth://oauth/callback"
                scopes = listOf("openid", "email", "profile")
            }
            
            github {
                clientId = "your-github-client-id-here"
                clientSecret = "your-github-client-secret-here"
                redirectUri = "com.example.multiauth://oauth/callback"
                scopes = listOf("user:email")
            }
            
            "my-custom-provider" {
                clientId = "your-custom-client-id"
                clientSecret = "your-custom-client-secret"
                redirectUri = "com.example.multiauth://oauth/callback"
                customAuthUrl = "https://your-custom-provider.com/oauth/authorize"
                customTokenUrl = "https://your-custom-provider.com/oauth/token"
                customUserInfoUrl = "https://your-custom-provider.com/api/user"
                scopes = listOf("read", "write")
            }
        }
    }
}
```

## Key Differences

### Current Implementation
- ✅ **Pros**: Simple, explicit method names, type-safe
- ❌ **Cons**: Requires individual methods for each provider, more verbose

### NamedDomainObjectContainer Implementation
- ✅ **Pros**: More idiomatic Gradle DSL, shorter syntax, dynamic provider names
- ❌ **Cons**: More complex implementation, requires `@DslMarker`, harder to provide IDE autocomplete

## Implementation Requirements for NamedDomainObjectContainer

To implement the improved DSL, you need:

1. **NamedDomainObjectContainer**: A container that manages named objects
2. **NamedDomainObjectFactory**: Factory for creating named objects
3. **@DslMarker**: Prevents scope pollution in nested DSL blocks
4. **Custom Extension Classes**: Proper extension hierarchy

### Example Implementation Structure

```kotlin
// 1. Create a DSL marker
@DslMarker
annotation class OAuthDslMarker

// 2. Create a named configuration class
@OAuthDslMarker
open class OAuthProviderConfigurationWithName(override val name: String) {
    var clientId: String = ""
    var clientSecret: String? = null
    // ... other properties
}

// 3. Create extension with container
@OAuthDslMarker
open class OAuthExtensionWithContainer(
    val providers: NamedDomainObjectContainer<OAuthProviderConfigurationWithName>
)

// 4. Create main extension
@OAuthDslMarker
open class MultiAuthExtensionWithContainer(
    val oauth: OAuthExtensionWithContainer
) {
    constructor(providers: NamedDomainObjectContainer<OAuthProviderConfigurationWithName>) : this(
        OAuthExtensionWithContainer(providers)
    )
}

// 5. In plugin apply method
val providersContainer = project.container(OAuthProviderConfigurationWithName::class.java)
val extension = project.extensions.create<MultiAuthExtensionWithContainer>("multiauth", providersContainer)
```

## Recommendation

For the Multi-Auth plugin, I recommend **keeping the current method-based approach** because:

1. **Simplicity**: Easier to implement and maintain
2. **Type Safety**: Better IDE support and autocomplete
3. **Explicit**: Clear what providers are supported
4. **Sufficient**: The current syntax is already quite readable

The NamedDomainObjectContainer approach is more complex and doesn't provide significant benefits for this use case where you have a known set of OAuth providers.

## Migration Path

If you decide to implement the NamedDomainObjectContainer approach:

1. Create the new plugin class (`MultiAuthPluginWithNamedContainer`)
2. Update the plugin descriptor to use the new class
3. Update the build scripts to use the new syntax
4. Test thoroughly with all supported OAuth providers

The current implementation is working and provides a good developer experience, so the migration would be optional.
