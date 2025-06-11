package com.cmscure.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import coil.ImageLoader
import coil.request.ImageRequest
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
import retrofit2.http.GET
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
     */
    data class CureConfiguration(
        val projectId: String,
        val apiKey: String,
        val projectSecret: String
    )
    data class ImageAsset(val key: String, val url: String)

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
    private var serverUrlString: String = "https://app.cmscure.com" // Default production server URL
    private var socketIOURLString: String = "wss://app.cmscure.com"

    // Retrofit service interface definition for API calls
    private interface ApiService {
        @POST("/api/sdk/auth")
        suspend fun authenticateSdk(@Body authRequest: AuthRequestPlain): AuthResult

        @POST("/api/sdk/translations/{projectId}/{tabName}")
        suspend fun getTranslations(
            @Path("projectId") projectId: String,
            @Path("tabName") tabName: String,
            @Header("X-API-Key") apiKey: String,
            @Body requestBody: EncryptedPayload
        ): TranslationResponse

        @POST("/api/sdk/languages/{projectId}")
        suspend fun getAvailableLanguages(
            @Path("projectId") projectId: String,
            @Header("X-API-Key") apiKey: String,
            @Body requestBody: Map<String, String>
        ): LanguagesResponse

        @GET("/api/sdk/images/{projectId}")
        suspend fun getImages(
            @Path("projectId") projectId: String,
            @Header("X-API-Key") apiKey: String
        ): List<ImageAsset>
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
    private val mainThreadHandler = Handler(Looper.getMainLooper()) // Handler for posting results to the main (UI) thread
    private var applicationContext: Context? = null // Android Application Context
    private val gson: Gson = GsonBuilder().setLenient().create() // Lenient Gson for robust JSON parsing
    private var imageLoader: ImageLoader? = null

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

    const val IMAGES_UPDATED = "__IMAGES_UPDATED__" // Event name for image updates

    // MutableSharedFlow for broadcasting content update events.
    // replay=0: New subscribers don't get old values.
    // extraBufferCapacity=1: Allows emitting one item even if there are no active collectors,
    // preventing suspension if emit is called from a non-suspending context rapidly.
    private val _contentUpdateFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    /**
     * A [SharedFlow] that emits events indicating content updates.
     *
     * Subscribers will receive:
     * - A `screenName` (String) when content for a specific tab/screen is updated.
     * - The [COLORS_UPDATED] constant when global color data is updated.
     * - The [ALL_SCREENS_UPDATED] constant when a general refresh affecting multiple screens is suggested (e.g., after language change or initial full sync).
     *
     * This flow is designed for UI components (both Jetpack Compose and traditional XML Views)
     * to observe content changes and trigger UI refreshes. Collection should typically occur
     * within a lifecycle-aware scope (e.g., `LaunchedEffect` in Compose, `lifecycleScope.launch` in Activities/Fragments).
     *
     * **Jetpack Compose Example:**
     * ```kotlin
     * LaunchedEffect(Unit) {
     * CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
     * Log.d("MyComposable", "Content updated for: $updatedIdentifier")
     * when (updatedIdentifier) {
     * CMSCureSDK.COLORS_UPDATED -> { /* Refresh colors */ }
     * CMSCureSDK.ALL_SCREENS_UPDATED -> { /* Refresh all relevant UI data */ }
     * "mySpecificScreen" -> { /* Refresh data for 'mySpecificScreen' */ }
     * else -> { /* Optionally refresh based on other screen names */ }
     * }
     * // Example: myColorState = CMSCureSDK.colorValue("my_color_key").toComposeColor()
     * //          myTextState = CMSCureSDK.translation("my_text_key", "my_screen")
     * }
     * }
     * ```
     *
     * **XML/View-based UI Example (Activity/Fragment):**
     * ```kotlin
     * override fun onCreate(savedInstanceState: Bundle?) {
     * super.onCreate(savedInstanceState)
     * // ... setContentView ...
     * lifecycleScope.launch {
     * CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
     * Log.d("MyActivity", "SDK Update: $updatedIdentifier")
     * // Manually update your TextViews, ImageViews, View backgrounds, etc.
     * // Example:
     * // textView.text = CMSCureSDK.translation("welcome_message", "home")
     * //
     * // val hexColor = CMSCureSDK.colorValue("my_bg_color")
     * // hexColor?.let {
     * //     try { myView.setBackgroundColor(android.graphics.Color.parseColor(it)) }
     * //     catch (e: IllegalArgumentException) { Log.e("MyActivity", "Invalid color hex: $it") }
     * // }
     * }
     * }
     * }
     * ```
     */
    val contentUpdateFlow: SharedFlow<String> = _contentUpdateFlow.asSharedFlow()


    /**
     * Initializes the SDK with the application context.
     * This method **MUST** be called once, typically in your Application class's `onCreate` method,
     * before calling [configure]. It prepares the SDK for use by setting up persistence mechanisms
     * and loading any previously saved state (like language preference, tokens, and cached data).
     *
     * @param context The application context. It will be used to obtain `applicationContext`
     * to avoid holding onto Activity or Service contexts.
     */
    fun init(context: Context) {
        this.applicationContext = context.applicationContext
        this.sharedPreferences = this.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        this.imageLoader = ImageLoader.Builder(context.applicationContext)
            .respectCacheHeaders(false)
            .build()
        loadPersistedState()
        logDebug("SDK Initialized. Current Language: $currentLanguage. Waiting for configure() call.")
    }

    /**
     * Configures the CMSCureSDK with necessary project credentials and server details.
     * This method **MUST** be called once after [init], typically in your Application class's `onCreate` method.
     * Subsequent calls to `configure` will be ignored if the SDK is already configured.
     *
     * Upon successful configuration, the SDK will:
     * 1. Store the provided configuration.
     * 2. Set up the network layer (Retrofit).
     * 3. Derive cryptographic keys from the provided `projectSecret`.
     * 4. Attempt a legacy authentication flow with the backend.
     * 5. If authentication is successful, it re-derives keys based on server-confirmed secrets,
     * establishes a Socket.IO connection for real-time updates, and performs an initial content sync.
     *
     * @param context The application context.
     * @param projectId Your unique Project ID from the CMSCure dashboard.
     * @param apiKey Your secret API Key from the CMSCure dashboard, used for authenticating API requests.
     * @param projectSecret Your Project Secret from the CMSCure dashboard, used for initial key derivation,
     * legacy encryption, and socket handshake. The server may confirm or provide an updated
     * secret during authentication, which will then be used.
     * @throws IllegalArgumentException if projectId, apiKey, or projectSecret are empty.
     * @throws MalformedURLException if serverUrlString is invalid.
     * @see [init] Must be called before this method.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Required for Base64 encoding used in crypto and potentially newer TLS features.
    // Also, some internal methods like sync() are marked with this due to encryption.
    fun configure(
        context: Context, projectId: String, apiKey: String, projectSecret: String,
        serverUrlString: String = this.serverUrlString, // Default production server URL
        socketIOURLString: String = this.socketIOURLString  // Default production socket URL
    ) {
        // --- Input Validation ---
        if (projectId.isEmpty()) { logError("Config failed: Project ID cannot be empty."); return }
        if (apiKey.isEmpty()) { logError("Config failed: API Key cannot be empty."); return }
        if (projectSecret.isEmpty()) { logError("Config failed: Project Secret cannot be empty."); return }

        val serverUrl = try { URL(serverUrlString) } catch (e: MalformedURLException) {
            logError("Config failed: Invalid server URL '$serverUrlString': ${e.message}"); return
        }
        // Socket URL string is validated for basic non-blank. URI.create() in connectSocketIfNeeded handles full parsing.
        if (socketIOURLString.isBlank()) {
            logError("Config failed: Invalid socket URL (blank string)"); return
        }
        // Basic scheme check for socket URL for logging purposes.
        if (!socketIOURLString.startsWith("ws://", ignoreCase = true) && !socketIOURLString.startsWith("wss://", ignoreCase = true) &&
            !socketIOURLString.startsWith("http://", ignoreCase = true) && !socketIOURLString.startsWith("https://", ignoreCase = true)) { // Allow http/https for initial handshake if proxying
            logWarn("Socket URL '$socketIOURLString' does not start with ws(s):// or http(s)://. Connection might fail or be insecure.")
        }

        // TODO: Add build type check (e.g., !BuildConfig.DEBUG) to enforce HTTPS/WSS for production builds.
        // Example:
        // val isDebugBuild = true // Replace with actual BuildConfig.DEBUG check from consuming app
        // if (!isDebugBuild) {
        //     if (serverUrl.protocol != "https") { logError("Config failed: Server URL must use HTTPS for production."); return }
        //     if (!socketIOURLString.startsWith("wss://", ignoreCase = true)) { logError("Config failed: Socket URL must use WSS for production."); return }
        // }

        val newConfiguration = CureConfiguration(projectId, apiKey, projectSecret)
        synchronized(configLock) { // Thread-safe assignment of configuration
            if (this.configuration != null) { logError("Config ignored: SDK already configured."); return }
            this.configuration = newConfiguration
            this.applicationContext = context.applicationContext // Ensure context is current
            // Initialize SharedPreferences if init() wasn't called or context changed (defensive)
            if (this.sharedPreferences == null) this.sharedPreferences = this.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        logDebug("SDK Configured: Project $projectId")
        logDebug("   - API Base URL: ${serverUrl.toExternalForm()}")
        logDebug("   - Socket URL String: $socketIOURLString")

        // --- Setup Network Layer (Retrofit) ---
        val loggingInterceptor = HttpLoggingInterceptor { message -> if (debugLogsEnabled) Log.d("$TAG-OkHttp", message) }
            .apply { level = if (debugLogsEnabled) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
        this.apiService = Retrofit.Builder().baseUrl(serverUrl).client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(ApiService::class.java)

        // Derive initial symmetric key from the projectSecret provided during configuration.
        // This key is primarily for the initial authentication handshake or as a fallback if auth fails.
        // The operational key is typically re-derived after successful authentication
        // from the server-confirmed projectSecret.
        deriveSymmetricKey(projectSecret, "initial configuration")

        performLegacyAuthenticationAndConnect() // Handles auth, key re-derivation, socket connect, initial sync
        // It's now called after successful authentication in performLegacyAuthenticationAndConnect.
    }

    /**
     * Derives the AES symmetric key from a given secret string using SHA-256.
     * The derived key is stored in [symmetricKey], and the secret string used for this derivation
     * is stored in [apiSecret] (this [apiSecret] is also persisted).
     *
     * @param secret The secret string (usually a projectSecret) to derive the key from.
     * @param contextMessage A message for logging to indicate the context of this key derivation
     * (e.g., "initial configuration", "auth response", "persisted state").
     */
    private fun deriveSymmetricKey(secret: String, contextMessage: String) {
        try {
            val secretData = secret.toByteArray(Charsets.UTF_8)
            // SHA-256 hash of the secret will produce a 32-byte key, suitable for AES-256.
            val hashedSecret = MessageDigest.getInstance("SHA-256").digest(secretData)
            this.symmetricKey = SecretKeySpec(hashedSecret, "AES")
            // Store the actual secret string that was used to derive this current symmetricKey.
            // This is important because this apiSecret is persisted and reloaded.
            this.apiSecret = secret
            logDebug("🔑 Symmetric key derived successfully from projectSecret ($contextMessage).")
        } catch (e: Exception) {
            logError("⚠️ Failed to derive symmetric key ($contextMessage): ${e.message}");
            this.symmetricKey = null // Ensure key is null on failure
        }
    }

    /**
     * Retrieves the current SDK configuration. For internal SDK use or for an application to check
     * if the SDK has been configured before attempting fallback configuration.
     * @return The current [CureConfiguration] or `null` if [configure] has not been successfully called.
     */
    internal fun getCurrentConfiguration(): CureConfiguration? = synchronized(configLock) { configuration }


    /**
     * Performs the legacy authentication process with the backend.
     * This involves sending the API key and Project ID. On success, it receives an auth token
     * and a server-confirmed `projectSecret`. The SDK then re-derives its symmetric encryption key
     * using this server-confirmed `projectSecret`.
     * It also fetches the initial list of project tabs.
     * Upon successful authentication, it initiates the Socket.IO connection, performs an initial data sync
     * This method is called internally after [configure] and should not be called directly by the app.
     */
    @RequiresApi(Build.VERSION_CODES.O) // For Base64 used in crypto if request/response were encrypted (not current auth)
    // and for syncIfOutdated() and connectSocketIfNeeded()
    private fun performLegacyAuthenticationAndConnect() {
        val currentConfig = getCurrentConfiguration() ?: run { logError("Auth: SDK not configured."); return }
        logDebug("Attempting legacy authentication with server...")
        coroutineScope.launch { // Launch on the SDK's IO dispatcher
            try {
                val authPayload = AuthRequestPlain(currentConfig.apiKey, currentConfig.projectId)
                val authResult = apiService?.authenticateSdk(authPayload)

                if (authResult?.token != null && authResult.receivedProjectSecret != null) {
                    authToken = authResult.token
                    // CRITICAL: Re-derive the operational symmetric key using the projectSecret
                    // received from the authentication response. This ensures the key matches
                    // what the server expects for subsequent encrypted communications (like socket handshake).
                    deriveSymmetricKey(authResult.receivedProjectSecret, "auth response")

                    synchronized(cacheLock) { // Thread-safe update of known tabs
                        knownProjectTabs.clear() // Clear any old tabs
                        authResult.tabs?.let { serverTabs -> knownProjectTabs.addAll(serverTabs) }
                    }
                    persistSensitiveState() // Persists authToken and the new apiSecret (used for key derivation)
                    persistTabsToDisk()     // Persists the new list of known project tabs

                    logDebug("✅ Auth successful. Token: ${authToken?.take(8)}..., Known Tabs: ${knownProjectTabs.size}")

                    connectSocketIfNeeded() // Attempt to connect socket now that auth is done
                    syncIfOutdated()        // Perform initial full sync of all content
                } else {
                    logError("Auth failed: Response missing token or projectSecret. Response: $authResult")
                }
            } catch (e: HttpException) { // Specific catch for Retrofit HTTP errors
                logError("🆘 Auth HTTP exception: ${e.code()} - ${e.message()}. Response Body: ${e.response()?.errorBody()?.string()}"); e.printStackTrace()
            } catch (e: Exception) { // Catch other potential exceptions (network, serialization, etc.)
                logError("🆘 Auth exception: ${e.message}"); e.printStackTrace()
            }
        }
    }

    /**
     * Sets the current active language for retrieving translations from the SDK.
     *
     * When the language is changed:
     * 1. The new language preference is persisted locally.
     * 2. The SDK attempts to synchronize content for all known tabs (including colors) in the new language.
     * 3. The [contentUpdateFlow] emits [ALL_SCREENS_UPDATED] to signal a general UI refresh.
     * Individual sync operations will also emit specific screen name updates to the flow.
     *
     * @param languageCode The IETF language tag (e.g., "en", "fr", "es-MX") to set as active.
     * If blank, the call is ignored.
     * @param force If `true`, forces the language change and subsequent data sync even if the
     * `languageCode` is the same as the current active language. Defaults to `false`.
     *
     * **XML/View-based UI Integration:**
     * After calling `setLanguage`, your UI components should observe [contentUpdateFlow].
     * When [ALL_SCREENS_UPDATED] (or specific screen names) are emitted, re-fetch all displayed
     * translations using [translation], [colorValue], and [imageUrl] and update your Views.
     * ```kotlin
     * CMSCureSDK.setLanguage("fr")
     * // In your observer for contentUpdateFlow:
     * // myTextView.text = CMSCureSDK.translation("greeting", "home_screen")
     * ```
     */
    @RequiresApi(Build.VERSION_CODES.O) // Because sync() is @RequiresApi(O) due to encryption
    fun setLanguage(languageCode: String, force: Boolean = false) {
        if (languageCode.isBlank()) { logWarn("SetLanguage: Attempted to set a blank language code. Call ignored."); return }
        if (languageCode == currentLanguage && !force) {
            logDebug("SetLanguage: Language '$languageCode' is already active and not forced. No change.")
            return
        }

        val oldLanguage = currentLanguage
        currentLanguage = languageCode
        // Persist immediately using commit for reliability, as language is a critical setting.
        sharedPreferences?.edit()?.putString(KEY_CURRENT_LANGUAGE, languageCode)?.commit()
        logDebug("🔄 Language changed from '$oldLanguage' to '$languageCode'.")

        // Sync all known tabs and colors for the new language.
        // The sync function itself will emit updates to contentUpdateFlow for each tab.
        val tabsToUpdate = synchronized(cacheLock) { knownProjectTabs.toList() + listOf("__colors__") }.distinct()
        logDebug("SetLanguage: Syncing tabs for new language: $tabsToUpdate")
        tabsToUpdate.forEach { tabName ->
            sync(if (tabName == "__colors__") COLORS_UPDATED else tabName) { success -> // Use constant for colors tab
                if (success) logDebug("Successfully synced tab '$tabName' for new language '$languageCode'.")
                else logError("Failed to sync tab '$tabName' for new language '$languageCode'.")
            }
        }
        // After all individual syncs have been initiated, also emit a general "all screens updated"
        // event because a language change typically affects the entire UI.
        coroutineScope.launch {
            logDebug("SetLanguage: Emitting ALL_SCREENS_UPDATED due to language change.")
            _contentUpdateFlow.tryEmit(ALL_SCREENS_UPDATED) // tryEmit is non-suspending
        }
    }

    /**
     * Retrieves the currently active language code being used by the SDK.
     * @return The current language code (e.g., "en"). Defaults to "en" if not set.
     */
    fun getLanguage(): String = currentLanguage

    /**
     * Fetches the list of available language codes supported by the configured project from the backend server.
     * If the server request fails, it attempts to provide a list of languages inferred from the local cache.
     *
     * @param completion A callback function that receives a list of language code strings (e.g., `["en", "fr"]`).
     * The list may be empty if no languages can be determined.
     * This callback is invoked on the main (UI) thread.
     */
    fun availableLanguages(completion: @Escaping (List<String>) -> Unit) {
        val config = getCurrentConfiguration() ?: run { logError("GetLangs: SDK not configured."); mainThreadHandler.post { completion(emptyList()) }; return }
        if (authToken == null) { logError("GetLangs: Not authenticated. Cannot fetch languages."); mainThreadHandler.post { completion(emptyList()) }; return }

        coroutineScope.launch {
            try {
                // The API key is passed as a header by the OkHttp interceptor or should be added here if not.
                // The body for this request as per backend is just {"projectId": "id"}
                val response = apiService?.getAvailableLanguages(config.projectId, config.apiKey, mapOf("projectId" to config.projectId))
                val languagesFromServer = response?.languages ?: emptyList()
                logDebug("Available languages fetched from server: $languagesFromServer")
                mainThreadHandler.post { completion(languagesFromServer) }
            } catch (e: Exception) {
                logError("Failed to fetch available languages from server: ${e.message}")
                // Fallback to languages present in cache keys if API fails
                val cachedLangs = synchronized(cacheLock) {
                    cache.values.asSequence() // Use sequence for potentially large caches
                        .flatMap { screenData -> screenData.values.asSequence() }      // Sequence of [Key -> [LangCode -> Value]]
                        .flatMap { langValueMap -> langValueMap.keys.asSequence() } // Sequence of all LangCodes
                        .distinct()
                        .filter { it != "color" } // Exclude the special key "color" used for the __colors__ tab
                        .toList()
                        .sorted() // Sort for consistent ordering
                }
                logDebug("Falling back to languages inferred from cache: $cachedLangs")
                mainThreadHandler.post { completion(cachedLangs) }
            }
        }
    }

    /**
     * Retrieves a translation for a specific key within a given tab (screen name),
     * using the language currently set by [setLanguage] (or default "en").
     * Returns an empty string if the translation is not found in the cache. This method is thread-safe.
     *
     * @param forKey The key for the desired translation (e.g., "welcome_message").
     * @param inTab The name of the tab/screen where the translation key is located (e.g., "home_screen").
     * @return The translated string for the current language, or an empty string if not found.
     *
     * **XML/View-based UI Integration:**
     * ```kotlin
     * val myText = CMSCureSDK.translation("greeting_key", "main_screen")
     * myTextView.text = myText
     * ```
     * Remember to re-fetch and update your UI when [contentUpdateFlow] signals changes
     * for `inTab` or [ALL_SCREENS_UPDATED].
     */
    fun translation(forKey: String, inTab: String): String {
        synchronized(cacheLock) { // Thread-safe access to cache
            return cache[inTab]?.get(forKey)?.get(currentLanguage) ?: ""
        }
    }

    /**
     * Retrieves a color hex string (e.g., "#RRGGBB" or "#AARRGGBB") for a given global color key.
     * Colors are typically stored in a special internal tab named `__colors__`.
     * Returns `null` if the color key is not found in the cache. This method is thread-safe.
     *
     * @param forKey The key for the desired color (e.g., "primary_background").
     * @return The color hex string or `null` if not found.
     *
     * **XML/View-based UI Integration:**
     * ```kotlin
     * val hexColor = CMSCureSDK.colorValue("primary_app_theme_color")
     * if (hexColor != null) {
     * try {
     * myView.setBackgroundColor(android.graphics.Color.parseColor(hexColor))
     * } catch (e: IllegalArgumentException) {
     * Log.e("MyApp", "Invalid color hex from CMS: $hexColor")
     * // Optionally set a default color
     * }
     * }
     * ```
     * Remember to re-fetch and update your UI when [contentUpdateFlow] signals [COLORS_UPDATED]
     * or [ALL_SCREENS_UPDATED].
     */
    fun colorValue(forKey: String): String? {
        synchronized(cacheLock) { // Thread-safe access to cache
            // Colors are stored in the "__colors__" tab, with "color" as the effective "language" key for the actual hex value.
            return cache["__colors__"]?.get(forKey)?.get("color") // CORRECTED: Use literal "__colors__"
        }
    }

    /**
     * Retrieves a [URL] for an image associated with a given key and tab.
     * The SDK expects the value for this key (obtained via [translation]) to be a valid URL string.
     * Returns `null` if the translation for the key is not found, is empty, or is not a valid URL.
     * This method is thread-safe.
     *
     * @param forKey The key whose value is the image URL string.
     * @param inTab The name of the tab/screen where the image URL key is located.
     * @return A [URL] object if a valid URL string is found, otherwise `null`.
     *
     * **XML/View-based UI Integration (using an image loading library like Glide or Picasso):**
     * ```kotlin
     * val imageUrl = CMSCureSDK.imageUrl("hero_banner_image", "home_screen")
     * if (imageUrl != null) {
     * Glide.with(context).load(imageUrl.toString()).into(myImageView)
     * } else {
     * myImageView.setImageResource(R.drawable.placeholder_image) // Set a placeholder
     * }
     * ```
     * Remember to re-fetch and update your UI when [contentUpdateFlow] signals changes.
     */
    /** Retrieves a URL for an image stored as a value within a translations tab. */
    fun imageUrl(forKey: String, inTab: String): URL? {
        val urlString = translation(forKey, inTab)
        return try { if (urlString.isNotBlank()) URL(urlString) else null } catch (e: Exception) { null }
    }

    /** Retrieves a URL for a globally managed image asset. */
    fun imageURL(forKey: String): URL? {
        val urlString = synchronized(cacheLock) { cache["__images__"]?.get(forKey)?.get("url") }
        return try { if (urlString?.isNotBlank() == true) URL(urlString) else null } catch (e: Exception) { null }
    }

    /**
     * Fetches the latest content (translations or colors) for a specific screen name (tab) from the backend.
     * The request body sent to the server is encrypted.
     * Upon successful synchronization, the local cache is updated, persisted to disk,
     * and an event is emitted to [contentUpdateFlow] with the `screenName`
     * (or [COLORS_UPDATED] if syncing the colors tab).
     *
     * This method requires the SDK to be configured and authenticated.
     *
     * @param screenName The name of the tab/screen to synchronize. For the global colors tab,
     * it's recommended to use the [COLORS_UPDATED] constant, which will be
     * translated internally to the `__colors__` tab name.
     * @param completion An optional callback function `(Boolean) -> Unit` that indicates
     * whether the synchronization and cache update were successful.
     * This callback is invoked on the main (UI) thread.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Required for encryption operations
    fun sync(screenName: String, completion: ((Boolean) -> Unit)? = null) {
        when (screenName) {
            IMAGES_UPDATED -> syncImages(completion)
            else -> syncTranslations(screenName, completion)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun syncImages(completion: ((Boolean) -> Unit)? = null) {
        val config = getCurrentConfiguration() ?: run { completion?.invoke(false); return }
        logDebug("🔄 Syncing global image assets...")
        coroutineScope.launch {
            var success = false
            try {
                val imageAssets = apiService?.getImages(config.projectId, config.apiKey)
                if (imageAssets != null) {
                    logDebug("✅ Fetched ${imageAssets.size} image assets from server.")
                    synchronized(cacheLock) {
                        val newImageCache: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
                        imageAssets.forEach { asset -> newImageCache[asset.key] = mutableMapOf("url" to asset.url) }
                        cache["__images__"] = newImageCache
                    }
                    persistCacheToDisk()
                    prefetchImages(imageAssets.mapNotNull { it.url })
                    success = true
                    _contentUpdateFlow.tryEmit(IMAGES_UPDATED)
                }
            } catch (e: Exception) { logError("🆘 Sync images exception: ${e.message}") }
            mainThreadHandler.post { completion?.invoke(success) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun syncTranslations(screenName: String, completion: ((Boolean) -> Unit)? = null) {
        // Internally, the colors tab is always referred to as "__colors__".
        // If COLORS_UPDATED event name is passed, map it to the actual tab name for API calls and caching.
        val effectiveScreenName = if (screenName == COLORS_UPDATED) "__colors__" else screenName

        val config = getCurrentConfiguration() ?: run { logError("Sync $effectiveScreenName: SDK not configured."); mainThreadHandler.post{ completion?.invoke(false)}; return }
        if (authToken == null) { logError("Sync $effectiveScreenName: Not authenticated."); mainThreadHandler.post{ completion?.invoke(false)}; return }
        // Symmetric key is essential for encrypting the request body for sync.
        if (symmetricKey == null) { logError("Sync $effectiveScreenName: SymmetricKey missing for encryption."); mainThreadHandler.post{ completion?.invoke(false)}; return }

        logDebug("🔄 Syncing tab '$effectiveScreenName'...")
        coroutineScope.launch {
            var success = false
            try {
                // The body for the sync request needs to be encrypted.
                val bodyToEncrypt = mapOf("projectId" to config.projectId, "screenName" to effectiveScreenName)
                val encryptedBody = encryptPayload(bodyToEncrypt, config.projectId) // Pass projectId for potential inclusion in payload
                    ?: throw IOException("Encryption failed for sync request body for tab '$effectiveScreenName'")

                val response = apiService?.getTranslations(config.projectId, effectiveScreenName, config.apiKey, encryptedBody)

                if (response?.keys != null) {
                    synchronized(cacheLock) { // Thread-safe update of the cache
                        val screenCache = cache.getOrPut(effectiveScreenName) { mutableMapOf() }
                        response.keys.forEach { translationItem ->
                            val keyData = screenCache.getOrPut(translationItem.key) { mutableMapOf() }
                            keyData.clear() // Clear old language values for this key
                            keyData.putAll(translationItem.values) // Add new/updated language values
                        }
                        // Add to knownProjectTabs only if it's a regular content tab (not internal like __colors__)
                        if (!knownProjectTabs.contains(effectiveScreenName) && effectiveScreenName != "__colors__") {
                            knownProjectTabs.add(effectiveScreenName)
                            persistTabsToDisk() // Persist the updated set of known tabs
                        }
                    }
                    persistCacheToDisk() // Persist the entire cache after updates
                    logDebug("✅ Synced and updated cache for tab '$effectiveScreenName'.")
                    success = true
                    // Emit event to notify UI of update for this specific screen.
                    // Use COLORS_UPDATED event name if it was the colors tab that was synced.
                    _contentUpdateFlow.tryEmit(if (effectiveScreenName == "__colors__") COLORS_UPDATED else effectiveScreenName)
                } else {
                    logError("Sync $effectiveScreenName: Response was null or contained no keys.")
                }
            } catch (e: HttpException) { // Specific catch for Retrofit HTTP errors
                logError("🆘 Sync $effectiveScreenName HTTP exception: ${e.code()} - ${e.message()}. Response Body: ${e.response()?.errorBody()?.string()}"); e.printStackTrace()
            } catch (e: Exception) { // Catch other potential exceptions
                logError("🆘 Sync $effectiveScreenName exception: ${e.message}"); e.printStackTrace()
            }
            // Invoke completion handler on the main thread
            mainThreadHandler.post{ completion?.invoke(success) }
        }
    }

    /**
     * Triggers a synchronization for all known project tabs and the special `__colors__` tab.
     * This is typically called on app start (after configuration and authentication),
     * after a language change, or by the socket events to ensure all
     * relevant content is up-to-date.
     * Emits [ALL_SCREENS_UPDATED] to [contentUpdateFlow] after attempting to sync all tabs.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Required due to calling sync()
    private fun syncIfOutdated() {
        val tabsToSync = synchronized(cacheLock) { knownProjectTabs.toList() }.distinct()
        val specialContent = listOf(COLORS_UPDATED, IMAGES_UPDATED)
        (tabsToSync + specialContent).forEach { sync(it) }
        coroutineScope.launch { _contentUpdateFlow.tryEmit(ALL_SCREENS_UPDATED) }
    }

    private fun prefetchImages(urls: List<String>) {
        val loader = imageLoader ?: return
        if (urls.isEmpty()) return
        logDebug("🖼️ Coil: Starting pre-fetch for ${urls.size} images.")
        urls.forEach { url ->
            val request = ImageRequest.Builder(applicationContext!!).data(url).build()
            loader.enqueue(request)
        }
    }


    /**
     * Establishes or re-establishes the Socket.IO connection if not already connected and acknowledged.
     * This method is typically called after successful authentication or when the app becomes active.
     * Requires the SDK to be configured.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Required for sendSocketHandshake() due to encryption
    fun connectSocketIfNeeded() {
        val config = getCurrentConfiguration() ?: run { logError("SocketConnect: SDK not configured."); return }

        // Check current socket state to avoid redundant operations
        if (socket?.connected() == true && handshakeAcknowledged) {
            logDebug("Socket already connected & handshake acknowledged. No action needed.")
            return
        }
        // If connected but handshake not done, try handshake again
        if (socket?.connected() == true && !handshakeAcknowledged) {
            logDebug("Socket connected, but handshake not acknowledged. Attempting handshake.")
            sendSocketHandshake()
            return
        }

        synchronized(socketLock) { // Ensure thread-safe socket manipulation
            // Clean up any existing, non-functional socket instance before creating a new one.
            socket?.disconnect()?.off() // Remove all listeners and disconnect
            socket = null               // Release the old socket instance

            try {
                val opts = IO.Options().apply {
                    forceNew = true // Ensures a new connection attempt, not reusing a potentially stale one
                    reconnection = true // Enable automatic reconnections
                    path = "/socket.io/" // Standard Socket.IO connection path
                    // Force WebSocket transport
                    transports = arrayOf(io.socket.engineio.client.transports.WebSocket.NAME)
                    // TODO: For WSS with self-signed certificates in a development environment,
                    // you might need to configure a custom SSLContext for OkHttp and pass it to Socket.IO options.
                    // For production with valid certificates, this is usually not needed.
                }
                val socketUri = URI.create(this.socketIOURLString) // Use the configured socket URL string
                logDebug("🔌 Attempting to connect socket to: $socketUri (path ${opts.path})")
                socket = IO.socket(socketUri, opts) // Create new Socket.IO client instance
                setupSocketHandlers() // Register event listeners for the new socket instance
                socket?.connect()     // Initiate the connection attempt
            } catch (e: Exception) { // Catch errors during URI parsing or IO.socket() creation
                logError("Socket connection setup exception: ${e.message}"); e.printStackTrace()
            }
        }
    }

    /**
     * Sets up standard event handlers (listeners) for the Socket.IO client instance.
     * This includes connection events, disconnection, errors, and custom server-sent events
     * like "handshake_ack" and "translationsUpdated".
     */
    @RequiresApi(Build.VERSION_CODES.O) // Required for sendSocketHandshake() and syncIfOutdated()
    private fun setupSocketHandlers() {
        socket?.on(Socket.EVENT_CONNECT) {
            logDebug("🟢✅ Socket connected! SID: ${socket?.id()}");
            handshakeAcknowledged = false; // Reset on new connection
            sendSocketHandshake() // Attempt handshake after connection
        }
        socket?.on("handshake_ack") { args ->
            logDebug("🤝 'handshake_ack' event received. Data: ${args.joinToString { it?.toString() ?: "null" }}");
            handshakeAcknowledged = true; // Mark handshake as successful
            syncIfOutdated() // Perform a full content sync after successful handshake
        }
        socket?.on("translationsUpdated") { args ->
            logDebug("📡 'translationsUpdated' event received. Data: ${args.joinToString { it?.toString() ?: "null" }}");
            handleSocketTranslationUpdate(args) // Process the content update
        }
        socket?.on(Socket.EVENT_DISCONNECT) { args ->
            logDebug("🔌 Socket disconnected. Reason: ${args.joinToString { it?.toString() ?: "null" }}");
            handshakeAcknowledged = false // Reset handshake status
        }
        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.getOrNull(0) // Error object is usually the first argument
            logError("🆘 Socket connection error: $error");
            (error as? Exception)?.printStackTrace() // Print stack trace if it's an Exception
        }
    }

    /**
     * Processes "translationsUpdated" events received from the socket.
     * Determines which screen/tab was updated and triggers a sync for it.
     * If "__ALL__" is received, triggers a sync for all outdated content.
     *
     * @param data The array of data received with the socket event. Expected to contain
     * a JSON object or Map with a "screenName" property.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Required for sync methods
    private fun handleSocketTranslationUpdate(data: Array<Any>) {
        try {
            val firstElement = data.firstOrNull()
            // Try to parse screenName from org.json.JSONObject (common from socket.io-client-java)
            // or from a Kotlin Map (if auto-converted by a layer like Gson with Socket.IO)
            val screenNameToUpdate: String? = when (firstElement) {
                is org.json.JSONObject -> firstElement.optString("screenName", null)
                is Map<*, *> -> firstElement["screenName"] as? String
                else -> null
            }

            if (screenNameToUpdate != null) {
                logDebug("Processing 'translationsUpdated' socket event for tab: '$screenNameToUpdate'")
                if (screenNameToUpdate.equals("__ALL__", ignoreCase = true)) {
                    syncIfOutdated() // Triggers sync for all tabs and emits ALL_SCREENS_UPDATED
                } else {
                    // If the update is specifically for colors (identified by "__colors__" tab name from server),
                    // use the COLORS_UPDATED constant when calling sync. This ensures sync() uses the
                    // correct internal tab name ("__colors__") and emits the correct event (COLORS_UPDATED)
                    // to contentUpdateFlow.
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
     * The handshake payload includes the projectId and is encrypted using AES/GCM
     * with the symmetric key derived from the server-confirmed `projectSecret`.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Required for encryption
    private fun sendSocketHandshake() {
        val config = getCurrentConfiguration()
        val currentSymKey = symmetricKey // Use the key derived from the auth response's projectSecret
        if (config == null || currentSymKey == null) {
            logError("Handshake cannot be sent: Missing configuration or symmetric key. Ensure auth completed and re-derived key."); return
        }
        logDebug("🤝 Sending encrypted handshake for projectId: ${config.projectId}")
        try {
            val handshakeBody = mapOf("projectId" to config.projectId) // Data to encrypt
            val encryptedPayload = encryptPayload(handshakeBody, config.projectId) // Encrypt it
                ?: run { logError("Handshake failed: Could not encrypt payload."); return }

            // Convert the EncryptedPayload data class to a JSON string, then to org.json.JSONObject
            // as the Socket.IO Java client expects this for emitting complex objects.
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
     * @return `true` if connected and handshake is acknowledged, `false` otherwise.
     */
    fun isConnected(): Boolean = socket?.connected() == true && handshakeAcknowledged

    /**
     * Encrypts a given payload map using AES/GCM with the current symmetric key.
     *
     * @param payloadMap The data to be encrypted, as a `Map<String, Any>`.
     * @param projectIdForPayload Optional project ID to include within the [EncryptedPayload] structure.
     * This is useful if the backend endpoint expects it alongside the encrypted data.
     * @return An [EncryptedPayload] data class containing Base64 encoded IV, ciphertext, and tag,
     * or `null` if encryption fails.
     *
     * @throws IllegalStateException if the symmetric key ([symmetricKey]) is not initialized.
     * @throws Exception for other cryptographic errors.
     */
    @RequiresApi(Build.VERSION_CODES.O) // For Base64.getEncoder and Cipher operations
    private fun encryptPayload(payloadMap: Map<String, Any>, projectIdForPayload: String?): EncryptedPayload? {
        val currentSymKey = symmetricKey ?: run { logError("Encryption failed: SymmetricKey is not available."); return null }
        return try {
            val jsonDataToEncrypt = gson.toJson(payloadMap).toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding") // Standard AES/GCM mode
            // Generate a new, random 12-byte Initialization Vector (IV) / Nonce for each encryption.
            // 12 bytes is recommended for GCM for performance and security.
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

            // GCMParameterSpec: (tag length in bits, IV byte array)
            // 128-bit authentication tag is common and strong.
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, currentSymKey, gcmSpec)

            val encryptedBytesWithTag = cipher.doFinal(jsonDataToEncrypt) // Output includes ciphertext + authentication tag

            // In Java's AES/GCM implementation, the authentication tag (16 bytes for a 128-bit tag)
            // is appended to the end of the ciphertext by the doFinal() method.
            // We need to separate them as the backend expects iv, ciphertext, and tag as distinct fields.
            val ciphertext = encryptedBytesWithTag.copyOfRange(0, encryptedBytesWithTag.size - 16) // -16 bytes for the tag
            val tag = encryptedBytesWithTag.copyOfRange(encryptedBytesWithTag.size - 16, encryptedBytesWithTag.size)

            EncryptedPayload(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(tag),
                projectId = projectIdForPayload // Include projectId if needed by the specific endpoint
            )
        } catch (e: Exception) {
            logError("Encryption exception: ${e.message}"); e.printStackTrace(); null
        }
    }

    /**
     * Loads persisted SDK state (current language, auth token, API secret, cached content, and known tabs)
     * from SharedPreferences and local files. This is called during [init].
     * If a persisted API secret is found, it attempts to derive the symmetric key from it.
     */
    private fun loadPersistedState() {
        currentLanguage = sharedPreferences?.getString(KEY_CURRENT_LANGUAGE, "en") ?: "en"
        authToken = sharedPreferences?.getString(KEY_AUTH_TOKEN, null)
        val persistedApiSecret = sharedPreferences?.getString(KEY_API_SECRET, null)

        // If a secret was persisted (likely from a previous successful auth),
        // derive the symmetric key from it for use until a new auth potentially updates it.
        if (persistedApiSecret != null && persistedApiSecret.isNotBlank()) { // Also check if not blank
            deriveSymmetricKey(persistedApiSecret, "persisted state")
        }
        loadCacheFromDisk() // Loads content cache
        loadTabsFromDisk()  // Loads known project tabs
        logDebug("Loaded persisted state: Lang=$currentLanguage, Token=${authToken!=null}, ApiSecret=${apiSecret!=null} (key derived: ${symmetricKey!=null}), Tabs=${knownProjectTabs.size}")
    }

    /**
     * Persists sensitive state like the auth token and the API secret (used for key derivation)
     * to SharedPreferences. Uses `commit()` for synchronous write to ensure data is saved
     * before critical operations that might depend on this state proceed.
     */
    private fun persistSensitiveState() {
        sharedPreferences?.edit()?.apply {
            putString(KEY_AUTH_TOKEN, authToken)
            // Persist the apiSecret that was used to derive the current symmetricKey.
            // This is crucial for re-deriving the key correctly on app restart.
            putString(KEY_API_SECRET, apiSecret)
            commit() // Use commit for sensitive data to ensure it's written before dependent operations
        }
        logDebug("Persisted sensitive state (token, apiSecret).")
    }

    /** Persists the in-memory content cache ([cache]) to a local JSON file. This operation is thread-safe. */
    private fun persistCacheToDisk() {
        val context = applicationContext ?: return // Need context for file operations
        synchronized(cacheLock) { // Synchronize access to the cache map
            try {
                File(context.filesDir, CACHE_FILE_NAME).writeText(gson.toJson(cache))
                logDebug("💾 Cache persisted to disk.")
            } catch (e: Exception) { logError("Failed to persist content cache to disk: ${e.message}") }
        }
    }

    /** Loads the content cache from a local JSON file into the in-memory [cache]. This operation is thread-safe. */
    private fun loadCacheFromDisk() {
        val context = applicationContext ?: return
        synchronized(cacheLock) { // Synchronize access to the cache map
            try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (file.exists() && file.length() > 0) { // Check if file exists and is not empty
                    val jsonString = file.readText()
                    if(jsonString.isNotBlank()){ // Double check content before parsing
                        val typeToken = object : TypeToken<MutableMap<String, MutableMap<String, MutableMap<String, String>>>>() {}.type
                        cache = gson.fromJson(jsonString, typeToken) ?: mutableMapOf() // Fallback to empty map on parse error
                        logDebug("📦 Cache loaded from disk. Contains ${cache.size} screen(s)/tab(s).")
                    } else { logWarn("Cache file ('$CACHE_FILE_NAME') is empty. Initializing with an empty cache."); cache = mutableMapOf() }
                } else {
                    logDebug("Cache file ('$CACHE_FILE_NAME') does not exist or is empty. Initializing with an empty cache.")
                    cache = mutableMapOf() // Ensure cache is initialized if file doesn't exist
                }
            } catch (e: JsonSyntaxException) { // Catch specific JSON parsing errors
                logError("Failed to load cache from disk due to JSON syntax error: ${e.message}. Deleting corrupted cache file.")
                File(context.filesDir, CACHE_FILE_NAME).delete() // Delete the corrupted file to prevent future errors
                cache = mutableMapOf() // Reset to empty cache
            }
            catch (e: Exception) { // Catch other file I/O or general errors
                logError("Failed to load content cache from disk: ${e.message}");
                cache = mutableMapOf() // Reset to empty cache on other errors
            }
        }
    }

    /** Persists the set of known project tabs ([knownProjectTabs]) to a local JSON file. This operation is thread-safe. */
    private fun persistTabsToDisk() {
        val context = applicationContext ?: return
        synchronized(cacheLock) { // knownProjectTabs is often modified in conjunction with cache
            try {
                File(context.filesDir, TABS_FILE_NAME).writeText(gson.toJson(knownProjectTabs))
                logDebug("💾 Known project tabs persisted to disk.")
            } catch (e: Exception) {
                logError("Failed to persist known tabs to disk: ${e.message}")
            }
        }
    }

    /** Loads the set of known project tabs from a local JSON file into [knownProjectTabs]. This operation is thread-safe. */
    private fun loadTabsFromDisk() {
        val context = applicationContext ?: return
        synchronized(cacheLock) { // Synchronize access to knownProjectTabs
            try {
                val file = File(context.filesDir, TABS_FILE_NAME) // Corrected: context.filesDir
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
                    knownProjectTabs = mutableSetOf() // Ensure set is initialized
                }
            } catch (e: JsonSyntaxException) {
                logError("Failed to load tabs from disk due to JSON syntax error: ${e.message}. Deleting corrupted tabs file.")
                File(context.filesDir, TABS_FILE_NAME).delete() // Delete corrupted file
                knownProjectTabs = mutableSetOf() // Reset to empty set
            }
            catch (e: Exception) {
                logError("Failed to load known tabs from disk: ${e.message}");
                knownProjectTabs = mutableSetOf() // Reset to empty set on other errors
            }
        }
    }

    // --- Logging Helper Methods ---
    private fun logDebug(message: String) { if (debugLogsEnabled) Log.d(TAG, message) }
    private fun logError(message: String) { Log.e(TAG, message) } // Errors are generally always logged
    private fun logWarn(message: String) { Log.w(TAG, message) } // Warnings are also generally always logged

    /**
     * Marker annotation for callback parameters that behave like Swift's @escaping.
     * This is for documentation purposes only and is not enforced by the Kotlin compiler.
     * It indicates that the function passed as an argument might be stored or called later,
     * potentially after the outer function has returned.
     */
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class Escaping

    // --- TODOs / Future Enhancements ---
    // - Implement public `clearAllData()` method to wipe all persisted SDK data (SharedPreferences, files).
    // - App lifecycle observers (e.g., using ProcessLifecycleOwner) to:
    //   - Disconnect/reconnect the socket more intelligently based on app visibility.
    // - More granular error reporting to the consuming application (e.g., via a dedicated error SharedFlow or through callbacks in relevant methods).
    // - For more direct Jetpack Compose integration, consider exposing content values
    //   (translations, colors) directly as `StateFlow<String>` or `StateFlow<Color?>`.
    //   This would require managing individual flows for each requested key.
}