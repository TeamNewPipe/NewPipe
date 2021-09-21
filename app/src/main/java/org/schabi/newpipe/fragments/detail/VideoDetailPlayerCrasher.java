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
import androidx.appcompat.app.AlertDialog;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.RendererCapabilities;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ListRadioIconItemBinding;
import org.schabi.newpipe.databinding.SingleChoiceDialogViewBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class VideoDetailPlayerCrasher {

    private static final String TAG = "VideoDetPlayerCrasher";

    @NonNull
    private final Supplier<Context> contextSupplier;
    @NonNull
    private final Supplier<LayoutInflater> layoutInflaterSupplier;

    public VideoDetailPlayerCrasher(
            @NonNull final Supplier<Context> contextSupplier,
            @NonNull final Supplier<LayoutInflater> layoutInflaterSupplier
    ) {
        this.contextSupplier = contextSupplier;
        this.layoutInflaterSupplier = layoutInflaterSupplier;
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
                        RendererCapabilities.FORMAT_HANDLED
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
        exceptionTypes.put(
                "Timeout",
                () -> ExoPlaybackException.createForTimeout(
                        new TimeoutException(defaultMsg),
                        ExoPlaybackException.TIMEOUT_OPERATION_UNDEFINED
                )
        );

        return exceptionTypes;
    }

    private Context getContext() {
        return this.contextSupplier.get();
    }

    private LayoutInflater getLayoutInflater() {
        return this.layoutInflaterSupplier.get();
    }

    private Context getThemeWrapperContext() {
        return new ContextThemeWrapper(
                getContext(),
                ThemeHelper.isLightThemeSelected(getContext())
                        ? R.style.LightTheme
                        : R.style.DarkTheme);
    }

    public void onCrashThePlayer(final Player player) {
        if (!isPlayerAvailable(player)) {
            Log.d(TAG, "Player is not available");
            Toast.makeText(getContext(), "Player is not available", Toast.LENGTH_SHORT)
                    .show();

            return;
        }

        final Context themeWrapperContext = getThemeWrapperContext();

        final LayoutInflater inflater = LayoutInflater.from(themeWrapperContext);
        final RadioGroup radioGroup = SingleChoiceDialogViewBinding.inflate(getLayoutInflater())
                .list;

        final AlertDialog alertDialog = new AlertDialog.Builder(getThemeWrapperContext())
                .setTitle("Choose an exception")
                .setView(radioGroup)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .create();

        for (final Map.Entry<String, Supplier<ExoPlaybackException>> entry
                : getExceptionTypes().entrySet()) {
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
    private void tryCrashPlayerWith(
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

    private boolean isPlayerAvailable(final Player player) {
        return player != null;
    }
}
