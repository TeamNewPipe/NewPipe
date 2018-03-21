package org.schabi.newpipe.player.helper;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import org.schabi.newpipe.R;

import static org.schabi.newpipe.player.BasePlayer.DEBUG;

public class PlaybackParameterDialog extends DialogFragment {
    private static final String TAG = "PlaybackParameterDialog";

    public static final float MINIMUM_PLAYBACK_VALUE = 0.25f;
    public static final float MAXIMUM_PLAYBACK_VALUE = 3.00f;

    public static final String STEP_UP_SIGN = "+";
    public static final String STEP_DOWN_SIGN = "-";
    public static final float PLAYBACK_STEP_VALUE = 0.05f;

    public static final float NIGHTCORE_TEMPO = 1.20f;
    public static final float NIGHTCORE_PITCH_LOWER = 1.15f;
    public static final float NIGHTCORE_PITCH_UPPER = 1.25f;

    public static final float DEFAULT_TEMPO = 1.00f;
    public static final float DEFAULT_PITCH = 1.00f;

    private static final String INITIAL_TEMPO_KEY = "initial_tempo_key";
    private static final String INITIAL_PITCH_KEY = "initial_pitch_key";

    public interface Callback {
        void onPlaybackParameterChanged(final float playbackTempo, final float playbackPitch);
    }

    private Callback callback;

    private float initialTempo = DEFAULT_TEMPO;
    private float initialPitch = DEFAULT_PITCH;

    private SeekBar tempoSlider;
    private TextView tempoMinimumText;
    private TextView tempoMaximumText;
    private TextView tempoCurrentText;
    private TextView tempoStepDownText;
    private TextView tempoStepUpText;

    private SeekBar pitchSlider;
    private TextView pitchMinimumText;
    private TextView pitchMaximumText;
    private TextView pitchCurrentText;
    private TextView pitchStepDownText;
    private TextView pitchStepUpText;

    private CheckBox unhookingCheckbox;

    private TextView nightCorePresetText;
    private TextView resetPresetText;

    public static PlaybackParameterDialog newInstance(final float playbackTempo,
                                                      final float playbackPitch) {
        PlaybackParameterDialog dialog = new PlaybackParameterDialog();
        dialog.initialTempo = playbackTempo;
        dialog.initialPitch = playbackPitch;
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context != null && context instanceof Callback) {
            callback = (Callback) context;
        } else {
            dismiss();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            initialTempo = savedInstanceState.getFloat(INITIAL_TEMPO_KEY, DEFAULT_TEMPO);
            initialPitch = savedInstanceState.getFloat(INITIAL_PITCH_KEY, DEFAULT_PITCH);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putFloat(INITIAL_TEMPO_KEY, initialTempo);
        outState.putFloat(INITIAL_PITCH_KEY, initialPitch);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final View view = View.inflate(getContext(), R.layout.dialog_playback_parameter, null);
        setupView(view);

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.playback_speed_control)
                .setView(view)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, (dialogInterface, i) ->
                        setPlaybackParameters(initialTempo, initialPitch))
                .setPositiveButton(R.string.finish, (dialogInterface, i) ->
                        setPlaybackParameters(getCurrentTempo(), getCurrentPitch()));

        return dialogBuilder.create();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog Builder
    //////////////////////////////////////////////////////////////////////////*/

    private void setupView(@NonNull View rootView) {
        setupHookingControl(rootView);
        setupTempoControl(rootView);
        setupPitchControl(rootView);
        setupPresetControl(rootView);
    }

    private void setupTempoControl(@NonNull View rootView) {
        tempoSlider = rootView.findViewById(R.id.tempoSeekbar);
        tempoMinimumText = rootView.findViewById(R.id.tempoMinimumText);
        tempoMaximumText = rootView.findViewById(R.id.tempoMaximumText);
        tempoCurrentText = rootView.findViewById(R.id.tempoCurrentText);
        tempoStepUpText = rootView.findViewById(R.id.tempoStepUp);
        tempoStepDownText = rootView.findViewById(R.id.tempoStepDown);

        tempoCurrentText.setText(PlayerHelper.formatSpeed(initialTempo));
        tempoMaximumText.setText(PlayerHelper.formatSpeed(MAXIMUM_PLAYBACK_VALUE));
        tempoMinimumText.setText(PlayerHelper.formatSpeed(MINIMUM_PLAYBACK_VALUE));

        tempoStepUpText.setText(getStepUpPercentString(PLAYBACK_STEP_VALUE));
        tempoStepUpText.setOnClickListener(view ->
                setTempo(getCurrentTempo() + PLAYBACK_STEP_VALUE));

        tempoStepDownText.setText(getStepDownPercentString(PLAYBACK_STEP_VALUE));
        tempoStepDownText.setOnClickListener(view ->
                setTempo(getCurrentTempo() - PLAYBACK_STEP_VALUE));

        tempoSlider.setMax(getSliderEquivalent(MINIMUM_PLAYBACK_VALUE, MAXIMUM_PLAYBACK_VALUE));
        tempoSlider.setProgress(getSliderEquivalent(MINIMUM_PLAYBACK_VALUE, initialTempo));
        tempoSlider.setOnSeekBarChangeListener(getOnTempoChangedListener());
    }
    
    private SeekBar.OnSeekBarChangeListener getOnTempoChangedListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final float currentTempo = getSliderEquivalent(MINIMUM_PLAYBACK_VALUE, progress);
                if (fromUser) { // this change is first in chain
                    setTempo(currentTempo);
                } else {
                    setPlaybackParameters(currentTempo, getCurrentPitch());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do Nothing.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do Nothing.
            }
        };
    }

    private void setupPitchControl(@NonNull View rootView) {
        pitchSlider = rootView.findViewById(R.id.pitchSeekbar);
        pitchMinimumText = rootView.findViewById(R.id.pitchMinimumText);
        pitchMaximumText = rootView.findViewById(R.id.pitchMaximumText);
        pitchCurrentText = rootView.findViewById(R.id.pitchCurrentText);
        pitchStepDownText = rootView.findViewById(R.id.pitchStepDown);
        pitchStepUpText = rootView.findViewById(R.id.pitchStepUp);

        pitchCurrentText.setText(PlayerHelper.formatPitch(initialPitch));
        pitchMaximumText.setText(PlayerHelper.formatPitch(MAXIMUM_PLAYBACK_VALUE));
        pitchMinimumText.setText(PlayerHelper.formatPitch(MINIMUM_PLAYBACK_VALUE));

        pitchStepUpText.setText(getStepUpPercentString(PLAYBACK_STEP_VALUE));
        pitchStepUpText.setOnClickListener(view ->
                setPitch(getCurrentPitch() + PLAYBACK_STEP_VALUE));

        pitchStepDownText.setText(getStepDownPercentString(PLAYBACK_STEP_VALUE));
        pitchStepDownText.setOnClickListener(view ->
                setPitch(getCurrentPitch() - PLAYBACK_STEP_VALUE));

        pitchSlider.setMax(getSliderEquivalent(MINIMUM_PLAYBACK_VALUE, MAXIMUM_PLAYBACK_VALUE));
        pitchSlider.setProgress(getSliderEquivalent(MINIMUM_PLAYBACK_VALUE, initialPitch));
        pitchSlider.setOnSeekBarChangeListener(getOnPitchChangedListener());
    }

    private SeekBar.OnSeekBarChangeListener getOnPitchChangedListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final float currentPitch = getSliderEquivalent(MINIMUM_PLAYBACK_VALUE, progress);
                if (fromUser) { // this change is first in chain
                    setPitch(currentPitch);
                } else {
                    setPlaybackParameters(getCurrentTempo(), currentPitch);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do Nothing.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do Nothing.
            }
        };
    }

    private void setupHookingControl(@NonNull View rootView) {
        unhookingCheckbox = rootView.findViewById(R.id.unhookCheckbox);
        unhookingCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (isChecked) return;
            // When unchecked, slide back to the minimum of current tempo or pitch
            final float minimum = Math.min(getCurrentPitch(), getCurrentTempo());
            setSliders(minimum);
        });
    }

    private void setupPresetControl(@NonNull View rootView) {
        nightCorePresetText = rootView.findViewById(R.id.presetNightcore);
        nightCorePresetText.setOnClickListener(view -> {
            final float randomPitch = NIGHTCORE_PITCH_LOWER +
                    (float) Math.random() * (NIGHTCORE_PITCH_UPPER - NIGHTCORE_PITCH_LOWER);

            setTempoSlider(NIGHTCORE_TEMPO);
            setPitchSlider(randomPitch);
        });

        resetPresetText = rootView.findViewById(R.id.presetReset);
        resetPresetText.setOnClickListener(view -> {
            setTempoSlider(DEFAULT_TEMPO);
            setPitchSlider(DEFAULT_PITCH);
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    private void setTempo(final float newTempo) {
        if (unhookingCheckbox == null) return;
        if (!unhookingCheckbox.isChecked()) {
            setSliders(newTempo);
        } else {
            setTempoSlider(newTempo);
        }
    }

    private void setPitch(final float newPitch) {
        if (unhookingCheckbox == null) return;
        if (!unhookingCheckbox.isChecked()) {
            setSliders(newPitch);
        } else {
            setPitchSlider(newPitch);
        }
    }

    private void setSliders(final float newValue) {
        setTempoSlider(newValue);
        setPitchSlider(newValue);
    }

    private void setTempoSlider(final float newTempo) {
        if (tempoSlider == null) return;
        // seekbar doesn't register progress if it is the same as the existing progress
        tempoSlider.setProgress(Integer.MAX_VALUE);
        tempoSlider.setProgress(getSliderEquivalent(MINIMUM_PLAYBACK_VALUE, newTempo));
    }

    private void setPitchSlider(final float newPitch) {
        if (pitchSlider == null) return;
        pitchSlider.setProgress(Integer.MAX_VALUE);
        pitchSlider.setProgress(getSliderEquivalent(MINIMUM_PLAYBACK_VALUE, newPitch));
    }

    private void setPlaybackParameters(final float tempo, final float pitch) {
        if (callback != null && tempoCurrentText != null && pitchCurrentText != null) {
            if (DEBUG) Log.d(TAG, "Setting playback parameters to " +
                    "tempo=[" + tempo + "], " +
                    "pitch=[" + pitch + "]");

            tempoCurrentText.setText(PlayerHelper.formatSpeed(tempo));
            pitchCurrentText.setText(PlayerHelper.formatPitch(pitch));
            callback.onPlaybackParameterChanged(tempo, pitch);
        }
    }

    private float getCurrentTempo() {
        return tempoSlider == null ? initialTempo : getSliderEquivalent(MINIMUM_PLAYBACK_VALUE,
                tempoSlider.getProgress());
    }

    private float getCurrentPitch() {
        return pitchSlider == null ? initialPitch : getSliderEquivalent(MINIMUM_PLAYBACK_VALUE,
                pitchSlider.getProgress());
    }

    /**
     * Converts from zeroed float with a minimum offset to the nearest rounded slider
     * equivalent integer
     * */
    private static int getSliderEquivalent(final float minimumValue, final float floatValue) {
        return Math.round((floatValue - minimumValue) * 100f);
    }

    /**
     * Converts from slider integer value to an equivalent float value with a given minimum offset
     * */
    private static float getSliderEquivalent(final float minimumValue, final int intValue) {
        return ((float) intValue) / 100f + minimumValue;
    }

    private static String getStepUpPercentString(final float percent) {
        return STEP_UP_SIGN + PlayerHelper.formatPitch(percent);
    }

    private static String getStepDownPercentString(final float percent) {
        return STEP_DOWN_SIGN + PlayerHelper.formatPitch(percent);
    }
}
