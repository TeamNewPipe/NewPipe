package org.schabi.newpipe.util;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.player.PlayerType;

/**
 * Utility class for play buttons and their respective click listeners.
 */
public final class PlayButtonHelper {

    private PlayButtonHelper() {
        // utility class
    }

    /**
     * Initialize {@link android.view.View.OnClickListener OnClickListener}
     * and {@link android.view.View.OnLongClickListener OnLongClickListener} for playlist control
     * buttons defined in {@link R.layout#playlist_control}.
     *
     * @param activity The activity to use for the {@link android.widget.Toast Toast}.
     * @param playlistControlBinding The binding of the
     *        {@link R.layout#playlist_control playlist control layout}.
     * @param fragment The fragment to get the play queue from.
     */
    public static void initPlaylistControlClickListener(
            @NonNull final AppCompatActivity activity,
            @NonNull final PlaylistControlBinding playlistControlBinding,
            @NonNull final PlaylistControlViewHolder fragment) {
        // click listener
        playlistControlBinding.playlistCtrlPlayAllButton.setOnClickListener(view -> {
            NavigationHelper.playOnMainPlayer(activity, fragment.getPlayQueue());
            showHoldToAppendToastIfNeeded(activity);
        });
        playlistControlBinding.playlistCtrlPlayPopupButton.setOnClickListener(view -> {
            NavigationHelper.playOnPopupPlayer(activity, fragment.getPlayQueue(), false);
            showHoldToAppendToastIfNeeded(activity);
        });
        playlistControlBinding.playlistCtrlPlayBgButton.setOnClickListener(view -> {
            NavigationHelper.playOnBackgroundPlayer(activity, fragment.getPlayQueue(), false);
            showHoldToAppendToastIfNeeded(activity);
        });

        // long click listener
        playlistControlBinding.playlistCtrlPlayPopupButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPlayer(activity, fragment.getPlayQueue(), PlayerType.POPUP);
            return true;
        });
        playlistControlBinding.playlistCtrlPlayBgButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPlayer(activity, fragment.getPlayQueue(), PlayerType.AUDIO);
            return true;
        });
    }

    /**
     * Show the "hold to append" toast if the corresponding preference is enabled.
     *
     * @param context The context to show the toast.
     */
    private static void showHoldToAppendToastIfNeeded(@NonNull final Context context) {
        if (shouldShowHoldToAppendTip(context)) {
            Toast.makeText(context, R.string.hold_to_append, Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * Check if the "hold to append" toast should be shown.
     *
     * <p>
     * The tip is shown if the corresponding preference is enabled.
     * This is the default behaviour.
     * </p>
     *
     * @param context The context to get the preference.
     * @return {@code true} if the tip should be shown, {@code false} otherwise.
     */
    public static boolean shouldShowHoldToAppendTip(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_hold_to_append_key), true);
    }
}
