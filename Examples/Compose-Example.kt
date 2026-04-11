// Compose-Example.kt
// A complete Jetpack Compose example using CMSCure Android SDK.
//
// BEFORE RUNNING:
// 1. Create the content listed in README.md → Quick Start → Step 2
// 2. Replace YOUR_PROJECT_ID, YOUR_API_KEY, YOUR_PROJECT_SECRET below
//
// This example demonstrates:
// - SDK initialisation in an Application class
// - CureText, CureImage, CureBackground composables
// - rememberCureString, rememberCureColor, rememberCureImageUrl helpers
// - cureDataStore for dynamic lists with DataStoreItem convenience methods
// - Language picker with CureLanguageDisplay flags & display names
// - RTL/LTR support with CureLayoutDirection & LanguageDirection

package com.example.cmscure.compose

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cmscure.sdk.*

// ──────────────────────────────────────────────
// MARK: - Application
// ──────────────────────────────────────────────

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Cure.init(applicationContext)
        Cure.configure(
            context = applicationContext,
            projectId = "YOUR_PROJECT_ID",       // ← replace
            apiKey = "YOUR_API_KEY",             // ← replace
            projectSecret = "YOUR_PROJECT_SECRET", // ← replace
            enableAutoRealTimeUpdates = true
        )

        Cure.debugLogsEnabled = BuildConfig.DEBUG
    }
}

// ──────────────────────────────────────────────
// MARK: - Activity
// ──────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CureLayoutDirection {            // auto RTL / LTR
                    HomeScreen()
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// MARK: - Home Screen
// ──────────────────────────────────────────────

@Composable
private fun HomeScreen() {
    // Translations (auto-subscribe to real-time updates)
    val welcomeTitle    = rememberCureString("welcome_title", "home_screen", "Welcome!")
    val welcomeSubtitle = rememberCureString("welcome_subtitle", "home_screen", "Manage content, your way.")
    val ctaLabel        = rememberCureString("cta_button", "home_screen", "Get Started")

    // Colors
    val primaryColor    = rememberCureColor("primary_color", Color(0xFF007AFF))
    val accentColor     = rememberCureColor("accent_color", Color(0xFFFF9500))
    val backgroundColor = rememberCureColor("background_color", Color(0xFFF2F2F7))

    // Data Store
    val products by cureDataStore("feature_products")

    // Language state
    val availableLanguages = remember { mutableStateListOf<String>() }
    var selectedLanguage by rememberSaveable { mutableStateOf(Cure.getLanguage()) }

    LaunchedEffect(Unit) {
        Cure.availableLanguages { codes ->
            availableLanguages.clear()
            availableLanguages.addAll(codes)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Hero Section ──
        item {
            CureImage(
                key = "hero_banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop,
                contentDescription = "Hero banner"
            )
        }

        item {
            CureImage(
                key = "app_logo",
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Fit,
                contentDescription = "App logo"
            )
        }

        item {
            Text(
                text = welcomeTitle,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = primaryColor
            )
        }

        item {
            Text(
                text = welcomeSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }

        item {
            Button(
                onClick = { /* your action */ },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(ctaLabel, modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        // ── Products Section ──
        item {
            Text(
                text = "Feature Products",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = primaryColor
            )
        }

        if (products.isEmpty()) {
            item {
                Text(
                    "No products available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            }
        }

        items(products, key = { it.id }) { product ->
            ProductRow(product, accentColor)
        }

        // ── Language Section ──
        item {
            LanguageSection(
                languages = availableLanguages,
                selected = selectedLanguage,
                onSelect = { code ->
                    selectedLanguage = code
                    Cure.setLanguage(code)
                }
            )
        }

        // ── Direction badge ──
        item {
            val dir = Cure.languageDirection
            Text(
                text = if (dir.isRTL) " RTL " else " LTR ",
                style = MaterialTheme.typography.labelMedium,
                color = if (dir.isRTL) Color(0xFFE65100) else Color(0xFF1565C0),
                modifier = Modifier
                    .background(
                        if (dir.isRTL) Color(0xFFFFF3E0) else Color(0xFFE3F2FD),
                        MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ──────────────────────────────────────────────
// MARK: - Product Row
// ──────────────────────────────────────────────

@Composable
private fun ProductRow(product: DataStoreItem, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Product image from data store field
        product.string("image_url")?.let { url ->
            CureSDKImage(
                url = url,
                contentDescription = product.string("title"),
                modifier = Modifier
                    .size(60.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.string("title") ?: "—",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "$${product.double("price") ?: 0.0}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        // Optional CTA button
        product.ctaURL?.let { url ->
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri(url) }) {
                Text("View", color = accentColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ──────────────────────────────────────────────
// MARK: - Language Section
// ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSection(
    languages: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            "Language",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                value = "${CureLanguageDisplay.flag(selected)}  ${CureLanguageDisplay.displayName(selected)}",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { code ->
                    DropdownMenuItem(
                        text = { Text("${CureLanguageDisplay.flag(code)}  ${CureLanguageDisplay.displayName(code)}") },
                        onClick = {
                            expanded = false
                            onSelect(code)
                        }
                    )
                }
            }
        }
    }
}
