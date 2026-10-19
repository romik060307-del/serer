plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rkdesign.glasslauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rkdesign.glasslauncher"
        minSdk = 26
        // target 28 keeps package discovery reliable on Android Oreo and avoids
        // Android 11 package-visibility filtering for this side-loaded launcher.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
