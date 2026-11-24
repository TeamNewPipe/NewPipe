package org.schabi.newpipe.views

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.graphics.Insets
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import kotlin.math.min
import kotlin.math.roundToInt
import org.schabi.newpipe.R
import org.schabi.newpipe.util.DeviceUtils

/** Keeps the optional floating surface inside system insets without changing navigation semantics. */
class FloatingBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.bottomNavigationStyle
) : BottomNavigationView(context, attrs, defStyleAttr) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val styleKey = context.getString(R.string.bottom_navigation_style_key)
    private val spacingKey = context.getString(R.string.floating_navigation_spacing_key)
    private val classicOutline = outlineProvider
    private val classicClipping = clipToOutline
    private val classicElevation = elevation
    private val initialMargins = Rect()
    private var safeInsets = Insets.NONE
    private var hasInsets = false
    private var floating = false
    private var extraBottom = 0
    private var reportedHeight = -1
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == styleKey || key == spacingKey) refreshAppearance()
    }
    private val floatingOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
        }
    }

    var onOccupiedHeightChanged: Runnable? = null

    val occupiedHeight: Int
        get() = (height.takeIf { it > 0 } ?: resources.getDimensionPixelSize(R.dimen.main_bottom_navigation_height)) + extraBottom

    init {
        refreshAppearance()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshAppearance()
    }

    override fun onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateMargins()
        notifyHeightChanged()
    }

    fun applySystemInsets(left: Int, top: Int, right: Int, bottom: Int, insets: Insets) {
        initialMargins.set(left, top, right, bottom)
        safeInsets = insets
        hasInsets = true
        updateMargins()
    }

    private fun refreshAppearance() {
        floating = !DeviceUtils.isTv(context) && preferences.getString(styleKey, "classic") == "floating"
        extraBottom = if (floating) dp(preferences.getInt(spacingKey, DEFAULT_SPACING_DP).coerceIn(0, MAX_SPACING_DP)) else 0
        outlineProvider = if (floating) floatingOutline else classicOutline
        clipToOutline = floating || classicClipping
        elevation = if (floating) dp(6).toFloat() else classicElevation
        invalidateOutline()
        updateMargins()
        notifyHeightChanged()
    }

    private fun updateMargins() {
        if (!hasInsets) return
        val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val parentWidth = (parent as? View)?.width ?: 0
        val available = parentWidth - safeInsets.left - safeInsets.right - initialMargins.left - initialMargins.right
        // Preserve space for every configured destination on narrow windows.
        val side = if (floating) {
            if (parentWidth > 0) min(dp(12), ((available - menu.size() * dp(48)) / 2).coerceAtLeast(0)) else dp(12)
        } else {
            0
        }
        val left = initialMargins.left + safeInsets.left + side
        val top = initialMargins.top + safeInsets.top
        val right = initialMargins.right + safeInsets.right + side
        val bottom = initialMargins.bottom + safeInsets.bottom + extraBottom
        if (params.leftMargin != left || params.topMargin != top || params.rightMargin != right || params.bottomMargin != bottom) {
            params.setMargins(left, top, right, bottom)
            layoutParams = params
        }
    }

    private fun notifyHeightChanged() {
        val current = occupiedHeight
        if (current != reportedHeight) {
            reportedHeight = current
            onOccupiedHeightChanged?.run()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val DEFAULT_SPACING_DP = 8
        const val MAX_SPACING_DP = 24
    }
}

/** Height reserved inside already inset page content; the system-bar inset is added by its owner. */
object MainNavigationGeometry {
    @JvmStatic
    fun occupiedBottomHeight(navigation: View): Int = when (navigation) {
        is NavigationRailView -> 0
        is FloatingBottomNavigationView -> navigation.occupiedHeight
        else -> navigation.height.takeIf { it > 0 } ?: navigation.resources.getDimensionPixelSize(R.dimen.main_bottom_navigation_height)
    }
}
