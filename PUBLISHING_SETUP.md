# Multi-Auth Library Publishing Setup

This document explains the simple Gradle-based setup for publishing the Multi-Auth library as a dependency for other Kotlin Multiplatform projects.

## 🎯 What Was Configured

### 1. Maven Publishing Configuration

**Root `build.gradle.kts`:**
- Added `maven-publish` and `signing` plugins
- Configured GitHub Packages repository
- Set group ID to `app.favorize.multiauth`
- Set version to `1.0.0`

**Shared Module `build.gradle.kts`:**
- Added publishing and signing plugins
- Configured POM metadata with:
  - Library name and description
  - MIT license information
  - Developer information
  - SCM links
- Set up GitHub Packages as publishing repository
- Added signing configuration for secure publishing
- **Added custom Gradle tasks for easy publishing**

### 2. Custom Gradle Tasks

**Three simple tasks for all publishing needs:**

1. **`publishLibrary`** - Build and publish the library
2. **`releaseVersion`** - Update version in all files
3. **`publishAndRelease`** - Complete release process

### 3. Version Management

**`gradle/libs.versions.toml`:**
- Added `multiauth` version reference
- Centralized version management

**`gradle.properties`:**
- Added publishing and signing properties
- Optimized build settings
- Platform-specific configurations

### 4. Documentation

**`LIBRARY_USAGE.md`:**
- Complete usage guide for consuming projects
- Installation instructions
- Code examples for all features
- Platform-specific setup
- Troubleshooting guide

**`README_LIBRARY.md`:**
- Quick start guide
- Feature overview
- Setup instructions for publishers
- Support and contribution information

**`example-consumer/build.gradle.kts`:**
- Example configuration for projects using the library
- Shows proper repository and dependency setup

## 🚀 How to Publish

### Option 1: Simple Publishing (Recommended)

```bash
# Just publish the current version
./gradlew :shared:publishLibrary
```

### Option 2: Release New Version

```bash
# Update version and prepare for release
./gradlew :shared:releaseVersion -Prelease.version=1.0.1

# Then commit, tag, and push manually:
git add .
git commit -m "Release version 1.0.1"
git tag -a v1.0.1 -m "Release version 1.0.1"
git push origin main && git push origin v1.0.1

# Finally publish
./gradlew :shared:publishLibrary
```

## 📦 How to Use in Other Projects

### 1. Add Repository

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
```

### 2. Add Dependency

```kotlin
dependencies {
    implementation("app.favorize.multiauth:shared:1.0.0")
}
```

### 3. Configure GitHub Token

**For local development:**
```properties
# gradle.properties
gpr.user=your-github-username
gpr.key=your-github-token
```

**For CI/CD:**
```bash
export GITHUB_TOKEN=your-github-token
```

## 🔧 Required Setup

### GitHub Token Permissions

The GitHub token needs these permissions:
- `read:packages` - To download the library
- `write:packages` - To publish the library (for publishing)
- `repo` - To create releases (for automated publishing)

### Repository Access

The library is published to:
- **GitHub Packages**: `https://maven.pkg.github.com/favorize-app/multi-auth`
- **Group ID**: `app.favorize.multiauth`
- **Artifact ID**: `shared`
- **Version**: `1.0.0` (or latest)

## 🎯 Next Steps

1. **Test the setup** by running a dry-run release:
   ```bash
   ./scripts/release.sh 1.0.0 --dry-run
   ```

2. **Create a GitHub token** with the required permissions

3. **Publish the first version**

4. **Test in your Favorize project** by adding the dependency

5. **Update documentation** as needed for your specific use case

## 🔍 Verification

To verify the library is properly published:

1. Check GitHub Packages: https://github.com/favorize-app/multi-auth/packages
2. Test installation in a sample project
3. Verify all platforms build correctly
4. Check that all features work as expected

## 📚 Additional Resources

- [Complete Usage Guide](LIBRARY_USAGE.md)
- [API Documentation](docs/API_DOCUMENTATION.md)
- [Architecture Overview](docs/ARCHITECTURE.md)
- [Example Consumer Project](example-consumer/)

The library is now ready to be used as a dependency in your Kotlin Multiplatform projects! 🎉
