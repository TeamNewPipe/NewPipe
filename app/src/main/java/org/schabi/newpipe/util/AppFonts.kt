/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import java.util.WeakHashMap
import org.schabi.newpipe.R

enum class AppFontPreset(val value: String, @StringRes val title: Int, val family: String?) {
    SYSTEM("system", R.string.app_font_system, null),
    SANS_SERIF("sans-serif", R.string.app_font_sans_serif, "sans-serif"),
    SERIF("serif", R.string.app_font_serif, "serif"),
    MONOSPACE("monospace", R.string.app_font_monospace, "monospace");

    companion object {
        fun from(value: String?): AppFontPreset = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

/** Local UI typography. Does not change caption styling or Android-owned surfaces. */
object AppFonts {
    const val KEY = "app_font_family"
    private val roots = WeakHashMap<View, Unit>()
    private val faces = mutableMapOf<FaceKey, Typeface>()
    private val handler = Handler(Looper.getMainLooper())
    private var preferences: SharedPreferences? = null
    private val refreshRoots = Runnable {
        roots.keys.toList().forEach { root -> (root.getTag(R.id.app_font_root) as? RootObserver)?.refresh() }
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == KEY) {
            handler.removeCallbacks(refreshRoots)
            handler.post(refreshRoots)
        }
    }

    @JvmStatic
    fun selected(context: Context): AppFontPreset = AppFontPreset.from(
        try {
            PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, null)
        } catch (_: ClassCastException) {
            null
        }
    )

    fun initialize(application: Application) {
        if (preferences != null) return
        preferences = PreferenceManager.getDefaultSharedPreferences(application).also {
            it.registerOnSharedPreferenceChangeListener(preferenceListener)
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = observe(activity.window.decorView)
            override fun onActivityStarted(activity: Activity) = observe(activity.window.decorView)
            override fun onActivityResumed(activity: Activity) = observe(activity.window.decorView)
            override fun onActivityDestroyed(activity: Activity) {
                (activity.window.decorView.getTag(R.id.app_font_root) as? RootObserver)?.close()
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
    }

    @JvmStatic
    fun prepareContext(context: Context) {
        context.theme.applyStyle(R.style.ThemeOverlay_WizeStream_AppFontInflater, true)
    }

    /** Called by the Material inflater; its attach hook also discovers dialog and popup windows. */
    @JvmStatic
    fun <T : TextView> inflated(view: T): T = view.apply {
        apply(this)
        if (getTag(R.id.app_font_attach_hook) != true) {
            setTag(R.id.app_font_attach_hook, true)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(attached: View) {
                    apply(attached as TextView)
                    observe(attached.rootView)
                }
                override fun onViewDetachedFromWindow(detached: View) = Unit
            })
        }
    }

    @JvmStatic
    fun observeDialog(dialog: Dialog) {
        dialog.window?.decorView?.let(::observe)
    }

    /** One observer per root; weak registry values never retain a root/activity. */
    @JvmStatic
    fun observe(root: View) {
        val existing = root.getTag(R.id.app_font_root) as? RootObserver
        // An inflation batch may attach hundreds of labels to the same window. The layout
        // callback handles new descendants once; do not walk the root for every label.
        if (existing != null) return
        RootObserver(root).also {
            root.setTag(R.id.app_font_root, it)
            roots[root] = Unit
            root.addOnAttachStateChangeListener(it)
            if (root.isAttachedToWindow) it.onViewAttachedToWindow(root)
            it.refresh()
        }
    }

    @JvmStatic
    fun apply(view: TextView) {
        if (!excluded(view)) applyPreset(view, selected(view.context))
    }

    /** Explicit preview calls use this without changing the saved selection. */
    fun applyPreset(view: TextView, preset: AppFontPreset) {
        if (preset == AppFontPreset.SYSTEM && view.getTag(R.id.app_font_original) == null) return
        val state = state(view)
        val next = resolve(preset, state.original)
        if (view.typeface !== next) view.typeface = next
        state.applied = next
    }

    @JvmStatic
    fun applyTree(root: View) = applyTree(root, selected(root.context))

    private fun applyTree(root: View, preset: AppFontPreset) {
        if (root.getTag(R.id.app_font_exempt) == true || root is SubtitleView || root is WebView) return
        (root.getTag(R.id.app_font_root) as? RootObserver)?.remember(preset)
        if (root is TextView) applyPreset(root, preset)
        if (root is ViewGroup) for (index in 0 until root.childCount) applyTree(root.getChildAt(index), preset)
    }

    /** Rebinding supplies its original face, preserving existing System-default behavior. */
    @JvmStatic
    fun applyTypeface(view: TextView, original: Typeface) = setOriginal(view, original)

    @JvmStatic
    fun applyMediaWeight(view: TextView) {
        val original = (view.getTag(R.id.media_original_typeface) as? Typeface)
            ?: originalTypeface(view).also { view.setTag(R.id.media_original_typeface, it) }
        val bold = PreferenceManager.getDefaultSharedPreferences(view.context)
            .getBoolean(view.context.getString(R.string.bold_media_text_key), false)
        setOriginal(view, if (bold) Typeface.create(original, if (original.isItalic) Typeface.BOLD_ITALIC else Typeface.BOLD) else original)
    }

    @JvmStatic
    fun typeface(context: Context, original: Typeface): Typeface = resolve(selected(context), original)

    fun css(context: Context): String = selected(context).family?.let { "body{font-family:$it!important}" }.orEmpty()

    private fun setOriginal(view: TextView, original: Typeface) {
        val state = state(view)
        state.original = original
        state.applied = view.typeface ?: Typeface.DEFAULT
        applyPreset(view, selected(view.context))
    }

    private fun originalTypeface(view: TextView): Typeface = state(view).original

    private fun state(view: TextView): FontState {
        val current = view.typeface ?: Typeface.DEFAULT
        val existing = view.getTag(R.id.app_font_original) as? FontState
        if (existing != null) {
            // Explicit application code may change emphasis when it reuses a view.
            if (current !== existing.applied) {
                existing.original = current
                existing.applied = current
            }
            return existing
        }
        return FontState(current, current).also { view.setTag(R.id.app_font_original, it) }
    }

    private fun resolve(preset: AppFontPreset, original: Typeface): Typeface {
        val family = preset.family ?: return original
        val weight = if (Build.VERSION.SDK_INT >= 28) {
            original.weight
        } else if (original.isBold) {
            700
        } else {
            400
        }
        val key = FaceKey(family, weight, original.isItalic)
        return faces.getOrPut(key) {
            if (Build.VERSION.SDK_INT >= 28) {
                Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, original.isItalic)
            } else {
                Typeface.create(family, original.style)
            }
        }
    }

    private fun excluded(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.getTag(R.id.app_font_exempt) == true || current is SubtitleView) return true
            current = current.parent as? View
        }
        return false
    }

    private data class FaceKey(val family: String, val weight: Int, val italic: Boolean)
    private data class FontState(var original: Typeface, var applied: Typeface)

    private class RootObserver(private val root: View) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        private var observer: ViewTreeObserver? = null
        private var lastPreset = AppFontPreset.SYSTEM
        private var applying = false

        override fun onViewAttachedToWindow(view: View) {
            if (observer?.isAlive != true) {
                observer = root.viewTreeObserver.also { it.addOnGlobalLayoutListener(this) }
            }
            refresh()
        }

        override fun onViewDetachedFromWindow(view: View) {
            observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
            observer = null
        }

        override fun onGlobalLayout() = refresh()

        fun remember(preset: AppFontPreset) {
            // An embedded player can later move to its own window. Remember changes applied
            // by an ancestor observer so System default can also restore it after detaching.
            lastPreset = preset
        }

        fun refresh() {
            if (applying) return
            val preset = selected(root.context)
            // System default incurs no tree traversal until an override actually needs restoring.
            if (preset == AppFontPreset.SYSTEM && lastPreset == AppFontPreset.SYSTEM) return
            var ancestor = root.parent as? View
            while (ancestor != null) {
                if (ancestor.getTag(R.id.app_font_root) is RootObserver) return
                ancestor = ancestor.parent as? View
            }
            applying = true
            try {
                applyTree(root, preset)
                lastPreset = preset
            } finally {
                applying = false
            }
        }

        fun close() {
            onViewDetachedFromWindow(root)
            root.removeOnAttachStateChangeListener(this)
            root.setTag(R.id.app_font_root, null)
            roots.remove(root)
        }
    }
}
