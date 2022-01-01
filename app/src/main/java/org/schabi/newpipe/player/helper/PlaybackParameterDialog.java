package org.schabi.newpipe.player.helper;

import static org.schabi.newpipe.player.Player.DEBUG;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
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

    private SeekBar tempoSlider;
    private TextView tempoCurrentText;
    private TextView tempoStepDownText;
    private TextView tempoStepUpText;

    private SeekBar pitchSlider;
    private TextView pitchCurrentText;
    private TextView pitchStepDownText;
    private TextView pitchStepUpText;

    private CheckBox unhookingCheckbox;
    private CheckBox skipSilenceCheckbox;

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
    public void onAttach(final Context context) {
        super.onAttach(context);
        if (context instanceof Callback) {
            callback = (Callback) context;
        } else if (callback == null) {
            dismiss();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
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

        final View view = View.inflate(getContext(), R.layout.dialog_playback_parameter, null);
        initUI(view);
        initUIData();

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity())
                .setView(view)
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

    private void initUI(@NonNull final View rootView) {
        // Tempo
        tempoSlider = Objects.requireNonNull(rootView.findViewById(R.id.tempoSeekbar));
        tempoCurrentText = Objects.requireNonNull(rootView.findViewById(R.id.tempoCurrentText));
        tempoStepUpText = Objects.requireNonNull(rootView.findViewById(R.id.tempoStepUp));
        tempoStepDownText = Objects.requireNonNull(rootView.findViewById(R.id.tempoStepDown));

        setText(rootView, R.id.tempoMinimumText, PlayerHelper::formatSpeed, MINIMUM_PLAYBACK_VALUE);
        setText(rootView, R.id.tempoMaximumText, PlayerHelper::formatSpeed, MAXIMUM_PLAYBACK_VALUE);

        // Pitch
        pitchSlider = Objects.requireNonNull(rootView.findViewById(R.id.pitchSeekbar));
        pitchCurrentText = Objects.requireNonNull(rootView.findViewById(R.id.pitchCurrentText));
        pitchStepUpText = Objects.requireNonNull(rootView.findViewById(R.id.pitchStepUp));
        pitchStepDownText = Objects.requireNonNull(rootView.findViewById(R.id.pitchStepDown));

        setText(rootView, R.id.pitchMinimumText, PlayerHelper::formatPitch, MINIMUM_PLAYBACK_VALUE);
        setText(rootView, R.id.pitchMaximumText, PlayerHelper::formatPitch, MAXIMUM_PLAYBACK_VALUE);

        // Steps
        setupStepTextView(rootView, R.id.stepSizeOnePercent, STEP_1_PERCENT_VALUE);
        setupStepTextView(rootView, R.id.stepSizeFivePercent, STEP_5_PERCENT_VALUE);
        setupStepTextView(rootView, R.id.stepSizeTenPercent, STEP_10_PERCENT_VALUE);
        setupStepTextView(rootView, R.id.stepSizeTwentyFivePercent, STEP_25_PERCENT_VALUE);
        setupStepTextView(rootView, R.id.stepSizeOneHundredPercent, STEP_100_PERCENT_VALUE);

        // Bottom controls
        unhookingCheckbox =
                Objects.requireNonNull(rootView.findViewById(R.id.unhookCheckbox));
        skipSilenceCheckbox =
                Objects.requireNonNull(rootView.findViewById(R.id.skipSilenceCheckbox));
    }

    private TextView setText(
            final TextView textView,
            final DoubleFunction<String> formatter,
            final double value
    ) {
        Objects.requireNonNull(textView).setText(formatter.apply(value));
        return textView;
    }

    private TextView setText(
            final View rootView,
            @IdRes final int idRes,
            final DoubleFunction<String> formatter,
            final double value
    ) {
        final TextView textView = rootView.findViewById(idRes);
        setText(textView, formatter, value);
        return textView;
    }

    private void setupStepTextView(
            final View rootView,
            @IdRes final int idRes,
            final double stepSizeValue
    ) {
        setText(rootView, idRes, PlaybackParameterDialog::getPercentString, stepSizeValue)
                .setOnClickListener(view -> setAndUpdateStepSize(stepSizeValue));
    }

    private void initUIData() {
        // Tempo
        tempoSlider.setMax(QUADRATIC_STRATEGY.progressOf(MAXIMUM_PLAYBACK_VALUE));
        setAndUpdateTempo(tempo);
        tempoSlider.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(this::onTempoSliderUpdated));

        registerOnStepClickListener(
                tempoStepDownText, tempo, -1, this::onTempoSliderUpdated);
        registerOnStepClickListener(
                tempoStepUpText, tempo, 1, this::onTempoSliderUpdated);

        // Pitch
        pitchSlider.setMax(QUADRATIC_STRATEGY.progressOf(MAXIMUM_PLAYBACK_VALUE));
        setAndUpdatePitch(pitch);
        pitchSlider.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(this::onPitchSliderUpdated));

        registerOnStepClickListener(
                pitchStepDownText, pitch, -1, this::onPitchSliderUpdated);
        registerOnStepClickListener(
                pitchStepUpText, pitch, 1, this::onPitchSliderUpdated);

        // Steps
        setAndUpdateStepSize(stepSize);

        // Bottom controls
        // restore whether pitch and tempo are unhooked or not
        unhookingCheckbox.setChecked(PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.playback_unhook_key), true));

        unhookingCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
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
        skipSilenceCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
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

        tempoStepUpText.setText(getStepUpPercentString(newStepSize));
        tempoStepDownText.setText(getStepDownPercentString(newStepSize));

        pitchStepUpText.setText(getStepUpPercentString(newStepSize));
        pitchStepDownText.setText(getStepDownPercentString(newStepSize));
    }

    private void setAndUpdateSkipSilence(final boolean newSkipSilence) {
        this.skipSilence = newSkipSilence;
        skipSilenceCheckbox.setChecked(newSkipSilence);
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
        if (!unhookingCheckbox.isChecked()) {
            setSliders(newTempo);
        } else {
            setAndUpdateTempo(newTempo);
        }
    }

    private void onPitchSliderUpdated(final double newPitch) {
        if (!unhookingCheckbox.isChecked()) {
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
        tempoSlider.setProgress(QUADRATIC_STRATEGY.progressOf(tempo));
        setText(tempoCurrentText, PlayerHelper::formatSpeed, tempo);
    }

    private void setAndUpdatePitch(final double newPitch) {
        this.pitch = newPitch;
        pitchSlider.setProgress(QUADRATIC_STRATEGY.progressOf(pitch));
        setText(pitchCurrentText, PlayerHelper::formatPitch, pitch);
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
