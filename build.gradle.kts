plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
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

    // Include sources and javadoc jars in release artifact
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // implementation(libs.material) // Uncomment if material is used in SDK UI

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// JitPack-compatible publishing block
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.cmscure"              // GitHub username or org
                artifactId = "andriod-sdk"                  // GitHub repo name
                version = "1.0.0"                            // Optional: JitPack overrides this with Git tag

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
                            email.set("your-contact-email@example.com") // 🔁 Replace this
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