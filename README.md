# CMSCure Android SDK

[![JitPack](https://jitpack.io/v/cmscure/andriod-sdk.svg)](https://jitpack.io/#cmscure/andriod-sdk)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)

The official Android SDK for integrating native apps with **CMSCure**. Deliver translations, colors, images, and structured data with powerful offline caching and intelligent real-time updates that now mirror the latest iOS implementation.

## What's New

- ✅ **Parity with iOS** – `configure()` now accepts the `projectSecret` and an `enableAutoRealTimeUpdates` flag (default `true`).
- ⚡ **Auto real-time everywhere** – `translation`, `colorValue`, `imageURL`, and `getStoreItems` now auto subscribe to live updates while remaining 100% backward compatible.
- 🎯 **One-liners for UI** – Drop-in Jetpack Compose helpers (`CureText`, `CureImage`, `CureBackground`) and XML widgets (`CureTextView`, `CureImageView`, `CureColorView`) make rendering CMS content effortless.
- 🧩 **Developer-quality ergonomics** – New `rememberCureString`, `rememberCureColor`, and `rememberCureImageUrl` helpers plus a `Cure` alias for consistent cross-platform code.

## Features

- **Live content management** for text, colors, global images, and data stores.
- **Offline-first cache** with automatic disk persistence and background refresh.
- **Socket-powered real-time updates** with smart auto-subscription that you can toggle per project.
- **Composable & XML-ready UI kit** for instant drop-in usage.
- **Coil-powered image caching** shared across Compose and XML layers.
- **Data store helpers** for rendering dynamic lists with localized values.

## Requirements

- Android API level 26+
- Kotlin 1.9+
- Jetpack Compose 1.5+ (for Compose helpers)
- Coil 2.5+

## Installation

Add JitPack to your root `settings.gradle(.kts)` and include the dependency in your app module:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.cmscure:andriod-sdk:1.0.12") // Use latest tag
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

Enable internet permission in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Quick Start

Initialize once in your `Application` class. A `typealias Cure = CMSCureSDK` is available for parity with iOS.

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Cure.init(applicationContext)
        Cure.configure(
            context = applicationContext,
            projectId = "YOUR_PROJECT_ID",
            apiKey = "YOUR_API_KEY",
            projectSecret = "YOUR_PROJECT_SECRET",
            enableAutoRealTimeUpdates = true // disable if you prefer manual subscriptions
        )

        Cure.debugLogsEnabled = BuildConfig.DEBUG
    }
}
```

## Ultra-fast Jetpack Compose integration

Render CMS-driven UI in one line:

```kotlin
@Composable
fun DashboardHeader() {
    CureBackground(key = "accent_background") {
        Column(modifier = Modifier.padding(24.dp)) {
            CureText(key = "dashboard_title", tab = "dashboard", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            CureImage(key = "hero_banner", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
        }
    }
}
```

Need direct access to the values? Use the `remember*` helpers:

```kotlin
val title = rememberCureString("dashboard_title", tab = "dashboard", default = "Dashboard")
val accent = rememberCureColor("accent_background", default = MaterialTheme.colorScheme.primary)
val heroUrl = rememberCureImageUrl("hero_banner")
```

For structured data stores:

```kotlin
val products by cureDataStore("featured_products")

LazyColumn {
    items(products) { item ->
        Text(item.data["title"]?.localizedString.orEmpty())
    }
}
```

## XML views in one line

Drop custom views straight into layouts:

```xml
<com.cmscure.sdk.ui.CureTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:cureKey="cta_headline"
    app:cureTab="marketing"
    app:cureDefault="Get Started"
    app:cureTextColorKey="primary_text" />

<com.cmscure.sdk.ui.CureImageView
    android:layout_width="match_parent"
    android:layout_height="180dp"
    android:scaleType="centerCrop"
    app:cureImageKey="marketing_banner"
    app:curePlaceholder="@drawable/placeholder" />

<com.cmscure.sdk.ui.CureColorView
    android:layout_width="match_parent"
    android:layout_height="4dp"
    app:cureColorKey="accent_background" />
```

Every view exposes `bind(...)` methods if you prefer imperative setup.

## Manual API surface

All low-level calls remain available via `CMSCureSDK` (or `Cure`):

```kotlin
val title = Cure.translation(forKey = "dashboard_title", inTab = "dashboard")
val colorHex = Cure.colorValue(forKey = "accent_background")
val imageUrl = Cure.imageURL(forKey = "hero_banner")
val store = Cure.getStoreItems(forIdentifier = "featured_products")

Cure.setLanguage("fr")
Cure.availableLanguages { languages ->
    // use languages
}
```

### Auto real-time utilities

```kotlin
val enabled = Cure.isAutoRealTimeUpdatesEnabled()
val tabs = Cure.getAutoSubscribedScreens()
val stores = Cure.getAutoSubscribedDataStores()
val colorsActive = Cure.isColorsAutoSubscribed()
val imagesActive = Cure.isGlobalImagesAutoSubscribed()
```

### Manual syncs

```kotlin
Cure.sync("dashboard")
Cure.sync(CMSCureSDK.COLORS_UPDATED)
Cure.syncStore("featured_products")
```

## Data stores

Each item provides typed access via the `JSONValue` helpers:

```kotlin
val products = Cure.getStoreItems("featured_products")
products.forEach { item ->
    val name = item.data["name"]?.localizedString
    val price = item.data["price"]?.doubleValue
}
```

## Troubleshooting

- Ensure `Cure.init(...)` is called once before `configure`.
- Keep `enableAutoRealTimeUpdates = false` if you prefer manual control.
- Use `Cure.debugLogsEnabled = true` during development to trace network and cache events.

## License

Released under the [MIT License](LICENSE.md).
