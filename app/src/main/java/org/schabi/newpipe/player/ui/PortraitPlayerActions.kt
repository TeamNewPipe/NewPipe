package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/** Reorders bound secondary controls without replacing their listeners or view identities. */
object PortraitPlayerActions {
    internal data class Action(val key: String, val viewId: Int, val titleId: Int)

    private val actions = listOf(
        Action("resize", R.id.resizeTextView, R.string.player_resize_help),
        Action("projection", R.id.projectionButton, R.string.player_projection),
        Action("danmaku", R.id.danmakuToggle, R.string.danmaku_enable),
        Action("captions", R.id.captionTextView, R.string.caption_setting_title),
        Action("ab_repeat", R.id.abRepeatButton, R.string.ab_repeat),
        Action("kodi", R.id.playWithKodi, R.string.play_with_kodi_title),
        Action("cast", R.id.castToPairedDevice, R.string.device_cast_title),
        Action("browser", R.id.openInBrowser, R.string.open_in_browser),
        Action("share", R.id.share, R.string.share),
        Action("info", R.id.playbackInformationButton, R.string.playback_information),
        Action("seek_back", R.id.seekHistoryBack, R.string.seek_history_back),
        Action("seek_forward", R.id.seekHistoryForward, R.string.seek_history_forward),
        Action("sleep", R.id.sleepTimerButton, R.string.sleep_timer),
        Action("sponsor", R.id.sponsorBlockContributeButton, R.string.sb_contribute_title),
        Action("equalizer", R.id.equalizerButton, R.string.equalizer),
        Action("boost", R.id.volumeBoostButton, R.string.volume_boost),
        Action("adjust", R.id.videoAdjustmentsButton, R.string.video_adjustments),
        Action("lock", R.id.touchLockButton, R.string.player_touch_lock),
        Action("listen", R.id.listenModeButton, R.string.enter_listen_mode),
        Action("mute", R.id.switchMute, R.string.mute),
        Action("fullscreen", R.id.fullScreenButton, R.string.toggle_fullscreen)
    )
    private val defaults = actions.map(Action::key)

    internal fun normalize(value: String?): List<String> {
        val known = value.orEmpty().split(',').filter { it in defaults }.distinct()
        return known + defaults.filterNot(known::contains)
    }

    fun keys(context: Context): List<String> = normalize(
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            context.getString(R.string.portrait_player_actions_key),
            null
        )
    )

    fun label(context: Context, key: String): String = actions.firstOrNull {
        it.key == key
    }?.let { context.getString(it.titleId) } ?: key

    fun save(context: Context, order: List<String>) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
            context.getString(R.string.portrait_player_actions_key),
            normalize(order.joinToString(",")).joinToString(",")
        ).apply()
    }

    fun reset(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(
            context.getString(R.string.portrait_player_actions_key)
        ).apply()
    }

    @JvmStatic
    fun apply(controls: HorizontalScrollView) {
        val row = controls.getChildAt(0) as? LinearLayout ?: return
        val original = row.children.toList()
        val byKey = original.mapNotNull { child ->
            val key = actions.firstOrNull { action ->
                child.findViewById<View>(action.viewId) != null
            }?.key ?: return@mapNotNull null
            key to child
        }.toMap()
        if (byKey.isEmpty()) return

        val context = controls.context
        val wanted = if (context.resources.configuration.orientation ==
            Configuration.ORIENTATION_PORTRAIT
        ) {
            keys(context)
        } else {
            defaults
        }
        val sorted = wanted.mapNotNull(byKey::get) + original.filterNot(byKey.values::contains)
        if (sorted == original) return

        val parameters = sorted.map { view -> view to view.layoutParams }
        row.removeAllViews()
        parameters.forEach { (view, params) -> row.addView(view, params) }
    }
}
