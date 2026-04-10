package com.example.cmscure.android.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import coil.compose.AsyncImage
import com.cmscure.sdk.CMSCureSDK
import com.cmscure.sdk.Cure
import com.cmscure.sdk.CureImage
import com.cmscure.sdk.CureLayoutDirection
import com.cmscure.sdk.CureText
import com.cmscure.sdk.CureLanguageDisplay
import com.cmscure.sdk.LanguageDirection
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
            MaterialTheme {
                CureLayoutDirection {
                    SampleApp()
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
// Main Screen
// ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleApp() {
    val scope = rememberCoroutineScope()

    // ── Translations (home_screen tab) ──
    val welcomeTitle = rememberCureString("welcome_title", "home_screen", "Welcome!")
    val welcomeSubtitle = rememberCureString("welcome_subtitle", "home_screen", "Manage content, your way.")
    val ctaButton = rememberCureString("cta_button", "home_screen", "Get Started")

    // ── Colors ──
    val primaryColor = rememberCureColor("primary_color", Color(0xFF007AFF))
    val accentColor = rememberCureColor("accent_color", Color(0xFFFF9500))
    val backgroundColor = rememberCureColor("background_color", Color(0xFFF2F2F7))

    // ── Data Store ──
    val products by cureDataStore("feature_products")

    // ── Language picker state ──
    val availableLanguages = remember { mutableStateListOf<String>() }
    var selectedLanguage by rememberSaveable { mutableStateOf(Cure.getLanguage().ifBlank { "en" }) }

    // ── Real-time updates ──
    val lastUpdate by CMSCureSDK.contentUpdateFlow.collectAsState(initial = "—")

    LaunchedEffect(Unit) {
        CMSCureSDK.availableLanguages { fetched ->
            availableLanguages.clear()
            availableLanguages.addAll(fetched.ifEmpty { listOf("en") })
            if (selectedLanguage !in availableLanguages) {
                selectedLanguage = availableLanguages.first()
            }
        }
    }

    val direction = LanguageDirection.direction(selectedLanguage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CMSCure Compose") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryColor,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            Cure.sync("home_screen")
                            Cure.sync(CMSCureSDK.COLORS_UPDATED)
                            Cure.sync(CMSCureSDK.IMAGES_UPDATED)
                            Cure.syncStore("feature_products")
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Hero Banner (global image) ──
            item {
                CureImage(
                    key = "hero_banner",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentDescription = "Hero banner",
                    contentScale = ContentScale.Crop
                )
            }

            // ── App Logo (global image) ──
            item {
                CureImage(
                    key = "app_logo",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentDescription = "App logo",
                    contentScale = ContentScale.Fit
                )
            }

            // ── Subtitle ──
            item {
                Text(
                    text = welcomeSubtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }

            // ── CTA Button ──
            item {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = ctaButton.ifBlank { "Get Started" },
                        modifier = Modifier.padding(vertical = 6.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Data Store: Feature Products ──
            item {
                Text(
                    "Feature Products",
                    style = MaterialTheme.typography.titleLarge,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Divider()
            }

            if (products.isEmpty()) {
                item {
                    Text(
                        "No products available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }

            items(products, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Product image from data store "image_url" field
                        val imageUrl = item.string("image_url")
                        if (!imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = item.string("title"),
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.string("title") ?: "—",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            val price = item.double("price")
                            if (price != null) {
                                Text(
                                    text = "$${String.format("%.2f", price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        // CTA link button
                        val ctaUrl = item.ctaURL
                        if (!ctaUrl.isNullOrBlank()) {
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) {
                                Text("View", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            // ── Language Section ──
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Language",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (direction.isRTL) "RTL" else "LTR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (direction.isRTL) accentColor else primaryColor,
                            modifier = Modifier
                                .background(
                                    if (direction.isRTL) accentColor.copy(alpha = 0.15f) else primaryColor.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LanguagePicker(
                        label = "",
                        languages = availableLanguages,
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { lang ->
                            selectedLanguage = lang
                            scope.launch {
                                withContext(Dispatchers.IO) { Cure.setLanguage(lang, force = true) }
                            }
                        }
                    )
                }
            }

            // ── Color Palette ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Color Palette", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ColorChip(primaryColor, "primary_color")
                            ColorChip(accentColor, "accent_color")
                            ColorChip(backgroundColor, "background_color")
                        }
                    }
                }
            }

            // ── Last Update Event ──
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last update: $lastUpdate",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
// Language Picker
// ────────────────────────────────────────────────────────────────

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
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                value = "${CureLanguageDisplay.flag(selectedLanguage)} ${CureLanguageDisplay.displayName(selectedLanguage)}",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = {
                            Text("${CureLanguageDisplay.flag(lang)} ${CureLanguageDisplay.displayName(lang)}")
                        },
                        onClick = {
                            expanded = false
                            onLanguageSelected(lang)
                        }
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
// Color Chip
// ────────────────────────────────────────────────────────────────

@Composable
private fun ColorChip(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(color)
        )
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
