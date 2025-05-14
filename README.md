# CMSCure Android SDK

The official Android SDK for integrating with the CMSCure content management platform.  
This SDK enables real-time content updates, dynamic localization, remote asset delivery, and secure project-based configuration.

---

## 📦 Installation

### 1. Add JitPack to your root `settings.gradle.kts`:

```
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri(“https://jitpack.io”) }
  }
}
```

### 2. Add the SDK dependency in `app/build.gradle.kts`:

```implementation(“com.github.cmscure:andriod-sdk:1.0.2”)```

---

## 🚀 Getting Started

### Initialize the SDK (e.g. in `MyApplication.kt`):

```
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
```

### Fetch content dynamically:

```
val title = CMSCureSDK.translation(“welcome_title”)
val colorHex = CMSCureSDK.color(“primaryBackground”)
val imageUrl = CMSCureSDK.image(“onboarding_banner”)
```

---

## 🛠️ Requirements

- minSdk: 24
- targetSdk: 35
- Java: Compiles with Java 11, runtime requires Java 17+
- Kotlin: Compatible with Kotlin 1.9+

---

## 📡 Features

- 🔄 Realtime WebSocket updates
- 🌐 Multi-language content localization
- 🎨 Remote color value support
- 🖼️ Remote image asset delivery
- 🔐 API key + project isolation
- 💾 Local cache with auto fallback

---

## 📄 License

Licensed under the Apache License 2.0  
http://www.apache.org/licenses/LICENSE-2.0.txt

---

## 💬 Support

For issues or bug reports:  
https://github.com/cmscure/andriod-sdk/issues

Enterprise inquiries:  
info@reignsol.com
