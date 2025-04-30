package com.sdk.cmscuresdk

// Imports - Ensure all are present
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CMSCure {
    private var isSdkInitialized = false
    // --- START: Log Tags (Simple) ---
    private const val baseURL = "http://192.168.65.16:5050"
    private const val TAG = "CMSDK"
    private const val SDK_NAME = "[CMSCure] :" // Simple Tag
    // --- END: Log Tags ---

    // Configuration & State
    var secretKey: String? = null
    var projectId: String? = null
    private var apiKey: String? = null
    var authToken: String? = null
    private var socket: Socket? = null
    private lateinit var appContext: Context
    var currentLanguage: String = "en"

    // Callbacks & UI
    var onTranslationsUpdated: (() -> Unit)? = null
    var onAvailableLanguagesUpdated: ((List<String>) -> Unit)? = null

    // Cache
    private val screenTranslations = mutableMapOf<String, MutableMap<String, MutableMap<String, String>>>()

    // Constants & Helpers
    private const val CONFIG_FILE_NAME = "cmscure_config.json"
    private const val CACHE_FILE_NAME = "cmscure_cache.json"
    private val client = OkHttpClient()
    private val gson = Gson()
    // --- START: Add Color Constants ---
    private const val COLORS_SCREEN_KEY = "__colors__"
    // --- START: CHANGE THIS LINE ---
    // private const val COLOR_VALUE_KEY =
    private const val COLOR_VALUE_KEY = "color"
    // --- END: Add Color Constants ---

    private data class ColorBinding(
        val key: String,
        val attribute: ColorAttribute,
        val viewRef: WeakReference<android.view.View>
    )

    private enum class ColorAttribute {
        BACKGROUND,
        TEXT_COLOR,
        IMAGE_TINT,
        HINT_TEXT_COLOR

    }


    // List to store active color bindings
    private val boundColorViews = mutableListOf<ColorBinding>()
    @ColorInt private val DEFAULT_COLOR_FALLBACK: Int = android.graphics.Color.TRANSPARENT // Ya Color.BLACK? Transparent shayad behtar hai failure indicate karne ke liye

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // --- Initialization ---
    fun initializeContext(context: Context) {
        if(isSdkInitialized) return
        appContext = context.applicationContext
        Log.i(TAG, "$SDK_NAME SDK Initializing...")
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        loadCacheFromDisk()
        readConfig()
        registerNetworkCallback()
        isSdkInitialized = true
        schedulePeriodicSync()
    }
    fun isInitialized(): Boolean { // <<< Helper function
        return this::appContext.isInitialized && isSdkInitialized
    }
    private const val SYNC_WORK_TAG = "CMSDK_PeriodicSync"
    private fun schedulePeriodicSync() {
        /*
        if (!this::appContext.isInitialized) {
            Log.e(TAG, "$SDK_NAME Cannot schedule sync: Context not initialized.")
            return
        }


        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()


        val repeatingRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES

        )
            .setConstraints(constraints)
            .addTag(SYNC_WORK_TAG)
            .build()

        Log.i(TAG, "$SDK_NAME Scheduling periodic sync work (every ~15 minutes)...")


        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(

            ExistingPeriodicWorkPolicy.KEEP,
            // ExistingPeriodicWorkPolicy.REPLACE,
            repeatingRequest
        )

         */
    }


    fun cancelPeriodicSync() {
        if (!this::appContext.isInitialized) return
        WorkManager.getInstance(appContext).cancelUniqueWork(SYNC_WORK_TAG)
        Log.i(TAG, "$SDK_NAME Periodic sync work cancelled.")
    }
    // --- Add Network Callback Logic ---
    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) { // Check if API level is sufficient (24+)
            Log.w(TAG, "$SDK_NAME Network callback requires API 24+. Active monitoring might be limited.")
            return // Stop if API level is too low for registerDefaultNetworkCallback
        }

        if (connectivityManager == null) {
            Log.e(TAG, "$SDK_NAME Cannot register network callback: ConnectivityManager is null.")
            return
        }
        if (networkCallback != null) {
            Log.d(TAG, "$SDK_NAME Network callback already registered.")
            return // Avoid registering multiple times
        }


        networkCallback = object : ConnectivityManager.NetworkCallback() {
            // Called when network connection is established (or regained)
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(TAG, "$SDK_NAME Network Callback: Network Available (Network: $network)")
                // Check if socket exists and is NOT connected
                val isSocketConnected = socket?.connected() == true // Check current state safely
                if (socket != null && !isSocketConnected) {
                    Log.i(TAG, "$SDK_NAME Network available and socket not connected. Attempting connectSocket()...")
                    // Use CoroutineScope to call connectSocket, IO dispatcher is suitable
                    CoroutineScope(Dispatchers.IO).launch {
                        connectSocket() // connectSocket already checks for network internally again, but this triggers it
                    }
                } else if (socket == null) {
                    Log.i(TAG, "$SDK_NAME Network available, but socket is null. May need authentication first.")
                    // Optionally: If you have logic to auto-authenticate when network is back, trigger here.
                    // For now, just log. Authentication flow will likely handle connection.
                } else {
                    Log.d(TAG, "$SDK_NAME Network available, but socket already connected.")
                }
            }

            // Called when network connection is lost
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.w(TAG, "$SDK_NAME Network Callback: Network Lost (Network: $network)")
                // Check if the socket instance exists and believes it's connected
                if (socket != null && socket?.connected() == true) {
                    Log.w(TAG, "$SDK_NAME Network lost, manually disconnecting socket to trigger library's reconnect logic...")
                    socket?.disconnect() // Force disconnect
                    // Socket.IO library (with reconnection=true) should now start trying to reconnect
                } else {
                    Log.d(TAG, "$SDK_NAME Network lost, but socket was not connected or null.")
                }
            }

            // Optional: Implement onCapabilitiesChanged if needed for more specific checks
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                // You could check here if the network still has INTERNET capability
                // val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                // Log.d(TAG, "$SDK_NAME Network capabilities changed for $network: Has Internet? $hasInternet")
            }
        }

        try {
            // Use registerDefaultNetworkCallback for monitoring general network state changes
            connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
            Log.i(TAG, "$SDK_NAME Default network callback registered successfully.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for network callback. Ensure ACCESS_NETWORK_STATE permission.", e)
            networkCallback = null // Reset callback if registration failed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
            networkCallback = null
        }
    }

    // --- Authentication (Handles Offline Check Internally) ---
    fun authenticate(
        apiKey: String,
        projectId: String,
        secretKey: String,
        callback: (success: Boolean, message: String?) -> Unit
    ) {
        // Store credentials passed to the function
        this.secretKey = secretKey
        this.projectId = projectId
        this.apiKey = apiKey
        Log.i(TAG, "$SDK_NAME authenticate called for project: $projectId")

        // 1. Configuration Check (Essential for both online/offline)
        // Ensure we have projectId and secretKey (either from params or previously loaded config)
        if (this.projectId.isNullOrEmpty() || this.secretKey.isNullOrEmpty()) {
            Log.e(TAG, "$SDK_NAME ❌ Cannot authenticate: Project ID or Secret Key missing.")
            CoroutineScope(Dispatchers.Main).launch { callback(false, "Project ID or Secret Key missing") }
            return
        }

        // 2. Attempt Online Authentication Directly (Removed initial offline check)
        Log.i(TAG, "$SDK_NAME Attempting online authentication...")
        val jsonBody = JSONObject().apply {
            // Use the credentials stored in the object's properties
            put("apiKey", this@CMSCure.apiKey)
            put("projectId", this@CMSCure.projectId)
            put("projectSecret", this@CMSCure.secretKey)
        }
        // Ensure URL is correct and uses the object's projectId
        val url = "$baseURL/api/sdk/auth?projectId=${this.projectId}" // Verify IP/Port

        postEncrypted(url, jsonBody, false, // Assuming useAuthHeader is false for auth itself
            onSuccess = { json ->
                // --- Online Success Path ---
                val receivedToken = json.optString("token")
                // Optionally update secret/project ID if server sends them back and if needed
                // this.secretKey = json.optString("projectSecret", this.secretKey)
                // this.projectId = json.optString("projectId", this.projectId)

                if (receivedToken.isNullOrEmpty()) {
                    Log.e(TAG, "$SDK_NAME Auth successful (Online) but token missing in response.")
                    // Treat as failure if token is essential for subsequent calls
                    CoroutineScope(Dispatchers.Main).launch { callback(false, "Token missing in auth response") }
                    return@postEncrypted // Stop processing this success path
                }


                Log.i(TAG, "$SDK_NAME ✅ Auth successful (Online). Token received.")
                authToken = receivedToken // Store the new/refreshed token
                saveConfig() // Save the updated config (especially the new token)
                Log.d(TAG, "$SDK_NAME Config Saved (Post-Online Auth)")
                // *** START: ADD REFRESH CALL HERE ***
                Log.i(TAG, "$SDK_NAME Auth successful. Triggering initial data refresh...")
                CoroutineScope(Dispatchers.Main).launch { // Use Main thread to ensure sync calls are initiated properly if they use MainLooper internally, or use IO if sync is fully background safe
                    refreshAllScreens() // Fetch latest data via HTTP immediately
                }
                // Connect socket AFTER successful online authentication
                Log.i(TAG, "$SDK_NAME Attempting socket connection (Post-Online Auth)...")
                connectSocket()

                // Signal overall success (online mode)
                CoroutineScope(Dispatchers.Main).launch { callback(true, "Ready Online") }
            },
            onFailure = { errorMsg ->
                // --- Online Failure Path (Server down, network error, wrong credentials etc.) ---
                Log.e(TAG, "$SDK_NAME ❌ Auth Failure (Online): $errorMsg")

                // Now, check if we can proceed using cached data
                // Check if the translation cache map (loaded during init) has any data.
                if (screenTranslations.isNotEmpty()) { // Use the correct cache variable name
                    Log.w(TAG, "$SDK_NAME ⚠️ Online auth failed. Falling back to using loaded cache.")

                    // Ensure necessary state is loaded/set if needed (like currentLanguage)
                    // Example: ensure currentLanguage is set from config loaded at init.
                    // readConfig() // Might be needed here if config wasn't loaded reliably at init

                    // *** Crucial: Attempt socket connection anyway ***
                    // Socket.IO library will handle retries in the background if server is down/comes back later.
                    Log.i(TAG, "$SDK_NAME Attempting socket connection (Post-Online Auth Failure - Cache Fallback)...")
                    connectSocket() //

                    // Report success, indicating cached/offline mode
                    CoroutineScope(Dispatchers.Main).launch { callback(true, "Ready from Cache") }

                } else {
                    // Online auth failed AND no cache available - Cannot proceed
                    Log.e(TAG, "$SDK_NAME ❌ Online auth failed and no cache found. Cannot proceed.")
                    CoroutineScope(Dispatchers.Main).launch { callback(false, "Online auth failed, no cache: $errorMsg") }
                }
            }
        ) // End of postEncrypted call
    }

    // --- Data Syncing (Handles Offline Check Internally) ---
    // --- Data Syncing (Handles Offline Check Internally) ---
    fun sync(screenName: String) {
        // --- Initial Checks (No Change) ---
        if (!isNetworkAvailable()) {
            Log.w(TAG, "$SDK_NAME Network Unavailable. Skipping sync for '$screenName'.")
            return
        }
        Log.d(TAG, "$SDK_NAME Network Available. Syncing screen: $screenName")
        val currentProjectId = projectId
        val currentSecretKey = secretKey
        val currentAuthToken = authToken
        if (currentProjectId == null || currentSecretKey == null || currentAuthToken == null) {
            Log.e(TAG, "$SDK_NAME Config missing for sync '$screenName'.")
            return
        }
        // --- End Initial Checks ---

        val url = "$baseURL/api/sdk/translations/$currentProjectId/$screenName"
        val body = JSONObject().apply {
            put("projectId", currentProjectId)
            put("screenName", screenName) // Send screenName in body
        }

        postEncrypted(url, body, useAuthHeader = true,
            onSuccess = { decrypted ->
                val keysArray = decrypted.optJSONArray("keys")
                if (keysArray == null) {
                    Log.w(TAG, "$SDK_NAME No 'keys' array received for '$screenName'. Server response might be empty or malformed.")
                    // Handle empty keys response for colors: Clear cache for this screen
                    if (screenName == COLORS_SCREEN_KEY) {
                        synchronized(screenTranslations) {
                            val screenMap = screenTranslations[COLORS_SCREEN_KEY]
                            if (screenMap != null && screenMap.isNotEmpty()) {
                                Log.w(TAG, "$SDK_NAME Received empty keys for $COLORS_SCREEN_KEY, clearing cached colors.")
                                screenMap.clear()
                                saveCacheToDisk() // Save change
                                // <<< CHANGE START >>>
                                // Also refresh bound color views (to apply default colors if keys were removed)
                                CoroutineScope(Dispatchers.Main).launch { refreshBoundColorViews() }
                                // <<< CHANGE END >>>
                            }
                        }
                    }
                    return@postEncrypted
                }

                var cacheUpdated = false
                synchronized(screenTranslations) {
                    val screenMap = screenTranslations.getOrPut(screenName) { mutableMapOf() }

                    // --- START: Conditional Logic for Colors vs Translations ---
                    if (screenName == COLORS_SCREEN_KEY) {
                        // === Handle Colors Screen ===
                        Log.d(TAG, "$SDK_NAME Processing sync data for COLORS screen: $screenName")
                        val currentKeys = mutableSetOf<String>() // Keep track of keys received
                        for (i in 0 until keysArray.length()) {
                            try {
                                val keyObj = keysArray.getJSONObject(i)
                                val keyName = keyObj.optString("key")
                                val values = keyObj.optJSONObject("values") // Expecting {"value": "#HEX"}

                                if (keyName.isNotEmpty() && values != null) {
                                    currentKeys.add(keyName)
                                    val colorHexValue = values.optString(COLOR_VALUE_KEY) // Use constant "color"

                                    if (colorHexValue.isNotEmpty()) {
                                        val keyColorMap = screenMap.getOrPut(keyName) { mutableMapOf() }
                                        // Store/Update using COLOR_VALUE_KEY
                                        if (keyColorMap[COLOR_VALUE_KEY] != colorHexValue) {
                                            keyColorMap.clear()
                                            keyColorMap[COLOR_VALUE_KEY] = colorHexValue
                                            cacheUpdated = true
                                        }
                                    } else {
                                        // If value is empty, remove the key from cache
                                        if (screenMap.remove(keyName) != null) {
                                            Log.d(TAG, "$SDK_NAME [Color] Removed key '$keyName' for screen '$screenName' due to empty value.")
                                            cacheUpdated = true
                                        }
                                    }
                                }
                            } catch (e: Exception) { Log.e(TAG, "$SDK_NAME Color Sync parse error: ${e.message}") }
                        }
                        // Remove keys from cache that were not in the response (handling deletions)
                        val keysToRemove = screenMap.keys.filterNot { it in currentKeys }
                        if (keysToRemove.isNotEmpty()) {
                            keysToRemove.forEach { screenMap.remove(it) }
                            Log.d(TAG, "$SDK_NAME [Color] Removed obsolete keys for screen '$screenName': $keysToRemove")
                            cacheUpdated = true
                        }

                    } else {
                        // === Handle Normal Translation Screen (Existing Logic with Deletion Handling) ===
                        Log.d(TAG, "$SDK_NAME Processing sync data for TRANSLATION screen: $screenName")
                        val currentKeys = mutableSetOf<String>()
                        for (i in 0 until keysArray.length()) {
                            try {
                                val keyObj = keysArray.getJSONObject(i); val keyName = keyObj.optString("key")
                                val values = keyObj.optJSONObject("values")

                                if (keyName.isNotEmpty() && values != null) {
                                    currentKeys.add(keyName)
                                    val keyLangsMap = screenMap.getOrPut(keyName) { mutableMapOf() }
                                    var keyUpdated = false
                                    val receivedLangCodes = mutableSetOf<String>()

                                    values.keys().forEach { langCode ->
                                        receivedLangCodes.add(langCode)
                                        val langValue = values.optString(langCode)
                                        // Store/Update using language code
                                        if (keyLangsMap[langCode] != langValue) { // Store even if empty from server to handle deletion
                                            keyLangsMap[langCode] = langValue
                                            keyUpdated = true
                                        }
                                    }
                                    // Remove languages for this key that were not in the response
                                    val langsToRemove = keyLangsMap.keys.filterNot { it in receivedLangCodes }
                                    if(langsToRemove.isNotEmpty()){
                                        langsToRemove.forEach{ keyLangsMap.remove(it) }
                                        keyUpdated = true
                                    }
                                    if (keyUpdated) cacheUpdated = true
                                }
                            } catch (e: Exception) { Log.e(TAG, "$SDK_NAME Sync parse error: ${e.message}") }
                        }
                        // Remove keys from cache that were not in the response
                        val keysToRemove = screenMap.keys.filterNot { it in currentKeys }
                        if (keysToRemove.isNotEmpty()) {
                            keysToRemove.forEach { screenMap.remove(it) }
                            Log.d(TAG, "$SDK_NAME Removed obsolete keys for screen '$screenName': $keysToRemove")
                            cacheUpdated = true
                        }
                    }
                    // --- END: Conditional Logic ---
                } // End synchronized block

                Log.d(TAG, "$SDK_NAME Sync done for '$screenName'. Cache updated: $cacheUpdated")
                if (cacheUpdated) {
                    saveCacheToDisk() // Save cache if anything changed
                    CoroutineScope(Dispatchers.Main).launch {
                        // --- START: Trigger Appropriate UI Update ---
                        if (screenName == COLORS_SCREEN_KEY) {
                            // <<< CHANGE START >>>
                            // Agar colors update hue hain, toh bound color views ko refresh karein
                            Log.d(TAG, "$SDK_NAME Color cache updated via sync. Refreshing bound color views.")
                            refreshBoundColorViews() // <-- SDK Level UI Update Trigger for Colors
                            // Optional: General callback phir bhi trigger kar sakte hain
                            onTranslationsUpdated?.invoke() // <-- Notify Activity something changed generally
                            // <<< CHANGE END >>>
                        } else {
                            // Normal translations update hui hain (existing logic)
                            Log.d(TAG, "$SDK_NAME Refreshing bound text views for screen '$screenName'.")
                            refreshBoundTextViews(screenName)
                            Log.d(TAG, "$SDK_NAME Invoking onTranslationsUpdated callback after '$screenName' sync.")
                            onTranslationsUpdated?.invoke() // <-- Notify Activity translations changed
                        }
                        // --- END: Trigger Appropriate UI Update ---
                    }
                }
            },
            onFailure = { errorMsg -> Log.e(TAG, "$SDK_NAME Sync Failed for '$screenName': $errorMsg") }
        )
    } // End sync function

    // --- Data Retrieval ---
    fun get(screen: String, key: String): String? {
        return synchronized(screenTranslations) { screenTranslations[screen]?.get(key)?.get(currentLanguage) }
    }

    // --- Language Management ---
    fun setLanguage(language: String) {
        if (currentLanguage == language) {
            Log.d(TAG, "$SDK_NAME Language '$language' is already set.")
            return
        }
        Log.i(TAG, "$SDK_NAME Setting language to '$language'")
        currentLanguage = language
        saveConfig() // Save the new language selection

        // --- START: IMPROVED IMMEDIATE UPDATE ---
        Log.d(TAG, "$SDK_NAME Language changed to '$language'. Triggering immediate UI refresh from cache...")

        // 1. (Existing) Refresh views already bound via SDK's bind() function using new language from cache
        CoroutineScope(Dispatchers.Main).launch {
            refreshBoundTextViews()
        }

        // 2. (New) Trigger the general update callback *immediately*.
        //    The application (e.g., MainActivity) should listen to this callback
        //    and re-fetch required translations using ContentForShare.get(screen, key)
        //    to update other UI elements not handled by bind().
        CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "$SDK_NAME Invoking onTranslationsUpdated callback for general UI refresh.")
            onTranslationsUpdated?.invoke()
        }
        // --- END: IMPROVED IMMEDIATE UPDATE ---

        // Still refresh data from network in the background afterwards
        // Note: refreshAllScreens calls sync, which itself calls refreshBoundTextViews again
        // after network fetch, which is fine (idempotent).
        refreshAllScreens()
    }

    fun refreshAllScreens() {
        Log.d(TAG, "$SDK_NAME Refreshing all screens (including colors) for language '$currentLanguage'")
        val screensToRefresh: List<String>
        synchronized(screenTranslations) {
            // Get existing screen keys and *always* add the colors key
            screensToRefresh = screenTranslations.keys.toMutableSet().apply {
                add(COLORS_SCREEN_KEY) // Ensure colors are always included
            }.toList()
        }
        if (screensToRefresh.isEmpty()){
            Log.w(TAG, "$SDK_NAME No screens (including colors) to refresh.")
            return
        }
        Log.d(TAG, "$SDK_NAME Screens to refresh: $screensToRefresh")
        screensToRefresh.forEach { screen -> sync(screen) } // sync() handles network check
    }
    // --- Add Color Retrieval Function ---
    /**
     * Retrieves the color hex string for a given key.
     * Assumes colors are stored under the special screen key "__colors__".
     *
     * @param key The key for the color (e.g., "primary_color").
     * @return The hex color string (e.g., "#FFFFFF") or null if not found.
     */
    fun getColorValue(key: String): String? {
        val colorHex = synchronized(screenTranslations) {
            screenTranslations[COLORS_SCREEN_KEY]?.get(key)?.get(COLOR_VALUE_KEY)
        }
        if (colorHex == null) {
            Log.w(TAG, "$SDK_NAME Color value (hex string) not found for key '$key' in cache.")
        }
        return colorHex
    }
    @ColorInt
    // <<< CHANGE START: defaultColor parameter remove karein >>>
    fun getColorInt(key: String): Int {
        // <<< CHANGE END >>>
        val colorHex: String? = getColorValue(key)
        if (colorHex == null) {
            return DEFAULT_COLOR_FALLBACK // <<< defaultColor ke bajaye internal constant use karein
        }
        return try {
            Color.parseColor(colorHex)
        } catch (e: Exception) { // Catch all exceptions during parse
            Log.e(TAG, "Failed to parse color hex '$colorHex' for key '$key'. Using fallback.", e)
            DEFAULT_COLOR_FALLBACK // <<< defaultColor ke bajaye internal constant use karein
        }
    }

    // <<< CHANGE START: defaultColor parameter remove karein >>>
    fun bindBackgroundColor(view: android.view.View, key: String) {
        // <<< CHANGE END >>>
        val viewId = view.id
        if (viewId == android.view.View.NO_ID) { Log.e(TAG, "$SDK_NAME Cannot bind background color: View ID missing."); return }
        boundColorViews.removeAll { val v = it.viewRef.get(); v == null || (v.id == viewId && it.attribute == ColorAttribute.BACKGROUND) }
        // <<< CHANGE START: defaultColor remove karein ColorBinding se >>>
        val binding = ColorBinding(key, ColorAttribute.BACKGROUND, WeakReference(view))
        // <<< CHANGE END >>>
        boundColorViews.add(binding)
        Log.d(TAG, "$SDK_NAME Background Color bound: View ID=${view.id}, Key='$key'")
        updateColorView(binding)
    }

    fun bindTextColor(textView: android.widget.TextView, key: String) {
        // <<< CHANGE END >>>
        val viewId = textView.id
        if (viewId == android.view.View.NO_ID) { Log.e(TAG, "$SDK_NAME Cannot bind text color: View ID missing."); return }
        boundColorViews.removeAll { val v = it.viewRef.get(); v == null || (v.id == viewId && it.attribute == ColorAttribute.TEXT_COLOR) }
        // <<< CHANGE START: defaultColor remove karein ColorBinding se >>>
        val binding = ColorBinding(key, ColorAttribute.TEXT_COLOR, WeakReference(textView))
        // <<< CHANGE END >>>
        boundColorViews.add(binding)
        Log.d(TAG, "$SDK_NAME Text Color bound: View ID=${textView.id}, Key='$key'")
        updateColorView(binding)
    }

    fun bindImageTint(imageView: android.widget.ImageView, key: String) {
        val viewId = imageView.id
        if (viewId == android.view.View.NO_ID) { Log.e(TAG, "$SDK_NAME Cannot bind image tint: View ID missing."); return }
        boundColorViews.removeAll { val v = it.viewRef.get(); v == null || (v.id == viewId && it.attribute == ColorAttribute.IMAGE_TINT) }
        val binding = ColorBinding(key, ColorAttribute.IMAGE_TINT, WeakReference(imageView))
        boundColorViews.add(binding)
        Log.d(TAG, "$SDK_NAME Image Tint bound: View ID=${imageView.id}, Key='$key'")
        updateColorView(binding)
    }

    /** Binds the hint text color of an EditText */
    fun bindHintTextColor(editText: android.widget.EditText, key: String) {
        val viewId = editText.id
        if (viewId == android.view.View.NO_ID) { Log.e(TAG, "$SDK_NAME Cannot bind hint text color: View ID missing."); return }
        boundColorViews.removeAll { val v = it.viewRef.get(); v == null || (v.id == viewId && it.attribute == ColorAttribute.HINT_TEXT_COLOR) }
        val binding = ColorBinding(key, ColorAttribute.HINT_TEXT_COLOR, WeakReference(editText))
        boundColorViews.add(binding)
        Log.d(TAG, "$SDK_NAME Hint Text Color bound: View ID=${editText.id}, Key='$key'")
        updateColorView(binding)
    }

    fun getAvailableLanguages(callback: (List<String>) -> Unit) {
        // Get current config state safely
        val currentProjectId = projectId
        val currentSecretKey = secretKey
        val currentAuthToken = authToken
        val sdkCurrentLanguage = currentLanguage // Current language from config

        // --- Helper function for Fallback Logic ---
        val executeFallback = { reason: String ->
            Log.w(TAG, "$SDK_NAME $reason. Falling back to languages available in cache.")
            val cachedLanguages: List<String> = synchronized(screenTranslations) {
                if (screenTranslations.isEmpty()) {
                    // If cache is empty, maybe return current language as absolute minimum? Or empty? Let's return current.
                    Log.w(TAG, "$SDK_NAME Cache is empty, returning only current language: $sdkCurrentLanguage")
                    listOfNotNull(sdkCurrentLanguage) // Return current lang if known, else empty list
                } else {
                    // Extract all unique language codes from the cache
                    val uniqueLangs = screenTranslations.values
                        .flatMap { keysMap -> keysMap.values } // Get all Map<Lang, Value>
                        .flatMap { langMap -> langMap.keys } // Get all Lang codes
                        .toSet() // Get unique languages
                    Log.d(TAG, "$SDK_NAME Languages found in cache: $uniqueLangs")
                    uniqueLangs.toList()
                }
            }
            CoroutineScope(Dispatchers.Main).launch { callback(cachedLanguages) }
        }
        // --- End Helper ---


        // 1. Check Essential Config
        if (currentProjectId == null || currentSecretKey == null) {
            executeFallback("Cannot get languages: Project ID or Secret Key missing in config")
            return
        }

        // 2. Check Network
        if (!isNetworkAvailable()) {
            executeFallback("Network Unavailable")
            return
        }

        // 3. Check Auth Token for Online Call
        if (currentAuthToken == null) {
            executeFallback("Cannot fetch languages online: Auth Token missing")
            return
        }

        // 4. Attempt Online Fetch
        Log.d(TAG, "$SDK_NAME Network Available. Fetching languages online...")
        val url = "$baseURL/api/sdk/languages/$currentProjectId"
        val body = JSONObject().apply { put("projectId", currentProjectId) }

        postEncrypted(url, body, useAuthHeader = true,
            onSuccess = { decryptedJson ->
                val languages = mutableListOf<String>()
                val langArray = decryptedJson.optJSONArray("languages")
                if (langArray != null) {
                    for (i in 0 until langArray.length()) {
                        langArray.optString(i)?.takeIf { it.isNotEmpty() }?.let { languages.add(it) }
                    }
                }

                if (languages.isNotEmpty()) {
                    // Success: API returned languages
                    Log.d(TAG, "$SDK_NAME Fetched languages online: $languages")
                    CoroutineScope(Dispatchers.Main).launch { callback(languages) }
                } else {
                    // Success, but API returned empty list - Use fallback logic
                    executeFallback("API returned empty language list")
                }
            },
            onFailure = { errorMsg ->
                // Failure: API call failed - Use fallback logic
                executeFallback("Failed fetch languages: $errorMsg")
            }
        )
    }

    // --- Configuration Persistence ---
    private fun saveConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configData = JSONObject().apply {
                    put("projectId", projectId); put("apiKey", apiKey); put("secretKey", secretKey)
                    put("authToken", authToken); put("currentLanguage", currentLanguage)
                }
                File(appContext.filesDir, CONFIG_FILE_NAME).writeText(configData.toString())
                Log.d(TAG, "$SDK_NAME Config saved.")
            } catch (e: Exception) { Log.e(TAG, "$SDK_NAME Failed save config: ${e.message}") }
        }
    }

    private fun readConfig() {
        try {
            val file = File(appContext.filesDir, CONFIG_FILE_NAME)
            if (!file.exists()) { Log.w(TAG, "$SDK_NAME Config file not found."); return }
            val configData = JSONObject(file.readText())
            projectId = configData.optString("projectId", null)
            apiKey = configData.optString("apiKey", null)
            secretKey = configData.optString("secretKey", null)
            authToken = configData.optString("authToken", null)
            currentLanguage = configData.optString("currentLanguage", "en")
            Log.d(TAG, "$SDK_NAME Config loaded. PID: $projectId, Lang: $currentLanguage, Token: ${authToken != null}")
            // REMOVED automatic connectSocket call from here
        } catch (e: Exception) { Log.e(TAG, "$SDK_NAME Failed read config: ${e.message}") }
    }

    // --- Cache Persistence ---
    private fun saveCacheToDisk() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cacheDataCopy: Map<String, Map<String, Map<String, String>>>
                synchronized(screenTranslations) { cacheDataCopy = screenTranslations.mapValues { (_, k) -> k.mapValues { (_, v) -> v.toMap() }.toMap() } }
                if (cacheDataCopy.isEmpty()) { return@launch }
                val jsonString = gson.toJson(cacheDataCopy)
                File(appContext.filesDir, CACHE_FILE_NAME).writeText(jsonString)
                Log.d(TAG, "$SDK_NAME Cache saved.")
            } catch (e: Exception) { Log.e(TAG, "Failed save cache: ${e.message}", e) }
        }
    }

    private fun loadCacheFromDisk() {
        try {
            val file = File(appContext.filesDir, CACHE_FILE_NAME)
            if (!file.exists() || file.length() == 0L) { Log.w(TAG, "$SDK_NAME Cache file not found/empty."); return }
            val jsonString = file.readText()
            val type = object : TypeToken<MutableMap<String, MutableMap<String, MutableMap<String, String>>>>() {}.type
            val loadedCache: MutableMap<String, MutableMap<String, MutableMap<String, String>>>? = try { gson.fromJson(jsonString, type) }
            catch (e: com.google.gson.JsonSyntaxException) { Log.e(TAG, "$SDK_NAME Failed parse cache: ${e.message}"); file.delete(); null }

            if (loadedCache != null) {
                synchronized(screenTranslations) { screenTranslations.clear(); screenTranslations.putAll(loadedCache) }
                Log.i(TAG, "$SDK_NAME Cache loaded. Screens: ${screenTranslations.keys.joinToString()}")
                CoroutineScope(Dispatchers.Main).launch { refreshBoundTextViews(); onTranslationsUpdated?.invoke() }
            }
        } catch (e: IOException) { Log.e(TAG, "Failed read cache file: ${e.message}", e)
        } catch (e: Exception) { Log.e(TAG, "Error loading cache: ${e.message}", e) }
    }

    // --- Socket Connection (Handles Offline Check Internally) ---
    fun connectSocket() {
        if (!isNetworkAvailable()) { // Check network INTERNALLY
            Log.w(TAG, "$SDK_NAME Network Unavailable. Skipping socket connection.")
            return
        }
        Log.i(TAG, "$SDK_NAME Network Available. Connecting socket...")
        val currentProjectId = projectId; val currentSecretKey = secretKey
        if (currentProjectId == null || currentSecretKey == null) { Log.e(TAG, "$SDK_NAME Config missing for socket."); return }
        if (socket?.connected() == true) { Log.w(TAG, "$SDK_NAME Already connected."); return }
        if (socket != null) { socket?.disconnect(); socket = null }

        try {
            val opts = IO.Options().apply { reconnection = true; timeout = 5000; transports = arrayOf("websocket") }
            socket = IO.socket("$baseURL", opts) // Check IP
            socket?.off()

            socket?.on(Socket.EVENT_CONNECT) {
                Log.i(TAG, "$SDK_NAME --> EVENT_CONNECT received")
                Log.i(TAG, "$SDK_NAME --> EVENT_CONNECT received (Connection Established!)") // Kya yeh log aata hai?
                // Plaintext body for encryption (Include projectId as per user's working fix)
                val bodyToEncrypt = JSONObject().apply {
                    put("projectId", currentProjectId)
                    // Agar projectSecret bhi encrypt karna zaroori tha working version mein, toh yahan add karein:
                    // put("projectSecret", currentSecretKey)
                }

                try {
                    val derivedKey = CryptoHelper.deriveKey(currentSecretKey) // Use currentSecretKey from function scope
                    val (iv, cipherText, tag) = CryptoHelper.encryptAES(bodyToEncrypt.toString(), derivedKey)

                    // Final payload to send: {iv, ciphertext, tag, projectId}
                    // (Assuming this structure worked based on previous user feedback)
                    val finalPayload = JSONObject().apply {
                        put("iv", iv)
                        put("ciphertext", cipherText)
                        put("tag", tag)
                        put("projectId", currentProjectId) // Add projectId unencrypted here
                    }

                    socket?.emit("handshake", finalPayload) // Emit this payload
                    Log.d(TAG, "$SDK_NAME --> Handshake emitted (Format: {iv,..,tag,projectId}) for project: $currentProjectId")

                } catch (e: Exception) {
                    Log.e(TAG, "Handshake encrypt/emit error: ${e.message}", e)
                }
            }

            socket?.on("handshake_ack") {
                Log.i(TAG, "$SDK_NAME --> Received 'handshake_ack'. (Handshake SUCCESSFUL)") // Log ko behtar kar dein

                // *** START: YEH CODE ADD KAREIN ***
                Log.i(TAG, "$SDK_NAME Handshake successful. Triggering refresh for all known screens...")
                // Ensure refresh runs on a suitable thread if needed. Main thread recommended for triggering UI related updates eventually.
                CoroutineScope(Dispatchers.Main).launch {
                    refreshAllScreens() // Call the function to sync all cached/known screens
                }
                // *** END: YEH CODE ADD KAREIN ***
            }
            socket?.on("translationsUpdated") { args ->
                Log.i(TAG, "$SDK_NAME --> Received 'translationsUpdated'. Data: ${args.joinToString()}") // Keep basic log

                val dict = args.firstOrNull() as? JSONObject ?: run {
                    Log.e(TAG, "$SDK_NAME Bad translationsUpdated data format") // Simple error log
                    return@on
                }
                val screenName = dict.optString("screenName", null) ?: run {
                    Log.e(TAG, "$SDK_NAME Missing screenName in translationsUpdated event") // Simple error log
                    return@on
                }
                CoroutineScope(Dispatchers.Main).launch { // Launch on Main thread to be safe
                    refreshAllScreens()
                }
                // --- START: Handle __ALL__ Case ---
                // --- START: Modify __ALL__ Case ---
                if (screenName == "__ALL__") {
                    Log.d(TAG, "$SDK_NAME --> Processing '__ALL__' update request.")
                    // Refresh content first
                    CoroutineScope(Dispatchers.Main).launch {
                        refreshAllScreens()
                    }
                    // THEN, refresh the language list and notify the Activity
                    Log.d(TAG, "$SDK_NAME --> Refreshing available languages list after __ALL__ update...")
                    getAvailableLanguages { languages -> // Call getAvailableLanguages again
                        CoroutineScope(Dispatchers.Main).launch {
                            onAvailableLanguagesUpdated?.invoke(languages) // Trigger the new callback
                        }
                    }
                    // --- END: Modify __ALL__ Case ---
                } else {
                    // Normal case: Sync the specific screen
                    Log.d(TAG, "$SDK_NAME --> Processing 'translationsUpdated' for screen: $screenName")
                    CoroutineScope(Dispatchers.Main).launch {
                        sync(screenName) // This will also trigger the general onTranslationsUpdated
                    }
                }
                // --- END: Handle __ALL__ Case ---
            }
            // Add other listeners if needed...
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args -> Log.e(TAG, "$SDK_NAME Connect Error: ${args.firstOrNull()}") }
            socket?.on(Socket.EVENT_DISCONNECT) { args -> Log.w(TAG, "$SDK_NAME Disconnect: ${args.firstOrNull()}") }

            socket?.connect()
            Log.d(TAG, "$SDK_NAME socket.connect() called.")
        } catch (e: Exception) { Log.e(TAG, "connectSocket setup error: ${e.message}", e); socket = null }
    }

    fun disconnectSocket() { Log.w(TAG, "$SDK_NAME Manual disconnect."); socket?.disconnect(); socket = null }

    // --- Networking & Encryption ---
    private fun postEncrypted( url: String, jsonBody: JSONObject, useAuthHeader: Boolean = false, onSuccess: (JSONObject) -> Unit, onFailure: (String) -> Unit) {
        val currentSecretKey = secretKey ?: return onFailure("Secret key missing")
        val currentAuthToken = authToken
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val derivedKey = CryptoHelper.deriveKey(currentSecretKey)
                val (iv, cipherText, tag) = CryptoHelper.encryptAES(jsonBody.toString(), derivedKey)
                val encryptedPayload = JSONObject().apply { put("iv", iv); put("ciphertext", cipherText); put("tag", tag) }
                val bodyBytes = encryptedPayload.toString().toByteArray(StandardCharsets.UTF_8)
                val signature = CryptoHelper.generateHmac(bodyBytes, derivedKey)
                val requestBuilder = Request.Builder().url(url)
                requestBuilder.addHeader("Content-Type", "application/json").addHeader("X-Signature", signature)
                if (useAuthHeader) { if (currentAuthToken == null) { onFailure("Auth token missing"); return@launch }; requestBuilder.addHeader("Authorization", "Bearer $currentAuthToken") }
                val request = requestBuilder.post(RequestBody.create("application/json".toMediaTypeOrNull(), bodyBytes)).build()
                // Log.d(TAG, "$SDK_NAME Making ${request.method} to ${request.url}")
                val response = client.newCall(request).execute()
                val responseBodyString = response.body?.string()
                // Log.d(TAG, "$SDK_NAME Response Code: ${response.code}")
                if (!response.isSuccessful) { onFailure("Request Failed: ${response.code}"); return@launch }
                if (responseBodyString.isNullOrBlank()) { onSuccess(JSONObject()); return@launch }
                val jsonResponse = try { JSONObject(responseBodyString) } catch (e: Exception) { onFailure("Invalid JSON"); return@launch }
                if (jsonResponse.has("iv") && jsonResponse.has("ciphertext") && jsonResponse.has("tag")) {
                    try { val decrypted = decryptPayload(jsonResponse, derivedKey); onSuccess(decrypted) }
                    catch (e: Exception) { onFailure("Decryption failed: ${e.message}") }
                } else { onSuccess(jsonResponse) }
            } catch (e: Exception) { Log.e(TAG, "$SDK_NAME Network Exception: ${e.message}", e); onFailure("Network Exception: ${e.message}") }
        }
    }

    private fun decryptPayload(encrypted: JSONObject, key: SecretKey): JSONObject {
        try {
            val ivString = encrypted.optString("iv"); val cipherTextString = encrypted.optString("ciphertext"); val tagString = encrypted.optString("tag")
            if (ivString.isEmpty() || cipherTextString.isEmpty() || tagString.isEmpty()) { throw IllegalArgumentException("Missing crypto data") }
            val iv = Base64.decode(ivString, Base64.NO_WRAP); val cipherText = Base64.decode(cipherTextString, Base64.NO_WRAP); val tag = Base64.decode(tagString, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainBytes = cipher.doFinal(cipherText) // Assumes tag is verified during doFinal
            val plainText = String(plainBytes, StandardCharsets.UTF_8)
            // Log.d(TAG, "$SDK_NAME Decryption successful.")
            return JSONObject(plainText)
        } catch (e: Exception) { Log.e(TAG, "Decryption failed: ${e.message}", e); throw e }
    }

    // --- UI Binding ---
    private data class TextBinding( val screen: String, val key: String, val viewRef: WeakReference<android.view.View> )
    private val boundViews = mutableListOf<TextBinding>()
    fun bind(view: android.view.View, screen: String, key: String) {
        val viewId = view.id; if (viewId == android.view.View.NO_ID) { return }
        boundViews.removeAll { it.viewRef.get() == null || it.viewRef.get()?.id == viewId }
        boundViews.add(TextBinding(screen, key, WeakReference(view)))
        // Log.d(TAG, "$SDK_NAME View bound: ID=${view.id}")
        updateView(view, get(screen, key)) // Initial update from cache
    }
    fun unbind(view: android.view.View) { /* ... */ }
    fun unbindAll() { boundViews.clear() }

    private fun updateView(view: android.view.View?, value: String?) {
        if (view == null) return; val text = value ?: return
        view.post {
            try {
                when (view) {
                    is android.widget.EditText -> view.setText(text)
                    is com.google.android.material.chip.Chip -> view.chipText = text
                    is androidx.appcompat.widget.Toolbar -> view.title = text
                    is com.google.android.material.appbar.CollapsingToolbarLayout -> view.title = text
                    is android.widget.TextView -> view.text = text
                }
            } catch (e: Exception) { /* Log error */ }
        }
    }
    private fun refreshBoundTextViews(screenName: String) {
        boundViews.removeAll { it.viewRef.get() == null }
        boundViews.forEach { b -> if (b.screen == screenName) { b.viewRef.get()?.let { v -> updateView(v, get(b.screen, b.key)) } } }
    }
    private fun refreshBoundTextViews() {
        boundViews.removeAll { it.viewRef.get() == null }
        boundViews.forEach { b -> b.viewRef.get()?.let { v -> updateView(v, get(b.screen, b.key)) } }
    }

    // --- Network Check Helper ---
    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(): Boolean {
        if (!this::appContext.isInitialized) { Log.e(TAG, "$SDK_NAME Context needed for network check!"); return false }
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val actNw = cm.getNetworkCapabilities(nw) ?: return false
            return actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            val nwInfo = cm.activeNetworkInfo ?: return false
            return nwInfo.isConnected
        }
    }


    // --- END: Add bindColor Function ---

    // --- START: Add unbindColor Function (Optional but good practice) ---
    /**
     * Unbinds color attributes previously set with bindColor.
     * @param view The view to unbind.
     * @param attribute Optional. Specific attribute ("backgroundColor", "textColor") to unbind. If null, unbinds all attributes for this view.
     */

    // --- END: Add unbindColor Function ---

    // --- START: Add Color Update Helpers ---
    /** Internal function to apply color to a single bound view */
    private fun updateColorView(binding: ColorBinding) {
        val view = binding.viewRef.get() ?: return
        // <<< CHANGE START: Call getColorInt without defaultColor parameter >>>
        val colorInt = getColorInt(binding.key)
        // <<< CHANGE END >>>

        view.post {
            try {
                when (binding.attribute) {
                    ColorAttribute.BACKGROUND -> view.setBackgroundColor(colorInt)
                    ColorAttribute.TEXT_COLOR -> {
                        if (view is TextView) { view.setTextColor(colorInt)
                        } else { Log.w(TAG, "$SDK_NAME Cannot set textColor on non-TextView for key '${binding.key}'") }
                    }
                    // <<< START: Add Case for Image Tint >>>
                    ColorAttribute.IMAGE_TINT -> {
                        if (view is ImageView) {
                            if (colorInt == DEFAULT_COLOR_FALLBACK && getColorValue(binding.key) == null) {

                                view.clearColorFilter()
                                // Ya default tint lagayein: view.setColorFilter(DEFAULT_COLOR_FALLBACK, android.graphics.PorterDuff.Mode.SRC_IN)
                            } else {
                                view.setColorFilter(colorInt, PorterDuff.Mode.SRC_IN) // Basic tint mode
                            }
                        } else { Log.w(TAG, "$SDK_NAME Cannot set imageTint on non-ImageView for key '${binding.key}'") }
                    }
                    ColorAttribute.HINT_TEXT_COLOR -> {
                        if (view is android.widget.EditText) {

                            if (colorInt != DEFAULT_COLOR_FALLBACK) {
                                view.setHintTextColor(colorInt)
                            } else {
                                // Optionally reset to default theme hint color if needed
                                // view.setHintTextColor(originalHintTextColor) // Requires saving original color
                            }
                        } else { Log.w(TAG, "$SDK_NAME Cannot set hintTextColor on non-EditText (Key: ${binding.key})") }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying color for key '${binding.key}' to view ${view.id}", e)
            }
        }
    }



    /** Internal function to refresh all currently bound color views */
    private fun refreshBoundColorViews() {
        boundColorViews.removeAll { it.viewRef.get() == null } // Clean up dead references
        Log.d(TAG, "$SDK_NAME Refreshing ${boundColorViews.size} bound color views.")
        boundColorViews.forEach { updateColorView(it) }
    }
    // --- END: Add Color Update Helpers ---

} // End of ContentForShare object


class SyncWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val SDK_NAME = "[CMSCure] :" // Simple Tag
    }

    override suspend fun doWork(): Result {
        //Log.i(TAG, "$SDK_NAME Periodic sync worker starting...")

        try {
            /*
            // <<< CHANGE START: Replace isInitialized() check with config check >>>
            // Check if essential config needed for sync is available in the SDK
            if (ContentForShare.projectId == null || ContentForShare.secretKey == null || ContentForShare.authToken == null) {
                Log.e(TAG, "$SDK_NAME SDK config (projectId/secretKey/authToken) missing. Cannot perform sync. Retrying later.")

                return Result.retry()
            }
            // <<< CHANGE END >>>


            ContentForShare.refreshAllScreens()

            Log.i(TAG, "$SDK_NAME Periodic sync worker finished successfully.")
            */
            return Result.success()

        } catch (e: Exception) {
            //Log.e(TAG, "Periodic sync worker failed: ${e.message}", e)

            return Result.failure()
        }

    }
}