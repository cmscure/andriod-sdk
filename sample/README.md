# CMSCure Android Sample Apps

Two runnable apps that demonstrate the CMSCure Android SDK in both **Jetpack Compose** and **XML Views**.

## Setup

1. Open the `sample/` folder in **Android Studio**
2. Replace credentials in both Application classes:

```
composeApp → ComposeSampleApp.kt
xmlApp     → XmlSampleApp.kt
```

```kotlin
private const val SAMPLE_PROJECT_ID    = "YOUR_PROJECT_ID"
private const val SAMPLE_API_KEY       = "YOUR_API_KEY"
private const val SAMPLE_PROJECT_SECRET = "YOUR_PROJECT_SECRET"
```

3. Sync Gradle and run on device/emulator

## Apps

### composeApp — Jetpack Compose

Demonstrates `CureText`, `CureImage`, `CureLayoutDirection`, `rememberCureString`, `rememberCureColor`, `cureDataStore`, `CureLanguageDisplay`, and `LanguageDirection`.

### xmlApp — XML Views

Demonstrates `CureTextView`, `CureImageView`, `CureColorView` widgets, View Binding, `CureLanguageDisplay`, `LanguageDirection`, and manual data store rendering.

## Dashboard Content Keys

Both apps use the same keys. Create this content in the CMSCure dashboard:

### Translations

| Screen | Key | Default Value |
|--------|-----|---------------|
| `home_screen` | `welcome_title` | Welcome! |
| `home_screen` | `welcome_subtitle` | Manage content, your way. |
| `home_screen` | `cta_button` | Get Started |
| `settings_screen` | `settings_title` | Settings |
| `settings_screen` | `language_label` | Language |
| `settings_screen` | `notifications_label` | Notifications |

### Colors

| Key | Default |
|-----|---------|
| `primary_color` | `#007AFF` |
| `accent_color` | `#FF9500` |
| `background_color` | `#F2F2F7` |

### Global Images

| Key | Purpose |
|-----|---------|
| `hero_banner` | Hero banner image |
| `app_logo` | App logo / icon |

### Data Store

| Identifier | Fields |
|-----------|--------|
| `feature_products` | `title` (String), `price` (Number), `image_url` (CTA/URL) |
