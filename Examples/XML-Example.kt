// XML-Example.kt
// A complete XML-based Activity example using CMSCure Android SDK.
//
// BEFORE RUNNING:
// 1. Create the content listed in README.md → Quick Start → Step 2
// 2. Replace YOUR_PROJECT_ID, YOUR_API_KEY, YOUR_PROJECT_SECRET below
//
// This example demonstrates:
// - SDK initialisation in an Application class
// - CureTextView, CureImageView, CureColorView XML widgets
// - Programmatic bind() calls for imperative setup
// - Data store rendering with DataStoreItem convenience methods
// - Language picker with CureLanguageDisplay flags & display names
// - RTL/LTR support with LanguageDirection

package com.example.cmscure.xml

import android.app.Application
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cmscure.sdk.*
import com.cmscure.sdk.ui.CureColorView
import com.cmscure.sdk.ui.CureImageView
import com.cmscure.sdk.ui.CureTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

class MainActivity : AppCompatActivity() {

    // SDK-aware views
    private lateinit var heroImage: CureImageView
    private lateinit var logoImage: CureImageView
    private lateinit var titleText: CureTextView
    private lateinit var subtitleText: CureTextView
    private lateinit var ctaButton: Button
    private lateinit var accentDivider: CureColorView
    private lateinit var directionBadge: TextView
    private lateinit var languageSpinner: Spinner
    private lateinit var productsContainer: LinearLayout

    private val languages = mutableListOf<String>()
    private lateinit var languageAdapter: ArrayAdapter<String>
    private var suppressSpinner = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
        bindSDKViews()
        setupLanguagePicker()
        observeUpdates()
        refreshAll()
    }

    // ── Build UI programmatically (no XML layout file needed) ──

    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }

        heroImage = CureImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200)
            ).apply { bottomMargin = dp(16) }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        logoImage = CureImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60)).apply { bottomMargin = dp(12) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        titleText = CureTextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        subtitleText = CureTextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            textSize = 16f
            setTextColor(0xFF757575.toInt())
        }

        ctaButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            text = "Get Started"
        }

        accentDivider = CureColorView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)
            ).apply { bottomMargin = dp(16) }
        }

        val productsHeader = TextView(this).apply {
            text = "Feature Products"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        productsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24) }
        }

        // Language section
        val langHeader = TextView(this).apply {
            text = "Language"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        directionBadge = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        languageSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(heroImage)
        root.addView(logoImage)
        root.addView(titleText)
        root.addView(subtitleText)
        root.addView(ctaButton)
        root.addView(accentDivider)
        root.addView(productsHeader)
        root.addView(productsContainer)
        root.addView(langHeader)
        root.addView(directionBadge)
        root.addView(languageSpinner)

        scroll.addView(root)
        setContentView(scroll)
    }

    // ── Bind SDK views ──

    private fun bindSDKViews() {
        heroImage.bindImage(key = "hero_banner")
        logoImage.bindImage(key = "app_logo")
        titleText.bind(
            key = "welcome_title",
            tab = "home_screen",
            default = "Welcome!",
            textColorKey = "primary_color"
        )
        subtitleText.bind(
            key = "welcome_subtitle",
            tab = "home_screen",
            default = "Manage content, your way."
        )
        accentDivider.bindColor(key = "accent_color", default = 0xFFFF9500.toInt())
    }

    // ── Language Picker ──

    private fun setupLanguagePicker() {
        languageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        languageSpinner.adapter = languageAdapter
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinner) return
                val code = languages.getOrNull(position) ?: return
                if (code != Cure.getLanguage()) {
                    Cure.setLanguage(code)
                    updateDirectionBadge()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        Cure.availableLanguages { codes ->
            languages.clear()
            languages.addAll(if (codes.isEmpty()) listOf("en") else codes)
            // Format as "🇺🇸  English" etc.
            val displayItems = languages.map { "${CureLanguageDisplay.flag(it)}  ${CureLanguageDisplay.displayName(it)}" }
            languageAdapter.clear()
            languageAdapter.addAll(displayItems)
            updateLanguageSelection(Cure.getLanguage())
        }

        updateDirectionBadge()
    }

    private fun updateLanguageSelection(code: String) {
        suppressSpinner = true
        val idx = languages.indexOf(code)
        if (idx >= 0) languageSpinner.setSelection(idx, false)
        suppressSpinner = false
    }

    private fun updateDirectionBadge() {
        val dir = Cure.languageDirection
        directionBadge.text = if (dir.isRTL) " RTL " else " LTR "
        directionBadge.setBackgroundColor(if (dir.isRTL) 0xFFFFF3E0.toInt() else 0xFFE3F2FD.toInt())
        directionBadge.setTextColor(if (dir.isRTL) 0xFFE65100.toInt() else 0xFF1565C0.toInt())
        window.decorView.layoutDirection = if (dir.isRTL) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    // ── Data Store rendering ──

    private fun renderProducts() {
        productsContainer.removeAllViews()
        val items = Cure.dataStoreRecords(forIdentifier = "feature_products")

        if (items.isEmpty()) {
            productsContainer.addView(TextView(this).apply {
                text = "No products available"
                textSize = 14f
                setTextColor(0xFF9E9E9E.toInt())
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }

        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setBackgroundColor(0xFFFFFFFF.toInt())
            }

            // Product image
            item.string("image_url")?.let { url ->
                val img = CureImageView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(60), dp(60)).apply { marginEnd = dp(12) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                img.bindImage(key = "", defaultUrl = url)
                row.addView(img)
            }

            // Product info
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            info.addView(TextView(this).apply {
                text = item.string("title") ?: "—"
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            info.addView(TextView(this).apply {
                text = "$${item.double("price") ?: 0.0}"
                textSize = 14f
                setTextColor(0xFF757575.toInt())
            })

            row.addView(info)

            // CTA button
            item.ctaURL?.let {
                row.addView(Button(this).apply {
                    text = "View"
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
            }

            productsContainer.addView(row)
        }
    }

    // ── CTA button ──

    private fun updateCtaButton() {
        ctaButton.text = Cure.translation(forKey = "cta_button", inTab = "home_screen").ifBlank { "Get Started" }
    }

    // ── Observe & Refresh ──

    private fun observeUpdates() {
        lifecycleScope.launch {
            CMSCureSDK.contentUpdateFlow.collectLatest { identifier ->
                updateCtaButton()
                updateDirectionBadge()
                if (identifier == "feature_products" || identifier == CMSCureSDK.ALL_SCREENS_UPDATED) {
                    renderProducts()
                }
            }
        }
    }

    private fun refreshAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            Cure.sync("home_screen")
            Cure.sync(CMSCureSDK.COLORS_UPDATED)
            Cure.sync(CMSCureSDK.IMAGES_UPDATED)
            Cure.syncStore("feature_products")
        }
    }

    // ── Helpers ──

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
