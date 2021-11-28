package org.schabi.newpipe.fragments.detail;

import android.content.Context;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ListRadioIconItemBinding;
import org.schabi.newpipe.databinding.SingleChoiceDialogViewBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Outsourced logic for crashing the player in the {@link VideoDetailFragment}.
 */
public final class VideoDetailPlayerCrasher {

    // This has to be <= 23 chars on devices running Android 7 or lower (API <= 25)
    // or it fails with an IllegalArgumentException
    // https://stackoverflow.com/a/54744028
    private static final String TAG = "VideoDetPlayerCrasher";

    private static final Map<String, Supplier<ExoPlaybackException>> AVAILABLE_EXCEPTION_TYPES =
            getExceptionTypes();

    private VideoDetailPlayerCrasher() {
        // No impls
    }

    private static Map<String, Supplier<ExoPlaybackException>> getExceptionTypes() {
        final String defaultMsg = "Dummy";
        final Map<String, Supplier<ExoPlaybackException>> exceptionTypes = new LinkedHashMap<>();
        exceptionTypes.put(
                "Source",
                () -> ExoPlaybackException.createForSource(
                        new IOException(defaultMsg)
                )
        );
        exceptionTypes.put(
                "Renderer",
                () -> ExoPlaybackException.createForRenderer(
                        new Exception(defaultMsg),
                        "Dummy renderer",
                        0,
                        null,
                        C.FORMAT_HANDLED
                )
        );
        exceptionTypes.put(
                "Unexpected",
                () -> ExoPlaybackException.createForUnexpected(
                        new RuntimeException(defaultMsg)
                )
        );
        exceptionTypes.put(
                "Remote",
                () -> ExoPlaybackException.createForRemote(defaultMsg)
        );

        return Collections.unmodifiableMap(exceptionTypes);
    }

    private static Context getThemeWrapperContext(final Context context) {
        return new ContextThemeWrapper(
                context,
                ThemeHelper.isLightThemeSelected(context)
                        ? R.style.LightTheme
                        : R.style.DarkTheme);
    }

    public static void onCrashThePlayer(
            @NonNull final Context context,
            @Nullable final Player player,
            @NonNull final LayoutInflater layoutInflater
    ) {
        if (player == null) {
            Log.d(TAG, "Player is not available");
            Toast.makeText(context, "Player is not available", Toast.LENGTH_SHORT)
                    .show();

            return;
        }

        // -- Build the dialog/UI --

        final Context themeWrapperContext = getThemeWrapperContext(context);

        final LayoutInflater inflater = LayoutInflater.from(themeWrapperContext);
        final RadioGroup radioGroup = SingleChoiceDialogViewBinding.inflate(layoutInflater)
                .list;

        final AlertDialog alertDialog = new AlertDialog.Builder(getThemeWrapperContext(context))
                .setTitle("Choose an exception")
                .setView(radioGroup)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .create();

        for (final Map.Entry<String, Supplier<ExoPlaybackException>> entry
                : AVAILABLE_EXCEPTION_TYPES.entrySet()) {
            final RadioButton radioButton = ListRadioIconItemBinding.inflate(inflater).getRoot();
            radioButton.setText(entry.getKey());
            radioButton.setChecked(false);
            radioButton.setLayoutParams(
                    new RadioGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
            radioButton.setOnClickListener(v -> {
                tryCrashPlayerWith(player, entry.getValue().get());
                if (alertDialog != null) {
                    alertDialog.cancel();
                }
            });
            radioGroup.addView(radioButton);
        }

        alertDialog.show();
    }

    /**
     * Note that this method does not crash the underlying exoplayer directly (it's not possible).
     * It simply supplies a Exception to {@link Player#onPlayerError(ExoPlaybackException)}.
     * @param player
     * @param exception
     */
    private static void tryCrashPlayerWith(
            @NonNull final Player player,
            @NonNull final ExoPlaybackException exception
    ) {
        Log.d(TAG, "Crashing the player using player.onPlayerError(ex)");
        try {
            player.onPlayerError(exception);
        } catch (final Exception exPlayer) {
            Log.e(TAG,
                    "Run into an exception while crashing the player:",
                    exPlayer);
        }
    }
}
