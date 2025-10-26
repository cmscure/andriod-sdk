package com.cmscure.sdk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.collectLatest

/**
 * Returns a [State] that stays in sync with a CMS-managed translation.
 */
@Composable
fun cureString(key: String, tab: String, default: String): State<String> {
    return produceState(initialValue = CMSCureSDK.translation(forKey = key, inTab = tab).ifEmpty { default }) {
        CMSCureSDK.contentUpdateFlow.collectLatest { updatedTab ->
            if (updatedTab == tab || updatedTab == CMSCureSDK.ALL_SCREENS_UPDATED) {
                value = CMSCureSDK.translation(forKey = key, inTab = tab).ifEmpty { default }
            }
        }
    }
}

/** Convenience overload that defaults to an empty string when no value is available. */
@Composable
fun cureString(key: String, tab: String): State<String> = cureString(key, tab, default = "")

/** Provides a Compose-friendly string value that automatically tracks CMS updates. */
@Composable
fun rememberCureString(key: String, tab: String, default: String = ""): String {
    val state by cureString(key, tab, default)
    return state
}

/** Returns a [State] that exposes a CMS-managed color as [Color]. */
@Composable
fun cureColor(key: String, default: Color = Color.Gray): State<Color> {
    fun parseColor(hex: String?) = hex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrElse { default }
    } ?: default

    return produceState(initialValue = parseColor(CMSCureSDK.colorValue(forKey = key))) {
        CMSCureSDK.contentUpdateFlow.collectLatest { id ->
            if (id == CMSCureSDK.COLORS_UPDATED || id == CMSCureSDK.ALL_SCREENS_UPDATED) {
                value = parseColor(CMSCureSDK.colorValue(forKey = key))
            }
        }
    }
}

/** Convenience accessor that returns a [Color] directly. */
@Composable
fun rememberCureColor(key: String, default: Color = Color.Gray): Color {
    val state by cureColor(key, default)
    return state
}

/** Returns a [State] containing a CMS-managed image URL. */
@Composable
fun cureImage(key: String, tab: String? = null, default: String? = null): State<String?> {
    val initialValue = (if (tab == null) {
        CMSCureSDK.imageURL(forKey = key)
    } else {
        CMSCureSDK.translation(forKey = key, inTab = tab).takeIf { it.isNotBlank() }
    }) ?: default

    return produceState(initialValue = initialValue) {
        CMSCureSDK.contentUpdateFlow.collectLatest { updatedIdentifier ->
            val shouldUpdate = updatedIdentifier == CMSCureSDK.ALL_SCREENS_UPDATED ||
                (tab == null && updatedIdentifier == CMSCureSDK.IMAGES_UPDATED) ||
                (tab != null && updatedIdentifier == tab)

            if (shouldUpdate) {
                value = (if (tab == null) {
                    CMSCureSDK.imageURL(forKey = key)
                } else {
                    CMSCureSDK.translation(forKey = key, inTab = tab).takeIf { it.isNotBlank() }
                }) ?: default
            }
        }
    }
}

/** Convenience accessor that returns the latest image URL directly. */
@Composable
fun rememberCureImageUrl(key: String, tab: String? = null, default: String? = null): String? {
    val state by cureImage(key, tab, default)
    return state
}

/** Returns a [State] that tracks items inside a CMS Data Store. */
@Composable
fun cureDataStore(apiIdentifier: String): State<List<DataStoreItem>> {
    return produceState(initialValue = CMSCureSDK.getStoreItems(forIdentifier = apiIdentifier)) {
        CMSCureSDK.syncStore(apiIdentifier) { success ->
            if (success) {
                value = CMSCureSDK.getStoreItems(forIdentifier = apiIdentifier)
            }
        }

        CMSCureSDK.contentUpdateFlow.collectLatest { id ->
            if (id == apiIdentifier || id == CMSCureSDK.ALL_SCREENS_UPDATED) {
                value = CMSCureSDK.getStoreItems(forIdentifier = apiIdentifier)
            }
        }
    }
}

/** Core image Composable used by higher-level helpers. */
@Composable
fun CureSDKImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = CMSCureSDK.applicationContext
    val imageLoader = CMSCureSDK.imageLoader
    if (context == null || imageLoader == null) {
        Box(modifier.background(Color.Gray.copy(alpha = 0.1f)))
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = modifier,
        contentScale = contentScale
    )
}

/** One-line text helper that automatically reacts to CMS updates. */
@Composable
fun CureText(
    key: String,
    tab: String,
    modifier: Modifier = Modifier,
    default: String = "",
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true
) {
    val textValue = rememberCureString(key, tab, default)
    Text(
        text = textValue,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap
    )
}

/** Wraps [CureSDKImage] so developers can drop in a CMS-backed image with one line. */
@Composable
fun CureImage(
    key: String,
    tab: String? = null,
    modifier: Modifier = Modifier,
    defaultUrl: String? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    val imageUrl = rememberCureImageUrl(key, tab, defaultUrl)
    CureSDKImage(
        url = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

/** Applies a CMS-managed background color to the supplied [content]. */
@Composable
fun CureBackground(
    key: String,
    modifier: Modifier = Modifier,
    default: Color = Color.Gray,
    content: @Composable () -> Unit
) {
    val color = rememberCureColor(key, default)
    Box(modifier.background(color)) {
        content()
    }
}
