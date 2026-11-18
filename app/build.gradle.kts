plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.tgwsproxy.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tgwsproxy.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install("cryptography")
            install("httpx[http2]")
            install("certifi")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
