package org.schabi.newpipe.player.helper;

import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.Player.DEBUG;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.math.MathUtils;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.evernote.android.state.State;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.livefront.bridge.Bridge;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogPlaybackParameterBinding;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.util.SliderStrategy;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

public class PlaybackParameterDialog extends DialogFragment {
    private static final String TAG = "PlaybackParameterDialog";

    // Minimum allowable range in ExoPlayer
    private static final double MIN_PITCH_OR_SPEED = 0.10f;
    static final double MAX_PLAYBACK_SPEED = 10.00f;
    static final double MAX_PITCH = 3.00f;

    private static final boolean PITCH_CTRL_MODE_PERCENT = false;
    private static final boolean PITCH_CTRL_MODE_SEMITONE = true;

    private static final double DEFAULT_TEMPO = 1.00f;
    private static final double DEFAULT_PITCH_PERCENT = 1.00f;
    private static final boolean DEFAULT_SKIP_SILENCE = false;

    private static final SliderStrategy TEMPO_STRATEGY = new SliderStrategy.Quadratic(
            MIN_PITCH_OR_SPEED,
            MAX_PLAYBACK_SPEED,
            1.00f,
            10_000);
    private static final SliderStrategy PITCH_STRATEGY = new SliderStrategy.Quadratic(
            MIN_PITCH_OR_SPEED,
            MAX_PITCH,
            1.00f,
            10_000);

    private static final SliderStrategy SEMITONE_STRATEGY = new SliderStrategy() {
        @Override
        public int progressOf(final double value) {
            return PlayerSemitoneHelper.percentToSemitones(value) + 12;
        }

        @Override
        public double valueOf(final int progress) {
            return PlayerSemitoneHelper.semitonesToPercent(progress - 12);
        }
    };

    @Nullable
    private Callback callback;

    @Nullable
    private Runnable channelSkippingAction;

    @State
    double initialTempo = DEFAULT_TEMPO;
    @State
    double initialPitchPercent = DEFAULT_PITCH_PERCENT;
    @State
    boolean initialSkipSilence = DEFAULT_SKIP_SILENCE;

    @State
    double tempo = DEFAULT_TEMPO;
    @State
    double pitchPercent = DEFAULT_PITCH_PERCENT;
    @State
    boolean skipSilence = DEFAULT_SKIP_SILENCE;

    @State
    boolean skipSilenceAvailable = true;

    private DialogPlaybackParameterBinding binding;

    public static PlaybackParameterDialog newInstance(
            final double playbackTempo,
            final double playbackPitch,
            final boolean playbackSkipSilence,
            final boolean skipSilenceAvailable,
            final Callback callback
    ) {
        final PlaybackParameterDialog dialog = new PlaybackParameterDialog();
        dialog.callback = callback;
        dialog.skipSilenceAvailable = skipSilenceAvailable;

        dialog.initialTempo = playbackTempo;
        dialog.initialPitchPercent = playbackPitch;
        dialog.initialSkipSilence = playbackSkipSilence;

        dialog.tempo = dialog.initialTempo;
        dialog.pitchPercent = dialog.initialPitchPercent;
        dialog.skipSilence = dialog.initialSkipSilence;

        return dialog;
    }

    public PlaybackParameterDialog withChannelSkippingAction(
            @Nullable final Runnable action) {
        channelSkippingAction = action;
        return this;
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
        Bridge.saveInstanceState(this, outState);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        Bridge.restoreInstanceState(this, savedInstanceState);

        final MaterialAlertDialogBuilder dialogBuilder =
                new MaterialAlertDialogBuilder(requireActivity());
        binding = DialogPlaybackParameterBinding.inflate(
                android.view.LayoutInflater.from(dialogBuilder.getContext()));
        initUI();

        dialogBuilder.setView(binding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                    setAndUpdateTempo(initialTempo);
                    setAndUpdatePitch(initialPitchPercent);
                    setAndUpdateSkipSilence(initialSkipSilence);
                    updateCallback();
                })
                .setNeutralButton(R.string.playback_reset, (dialogInterface, i) -> {
                    setAndUpdateTempo(DEFAULT_TEMPO);
                    setAndUpdatePitch(DEFAULT_PITCH_PERCENT);
                    setAndUpdateSkipSilence(DEFAULT_SKIP_SILENCE);
                    updateCallback();
                })
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> updateCallback());

        return dialogBuilder.create();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // UI Initialization and Control
    //////////////////////////////////////////////////////////////////////////*/

    private void initUI() {
        binding.channelSkipButton.setVisibility(channelSkippingAction == null
                ? View.GONE : View.VISIBLE);
        binding.channelSkipButton.setOnClickListener(view -> {
            if (channelSkippingAction != null) {
                updateCallback();
                dismiss();
                channelSkippingAction.run();
            }
        });

        // Tempo
        setText(binding.tempoMinimumText, PlayerHelper::formatSpeed, MIN_PITCH_OR_SPEED);
        setText(binding.tempoMaximumText, PlayerHelper::formatSpeed, MAX_PLAYBACK_SPEED);

        binding.tempoSeekbar.setValueTo(TEMPO_STRATEGY.progressOf(MAX_PLAYBACK_SPEED));
        binding.tempoSeekbar.setLabelFormatter(value ->
                PlayerHelper.formatSpeed(TEMPO_STRATEGY.valueOf(Math.round(value))));
        setAndUpdateTempo(tempo);
        binding.tempoSeekbar.addOnChangeListener(
                getTempoOrPitchSeekbarChangeListener(
                        TEMPO_STRATEGY,
                        this::onTempoSliderUpdated));

        registerOnStepClickListener(
                binding.tempoStepDown,
                () -> tempo,
                -1,
                this::onTempoSliderUpdated);
        registerOnStepClickListener(
                binding.tempoStepUp,
                () -> tempo,
                1,
                this::onTempoSliderUpdated);

        // Pitch
        binding.pitchToogleControlModes.setOnClickListener(v -> {
            final boolean isCurrentlyVisible =
                    binding.pitchControlModeTabs.getVisibility() == View.GONE;
            binding.pitchControlModeTabs.setVisibility(isCurrentlyVisible
                    ? View.VISIBLE
                    : View.GONE);
            animateRotation(binding.pitchToogleControlModes,
                    VideoPlayerUi.DEFAULT_CONTROLS_DURATION,
                    isCurrentlyVisible ? 180 : 0);
        });

        getPitchControlModeComponentMappings()
                .forEach(this::setupPitchControlModeTextView);
        // Initialization is done at the end

        // Pitch - Percent
        setText(binding.pitchPercentMinimumText, PlayerHelper::formatPitch, MIN_PITCH_OR_SPEED);
        setText(binding.pitchPercentMaximumText, PlayerHelper::formatPitch, MAX_PITCH);

        binding.pitchPercentSeekbar.setValueTo(PITCH_STRATEGY.progressOf(MAX_PITCH));
        binding.pitchPercentSeekbar.setLabelFormatter(value ->
                PlayerHelper.formatPitch(PITCH_STRATEGY.valueOf(Math.round(value))));
        binding.pitchSemitoneSeekbar.setLabelFormatter(value ->
                PlayerSemitoneHelper.formatPitchSemitones(
                        SEMITONE_STRATEGY.valueOf(Math.round(value))));
        setAndUpdatePitch(pitchPercent);
        binding.pitchPercentSeekbar.addOnChangeListener(
                getTempoOrPitchSeekbarChangeListener(
                        PITCH_STRATEGY,
                        this::onPitchPercentSliderUpdated));

        registerOnStepClickListener(
                binding.pitchPercentStepDown,
                () -> pitchPercent,
                -1,
                this::onPitchPercentSliderUpdated);
        registerOnStepClickListener(
                binding.pitchPercentStepUp,
                () -> pitchPercent,
                1,
                this::onPitchPercentSliderUpdated);

        // Pitch - Semitone
        binding.pitchSemitoneSeekbar.addOnChangeListener(
                getTempoOrPitchSeekbarChangeListener(
                        SEMITONE_STRATEGY,
                        this::onPitchPercentSliderUpdated));

        registerOnSemitoneStepClickListener(
                binding.pitchSemitoneStepDown,
                -1,
                this::onPitchPercentSliderUpdated);
        registerOnSemitoneStepClickListener(
                binding.pitchSemitoneStepUp,
                1,
                this::onPitchPercentSliderUpdated);

        // Steps
        getStepSizeComponentMappings()
                .forEach(this::setupStepTextView);
        // Initialize UI
        setStepSizeToUI(getCurrentStepSize());

        // Bottom controls
        bindCheckboxWithBoolPref(
                binding.unhookCheckbox,
                R.string.playback_unhook_key,
                true,
                isChecked -> {
                    if (!isChecked) {
                        // when unchecked, slide back to the minimum of current tempo or pitch
                        ensureHookIsValidAndUpdateCallBack();
                    }
                });

        setAndUpdateSkipSilence(skipSilence);
        binding.skipSilenceCheckbox.setEnabled(skipSilenceAvailable);
        binding.skipSilenceCheckbox.setText(skipSilenceAvailable
                ? R.string.skip_silence_checkbox : R.string.skip_silence_unavailable_live);
        binding.skipSilenceCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            skipSilence = isChecked;
            updateCallback();
        });

        // PitchControlMode has to be initialized at the end because it requires the unhookCheckbox
        changePitchControlMode(isCurrentPitchControlModeSemitone());
    }

    // -- General formatting --

    private void setText(
            final TextView textView,
            final DoubleFunction<String> formatter,
            final double value
    ) {
        Objects.requireNonNull(textView).setText(formatter.apply(value));
    }

    // -- Steps --

    private void registerOnStepClickListener(
            final TextView stepTextView,
            final DoubleSupplier currentValueSupplier,
            final double direction, // -1 for step down, +1 for step up
            final DoubleConsumer newValueConsumer
    ) {
        stepTextView.setOnClickListener(view -> {
            newValueConsumer.accept(
                    currentValueSupplier.getAsDouble() + 1 * getCurrentStepSize() * direction);
            updateCallback();
        });
    }

    private void registerOnSemitoneStepClickListener(
            final TextView stepTextView,
            final int direction, // -1 for step down, +1 for step up
            final DoubleConsumer newValueConsumer
    ) {
        stepTextView.setOnClickListener(view -> {
            newValueConsumer.accept(PlayerSemitoneHelper.semitonesToPercent(
                    PlayerSemitoneHelper.percentToSemitones(this.pitchPercent) + direction));
            updateCallback();
        });
    }

    // -- Pitch --

    private void setupPitchControlModeTextView(
            final boolean semitones,
            final TextView textView
    ) {
        textView.setOnClickListener(view -> {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putBoolean(getString(R.string.playback_adjust_by_semitones_key), semitones)
                    .apply();

            changePitchControlMode(semitones);
        });
    }

    private Map<Boolean, TextView> getPitchControlModeComponentMappings() {
        return Map.of(PITCH_CTRL_MODE_PERCENT, binding.pitchControlModePercent,
                PITCH_CTRL_MODE_SEMITONE, binding.pitchControlModeSemitone);
    }

    private void changePitchControlMode(final boolean semitones) {
        // Bring all textviews into a normal state
        final Map<Boolean, TextView> pitchCtrlModeComponentMapping =
                getPitchControlModeComponentMappings();
        pitchCtrlModeComponentMapping.forEach((value, view) ->
                ((Chip) view).setChecked(value == semitones));

        // Show or hide component
        binding.pitchPercentControl.setVisibility(semitones ? View.GONE : View.VISIBLE);
        binding.pitchSemitoneControl.setVisibility(semitones ? View.VISIBLE : View.GONE);

        if (semitones) {
            // Recalculate pitch percent when changing to semitone
            // (as it could be an invalid semitone value)
            final double newPitchPercent = calcValidPitch(pitchPercent);

            // If the values differ set the new pitch
            if (this.pitchPercent != newPitchPercent) {
                if (DEBUG) {
                    Log.d(TAG, "Bringing pitchPercent to correct corresponding semitone: "
                            + "currentPitchPercent = " + pitchPercent + ", "
                            + "newPitchPercent = " + newPitchPercent
                    );
                }
                this.onPitchPercentSliderUpdated(newPitchPercent);
                updateCallback();
            }
        } else if (!binding.unhookCheckbox.isChecked()) {
            // When changing to percent it's possible that tempo is != pitch
            ensureHookIsValidAndUpdateCallBack();
        }
    }

    private boolean isCurrentPitchControlModeSemitone() {
        return PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(
                        getString(R.string.playback_adjust_by_semitones_key),
                        PITCH_CTRL_MODE_PERCENT);
    }

    // -- Steps (Set) --

    private void setupStepTextView(
            final float stepSizeValue,
            final TextView textView
    ) {
        setText(textView, PlaybackParameterDialog::getPercentString, stepSizeValue);
        textView.setOnClickListener(view -> {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putFloat(getString(R.string.adjustment_step_key), stepSizeValue)
                    .apply();

            setStepSizeToUI(stepSizeValue);
        });
    }

    private Map<Float, TextView> getStepSizeComponentMappings() {
        return Map.of(PlaybackParameterPreferences.STEP_1_PERCENT,
                binding.stepSizeOnePercent,
                PlaybackParameterPreferences.STEP_5_PERCENT,
                binding.stepSizeFivePercent,
                PlaybackParameterPreferences.STEP_10_PERCENT,
                binding.stepSizeTenPercent,
                PlaybackParameterPreferences.STEP_25_PERCENT,
                binding.stepSizeTwentyFivePercent);
    }

    private void setStepSizeToUI(final float newStepSize) {
        // Bring all textviews into a normal state
        final Map<Float, TextView> stepSiteComponentMapping = getStepSizeComponentMappings();
        stepSiteComponentMapping.forEach((value, view) ->
                ((Chip) view).setChecked(value == newStepSize));

        // Bind to the corresponding control components
        binding.tempoStepUp.setText(getStepUpPercentString(newStepSize));
        binding.tempoStepDown.setText(getStepDownPercentString(newStepSize));

        binding.pitchPercentStepUp.setText(getStepUpPercentString(newStepSize));
        binding.pitchPercentStepDown.setText(getStepDownPercentString(newStepSize));
    }

    private float getCurrentStepSize() {
        return PlaybackParameterPreferences.getAdjustmentStep(requireContext());
    }

    // -- Additional options --

    private void setAndUpdateSkipSilence(final boolean newSkipSilence) {
        this.skipSilence = newSkipSilence && skipSilenceAvailable;
        binding.skipSilenceCheckbox.setChecked(this.skipSilence);
    }

    @SuppressWarnings("SameParameterValue") // this method was written to be reusable
    private void bindCheckboxWithBoolPref(
            @NonNull final CheckBox checkBox,
            @StringRes final int resId,
            final boolean defaultValue,
            @NonNull final Consumer<Boolean> onInitialValueOrValueChange
    ) {
        final boolean prefValue = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(resId), defaultValue);

        checkBox.setChecked(prefValue);

        onInitialValueOrValueChange.accept(prefValue);

        checkBox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save whether pitch and tempo are unhooked or not
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putBoolean(getString(resId), isChecked)
                    .apply();

            onInitialValueOrValueChange.accept(isChecked);
        });
    }

    /**
     * Ensures that the slider hook is valid and if not sets and updates the sliders accordingly.
     * <br/>
     * You have to ensure by yourself that the hooking is active.
     */
    private void ensureHookIsValidAndUpdateCallBack() {
        if (tempo != pitchPercent) {
            setSliders(Math.min(tempo, pitchPercent));
            updateCallback();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Sliders
    //////////////////////////////////////////////////////////////////////////*/

    private Slider.OnChangeListener getTempoOrPitchSeekbarChangeListener(
            final SliderStrategy sliderStrategy,
            final DoubleConsumer newValueConsumer
    ) {
        return (slider, value, fromUser) -> {
            if (fromUser) {
                newValueConsumer.accept(sliderStrategy.valueOf(Math.round(value)));
                updateCallback();
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

    private void onPitchPercentSliderUpdated(final double newPitch) {
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
        this.tempo = MathUtils.clamp(newTempo, MIN_PITCH_OR_SPEED, MAX_PLAYBACK_SPEED);

        binding.tempoSeekbar.setValue(TEMPO_STRATEGY.progressOf(tempo));
        setText(binding.tempoCurrentText, PlayerHelper::formatSpeed, tempo);
    }

    private void setAndUpdatePitch(final double newPitch) {
        this.pitchPercent = calcValidPitch(newPitch);

        binding.pitchPercentSeekbar.setValue(PITCH_STRATEGY.progressOf(pitchPercent));
        binding.pitchSemitoneSeekbar.setValue(
                MathUtils.clamp(SEMITONE_STRATEGY.progressOf(pitchPercent), 0, 24));
        setText(binding.pitchPercentCurrentText,
                PlayerHelper::formatPitch,
                pitchPercent);
        setText(binding.pitchSemitoneCurrentText,
                PlayerSemitoneHelper::formatPitchSemitones,
                pitchPercent);
    }

    private double calcValidPitch(final double newPitch) {
        final double calcPitch = MathUtils.clamp(newPitch, MIN_PITCH_OR_SPEED, MAX_PITCH);

        if (!isCurrentPitchControlModeSemitone()) {
            return calcPitch;
        }

        return PlayerSemitoneHelper.semitonesToPercent(
                PlayerSemitoneHelper.percentToSemitones(calcPitch));
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
                    + "tempo = " + tempo + ", "
                    + "pitchPercent = " + pitchPercent + ", "
                    + "skipSilence = " + skipSilence
            );
        }
        callback.onPlaybackParameterChanged((float) tempo, (float) pitchPercent, skipSilence);
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
