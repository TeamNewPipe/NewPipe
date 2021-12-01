package org.schabi.newpipe.player.playererror;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.ExoPlaybackException;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.Info;

/**
 * Handles (exoplayer)errors that occur in the player.
 */
public class PlayerErrorHandler {
    // This has to be <= 23 chars on devices running Android 7 or lower (API <= 25)
    // or it fails with an IllegalArgumentException
    // https://stackoverflow.com/a/54744028
    private static final String TAG = "PlayerErrorHandler";

    @Nullable
    private Toast errorToast;

    @NonNull
    private final Context context;

    public PlayerErrorHandler(@NonNull final Context context) {
        this.context = context;
    }

    public void showPlayerError(
            @NonNull final ExoPlaybackException exception,
            @NonNull final Info info,
            @StringRes final int textResId
    ) {
        // Hide existing toast message
        if (errorToast != null) {
            Log.d(TAG, "Trying to cancel previous player error error toast");
            errorToast.cancel();
            errorToast = null;
        }

        if (shouldReportError()) {
            try {
                reportError(exception, info);
                // When a report pops up we need no toast
                return;
            } catch (final Exception ex) {
                Log.w(TAG, "Unable to report error:", ex);
                // This will show the toast as fallback
            }
        }

        Log.d(TAG, "Showing player error toast");
        errorToast = Toast.makeText(context, textResId, Toast.LENGTH_SHORT);
        errorToast.show();
    }

    private void reportError(@NonNull final ExoPlaybackException exception,
                             @NonNull final Info info) {
        ErrorUtil.createNotification(
                context,
                new ErrorInfo(
                        exception,
                        UserAction.PLAY_STREAM,
                        "Player error[type=" + exception.type + "] occurred while playing: "
                                + info.getUrl(),
                        info
                )
        );
    }

    private boolean shouldReportError() {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(
                        context.getString(R.string.report_player_errors_key),
                        false);
    }
}
