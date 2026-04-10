package com.example.cmscure.android.xml

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.RoundedCornersTransformation
import com.cmscure.sdk.CMSCureSDK
import com.cmscure.sdk.Cure
import com.cmscure.sdk.CureLanguageDisplay
import com.cmscure.sdk.LanguageDirection
import com.example.cmscure.android.xml.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var languageAdapter: ArrayAdapter<String>
    private val languageCodes = mutableListOf<String>()
    private val languageDisplayNames = mutableListOf<String>()
    private var suppressSpinner = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguagePicker()
        setupListeners()
        renderProducts()
        refreshAll()
        updateCta()
        updateDirection(Cure.getLanguage().ifBlank { "en" })
    }

    private fun setupListeners() {
        binding.buttonRefresh.setOnClickListener { refreshAll() }

        lifecycleScope.launch {
            CMSCureSDK.contentUpdateFlow.collectLatest { identifier ->
                binding.textLastUpdate.text = "Last update: $identifier"
                updateCta()
                if (!suppressSpinner) updateLanguageSelection(Cure.getLanguage())
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

    // ── Language Picker ──

    private fun setupLanguagePicker() {
        languageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageDisplayNames)
        binding.spinnerLanguages.adapter = languageAdapter

        binding.spinnerLanguages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinner) return
                val code = languageCodes.getOrNull(position) ?: return
                if (code != Cure.getLanguage()) {
                    updateDirection(code)
                    lifecycleScope.launch(Dispatchers.IO) { Cure.setLanguage(code) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        CMSCureSDK.availableLanguages { fetched ->
            val codes = fetched.ifEmpty { listOf("en") }
            languageCodes.clear()
            languageCodes.addAll(codes)
            languageDisplayNames.clear()
            languageDisplayNames.addAll(codes.map { code ->
                "${CureLanguageDisplay.flag(code)} ${CureLanguageDisplay.displayName(code)}"
            })
            languageAdapter.notifyDataSetChanged()
            updateLanguageSelection(Cure.getLanguage().ifBlank { codes.first() })
        }
    }

    private fun updateLanguageSelection(language: String) {
        suppressSpinner = true
        val index = languageCodes.indexOf(language)
        if (index >= 0) binding.spinnerLanguages.setSelection(index, false)
        suppressSpinner = false
    }

    private fun updateDirection(code: String) {
        val dir = LanguageDirection.direction(code)
        binding.textDirection.text = if (dir.isRTL) "RTL" else "LTR"
        binding.root.layoutDirection = if (dir.isRTL) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    // ── CTA Button ──

    private fun updateCta() {
        val text = Cure.translation("cta_button", "home_screen").ifBlank { "Get Started" }
        binding.buttonCta.text = text
    }

    // ── Data Store: Feature Products ──

    private fun renderProducts() {
        val container = binding.containerProducts
        container.removeAllViews()

        val items = Cure.getStoreItems("feature_products")

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No products available"
                setTextColor(0xFF999999.toInt())
                setPadding(0, 24, 0, 24)
            }
            container.addView(empty)
            return
        }

        items.forEach { item ->
            val card = CardView(this).apply {
                radius = 16f
                cardElevation = 2f
                setContentPadding(24, 24, 24, 24)
                useCompatPadding = true
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // Product image
            val imageUrl = item.string("image_url")
            if (!imageUrl.isNullOrBlank()) {
                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(150, 150).apply {
                        marginEnd = 24
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                imageView.load(imageUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(16f))
                }
                row.addView(imageView)
            }

            // Text column
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val title = TextView(this).apply {
                text = item.string("title") ?: "—"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            textCol.addView(title)

            val price = item.double("price")
            if (price != null) {
                val priceView = TextView(this).apply {
                    text = "$${String.format("%.2f", price)}"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(0xFF999999.toInt())
                }
                textCol.addView(priceView)
            }

            row.addView(textCol)

            // CTA button
            val ctaUrl = item.ctaURL
            if (!ctaUrl.isNullOrBlank()) {
                val ctaBtn = com.google.android.material.button.MaterialButton(this).apply {
                    text = "View"
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                row.addView(ctaBtn)
            }

            card.addView(row)
            container.addView(card)
        }
    }
}
