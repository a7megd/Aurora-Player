plugins {
    kotlin("multiplatform") version "2.0.21"
    id("com.android.library") version "8.7.3"
}

kotlin {
    androidTarget()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.whisper.player.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}
