package org.schabi.newpipe.player;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.schabi.newpipe.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public final class NotificationConstants {

    private NotificationConstants() { }


    public static final int NOTHING = 0;
    public static final int PREVIOUS = 1;
    public static final int NEXT = 2;
    public static final int REWIND = 3;
    public static final int FORWARD = 4;
    public static final int SMART_REWIND_PREVIOUS = 5;
    public static final int SMART_FORWARD_NEXT = 6;
    public static final int PLAY_PAUSE = 7;
    public static final int PLAY_PAUSE_BUFFERING = 8;
    public static final int REPEAT = 9;
    public static final int SHUFFLE = 10;
    public static final int CLOSE = 11;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NOTHING, PREVIOUS, NEXT, REWIND, FORWARD, SMART_REWIND_PREVIOUS, SMART_FORWARD_NEXT,
            PLAY_PAUSE, PLAY_PAUSE_BUFFERING, REPEAT, SHUFFLE, CLOSE})
    public @interface Action { }

    @StringRes
    public static final int[] ACTION_SUMMARIES = {
            R.string.notification_action_nothing,
            R.string.notification_action_previous,
            R.string.notification_action_next,
            R.string.notification_action_rewind,
            R.string.notification_action_forward,
            R.string.notification_action_smart_rewind_previous,
            R.string.notification_action_smart_forward_next,
            R.string.notification_action_play_pause,
            R.string.notification_action_play_pause_buffering,
            R.string.notification_action_repeat,
            R.string.notification_action_shuffle,
            R.string.close,
    };

    @DrawableRes
    public static final int[] ACTION_ICONS = {
            0,
            R.drawable.exo_icon_previous,
            R.drawable.exo_icon_next,
            R.drawable.exo_icon_rewind,
            R.drawable.exo_icon_fastforward,
            R.drawable.exo_icon_previous,
            R.drawable.exo_icon_next,
            R.drawable.ic_pause_white_24dp,
            R.drawable.ic_hourglass_top_white_24dp,
            R.drawable.exo_icon_repeat_all,
            R.drawable.exo_icon_shuffle_on,
            R.drawable.ic_close_white_24dp,
    };


    @Action
    public static final int[] SLOT_DEFAULTS = {
            SMART_REWIND_PREVIOUS,
            PLAY_PAUSE_BUFFERING,
            SMART_FORWARD_NEXT,
            REPEAT,
            CLOSE,
    };

    @Action
    public static final int[][] SLOT_ALLOWED_ACTIONS = {
            new int[] {PREVIOUS, REWIND, SMART_REWIND_PREVIOUS},
            new int[] {REWIND, PLAY_PAUSE, PLAY_PAUSE_BUFFERING},
            new int[] {NEXT, FORWARD, SMART_FORWARD_NEXT, PLAY_PAUSE, PLAY_PAUSE_BUFFERING},
            new int[] {NOTHING, PREVIOUS, NEXT, REWIND, FORWARD, SMART_REWIND_PREVIOUS,
                    SMART_FORWARD_NEXT, REPEAT, SHUFFLE, CLOSE},
            new int[] {NOTHING, NEXT, FORWARD, SMART_FORWARD_NEXT, REPEAT, SHUFFLE, CLOSE},
    };

    public static final int[] SLOT_PREF_KEYS = {
            R.string.notification_slot_0_key,
            R.string.notification_slot_1_key,
            R.string.notification_slot_2_key,
            R.string.notification_slot_3_key,
            R.string.notification_slot_4_key,
    };


    public static final Integer[] SLOT_COMPACT_DEFAULTS = {0, 1, 2};

    public static final int[] SLOT_COMPACT_PREF_KEYS = {
            R.string.notification_slot_compact_0_key,
            R.string.notification_slot_compact_1_key,
            R.string.notification_slot_compact_2_key,
    };

    /**
     * @param context the context to use
     * @param sharedPreferences the shared preferences to query values from
     * @param slotCount remove indices >= than this value (set to {@code 5} to do nothing, or make
     *                  it lower if there are slots with empty actions)
     * @return a sorted list of the indices of the slots to use as compact slots
     */
    public static List<Integer> getCompactSlotsFromPreferences(
            @NonNull final Context context,
            final SharedPreferences sharedPreferences,
            final int slotCount) {
        final SortedSet<Integer> compactSlots = new TreeSet<>();
        for (int i = 0; i < 3; i++) {
            final int compactSlot = sharedPreferences.getInt(
                    context.getString(SLOT_COMPACT_PREF_KEYS[i]), Integer.MAX_VALUE);

            if (compactSlot == Integer.MAX_VALUE) {
                // settings not yet populated, return default values
                return new ArrayList<>(Arrays.asList(SLOT_COMPACT_DEFAULTS));
            }

            // a negative value (-1) is set when the user does not want a particular compact slot
            if (compactSlot >= 0 && compactSlot < slotCount) {
                compactSlots.add(compactSlot);
            }
        }
        return new ArrayList<>(compactSlots);
    }
}
