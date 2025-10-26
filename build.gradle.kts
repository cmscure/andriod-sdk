group = "com.github.cmscure"

plugins {
    id("com.android.library") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.23"
    id("maven-publish")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

android {
    namespace = "com.cmscure.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // CRITICAL: Enable Compose support
    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11" // Match your Kotlin version
    }

    // Include sources and javadoc jars in release artifact
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // AndroidX core + appcompat
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // CRITICAL: Add Compose dependencies
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.ui:ui-graphics")
    api("androidx.compose.runtime:runtime")
    api("androidx.compose.material3:material3")

    // Coil for Compose (not the old version)
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Socket.IO
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }

    // SharedPreferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// JitPack-compatible publishing block
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.cmscure"
                artifactId = "andriod-sdk"
                version = "1.0.12"  // Bump version for the fix

                pom {
                    name.set("CMSCure Android SDK")
                    description.set("The official Android SDK for CMSCure content management system.")
                    url.set("https://github.com/cmscure/andriod-sdk")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cmscure")
                            name.set("CMSCure Team")
                            email.set("info@reignsol.com")
                            organization.set("CMSCure")
                            organizationUrl.set("https://github.com/cmscure")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/cmscure/andriod-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/cmscure/andriod-sdk.git")
                        url.set("https://github.com/cmscure/andriod-sdk/tree/main")
                    }
                }
            }
        }
    }
}