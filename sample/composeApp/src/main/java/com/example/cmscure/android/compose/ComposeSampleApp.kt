package com.example.cmscure.android.compose

import android.app.Application
import com.cmscure.sdk.Cure

/**
 * Minimal Application class that wires the CMSCure SDK for the Compose sample.
 *
 * Replace the placeholder credentials with values from your CMSCure dashboard
 * before running the sample.
 */
class ComposeSampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Cure.init(applicationContext)
        if (usingSamplePlaceholders()) {
            Cure.debugLogsEnabled = true
            return
        }
        Cure.configure(
            context = applicationContext,
            projectId = SAMPLE_PROJECT_ID,
            apiKey = SAMPLE_API_KEY,
            projectSecret = SAMPLE_PROJECT_SECRET,
            enableAutoRealTimeUpdates = true
        )

        Cure.debugLogsEnabled = true
    }

    companion object {
        private const val SAMPLE_PROJECT_ID = "1524a7bd6b7d25ae"
        private const val SAMPLE_API_KEY = "68e923c0a4db051026351b1d433ec31d30ceb0eb7bfaf414b2d3f6c091ba7bbd"
        private const val SAMPLE_PROJECT_SECRET = "00c090eb310bd0148f9bb1239cac40826dee0b8f4b9d16cba568979858721c0b"


        private fun usingSamplePlaceholders(): Boolean = listOf(
            SAMPLE_PROJECT_ID,
            SAMPLE_API_KEY,
            SAMPLE_PROJECT_SECRET
        ).any { it.startsWith("YOUR_") }
    }
}
