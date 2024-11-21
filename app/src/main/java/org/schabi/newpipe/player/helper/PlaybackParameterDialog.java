package org.schabi.newpipe.player.helper;

import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.Player.DEBUG;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;
import static org.schabi.newpipe.util.ThemeHelper.resolveDrawable;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogPlaybackParameterBinding;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener;
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
    private static final double MAX_PITCH_OR_SPEED = 3.00f;

    private static final boolean PITCH_CTRL_MODE_PERCENT = false;
    private static final boolean PITCH_CTRL_MODE_SEMITONE = true;

    private static final double STEP_1_PERCENT_VALUE = 0.01f;
    private static final double STEP_5_PERCENT_VALUE = 0.05f;
    private static final double STEP_10_PERCENT_VALUE = 0.10f;
    private static final double STEP_25_PERCENT_VALUE = 0.25f;
    private static final double STEP_100_PERCENT_VALUE = 1.00f;

    private static final double DEFAULT_TEMPO = 1.00f;
    private static final double DEFAULT_PITCH_PERCENT = 1.00f;
    private static final double DEFAULT_STEP = STEP_25_PERCENT_VALUE;
    private static final boolean DEFAULT_SKIP_SILENCE = false;

    private static final SliderStrategy QUADRATIC_STRATEGY = new SliderStrategy.Quadratic(
            MIN_PITCH_OR_SPEED,
            MAX_PITCH_OR_SPEED,
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
        dialog.initialPitchPercent = playbackPitch;
        dialog.initialSkipSilence = playbackSkipSilence;

        dialog.tempo = dialog.initialTempo;
        dialog.pitchPercent = dialog.initialPitchPercent;
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
        Bridge.saveInstanceState(this, outState);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        assureCorrectAppLanguage(getContext());
        Bridge.restoreInstanceState(this, savedInstanceState);

        binding = DialogPlaybackParameterBinding.inflate(getLayoutInflater());
        initUI();

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity())
                .setView(binding.getRoot())
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
        // Tempo
        setText(binding.tempoMinimumText, PlayerHelper::formatSpeed, MIN_PITCH_OR_SPEED);
        setText(binding.tempoMaximumText, PlayerHelper::formatSpeed, MAX_PITCH_OR_SPEED);

        binding.tempoSeekbar.setMax(QUADRATIC_STRATEGY.progressOf(MAX_PITCH_OR_SPEED));
        setAndUpdateTempo(tempo);
        binding.tempoSeekbar.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(
                        QUADRATIC_STRATEGY,
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
        setText(binding.pitchPercentMaximumText, PlayerHelper::formatPitch, MAX_PITCH_OR_SPEED);

        binding.pitchPercentSeekbar.setMax(QUADRATIC_STRATEGY.progressOf(MAX_PITCH_OR_SPEED));
        setAndUpdatePitch(pitchPercent);
        binding.pitchPercentSeekbar.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(
                        QUADRATIC_STRATEGY,
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
        binding.pitchSemitoneSeekbar.setOnSeekBarChangeListener(
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
        pitchCtrlModeComponentMapping.forEach((v, textView) -> textView.setBackground(
                resolveDrawable(requireContext(), android.R.attr.selectableItemBackground)));

        // Mark the selected textview
        final TextView textView = pitchCtrlModeComponentMapping.get(semitones);
        if (textView != null) {
            textView.setBackground(new LayerDrawable(new Drawable[]{
                    resolveDrawable(requireContext(), R.attr.dashed_border),
                    resolveDrawable(requireContext(), android.R.attr.selectableItemBackground)
            }));
        }

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
            final double stepSizeValue,
            final TextView textView
    ) {
        setText(textView, PlaybackParameterDialog::getPercentString, stepSizeValue);
        textView.setOnClickListener(view -> {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putFloat(getString(R.string.adjustment_step_key), (float) stepSizeValue)
                    .apply();

            setStepSizeToUI(stepSizeValue);
        });
    }

    private Map<Double, TextView> getStepSizeComponentMappings() {
        return Map.of(STEP_1_PERCENT_VALUE, binding.stepSizeOnePercent,
                STEP_5_PERCENT_VALUE, binding.stepSizeFivePercent,
                STEP_10_PERCENT_VALUE, binding.stepSizeTenPercent,
                STEP_25_PERCENT_VALUE, binding.stepSizeTwentyFivePercent,
                STEP_100_PERCENT_VALUE, binding.stepSizeOneHundredPercent);
    }

    private void setStepSizeToUI(final double newStepSize) {
        // Bring all textviews into a normal state
        final Map<Double, TextView> stepSiteComponentMapping = getStepSizeComponentMappings();
        stepSiteComponentMapping.forEach((v, textView) -> textView.setBackground(
                resolveDrawable(requireContext(), android.R.attr.selectableItemBackground)));

        // Mark the selected textview
        final TextView textView = stepSiteComponentMapping.get(newStepSize);
        if (textView != null) {
            textView.setBackground(new LayerDrawable(new Drawable[]{
                    resolveDrawable(requireContext(), R.attr.dashed_border),
                    resolveDrawable(requireContext(), android.R.attr.selectableItemBackground)
            }));
        }

        // Bind to the corresponding control components
        binding.tempoStepUp.setText(getStepUpPercentString(newStepSize));
        binding.tempoStepDown.setText(getStepDownPercentString(newStepSize));

        binding.pitchPercentStepUp.setText(getStepUpPercentString(newStepSize));
        binding.pitchPercentStepDown.setText(getStepDownPercentString(newStepSize));
    }

    private double getCurrentStepSize() {
        return PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getFloat(getString(R.string.adjustment_step_key), (float) DEFAULT_STEP);
    }

    // -- Additional options --

    private void setAndUpdateSkipSilence(final boolean newSkipSilence) {
        this.skipSilence = newSkipSilence;
        binding.skipSilenceCheckbox.setChecked(newSkipSilence);
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

    private SeekBar.OnSeekBarChangeListener getTempoOrPitchSeekbarChangeListener(
            final SliderStrategy sliderStrategy,
            final DoubleConsumer newValueConsumer
    ) {
        return new SimpleOnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull final SeekBar seekBar,
                                          final int progress,
                                          final boolean fromUser) {
                if (fromUser) { // ensure that the user triggered the change
                    newValueConsumer.accept(sliderStrategy.valueOf(progress));
                    updateCallback();
                }
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
        this.tempo = MathUtils.clamp(newTempo, MIN_PITCH_OR_SPEED, MAX_PITCH_OR_SPEED);

        binding.tempoSeekbar.setProgress(QUADRATIC_STRATEGY.progressOf(tempo));
        setText(binding.tempoCurrentText, PlayerHelper::formatSpeed, tempo);
    }

    private void setAndUpdatePitch(final double newPitch) {
        this.pitchPercent = calcValidPitch(newPitch);

        binding.pitchPercentSeekbar.setProgress(QUADRATIC_STRATEGY.progressOf(pitchPercent));
        binding.pitchSemitoneSeekbar.setProgress(SEMITONE_STRATEGY.progressOf(pitchPercent));
        setText(binding.pitchPercentCurrentText,
                PlayerHelper::formatPitch,
                pitchPercent);
        setText(binding.pitchSemitoneCurrentText,
                PlayerSemitoneHelper::formatPitchSemitones,
                pitchPercent);
    }

    private double calcValidPitch(final double newPitch) {
        final double calcPitch = MathUtils.clamp(newPitch, MIN_PITCH_OR_SPEED, MAX_PITCH_OR_SPEED);

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
