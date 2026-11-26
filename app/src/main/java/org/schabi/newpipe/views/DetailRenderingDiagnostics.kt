package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.themes.ThemePacks
import org.schabi.newpipe.util.AppFonts

/** Opt-in, main-thread diagnostics. Stored snapshots never retain views or media text. */
object DetailRenderingDiagnostics {
    private const val ARM_TIMEOUT_MS = 120_000L
    private const val CAPTURE_DURATION_MS = 30_000L
    private const val SAMPLE_INTERVAL_MS = 500L
    private const val MAX_SAMPLES = 60
    private const val MAX_REPORT_CHARS = 128_000
    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null

    @JvmStatic
    fun isAvailable(): Boolean = BuildConfig.DEBUG && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q

    fun startCapture(context: Context): Boolean {
        if (!isAvailable()) return false
        clear()
        val pack = ThemePacks.active(context)
        val environment = JSONObject()
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("fontPreset", AppFonts.selected(context).name)
            .put("themePack", pack != null)
            .put("glass", pack?.glass != null)
            .put("skin", pack?.skin != null)
        session = Session(profileId(context), SystemClock.uptimeMillis(), environment.toString())
        return true
    }

    fun clear() {
        session?.let { handler.removeCallbacksAndMessages(it.token) }
        session = null
    }

    fun report(): String? {
        val current = session ?: return null
        expire(current, SystemClock.uptimeMillis())
        val result = JSONObject()
            .put("diagnostic", "android10-detail-rendering")
            .put("state", current.state)
            .put("environment", JSONObject(current.environment))
            .put("sampleCount", current.sampleCount)
            .put("omittedFrames", 0)
            .put(
                "frames",
                JSONArray().apply {
                    current.frames.forEach { frame ->
                        put(
                            JSONObject()
                                .put("firstMs", frame.firstMs)
                                .put("lastMs", frame.lastMs)
                                .put("samples", frame.samples)
                                .put("views", JSONObject(frame.snapshot))
                        )
                    }
                }
            )
        // Bound explicit clipboard exports and keep recent states if frames need to be omitted.
        val frames = result.getJSONArray("frames")
        var text = result.toString(2)
        while (text.length > MAX_REPORT_CHARS && frames.length() > 1) {
            frames.remove(0)
            result.put("omittedFrames", current.frames.size - frames.length())
            text = result.toString(2)
        }
        return text
    }

    @JvmStatic
    fun observe(fragment: Fragment, root: View) {
        if (isAvailable()) Sampler(root, fragment.viewLifecycleOwner.lifecycle)
    }

    private fun profileId(context: Context): String = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        .getString(ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY, ProfileManager.DEFAULT_PROFILE_ID)
        ?: ProfileManager.DEFAULT_PROFILE_ID

    private fun stop(current: Session, state: String) {
        current.state = state
        handler.removeCallbacksAndMessages(current.token)
    }

    private fun expire(current: Session, now: Long) {
        if (current.state == "armed" && now - current.armedAt >= ARM_TIMEOUT_MS) stop(current, "expired")
        val startedAt = current.startedAt
        if (current.state == "capturing" && startedAt != null && now - startedAt >= CAPTURE_DURATION_MS) stop(current, "completed")
    }

    private class Sampler(private var root: View?, private val lifecycle: Lifecycle) : DefaultLifecycleObserver {
        private val sample = object : Runnable {
            override fun run() {
                val view = root ?: return
                val current = session ?: return
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                val now = SystemClock.uptimeMillis()
                expire(current, now)
                if (current.state != "armed" && current.state != "capturing") return
                try {
                    if (profileId(view.context) != current.profileId) {
                        stop(current, "profile_changed")
                        return
                    }
                    if (current.startedAt == null) current.startedAt = now
                    current.state = "capturing"
                    val elapsed = now - requireNotNull(current.startedAt)
                    val snapshot = capture(view)
                    val previous = current.frames.lastOrNull()
                    if (previous?.snapshot == snapshot) {
                        previous.lastMs = elapsed
                        previous.samples++
                    } else {
                        current.frames += Frame(elapsed, elapsed, 1, snapshot)
                    }
                    current.sampleCount++
                    if (current.sampleCount >= MAX_SAMPLES) stop(current, "completed")
                } catch (_: Exception) {
                    // A diagnostic must not interrupt playback or export exception messages.
                    stop(current, "capture_failed")
                }
                if (current.state == "capturing") handler.postAtTime(this, current.token, now + SAMPLE_INTERVAL_MS)
            }
        }

        init {
            lifecycle.addObserver(this)
        }

        override fun onResume(owner: LifecycleOwner) = sample.run()

        override fun onPause(owner: LifecycleOwner) {
            handler.removeCallbacks(sample)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            handler.removeCallbacks(sample)
            root = null
            lifecycle.removeObserver(this)
        }
    }

    private fun capture(root: View): String {
        val result = JSONObject()
            .put("fontScale", root.resources.configuration.fontScale.finiteValue())
            .put("densityDpi", root.resources.displayMetrics.densityDpi)
            .put("orientation", root.resources.configuration.orientation)
            .put("nightMode", root.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            .put("window", state(root.rootView))
            .put("detail", state(root))
        val ancestors = JSONArray()
        var ancestor = root.parent as? View
        while (ancestor != null && ancestors.length() < 8) {
            ancestors.put(state(ancestor))
            ancestor = ancestor.parent as? View
        }
        result.put("ancestors", ancestors)
        for ((role, id) in roles) result.put(role, state(root.findViewById(id)))
        return result.toString()
    }

    private fun state(view: View?): JSONObject {
        if (view == null) return JSONObject().put("present", false)
        val location = IntArray(2).also(view::getLocationInWindow)
        val visible = Rect()
        val globallyVisible = view.getGlobalVisibleRect(visible)
        return JSONObject()
            .put("present", true)
            .put("attached", view.isAttachedToWindow)
            .put("shown", view.isShown)
            .put("visibility", view.visibility)
            .put("alpha", view.alpha.finiteValue())
            .put("layer", view.layerType)
            .put("hardwareAccelerated", view.isHardwareAccelerated)
            .put("selected", view.isSelected)
            .put("focused", view.isFocused)
            .put("windowFocus", view.hasWindowFocus())
            .put("layoutDirection", view.layoutDirection)
            .put("size", JSONArray(listOf(view.width, view.height)))
            .put("windowPosition", JSONArray(location.toList()))
            .put("translation", JSONArray(listOf(view.translationX.finiteValue(), view.translationY.finiteValue(), view.translationZ.finiteValue())))
            .put("scroll", JSONArray(listOf(view.scrollX, view.scrollY)))
            .put("globallyVisible", globallyVisible)
            .put("visibleBounds", rect(visible))
            .put("clipBounds", view.clipBounds?.let(::rect) ?: JSONObject.NULL)
            .apply {
                if (view is ViewGroup) put("children", view.childCount).put("clipChildren", view.clipChildren).put("clipToPadding", view.clipToPadding)
                if (view is SurfaceView) put("surfaceValid", view.holder.surface.isValid)
                if (view is ImageView) put("drawablePresent", view.drawable != null)
                if (view is TextView) {
                    put("textLength", view.text.length)
                    put("textLines", view.lineCount)
                    put("maxLines", view.maxLines)
                    put("ellipsize", view.ellipsize?.name ?: "none")
                    put("textSizePx", view.textSize.finiteValue())
                    put("textColor", view.currentTextColor)
                }
            }
    }

    private fun rect(rect: Rect) = JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))

    private fun Float.finiteValue(): Any = if (isFinite()) this else toString()

    private val roles = listOf(
        "appBar" to R.id.app_bar_layout,
        "collapsingToolbar" to R.id.detail_collapsing_toolbar_layout,
        "thumbnailPanel" to R.id.detail_thumbnail_root_layout,
        "playerPlaceholder" to R.id.player_placeholder,
        "videoSurface" to R.id.surfaceView,
        "detailPanel" to R.id.detail_content_root_layout,
        "title" to R.id.detail_video_title_view,
        "channel" to R.id.detail_sub_channel_text_view,
        "channelImage" to R.id.detail_sub_channel_thumbnail_view,
        "uploader" to R.id.detail_uploader_text_view,
        "loadingContent" to R.id.detail_content_root_hiding,
        "actions" to R.id.detail_control_panel,
        "downloadAction" to R.id.detail_controls_download,
        "pager" to R.id.view_pager,
        "navigation" to R.id.detail_navigation
    )

    private class Session(val profileId: String, val armedAt: Long, val environment: String) {
        val token = Any()
        var startedAt: Long? = null
        var state = "armed"
        var sampleCount = 0
        val frames = mutableListOf<Frame>()
    }

    private data class Frame(val firstMs: Long, var lastMs: Long, var samples: Int, val snapshot: String)
}
