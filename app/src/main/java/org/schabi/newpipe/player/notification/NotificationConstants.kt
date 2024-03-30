package org.schabi.newpipe.player.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization
import java.util.SortedSet
import java.util.TreeSet

object NotificationConstants {
    /*//////////////////////////////////////////////////////////////////////////
    // Intent actions
    ////////////////////////////////////////////////////////////////////////// */
    private val BASE_ACTION: String = App.Companion.PACKAGE_NAME + ".player.MainPlayer."
    val ACTION_CLOSE: String = BASE_ACTION + "CLOSE"
    val ACTION_PLAY_PAUSE: String = BASE_ACTION + ".player.MainPlayer.PLAY_PAUSE"
    val ACTION_REPEAT: String = BASE_ACTION + ".player.MainPlayer.REPEAT"
    val ACTION_PLAY_NEXT: String = BASE_ACTION + ".player.MainPlayer.ACTION_PLAY_NEXT"
    val ACTION_PLAY_PREVIOUS: String = BASE_ACTION + ".player.MainPlayer.ACTION_PLAY_PREVIOUS"
    val ACTION_FAST_REWIND: String = BASE_ACTION + ".player.MainPlayer.ACTION_FAST_REWIND"
    val ACTION_FAST_FORWARD: String = BASE_ACTION + ".player.MainPlayer.ACTION_FAST_FORWARD"
    val ACTION_SHUFFLE: String = BASE_ACTION + ".player.MainPlayer.ACTION_SHUFFLE"
    val ACTION_RECREATE_NOTIFICATION: String = BASE_ACTION + ".player.MainPlayer.ACTION_RECREATE_NOTIFICATION"
    val NOTHING: Int = 0
    val PREVIOUS: Int = 1
    val NEXT: Int = 2
    val REWIND: Int = 3
    val FORWARD: Int = 4
    val SMART_REWIND_PREVIOUS: Int = 5
    val SMART_FORWARD_NEXT: Int = 6
    val PLAY_PAUSE: Int = 7
    val PLAY_PAUSE_BUFFERING: Int = 8
    val REPEAT: Int = 9
    val SHUFFLE: Int = 10
    val CLOSE: Int = 11

    @Action
    val ALL_ACTIONS: IntArray = intArrayOf(NOTHING, PREVIOUS, NEXT, REWIND, FORWARD,
            SMART_REWIND_PREVIOUS, SMART_FORWARD_NEXT, PLAY_PAUSE, PLAY_PAUSE_BUFFERING, REPEAT,
            SHUFFLE, CLOSE)

    @DrawableRes
    val ACTION_ICONS: IntArray = intArrayOf(
            0,
            R.drawable.exo_icon_previous,
            R.drawable.exo_icon_next,
            R.drawable.exo_icon_rewind,
            R.drawable.exo_icon_fastforward,
            R.drawable.exo_icon_previous,
            R.drawable.exo_icon_next,
            R.drawable.ic_pause,
            R.drawable.ic_hourglass_top,
            R.drawable.exo_icon_repeat_all,
            R.drawable.exo_icon_shuffle_on,
            R.drawable.ic_close)

    @Action
    val SLOT_DEFAULTS: IntArray = intArrayOf(
            SMART_REWIND_PREVIOUS,
            PLAY_PAUSE_BUFFERING,
            SMART_FORWARD_NEXT,
            REPEAT,
            CLOSE)
    val SLOT_PREF_KEYS: IntArray = intArrayOf(
            R.string.notification_slot_0_key,
            R.string.notification_slot_1_key,
            R.string.notification_slot_2_key,
            R.string.notification_slot_3_key,
            R.string.notification_slot_4_key)
    val SLOT_COMPACT_DEFAULTS: List<Int> = listOf(0, 1, 2)
    val SLOT_COMPACT_PREF_KEYS: IntArray = intArrayOf(
            R.string.notification_slot_compact_0_key,
            R.string.notification_slot_compact_1_key,
            R.string.notification_slot_compact_2_key)

    fun getActionName(context: Context, @Action action: Int): String {
        when (action) {
            PREVIOUS -> return context.getString(R.string.exo_controls_previous_description)
            NEXT -> return context.getString(R.string.exo_controls_next_description)
            REWIND -> return context.getString(R.string.exo_controls_rewind_description)
            FORWARD -> return context.getString(R.string.exo_controls_fastforward_description)
            SMART_REWIND_PREVIOUS -> return Localization.concatenateStrings(
                    context.getString(R.string.exo_controls_rewind_description),
                    context.getString(R.string.exo_controls_previous_description))

            SMART_FORWARD_NEXT -> return Localization.concatenateStrings(
                    context.getString(R.string.exo_controls_fastforward_description),
                    context.getString(R.string.exo_controls_next_description))

            PLAY_PAUSE -> return Localization.concatenateStrings(
                    context.getString(R.string.exo_controls_play_description),
                    context.getString(R.string.exo_controls_pause_description))

            PLAY_PAUSE_BUFFERING -> return Localization.concatenateStrings(
                    context.getString(R.string.exo_controls_play_description),
                    context.getString(R.string.exo_controls_pause_description),
                    context.getString(R.string.notification_action_buffering))

            REPEAT -> return context.getString(R.string.notification_action_repeat)
            SHUFFLE -> return context.getString(R.string.notification_action_shuffle)
            CLOSE -> return context.getString(R.string.close)
            NOTHING -> return context.getString(R.string.notification_action_nothing)
            else -> return context.getString(R.string.notification_action_nothing)
        }
    }

    /**
     * @param context the context to use
     * @param sharedPreferences the shared preferences to query values from
     * @return a sorted list of the indices of the slots to use as compact slots
     */
    fun getCompactSlotsFromPreferences(
            context: Context,
            sharedPreferences: SharedPreferences?): Collection<Int> {
        val compactSlots: SortedSet<Int> = TreeSet()
        for (i in 0..2) {
            val compactSlot: Int = sharedPreferences!!.getInt(
                    context.getString(SLOT_COMPACT_PREF_KEYS.get(i)), Int.MAX_VALUE)
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

    @Retention(AnnotationRetention.SOURCE)
    @IntDef([NOTHING, PREVIOUS, NEXT, REWIND, FORWARD, SMART_REWIND_PREVIOUS, SMART_FORWARD_NEXT, PLAY_PAUSE, PLAY_PAUSE_BUFFERING, REPEAT, SHUFFLE, CLOSE])
    annotation class Action()
}
