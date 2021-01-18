package org.schabi.newpipe.player.helper;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogPlaybackParameterBinding;
import org.schabi.newpipe.util.SliderStrategy;

import static org.schabi.newpipe.player.Player.DEBUG;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class PlaybackParameterDialog extends DialogFragment {
    // Minimum allowable range in ExoPlayer
    private static final double MINIMUM_PLAYBACK_VALUE = 0.10f;
    private static final double MAXIMUM_PLAYBACK_VALUE = 3.00f;

    private static final char STEP_UP_SIGN = '+';
    private static final char STEP_DOWN_SIGN = '-';

    private static final double STEP_ONE_PERCENT_VALUE = 0.01f;
    private static final double STEP_FIVE_PERCENT_VALUE = 0.05f;
    private static final double STEP_TEN_PERCENT_VALUE = 0.10f;
    private static final double STEP_TWENTY_FIVE_PERCENT_VALUE = 0.25f;
    private static final double STEP_ONE_HUNDRED_PERCENT_VALUE = 1.00f;

    private static final double DEFAULT_TEMPO = 1.00f;
    private static final double DEFAULT_PITCH = 1.00f;
    private static final double DEFAULT_STEP = STEP_TWENTY_FIVE_PERCENT_VALUE;
    private static final boolean DEFAULT_SKIP_SILENCE = false;

    private static final String TAG = "PlaybackParameterDialog";
    private static final String INITIAL_TEMPO_KEY = "initial_tempo_key";
    private static final String INITIAL_PITCH_KEY = "initial_pitch_key";

    private static final String TEMPO_KEY = "tempo_key";
    private static final String PITCH_KEY = "pitch_key";
    private static final String STEP_SIZE_KEY = "step_size_key";

    private final SliderStrategy strategy = new SliderStrategy.Quadratic(
            MINIMUM_PLAYBACK_VALUE, MAXIMUM_PLAYBACK_VALUE,
            /*centerAt=*/1.00f, /*sliderGranularity=*/10000);

    @Nullable
    private Callback callback;

    private double initialTempo = DEFAULT_TEMPO;
    private double initialPitch = DEFAULT_PITCH;
    private boolean initialSkipSilence = DEFAULT_SKIP_SILENCE;
    private double tempo = DEFAULT_TEMPO;
    private double pitch = DEFAULT_PITCH;
    private double stepSize = DEFAULT_STEP;

    @Nullable
    private DialogPlaybackParameterBinding binding;

    public static PlaybackParameterDialog newInstance(final double playbackTempo,
                                                      final double playbackPitch,
                                                      final boolean playbackSkipSilence,
                                                      final Callback callback) {
        final PlaybackParameterDialog dialog = new PlaybackParameterDialog();
        dialog.callback = callback;
        dialog.initialTempo = playbackTempo;
        dialog.initialPitch = playbackPitch;

        dialog.tempo = playbackTempo;
        dialog.pitch = playbackPitch;

        dialog.initialSkipSilence = playbackSkipSilence;
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
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        assureCorrectAppLanguage(getContext());
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            initialTempo = savedInstanceState.getDouble(INITIAL_TEMPO_KEY, DEFAULT_TEMPO);
            initialPitch = savedInstanceState.getDouble(INITIAL_PITCH_KEY, DEFAULT_PITCH);

            tempo = savedInstanceState.getDouble(TEMPO_KEY, DEFAULT_TEMPO);
            pitch = savedInstanceState.getDouble(PITCH_KEY, DEFAULT_PITCH);
            stepSize = savedInstanceState.getDouble(STEP_SIZE_KEY, DEFAULT_STEP);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(INITIAL_TEMPO_KEY, initialTempo);
        outState.putDouble(INITIAL_PITCH_KEY, initialPitch);

        outState.putDouble(TEMPO_KEY, getCurrentTempo());
        outState.putDouble(PITCH_KEY, getCurrentPitch());
        outState.putDouble(STEP_SIZE_KEY, getCurrentStepSize());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        assureCorrectAppLanguage(getContext());
        binding = DialogPlaybackParameterBinding.inflate(LayoutInflater.from(requireContext()));
        setupControlViews();

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.playback_speed_control)
                .setView(binding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, (dialogInterface, i) ->
                        setPlaybackParameters(initialTempo, initialPitch, initialSkipSilence))
                .setNeutralButton(R.string.playback_reset, (dialogInterface, i) ->
                        setPlaybackParameters(DEFAULT_TEMPO, DEFAULT_PITCH, DEFAULT_SKIP_SILENCE))
                .setPositiveButton(R.string.finish, (dialogInterface, i) ->
                        setCurrentPlaybackParameters());

        return dialogBuilder.create();
    }

    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        super.onDismiss(dialog);
        binding = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Control Views
    //////////////////////////////////////////////////////////////////////////*/

    private void setupControlViews() {
        setupHookingControl();
        setupSkipSilenceControl();

        setupTempoControl();
        setupPitchControl();

        setStepSize(stepSize);
        setupStepSizeSelector();
    }

    private void setupTempoControl() {
        if (binding != null) {
            binding.tempoCurrentText.setText(PlayerHelper.formatSpeed(tempo));
            binding.tempoMaximumText.setText(PlayerHelper.formatSpeed(MAXIMUM_PLAYBACK_VALUE));
            binding.tempoMinimumText.setText(PlayerHelper.formatSpeed(MINIMUM_PLAYBACK_VALUE));
            binding.tempoSeekbar.setMax(strategy.progressOf(MAXIMUM_PLAYBACK_VALUE));
            binding.tempoSeekbar.setProgress(strategy.progressOf(tempo));
            binding.tempoSeekbar.setOnSeekBarChangeListener(getOnTempoChangedListener());
        }
    }

    private void setupPitchControl() {
        if (binding != null) {
            binding.pitchCurrentText.setText(PlayerHelper.formatPitch(pitch));
            binding.pitchMaximumText.setText(PlayerHelper.formatPitch(MAXIMUM_PLAYBACK_VALUE));
            binding.pitchMinimumText.setText(PlayerHelper.formatPitch(MINIMUM_PLAYBACK_VALUE));
            binding.pitchSeekbar.setMax(strategy.progressOf(MAXIMUM_PLAYBACK_VALUE));
            binding.pitchSeekbar.setProgress(strategy.progressOf(pitch));
            binding.pitchSeekbar.setOnSeekBarChangeListener(getOnPitchChangedListener());
        }
    }

    private void setupHookingControl() {
        if (binding != null) {
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
                    final double minimum = Math.min(getCurrentPitch(), getCurrentTempo());
                    setSliders(minimum);
                    setCurrentPlaybackParameters();
                }
            });
        }
    }

    private void setupSkipSilenceControl() {
        if (binding != null) {
            binding.skipSilenceCheckbox.setChecked(initialSkipSilence);
            binding.skipSilenceCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) ->
                    setCurrentPlaybackParameters());
        }
    }

    private void setupStepSizeSelector() {
        if (binding != null) {
            binding.stepSizeOnePercent.setText(getPercentString(STEP_ONE_PERCENT_VALUE));
            binding.stepSizeOnePercent
                    .setOnClickListener(view -> setStepSize(STEP_ONE_PERCENT_VALUE));

            binding.stepSizeFivePercent.setText(getPercentString(STEP_FIVE_PERCENT_VALUE));
            binding.stepSizeFivePercent
                    .setOnClickListener(view -> setStepSize(STEP_FIVE_PERCENT_VALUE));

            binding.stepSizeTenPercent.setText(getPercentString(STEP_TEN_PERCENT_VALUE));
            binding.stepSizeTenPercent
                    .setOnClickListener(view -> setStepSize(STEP_TEN_PERCENT_VALUE));

            binding.stepSizeTwentyFivePercent
                    .setText(getPercentString(STEP_TWENTY_FIVE_PERCENT_VALUE));
            binding.stepSizeTwentyFivePercent
                    .setOnClickListener(view -> setStepSize(STEP_TWENTY_FIVE_PERCENT_VALUE));

            binding.stepSizeOneHundredPercent
                    .setText(getPercentString(STEP_ONE_HUNDRED_PERCENT_VALUE));
            binding.stepSizeOneHundredPercent
                    .setOnClickListener(view -> setStepSize(STEP_ONE_HUNDRED_PERCENT_VALUE));
        }
    }

    private void setStepSize(final double stepSize) {
        this.stepSize = stepSize;

        if (binding != null) {
            binding.tempoStepUp.setText(getStepUpPercentString(stepSize));
            binding.tempoStepUp.setOnClickListener(view -> {
                onTempoSliderUpdated(getCurrentTempo() + stepSize);
                setCurrentPlaybackParameters();
            });

            binding.tempoStepDown.setText(getStepDownPercentString(stepSize));
            binding.tempoStepDown.setOnClickListener(view -> {
                onTempoSliderUpdated(getCurrentTempo() - stepSize);
                setCurrentPlaybackParameters();
            });

            binding.pitchStepUp.setText(getStepUpPercentString(stepSize));
            binding.pitchStepUp.setOnClickListener(view -> {
                onPitchSliderUpdated(getCurrentPitch() + stepSize);
                setCurrentPlaybackParameters();
            });

            binding.pitchStepDown.setText(getStepDownPercentString(stepSize));
            binding.pitchStepDown.setOnClickListener(view -> {
                onPitchSliderUpdated(getCurrentPitch() - stepSize);
                setCurrentPlaybackParameters();
            });
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Sliders
    //////////////////////////////////////////////////////////////////////////*/

    private SeekBar.OnSeekBarChangeListener getOnTempoChangedListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                final double currentTempo = strategy.valueOf(progress);
                if (fromUser) {
                    onTempoSliderUpdated(currentTempo);
                    setCurrentPlaybackParameters();
                }
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
                // Do Nothing.
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                // Do Nothing.
            }
        };
    }

    private SeekBar.OnSeekBarChangeListener getOnPitchChangedListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                final double currentPitch = strategy.valueOf(progress);
                if (fromUser) { // this change is first in chain
                    onPitchSliderUpdated(currentPitch);
                    setCurrentPlaybackParameters();
                }
            }

            @Override
            public void onStartTrackingTouch(final SeekBar seekBar) {
                // Do Nothing.
            }

            @Override
            public void onStopTrackingTouch(final SeekBar seekBar) {
                // Do Nothing.
            }
        };
    }

    private void onTempoSliderUpdated(final double newTempo) {
        if (binding == null) {
            return;
        }
        if (!binding.unhookCheckbox.isChecked()) {
            setSliders(newTempo);
        } else {
            setTempoSlider(newTempo);
        }
    }

    private void onPitchSliderUpdated(final double newPitch) {
        if (binding == null) {
            return;
        }
        if (!binding.unhookCheckbox.isChecked()) {
            setSliders(newPitch);
        } else {
            setPitchSlider(newPitch);
        }
    }

    private void setSliders(final double newValue) {
        setTempoSlider(newValue);
        setPitchSlider(newValue);
    }

    private void setTempoSlider(final double newTempo) {
        if (binding == null) {
            return;
        }
        binding.tempoSeekbar.setProgress(strategy.progressOf(newTempo));
    }

    private void setPitchSlider(final double newPitch) {
        if (binding == null) {
            return;
        }
        binding.pitchSeekbar.setProgress(strategy.progressOf(newPitch));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    private void setCurrentPlaybackParameters() {
        setPlaybackParameters(getCurrentTempo(), getCurrentPitch(), getCurrentSkipSilence());
    }

    private void setPlaybackParameters(final double newTempo, final double newPitch,
                                       final boolean skipSilence) {
        if (callback != null && binding != null) {
            if (DEBUG) {
                Log.d(TAG, "Setting playback parameters to "
                        + "tempo=[" + newTempo + "], "
                        + "pitch=[" + newPitch + "]");
            }

            binding.tempoCurrentText.setText(PlayerHelper.formatSpeed(newTempo));
            binding.pitchCurrentText.setText(PlayerHelper.formatPitch(newPitch));
            callback.onPlaybackParameterChanged((float) newTempo, (float) newPitch, skipSilence);
        }
    }

    private double getCurrentTempo() {
        return binding == null ? tempo : strategy.valueOf(binding.tempoSeekbar.getProgress());
    }

    private double getCurrentPitch() {
        return binding == null ? pitch : strategy.valueOf(binding.pitchSeekbar.getProgress());
    }

    private double getCurrentStepSize() {
        return stepSize;
    }

    private boolean getCurrentSkipSilence() {
        return binding != null && binding.skipSilenceCheckbox.isChecked();
    }

    @NonNull
    private static String getStepUpPercentString(final double percent) {
        return STEP_UP_SIGN + getPercentString(percent);
    }

    @NonNull
    private static String getStepDownPercentString(final double percent) {
        return STEP_DOWN_SIGN + getPercentString(percent);
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
