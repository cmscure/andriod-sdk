# CMSCure Android SDK

[![JitPack](https://jitpack.io/v/cmscure/andriod-sdk.svg)](https://jitpack.io/#cmscure/andriod-sdk)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)

The official Android SDK for integrating your Kotlin-based Android application with the CMSCure platform. This SDK enables real-time content updates via WebSockets, multi-language localization, remote asset delivery (text, colors, image URLs), and secure project-based configuration with offline caching.

## Features

* **Simplified Setup:** Easy initialization and configuration.
* **Dynamic Content:** Fetch translations, global colors, and image URLs from CMSCure.
* **Real-time Updates:** Leverages WebSockets for live content changes, with updates propagated via Kotlin Flows.
* **Offline First:** Caches content locally, ensuring functionality even without network access.
* **Language Management:** Supports dynamic language switching, with automatic content refresh.
* **Modern Android:** Designed for Kotlin and easily integrable with Jetpack Compose and traditional View systems.
* **Secure:** Uses API keys and project secrets for authenticated communication.

## Requirements

* Android API Level 24+ (Nougat)
* Kotlin 1.8+
* Android Gradle Plugin 7.0+
* Java 11 (for build environment)
* Required dependencies: Kotlin Coroutines, Retrofit, OkHttp, Socket.IO Client Java, Gson.

## Installation

### 1. Add JitPack Repository

Add the JitPack repository to your project's root `settings.gradle` or `settings.gradle.kts` file:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("[https://jitpack.io](https://jitpack.io)") } // JitPack repository
    }
}
```

### 2. Add SDK Dependency

Add the CMSCure SDK dependency to your app module's `build.gradle` or `build.gradle.kts` file:

```kotlin
// build.gradle.kts (:app)
dependencies {
    implementation("com.github.cmscure:andriod-sdk:1.0.4") // Use the latest version

    // CMSCure SDK uses Kotlin Coroutines for background tasks and flows
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") // Or latest
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Or latest
    
    // Other necessary dependencies like Retrofit, OkHttp, Socket.IO, Gson
    // are transitive dependencies of the SDK.
}
```
Sync your project with the Gradle files.

### 3. Permissions

Ensure your app has the necessary internet permissions in your `AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="[http://schemas.android.com/apk/res/android](http://schemas.android.com/apk/res/android)"
    ...>

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        ...>
        <!-- If targeting Android P (API 28) or higher and using HTTP for local dev, -->
        <!-- ensure networkSecurityConfig is set up for cleartext traffic if needed. -->
        <!-- e.g., android:networkSecurityConfig="@xml/network_security_config" -->
        <!-- android:usesCleartextTraffic="true" -->
        ...
    </application>
</manifest>
```

## Configuration & Initialization

Initialize and configure the SDK **once** when your application starts. The `Application` class is the recommended place for this.

**Important:** `CMSCureSDK.configure()` requires **API Level 26 (Android O)** or higher due to its use of modern cryptographic APIs. Ensure your app's `minSdk` is compatible or handle this requirement appropriately.

```kotlin
// In YourApplication.kt (your custom Application class)
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.cmscure.sdk.CMSCureSDK

class YourApplication : Application() {

    @RequiresApi(Build.VERSION_CODES.O) // configure() requires API 26+
    override fun onCreate() {
        super.onCreate()

        // Step 1: Initialize the SDK (must be called once before configure)
        // This sets up shared preferences and loads any persisted state.
        CMSCureSDK.init(applicationContext)

        // Step 2: Configure the SDK with your project credentials
        // Replace with your actual credentials. Store them securely!
        CMSCureSDK.configure(
            context = applicationContext,
            projectId = "YOUR_PROJECT_ID",
            apiKey = "YOUR_API_KEY",
            projectSecret = "YOUR_PROJECT_SECRET"
            // Optional: serverUrlString and socketIOURLString default to CMSCure production URLs.
            // For local development with an emulator, you might use:
            // serverUrlString = "[http://10.0.2.2](http://10.0.2.2):YOUR_BACKEND_PORT",
            // socketIOURLString = "ws://10.0.2.2:YOUR_SOCKET_PORT"
        )

        // After `configure` is called, the SDK attempts to authenticate, connect to the
        // WebSocket server, and perform an initial synchronization of content.
        // There isn't a direct callback for configuration completion; instead, you should
        // observe `CMSCureSDK.contentUpdateFlow` for content readiness and updates.

        // Optional: Enable debug logs from the SDK
        CMSCureSDK.debugLogsEnabled = true

        Log.i("YourApplication", "CMSCure SDK initialized and configured.")
    }
}
```
Remember to register `YourApplication` in your `AndroidManifest.xml`:
`<application android:name=".YourApplication" ... >`

## Core Usage

### Observing Content Updates

The primary way to react to content changes (from initial sync, language change, or real-time updates) is by observing the `CMSCureSDK.contentUpdateFlow`:

```kotlin
// In your Activity, Fragment, or ViewModel (using lifecycleScope)
// Or in a Composable (using LaunchedEffect)

// Example in an Activity:
lifecycleScope.launch {
    CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
        // updatedIdentifier can be:
        // - A specific screenName (String) if that tab's content was updated.
        // - CMSCureSDK.COLORS_UPDATED if global colors were updated.
        // - CMSCureSDK.ALL_SCREENS_UPDATED if a general refresh is needed (e.g., after language change).
        
        Log.i("MyApp", "CMSCure content updated for: $updatedIdentifier. Refreshing UI.")
        // Trigger a refresh of your UI elements that depend on CMSCure data.
        // For example, re-fetch translations, colors, image URLs.
        loadContentForMyScreen() 
    }
}
```

### Language Management

**Set Current Language:**
Updates the active language. The SDK will then fetch content for this new language and emit an update via `contentUpdateFlow`. Requires API Level 26 (Android O)+.

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    CMSCureSDK.setLanguage("fr") // Example: Set language to French
} else {
    Log.w("MyApp", "Setting language requires API Level O or higher.")
}
```

**Get Current Language:**
Returns the currently active language code (e.g., "en", "fr").
```kotlin
val currentLanguage: String = CMSCureSDK.getLanguage()
```

**Fetch Available Languages:**
Asynchronously retrieves a list of language codes supported by your project. The callback runs on the Main thread.
```kotlin
CMSCureSDK.availableLanguages { languageCodesArray ->
    // languageCodesArray will be a List<String>, e.g., ["en", "fr", "es"]
    Log.d("MyApp", "Available languages from CMS: $languageCodesArray")
    // Use this list to populate a language selection UI
}
```

### Fetching Content Manually

Use these methods to get the current cached values. These are typically called after `contentUpdateFlow` signals an update or during initial UI setup.

**Translations:**
Retrieve a translated string for a key within a specific "tab" (screen/content group). Returns an empty string if not found.
```kotlin
val pageTitle: String = CMSCureSDK.translation(forKey = "welcome_title", inTab = "home_screen")
val buttonLabel: String = CMSCureSDK.translation("submit_button", "user_form").ifEmpty { "Submit" }
```

**Colors:**
Retrieve a global color hex string (e.g., `"#FF5733"`). Colors are fetched from the `__colors__` tab by default. Returns `null` if the key isn't found.
```kotlin
val primaryColorHex: String? = CMSCureSDK.colorValue(forKey = "primary_brand_color")
if (primaryColorHex != null) {
    try {
        val colorInt = android.graphics.Color.parseColor(primaryColorHex)
        // myView.setBackgroundColor(colorInt)
    } catch (e: IllegalArgumentException) {
        Log.e("MyApp", "Invalid hex color from CMS: $primaryColorHex")
    }
}
```

**Image URLs:**
Retrieve an image `URL` for a given key and tab. Returns `null` if the key isn't found or its value isn't a valid URL.
```kotlin
val logoUrl: java.net.URL? = CMSCureSDK.imageUrl(forKey = "app_logo", inTab = "common_assets")
if (logoUrl != null) {
    // Use with image loading libraries like Coil, Glide, or Picasso
    // Coil example:
    // myImageView.load(logoUrl.toString())
}
```

### Real-time Connection Status

Check if the SDK's WebSocket is currently connected:
```swift
if (CMSCureSDK.isConnected()) {
    Log.i("MyApp", "CMSCure SDK is connected for real-time updates.")
} else {
    Log.w("MyApp", "CMSCure SDK is not currently connected.")
}
```
The SDK attempts to manage the connection automatically after `configure()`.

### Manual Synchronization (Optional)

While the SDK handles initial sync and real-time updates, you can trigger a manual sync for a specific tab if needed. Requires API Level 26 (Android O)+.
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    CMSCureSDK.sync("my_special_content_tab") { success ->
        Log.d("MyApp", "Manual sync for 'my_special_content_tab' completed. Success: $success")
    }
}
```

## Jetpack Compose Integration Example

Observe `contentUpdateFlow` and update your Composable states.

```kotlin
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
import com.cmscure.sdk.CMSCureSDK
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import coil.compose.AsyncImage // Example image loader
import android.util.Log // For logging

// Helper to convert hex string to Compose Color (place in a utility file)
fun String?.toComposeColor(defaultColor: ComposeColor = ComposeColor.Gray): ComposeColor {
    val hex = this?.trim()?.removePrefix("#") ?: return defaultColor
    if (hex.length != 6 && hex.length != 8) return defaultColor // Basic validation
    return try {
        val colorLong = hex.toLong(16)
        if (hex.length == 8) { ComposeColor(color = colorLong) } // AARRGGBB
        else { ComposeColor(color = 0xFF000000L or colorLong) } // RRGGBB
    } catch (e: NumberFormatException) {
        Log.w("ColorParse", "Failed to parse hex '$this' to ComposeColor.", e)
        defaultColor
    }
}

@RequiresApi(Build.VERSION_CODES.O) // Due to CMSCureSDK.configure and other methods
@Composable
fun MyDynamicComposeScreen() {
    var screenTitle by remember { mutableStateOf("Loading Title...") }
    var welcomeMessage by remember { mutableStateOf<String?>(null) }
    var pageBackgroundColor by remember { mutableStateOf(ComposeColor.White) }
    var bannerUrl by remember { mutableStateOf<String?>(null) }

    val refreshContent = {
        Log.d("MyComposeScreen", "Refreshing content from CMSCureSDK")
        screenTitle = CMSCureSDK.translation("screen_title", "compose_demo").ifEmpty { "My Screen" }
        welcomeMessage = CMSCureSDK.translation("welcome_message", "compose_demo")
        pageBackgroundColor = CMSCureSDK.colorValue("page_background")
            .toComposeColor(defaultColor = ComposeColor.LightGray)
        bannerUrl = CMSCureSDK.imageUrl("banner_image", "compose_assets")?.toString()
    }

    LaunchedEffect(Unit) { // Use Unit for one-time setup
        refreshContent() // Initial content load

        CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
            Log.i("MyComposeScreen", "contentUpdateFlow emitted: $updatedIdentifier. Refreshing.")
            refreshContent() // Re-fetch data and update states
        }
    }

    Column(modifier = Modifier.background(pageBackgroundColor)) {
        Text(text = screenTitle, style = MaterialTheme.typography.headlineMedium)
        
        welcomeMessage?.let { Text(text = it) }

        bannerUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "Banner Image",
                // Add modifiers, placeholders, error handling as needed
            )
        }
        // ... more composables ...
    }
}
```

## Cache Management

**Clear All Cached Data:**
Resets the SDK, removing all locally stored data (translations, colors, known tabs, internal SDK config) and its runtime configuration. `CMSCureSDK.init()` and `CMSCureSDK.configure()` will need to be called again to make the SDK operational.
```kotlin
CMSCureSDK.clearAllData() // Ensure you have such a method or handle cache clearing appropriately
```

## Error Handling

The SDK logs errors internally (if `debugLogsEnabled` is true). For critical failures like initial configuration or authentication, monitor logs and implement appropriate fallback mechanisms in your application. Specific API calls like `sync` provide a success callback.

## Debugging

Enable detailed console logs from the SDK for diagnosing connection issues, sync problems, or cache behavior:
```kotlin
CMSCureSDK.debugLogsEnabled = true
```
This is generally `true` by default in the SDK during development. It is highly recommended to set this to `false` for your production/release builds to avoid excessive console output.

## License

CMSCure Android SDK is released under the **MIT** License. See `LICENSE.md` for details.

---

We hope CMSCureSDK helps you build amazing, dynamic Android applications! If you have any questions, feedback, or issues, please [open an issue on GitHub](https://github.com/cmscure/andriod-sdk/issues).
