# CMSCure Android SDK

[![JitPack](https://jitpack.io/v/cmscure/andriod-sdk.svg)](https://jitpack.io/#cmscure/andriod-sdk)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)

The official Android SDK for integrating your Kotlin-based Android application with the CMSCure platform. This SDK enables you to manage and deliver dynamic content—text, colors, images, and structured data—with real-time updates and a robust offline-first caching system.

## Features

-   **Live Content Management:** Fetch and display translations, global color schemes, and image URLs from your CMSCure dashboard.
-   **Dynamic Lists via Data Stores:** Effortlessly manage and display structured content like product catalogs, news feeds, or feature lists using `cureDataStore`.
-   **Full Localization Support:** Built-in support for multiple languages, including for complex, nested data within Data Stores.
-   **Reactive Jetpack Compose Integration:** A suite of `@Composable` functions (`cureString`, `cureColor`, `cureImage`, `cureDataStore`) automatically keeps your UI in sync with your content.
-   **Real-time Updates:** Utilizes a persistent WebSocket connection to receive live content updates, propagated through a Kotlin `SharedFlow`.
-   **Robust Offline Caching:** Persists all fetched content to disk, ensuring your app is fully functional without a network connection.
-   **Automatic Image Caching:** Seamlessly caches all remote images using **Coil**. The SDK provides a simple `CureSDKImage` composable for easy display.
-   **Simple Configuration:** Get up and running with just your Project ID and API Key.

## Requirements

-   Android API Level 26+ (Oreo)
-   Kotlin 1.8+
-   Jetpack Compose
-   An active CMSCure project and its associated credentials.

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
    implementation("com.github.cmscure:andriod-sdk:1.0.7") // Use the latest version

    // Add Coil for displaying images. The SDK's helpers depend on it.
    implementation("io.coil-kt:coil-compose:2.6.0")
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
import com.cmscure.sdk.CMSCureSDK

class YourApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Step 1: Initialize the SDK (must be called before configure)
        CMSCureSDK.init(applicationContext)

        // Step 2: Configure the SDK with your project credentials
        CMSCureSDK.configure(
            context = applicationContext,
            projectId = "YOUR_PROJECT_ID",
            apiKey = "YOUR_API_KEY"
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

## Jetpack Compose Usage (Recommended)

The easiest way to use the SDK is with its reactive `@Composable` functions. They return a `State<T>` object that automatically updates your UI when content changes in the CMS.

### Displaying Text, Colors, and Images

* `cureString(key, tab, default)`: For a piece of text.
* `cureColor(key, default)`: For a global color.
* `cureImage(key)`: For a global image asset URL.
* `CureSDKImage(url, ...)`: A ready-to-use Composable for displaying images with automatic caching.

### Displaying Dynamic Lists with Data Stores

Use `cureDataStore(apiIdentifier)` to get a reactive list of structured data.
```kotlin
@Composable
fun ProductList() {
    val products by cureDataStore("featured_products")

    LazyColumn {
        items(products) { product ->
            // Access localized and non-localized fields
            Text(product.data["name"]?.localizedString ?: "N/A")
            Text("Price: ${product.data["price"]?.doubleValue ?: 0.0}")
        }
    }
}
```

## Comprehensive Compose Example
```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.cmscure.sdk.*

@Composable
fun StoreScreen() {
    // 1. Define reactive properties for your content
    val screenTitle by cureString("store_title", tab = "store_screen", default = "Our Store")
    val accentColor by cureColor("accent_color", default = Color.Blue)
    val products by cureDataStore("featured_products")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = screenTitle,
            style = MaterialTheme.typography.headlineLarge,
            color = accentColor,
            modifier = Modifier.padding(16.dp)
        )

        // 2. Display the dynamic list from the Data Store
        if (products.isEmpty()) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products) { product ->
                    ProductCard(product = product)
                }
            }
        }
    }
}

@Composable
fun ProductCard(product: DataStoreItem) {
    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column {
            // 3. Use the SDK's image composable for easy, cached images
            CureSDKImage(
                url = product.data["image_url"]?.stringValue,
                contentDescription = "Image of ${product.data["name"]?.localizedString ?: "product"}",
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Crop
            )

            Column(Modifier.padding(16.dp)) {
                // 4. Access localized and simple values from the item's data
                Text(
                    text = product.data["name"]?.localizedString ?: "Unnamed Product",
                    style = MaterialTheme.typography.titleMedium
                )

                // Use Kotlin's 'let' for safe handling of the optional price
                product.data["price"]?.doubleValue?.let { price ->
                    Text(
                        text = String.format("$%.2f", price),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
```

## Traditional Views & Manual Access
For XML-based UIs or other architectures, you can fetch data directly and observe the `contentUpdateFlow` for changes.

### Observing Updates
In an Activity or Fragment, collect the flow within a lifecycle-aware coroutine scope.
```kotlin
lifecycleScope.launch {
    CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
        Log.d("MyApp", "Content updated for: $updatedIdentifier. Refreshing UI.")
        // Manually update your TextViews, ImageViews, etc.
        updateAllUI()
    }
}
```
### Manual Fetching
* `CMSCureSDK.translation(forKey: "key", inTab: "...")`
* `CMSCureSDK.colorValue(forKey: "key")`
* `CMSCureSDK.imageURL(forKey: "key")`
* `CMSCureSDK.getStoreItems(forIdentifier: "...")`
* `CMSCureSDK.syncStore(apiIdentifier: "...") { ... }`

## License
CMSCure Android SDK is released under the MIT License. See `LICENSE.md` for details.