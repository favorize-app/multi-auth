plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
    id("signing")
}


kotlin {
    androidTarget() // Ensure Android target is registered

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    js(IR) {
        browser()
        nodejs()
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.uuid)
                implementation(libs.jsonwebtoken.jjwt.api)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                // KotlinCrypto dependencies
                implementation(libs.kotlincrypto.sha2)
                implementation(libs.kotlincrypto.hmac.sha1)
                implementation(libs.kotlincrypto.hmac.sha2)
                implementation(compose.runtime)
                implementation(compose.foundation)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.jsonwebtoken.jjwt.impl)
                implementation(libs.jsonwebtoken.jjwt.jackson)
                implementation(libs.androidx.compiler)
                implementation(libs.androidx.runtime)
                implementation(project.dependencies.platform("androidx.compose:compose-bom:2024.09.00"))
                implementation(libs.ktor.client.android)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }

        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }

        val jsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.jsonwebtoken.jjwt.impl)
                implementation(libs.jsonwebtoken.jjwt.jackson)
                implementation(libs.ktor.client.cio)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
            }
        }
    }
}

android {
    namespace = "app.multiauth.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

// Publishing configuration
publishing {
    publications {
        withType<MavenPublication> {
            // Configure POM metadata
            pom {
                name.set("Multi-Auth")
                description.set("A comprehensive Kotlin Multiplatform authentication library supporting OAuth, MFA, biometrics, and more")
                url.set("https://github.com/favorize-app/multi-auth")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("favorize-app")
                        name.set("Favorize Team")
                        email.set("team@favorize.app")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/favorize-app/multi-auth.git")
                    developerConnection.set("scm:git:ssh://github.com:favorize-app/multi-auth.git")
                    url.set("https://github.com/favorize-app/multi-auth")
                }
            }
        }
    }
    
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
}

// Signing configuration
signing {
    val signingKeyId = project.findProperty("signing.keyId") as String?
    val signingKey = project.findProperty("signing.key") as String?
    val signingPassword = project.findProperty("signing.password") as String?
    
    if (signingKeyId != null && signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        sign(publishing.publications)
    }
}

// Custom tasks for publishing and releasing
tasks.register("publishLibrary") {
    group = "publishing"
    description = "Build and publish the library to GitHub Packages"
    
    dependsOn("build", "publish")
    
    doLast {
        println("✅ Library published successfully!")
        println("📦 Group: ${project.group}")
        println("📦 Artifact: ${project.name}")
        println("📦 Version: ${project.version}")
        println("🔗 Repository: https://maven.pkg.github.com/favorize-app/multi-auth")
    }
}

tasks.register("releaseVersion") {
    group = "publishing"
    description = "Release a new version with automatic version bumping and git tagging"
    
    doLast {
        val version = project.findProperty("release.version") as String?
        if (version == null) {
            throw GradleException("Please specify version with -Prelease.version=<version>")
        }
        
        // Validate version format
        if (!version.matches(Regex("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9\\-]+)?$"))) {
            throw GradleException("Version must follow semantic versioning (e.g., 1.0.0, 1.0.0-beta.1)")
        }
        
        println("🚀 Releasing version $version")
        
        // Update version in root build.gradle.kts
        val rootBuildFile = project.rootProject.file("build.gradle.kts")
        val rootContent = rootBuildFile.readText()
        val updatedRootContent = rootContent.replace(
            Regex("version = \".*\""),
            "version = \"$version\""
        )
        rootBuildFile.writeText(updatedRootContent)
        
        // Update version in libs.versions.toml
        val libsFile = project.rootProject.file("gradle/libs.versions.toml")
        val libsContent = libsFile.readText()
        val updatedLibsContent = libsContent.replace(
            Regex("multiauth = \".*\""),
            "multiauth = \"$version\""
        )
        libsFile.writeText(updatedLibsContent)
        
        // Update this module's version
        project.version = version
        
        println("📝 Updated version to $version in all files")
        println("💡 Next steps:")
        println("   1. Review changes: git diff")
        println("   2. Commit changes: git add . && git commit -m \"Release version $version\"")
        println("   3. Create tag: git tag -a v$version -m \"Release version $version\"")
        println("   4. Push changes: git push origin main && git push origin v$version")
        println("   5. Publish library: ./gradlew :shared:publishLibrary")
    }
}

tasks.register("publishAndRelease") {
    group = "publishing"
    description = "Complete release process: version bump, git operations, and publish"
    
    doLast {
        val version = project.findProperty("release.version") as String?
        if (version == null) {
            throw GradleException("Please specify version with -Prelease.version=<version>")
        }
        
        println("🚀 Starting complete release process for version $version")
        
        // First run the release version task
        tasks.named("releaseVersion").get().actions.forEach { it.execute(this) }
        
        // Then build and publish
        tasks.named("publishLibrary").get().actions.forEach { it.execute(this) }
        
        println("✅ Release process completed!")
        println("📋 Summary:")
        println("   - Version updated to $version")
        println("   - Library built and published")
        println("   - Ready for git commit and tag creation")
    }
}
