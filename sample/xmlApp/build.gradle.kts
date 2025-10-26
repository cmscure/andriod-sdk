plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.cmscure.android.xml"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cmscure.android.xml"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables.useSupportLibrary = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // CMSCure SDK - FIXED TYPO: "android" not "andriod "
    implementation("com.github.cmscure:andriod-sdk")

    // If the above doesn't work, you might need to use the local module:
    // implementation(project(":cmscure-sdk"))

    // Android Core dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Required for CMSCure SDK if not included in the SDK itself
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.socket:socket.io-client:2.1.0")
    implementation("io.coil-kt:coil:2.5.0")
    implementation ("com.google.android.material:material:1.13.0")

}

// If you're using JitPack, make sure you have this in your root build.gradle or settings.gradle:
// repositories {
//     maven { url = uri("https://jitpack.io") }
// }