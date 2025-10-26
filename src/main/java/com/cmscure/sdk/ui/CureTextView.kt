package com.cmscure.sdk.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
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
 * A drop-in TextView that keeps its text (and optional text color) in sync with CMSCure.
 *
 * Usage in XML:
 * ```xml
 * <com.cmscure.sdk.ui.CureTextView
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:cureKey="welcome_title"
 *     app:cureTab="home_screen"
 *     app:cureDefault="Welcome" />
 * ```
 */
class CureTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null

    private var cureKey: String? = null
    private var cureTab: String? = null
    private var defaultText: String? = null
    private var autoStart: Boolean = true

    private var textColorKey: String? = null
    private var textColorDefault: Int? = null

    init {
        if (attrs != null) {
            context.obtainStyledAttributes(attrs, R.styleable.CureTextView, defStyleAttr, 0).apply {
                cureKey = getString(R.styleable.CureTextView_cureKey)
                cureTab = getString(R.styleable.CureTextView_cureTab)
                defaultText = getString(R.styleable.CureTextView_cureDefault)
                autoStart = getBoolean(R.styleable.CureTextView_cureAutoStart, true)
                textColorKey = getString(R.styleable.CureTextView_cureTextColorKey)
                if (hasValue(R.styleable.CureTextView_cureTextColorDefault)) {
                    textColorDefault = getColor(R.styleable.CureTextView_cureTextColorDefault, currentTextColor)
                }
                recycle()
            }
        }

        // Apply initial values immediately if possible
        refreshText()
        refreshTextColor()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoStart) {
            startObserving(true)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopObserving()
    }

    /** Programmatically binds the view to a CMS translation. */
    fun bind(
        key: String,
        tab: String,
        default: String? = null,
        textColorKey: String? = null,
        textColorDefault: Int? = null,
        autoStart: Boolean = true
    ) {
        this.cureKey = key
        this.cureTab = tab
        this.defaultText = default
        this.textColorKey = textColorKey
        this.textColorDefault = textColorDefault
        this.autoStart = autoStart

        refreshText()
        refreshTextColor()

        if (autoStart && isAttachedToWindow) {
            startObserving(true)
        } else {
            stopObserving()
        }
    }

    /** Stops observing CMS updates. */
    fun unbind() {
        stopObserving()
    }

    private fun startObserving(forceRestart: Boolean) {
        val key = cureKey
        val tab = cureTab
        if (key.isNullOrBlank() || tab.isNullOrBlank()) return

        if (forceRestart) {
            stopObserving()
        } else if (updateJob != null) {
            return
        }

        updateJob = scope.launch {
            CMSCureSDK.contentUpdateFlow.collectLatest { identifier ->
                val shouldUpdateText = identifier == tab || identifier == CMSCureSDK.ALL_SCREENS_UPDATED
                val shouldUpdateColor = textColorKey != null &&
                    (identifier == CMSCureSDK.COLORS_UPDATED || identifier == CMSCureSDK.ALL_SCREENS_UPDATED)

                if (shouldUpdateText) {
                    refreshText()
                }

                if (shouldUpdateColor) {
                    refreshTextColor()
                }
            }
        }
    }

    private fun stopObserving() {
        updateJob?.cancel()
        updateJob = null
        scope.coroutineContext.cancelChildren()
    }

    private fun refreshText() {
        val key = cureKey
        val tab = cureTab
        val resolved = if (key != null && tab != null) {
            CMSCureSDK.translation(forKey = key, inTab = tab).ifEmpty { defaultText.orEmpty() }
        } else {
            defaultText
        }

        if (resolved != null && text.toString() != resolved) {
            text = resolved
        }
    }

    private fun refreshTextColor() {
        val colorKey = textColorKey ?: return
        val resolvedColor = CMSCureSDK.colorValue(forKey = colorKey)?.let { hex ->
            runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
        } ?: textColorDefault

        resolvedColor?.let {
            if (currentTextColor != it) {
                setTextColor(ColorStateList.valueOf(it))
            }
        }
    }
}
