// Example build.gradle.kts for a project that wants to use the Multi-Auth library

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
    id("multiauth")
}
kotlin {
    androidTarget()
    
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
    }
    
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Add the Multi-Auth library
                implementation("app.favorize.multiauth:shared:1.0.0")
                
                // Other common dependencies
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
            }
        }
        
        val androidMain by getting {
            dependencies {
                // Android-specific dependencies
            }
        }
        
        val iosMain by getting {
            dependencies {
                // iOS-specific dependencies
            }
        }
        
        val jsMain by getting {
            dependencies {
                // Web-specific dependencies
            }
        }
        
        val desktopMain by getting {
            dependencies {
                // Desktop-specific dependencies
            }
        }
    }
}

android {
    namespace = "com.example.yourapp"
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

repositories {
    google()
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/favorize-app/multi-auth")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
