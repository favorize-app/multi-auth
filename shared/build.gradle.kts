plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidLibrary)
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
                implementation(libs.kotlinx.datetime)
                implementation(libs.uuid)
                implementation(libs.jsonwebtoken.jjwt.api)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
//                implementation(libs.ktor.client.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)
                // KotlinCrypto MACs for HMAC operations
                // Use individual dependencies for better platform compatibility
                //noinspection UseTomlInstead
                implementation("org.kotlincrypto.macs:hmac-sha1:0.7.1")
                implementation(libs.hmac.sha2)
                // Password hashing for secure authentication
                implementation(libs.sha2)
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
                implementation(libs.jjwt.impl)
                implementation(libs.jjwt.jackson)
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
                implementation(libs.kotlin.browser)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(libs.kotlin.test.js)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.jjwt.impl)
                implementation(libs.jjwt.jackson)
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
