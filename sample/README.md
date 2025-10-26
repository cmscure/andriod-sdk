# CMSCure Android Sample Apps

Two runnable apps that demonstrate how to use the CMSCure Android SDK in both Jetpack Compose and traditional XML UIs.

## Project Layout

- `composeApp` – Kotlin/Compose implementation showcasing `CureText`, `CureImage`, `CureBackground`, the `rememberCure*` helpers, and reactive data-store rendering.
- `xmlApp` – View-based implementation using the `CureTextView`, `CureImageView`, and `CureColorView` widgets plus manual data-store rendering with view binding.

Both apps share the same fake placeholder credentials. Replace the values in:

```
composeApp/src/main/java/com/example/cmscure/android/compose/ComposeSampleApp.kt
xmlApp/src/main/java/com/example/cmscure/android/xml/XmlSampleApp.kt
```

```kotlin
private const val SAMPLE_PROJECT_ID = "YOUR_PROJECT_ID"
private const val SAMPLE_API_KEY = "YOUR_API_KEY"
private const val SAMPLE_PROJECT_SECRET = "YOUR_PROJECT_SECRET"
```

with credentials from the CMSCure dashboard before running.

## Building & Running

From the repository root:

```bash
./gradlew -p CMSCureAndroidSDK/sample composeApp:installDebug
./gradlew -p CMSCureAndroidSDK/sample xmlApp:installDebug
```

To run a release build of either sample:

```bash
./gradlew -p CMSCureAndroidSDK/sample composeApp:assembleRelease
./gradlew -p CMSCureAndroidSDK/sample xmlApp:assembleRelease
```

## What’s Demonstrated

### Compose Sample
- Global initialization and `Cure` alias usage.
- Top-of-screen language picker using `Cure.availableLanguages` + `Cure.setLanguage` (falls back to `en` if the project returns nothing).
- One-line UI helpers: `CureText`, `CureImage`, `CureBackground`.
- `rememberCureString` / `rememberCureColor` / `rememberCureImageUrl` state helpers.
- CTA card, icon highlights, dual banner images, three brand colors, and a data-driven product list.
- Manual refresh button calling `Cure.sync(...)` and `Cure.syncStore(...)`.

### XML Sample
- Auto-updating widgets (`CureTextView`, `CureImageView`, `CureColorView`).
- Language `Spinner` fed from `Cure.availableLanguages` with automatic `Cure.setLanguage` calls (defaulting to `en` when no languages are returned).
- View binding + `Cure.getStoreItems(...)` to render data-store content in a `LinearLayout`.
- Collecting `CMSCureSDK.contentUpdateFlow` to display update events, update CTA text, and refresh color/image widgets.
- Manual refresh button to trigger syncs.

Both samples target `minSdk = 24` and use the local project build of the SDK via an included build.

## Content Keys Reference

The Compose and XML demos both expect the following CMS content. Replace the defaults with your own values in the CMS dashboard.

### Tab: `marketing`

| Key | Purpose | Default value |
| --- | --- | --- |
| `language_picker_label` | Label above the language selector | `Language` |
| `hero_title` | Primary hero headline | `Your marketing headline here` |
| `hero_subtitle` | Supporting hero subtitle | `Tell your story in multiple languages` |
| `hero_body` | Hero body copy | `Serve localized content instantly from CMSCure.` |
| `hero_supporting_text` | CTA badge text | `No redeploy required.` |
| `cta_headline` | CTA card title | `Get started` |
| `cta_primary_action` | CTA button label | `Launch CMSCure` |
| `cta_hint` | CTA helper text | `Try switching languages to see the SDK react.` |
| `value_prop_one_title` | Highlight #1 title | `Real-time updates` |
| `value_prop_one_body` | Highlight #1 body | `Push content changes instantly to every device.` |
| `value_prop_two_title` | Highlight #2 title | `Secure delivery` |
| `value_prop_two_body` | Highlight #2 body | `Ship safely with end-to-end encryption and auditing.` |
| `value_prop_three_title` | Highlight #3 title | `Collaborative workflow` |
| `value_prop_three_body` | Highlight #3 body | `Empower editors to localize content without developers.` |
| `color_palette_title` | Brand palette section title | `Brand palette` |
| `color_accent_label` | Label beneath the accent color chip | `Accent` |
| `color_surface_label` | Label beneath the surface color chip | `Surface` |
| `color_badge_label` | Label beneath the badge color chip | `Badge` |
| `featured_products_title` | Data-store section header | `Featured products` |

### Global images

| Key | Purpose | Default URL |
| --- | --- | --- |
| `hero_banner_primary` | Primary marketing banner | `https://images.unsplash.com/photo-1529333166437-7750a6dd5a70?auto=format&fit=crop&w=1350&q=80` |
| `customer_showcase_banner` | Secondary banner | `https://images.unsplash.com/photo-1521737604893-d14cc237f11d?auto=format&fit=crop&w=1200&q=80` |
| `feature_icon_growth` | Highlight icon for growth | `https://cdn-icons-png.flaticon.com/512/4221/4221419.png` |
| `feature_icon_security` | Highlight icon for security | `https://cdn-icons-png.flaticon.com/512/3106/3106921.png` |

### Colors (`__colors__` tab)

| Key | Purpose | Default value |
| --- | --- | --- |
| `accent_color` | Used for hero title and accent elements | `#6750A4` |
| `surface_color` | Used for screen background | `#F4E8FF` |
| `badge_color` | Used for CTA badge + chips | `#E8DEF8` |

### Data Store

- Identifier: `featured_products`
- Expected fields (per item):
  - `name` (Localized String)
  - `description` (Localized String)
  - `price` (Number)
