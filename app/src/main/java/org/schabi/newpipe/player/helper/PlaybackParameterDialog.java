package org.schabi.newpipe.player.helper;

import static org.schabi.newpipe.player.Player.DEBUG;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogPlaybackParameterBinding;
import org.schabi.newpipe.util.SliderStrategy;

import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

import icepick.Icepick;
import icepick.State;

public class PlaybackParameterDialog extends DialogFragment {
    private static final String TAG = "PlaybackParameterDialog";

    // Minimum allowable range in ExoPlayer
    private static final double MINIMUM_PLAYBACK_VALUE = 0.10f;
    private static final double MAXIMUM_PLAYBACK_VALUE = 3.00f;

    private static final double STEP_1_PERCENT_VALUE = 0.01f;
    private static final double STEP_5_PERCENT_VALUE = 0.05f;
    private static final double STEP_10_PERCENT_VALUE = 0.10f;
    private static final double STEP_25_PERCENT_VALUE = 0.25f;
    private static final double STEP_100_PERCENT_VALUE = 1.00f;

    private static final double DEFAULT_TEMPO = 1.00f;
    private static final double DEFAULT_PITCH = 1.00f;
    private static final double DEFAULT_STEP = STEP_25_PERCENT_VALUE;
    private static final boolean DEFAULT_SKIP_SILENCE = false;

    private static final SliderStrategy QUADRATIC_STRATEGY = new SliderStrategy.Quadratic(
            MINIMUM_PLAYBACK_VALUE,
            MAXIMUM_PLAYBACK_VALUE,
            1.00f,
            10_000);

    @Nullable
    private Callback callback;

    @State
    double initialTempo = DEFAULT_TEMPO;
    @State
    double initialPitch = DEFAULT_PITCH;
    @State
    boolean initialSkipSilence = DEFAULT_SKIP_SILENCE;

    @State
    double tempo = DEFAULT_TEMPO;
    @State
    double pitch = DEFAULT_PITCH;
    @State
    double stepSize = DEFAULT_STEP;
    @State
    boolean skipSilence = DEFAULT_SKIP_SILENCE;

    private DialogPlaybackParameterBinding binding;

    public static PlaybackParameterDialog newInstance(
            final double playbackTempo,
            final double playbackPitch,
            final boolean playbackSkipSilence,
            final Callback callback
    ) {
        final PlaybackParameterDialog dialog = new PlaybackParameterDialog();
        dialog.callback = callback;

        dialog.initialTempo = playbackTempo;
        dialog.initialPitch = playbackPitch;
        dialog.initialSkipSilence = playbackSkipSilence;

        dialog.tempo = dialog.initialTempo;
        dialog.pitch = dialog.initialPitch;
        dialog.skipSilence = dialog.initialSkipSilence;

        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (context instanceof Callback) {
            callback = (Callback) context;
        } else if (callback == null) {
            dismiss();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        assureCorrectAppLanguage(getContext());
        Icepick.restoreInstanceState(this, savedInstanceState);

        binding = DialogPlaybackParameterBinding.inflate(LayoutInflater.from(getContext()));
        initUI();
        initUIData();

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity())
                .setView(binding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                    setAndUpdateTempo(initialTempo);
                    setAndUpdatePitch(initialPitch);
                    setAndUpdateSkipSilence(initialSkipSilence);
                    updateCallback();
                })
                .setNeutralButton(R.string.playback_reset, (dialogInterface, i) -> {
                    setAndUpdateTempo(DEFAULT_TEMPO);
                    setAndUpdatePitch(DEFAULT_PITCH);
                    setAndUpdateSkipSilence(DEFAULT_SKIP_SILENCE);
                    updateCallback();
                })
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> updateCallback());

        return dialogBuilder.create();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Control Views
    //////////////////////////////////////////////////////////////////////////*/

    private void initUI() {
        // Tempo
        setText(binding.tempoMinimumText, PlayerHelper::formatSpeed, MINIMUM_PLAYBACK_VALUE);
        setText(binding.tempoMaximumText, PlayerHelper::formatSpeed, MAXIMUM_PLAYBACK_VALUE);

        // Pitch
        setText(binding.pitchMinimumText, PlayerHelper::formatPitch, MINIMUM_PLAYBACK_VALUE);
        setText(binding.pitchMaximumText, PlayerHelper::formatPitch, MAXIMUM_PLAYBACK_VALUE);

        // Steps
        setupStepTextView(binding.stepSizeOnePercent, STEP_1_PERCENT_VALUE);
        setupStepTextView(binding.stepSizeFivePercent, STEP_5_PERCENT_VALUE);
        setupStepTextView(binding.stepSizeTenPercent, STEP_10_PERCENT_VALUE);
        setupStepTextView(binding.stepSizeTwentyFivePercent, STEP_25_PERCENT_VALUE);
        setupStepTextView(binding.stepSizeOneHundredPercent, STEP_100_PERCENT_VALUE);
    }

    private TextView setText(
            final TextView textView,
            final DoubleFunction<String> formatter,
            final double value
    ) {
        Objects.requireNonNull(textView).setText(formatter.apply(value));
        return textView;
    }

    private void setupStepTextView(
            final TextView textView,
            final double stepSizeValue
    ) {
        setText(textView, PlaybackParameterDialog::getPercentString, stepSizeValue)
                .setOnClickListener(view -> setAndUpdateStepSize(stepSizeValue));
    }

    private void initUIData() {
        // Tempo
        binding.tempoSeekbar.setMax(QUADRATIC_STRATEGY.progressOf(MAXIMUM_PLAYBACK_VALUE));
        setAndUpdateTempo(tempo);
        binding.tempoSeekbar.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(this::onTempoSliderUpdated));

        registerOnStepClickListener(
                binding.tempoStepDown, tempo, -1, this::onTempoSliderUpdated);
        registerOnStepClickListener(
                binding.tempoStepUp, tempo, 1, this::onTempoSliderUpdated);

        // Pitch
        binding.pitchSeekbar.setMax(QUADRATIC_STRATEGY.progressOf(MAXIMUM_PLAYBACK_VALUE));
        setAndUpdatePitch(pitch);
        binding.pitchSeekbar.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(this::onPitchSliderUpdated));

        registerOnStepClickListener(
                binding.pitchStepDown, pitch, -1, this::onPitchSliderUpdated);
        registerOnStepClickListener(
                binding.pitchStepUp, pitch, 1, this::onPitchSliderUpdated);

        // Steps
        setAndUpdateStepSize(stepSize);

        // Bottom controls
        // restore whether pitch and tempo are unhooked or not
        binding.unhookCheckbox.setChecked(PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.playback_unhook_key), true));

        binding.unhookCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save whether pitch and tempo are unhooked or not
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putBoolean(getString(R.string.playback_unhook_key), isChecked)
                    .apply();

            if (!isChecked) {
                // when unchecked, slide back to the minimum of current tempo or pitch
                setSliders(Math.min(pitch, tempo));
            }
        });

        setAndUpdateSkipSilence(skipSilence);
        binding.skipSilenceCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            skipSilence = isChecked;
            updateCallback();
        });
    }

    private void registerOnStepClickListener(
            final TextView stepTextView,
            final double currentValue,
            final double direction, // -1 for step down, +1 for step up
            final DoubleConsumer newValueConsumer
    ) {
        stepTextView.setOnClickListener(view ->
                newValueConsumer.accept(currentValue * direction)
        );
    }

    private void setAndUpdateStepSize(final double newStepSize) {
        this.stepSize = newStepSize;

        binding.tempoStepUp.setText(getStepUpPercentString(newStepSize));
        binding.tempoStepDown.setText(getStepDownPercentString(newStepSize));

        binding.pitchStepUp.setText(getStepUpPercentString(newStepSize));
        binding.pitchStepDown.setText(getStepDownPercentString(newStepSize));
    }

    private void setAndUpdateSkipSilence(final boolean newSkipSilence) {
        this.skipSilence = newSkipSilence;
        binding.skipSilenceCheckbox.setChecked(newSkipSilence);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Sliders
    //////////////////////////////////////////////////////////////////////////*/

    private SeekBar.OnSeekBarChangeListener getTempoOrPitchSeekbarChangeListener(
            final DoubleConsumer newValueConsumer
    ) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                if (fromUser) { // this change is first in chain
                    newValueConsumer.accept(QUADRATIC_STRATEGY.valueOf(progress));
                    updateCallback();
                }
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                // Do nothing
            }
        };
    }

    private void onTempoSliderUpdated(final double newTempo) {
        if (!binding.unhookCheckbox.isChecked()) {
            setSliders(newTempo);
        } else {
            setAndUpdateTempo(newTempo);
        }
    }

    private void onPitchSliderUpdated(final double newPitch) {
        if (!binding.unhookCheckbox.isChecked()) {
            setSliders(newPitch);
        } else {
            setAndUpdatePitch(newPitch);
        }
    }

    private void setSliders(final double newValue) {
        setAndUpdateTempo(newValue);
        setAndUpdatePitch(newValue);
    }

    private void setAndUpdateTempo(final double newTempo) {
        this.tempo = newTempo;
        binding.tempoSeekbar.setProgress(QUADRATIC_STRATEGY.progressOf(tempo));
        setText(binding.tempoCurrentText, PlayerHelper::formatSpeed, tempo);
    }

    private void setAndUpdatePitch(final double newPitch) {
        this.pitch = newPitch;
        binding.pitchSeekbar.setProgress(QUADRATIC_STRATEGY.progressOf(pitch));
        setText(binding.pitchCurrentText, PlayerHelper::formatPitch, pitch);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    private void updateCallback() {
        if (callback == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Updating callback: "
                    + "tempo = [" + tempo + "], "
                    + "pitch = [" + pitch + "], "
                    + "skipSilence = [" + skipSilence + "]"
            );
        }
        callback.onPlaybackParameterChanged((float) tempo, (float) pitch, skipSilence);
    }

    @NonNull
    private static String getStepUpPercentString(final double percent) {
        return '+' + getPercentString(percent);
    }

    @NonNull
    private static String getStepDownPercentString(final double percent) {
        return '-' + getPercentString(percent);
    }

    @NonNull
    private static String getPercentString(final double percent) {
        return PlayerHelper.formatPitch(percent);
    }

    public interface Callback {
        void onPlaybackParameterChanged(float playbackTempo, float playbackPitch,
                                        boolean playbackSkipSilence);
    }
}
