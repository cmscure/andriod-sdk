package com.cmscure.sdk.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
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
 * Simple view that paints its background using a CMS-managed color.
 */
class CureColorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null

    private var colorKey: String? = null
    @ColorInt private var defaultColor: Int? = null
    private var autoStart: Boolean = true

    init {
        if (attrs != null) {
            context.obtainStyledAttributes(attrs, R.styleable.CureColorView, defStyleAttr, 0).apply {
                colorKey = getString(R.styleable.CureColorView_cureColorKey)
                if (hasValue(R.styleable.CureColorView_cureColorDefault)) {
                    defaultColor = getColor(R.styleable.CureColorView_cureColorDefault, 0)
                }
                autoStart = getBoolean(R.styleable.CureColorView_cureAutoStart, true)
                recycle()
            }
        }

        refreshColor()
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

    fun bindColor(key: String, @ColorInt default: Int? = null, autoStart: Boolean = true) {
        colorKey = key
        defaultColor = default
        this.autoStart = autoStart
        refreshColor()
        if (autoStart && isAttachedToWindow) {
            startObserving(true)
        } else {
            stopObserving()
        }
    }

    fun unbindColor() {
        stopObserving()
    }

    private fun startObserving(forceRestart: Boolean) {
        val key = colorKey ?: return
        if (forceRestart) {
            stopObserving()
        } else if (updateJob != null) {
            return
        }

        updateJob = scope.launch {
            CMSCureSDK.contentUpdateFlow.collectLatest { identifier ->
                val shouldUpdate = identifier == CMSCureSDK.COLORS_UPDATED || identifier == CMSCureSDK.ALL_SCREENS_UPDATED
                if (shouldUpdate) {
                    refreshColor()
                }
            }
        }
    }

    private fun stopObserving() {
        updateJob?.cancel()
        updateJob = null
        scope.coroutineContext.cancelChildren()
    }

    private fun refreshColor() {
        val key = colorKey ?: return
        val resolved = CMSCureSDK.colorValue(forKey = key)?.let { hex ->
            runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
        } ?: defaultColor

        resolved?.let { setBackgroundColor(it) }
    }
}
