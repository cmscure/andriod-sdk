package com.cmscure.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import coil.ImageLoader
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
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
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.File
import java.lang.reflect.Type
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64


/**
 * The primary singleton object for interacting with the CMSCure backend.
 *
 * This SDK manages content synchronization, language settings, real-time updates via Socket.IO,
 * and provides access to translations, colors, images, and structured data managed within the CMS.
 */
@SuppressLint("ApplySharedPref")
object CMSCureSDK {

    private const val TAG = "CMSCureSDK"
    private const val DEFAULT_SERVER_URL = "https://gateway.cmscure.com"
    private const val DEFAULT_SOCKET_URL = "wss://app.cmscure.com"

    // --- Data Classes for API & Internal Models ---
    data class CureConfiguration(
        val projectId: String,
        val apiKey: String,
        val projectSecret: String,
        val enableAutoRealTimeUpdates: Boolean
    )
    data class ImageAsset(val key: String, val url: String)
    data class ColorItem(val key: String, val value: String)

    private data class EncryptedPayload(val iv: String, val ciphertext: String, val tag: String)
    private data class AuthResult(
        val token: String?,
        @SerializedName("projectSecret") val receivedProjectSecret: String?,
        val tabs: List<String>?,
        val stores: List<String>?,
        val availableLanguages: List<String>?
    )
    private data class TranslationKeyItem(val key: String, val values: Map<String, String>)
    private data class TranslationResponse(val keys: List<TranslationKeyItem>?)
    private data class LanguagesResponse(val languages: List<String>?)
    private data class DataStoreResponse(val items: List<DataStoreItem>)

    private var configuration: CureConfiguration? = null
    private val configLock = Any()
    private var authToken: String? = null
    private var symmetricKey: SecretKeySpec? = null
    var debugLogsEnabled: Boolean = true
    internal var applicationContext: Context? = null
    internal var imageLoader: ImageLoader? = null

    private var cache: MutableMap<String, MutableMap<String, MutableMap<String, String>>> = mutableMapOf()
    private var dataStoreCache: MutableMap<String, List<DataStoreItem>> = mutableMapOf()
    private val cacheLock = Any()
    private var currentLanguage: String = "en"
    private var availableLanguagesCache: MutableList<String> = mutableListOf()
    private var knownProjectTabs: MutableSet<String> = mutableSetOf()
    private var knownDataStoreIdentifiers: MutableSet<String> = mutableSetOf()
    private val autoSubscriptionLock = Any()
    private var autoRegisteredScreens: MutableSet<String> = mutableSetOf()
    private var autoSubscribedDataStores: MutableSet<String> = mutableSetOf()
    private var autoSubscribedColors: Boolean = false
    private var autoSubscribedGlobalImages: Boolean = false

    private const val PREFS_NAME = "CMSCureSDKPrefs"
    private const val KEY_CURRENT_LANGUAGE = "currentLanguage"
    private const val KEY_AUTH_TOKEN = "authToken"
    private const val CACHE_FILE_NAME = "cmscure_cache.json"
    private const val TABS_FILE_NAME = "cmscure_tabs.json"
    private const val DATA_STORE_CACHE_FILE_NAME = "cmscure_datastore_cache.json"
    private const val DATA_STORE_LIST_FILE_NAME = "cmscure_datastore_list.json"
    private const val LANGUAGES_FILE_NAME = "cmscure_languages.json"
    private var sharedPreferences: SharedPreferences? = null

    private var apiService: ApiService? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private val socketLock = Any()
    private val _contentUpdateFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val contentUpdateFlow: SharedFlow<String> = _contentUpdateFlow.asSharedFlow()

    const val ALL_SCREENS_UPDATED = "__ALL_SCREENS_UPDATED__"
    const val COLORS_UPDATED = "__colors__"
    const val IMAGES_UPDATED = "__images__"

    private interface ApiService {
        @POST("/api/sdk/auth")
        suspend fun authenticateSdk(@Body authRequest: Map<String, String>): AuthResult

        @GET("/api/sdk/translations/{projectId}/{tabName}")
        suspend fun getTranslations(
            @Path("projectId") projectId: String,
            @Path("tabName") tabName: String,
            @Header("Authorization") authHeader: String?,
            @Header("X-API-Key") apiKey: String
        ): Response<TranslationResponse>

        @GET("/api/sdk/images/{projectId}")
        suspend fun getImages(
            @Path("projectId") projectId: String,
            @Header("Authorization") authHeader: String?,
            @Header("X-API-Key") apiKey: String
        ): Response<List<ImageAsset>>

        @GET("/api/sdk/store/{projectId}/{apiIdentifier}")
        suspend fun getStore(
            @Path("projectId") projectId: String,
            @Path("apiIdentifier") apiIdentifier: String,
            @Header("Authorization") authHeader: String?,
            @Header("X-API-Key") apiKey: String
        ): Response<DataStoreResponse>
        @GET("/api/sdk/colors/{projectId}")
        suspend fun getColors(
            @Path("projectId") projectId: String,
            @Header("Authorization") authHeader: String?,
            @Header("X-API-Key") apiKey: String
        ): Response<List<ColorItem>>

        @POST("/api/sdk/languages/{projectId}")
        suspend fun getAvailableLanguages(
            @Path("projectId") projectId: String,
            @Header("Authorization") authHeader: String?,
            @Header("X-API-Key") apiKey: String
        ): Response<LanguagesResponse>
    }

    private val gson: Gson = GsonBuilder().registerTypeAdapter(JSONValue::class.java, JSONValue.GsonAdapter()).setLenient().create()


    /**
     * Initializes the SDK with the application context.
     * This method **MUST** be called once, typically in your Application class's `onCreate` method,
     * before calling [configure].
     */
    fun init(context: Context) {
        this.applicationContext = context.applicationContext
        this.sharedPreferences = this.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        this.imageLoader = ImageLoader.Builder(context.applicationContext).build()
        loadPersistedState()
        logDebug("SDK Initialized. Lang: $currentLanguage. Waiting for configure().")
    }

    /**
     * Configures the SDK with necessary project credentials.
     * This method **MUST** be called once after [init].
     */
    fun configure(
        context: Context,
        projectId: String,
        apiKey: String,
        projectSecret: String,
        enableAutoRealTimeUpdates: Boolean = true
    ) {
        if (projectId.isBlank() || apiKey.isBlank() || projectSecret.isBlank()) {
            logError("Config failed: Project ID, API Key, and Project Secret cannot be empty.")
            return
        }

        val serverUrl = try { URL(DEFAULT_SERVER_URL) } catch (e: Exception) {
            logError("Config failed: Invalid server URL"); return
        }

        synchronized(configLock) {
            if (this.configuration != null) {
                logError("Config ignored: SDK already configured.");
                return
            }
            this.configuration = CureConfiguration(projectId, apiKey, projectSecret, enableAutoRealTimeUpdates)
            // Reset auto-subscription tracking whenever we (re)configure
            synchronized(autoSubscriptionLock) {
                autoRegisteredScreens.clear()
                autoSubscribedDataStores.clear()
                autoSubscribedColors = false
                autoSubscribedGlobalImages = false
            }
        }

        deriveSymmetricKey(projectSecret)

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("$TAG-OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY // <- force BODY for now
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
        this.apiService = Retrofit.Builder().baseUrl(serverUrl).client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(ApiService::class.java)

        logDebug("SDK Configured for Project $projectId (auto realtime: $enableAutoRealTimeUpdates)")
        performAuthentication()
    }

    /**
     * Fetches the list of available language codes supported by the configured project from the backend server.
     * If the server request fails, it attempts to provide a list of languages inferred from the local cache.
     *
     * @param completion A callback function that receives a list of language code strings (e.g., `["en", "fr"]`).
     * The list may be empty if no languages can be determined. This callback is invoked on the main (UI) thread.
     */
    fun availableLanguages(completion: (List<String>) -> Unit) {
        val config = getCurrentConfiguration() ?: run {
            logError("GetLangs: SDK not configured.")
            Handler(Looper.getMainLooper()).post { completion(emptyList()) }
            return
        }

        val cachedLanguages = synchronized(cacheLock) { availableLanguagesCache.toList() }
        var deliveredCached = false
        if (cachedLanguages.isNotEmpty()) {
            deliveredCached = true
            Handler(Looper.getMainLooper()).post { completion(cachedLanguages) }
        }

        coroutineScope.launch {
            try {
                val response = apiService?.getAvailableLanguages(config.projectId, currentAuthHeader(), config.apiKey)
                if (response?.isSuccessful == true) {
                    val languagesFromServer = response.body()?.languages?.filter { it.isNotBlank() } ?: emptyList()
                    logDebug("Available languages fetched from server: $languagesFromServer")

                    var shouldDeliver = !deliveredCached
                    if (languagesFromServer.isNotEmpty()) {
                        val changed = synchronized(cacheLock) {
                            if (languagesFromServer != availableLanguagesCache) {
                                availableLanguagesCache = languagesFromServer.toMutableList()
                                true
                            } else {
                                false
                            }
                        }
                        if (changed) {
                            persistLanguagesToDisk()
                            shouldDeliver = true
                        }
                    }

                    if (shouldDeliver) {
                        val result = if (languagesFromServer.isNotEmpty()) languagesFromServer else inferLanguagesFromCache()
                        deliveredCached = true
                        withContext(Dispatchers.Main) { completion(result) }
                    }
                } else {
                    val code = response?.code() ?: -1
                    val errorBody = response?.errorBody()?.string()
                    logError("Failed to fetch available languages. HTTP $code Body=$errorBody")
                    if (!deliveredCached) {
                        val inferred = inferLanguagesFromCache()
                        logDebug("Falling back to languages inferred from cache: $inferred")
                        deliveredCached = true
                        withContext(Dispatchers.Main) { completion(inferred) }
                    }
                }
            } catch (e: Exception) {
                logError("Failed to fetch available languages from server: ${e.message}")
                if (!deliveredCached) {
                    val inferred = inferLanguagesFromCache()
                    logDebug("Falling back to languages inferred from cache: $inferred")
                    deliveredCached = true
                    withContext(Dispatchers.Main) { completion(inferred) }
                }
            }
        }
    }

    private fun inferLanguagesFromCache(): List<String> = synchronized(cacheLock) {
        if (availableLanguagesCache.isNotEmpty()) {
            return@synchronized availableLanguagesCache.toList()
        }

        cache.values.asSequence()
            .flatMap { it.values.asSequence() }
            .flatMap { it.keys.asSequence() }
            .distinct()
            .filter { it != "color" && it != "url" }
            .sorted()
            .toList()
    }

    private fun performAuthentication() {
        val config = getCurrentConfiguration() ?: return
        logDebug("Attempting authentication...")
        coroutineScope.launch {
            try {
                val authResult = apiService?.authenticateSdk(mapOf("apiKey" to config.apiKey, "projectId" to config.projectId))

                if (authResult?.token.isNullOrBlank()) {
                    logError("Auth failed: Response missing token.")
                    return@launch
                }

                authToken = authResult?.token

                // Prefer the backend-provided secret if available, otherwise keep the configured one.
                authResult?.receivedProjectSecret?.takeIf { it.isNotBlank() }?.let { serverSecret ->
                    deriveSymmetricKey(serverSecret)
                    synchronized(configLock) {
                        configuration = configuration?.copy(projectSecret = serverSecret)
                    }
                }

                synchronized(cacheLock) {
                    authResult?.tabs?.let { knownProjectTabs = it.toMutableSet() }
                    authResult?.stores?.let { knownDataStoreIdentifiers = it.toMutableSet() }
                    authResult?.availableLanguages
                        ?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { availableLanguagesCache = it.toMutableList() }
                }

                persistCoreState()
                persistTabsToDisk()
                persistDataStoreListToDisk()
                persistLanguagesToDisk()

                logDebug(
                    "✅ Auth successful. Token: ${authToken?.take(8)}..., Tabs: ${knownProjectTabs.size}, Stores: ${knownDataStoreIdentifiers.size}, Languages: ${availableLanguagesCache.size}"
                )

                connectSocketIfNeeded()
                syncIfOutdated()
            } catch (e: Exception) {
                logError("🆘 Auth exception: ${e.message}")
            }
        }
    }

    private fun deriveSymmetricKey(secret: String) {
        try {
            val secretData = secret.toByteArray(Charsets.UTF_8)
            val hashedSecret = MessageDigest.getInstance("SHA-256").digest(secretData)
            this.symmetricKey = SecretKeySpec(hashedSecret, "AES")
            logDebug("🔑 Symmetric key derived successfully.")
        } catch (e: Exception) { logError("⚠️ Failed to derive symmetric key: ${e.message}") }
    }

    private fun currentAuthHeader(): String? {
        val token = authToken
        return if (!token.isNullOrBlank()) "Bearer $token" else null
    }

    private fun connectSocketIfNeeded() {
        val config = getCurrentConfiguration() ?: return
        if (socket?.connected() == true) return

        synchronized(socketLock) {
            socket?.disconnect()?.off()
            try {
                val opts = IO.Options.builder()
                    .setForceNew(true)
                    .setReconnection(true)
                    .setPath("/socket.io/")
                    .setTransports(arrayOf(io.socket.engineio.client.transports.WebSocket.NAME))
                    .setSecure(true)              // Add this
                    .build()
                val socketUri = URI.create(DEFAULT_SOCKET_URL)
                socket = IO.socket(socketUri, opts)
                setupSocketHandlers(config.projectId)
                socket?.connect()
                logDebug("🔌 Attempting socket connection to: $socketUri")
            } catch (e: Exception) {
                logError("Socket connection setup exception: ${e.message}")
            }
        }
    }

    private fun setupSocketHandlers(projectId: String) {
        socket?.on(Socket.EVENT_CONNECT) {
            logDebug("🟢✅ Socket connected! SID: ${socket?.id()}")
            sendSocketHandshake(projectId)
        }
        socket?.on("handshake_ack") { logDebug("🤝 Handshake Acknowledged.") }
        socket?.on("translationsUpdated") { handleSocketUpdate(it, false) }
        socket?.on("dataStoreUpdated") { handleSocketUpdate(it, true) }
        socket?.on(Socket.EVENT_DISCONNECT) { logDebug("🔌 Socket disconnected.") }
        socket?.on(Socket.EVENT_CONNECT_ERROR) { args -> logError("🆘 Socket connection error: ${args.joinToString()}") }
    }

    private fun handleSocketUpdate(args: Array<Any>, isDataStore: Boolean) {
        val payload = args.firstOrNull() as? org.json.JSONObject
        val identifier = if(isDataStore) payload?.optString("storeApiIdentifier") else payload?.optString("screenName")
        if (!identifier.isNullOrBlank()) {
            if (identifier.equals("__ALL__", ignoreCase = true)) syncIfOutdated()
            else if(isDataStore) syncStore(identifier)
            else sync(identifier)
        }
    }

    private fun sendSocketHandshake(projectId: String) {
        val currentSymKey = symmetricKey ?: run { logError("Handshake failed: Symmetric key is missing."); return }
        logDebug("🤝 Sending handshake for projectId: $projectId")
        try {
            val handshakeBody = mapOf("projectId" to projectId)
            val jsonToEncrypt = gson.toJson(handshakeBody).toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, currentSymKey, gcmSpec)
            val encryptedBytesWithTag = cipher.doFinal(jsonToEncrypt)

            val ciphertext = encryptedBytesWithTag.copyOfRange(0, encryptedBytesWithTag.size - 16)
            val tag = encryptedBytesWithTag.copyOfRange(encryptedBytesWithTag.size - 16, encryptedBytesWithTag.size)

            // Send as a single emit with all data
            val payloadForServer = org.json.JSONObject().apply {
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                put("tag", Base64.encodeToString(tag, Base64.NO_WRAP))
                put("projectId", projectId)
            }

            socket?.emit("handshake", payloadForServer)
            logDebug("Handshake emitted with encrypted payload.")
        } catch (e: Exception) {
            logError("Handshake emission exception: ${e.message}")
        }
    }
    /** Returns a translation for the given key and tab, or an empty string if not found. */
    fun translation(forKey: String, inTab: String): String {
        maybeAutoSubscribeTab(inTab)

        val value = synchronized(cacheLock) {
            cache[inTab]?.get(forKey)?.get(currentLanguage) ?: ""
        }

        if (debugLogsEnabled) {
            if (value.isEmpty()) {
                logDebug("🔍 Translation not found for key='$forKey' tab='$inTab' lang='$currentLanguage'")
            } else {
                logDebug("✅ Translation cache hit for key='$forKey' tab='$inTab' lang='$currentLanguage'")
            }
        }

        return value
    }

    /** Returns a color hex string for the given key, or null if not found. */
    fun colorValue(forKey: String): String? {
        maybeAutoSubscribeColors()

        val color = synchronized(cacheLock) { cache[COLORS_UPDATED]?.get(forKey)?.get("color") }
        if (debugLogsEnabled && color == null) {
            logDebug("🔍 Color not found for key='$forKey'")
        }
        return color
    }

    /** Returns an image URL string for the given global asset key, or null if not found. */
    fun imageURL(forKey: String): String? {
        maybeAutoSubscribeGlobalImages()

        val url = synchronized(cacheLock) { cache[IMAGES_UPDATED]?.get(forKey)?.get("url") }
        if (debugLogsEnabled && url == null) {
            logDebug("🔍 Image URL not found for key='$forKey'")
        }
        return url
    }

    /** Returns a list of all items for a given Data Store from the local cache. */
    fun getStoreItems(forIdentifier: String): List<DataStoreItem> {
        maybeAutoSubscribeStore(forIdentifier)
        return synchronized(cacheLock) { dataStoreCache[forIdentifier] ?: emptyList() }
    }

    /** Sets the active language and triggers a full content refresh. */
    fun setLanguage(languageCode: String, force: Boolean = false) {
        if (languageCode.isBlank() || (languageCode == currentLanguage && !force)) return
        currentLanguage = languageCode
        sharedPreferences?.edit()?.putString(KEY_CURRENT_LANGUAGE, languageCode)?.commit()
        logDebug("🔄 Language changed to '$languageCode'. Triggering full sync.")
        syncIfOutdated()
    }

    /** Gets the current active language code. */
    fun getLanguage(): String = currentLanguage

    /** Indicates whether automatic real-time updates are enabled in the current configuration. */
    fun isAutoRealTimeUpdatesEnabled(): Boolean = synchronized(configLock) {
        configuration?.enableAutoRealTimeUpdates ?: false
    }

    /** Returns a snapshot of tabs/screens that have been auto-subscribed for real-time updates. */
    fun getAutoSubscribedScreens(): Set<String> = synchronized(autoSubscriptionLock) { autoRegisteredScreens.toSet() }

    /** Returns a snapshot of data stores currently auto-subscribed for real-time updates. */
    fun getAutoSubscribedDataStores(): Set<String> = synchronized(autoSubscriptionLock) { autoSubscribedDataStores.toSet() }

    /** Indicates whether the global colors collection is currently auto-subscribed. */
    fun isColorsAutoSubscribed(): Boolean = synchronized(autoSubscriptionLock) { autoSubscribedColors }

    /** Indicates whether the global images collection is currently auto-subscribed. */
    fun isGlobalImagesAutoSubscribed(): Boolean = synchronized(autoSubscriptionLock) { autoSubscribedGlobalImages }

    private fun maybeAutoSubscribeTab(screenName: String) {
        if (!isAutoRealTimeUpdatesEnabled() || screenName.isBlank()) return

        val shouldSync = synchronized(autoSubscriptionLock) {
            if (autoRegisteredScreens.contains(screenName)) {
                false
            } else {
                autoRegisteredScreens.add(screenName)
                true
            }
        }

        if (shouldSync) {
            markTabKnown(screenName)
            sync(screenName)
        }
    }

    private fun maybeAutoSubscribeColors() {
        if (!isAutoRealTimeUpdatesEnabled()) return

        val shouldSync = synchronized(autoSubscriptionLock) {
            if (autoSubscribedColors) {
                false
            } else {
                autoSubscribedColors = true
                true
            }
        }

        if (shouldSync) {
            markTabKnown(COLORS_UPDATED)
            sync(COLORS_UPDATED)
        }
    }

    private fun maybeAutoSubscribeGlobalImages() {
        if (!isAutoRealTimeUpdatesEnabled()) return

        val shouldSync = synchronized(autoSubscriptionLock) {
            if (autoSubscribedGlobalImages) {
                false
            } else {
                autoSubscribedGlobalImages = true
                true
            }
        }

        if (shouldSync) {
            markTabKnown(IMAGES_UPDATED)
            sync(IMAGES_UPDATED)
        }
    }

    private fun maybeAutoSubscribeStore(apiIdentifier: String) {
        if (!isAutoRealTimeUpdatesEnabled() || apiIdentifier.isBlank()) return

        val shouldSync = synchronized(autoSubscriptionLock) {
            if (autoSubscribedDataStores.contains(apiIdentifier)) {
                false
            } else {
                autoSubscribedDataStores.add(apiIdentifier)
                true
            }
        }

        if (shouldSync) {
            markStoreKnown(apiIdentifier)
            syncStore(apiIdentifier)
        }
    }

    private fun markTabKnown(screenName: String) {
        val added = synchronized(cacheLock) { knownProjectTabs.add(screenName) }
        if (added) {
            persistTabsToDisk()
        }
    }

    private fun markStoreKnown(apiIdentifier: String) {
        val added = synchronized(cacheLock) { knownDataStoreIdentifiers.add(apiIdentifier) }
        if (added) {
            persistDataStoreListToDisk()
        }
    }

    /** Manually triggers a sync for a specific screen, or for images/colors using the constants. */
    fun sync(screenName: String, completion: ((Boolean) -> Unit)? = null) {
        when (screenName) {
            IMAGES_UPDATED -> syncImages(completion)
            COLORS_UPDATED -> syncColors(completion)
            else -> syncTranslations(screenName, completion)
        }
    }

    /** Manually triggers a sync for a specific Data Store. */
    fun syncStore(apiIdentifier: String, completion: ((Boolean) -> Unit)? = null) {
        val config = getCurrentConfiguration() ?: run { completion?.invoke(false); return }
        logDebug("🔄 Syncing data store '$apiIdentifier'...")
        coroutineScope.launch {
            var success = false
            try {
                val response = apiService?.getStore(config.projectId, apiIdentifier, currentAuthHeader(), config.apiKey)
                if (response?.isSuccessful == true) {
                    val body = response.body()
                    if (body != null) {
                        synchronized(cacheLock) {
                            dataStoreCache[apiIdentifier] = body.items
                        }
                        markStoreKnown(apiIdentifier)
                        persistDataStoreCacheToDisk()
                        _contentUpdateFlow.tryEmit(apiIdentifier)
                        success = true
                        logDebug("✅ Synced data store '$apiIdentifier' with ${body.items.size} items.")
                    } else {
                        logError("Sync store '$apiIdentifier' returned empty body despite HTTP 200.")
                    }
                } else {
                    val code = response?.code() ?: -1
                    val errorBody = response?.errorBody()?.string()
                    logError("Sync store '$apiIdentifier' failed. HTTP $code Body=$errorBody")
                }
            } catch (e: Exception) { logError("🆘 Sync store '$apiIdentifier' exception: ${e.message}") }
            withContext(Dispatchers.Main) { completion?.invoke(success) }
        }
    }

    private fun syncIfOutdated() {
        val tabs = synchronized(cacheLock) { knownProjectTabs.toList() }
        val stores = synchronized(cacheLock) { knownDataStoreIdentifiers.toList() }

        (tabs + listOf(COLORS_UPDATED, IMAGES_UPDATED))
            .distinct()
            .forEach { sync(it) }

        stores.distinct().forEach { syncStore(it) }
        coroutineScope.launch { _contentUpdateFlow.tryEmit(ALL_SCREENS_UPDATED) }
    }

    private fun syncTranslations(screenName: String, completion: ((Boolean) -> Unit)?) {
        logDebug("Syncing tab '$screenName' (lang=${getLanguage()})")

        val config = getCurrentConfiguration() ?: run { completion?.invoke(false); return }
        logDebug("🔄 Syncing translations for '$screenName'...")
        coroutineScope.launch {
            var success = false
            try {
                val response = apiService?.getTranslations(config.projectId, screenName, currentAuthHeader(), config.apiKey)
                if (response?.isSuccessful == true) {
                    val body = response.body()
                    if (body?.keys != null) {
                        synchronized(cacheLock) {
                            val screenCache = cache.getOrPut(screenName) { mutableMapOf() }
                            body.keys.forEach { item -> screenCache[item.key] = item.values.toMutableMap() }
                        }
                        markTabKnown(screenName)
                        persistCacheToDisk()
                        _contentUpdateFlow.tryEmit(screenName)
                        success = true
                        logDebug("✅ Synced translations for '$screenName' with ${body.keys.size} entries.")
                    } else {
                        logError("Sync translations '$screenName' returned empty keys despite HTTP 200.")
                    }
                } else {
                    val code = response?.code() ?: -1
                    val errorBody = response?.errorBody()?.string()
                    logError("Sync translations '$screenName' failed. HTTP $code Body=$errorBody")
                }
            } catch (e: Exception) { logError("🆘 Sync translations '$screenName' exception: ${e.message}") }
            withContext(Dispatchers.Main) { completion?.invoke(success) }
        }
    }

    private fun syncColors(completion: ((Boolean) -> Unit)?) {
        val config = getCurrentConfiguration() ?: run { completion?.invoke(false); return }
        logDebug("🔄 Syncing colors...")
        coroutineScope.launch {
            var success = false
            try {
                val response = apiService?.getColors(config.projectId, currentAuthHeader(), config.apiKey)
                if (response?.isSuccessful == true) {
                    val colors = response.body()
                    if (colors != null) {
                        synchronized(cacheLock) {
                            val colorCache = cache.getOrPut(COLORS_UPDATED) { mutableMapOf() }
                            colorCache.clear()
                            colors.forEach { color -> colorCache[color.key] = mutableMapOf("color" to color.value) }
                        }
                        markTabKnown(COLORS_UPDATED)
                        persistCacheToDisk()
                        _contentUpdateFlow.tryEmit(COLORS_UPDATED)
                        success = true
                        logDebug("✅ Synced ${colors.size} colors.")
                    } else {
                        logError("Sync colors succeeded with empty body.")
                    }
                } else {
                    val code = response?.code() ?: -1
                    val errorBody = response?.errorBody()?.string()
                    logError("Sync colors failed. HTTP $code Body=$errorBody")
                }
            } catch (e: Exception) { logError("🆘 Sync colors exception: ${e.message}") }
            withContext(Dispatchers.Main) { completion?.invoke(success) }
        }
    }

    private fun syncImages(completion: ((Boolean) -> Unit)?) {
        val config = getCurrentConfiguration() ?: run { completion?.invoke(false); return }
        logDebug("🔄 Syncing image assets...")
        coroutineScope.launch {
            var success = false
            try {
                val response = apiService?.getImages(config.projectId, currentAuthHeader(), config.apiKey)
                if (response?.isSuccessful == true) {
                    val imageAssets = response.body()
                    if (imageAssets != null) {
                        synchronized(cacheLock) {
                            val imageCache = cache.getOrPut(IMAGES_UPDATED) { mutableMapOf() }
                            imageAssets.forEach { asset -> imageCache[asset.key] = mutableMapOf("url" to asset.url) }
                        }
                        persistCacheToDisk()
                        _contentUpdateFlow.tryEmit(IMAGES_UPDATED)
                        success = true
                        logDebug("✅ Synced ${imageAssets.size} image assets.")
                    } else {
                        logError("Sync images succeeded with empty body.")
                    }
                } else {
                    val code = response?.code() ?: -1
                    val errorBody = response?.errorBody()?.string()
                    logError("Sync images failed. HTTP $code Body=$errorBody")
                }
            } catch (e: Exception) { logError("🆘 Sync images exception: ${e.message}") }
            withContext(Dispatchers.Main) { completion?.invoke(success) }
        }
    }

    // --- Persistence Logic ---
    private fun persistCoreState() {
        sharedPreferences?.edit()?.apply {
            putString(KEY_AUTH_TOKEN, authToken)
            putString(KEY_CURRENT_LANGUAGE, currentLanguage)
            commit() // Use commit for synchronous save of critical auth data
        }
        logDebug("Persisted core state (token, language).")
    }

    private fun loadPersistedState() {
        currentLanguage = sharedPreferences?.getString(KEY_CURRENT_LANGUAGE, "en") ?: "en"
        authToken = sharedPreferences?.getString(KEY_AUTH_TOKEN, null)
        loadCacheFromDisk()
        loadTabsFromDisk()
        loadDataStoreCacheFromDisk()
        loadDataStoreListFromDisk()
        loadLanguagesFromDisk()
    }

    private fun persistCacheToDisk() = synchronized(cacheLock) {
        applicationContext?.let { File(it.filesDir, CACHE_FILE_NAME).writeText(gson.toJson(cache)) }
    }

    private fun loadCacheFromDisk() = synchronized(cacheLock) {
        applicationContext?.let {
            try {
                val file = File(it.filesDir, CACHE_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val json = file.readText()
                    val type = object : TypeToken<MutableMap<String, MutableMap<String, MutableMap<String, String>>>>() {}.type
                    cache = gson.fromJson(json, type) ?: mutableMapOf()
                }
            } catch (e: Exception) { logError("Failed to load content cache: ${e.message}") }
        }
    }

    private fun persistTabsToDisk() = synchronized(cacheLock) {
        applicationContext?.let { File(it.filesDir, TABS_FILE_NAME).writeText(gson.toJson(knownProjectTabs)) }
    }

    private fun loadTabsFromDisk() = synchronized(cacheLock) {
        applicationContext?.let {
            try {
                val file = File(it.filesDir, TABS_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val json = file.readText()
                    val type = object : TypeToken<MutableSet<String>>() {}.type
                    knownProjectTabs = gson.fromJson(json, type) ?: mutableSetOf()
                }
            } catch (e: Exception) { logError("Failed to load tabs list: ${e.message}") }
        }
    }

    private fun persistDataStoreCacheToDisk() = synchronized(cacheLock) {
        applicationContext?.let { File(it.filesDir, DATA_STORE_CACHE_FILE_NAME).writeText(gson.toJson(dataStoreCache)) }
    }

    private fun loadDataStoreCacheFromDisk() = synchronized(cacheLock) {
        applicationContext?.let {
            try {
                val file = File(it.filesDir, DATA_STORE_CACHE_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val json = file.readText()
                    val type = object : TypeToken<MutableMap<String, List<DataStoreItem>>>() {}.type
                    dataStoreCache = gson.fromJson(json, type) ?: mutableMapOf()
                }
            } catch (e: Exception) { logError("Failed to load Data Store cache: ${e.message}") }
        }
    }

    private fun persistDataStoreListToDisk() = synchronized(cacheLock) {
        applicationContext?.let { File(it.filesDir, DATA_STORE_LIST_FILE_NAME).writeText(gson.toJson(knownDataStoreIdentifiers)) }
    }

    private fun loadDataStoreListFromDisk() = synchronized(cacheLock) {
        applicationContext?.let {
            try {
                val file = File(it.filesDir, DATA_STORE_LIST_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val json = file.readText()
                    val type = object : TypeToken<MutableSet<String>>() {}.type
                    knownDataStoreIdentifiers = gson.fromJson(json, type) ?: mutableSetOf()
                }
            } catch(e: Exception) { logError("Failed to load Data Store list: ${e.message}") }
        }
    }

    private fun persistLanguagesToDisk() = synchronized(cacheLock) {
        applicationContext?.let { File(it.filesDir, LANGUAGES_FILE_NAME).writeText(gson.toJson(availableLanguagesCache)) }
    }

    private fun loadLanguagesFromDisk() = synchronized(cacheLock) {
        applicationContext?.let {
            try {
                val file = File(it.filesDir, LANGUAGES_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val json = file.readText()
                    val type = object : TypeToken<MutableList<String>>() {}.type
                    availableLanguagesCache = gson.fromJson(json, type) ?: mutableListOf()
                }
            } catch (e: Exception) { logError("Failed to load languages cache: ${e.message}") }
        }
    }

    private fun getCurrentConfiguration(): CureConfiguration? = synchronized(configLock) { configuration }
    private fun logDebug(message: String) { if (debugLogsEnabled) Log.d(TAG, message) }
    private fun logError(message: String) { Log.e(TAG, message) }
}

/**
 * A sealed class representing different types of JSON values that can be stored
 * in a Data Store item. This allows for type-safe access to data.
 */
sealed class JSONValue {
    data class StringValue(val value: String) : JSONValue()
    data class IntValue(val value: Int) : JSONValue()
    data class DoubleValue(val value: Double) : JSONValue()
    data class BoolValue(val value: Boolean) : JSONValue()
    data class LocalizedStringValue(val values: Map<String, String>) : JSONValue()
    object NullValue : JSONValue()

    val stringValue: String? get() = (this as? StringValue)?.value
    val intValue: Int? get() = (this as? IntValue)?.value
    val doubleValue: Double? get() = (this as? DoubleValue)?.value
    val boolValue: Boolean? get() = (this as? BoolValue)?.value

    /** Returns the string for the current language, falling back to English or the first available. */
    val localizedString: String? get() {
        return if (this is LocalizedStringValue) {
            values[CMSCureSDK.getLanguage()] ?: values["en"] ?: values.values.firstOrNull()
        } else stringValue
    }

    internal class GsonAdapter : JsonSerializer<JSONValue>, JsonDeserializer<JSONValue> {
        override fun serialize(src: JSONValue?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return when (src) {
                is StringValue -> JsonPrimitive(src.value)
                is IntValue -> JsonPrimitive(src.value)
                is DoubleValue -> JsonPrimitive(src.value)
                is BoolValue -> JsonPrimitive(src.value)
                is LocalizedStringValue -> context?.serialize(src.values) ?: Gson().toJsonTree(src.values)
                else -> Gson().toJsonTree(null)
            }
        }
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): JSONValue {
            return when {
                json == null || json.isJsonNull -> NullValue
                json.isJsonPrimitive -> {
                    val primitive = json.asJsonPrimitive
                    when {
                        primitive.isBoolean -> BoolValue(primitive.asBoolean)
                        primitive.isString -> StringValue(primitive.asString)
                        primitive.isNumber -> {
                            val numStr = primitive.asString
                            if (numStr.contains(".")) DoubleValue(numStr.toDouble()) else IntValue(numStr.toInt())
                        }
                        else -> NullValue
                    }
                }
                json.isJsonObject -> {
                    val mapType = object : TypeToken<Map<String, String>>() {}.type
                    val localizedValues: Map<String, String> = context?.deserialize(json, mapType) ?: emptyMap()
                    LocalizedStringValue(localizedValues)
                }
                else -> NullValue
            }
        }
    }
}

// --- Jetpack Compose Integration ---

/**
 * Represents a single item within a Data Store.
 * @property id The unique identifier of the item.
 * @property data A map where keys are your field names and values are the content, wrapped in a [JSONValue].
 * @property createdAt The timestamp of when the item was created.
 * @property updatedAt The timestamp of the last update.
 */
data class DataStoreItem(
    @SerializedName("_id") val id: String,
    val data: Map<String, JSONValue>,
    val createdAt: String,
    val updatedAt: String
)
