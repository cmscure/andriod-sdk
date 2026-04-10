package com.example.cmscure.android.xml

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
                updateSettingsLabels()
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
            Cure.sync("settings_screen")
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
        binding.textDirection.text = if (dir.isRTL) "Direction: RTL ←" else "Direction: LTR →"
        // Set layout direction for the whole content
        binding.root.layoutDirection = if (dir.isRTL) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    // ── CTA Button ──

    private fun updateCta() {
        val text = Cure.translation("cta_button", "home_screen").ifBlank { "Get Started" }
        binding.buttonCta.text = text
    }

    // ── Settings Labels ──

    private fun updateSettingsLabels() {
        val settingsTitle = Cure.translation("settings_title", "settings_screen").ifBlank { "Settings" }
        binding.textSettingsTitle.text = settingsTitle
    }

    // ── Data Store: Feature Products ──

    private fun renderProducts() {
        val container = binding.containerProducts
        container.removeAllViews()

        val items = Cure.getStoreItems("feature_products")

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No products yet — add a \"feature_products\" data store in the dashboard."
                setTextColor(0xFF999999.toInt())
            }
            container.addView(empty)
            return
        }

        items.forEach { item ->
            val row = layoutInflater.inflate(android.R.layout.simple_list_item_2, container, false)
            val title = row.findViewById<TextView>(android.R.id.text1)
            val subtitle = row.findViewById<TextView>(android.R.id.text2)

            title.text = item.string("title") ?: "Untitled"
            val price = item.double("price")
            subtitle.text = if (price != null) "$${String.format("%.2f", price)}" else ""

            container.addView(row)
        }
    }
}
