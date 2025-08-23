plugins {
    kotlin("multiplatform") version "2.2.10" apply false
    kotlin("plugin.serialization") version "2.2.10" apply false
    id("org.jetbrains.compose") version "1.6.0" apply false
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "org.jetbrains.compose")
    
    group = "app.multiauth"
    version = "0.1.0-SNAPSHOT"
}