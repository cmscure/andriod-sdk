# CMSCure Android SDK

[![JitPack](https://jitpack.io/v/cmscure/andriod-sdk.svg)](https://jitpack.io/#cmscure/andriod-sdk)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)

The official Android SDK for **CMSCure** — deliver translations, colors, images, and structured data to your native Android app with powerful offline caching, intelligent real-time updates, and full parity with the iOS SDK.

## Features

| Feature | Details |
|---|---|
| **Live Content** | Translations, colors, global images, and data stores |
| **Offline-first** | Automatic disk persistence with background refresh |
| **Real-time Updates** | Socket-powered with smart auto-subscription |
| **Jetpack Compose** | Drop-in `CureText`, `CureImage`, `CureBackground`, `CureLayoutDirection` composables |
| **XML Views** | `CureTextView`, `CureImageView`, `CureColorView` custom widgets |
| **RTL / LTR Support** | `LanguageDirection` enum with automatic detection for 11+ RTL languages |
| **Language Display** | `CureLanguageDisplay` with flag emojis and display names for 80+ languages |
| **Data Stores** | Typed access via `JSONValue` with convenience `.string()`, `.double()`, `.ctaURL` helpers |
| **Image Caching** | Coil-powered, shared across Compose and XML layers |
| **Cross-platform API** | `Cure` alias and naming conventions match the iOS SDK |

## Requirements

| Requirement | Minimum |
|---|---|
| Android API | 24+ |
| Kotlin | 1.9+ |
| Jetpack Compose | 1.5+ *(for Compose helpers)* |
| Coil | 2.5+ |

## Installation

### 1. Add JitPack repository

In your **root** `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

### 2. Add the dependency

In your **app** `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.cmscure:andriod-sdk:1.0.12") // latest tag
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

### 3. Internet permission

In `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Quick Start

### 1. Initialize

Initialize the SDK once in your `Application` class. A `typealias Cure = CMSCureSDK` is available for cross-platform parity with iOS.

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
            enableAutoRealTimeUpdates = true
        )

        Cure.debugLogsEnabled = BuildConfig.DEBUG
    }
}
```

### 2. Create Content in the CMSCure Dashboard

Create a new project and add the following content. These keys and values are used by the two example files in the [`Examples/`](Examples/) folder ([Compose](Examples/Compose-Example.kt) and [XML](Examples/XML-Example.kt)), so adding them lets you run the examples immediately:

| Type | Screen / Store | Key | Sample Value |
|------|---------------|-----|-------------|
| Translation | `home_screen` | `welcome_title` | Welcome! |
| Translation | `home_screen` | `welcome_subtitle` | Manage content, your way. |
| Translation | `home_screen` | `cta_button` | Get Started |
| Translation | `settings_screen` | `settings_title` | Settings |
| Translation | `settings_screen` | `language_label` | Language |
| Translation | `settings_screen` | `notifications_label` | Notifications |
| Color | — | `primary_color` | #007AFF |
| Color | — | `accent_color` | #FF9500 |
| Color | — | `background_color` | #F2F2F7 |
| Image | — | `app_logo` | *(upload your logo)* |
| Image | — | `hero_banner` | *(upload a banner)* |
| Data Store | `feature_products` | — | Store Name: **Feature Products**, Fields: `title` (String), `price` (Number), `image_url` (String) |

> **Replace** `YOUR_PROJECT_ID`, `YOUR_API_KEY`, and `YOUR_PROJECT_SECRET` with the credentials from your CMSCure dashboard.

---

## Jetpack Compose

### One-line composables

```kotlin
@Composable
fun HomeScreen() {
    CureLayoutDirection {                          // automatic RTL / LTR
        CureBackground(key = "background_color") {
            Column(modifier = Modifier.padding(24.dp)) {
                CureText(
                    key = "welcome_title",
                    tab = "home_screen",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(12.dp))
                CureImage(
                    key = "hero_banner",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
```

### `remember*` helpers for direct access

```kotlin
val title  = rememberCureString("welcome_title", tab = "home_screen", default = "Welcome!")
val accent = rememberCureColor("accent_color", default = MaterialTheme.colorScheme.primary)
val heroUrl = rememberCureImageUrl("hero_banner")
```

### Data stores in Compose

```kotlin
val products by cureDataStore("feature_products")

LazyColumn {
    items(products) { item ->
        Text(item.string("title").orEmpty())
        Text("$${item.double("price") ?: 0.0}")
    }
}
```

---

## XML Views

Drop custom views straight into your layouts:

```xml
<com.cmscure.sdk.ui.CureTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:cureKey="welcome_title"
    app:cureTab="home_screen"
    app:cureDefault="Welcome!"
    app:cureTextColorKey="primary_color" />

<com.cmscure.sdk.ui.CureImageView
    android:layout_width="match_parent"
    android:layout_height="180dp"
    android:scaleType="centerCrop"
    app:cureImageKey="hero_banner"
    app:curePlaceholder="@drawable/placeholder" />

<com.cmscure.sdk.ui.CureColorView
    android:layout_width="match_parent"
    android:layout_height="4dp"
    app:cureColorKey="accent_color" />
```

Every view also exposes `bind(...)` / `unbind()` methods for programmatic setup.

---

## Core API

All low-level calls remain available via `CMSCureSDK` (or `Cure`):

```kotlin
// Translations
val title    = Cure.translation(forKey = "welcome_title", inTab = "home_screen")

// Colors
val colorHex = Cure.colorValue(forKey = "primary_color")

// Images
val imageUrl = Cure.imageURL(forKey = "hero_banner")

// Data Stores
val items    = Cure.getStoreItems(forIdentifier = "feature_products")
// or the iOS-style alias:
val records  = Cure.dataStoreRecords(forIdentifier = "feature_products")
```

### Language management

```kotlin
Cure.setLanguage("fr")
Cure.getLanguage()                    // "fr"
Cure.languageDirection                // LanguageDirection.LTR
Cure.languageDirection.isRTL          // false

Cure.availableLanguages { languages ->
    // e.g. ["en", "fr", "ar", "ur"]
}
```

### Language display helpers

```kotlin
CureLanguageDisplay.flag("ar")         // "🇸🇦"
CureLanguageDisplay.displayName("ar")  // "Arabic"
CureLanguageDisplay.flag("en-gb")      // "🇬🇧"
CureLanguageDisplay.displayName("zh")  // "Chinese"
```

### RTL / LTR direction

```kotlin
LanguageDirection.direction("ar")      // LanguageDirection.RTL
LanguageDirection.direction("en")      // LanguageDirection.LTR

// In Compose — wraps content with correct LayoutDirection:
CureLayoutDirection { /* your composable tree */ }

// In XML / Activity:
val dir = Cure.languageDirection
if (dir.isRTL) {
    window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
}
```

### Auto real-time utilities

```kotlin
Cure.isAutoRealTimeUpdatesEnabled()
Cure.getAutoSubscribedScreens()
Cure.getAutoSubscribedDataStores()
Cure.isColorsAutoSubscribed()
Cure.isGlobalImagesAutoSubscribed()
```

### Manual syncs

```kotlin
Cure.sync("home_screen")
Cure.sync(CMSCureSDK.COLORS_UPDATED)
Cure.sync(CMSCureSDK.IMAGES_UPDATED)
Cure.syncStore("feature_products")
```

---

## Data Stores

Each `DataStoreItem` provides typed access via convenience methods and the `JSONValue` sealed class:

```kotlin
val products = Cure.getStoreItems("feature_products")

products.forEach { item ->
    val name  = item.string("title")       // localized string
    val price = item.double("price")       // Double?
    val cta   = item.ctaURL                // String? from "cta_url" field

    // Or access raw JSONValue:
    val raw = item.data["title"]?.localizedString
}
```

---

## iOS / Android API Parity

| iOS | Android | Notes |
|---|---|---|
| `Cure.shared.configure(...)` | `Cure.configure(...)` | Android uses a Kotlin `object` singleton |
| `Cure.shared.translation(for:inTab:)` | `Cure.translation(forKey:inTab:)` | Same semantics |
| `Cure.shared.colorValue(for:)` | `Cure.colorValue(forKey:)` | Returns hex string |
| `Cure.shared.imageURL(forKey:)` | `Cure.imageURL(forKey:)` | Returns URL string |
| `Cure.shared.dataStoreRecords(for:)` | `Cure.dataStoreRecords(forIdentifier:)` | Returns `List<DataStoreItem>` |
| `CureString(key, tab:)` | `rememberCureString(key, tab)` | Compose `@Composable` |
| `CureColor(key)` | `rememberCureColor(key)` | Returns Compose `Color` |
| `CureLanguageDisplay.flag(for:)` | `CureLanguageDisplay.flag(code)` | Flag emoji |
| `Cure.shared.languageDirection` | `Cure.languageDirection` | `LanguageDirection.LTR/RTL` |

---

## Troubleshooting

- Call `Cure.init(...)` **once** in your `Application.onCreate()` before `configure()`.
- Set `enableAutoRealTimeUpdates = false` if you prefer manual subscription control.
- Use `Cure.debugLogsEnabled = true` during development to trace network and cache events.
- Ensure the JitPack repository is in your **root** `settings.gradle.kts`, not only the app module.

## Examples

Complete working examples are available in the `Examples/` directory:

- **[Compose-Example.kt](Examples/Compose-Example.kt)** — Full Jetpack Compose sample with translations, colors, images, data store, language picker, and RTL support.
- **[XML-Example.kt](Examples/XML-Example.kt)** — Full XML-based Activity with `CureTextView`, `CureImageView`, `CureColorView`, language spinner, and data store rendering.

## License

Released under the [MIT License](LICENSE.md).
