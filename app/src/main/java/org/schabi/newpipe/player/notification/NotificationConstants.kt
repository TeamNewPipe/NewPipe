package org.schabi.newpipe.player.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import java.util.TreeSet
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization

object NotificationConstants {

    private const val BASE_ACTION = App.PACKAGE_NAME + ".player.MainPlayer."
    const val ACTION_CLOSE = BASE_ACTION + "CLOSE"
    const val ACTION_PLAY_PAUSE = BASE_ACTION + ".player.MainPlayer.PLAY_PAUSE"
    const val ACTION_REPEAT = BASE_ACTION + ".player.MainPlayer.REPEAT"
    const val ACTION_PLAY_NEXT = BASE_ACTION + ".player.MainPlayer.ACTION_PLAY_NEXT"
    const val ACTION_PLAY_PREVIOUS = BASE_ACTION + ".player.MainPlayer.ACTION_PLAY_PREVIOUS"
    const val ACTION_FAST_REWIND = BASE_ACTION + ".player.MainPlayer.ACTION_FAST_REWIND"
    const val ACTION_FAST_FORWARD = BASE_ACTION + ".player.MainPlayer.ACTION_FAST_FORWARD"
    const val ACTION_SHUFFLE = BASE_ACTION + ".player.MainPlayer.ACTION_SHUFFLE"
    const val ACTION_RECREATE_NOTIFICATION = BASE_ACTION + ".player.MainPlayer.ACTION_RECREATE_NOTIFICATION"

    const val NOTHING = 0
    const val PREVIOUS = 1
    const val NEXT = 2
    const val REWIND = 3
    const val FORWARD = 4
    const val SMART_REWIND_PREVIOUS = 5
    const val SMART_FORWARD_NEXT = 6
    const val PLAY_PAUSE = 7
    const val PLAY_PAUSE_BUFFERING = 8
    const val REPEAT = 9
    const val SHUFFLE = 10
    const val CLOSE = 11

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        NOTHING, PREVIOUS, NEXT, REWIND, FORWARD,
        SMART_REWIND_PREVIOUS, SMART_FORWARD_NEXT, PLAY_PAUSE, PLAY_PAUSE_BUFFERING, REPEAT,
        SHUFFLE, CLOSE
    )
    annotation class Action

    @Action
    @JvmField
    val ALL_ACTIONS = intArrayOf(
        NOTHING, PREVIOUS, NEXT, REWIND, FORWARD,
        SMART_REWIND_PREVIOUS, SMART_FORWARD_NEXT, PLAY_PAUSE, PLAY_PAUSE_BUFFERING, REPEAT,
        SHUFFLE, CLOSE
    )

    @DrawableRes
    @JvmField
    val ACTION_ICONS = intArrayOf(
        0,
        androidx.media3.ui.R.drawable.exo_icon_previous,
        androidx.media3.ui.R.drawable.exo_icon_next,
        androidx.media3.ui.R.drawable.exo_icon_rewind,
        androidx.media3.ui.R.drawable.exo_icon_fastforward,
        androidx.media3.ui.R.drawable.exo_icon_previous,
        androidx.media3.ui.R.drawable.exo_icon_next,
        R.drawable.ic_pause,
        R.drawable.ic_hourglass_top,
        androidx.media3.ui.R.drawable.exo_icon_repeat_all,
        androidx.media3.ui.R.drawable.exo_icon_shuffle_on,
        R.drawable.ic_close
    )

    @Action
    @JvmField
    val SLOT_DEFAULTS = intArrayOf(
        SMART_REWIND_PREVIOUS,
        PLAY_PAUSE_BUFFERING,
        SMART_FORWARD_NEXT,
        REPEAT,
        CLOSE
    )

    @JvmField
    val SLOT_PREF_KEYS = intArrayOf(
        R.string.notification_slot_0_key,
        R.string.notification_slot_1_key,
        R.string.notification_slot_2_key,
        R.string.notification_slot_3_key,
        R.string.notification_slot_4_key
    )

    @JvmField
    val SLOT_COMPACT_DEFAULTS = listOf(0, 1, 2)

    @JvmField
    val SLOT_COMPACT_PREF_KEYS = intArrayOf(
        R.string.notification_slot_compact_0_key,
        R.string.notification_slot_compact_1_key,
        R.string.notification_slot_compact_2_key
    )

    @JvmStatic
    fun getActionName(context: Context, @Action action: Int): String {
        return when (action) {
            PREVIOUS -> context.getString(androidx.media3.ui.R.string.exo_controls_previous_description)

            NEXT -> context.getString(androidx.media3.ui.R.string.exo_controls_next_description)

            REWIND -> context.getString(androidx.media3.ui.R.string.exo_controls_rewind_description)

            FORWARD -> context.getString(androidx.media3.ui.R.string.exo_controls_fastforward_description)

            SMART_REWIND_PREVIOUS -> Localization.concatenateStrings(
                context.getString(androidx.media3.ui.R.string.exo_controls_rewind_description),
                context.getString(androidx.media3.ui.R.string.exo_controls_previous_description)
            )

            SMART_FORWARD_NEXT -> Localization.concatenateStrings(
                context.getString(androidx.media3.ui.R.string.exo_controls_fastforward_description),
                context.getString(androidx.media3.ui.R.string.exo_controls_next_description)
            )

            PLAY_PAUSE -> Localization.concatenateStrings(
                context.getString(androidx.media3.ui.R.string.exo_controls_play_description),
                context.getString(androidx.media3.ui.R.string.exo_controls_pause_description)
            )

            PLAY_PAUSE_BUFFERING -> Localization.concatenateStrings(
                context.getString(androidx.media3.ui.R.string.exo_controls_play_description),
                context.getString(androidx.media3.ui.R.string.exo_controls_pause_description),
                context.getString(R.string.notification_action_buffering)
            )

            REPEAT -> context.getString(R.string.notification_action_repeat)

            SHUFFLE -> context.getString(R.string.notification_action_shuffle)

            CLOSE -> context.getString(R.string.close)

            else -> context.getString(R.string.notification_action_nothing)
        }
    }

    /**
     * @param context the context to use
     * @param sharedPreferences the shared preferences to query values from
     * @return a sorted list of the indices of the slots to use as compact slots
     */
    @JvmStatic
    fun getCompactSlotsFromPreferences(
        context: Context,
        sharedPreferences: SharedPreferences
    ): Collection<Int> {
        val compactSlots = TreeSet<Int>()
        for (i in 0..2) {
            val compactSlot = sharedPreferences.getInt(
                context.getString(SLOT_COMPACT_PREF_KEYS[i]),
                Int.MAX_VALUE
            )

            if (compactSlot == Int.MAX_VALUE) {
                // settings not yet populated, return default values
                return SLOT_COMPACT_DEFAULTS
            }

            if (compactSlot >= 0) {
                // compact slot is < 0 if there are less than 3 checked checkboxes
                compactSlots.add(compactSlot)
            }
        }
        return compactSlots
    }
}
