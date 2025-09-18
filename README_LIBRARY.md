# Multi-Auth Library

A comprehensive Kotlin Multiplatform authentication library supporting OAuth, MFA, biometrics, and more.

## 🚀 Quick Start

### Installation

Add the repository and dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/favorize-app/multi-auth")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("app.favorize.multiauth:shared:1.0.0")
}
```

### Basic Usage

```kotlin
import app.multiauth.core.AuthEngine
import app.multiauth.oauth.OAuthManager

class AuthService {
    private val authEngine = AuthEngine()
    private val oauthManager = OAuthManager()
    
    suspend fun signIn() {
        val result = oauthManager.authenticateWithGoogle()
        if (result.isSuccess) {
            println("User authenticated: ${result.getOrNull()?.email}")
        }
    }
}
```

## 📚 Documentation

- [Complete Usage Guide](LIBRARY_USAGE.md)
- [API Documentation](docs/API_DOCUMENTATION.md)
- [Architecture Overview](docs/ARCHITECTURE.md)

## 🏗️ Features

- **OAuth 2.0** - Support for Google, GitHub, Microsoft, Discord, and more
- **Multi-Factor Authentication** - TOTP, SMS verification
- **Biometric Authentication** - Fingerprint, Face ID, Touch ID
- **Session Management** - Secure token handling and refresh
- **Cross-Platform** - Android, iOS, Desktop, Web support
- **Security** - Rate limiting, threat detection, audit logging

## 🔧 Setup for Publishing

This library is configured for publishing to GitHub Packages. To publish a new version:

### Simple Publishing

```bash
# Just publish the current version
./gradlew :shared:publishLibrary
```

### Release New Version

```bash
# Update version and prepare for release
./gradlew :shared:releaseVersion -Prelease.version=1.0.1

# Commit and push changes
git add .
git commit -m "Release version 1.0.1"
git tag -a v1.0.1 -m "Release version 1.0.1"
git push origin main && git push origin v1.0.1

# Publish the library
./gradlew :shared:publishLibrary
```

### Available Gradle Tasks

- `publishLibrary` - Build and publish the library
- `releaseVersion` - Update version in all files
- `publishAndRelease` - Complete release process (version + publish)

## 🔑 Authentication Setup

### GitHub Token

You need a GitHub Personal Access Token with `read:packages` permission:

1. Go to GitHub Settings → Developer settings → Personal access tokens
2. Generate a new token with `read:packages` scope
3. Set it as an environment variable:
   ```bash
   export GITHUB_TOKEN=your_token_here
   ```

### OAuth Configuration

Configure OAuth providers in your app:

```kotlin
val googleConfig = OAuthConfig(
    clientId = "your-google-client-id",
    clientSecret = "your-google-client-secret",
    redirectUri = "your-app://oauth/callback"
)
```

## 📦 Supported Platforms

- ✅ Android (API 24+)
- ✅ iOS (iOS 13+)
- ✅ Desktop (JVM 17+)
- ✅ Web (Modern browsers)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- 📖 [Documentation](docs/)
- 🐛 [Report Issues](https://github.com/favorize-app/multi-auth/issues)
- 💬 [Discussions](https://github.com/favorize-app/multi-auth/discussions)

## 🔄 Version History

- **1.0.0** - Initial release with OAuth, MFA, and biometric support
