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
        private const val SAMPLE_PROJECT_ID = "f5ff03fa11338b74"
        private const val SAMPLE_API_KEY = "d4ff36e2870860a5874062d60778c700cc2f082c6bcf949da718de6652686cfd"
        private const val SAMPLE_PROJECT_SECRET = "bd2886fccd119c91d5762037eecbe2c202905861b1f39d10da02a877b547020a"


        private fun usingSamplePlaceholders(): Boolean = listOf(
            SAMPLE_PROJECT_ID,
            SAMPLE_API_KEY,
            SAMPLE_PROJECT_SECRET
        ).any { it.startsWith("YOUR_") }
    }
}
