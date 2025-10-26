package com.cmscure.sdk.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import coil.ImageLoader
import coil.request.ImageRequest
import com.cmscure.sdk.CMSCureSDK
import com.cmscure.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ImageView that automatically keeps its image in sync with CMSCure content.
 */
class CureImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null

    private var cureKey: String? = null
    private var cureTab: String? = null
    private var defaultUrl: String? = null
    private var placeholderResId: Int = 0

    init {
        if (attrs != null) {
            context.obtainStyledAttributes(attrs, R.styleable.CureImageView, defStyleAttr, 0).apply {
                cureKey = getString(R.styleable.CureImageView_cureImageKey)
                cureTab = getString(R.styleable.CureImageView_cureImageTab)
                defaultUrl = getString(R.styleable.CureImageView_cureImageDefaultUrl)
                placeholderResId = getResourceId(R.styleable.CureImageView_curePlaceholder, 0)
                recycle()
            }
        }

        refreshImage()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startObserving(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopObserving()
    }

    /** Programmatically binds this view to a CMS image. */
    fun bindImage(key: String, tab: String? = null, defaultUrl: String? = null, placeholderResId: Int = 0) {
        this.cureKey = key
        this.cureTab = tab
        this.defaultUrl = defaultUrl
        this.placeholderResId = placeholderResId

        refreshImage()
        if (isAttachedToWindow) {
            startObserving(true)
        }
    }

    fun unbindImage() {
        stopObserving()
    }

    private fun startObserving(forceRestart: Boolean) {
        val key = cureKey ?: return

        if (forceRestart) {
            stopObserving()
        } else if (updateJob != null) {
            return
        }

        updateJob = scope.launch {
            CMSCureSDK.contentUpdateFlow.collectLatest { identifier ->
                val tab = cureTab
                val shouldUpdate = identifier == CMSCureSDK.ALL_SCREENS_UPDATED ||
                    identifier == CMSCureSDK.IMAGES_UPDATED ||
                    (tab != null && identifier == tab)

                if (shouldUpdate) {
                    refreshImage()
                }
            }
        }
    }

    private fun stopObserving() {
        updateJob?.cancel()
        updateJob = null
        scope.coroutineContext.cancelChildren()
    }

    private fun refreshImage() {
        val url = currentUrl()
        if (url.isNullOrBlank()) {
            if (placeholderResId != 0) {
                setImageResource(placeholderResId)
            } else {
                setImageDrawable(null)
            }
            return
        }

        val loader = CMSCureSDK.imageLoader ?: ImageLoader.Builder(context.applicationContext).build().also {
            CMSCureSDK.imageLoader = it
        }

        val requestBuilder = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)

        if (placeholderResId != 0) {
            requestBuilder.placeholder(placeholderResId)
        }

        loader.enqueue(
            requestBuilder
                .target { drawable -> setImageDrawable(drawable) }
                .build()
        )
    }

    private fun currentUrl(): String? {
        val key = cureKey ?: return null
        val tab = cureTab

        val resolved = if (tab.isNullOrBlank()) {
            CMSCureSDK.imageURL(forKey = key)
        } else {
            CMSCureSDK.translation(forKey = key, inTab = tab).takeIf { it.isNotBlank() }
        }

        return resolved ?: defaultUrl
    }
}
