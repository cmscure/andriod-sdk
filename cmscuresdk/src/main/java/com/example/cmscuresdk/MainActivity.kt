package com.example.cmscuresdk


import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.cmscuresdk.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var languageAdapter: ArrayAdapter<String>? = null
    // availableLanguages list ki zaroorat shayad na rahe agar direct adapter update karein

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Initialize SDK (Loads cache, reads config)
        CMSCrue.initializeContext(this)

        // 2. Setup Language Dropdown Adapter
        languageAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        binding.languageAutocompleteText.setAdapter(languageAdapter)

        // 3. Bind Views (Shows data from cache immediately if available)
        CMSCrue.bind(binding.txtWelcome, "login", "welcome_text")

        // 4. Authenticate (SDK handles online/offline internally)
        CMSCrue.authenticate(
            "d5e4595065193f472a27fe7d406ebbfd8afb86a83d1cf2796c788a744054d862",
            "4e9bb3d1ae5fa51f",
            "6d672449c876c7af46883890347eb038a825967254165969adcca0456b834c91"
        ) { success, message ->
            runOnUiThread {
                if (success) {
                    Log.d("APP", "✅ SDK Ready: $message") // Message will indicate offline/online
                    // Fetch languages only if SDK is ready (online or offline with token)
                    fetchAndPopulateLanguages()
                    // Initial sync only if online (sync checks internally)
                    CMSCrue.sync("login")

                } else {
                    Log.e("APP", "[CMSCure]: ❌ SDK Auth/Init Failed: $message")
                    // Show error only if it's a real failure, not "Offline..."
                    if (message != "Offline and no cached credentials") {
                        binding.txtWelcome.text = "Error: $message"
                    }
                    // If offline without cached credentials, UI shows default/cached text from bind()
                }
            }

            CMSCrue.onAvailableLanguagesUpdated = { languages ->
                Log.d("APP", "onAvailableLanguagesUpdated triggered. Updating dropdown.")

                populateLanguageDropdown(languages)
            }
        }
        val backgroundColorInt = CMSCrue.getColorInt("background_color")



        Log.d("APP_COLORS", "Applying colorInt $backgroundColorInt for key 'background_color'")


        CMSCrue.bindBackgroundColor(binding.main, "background_color")
        CMSCrue.bindTextColor(binding.txtWelcome,"welcome_text_color")

        // 5. Set Dropdown Selection Listener
        binding.languageAutocompleteText.setOnItemClickListener { parent, view, position, id ->
            val selectedLanguage = parent.getItemAtPosition(position) as? String
            if (selectedLanguage != null && selectedLanguage != CMSCrue.currentLanguage) {
                CMSCrue.setLanguage(selectedLanguage)
            }
        }
    }
    private fun fetchAndPopulateLanguages() {
        CMSCrue.getAvailableLanguages { languages ->
            populateLanguageDropdown(languages)
        }
    }

    // Yeh function MainActivity mein define karna zaroori hai
    private fun populateLanguageDropdown(languages: List<String>) {
        languageAdapter?.clear()
        val currentSdkLang = CMSCrue.currentLanguage
        if (languages.isNotEmpty()) {
            languageAdapter?.addAll(languages)
            binding.languageAutocompleteText.setText(
                languages.firstOrNull { it == currentSdkLang } ?: languages.firstOrNull() ?: "",
                false
            )
            binding.languageAutocompleteText.isEnabled = languages.size > 1
        } else {
            languageAdapter?.add(currentSdkLang)
            binding.languageAutocompleteText.setText(currentSdkLang, false)
            binding.languageAutocompleteText.isEnabled = false
        }
        languageAdapter?.notifyDataSetChanged()
    }

    // onDestroy remains the same
    override fun onDestroy() { super.onDestroy(); CMSCrue.unbindAll() }
}