# CMSCure Android SDK ![JitPack](https://jitpack.io/v/cmscure/andriod-sdk.svg) ![License](https://img.shields.io/badge/license-Apache%202.0-blue)

The official Android SDK for integrating with the [CMSCure](https://app.cmscure.com) content management platform.  
This SDK enables real-time content updates, multi-language localization, remote asset delivery (text, images, colors), and secure project-based configuration.

---

## 📦 Installation

### 1. Add JitPack to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add the SDK dependency in `app/build.gradle.kts`:

```kotlin
implementation("com.github.cmscure:andriod-sdk:1.0.2")
```

---

## 🚀 Getting Started

### Initialize the SDK (e.g. in `MyApplication.kt`)

```kotlin
import com.cmscure.sdk.CMSCureSDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        CMSCureSDK.init(
            context = this,
            apiKey = "your-api-key",
            projectId = "your-project-id",
            defaultLanguage = "en"
        )
    }
}
```

### Fetch content in your app

```kotlin
val title = CMSCureSDK.translation("welcome_title")
val colorHex = CMSCureSDK.color("primaryBackground")
val imageUrl = CMSCureSDK.image("onboarding_banner")
```

---

## 🛠 Requirements

- **minSdk:** 24  
- **targetSdk:** 35  
- **Java:** 11 (build), 17+ (Gradle runtime)  
- **Kotlin:** 1.9+

---

## ✨ Features

- 🔄 Real-time WebSocket sync
- 🌍 Multi-language translation
- 🎨 Remote color styling (hex support)
- 🖼️ Remote image asset URLs
- 🔐 API Key + Project ID isolation
- 💾 Local caching + fallback system

---

## 📄 License

This SDK is licensed under the Apache License 2.0.  
[Read Full License](http://www.apache.org/licenses/LICENSE-2.0.txt)

---

## 💬 Support

- Questions / Issues: [GitHub Issues](https://github.com/cmscure/andriod-sdk/issues)  
- Contact us: **info@reignsol.com**
