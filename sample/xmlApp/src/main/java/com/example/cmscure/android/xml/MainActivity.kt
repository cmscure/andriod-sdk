package com.example.cmscure.android.xml

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cmscure.sdk.CMSCureSDK
import com.cmscure.sdk.Cure
import com.example.cmscure.android.xml.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var languageAdapter: ArrayAdapter<String>
    private val languages = mutableListOf<String>()
    private var suppressSpinnerCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguagePicker()
        setupListeners()
        renderFeaturedProducts()
        refreshData()
        updateCtaButton()
        binding.textUpdateEvents.text = getString(R.string.update_events_label, "-")
    }

    private fun setupListeners() {
        binding.buttonManualRefresh.setOnClickListener {
            refreshData()
        }

        lifecycleScope.launch {
            CMSCureSDK.contentUpdateFlow.collectLatest { identifier ->
                binding.textUpdateEvents.text = getString(R.string.update_events_label, identifier)
                updateCtaButton()
                if (!suppressSpinnerCallback) {
                    updateLanguageSelection(Cure.getLanguage())
                }
                if (identifier == DataStoreKeys.FEATURED_PRODUCTS || identifier == CMSCureSDK.ALL_SCREENS_UPDATED) {
                    renderFeaturedProducts()
                }
            }
        }
    }

    private fun refreshData() {
        lifecycleScope.launch(Dispatchers.IO) {
            Cure.sync(Tabs.MARKETING)
            Cure.sync(CMSCureSDK.COLORS_UPDATED)
            Cure.sync(CMSCureSDK.IMAGES_UPDATED)
            Cure.syncStore(DataStoreKeys.FEATURED_PRODUCTS)
        }
    }

    private fun setupLanguagePicker() {
        languageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        binding.spinnerLanguages.adapter = languageAdapter
        binding.spinnerLanguages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallback) return
                val selected = languages.getOrNull(position) ?: return
                if (selected != Cure.getLanguage()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        Cure.setLanguage(selected)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        loadLanguages()
    }

    private fun loadLanguages() {
        CMSCureSDK.availableLanguages { fetched ->
            val list = if (fetched.isEmpty()) DEFAULT_LANGUAGES else fetched
            languages.clear()
            languages.addAll(list)
            languageAdapter.notifyDataSetChanged()
            updateLanguageSelection(Cure.getLanguage().ifBlank { list.first() })
        }
    }

    private fun updateLanguageSelection(language: String) {
        suppressSpinnerCallback = true
        val index = languages.indexOf(language)
        if (index >= 0) {
            binding.spinnerLanguages.setSelection(index, false)
        }
        suppressSpinnerCallback = false
    }

    private fun renderFeaturedProducts() {
        val container = binding.containerFeaturedProducts
        container.removeAllViews()

        val inflater = layoutInflater
        val items = Cure.getStoreItems(DataStoreKeys.FEATURED_PRODUCTS)

        if (items.isEmpty()) {
            val emptyView = inflater.inflate(android.R.layout.simple_list_item_1, container, false) as android.widget.TextView
            emptyView.text = getString(R.string.no_products_placeholder)
            container.addView(emptyView)
            return
        }

        items.forEach { item ->
            val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
            val title = view.findViewById<android.widget.TextView>(android.R.id.text1)
            val subtitle = view.findViewById<android.widget.TextView>(android.R.id.text2)

            title.text = item.data["name"]?.localizedString ?: "Unnamed product"
            subtitle.text = item.data["description"]?.localizedString ?: "No description"

            container.addView(view)
        }
    }

    private fun updateCtaButton() {
        val buttonText = Cure.translation(ContentKeys.CTA_BUTTON, Tabs.MARKETING).ifBlank { DEFAULT_CTA_LABEL }
        binding.buttonGetStarted.text = buttonText
    }

    private object Tabs {
        const val MARKETING = "marketing"
    }

    private object DataStoreKeys {
        const val FEATURED_PRODUCTS = "featured_products"
    }

    private object ContentKeys {
        const val CTA_BUTTON = "cta_primary_action"
    }

    private companion object {
        private val DEFAULT_LANGUAGES = listOf("en")
        private const val DEFAULT_CTA_LABEL = "Launch CMSCure"
    }
}
