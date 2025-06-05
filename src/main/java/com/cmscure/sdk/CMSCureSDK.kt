package com.cmscure.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
// POLLING REMOVED: Timer and TimerTask imports are no longer needed
// import java.util.Timer
// import java.util.TimerTask
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The primary singleton object for interacting with the CMSCure backend.
 *
 * This SDK manages content synchronization, language settings, real-time updates via Socket.IO,
 * and provides access to translations, colors, and image URLs managed within the CMS.
 *
 * **Key Responsibilities:**
 * - Configuration: Must be configured once with project-specific credentials via [configure].
 * - Authentication: Handles authentication with the backend.
 * - Data Caching: Stores fetched content (translations, colors) in an in-memory cache with disk persistence.
 * - Synchronization: Fetches content updates via API calls and real-time socket events.
 * - Language Management: Allows setting and retrieving the active language for content.
 * - Socket Communication: Manages a WebSocket connection for receiving live updates.
 * - Thread Safety: Uses synchronization mechanisms for safe access to shared resources.
 * - UI Update Notification: Provides a [SharedFlow] ([contentUpdateFlow]) for observing content changes,
 * suitable for reactive UI updates in both Jetpack Compose and traditional XML views.
 *
 * **Basic Usage Steps:**
 * 1. **Initialization (in your Application class):**
 * ```kotlin
 * // In your custom Application class (e.g., MyApplication.kt)
 * class MyApplication : Application() {
 * override fun onCreate() {
 * super.onCreate()
 * CMSCureSDK.init(applicationContext) // Initialize SDK
 *
 * // Configure SDK (ensure this is called only once)
 * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
 * CMSCureSDK.configure(
 * context = applicationContext,
 * projectId = "YOUR_PROJECT_ID",
 * apiKey = "YOUR_API_KEY",
 * projectSecret = "YOUR_PROJECT_SECRET",
 * serverUrlString = "[https://your.server.com](https://your.server.com)", // Or [http://10.0.2.2](http://10.0.2.2):PORT for local dev
 * socketIOURLString = "wss://your.socket.server.com" // Or ws://10.0.2.2:PORT for local dev
 * )
 * } else {
 * // Handle cases for API < O if SDK features requiring it are critical
 * Log.e("MyApplication", "CMSCureSDK requires API Level O or higher for full functionality.")
 * }
 * }
 * }
 * ```
 * Remember to register `MyApplication` in your `AndroidManifest.xml`:
 * `<application android:name=".MyApplication" ... >`
 *
 * 2. **Accessing Content (in Activities, Fragments, ViewModels, or Composables):**
 * ```kotlin
 * val greeting = CMSCureSDK.translation("greeting_key", "main_screen")
 * val primaryColorHex = CMSCureSDK.colorValue("primary_app_color")
 * val logoUrl = CMSCureSDK.imageUrl("logo_key", "common_assets")
 * ```
 *
 * 3. **Observing Updates for Jetpack Compose UI:**
 * ```kotlin
 * // In your Composable function
 * LaunchedEffect(Unit) {
 * CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
 * Log.d("MyComposable", "Content updated for: $updatedIdentifier")
 * // Trigger a refresh of your UI state variables that depend on SDK data
 * // e.g., myTextState = CMSCureSDK.translation("greeting_key", "main_screen")
 * // if (updatedIdentifier == CMSCureSDK.COLORS_UPDATED) { /* Refresh colors */ }
 * // if (updatedIdentifier == CMSCureSDK.ALL_SCREENS_UPDATED) { /* Refresh all relevant data */ }
 * }
 * }
 * ```
 *
 * 4. **Observing Updates for XML/View-based UI (in an Activity or Fragment):**
 * ```kotlin
 * // In your Activity's or Fragment's onCreate/onViewCreated
 * lifecycleScope.launch {
 * CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
 * Log.d("MyActivity", "Content updated for: $updatedIdentifier")
 * // Manually update your TextViews, ImageViews, View backgrounds, etc.
 * // Example:
 * // val greeting = CMSCureSDK.translation("greeting_key", "main_screen")
 * // myTextView.text = greeting
 * //
 * // val hexColor = CMSCureSDK.colorValue("my_bg_color")
 * // hexColor?.let {
 * //     try { myView.setBackgroundColor(android.graphics.Color.parseColor(it)) }
 * //     catch (e: IllegalArgumentException) { Log.e("MyActivity", "Invalid color hex: $it") }
 * // }
 * }
 * }
 * ```
 */
@SuppressLint("ApplySharedPref") // Using commit() for synchronous save for simplicity and reliability of critical data
object CMSCureSDK {

    private const val TAG = "CMSCureSDK" // Logcat Tag

    /**
     * Data class holding the SDK's active configuration.
     * This is set internally when [configure] is called.
     *
     * @property projectId The unique identifier for your project in CMSCure.
     * @property apiKey The API key for authenticating requests with the CMSCure backend.
     * @property projectSecret The secret key associated with your project, used for legacy encryption and handshake validation. This is the initial secret provided during configuration.
     * @property serverUrl The base URL for the CMSCure backend API.
     * @property socketIOURLString The URL string for the CMSCure Socket.IO server.
     * POLLING REMOVED: pollingIntervalSeconds property is removed.
     */
    data class CureConfiguration(
        val projectId: String,
        val apiKey: String,
        val projectSecret: String, // Initial secret from config, used to derive first key
        val serverUrl: URL,
        val socketIOURLString: String // Stored as String for flexibility with ws/wss schemes
        // POLLING REMOVED: pollingIntervalSeconds property was here
    )

    private var configuration: CureConfiguration? = null
    private val configLock = Any() // Synchronization lock for accessing 'configuration'

    // Internal credentials and cryptographic keys
    private var apiSecret: String? = null // Secret confirmed/provided by auth, used for deriving operational symmetricKey
    private var symmetricKey: SecretKeySpec? = null // AES key for encryption/decryption
    private var authToken: String? = null // Authentication token received from the backend

    /**
     * Enables or disables verbose debug logging to Android's Logcat.
     * When `true`, detailed logs about SDK operations (configuration, auth, sync, socket events, errors) are printed.
     * It's recommended to set this to `false` for production releases to avoid excessive logging.
     * Default value is `true`.
     */
    var debugLogsEnabled: Boolean = true

    // In-memory cache for translations and colors.
    // Structure: [ScreenName (Tab Name): [Content Key: [LanguageCode: Translated Value]]]
    // For colors, ScreenName is typically "__colors__" and LanguageCode is "color".
    private var cache: MutableMap<String, MutableMap<String, MutableMap<String, String>>> = mutableMapOf()
    private val cacheLock = Any() // Synchronization lock for accessing 'cache', 'knownProjectTabs'
    private var currentLanguage: String = "en" // Default active language
    private var knownProjectTabs: MutableSet<String> = mutableSetOf() // Set of tab names known to this project

    // SharedPreferences keys and file names for persistence
    private const val PREFS_NAME = "CMSCureSDKPrefs" // Name of the SharedPreferences file
    private const val KEY_CURRENT_LANGUAGE = "currentLanguage"
    private const val KEY_AUTH_TOKEN = "authToken"
    private const val KEY_API_SECRET = "apiSecret" // Persisted secret (from auth) used for key derivation
    private const val CACHE_FILE_NAME = "cmscure_cache.json" // Filename for disk cache of translations/colors
    private const val TABS_FILE_NAME = "cmscure_tabs.json"   // Filename for disk cache of known tabs
    private var sharedPreferences: SharedPreferences? = null

    // Retrofit service interface definition for API calls
    private interface ApiService {
        @POST("/api/sdk/auth")
        suspend fun authenticateSdk(@Body authRequest: AuthRequestPlain): AuthResult

        @POST("/api/sdk/translations/{projectId}/{tabName}")
        suspend fun getTranslations(
            @Path("projectId") projectId: String,
            @Path("tabName") tabName: String,
            @Header("X-API-Key") apiKey: String,
            @Body requestBody: EncryptedPayload // Sync request body is encrypted
        ): TranslationResponse

        @POST("/api/sdk/languages/{projectId}")
        suspend fun getAvailableLanguages(
            @Path("projectId") projectId: String,
            @Header("X-API-Key") apiKey: String,
            @Body requestBody: Map<String, String> // Request body, e.g., {"projectId": "id"}
        ): LanguagesResponse
    }

    // Data classes for structuring API requests and responses
    data class AuthRequestPlain(val apiKey: String, val projectId: String)
    data class AuthResult(
        val token: String?, val userId: String?,
        @SerializedName("projectId") val receivedProjectId: String?, // Project ID confirmed by backend
        @SerializedName("projectSecret") val receivedProjectSecret: String?, // Project Secret confirmed/provided by backend
        val tabs: List<String>? // List of tab names associated with the project
    )

    data class TranslationKeyItem(val key: String, val values: Map<String, String>) // Map of LanguageCode -> Translated Value
    data class TranslationResponse(val version: Int?, val timestamp: String?, val keys: List<TranslationKeyItem>?)
    data class LanguagesResponse(val languages: List<String>?)
    data class EncryptedPayload( // Structure for AES/GCM encrypted request bodies
        val iv: String, // Base64 encoded Initialization Vector (Nonce)
        val ciphertext: String, // Base64 encoded Ciphertext
        val tag: String, // Base64 encoded Authentication Tag
        val projectId: String? = null, // Optional: Project ID within the encrypted payload
        val screenName: String? = null // Optional: Screen name within the encrypted payload
    )

    // Networking and asynchronous operations setup
    private var apiService: ApiService? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // Dedicated scope for SDK's background tasks
    private var socket: Socket? = null // Socket.IO client instance
    private var handshakeAcknowledged = false // Flag indicating if socket handshake was successful
    private val socketLock = Any() // Synchronization lock for socket operations
    // POLLING REMOVED: pollingTimer variable was here
    // private var pollingTimer: Timer? = null
    private val mainThreadHandler = Handler(Looper.getMainLooper()) // Handler for posting results to the main (UI) thread
    private var applicationContext: Context? = null // Android Application Context
    private val gson: Gson = GsonBuilder().setLenient().create() // Lenient Gson for robust JSON parsing

    // --- Event Emitter for UI Updates ---
    /**
     * A special constant emitted by [contentUpdateFlow] when a global refresh is suggested,
     * such as after a language change that affects multiple screens, or after an initial full sync.
     */
    const val ALL_SCREENS_UPDATED = "__ALL_SCREENS_UPDATED__"
    /**
     * A special constant emitted by [contentUpdateFlow] when color data (from the `__colors__` tab) is updated.
     * This is the event name. The actual tab name used for fetching and caching colors is `__colors__`.
     */
    const val COLORS_UPDATED = "__COLORS_UPDATED__" // Event name for color updates

    private val _contentUpdateFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    /**
     * A [SharedFlow] that emits events indicating content updates.
     * (Full KDoc remains the same)
     */
    val contentUpdateFlow: SharedFlow<String> = _contentUpdateFlow.asSharedFlow()


    /**
     * Initializes the SDK with the application context.
     * (Full KDoc remains the same)
     */
    fun init(context: Context) {
        this.applicationContext = context.applicationContext
        this.sharedPreferences = this.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPersistedState()
        logDebug("SDK Initialized. Current Language: $currentLanguage. Waiting for configure() call.")
    }

    /**
     * Configures the CMSCureSDK with necessary project credentials and server details.
     * (Full KDoc remains the same, parameter list updated)
     * POLLING REMOVED: pollingIntervalSeconds parameter removed.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun configure(
        context: Context, projectId: String, apiKey: String, projectSecret: String,
        serverUrlString: String = "https://app.cmscure.com",
        socketIOURLString: String = "wss://app.cmscure.com"
        // POLLING REMOVED: pollingIntervalSeconds parameter was here
    ) {
        // --- Input Validation ---
        if (projectId.isEmpty()) { logError("Config failed: Project ID cannot be empty."); return }
        if (apiKey.isEmpty()) { logError("Config failed: API Key cannot be empty."); return }
        if (projectSecret.isEmpty()) { logError("Config failed: Project Secret cannot be empty."); return }

        val serverUrl = try { URL(serverUrlString) } catch (e: MalformedURLException) {
            logError("Config failed: Invalid server URL '$serverUrlString': ${e.message}"); return
        }
        if (socketIOURLString.isBlank()) {
            logError("Config failed: Invalid socket URL (blank string)"); return
        }
        if (!socketIOURLString.startsWith("ws://", ignoreCase = true) && !socketIOURLString.startsWith("wss://", ignoreCase = true) &&
            !socketIOURLString.startsWith("http://", ignoreCase = true) && !socketIOURLString.startsWith("https://", ignoreCase = true)) {
            logWarn("Socket URL '$socketIOURLString' does not start with ws(s):// or http(s)://. Connection might fail or be insecure.")
        }

        // POLLING REMOVED: pollingIntervalSeconds removed from CureConfiguration instantiation
        val newConfiguration = CureConfiguration(projectId, apiKey, projectSecret, serverUrl, socketIOURLString)
        synchronized(configLock) {
            if (this.configuration != null) { logError("Config ignored: SDK already configured."); return }
            this.configuration = newConfiguration
            this.applicationContext = context.applicationContext
            if (this.sharedPreferences == null) this.sharedPreferences = this.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        logDebug("SDK Configured: Project $projectId")
        logDebug("   - API Base URL: ${serverUrl.toExternalForm()}")
        logDebug("   - Socket URL String: $socketIOURLString")

        val loggingInterceptor = HttpLoggingInterceptor { message -> if (debugLogsEnabled) Log.d("$TAG-OkHttp", message) }
            .apply { level = if (debugLogsEnabled) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
        this.apiService = Retrofit.Builder().baseUrl(serverUrl).client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(ApiService::class.java)

        deriveSymmetricKey(projectSecret, "initial configuration")
        performLegacyAuthenticationAndConnect()
        // POLLING REMOVED: setupPollingTimer() call was here
    }

    /**
     * Derives the AES symmetric key from a given secret string using SHA-256.
     * (Full KDoc and implementation remain the same)
     */
    private fun deriveSymmetricKey(secret: String, contextMessage: String) {
        try {
            val secretData = secret.toByteArray(Charsets.UTF_8)
            val hashedSecret = MessageDigest.getInstance("SHA-256").digest(secretData)
            this.symmetricKey = SecretKeySpec(hashedSecret, "AES")
            this.apiSecret = secret
            logDebug("🔑 Symmetric key derived successfully from projectSecret ($contextMessage).")
        } catch (e: Exception) {
            logError("⚠️ Failed to derive symmetric key ($contextMessage): ${e.message}");
            this.symmetricKey = null
        }
    }

    /**
     * Retrieves the current SDK configuration.
     * (Full KDoc and implementation remain the same)
     */
    internal fun getCurrentConfiguration(): CureConfiguration? = synchronized(configLock) { configuration }


    /**
     * Performs the legacy authentication process with the backend.
     * (Full KDoc remains the same)
     * POLLING REMOVED: Call to setupPollingTimer() removed.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun performLegacyAuthenticationAndConnect() {
        val currentConfig = getCurrentConfiguration() ?: run { logError("Auth: SDK not configured."); return }
        logDebug("Attempting legacy authentication with server...")
        coroutineScope.launch {
            try {
                val authPayload = AuthRequestPlain(currentConfig.apiKey, currentConfig.projectId)
                val authResult = apiService?.authenticateSdk(authPayload)

                if (authResult?.token != null && authResult.receivedProjectSecret != null) {
                    authToken = authResult.token
                    deriveSymmetricKey(authResult.receivedProjectSecret, "auth response")

                    synchronized(cacheLock) {
                        knownProjectTabs.clear()
                        authResult.tabs?.let { serverTabs -> knownProjectTabs.addAll(serverTabs) }
                    }
                    persistSensitiveState()
                    persistTabsToDisk()

                    logDebug("✅ Auth successful. Token: ${authToken?.take(8)}..., Known Tabs: ${knownProjectTabs.size}")

                    connectSocketIfNeeded()
                    syncIfOutdated()
                    // POLLING REMOVED: setupPollingTimer() call was here
                } else {
                    logError("Auth failed: Response missing token or projectSecret. Response: $authResult")
                }
            } catch (e: HttpException) {
                logError("🆘 Auth HTTP exception: ${e.code()} - ${e.message()}. Response Body: ${e.response()?.errorBody()?.string()}"); e.printStackTrace()
            } catch (e: Exception) {
                logError("🆘 Auth exception: ${e.message}"); e.printStackTrace()
            }
        }
    }

    /**
     * Sets the current active language for retrieving translations from the SDK.
     * (Full KDoc and implementation remain the same)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun setLanguage(languageCode: String, force: Boolean = false) {
        if (languageCode.isBlank()) { logWarn("SetLanguage: Attempted to set a blank language code. Call ignored."); return }
        if (languageCode == currentLanguage && !force) {
            logDebug("SetLanguage: Language '$languageCode' is already active and not forced. No change.")
            return
        }

        val oldLanguage = currentLanguage
        currentLanguage = languageCode
        sharedPreferences?.edit()?.putString(KEY_CURRENT_LANGUAGE, languageCode)?.commit()
        logDebug("🔄 Language changed from '$oldLanguage' to '$languageCode'.")

        val tabsToUpdate = synchronized(cacheLock) { knownProjectTabs.toList() + listOf("__colors__") }.distinct()
        logDebug("SetLanguage: Syncing tabs for new language: $tabsToUpdate")
        tabsToUpdate.forEach { tabName ->
            sync(if (tabName == "__colors__") COLORS_UPDATED else tabName) { success ->
                if (success) logDebug("Successfully synced tab '$tabName' for new language '$languageCode'.")
                else logError("Failed to sync tab '$tabName' for new language '$languageCode'.")
            }
        }
        coroutineScope.launch {
            logDebug("SetLanguage: Emitting ALL_SCREENS_UPDATED due to language change.")
            _contentUpdateFlow.tryEmit(ALL_SCREENS_UPDATED)
        }
    }

    /**
     * Retrieves the currently active language code being used by the SDK.
     * (Full KDoc and implementation remain the same)
     */
    fun getLanguage(): String = currentLanguage

    /**
     * Fetches the list of available language codes supported by the configured project from the backend server.
     * (Full KDoc and implementation remain the same)
     */
    fun availableLanguages(completion: @Escaping (List<String>) -> Unit) {
        val config = getCurrentConfiguration() ?: run { logError("GetLangs: SDK not configured."); mainThreadHandler.post { completion(emptyList()) }; return }
        if (authToken == null) { logError("GetLangs: Not authenticated. Cannot fetch languages."); mainThreadHandler.post { completion(emptyList()) }; return }

        coroutineScope.launch {
            try {
                val response = apiService?.getAvailableLanguages(config.projectId, config.apiKey, mapOf("projectId" to config.projectId))
                val languagesFromServer = response?.languages ?: emptyList()
                logDebug("Available languages fetched from server: $languagesFromServer")
                mainThreadHandler.post { completion(languagesFromServer) }
            } catch (e: Exception) {
                logError("Failed to fetch available languages from server: ${e.message}")
                val cachedLangs = synchronized(cacheLock) {
                    cache.values.asSequence()
                        .flatMap { screenData -> screenData.values.asSequence() }
                        .flatMap { langValueMap -> langValueMap.keys.asSequence() }
                        .distinct()
                        .filter { it != "color" }
                        .toList()
                        .sorted()
                }
                logDebug("Falling back to languages inferred from cache: $cachedLangs")
                mainThreadHandler.post { completion(cachedLangs) }
            }
        }
    }

    /**
     * Retrieves a translation for a specific key within a given tab (screen name),
     * (Full KDoc and implementation remain the same)
     */
    fun translation(forKey: String, inTab: String): String {
        synchronized(cacheLock) {
            return cache[inTab]?.get(forKey)?.get(currentLanguage) ?: ""
        }
    }

    /**
     * Retrieves a color hex string (e.g., "#RRGGBB" or "#AARRGGBB") for a given global color key.
     * (Full KDoc and implementation remain the same)
     */
    fun colorValue(forKey: String): String? {
        synchronized(cacheLock) {
            return cache["__colors__"]?.get(forKey)?.get("color")
        }
    }

    /**
     * Retrieves a [URL] for an image associated with a given key and tab.
     * (Full KDoc and implementation remain the same)
     */
    fun imageUrl(forKey: String, inTab: String): URL? {
        val urlString = translation(forKey, inTab)
        return try {
            if (urlString.isNotBlank()) URL(urlString) else null
        } catch (e: MalformedURLException) {
            logError("Invalid URL format for image key '$forKey' in tab '$inTab': $urlString"); null
        } catch (e: Exception) {
            logError("Error creating URL for image key '$forKey' in tab '$inTab': ${e.message}"); null
        }
    }

    /**
     * Fetches the latest content (translations or colors) for a specific screen name (tab) from the backend.
     * (Full KDoc and implementation remain the same)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun sync(screenName: String, completion: ((Boolean) -> Unit)? = null) {
        val effectiveScreenName = if (screenName == COLORS_UPDATED) "__colors__" else screenName
        val config = getCurrentConfiguration() ?: run { logError("Sync $effectiveScreenName: SDK not configured."); mainThreadHandler.post{ completion?.invoke(false)}; return }
        if (authToken == null) { logError("Sync $effectiveScreenName: Not authenticated."); mainThreadHandler.post{ completion?.invoke(false)}; return }
        if (symmetricKey == null) { logError("Sync $effectiveScreenName: SymmetricKey missing for encryption."); mainThreadHandler.post{ completion?.invoke(false)}; return }

        logDebug("🔄 Syncing tab '$effectiveScreenName'...")
        coroutineScope.launch {
            var success = false
            try {
                val bodyToEncrypt = mapOf("projectId" to config.projectId, "screenName" to effectiveScreenName)
                val encryptedBody = encryptPayload(bodyToEncrypt, config.projectId)
                    ?: throw IOException("Encryption failed for sync request body for tab '$effectiveScreenName'")

                val response = apiService?.getTranslations(config.projectId, effectiveScreenName, config.apiKey, encryptedBody)

                if (response?.keys != null) {
                    synchronized(cacheLock) {
                        val screenCache = cache.getOrPut(effectiveScreenName) { mutableMapOf() }
                        response.keys.forEach { translationItem ->
                            val keyData = screenCache.getOrPut(translationItem.key) { mutableMapOf() }
                            keyData.clear()
                            keyData.putAll(translationItem.values)
                        }
                        if (!knownProjectTabs.contains(effectiveScreenName) && effectiveScreenName != "__colors__") {
                            knownProjectTabs.add(effectiveScreenName)
                            persistTabsToDisk()
                        }
                    }
                    persistCacheToDisk()
                    logDebug("✅ Synced and updated cache for tab '$effectiveScreenName'.")
                    success = true
                    _contentUpdateFlow.tryEmit(if (effectiveScreenName == "__colors__") COLORS_UPDATED else effectiveScreenName)
                } else {
                    logError("Sync $effectiveScreenName: Response was null or contained no keys.")
                }
            } catch (e: HttpException) {
                logError("🆘 Sync $effectiveScreenName HTTP exception: ${e.code()} - ${e.message()}. Response Body: ${e.response()?.errorBody()?.string()}"); e.printStackTrace()
            } catch (e: Exception) {
                logError("🆘 Sync $effectiveScreenName exception: ${e.message}"); e.printStackTrace()
            }
            mainThreadHandler.post{ completion?.invoke(success) }
        }
    }

    /**
     * Triggers a synchronization for all known project tabs and the special `__colors__` tab.
     * (Full KDoc remains the same)
     * POLLING REMOVED: This function is still useful for initial syncs and language changes.
     * The comment about polling timer is removed.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun syncIfOutdated() {
        logDebug("Performing syncIfOutdated for all relevant tabs.")
        val tabsToSyncSnapshot = synchronized(cacheLock) { knownProjectTabs.toList() }.distinct()

        if (tabsToSyncSnapshot.isEmpty()) {
            logDebug("syncIfOutdated: No user-defined tabs known yet. Syncing default 'general' and using constant for colors tab.")
            sync("general")
            sync(COLORS_UPDATED)
        } else {
            tabsToSyncSnapshot.forEach { sync(it) }
            sync(COLORS_UPDATED)
        }
        coroutineScope.launch {
            logDebug("syncIfOutdated: Emitting ALL_SCREENS_UPDATED.")
            _contentUpdateFlow.tryEmit(ALL_SCREENS_UPDATED)
        }
    }

    /**
     * Establishes or re-establishes the Socket.IO connection if not already connected and acknowledged.
     * (Full KDoc and implementation remain the same)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun connectSocketIfNeeded() {
        val config = getCurrentConfiguration() ?: run { logError("SocketConnect: SDK not configured."); return }

        if (socket?.connected() == true && handshakeAcknowledged) {
            logDebug("Socket already connected & handshake acknowledged. No action needed.")
            return
        }
        if (socket?.connected() == true && !handshakeAcknowledged) {
            logDebug("Socket connected, but handshake not acknowledged. Attempting handshake.")
            sendSocketHandshake()
            return
        }

        synchronized(socketLock) {
            socket?.disconnect()?.off()
            socket = null

            try {
                val opts = IO.Options().apply {
                    forceNew = true
                    reconnection = true
                    path = "/socket.io/"
                    transports = arrayOf(io.socket.engineio.client.transports.WebSocket.NAME)
                }
                val socketUri = URI.create(config.socketIOURLString)
                logDebug("🔌 Attempting to connect socket to: $socketUri (path ${opts.path})")
                socket = IO.socket(socketUri, opts)
                setupSocketHandlers()
                socket?.connect()
            } catch (e: Exception) {
                logError("Socket connection setup exception: ${e.message}"); e.printStackTrace()
            }
        }
    }

    /**
     * Sets up standard event handlers (listeners) for the Socket.IO client instance.
     * (Full KDoc and implementation remain the same)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupSocketHandlers() {
        socket?.on(Socket.EVENT_CONNECT) {
            logDebug("🟢✅ Socket connected! SID: ${socket?.id()}");
            handshakeAcknowledged = false;
            sendSocketHandshake()
        }
        socket?.on("handshake_ack") { args ->
            logDebug("🤝 'handshake_ack' event received. Data: ${args.joinToString { it?.toString() ?: "null" }}");
            handshakeAcknowledged = true;
            syncIfOutdated()
        }
        socket?.on("translationsUpdated") { args ->
            logDebug("📡 'translationsUpdated' event received. Data: ${args.joinToString { it?.toString() ?: "null" }}");
            handleSocketTranslationUpdate(args)
        }
        socket?.on(Socket.EVENT_DISCONNECT) { args ->
            logDebug("🔌 Socket disconnected. Reason: ${args.joinToString { it?.toString() ?: "null" }}");
            handshakeAcknowledged = false
        }
        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.getOrNull(0)
            logError("🆘 Socket connection error: $error");
            (error as? Exception)?.printStackTrace()
        }
    }

    /**
     * Processes "translationsUpdated" events received from the socket.
     * (Full KDoc and implementation remain the same)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleSocketTranslationUpdate(data: Array<Any>) {
        try {
            val firstElement = data.firstOrNull()
            val screenNameToUpdate: String? = when (firstElement) {
                is org.json.JSONObject -> firstElement.optString("screenName", null)
                is Map<*, *> -> firstElement["screenName"] as? String
                else -> null
            }

            if (screenNameToUpdate != null) {
                logDebug("Processing 'translationsUpdated' socket event for tab: '$screenNameToUpdate'")
                if (screenNameToUpdate.equals("__ALL__", ignoreCase = true)) {
                    syncIfOutdated()
                } else {
                    sync(if (screenNameToUpdate.equals("__colors__", ignoreCase = true)) COLORS_UPDATED else screenNameToUpdate)
                }
            } else {
                logWarn("Invalid 'translationsUpdated' event: 'screenName' missing or data format unexpected. Data: ${data.joinToString()}")
            }
        } catch (e: Exception) {
            logError("Error processing 'translationsUpdated' data from socket: ${e.message}"); e.printStackTrace()
        }
    }

    /**
     * Sends the encrypted handshake message to the Socket.IO server after connection.
     * (Full KDoc and implementation remain the same)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendSocketHandshake() {
        val config = getCurrentConfiguration()
        val currentSymKey = symmetricKey
        if (config == null || currentSymKey == null) {
            logError("Handshake cannot be sent: Missing configuration or symmetric key. Ensure auth completed and re-derived key."); return
        }
        logDebug("🤝 Sending encrypted handshake for projectId: ${config.projectId}")
        try {
            val handshakeBody = mapOf("projectId" to config.projectId)
            val encryptedPayload = encryptPayload(handshakeBody, config.projectId)
                ?: run { logError("Handshake failed: Could not encrypt payload."); return }

            val payloadJsonString = gson.toJson(encryptedPayload)
            socket?.emit("handshake", org.json.JSONObject(payloadJsonString))
            logDebug("Handshake emitted with encrypted payload.")
        } catch (e: Exception) {
            logError("Handshake emission exception: ${e.message}"); e.printStackTrace()
        }
    }

    /**
     * Checks if the Socket.IO client is currently connected to the server and
     * if the handshake has been successfully acknowledged.
     * (Full KDoc and implementation remain the same)
     */
    fun isConnected(): Boolean = socket?.connected() == true && handshakeAcknowledged

    /**
     * Encrypts a given payload map using AES/GCM with the current symmetric key.
     * (Full KDoc and implementation remain the same)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun encryptPayload(payloadMap: Map<String, Any>, projectIdForPayload: String?): EncryptedPayload? {
        val currentSymKey = symmetricKey ?: run { logError("Encryption failed: SymmetricKey is not available."); return null }
        return try {
            val jsonDataToEncrypt = gson.toJson(payloadMap).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, currentSymKey, gcmSpec)
            val encryptedBytesWithTag = cipher.doFinal(jsonDataToEncrypt)
            val ciphertext = encryptedBytesWithTag.copyOfRange(0, encryptedBytesWithTag.size - 16)
            val tag = encryptedBytesWithTag.copyOfRange(encryptedBytesWithTag.size - 16, encryptedBytesWithTag.size)

            EncryptedPayload(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(tag),
                projectId = projectIdForPayload
            )
        } catch (e: Exception) {
            logError("Encryption exception: ${e.message}"); e.printStackTrace(); null
        }
    }

    // POLLING REMOVED: setupPollingTimer() method is entirely removed.
    /*
    private fun setupPollingTimer() {
        val intervalMillis = (configuration?.pollingIntervalSeconds?.takeIf { it > 0 } ?: 300L) * 1000L
        pollingTimer?.cancel() // Cancel any existing timer before creating a new one
        pollingTimer = Timer("CMSCurePollingTimer", true).apply { // true makes it a daemon thread
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // syncIfOutdated calls sync(), which needs API O for encryption
                        logDebug("⏰ Polling timer fired: triggering syncIfOutdated.")
                        syncIfOutdated()
                    } else {
                        logWarn("Polling: syncIfOutdated skipped, as it requires API Level O for underlying encryption operations.")
                    }
                }
            }, intervalMillis, intervalMillis) // Initial delay and period
        }
        logDebug("⏱️ Polling timer configured with interval: ${intervalMillis / 1000} seconds.")
    }
    */

    /**
     * Loads persisted SDK state (current language, auth token, API secret, cached content, and known tabs)
     * (Full KDoc and implementation remain the same)
     */
    private fun loadPersistedState() {
        currentLanguage = sharedPreferences?.getString(KEY_CURRENT_LANGUAGE, "en") ?: "en"
        authToken = sharedPreferences?.getString(KEY_AUTH_TOKEN, null)
        val persistedApiSecret = sharedPreferences?.getString(KEY_API_SECRET, null)

        if (persistedApiSecret != null && persistedApiSecret.isNotBlank()) {
            deriveSymmetricKey(persistedApiSecret, "persisted state")
        }
        loadCacheFromDisk()
        loadTabsFromDisk()
        logDebug("Loaded persisted state: Lang=$currentLanguage, Token=${authToken!=null}, ApiSecret=${apiSecret!=null} (key derived: ${symmetricKey!=null}), Tabs=${knownProjectTabs.size}")
    }

    /**
     * Persists sensitive state like the auth token and the API secret (used for key derivation)
     * (Full KDoc and implementation remain the same)
     */
    private fun persistSensitiveState() {
        sharedPreferences?.edit()?.apply {
            putString(KEY_AUTH_TOKEN, authToken)
            putString(KEY_API_SECRET, apiSecret)
            commit()
        }
        logDebug("Persisted sensitive state (token, apiSecret).")
    }

    /** Persists the in-memory content cache ([cache]) to a local JSON file. This operation is thread-safe.
     * (Full KDoc and implementation remain the same)
     */
    private fun persistCacheToDisk() {
        val context = applicationContext ?: return
        synchronized(cacheLock) {
            try {
                File(context.filesDir, CACHE_FILE_NAME).writeText(gson.toJson(cache))
                logDebug("💾 Cache persisted to disk.")
            } catch (e: Exception) { logError("Failed to persist content cache to disk: ${e.message}") }
        }
    }

    /** Loads the content cache from a local JSON file into the in-memory [cache]. This operation is thread-safe.
     * (Full KDoc and implementation remain the same)
     */
    private fun loadCacheFromDisk() {
        val context = applicationContext ?: return
        synchronized(cacheLock) {
            try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val jsonString = file.readText()
                    if(jsonString.isNotBlank()){
                        val typeToken = object : TypeToken<MutableMap<String, MutableMap<String, MutableMap<String, String>>>>() {}.type
                        cache = gson.fromJson(jsonString, typeToken) ?: mutableMapOf()
                        logDebug("📦 Cache loaded from disk. Contains ${cache.size} screen(s)/tab(s).")
                    } else { logWarn("Cache file ('$CACHE_FILE_NAME') is empty. Initializing with an empty cache."); cache = mutableMapOf() }
                } else {
                    logDebug("Cache file ('$CACHE_FILE_NAME') does not exist or is empty. Initializing with an empty cache.")
                    cache = mutableMapOf()
                }
            } catch (e: JsonSyntaxException) {
                logError("Failed to load cache from disk due to JSON syntax error: ${e.message}. Deleting corrupted cache file.")
                File(context.filesDir, CACHE_FILE_NAME).delete()
                cache = mutableMapOf()
            }
            catch (e: Exception) {
                logError("Failed to load content cache from disk: ${e.message}");
                cache = mutableMapOf()
            }
        }
    }

    /** Persists the set of known project tabs ([knownProjectTabs]) to a local JSON file. This operation is thread-safe.
     * (Full KDoc and implementation remain the same)
     */
    private fun persistTabsToDisk() {
        val context = applicationContext ?: return
        synchronized(cacheLock) {
            try {
                File(context.filesDir, TABS_FILE_NAME).writeText(gson.toJson(knownProjectTabs))
                logDebug("💾 Known project tabs persisted to disk.")
            } catch (e: Exception) {
                logError("Failed to persist known tabs to disk: ${e.message}")
            }
        }
    }

    /** Loads the set of known project tabs from a local JSON file into [knownProjectTabs]. This operation is thread-safe.
     * (Full KDoc and implementation remain the same)
     */
    private fun loadTabsFromDisk() {
        val context = applicationContext ?: return
        synchronized(cacheLock) {
            try {
                val file = File(context.filesDir, TABS_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val jsonString = file.readText()
                    if(jsonString.isNotBlank()){
                        val typeToken = object : TypeToken<MutableSet<String>>() {}.type
                        knownProjectTabs = gson.fromJson(jsonString, typeToken) ?: mutableSetOf()
                        logDebug("📦 Known project tabs loaded from disk: ${knownProjectTabs.size} tabs.")
                    } else {
                        logWarn("Tabs file ('$TABS_FILE_NAME') is empty. Initializing with an empty set of tabs.")
                        knownProjectTabs = mutableSetOf()
                    }
                } else {
                    logDebug("Tabs file ('$TABS_FILE_NAME') does not exist or is empty. Initializing with an empty set of tabs.")
                    knownProjectTabs = mutableSetOf()
                }
            } catch (e: JsonSyntaxException) {
                logError("Failed to load tabs from disk due to JSON syntax error: ${e.message}. Deleting corrupted tabs file.")
                File(context.filesDir, TABS_FILE_NAME).delete()
                knownProjectTabs = mutableSetOf()
            }
            catch (e: Exception) {
                logError("Failed to load known tabs from disk: ${e.message}");
                knownProjectTabs = mutableSetOf()
            }
        }
    }

    // --- Logging Helper Methods ---
    private fun logDebug(message: String) { if (debugLogsEnabled) Log.d(TAG, message) }
    private fun logError(message: String) { Log.e(TAG, message) }
    private fun logWarn(message: String) { Log.w(TAG, message) }

    /**
     * Marker annotation for callback parameters that behave like Swift's @escaping.
     * (Full KDoc and implementation remain the same)
     */
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class Escaping

    // --- TODOs / Future Enhancements ---
    // POLLING REMOVED: TODO related to pausing/resuming polling timer is no longer relevant here.
    // - Implement public `clearAllData()` method to wipe all persisted SDK data (SharedPreferences, files).
    // - App lifecycle observers (e.g., using ProcessLifecycleOwner) to:
    //   - Disconnect/reconnect the socket more intelligently based on app visibility.
    // - More granular error reporting to the consuming application (e.g., via a dedicated error SharedFlow or through callbacks in relevant methods).
    // - For more direct Jetpack Compose integration, consider exposing content values
    //   (translations, colors) directly as `StateFlow<String>` or `StateFlow<Color?>`.
    //   This would require managing individual flows for each requested key.
}