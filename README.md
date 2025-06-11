# CMSCure Android SDK

[![JitPack](https://jitpack.io/v/cmscure/andriod-sdk.svg)](https://jitpack.io/#cmscure/andriod-sdk)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)

The official Android SDK for integrating your Kotlin-based Android application with the CMSCure platform. This SDK enables real-time content updates, multi-language localization, remote asset delivery (text, colors, images), and secure, offline-first content caching.

## Features

-   **Simplified Setup:** Easy initialization and configuration in your Application class.
-   **Dynamic Content:** Fetch translations, global colors, and image URLs from CMSCure.
-   **Global Image Assets:** Manage a central library of images, independent of specific app screens.
-   **Automatic Image Caching:** Seamlessly caches all remote images using **Coil** for robust offline support and performance. No extra setup required.
-   **Real-time Updates:** Leverages WebSockets for live content changes, with updates propagated via Kotlin Flows.
-   **Offline First:** Caches all content locally, ensuring your app is fully functional without a network connection.
-   **Language Management:** Supports dynamic language switching with automatic content refreshing.
-   **Modern Android:** Designed for Kotlin and easily integrable with Jetpack Compose and traditional View systems.
-   **Secure:** Uses API keys and project secrets for authenticated communication.

## Requirements

-   Android API Level 26+ (Oreo) - Required for modern cryptographic APIs.
-   Kotlin 1.8+
-   Jetpack Compose (for Compose examples)
-   Coil Image Loading Library

## Installation

### 1. Add JitPack & Dependencies

First, add the JitPack repository to your project's root `settings.gradle` or `settings.gradle.kts` file. Then, add the SDK and Coil dependencies to your app-level `build.gradle.kts`.

```kotlin
// In your settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("[https://jitpack.io](https://jitpack.io)") } // JitPack repository
    }
}

// In your app/build.gradle.kts
dependencies {
    // CMSCure SDK
    implementation("com.github.cmscure:andriod-sdk:1.0.5") // Use the latest version

    // Add Coil for displaying images (works with the SDK's internal caching)
    implementation("io.coil-kt:coil-compose:2.5.0") // if needed
}
```

### 2. Permissions

Ensure your app has internet permissions in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Configuration & Initialization

Initialize and configure the SDK **once** when your application starts. The `Application` class is the recommended place for this.

```kotlin
// In YourApplication.kt (your custom Application class)
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.cmscure.sdk.CMSCureSDK

class YourApplication : Application() {

    @RequiresApi(Build.VERSION_CODES.O) // configure() requires API 26+
    override fun onCreate() {
        super.onCreate()

        // Step 1: Initialize the SDK (must be called before configure)
        CMSCureSDK.init(applicationContext)

        // Step 2: Configure the SDK with your project credentials
        CMSCureSDK.configure(
            context = applicationContext,
            projectId = "YOUR_PROJECT_ID",
            apiKey = "YOUR_API_KEY",
            projectSecret = "YOUR_PROJECT_SECRET"
        )

        // Optional: Enable debug logs
        CMSCureSDK.debugLogsEnabled = true
    }
}
```

Remember to register `YourApplication` in your `AndroidManifest.xml`: `<application android:name=".YourApplication" ... >`

## Core Usage

### Observing Content Updates

The primary way to react to content changes is by observing the `CMSCureSDK.contentUpdateFlow`. This is ideal for Jetpack Compose.

```kotlin
// In a Composable function
LaunchedEffect(Unit) {
    CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
        // updatedIdentifier can be a specific screenName,
        // CMSCureSDK.COLORS_UPDATED, CMSCureSDK.IMAGES_UPDATED,
        // or CMSCureSDK.ALL_SCREENS_UPDATED.
        
        Log.i("MyApp", "Content updated for: $updatedIdentifier. Refreshing UI.")
        // Trigger a refresh of your UI state variables
    }
}
```

### Fetching Content

Use these methods to get the current cached values.

**Translations:**
```kotlin
val pageTitle = CMSCureSDK.translation(forKey = "welcome_title", inTab = "home_screen")
```

**Colors:**
Returns a hex string (e.g., `"#FF5733"`) or `null`.
```kotlin
val primaryColorHex: String? = CMSCureSDK.colorValue(forKey = "primary_brand_color")
```

### Fetching & Displaying Images

The SDK supports two ways to manage images, both with automatic offline caching provided by its internal Coil integration.

**1. Global Image Assets (Recommended)**
Fetch a URL from your central image library.
```kotlin
val logoUrl: URL? = CMSCureSDK.imageURL(forKey = "logo_primary")
```

**2. Screen-Dependent Image URLs (Legacy)**
Fetch a URL stored as a value within a translations tab.
```kotlin
val bannerUrl: URL? = CMSCureSDK.imageUrl(forKey = "hero_image", inTab = "home_assets")
```

**Displaying Images in Jetpack Compose**
To benefit from the SDK's automatic caching, simply use the standard `AsyncImage` composable from Coil. The SDK ensures the image is cached in the background, so `AsyncImage` can load it instantly from the cache when available.

```kotlin
import coil.compose.AsyncImage

// In your Composable...
val logoUrl = CMSCureSDK.imageURL(forKey = "logo_primary")

AsyncImage(
    model = logoUrl?.toString(),
    contentDescription = "Company Logo",
    placeholder = painterResource(id = R.drawable.placeholder), // Optional
    error = painterResource(id = R.drawable.error_image) // Optional
)
```

## License

CMSCure Android SDK is released under the **MIT** License. See `LICENSE.md` for details.
