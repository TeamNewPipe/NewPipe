package org.schabi.newpipe.player.helper;

import static org.schabi.newpipe.player.helper.VolumeBoostAudioProcessor.MAXIMUM_VOLUME_BOOST;
import static org.schabi.newpipe.player.helper.VolumeBoostAudioProcessor.MINIMUM_VOLUME_BOOST;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.DialogFragment;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogVolumeBoostBinding;
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener;
import org.schabi.newpipe.util.SliderStrategy;

/**
 * Dialog letting the user amplify the audio of the stream being played, either by picking a gain
 * themselves or by letting the player adjust it in real time based on how loud the stream is.
 */
public class VolumeBoostDialog extends DialogFragment {

    private static final double MIN_VOLUME_BOOST = MINIMUM_VOLUME_BOOST;
    private static final double MAX_VOLUME_BOOST = MAXIMUM_VOLUME_BOOST;
    private static final double VOLUME_BOOST_STEP = 0.25f;

    private static final SliderStrategy STRATEGY = new SliderStrategy.Linear(
            MIN_VOLUME_BOOST,
            MAX_VOLUME_BOOST,
            10_000);

    @Nullable
    private Callback callback;

    @State
    double initialVolumeBoost = MIN_VOLUME_BOOST;
    @State
    boolean initialAutoVolumeBoost = false;

    @State
    double volumeBoost = MIN_VOLUME_BOOST;
    @State
    boolean autoVolumeBoost = false;

    private DialogVolumeBoostBinding binding;

    public static VolumeBoostDialog newInstance(final double playbackVolumeBoost,
                                                final boolean playbackAutoVolumeBoost,
                                                final Callback callback) {
        final VolumeBoostDialog dialog = new VolumeBoostDialog();
        dialog.callback = callback;

        dialog.initialVolumeBoost = playbackVolumeBoost;
        dialog.initialAutoVolumeBoost = playbackAutoVolumeBoost;

        dialog.volumeBoost = dialog.initialVolumeBoost;
        dialog.autoVolumeBoost = dialog.initialAutoVolumeBoost;

        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        Bridge.restoreInstanceState(this, savedInstanceState);

        binding = DialogVolumeBoostBinding.inflate(getLayoutInflater());
        initUI();

        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.volume_boost)
                .setView(binding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                    setVolumeBoost(initialVolumeBoost);
                    setAutoVolumeBoost(initialAutoVolumeBoost);
                    updateCallback();
                })
                .setNeutralButton(R.string.playback_reset, (dialogInterface, i) -> {
                    setVolumeBoost(MIN_VOLUME_BOOST);
                    setAutoVolumeBoost(false);
                    updateCallback();
                })
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> updateCallback())
                .create();
    }

    private void initUI() {
        binding.volumeBoostMinimumText.setText(PlayerHelper.formatPitch(MIN_VOLUME_BOOST));
        binding.volumeBoostMaximumText.setText(PlayerHelper.formatPitch(MAX_VOLUME_BOOST));
        binding.volumeBoostStepDown.setText(getStepString(-VOLUME_BOOST_STEP));
        binding.volumeBoostStepUp.setText(getStepString(VOLUME_BOOST_STEP));

        binding.volumeBoostSeekbar.setMax(STRATEGY.progressOf(MAX_VOLUME_BOOST));
        binding.volumeBoostSeekbar.setOnSeekBarChangeListener(
                new SimpleOnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(@NonNull final SeekBar seekBar,
                                                  final int progress,
                                                  final boolean fromUser) {
                        if (fromUser) { // ensure that the user triggered the change
                            setVolumeBoost(STRATEGY.valueOf(progress));
                            updateCallback();
                        }
                    }
                });

        binding.volumeBoostStepDown.setOnClickListener(view -> {
            setVolumeBoost(volumeBoost - VOLUME_BOOST_STEP);
            updateCallback();
        });
        binding.volumeBoostStepUp.setOnClickListener(view -> {
            setVolumeBoost(volumeBoost + VOLUME_BOOST_STEP);
            updateCallback();
        });

        binding.autoVolumeBoostSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            setAutoVolumeBoost(isChecked);
            updateCallback();
        });

        setVolumeBoost(volumeBoost);
        setAutoVolumeBoost(autoVolumeBoost);
    }

    private void setVolumeBoost(final double newVolumeBoost) {
        volumeBoost = MathUtils.clamp(newVolumeBoost, MIN_VOLUME_BOOST, MAX_VOLUME_BOOST);

        binding.volumeBoostSeekbar.setProgress(STRATEGY.progressOf(volumeBoost));
        binding.volumeBoostCurrentText.setText(PlayerHelper.formatPitch(volumeBoost));
    }

    private void setAutoVolumeBoost(final boolean newAutoVolumeBoost) {
        autoVolumeBoost = newAutoVolumeBoost;

        binding.autoVolumeBoostSwitch.setChecked(newAutoVolumeBoost);
        // the gain is chosen by the player itself in automatic mode, so the slider does not apply
        binding.volumeBoostSeekbar.setEnabled(!newAutoVolumeBoost);
        binding.volumeBoostStepDown.setEnabled(!newAutoVolumeBoost);
        binding.volumeBoostStepUp.setEnabled(!newAutoVolumeBoost);
        setChildrenAlpha(newAutoVolumeBoost ? 0.4f : 1.0f);
    }

    private void setChildrenAlpha(final float alpha) {
        for (final View view : new View[] {
                binding.volumeBoostSeekbar,
                binding.volumeBoostStepDown,
                binding.volumeBoostStepUp,
                binding.volumeBoostMinimumText,
                binding.volumeBoostCurrentText,
                binding.volumeBoostMaximumText
        }) {
            view.setAlpha(alpha);
        }
    }

    private void updateCallback() {
        if (callback != null) {
            callback.onVolumeBoostChanged((float) volumeBoost, autoVolumeBoost);
        }
    }

    @NonNull
    private static String getStepString(final double step) {
        return (step > 0 ? "+" : "-") + PlayerHelper.formatPitch(Math.abs(step));
    }

    public interface Callback {
        void onVolumeBoostChanged(float volumeBoost, boolean autoVolumeBoost);
    }
}
