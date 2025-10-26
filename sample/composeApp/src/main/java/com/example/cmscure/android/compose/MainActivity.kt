package com.example.cmscure.android.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
//import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
//import androidx.compose.material3.menuAnchor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cmscure.sdk.CMSCureSDK
import com.cmscure.sdk.Cure
import com.cmscure.sdk.CureBackground
import com.cmscure.sdk.JSONValue
import com.cmscure.sdk.CureImage
import com.cmscure.sdk.CureText
import com.cmscure.sdk.cureDataStore
import com.cmscure.sdk.rememberCureColor
import com.cmscure.sdk.rememberCureString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = defaultColorScheme()) {
                ComposeSampleScreen()
            }
        }
    }
}

@Composable
private fun ComposeSampleScreen() {
    val scope = rememberCoroutineScope()
    val featuredProducts by cureDataStore(DataStoreKeys.FEATURED_PRODUCTS)
    val lastUpdateEvent by CMSCureSDK.contentUpdateFlow.collectAsState(initial = "None")

    val availableLanguages = remember { mutableStateListOf<String>() }
    var selectedLanguage by rememberSaveable { mutableStateOf(Cure.getLanguage().ifBlank { "en" }) }
    val languageLabel = rememberCureString(ContentKeys.LANGUAGE_LABEL, Tabs.MARKETING, "Language")

    LaunchedEffect(Unit) {
        CMSCureSDK.availableLanguages { fetched ->
            if (fetched.isNotEmpty()) {
                availableLanguages.clear()
                availableLanguages.addAll(fetched)
                if (selectedLanguage !in fetched) {
                    selectedLanguage = fetched.first()
                }
            }
            if (availableLanguages.isEmpty()) {
                availableLanguages.addAll(DEFAULT_LANGUAGES)
            }
        }
    }

    val accentColor = rememberCureColor(ColorKeys.ACCENT, Color(0xFF6750A4))
    val surfaceTint = rememberCureColor(ColorKeys.SURFACE, Color(0xFFF4E8FF))
    val badgeColor = rememberCureColor(ColorKeys.BADGE, Color(0xFFE8DEF8))

    val heroTitle = rememberCureString(ContentKeys.HERO_TITLE, Tabs.MARKETING, "Your marketing headline here")
    val heroSubtitle = rememberCureString(ContentKeys.HERO_SUBTITLE, Tabs.MARKETING, "Tell your story in multiple languages")
    val heroBody = rememberCureString(ContentKeys.HERO_BODY, Tabs.MARKETING, "Serve localized content instantly from CMSCure.")
    val heroSupport = rememberCureString(ContentKeys.HERO_SUPPORT, Tabs.MARKETING, "No redeploy required.")

    val featureOne = FeatureHighlight(
        title = rememberCureString(ContentKeys.VALUE_PROP_ONE_TITLE, Tabs.MARKETING, "Real-time updates"),
        body = rememberCureString(ContentKeys.VALUE_PROP_ONE_BODY, Tabs.MARKETING, "Push content changes instantly to every device."),
        iconKey = ImageKeys.FEATURE_ICON_GROWTH,
        defaultIconUrl = DEFAULT_ICON_GROWTH
    )
    val featureTwo = FeatureHighlight(
        title = rememberCureString(ContentKeys.VALUE_PROP_TWO_TITLE, Tabs.MARKETING, "Secure delivery"),
        body = rememberCureString(ContentKeys.VALUE_PROP_TWO_BODY, Tabs.MARKETING, "Ship safely with end-to-end encryption and auditing."),
        iconKey = ImageKeys.FEATURE_ICON_SECURITY,
        defaultIconUrl = DEFAULT_ICON_SECURITY
    )

    val featureThreeTitle = rememberCureString(ContentKeys.VALUE_PROP_THREE_TITLE, Tabs.MARKETING, "Collaborative workflow")
    val featureThreeBody = rememberCureString(ContentKeys.VALUE_PROP_THREE_BODY, Tabs.MARKETING, "Empower editors to localize content without developers.")

    val ctaTitle = rememberCureString(ContentKeys.CTA_TITLE, Tabs.MARKETING, "Get started")
    val ctaButtonLabel = rememberCureString(ContentKeys.CTA_BUTTON, Tabs.MARKETING, "Launch CMSCure")
    val ctaHint = rememberCureString(ContentKeys.CTA_HINT, Tabs.MARKETING, "Try switching languages to see the SDK react.")

    CureBackground(key = ColorKeys.SURFACE, default = Color.White) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                LanguagePicker(
                    label = languageLabel,
                    languages = availableLanguages,
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { language ->
                        selectedLanguage = language
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                Cure.setLanguage(language, force = true)
                            }
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = heroTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = heroSubtitle, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = heroBody, style = MaterialTheme.typography.bodyLarge)
                    }

                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            Cure.sync(Tabs.MARKETING)
                            Cure.sync(CMSCureSDK.COLORS_UPDATED)
                            Cure.syncStore(DataStoreKeys.FEATURED_PRODUCTS)
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh content")
                    }
                }
            }

            item {
                CureImage(
                    key = ImageKeys.HERO_BANNER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(MaterialTheme.shapes.medium),
                    defaultUrl = DEFAULT_BANNER_URL,
                    contentDescription = "Hero banner",
                    contentScale = ContentScale.Crop
                )
            }

            item {
                HighlightCard(
                    backgroundColor = surfaceTint,
                    badgeColor = badgeColor,
                    supportText = heroSupport
                ) {
                    Text(text = ctaTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { /* Hook navigation into your app */ }) {
                        Text(text = ctaButtonLabel, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = ctaHint, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                FeatureHighlightsRow(features = listOf(featureOne, featureTwo))
            }

            item {
                ValuePropCard(title = featureThreeTitle, body = featureThreeBody)
            }

            item {
                ColorPaletteRow(accentColor = accentColor, surfaceTint = surfaceTint, badgeColor = badgeColor)
            }

            item {
                CureImage(
                    key = ImageKeys.SECONDARY_BANNER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(MaterialTheme.shapes.medium),
                    defaultUrl = DEFAULT_SECONDARY_BANNER,
                    contentDescription = "Secondary banner",
                    contentScale = ContentScale.Crop
                )
            }

            item {
                SectionHeader(title = rememberCureString(ContentKeys.FEATURED_TITLE, Tabs.MARKETING, "Featured products"))
            }

            items(featuredProducts, key = { it.id }) { product ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = product.data["name"]?.localizedString ?: "Product name",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.data["description"]?.localizedString ?: "Product description",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rememberPrice(product.data["price"]),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Divider()
            }

            item {
                Text(
                    text = "Last update event: $lastUpdateEvent",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun rememberPrice(value: JSONValue?): String {
    val numeric = when (value) {
        is JSONValue.DoubleValue -> value.value
        is JSONValue.IntValue -> value.value.toDouble()
        is JSONValue.StringValue -> value.value.toDoubleOrNull()
        is JSONValue.LocalizedStringValue -> value.localizedString?.toDoubleOrNull()
        else -> null
    }
    return "Price: ${numeric ?: 0.0}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(
    label: String,
    languages: List<String>,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                value = selectedLanguage,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )

            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.uppercase()) },
                        onClick = {
                            expanded = false
                            onLanguageSelected(language)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(
    backgroundColor: Color,
    badgeColor: Color,
    supportText: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = MaterialTheme.shapes.medium)
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(badgeColor)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text = supportText, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun FeatureHighlightsRow(features: List<FeatureHighlight>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        features.forEach { feature ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CureImage(
                    key = feature.iconKey,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.small),
                    defaultUrl = feature.defaultIconUrl,
                    contentDescription = feature.title,
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = feature.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = feature.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ColorPaletteRow(accentColor: Color, surfaceTint: Color, badgeColor: Color) {
    val paletteTitle = rememberCureString(ContentKeys.COLOR_PALETTE_TITLE, Tabs.MARKETING, "Brand palette")
    val accentLabel = rememberCureString(ContentKeys.COLOR_ACCENT_LABEL, Tabs.MARKETING, "Accent")
    val surfaceLabel = rememberCureString(ContentKeys.COLOR_SURFACE_LABEL, Tabs.MARKETING, "Surface")
    val badgeLabel = rememberCureString(ContentKeys.COLOR_BADGE_LABEL, Tabs.MARKETING, "Badge")

    Column {
        Text(text = paletteTitle, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ColorChip(color = accentColor, label = accentLabel)
            ColorChip(color = surfaceTint, label = surfaceLabel)
            ColorChip(color = badgeColor, label = badgeLabel)
        }
    }
}

@Composable
private fun ColorChip(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(color)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
    }
}

@Composable
private fun ValuePropCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F2FF), shape = MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun defaultColorScheme() = lightColorScheme()

private object Tabs {
    const val MARKETING = "marketing"
}

private object ContentKeys {
    const val LANGUAGE_LABEL = "language_picker_label"
    const val HERO_TITLE = "hero_title"
    const val HERO_SUBTITLE = "hero_subtitle"
    const val HERO_BODY = "hero_body"
    const val HERO_SUPPORT = "hero_supporting_text"
    const val CTA_TITLE = "cta_headline"
    const val CTA_BUTTON = "cta_primary_action"
    const val CTA_HINT = "cta_hint"
    const val COLOR_PALETTE_TITLE = "color_palette_title"
    const val COLOR_ACCENT_LABEL = "color_accent_label"
    const val COLOR_SURFACE_LABEL = "color_surface_label"
    const val COLOR_BADGE_LABEL = "color_badge_label"
    const val VALUE_PROP_ONE_TITLE = "value_prop_one_title"
    const val VALUE_PROP_ONE_BODY = "value_prop_one_body"
    const val VALUE_PROP_TWO_TITLE = "value_prop_two_title"
    const val VALUE_PROP_TWO_BODY = "value_prop_two_body"
    const val VALUE_PROP_THREE_TITLE = "value_prop_three_title"
    const val VALUE_PROP_THREE_BODY = "value_prop_three_body"
    const val FEATURED_TITLE = "featured_products_title"
}

private object ImageKeys {
    const val HERO_BANNER = "hero_banner_primary"
    const val SECONDARY_BANNER = "customer_showcase_banner"
    const val FEATURE_ICON_GROWTH = "feature_icon_growth"
    const val FEATURE_ICON_SECURITY = "feature_icon_security"
}

private object ColorKeys {
    const val ACCENT = "accent_color"
    const val SURFACE = "surface_color"
    const val BADGE = "badge_color"
}

private object DataStoreKeys {
    const val FEATURED_PRODUCTS = "featured_productss"
}

private data class FeatureHighlight(
    val title: String,
    val body: String,
    val iconKey: String,
    val defaultIconUrl: String?
)

private val DEFAULT_LANGUAGES = listOf("en")

private const val DEFAULT_BANNER_URL = "https://images.unsplash.com/photo-1529333166437-7750a6dd5a70?auto=format&fit=crop&w=1350&q=80"
private const val DEFAULT_SECONDARY_BANNER = "https://images.unsplash.com/photo-1521737604893-d14cc237f11d?auto=format&fit=crop&w=1200&q=80"
private const val DEFAULT_ICON_GROWTH = "https://cdn-icons-png.flaticon.com/512/4221/4221419.png"
private const val DEFAULT_ICON_SECURITY = "https://cdn-icons-png.flaticon.com/512/3106/3106921.png"
