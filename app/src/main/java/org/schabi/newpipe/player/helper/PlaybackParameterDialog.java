package org.schabi.newpipe.player.helper;

import static org.schabi.newpipe.player.Player.DEBUG;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener;
import org.schabi.newpipe.util.SliderStrategy;

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
    private static final int DEFAULT_SEMITONES = 0;
    private static final double DEFAULT_STEP = STEP_TWENTY_FIVE_PERCENT_VALUE;
    private static final boolean DEFAULT_SKIP_SILENCE = false;

    @NonNull
    private static final String TAG = "PlaybackParameterDialog";
    @NonNull
    private static final String INITIAL_TEMPO_KEY = "initial_tempo_key";
    @NonNull
    private static final String INITIAL_PITCH_KEY = "initial_pitch_key";

    @NonNull
    private static final String TEMPO_KEY = "tempo_key";
    @NonNull
    private static final String PITCH_KEY = "pitch_key";
    @NonNull
    private static final String STEP_SIZE_KEY = "step_size_key";

    @NonNull
    private final SliderStrategy strategy = new SliderStrategy.Quadratic(
            MINIMUM_PLAYBACK_VALUE, MAXIMUM_PLAYBACK_VALUE,
            /*centerAt=*/1.00f, /*sliderGranularity=*/10000);

    @Nullable
    private Callback callback;

    private double initialTempo = DEFAULT_TEMPO;
    private double initialPitch = DEFAULT_PITCH;
    private int initialSemitones = DEFAULT_SEMITONES;
    private boolean initialSkipSilence = DEFAULT_SKIP_SILENCE;
    private double tempo = DEFAULT_TEMPO;
    private double pitch = DEFAULT_PITCH;
    private int semitones = DEFAULT_SEMITONES;

    @Nullable
    private SeekBar tempoSlider;
    @Nullable
    private TextView tempoCurrentText;
    @Nullable
    private TextView tempoStepDownText;
    @Nullable
    private TextView tempoStepUpText;
    @Nullable
    private SeekBar pitchSlider;
    @Nullable
    private TextView pitchCurrentText;
    @Nullable
    private TextView pitchStepDownText;
    @Nullable
    private TextView pitchStepUpText;
    @Nullable
    private SeekBar semitoneSlider;
    @Nullable
    private TextView semitoneCurrentText;
    @Nullable
    private TextView semitoneStepDownText;
    @Nullable
    private TextView semitoneStepUpText;
    @Nullable
    private CheckBox unhookingCheckbox;
    @Nullable
    private CheckBox skipSilenceCheckbox;
    @Nullable
    private CheckBox adjustBySemitonesCheckbox;

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
        dialog.semitones = dialog.percentToSemitones(playbackPitch);

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
            initialSemitones = percentToSemitones(initialPitch);

            tempo = savedInstanceState.getDouble(TEMPO_KEY, DEFAULT_TEMPO);
            pitch = savedInstanceState.getDouble(PITCH_KEY, DEFAULT_PITCH);
            semitones = percentToSemitones(pitch);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(INITIAL_TEMPO_KEY, initialTempo);
        outState.putDouble(INITIAL_PITCH_KEY, initialPitch);

        outState.putDouble(TEMPO_KEY, getCurrentTempo());
        outState.putDouble(PITCH_KEY, getCurrentPitch());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        assureCorrectAppLanguage(getContext());
        final View view = View.inflate(getContext(), R.layout.dialog_playback_parameter, null);
        setupControlViews(view);

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity())
                .setView(view)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, (dialogInterface, i) ->
                        setPlaybackParameters(initialTempo, initialPitch,
                                initialSemitones, initialSkipSilence))
                .setNeutralButton(R.string.playback_reset, (dialogInterface, i) ->
                        setPlaybackParameters(DEFAULT_TEMPO, DEFAULT_PITCH,
                                DEFAULT_SEMITONES, DEFAULT_SKIP_SILENCE))
                .setPositiveButton(R.string.ok, (dialogInterface, i) ->
                        setCurrentPlaybackParameters());

        return dialogBuilder.create();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Control Views
    //////////////////////////////////////////////////////////////////////////*/

    private void setupControlViews(@NonNull final View rootView) {
        setupHookingControl(rootView);
        setupSkipSilenceControl(rootView);
        setupAdjustBySemitonesControl(rootView);

        setupTempoControl(rootView);
        setupPitchControl(rootView);
        setupSemitoneControl(rootView);

        togglePitchSliderType(rootView);

        setupStepSizeSelector(rootView);
    }

    private void togglePitchSliderType(@NonNull final View rootView) {
        final RelativeLayout pitchControl = rootView.findViewById(R.id.pitchControl);
        final RelativeLayout semitoneControl = rootView.findViewById(R.id.semitoneControl);

        final View separatorStepSizeSelector =
                rootView.findViewById(R.id.separatorStepSizeSelector);
        final RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams) separatorStepSizeSelector.getLayoutParams();
        if (pitchControl != null && semitoneControl != null && unhookingCheckbox != null) {
            if (getCurrentAdjustBySemitones()) {
                // replaces pitchControl slider with semitoneControl slider
                pitchControl.setVisibility(View.GONE);
                semitoneControl.setVisibility(View.VISIBLE);
                params.addRule(RelativeLayout.BELOW, R.id.semitoneControl);

                // forces unhook for semitones
                unhookingCheckbox.setChecked(true);
                unhookingCheckbox.setEnabled(false);

                setupTempoStepSizeSelector(rootView);
            } else {
                semitoneControl.setVisibility(View.GONE);
                pitchControl.setVisibility(View.VISIBLE);
                params.addRule(RelativeLayout.BELOW, R.id.pitchControl);

                // (re)enables hooking selection
                unhookingCheckbox.setEnabled(true);
                setupCombinedStepSizeSelector(rootView);
            }
        }
    }

    private void setupTempoControl(@NonNull final View rootView) {
        tempoSlider = rootView.findViewById(R.id.tempoSeekbar);
        final TextView tempoMinimumText = rootView.findViewById(R.id.tempoMinimumText);
        final TextView tempoMaximumText = rootView.findViewById(R.id.tempoMaximumText);
        tempoCurrentText = rootView.findViewById(R.id.tempoCurrentText);
        tempoStepUpText = rootView.findViewById(R.id.tempoStepUp);
        tempoStepDownText = rootView.findViewById(R.id.tempoStepDown);

        if (tempoCurrentText != null) {
            tempoCurrentText.setText(PlayerHelper.formatSpeed(tempo));
        }
        if (tempoMaximumText != null) {
            tempoMaximumText.setText(PlayerHelper.formatSpeed(MAXIMUM_PLAYBACK_VALUE));
        }
        if (tempoMinimumText != null) {
            tempoMinimumText.setText(PlayerHelper.formatSpeed(MINIMUM_PLAYBACK_VALUE));
        }

        if (tempoSlider != null) {
            tempoSlider.setMax(strategy.progressOf(MAXIMUM_PLAYBACK_VALUE));
            tempoSlider.setProgress(strategy.progressOf(tempo));
            tempoSlider.setOnSeekBarChangeListener(getOnTempoChangedListener());
        }
    }

    private void setupPitchControl(@NonNull final View rootView) {
        pitchSlider = rootView.findViewById(R.id.pitchSeekbar);
        final TextView pitchMinimumText = rootView.findViewById(R.id.pitchMinimumText);
        final TextView pitchMaximumText = rootView.findViewById(R.id.pitchMaximumText);
        pitchCurrentText = rootView.findViewById(R.id.pitchCurrentText);
        pitchStepDownText = rootView.findViewById(R.id.pitchStepDown);
        pitchStepUpText = rootView.findViewById(R.id.pitchStepUp);

        if (pitchCurrentText != null) {
            pitchCurrentText.setText(PlayerHelper.formatPitch(pitch));
        }
        if (pitchMaximumText != null) {
            pitchMaximumText.setText(PlayerHelper.formatPitch(MAXIMUM_PLAYBACK_VALUE));
        }
        if (pitchMinimumText != null) {
            pitchMinimumText.setText(PlayerHelper.formatPitch(MINIMUM_PLAYBACK_VALUE));
        }

        if (pitchSlider != null) {
            pitchSlider.setMax(strategy.progressOf(MAXIMUM_PLAYBACK_VALUE));
            pitchSlider.setProgress(strategy.progressOf(pitch));
            pitchSlider.setOnSeekBarChangeListener(getOnPitchChangedListener());
        }
    }

    private void setupSemitoneControl(@NonNull final View rootView) {
        semitoneSlider = rootView.findViewById(R.id.semitoneSeekbar);
        semitoneCurrentText = rootView.findViewById(R.id.semitoneCurrentText);
        semitoneStepDownText = rootView.findViewById(R.id.semitoneStepDown);
        semitoneStepUpText = rootView.findViewById(R.id.semitoneStepUp);

        if (semitoneCurrentText != null) {
            semitoneCurrentText.setText(getSignedSemitonesString(semitones));
        }

        if (semitoneSlider != null) {
            setSemitoneSlider(semitones);
            semitoneSlider.setOnSeekBarChangeListener(getOnSemitoneChangedListener());
        }

    }

    private void setupHookingControl(@NonNull final View rootView) {
        unhookingCheckbox = rootView.findViewById(R.id.unhookCheckbox);
        if (unhookingCheckbox != null) {
            // restores whether pitch and tempo are unhooked or not
            unhookingCheckbox.setChecked(PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
                    .getBoolean(getString(R.string.playback_unhook_key), true));

            unhookingCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
                // saves whether pitch and tempo are unhooked or not
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putBoolean(getString(R.string.playback_unhook_key), isChecked)
                        .apply();

                if (!isChecked) {
                    // when unchecked, slides back to the minimum of current tempo or pitch
                    final double minimum = Math.min(getCurrentPitch(), getCurrentTempo());
                    setSliders(minimum);
                    setCurrentPlaybackParameters();
                }
            });
        }
    }

    private void setupSkipSilenceControl(@NonNull final View rootView) {
        skipSilenceCheckbox = rootView.findViewById(R.id.skipSilenceCheckbox);
        if (skipSilenceCheckbox != null) {
            skipSilenceCheckbox.setChecked(initialSkipSilence);
            skipSilenceCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) ->
                    setCurrentPlaybackParameters());
        }
    }

    private void setupAdjustBySemitonesControl(@NonNull final View rootView) {
        adjustBySemitonesCheckbox = rootView.findViewById(R.id.adjustBySemitonesCheckbox);
        if (adjustBySemitonesCheckbox != null) {
            // restores whether semitone adjustment is used or not
            adjustBySemitonesCheckbox.setChecked(PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.playback_adjust_by_semitones_key), true));

            // stores whether semitone adjustment is used or not
            adjustBySemitonesCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putBoolean(getString(R.string.playback_adjust_by_semitones_key), isChecked)
                    .apply();
                togglePitchSliderType(rootView);
                if (isChecked) {
                    setPlaybackParameters(
                            getCurrentTempo(),
                            getCurrentPitch(),
                            Integer.min(12,
                                    Integer.max(-12, percentToSemitones(getCurrentPitch())
                            )),
                            getCurrentSkipSilence()
                    );
                    setSemitoneSlider(Integer.min(12,
                            Integer.max(-12, percentToSemitones(getCurrentPitch()))
                    ));
                } else {
                    setPlaybackParameters(
                            getCurrentTempo(),
                            semitonesToPercent(getCurrentSemitones()),
                            getCurrentSemitones(),
                            getCurrentSkipSilence()
                    );
                    setPitchSlider(semitonesToPercent(getCurrentSemitones()));
                }
            });
        }
    }

    private void setupStepSizeSelector(@NonNull final View rootView) {
        setStepSize(PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getFloat(getString(R.string.adjustment_step_key), (float) DEFAULT_STEP));

        final TextView stepSizeOnePercentText = rootView.findViewById(R.id.stepSizeOnePercent);
        final TextView stepSizeFivePercentText = rootView.findViewById(R.id.stepSizeFivePercent);
        final TextView stepSizeTenPercentText = rootView.findViewById(R.id.stepSizeTenPercent);
        final TextView stepSizeTwentyFivePercentText = rootView
                .findViewById(R.id.stepSizeTwentyFivePercent);
        final TextView stepSizeOneHundredPercentText = rootView
                .findViewById(R.id.stepSizeOneHundredPercent);

        if (stepSizeOnePercentText != null) {
            stepSizeOnePercentText.setText(getPercentString(STEP_ONE_PERCENT_VALUE));
            stepSizeOnePercentText
                    .setOnClickListener(view -> setStepSize(STEP_ONE_PERCENT_VALUE));
        }

        if (stepSizeFivePercentText != null) {
            stepSizeFivePercentText.setText(getPercentString(STEP_FIVE_PERCENT_VALUE));
            stepSizeFivePercentText
                    .setOnClickListener(view -> setStepSize(STEP_FIVE_PERCENT_VALUE));
        }

        if (stepSizeTenPercentText != null) {
            stepSizeTenPercentText.setText(getPercentString(STEP_TEN_PERCENT_VALUE));
            stepSizeTenPercentText
                    .setOnClickListener(view -> setStepSize(STEP_TEN_PERCENT_VALUE));
        }

        if (stepSizeTwentyFivePercentText != null) {
            stepSizeTwentyFivePercentText
                    .setText(getPercentString(STEP_TWENTY_FIVE_PERCENT_VALUE));
            stepSizeTwentyFivePercentText
                    .setOnClickListener(view -> setStepSize(STEP_TWENTY_FIVE_PERCENT_VALUE));
        }

        if (stepSizeOneHundredPercentText != null) {
            stepSizeOneHundredPercentText
                    .setText(getPercentString(STEP_ONE_HUNDRED_PERCENT_VALUE));
            stepSizeOneHundredPercentText
                    .setOnClickListener(view -> setStepSize(STEP_ONE_HUNDRED_PERCENT_VALUE));
        }
    }

    private void setupTempoStepSizeSelector(@NonNull final View rootView) {
        final TextView playbackStepTypeText = rootView.findViewById(R.id.playback_step_type);
        if (playbackStepTypeText != null) {
            playbackStepTypeText.setText(R.string.playback_tempo_step);
        }
        setupStepSizeSelector(rootView);
    }

    private void setupCombinedStepSizeSelector(@NonNull final View rootView) {
        final TextView playbackStepTypeText = rootView.findViewById(R.id.playback_step_type);
        if (playbackStepTypeText != null) {
            playbackStepTypeText.setText(R.string.playback_step);
        }
        setupStepSizeSelector(rootView);
    }

    private void setStepSize(final double stepSize) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putFloat(getString(R.string.adjustment_step_key), (float) stepSize)
                .apply();

        if (tempoStepUpText != null) {
            tempoStepUpText.setText(getStepUpPercentString(stepSize));
            tempoStepUpText.setOnClickListener(view -> {
                onTempoSliderUpdated(getCurrentTempo() + stepSize);
                setCurrentPlaybackParameters();
            });
        }

        if (tempoStepDownText != null) {
            tempoStepDownText.setText(getStepDownPercentString(stepSize));
            tempoStepDownText.setOnClickListener(view -> {
                onTempoSliderUpdated(getCurrentTempo() - stepSize);
                setCurrentPlaybackParameters();
            });
        }

        if (pitchStepUpText != null) {
            pitchStepUpText.setText(getStepUpPercentString(stepSize));
            pitchStepUpText.setOnClickListener(view -> {
                onPitchSliderUpdated(getCurrentPitch() + stepSize);
                setCurrentPlaybackParameters();
            });
        }

        if (pitchStepDownText != null) {
            pitchStepDownText.setText(getStepDownPercentString(stepSize));
            pitchStepDownText.setOnClickListener(view -> {
                onPitchSliderUpdated(getCurrentPitch() - stepSize);
                setCurrentPlaybackParameters();
            });
        }

        if (semitoneStepDownText != null) {
            semitoneStepDownText.setOnClickListener(view -> {
                onSemitoneSliderUpdated(getCurrentSemitones() - 1);
                setCurrentPlaybackParameters();
            });
        }

        if (semitoneStepUpText != null) {
            semitoneStepUpText.setOnClickListener(view -> {
                onSemitoneSliderUpdated(getCurrentSemitones() + 1);
                setCurrentPlaybackParameters();
            });
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Sliders
    //////////////////////////////////////////////////////////////////////////*/

    private SimpleOnSeekBarChangeListener getOnTempoChangedListener() {
        return new SimpleOnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                final double currentTempo = strategy.valueOf(progress);
                if (fromUser) {
                    onTempoSliderUpdated(currentTempo);
                    setCurrentPlaybackParameters();
                }
            }
        };
    }

    private SimpleOnSeekBarChangeListener getOnPitchChangedListener() {
        return new SimpleOnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                final double currentPitch = strategy.valueOf(progress);
                if (fromUser) { // this change is first in chain
                    onPitchSliderUpdated(currentPitch);
                    setCurrentPlaybackParameters();
                }
            }
        };
    }

    private SimpleOnSeekBarChangeListener getOnSemitoneChangedListener() {
        return new SimpleOnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull final SeekBar seekBar, final int progress,
                                          final boolean fromUser) {
                // semitone slider supplies values 0 to 24, subtraction by 12 is required
                final int currentSemitones = progress - 12;
                if (fromUser) { // this change is first in chain
                    onSemitoneSliderUpdated(currentSemitones);
                    // line below also saves semitones as pitch percentages
                    onPitchSliderUpdated(semitonesToPercent(currentSemitones));
                    setCurrentPlaybackParameters();
                }
            }
        };
    }

    private void onTempoSliderUpdated(final double newTempo) {
        if (!unhookingCheckbox.isChecked()) {
            setSliders(newTempo);
        } else {
            setTempoSlider(newTempo);
        }
    }

    private void onPitchSliderUpdated(final double newPitch) {
        if (!unhookingCheckbox.isChecked()) {
            setSliders(newPitch);
        } else {
            setPitchSlider(newPitch);
        }
    }

    private void onSemitoneSliderUpdated(final int newSemitone) {
        setSemitoneSlider(newSemitone);
    }

    private void setSliders(final double newValue) {
        setTempoSlider(newValue);
        setPitchSlider(newValue);
    }

    private void setTempoSlider(final double newTempo) {
        if (tempoSlider == null) {
            return;
        }
        tempoSlider.setProgress(strategy.progressOf(newTempo));
    }

    private void setPitchSlider(final double newPitch) {
        if (pitchSlider == null) {
            return;
        }
        pitchSlider.setProgress(strategy.progressOf(newPitch));
    }

    private void setSemitoneSlider(final int newSemitone) {
        if (semitoneSlider == null) {
            return;
        }
        semitoneSlider.setProgress(newSemitone + 12);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    private void setCurrentPlaybackParameters() {
        if (getCurrentAdjustBySemitones()) {
            setPlaybackParameters(
                    getCurrentTempo(),
                    semitonesToPercent(getCurrentSemitones()),
                    getCurrentSemitones(),
                    getCurrentSkipSilence()
            );
        } else {
            setPlaybackParameters(
                    getCurrentTempo(),
                    getCurrentPitch(),
                    percentToSemitones(getCurrentPitch()),
                    getCurrentSkipSilence()
            );
        }
    }

    private void setPlaybackParameters(final double newTempo, final double newPitch,
                                       final int newSemitones, final boolean skipSilence) {
        if (callback != null && tempoCurrentText != null
                && pitchCurrentText != null && semitoneCurrentText != null) {
            if (DEBUG) {
                Log.d(TAG, "Setting playback parameters to "
                        + "tempo=[" + newTempo + "], "
                        + "pitch=[" + newPitch + "], "
                        + "semitones=[" + newSemitones + "]");
            }

            tempoCurrentText.setText(PlayerHelper.formatSpeed(newTempo));
            pitchCurrentText.setText(PlayerHelper.formatPitch(newPitch));
            semitoneCurrentText.setText(getSignedSemitonesString(newSemitones));
            callback.onPlaybackParameterChanged((float) newTempo, (float) newPitch, skipSilence);
        }
    }

    private double getCurrentTempo() {
        return tempoSlider == null ? tempo : strategy.valueOf(tempoSlider.getProgress());
    }

    private double getCurrentPitch() {
        return pitchSlider == null ? pitch : strategy.valueOf(pitchSlider.getProgress());
    }

    private int getCurrentSemitones() {
        // semitoneSlider is absolute, that's why - 12
        return semitoneSlider == null ? semitones : semitoneSlider.getProgress() - 12;
    }

    private boolean getCurrentSkipSilence() {
        return skipSilenceCheckbox != null && skipSilenceCheckbox.isChecked();
    }

    private boolean getCurrentAdjustBySemitones() {
        return adjustBySemitonesCheckbox != null && adjustBySemitonesCheckbox.isChecked();
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

    @NonNull
    private static String getSignedSemitonesString(final int semitones) {
        return semitones > 0 ? "+" + semitones : "" + semitones;
    }

    public interface Callback {
        void onPlaybackParameterChanged(float playbackTempo, float playbackPitch,
                                        boolean playbackSkipSilence);
    }

    public double semitonesToPercent(final int inSemitones) {
        return Math.pow(2, inSemitones / 12.0);
    }

    public int percentToSemitones(final double inPercent) {
        return (int) Math.round(12 * Math.log(inPercent) / Math.log(2));
    }
}
