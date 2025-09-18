# Gradle Tasks for Multi-Auth Library

This document explains the custom Gradle tasks available for publishing and releasing the Multi-Auth library.

## 📋 Available Tasks

### `publishLibrary`
**Description:** Build and publish the library to GitHub Packages  
**Group:** publishing  
**Dependencies:** build, publish

```bash
./gradlew :shared:publishLibrary
```

**What it does:**
- Builds the library for all platforms
- Publishes to GitHub Packages
- Shows confirmation with library details

### `releaseVersion`
**Description:** Update version in all configuration files  
**Group:** publishing  
**Parameters:** `-Prelease.version=<version>`

```bash
./gradlew :shared:releaseVersion -Prelease.version=1.0.1
```

**What it does:**
- Validates version format (semantic versioning)
- Updates version in `build.gradle.kts`
- Updates version in `gradle/libs.versions.toml`
- Updates module version
- Shows next steps for git operations

**Example:**
```bash
# Valid versions
./gradlew :shared:releaseVersion -Prelease.version=1.0.0
./gradlew :shared:releaseVersion -Prelease.version=1.0.0-beta.1
./gradlew :shared:releaseVersion -Prelease.version=2.0.0-rc.1

# Invalid versions (will fail)
./gradlew :shared:releaseVersion -Prelease.version=1.0
./gradlew :shared:releaseVersion -Prelease.version=v1.0.0
```

### `publishAndRelease`
**Description:** Complete release process (version update + publish)  
**Group:** publishing  
**Parameters:** `-Prelease.version=<version>`

```bash
./gradlew :shared:publishAndRelease -Prelease.version=1.0.1
```

**What it does:**
- Runs `releaseVersion` task
- Runs `publishLibrary` task
- Shows complete summary

## 🚀 Common Workflows

### 1. Quick Publish (Current Version)
```bash
# Just publish what's already configured
./gradlew :shared:publishLibrary
```

### 2. Release New Version
```bash
# Step 1: Update version
./gradlew :shared:releaseVersion -Prelease.version=1.0.1

# Step 2: Review changes
git diff

# Step 3: Commit and tag
git add .
git commit -m "Release version 1.0.1"
git tag -a v1.0.1 -m "Release version 1.0.1"

# Step 4: Push
git push origin main && git push origin v1.0.1

# Step 5: Publish
./gradlew :shared:publishLibrary
```

### 3. Complete Release (Automated)
```bash
# Do everything except git operations
./gradlew :shared:publishAndRelease -Prelease.version=1.0.1

# Then just commit and push
git add .
git commit -m "Release version 1.0.1"
git tag -a v1.0.1 -m "Release version 1.0.1"
git push origin main && git push origin v1.0.1
```

## 🔍 Task Details

### Version Validation
The `releaseVersion` task validates that the version follows semantic versioning:
- ✅ `1.0.0` - Major.Minor.Patch
- ✅ `1.0.0-beta.1` - With pre-release identifier
- ✅ `2.0.0-rc.1` - Release candidate
- ❌ `1.0` - Missing patch version
- ❌ `v1.0.0` - Should not include 'v' prefix
- ❌ `1.0.0.1` - Too many version parts

### Files Updated
When running `releaseVersion`, these files are updated:
- `build.gradle.kts` - Root project version
- `gradle/libs.versions.toml` - Version catalog
- Module version (in memory)

### Output Information
All tasks provide helpful output:
- **publishLibrary**: Shows published artifact details
- **releaseVersion**: Shows next steps for git operations
- **publishAndRelease**: Shows complete summary

## 🛠️ Troubleshooting

### Version Already Exists
If you try to publish a version that already exists:
```bash
# Check what versions are published
# Visit: https://github.com/favorize-app/multi-auth/packages

# Use a different version
./gradlew :shared:releaseVersion -Prelease.version=1.0.2
```

### Authentication Issues
If publishing fails due to authentication:
```bash
# Check your GitHub token
echo $GITHUB_TOKEN

# Or check gradle.properties
cat gradle.properties | grep gpr
```

### Build Failures
If the build fails:
```bash
# Clean and try again
./gradlew clean
./gradlew :shared:publishLibrary
```

## 📚 Related Documentation

- [Library Usage Guide](LIBRARY_USAGE.md)
- [Publishing Setup](PUBLISHING_SETUP.md)
- [API Documentation](docs/API_DOCUMENTATION.md)
