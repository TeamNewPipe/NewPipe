package org.schabi.newpipe.player.helper

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.math.MathUtils
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import icepick.Icepick
import icepick.State
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogPlaybackParameterBinding
import org.schabi.newpipe.ktx.animateRotation
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.SimpleOnSeekBarChangeListener
import org.schabi.newpipe.util.SliderStrategy
import org.schabi.newpipe.util.SliderStrategy.Quadratic
import org.schabi.newpipe.util.ThemeHelper
import java.util.Objects
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.DoubleConsumer
import java.util.function.DoubleFunction
import java.util.function.DoubleSupplier
import kotlin.math.min

class PlaybackParameterDialog() : DialogFragment() {
    private var callback: Callback? = null

    @State
    var initialTempo: Double = DEFAULT_TEMPO

    @State
    var initialPitchPercent: Double = DEFAULT_PITCH_PERCENT

    @State
    var initialSkipSilence: Boolean = DEFAULT_SKIP_SILENCE

    @State
    var tempo: Double = DEFAULT_TEMPO

    @State
    var pitchPercent: Double = DEFAULT_PITCH_PERCENT

    @State
    var skipSilence: Boolean = DEFAULT_SKIP_SILENCE
    private var binding: DialogPlaybackParameterBinding? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Callback) {
            callback = context
        } else if (callback == null) {
            dismiss()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Localization.assureCorrectAppLanguage(getContext())
        Icepick.restoreInstanceState(this, savedInstanceState)
        binding = DialogPlaybackParameterBinding.inflate(getLayoutInflater())
        initUI()
        val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireActivity())
                .setView(binding!!.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener({ dialogInterface: DialogInterface?, i: Int ->
                    setAndUpdateTempo(initialTempo)
                    setAndUpdatePitch(initialPitchPercent)
                    setAndUpdateSkipSilence(initialSkipSilence)
                    updateCallback()
                }))
                .setNeutralButton(R.string.playback_reset, DialogInterface.OnClickListener({ dialogInterface: DialogInterface?, i: Int ->
                    setAndUpdateTempo(DEFAULT_TEMPO)
                    setAndUpdatePitch(DEFAULT_PITCH_PERCENT)
                    setAndUpdateSkipSilence(DEFAULT_SKIP_SILENCE)
                    updateCallback()
                }))
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialogInterface: DialogInterface?, i: Int -> updateCallback() }))
        return dialogBuilder.create()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // UI Initialization and Control
    ////////////////////////////////////////////////////////////////////////// */
    private fun initUI() {
        // Tempo
        setText(binding!!.tempoMinimumText, DoubleFunction({ obj: Double -> PlayerHelper.formatSpeed() }), MIN_PITCH_OR_SPEED)
        setText(binding!!.tempoMaximumText, DoubleFunction({ obj: Double -> PlayerHelper.formatSpeed() }), MAX_PITCH_OR_SPEED)
        binding!!.tempoSeekbar.setMax(QUADRATIC_STRATEGY.progressOf(MAX_PITCH_OR_SPEED))
        setAndUpdateTempo(tempo)
        binding!!.tempoSeekbar.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(
                        QUADRATIC_STRATEGY, DoubleConsumer({ newTempo: Double -> onTempoSliderUpdated(newTempo) })))
        registerOnStepClickListener(
                binding!!.tempoStepDown,
                DoubleSupplier({ tempo }),
                -1.0, DoubleConsumer({ newTempo: Double -> onTempoSliderUpdated(newTempo) }))
        registerOnStepClickListener(
                binding!!.tempoStepUp,
                DoubleSupplier({ tempo }),
                1.0, DoubleConsumer({ newTempo: Double -> onTempoSliderUpdated(newTempo) }))

        // Pitch
        binding!!.pitchToogleControlModes.setOnClickListener(View.OnClickListener({ v: View? ->
            val isCurrentlyVisible: Boolean = binding!!.pitchControlModeTabs.getVisibility() == View.GONE
            binding!!.pitchControlModeTabs.setVisibility(if (isCurrentlyVisible) View.VISIBLE else View.GONE)
            binding!!.pitchToogleControlModes.animateRotation(VideoPlayerUi.Companion.DEFAULT_CONTROLS_DURATION, if (isCurrentlyVisible) 180 else 0)
        }))
        pitchControlModeComponentMappings
                .forEach(BiConsumer({ semitones: Boolean, textView: TextView -> setupPitchControlModeTextView(semitones, textView) }))
        // Initialization is done at the end

        // Pitch - Percent
        setText(binding!!.pitchPercentMinimumText, DoubleFunction({ obj: Double -> PlayerHelper.formatPitch() }), MIN_PITCH_OR_SPEED)
        setText(binding!!.pitchPercentMaximumText, DoubleFunction({ obj: Double -> PlayerHelper.formatPitch() }), MAX_PITCH_OR_SPEED)
        binding!!.pitchPercentSeekbar.setMax(QUADRATIC_STRATEGY.progressOf(MAX_PITCH_OR_SPEED))
        setAndUpdatePitch(pitchPercent)
        binding!!.pitchPercentSeekbar.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(
                        QUADRATIC_STRATEGY, DoubleConsumer({ newPitch: Double -> onPitchPercentSliderUpdated(newPitch) })))
        registerOnStepClickListener(
                binding!!.pitchPercentStepDown,
                DoubleSupplier({ pitchPercent }),
                -1.0, DoubleConsumer({ newPitch: Double -> onPitchPercentSliderUpdated(newPitch) }))
        registerOnStepClickListener(
                binding!!.pitchPercentStepUp,
                DoubleSupplier({ pitchPercent }),
                1.0, DoubleConsumer({ newPitch: Double -> onPitchPercentSliderUpdated(newPitch) }))

        // Pitch - Semitone
        binding!!.pitchSemitoneSeekbar.setOnSeekBarChangeListener(
                getTempoOrPitchSeekbarChangeListener(
                        SEMITONE_STRATEGY, DoubleConsumer({ newPitch: Double -> onPitchPercentSliderUpdated(newPitch) })))
        registerOnSemitoneStepClickListener(
                binding!!.pitchSemitoneStepDown,
                -1, DoubleConsumer({ newPitch: Double -> onPitchPercentSliderUpdated(newPitch) }))
        registerOnSemitoneStepClickListener(
                binding!!.pitchSemitoneStepUp,
                1, DoubleConsumer({ newPitch: Double -> onPitchPercentSliderUpdated(newPitch) }))

        // Steps
        stepSizeComponentMappings
                .forEach(BiConsumer({ stepSizeValue: Double, textView: TextView -> setupStepTextView(stepSizeValue, textView) }))
        // Initialize UI
        setStepSizeToUI(currentStepSize)

        // Bottom controls
        bindCheckboxWithBoolPref(
                binding!!.unhookCheckbox,
                R.string.playback_unhook_key,
                true,
                Consumer({ isChecked: Boolean? ->
                    if (!isChecked!!) {
                        // when unchecked, slide back to the minimum of current tempo or pitch
                        ensureHookIsValidAndUpdateCallBack()
                    }
                }))
        setAndUpdateSkipSilence(skipSilence)
        binding!!.skipSilenceCheckbox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener({ compoundButton: CompoundButton?, isChecked: Boolean ->
            skipSilence = isChecked
            updateCallback()
        }))

        // PitchControlMode has to be initialized at the end because it requires the unhookCheckbox
        changePitchControlMode(isCurrentPitchControlModeSemitone)
    }

    // -- General formatting --
    private fun setText(
            textView: TextView,
            formatter: DoubleFunction<String>,
            value: Double
    ) {
        Objects.requireNonNull(textView).setText(formatter.apply(value))
    }

    // -- Steps --
    private fun registerOnStepClickListener(
            stepTextView: TextView,
            currentValueSupplier: DoubleSupplier,
            direction: Double,  // -1 for step down, +1 for step up
            newValueConsumer: DoubleConsumer
    ) {
        stepTextView.setOnClickListener(View.OnClickListener({ view: View? ->
            newValueConsumer.accept(
                    currentValueSupplier.getAsDouble() + 1 * currentStepSize * direction)
            updateCallback()
        }))
    }

    private fun registerOnSemitoneStepClickListener(
            stepTextView: TextView,
            direction: Int,  // -1 for step down, +1 for step up
            newValueConsumer: DoubleConsumer
    ) {
        stepTextView.setOnClickListener(View.OnClickListener({ view: View? ->
            newValueConsumer.accept(PlayerSemitoneHelper.semitonesToPercent(
                    PlayerSemitoneHelper.percentToSemitones(pitchPercent) + direction))
            updateCallback()
        }))
    }

    // -- Pitch --
    private fun setupPitchControlModeTextView(
            semitones: Boolean,
            textView: TextView
    ) {
        textView.setOnClickListener(View.OnClickListener({ view: View? ->
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putBoolean(getString(R.string.playback_adjust_by_semitones_key), semitones)
                    .apply()
            changePitchControlMode(semitones)
        }))
    }

    private val pitchControlModeComponentMappings: Map<Boolean, TextView>
        private get() {
            return java.util.Map.of<Boolean, TextView>(PITCH_CTRL_MODE_PERCENT, binding!!.pitchControlModePercent,
                    PITCH_CTRL_MODE_SEMITONE, binding!!.pitchControlModeSemitone)
        }

    private fun changePitchControlMode(semitones: Boolean) {
        // Bring all textviews into a normal state
        val pitchCtrlModeComponentMapping: Map<Boolean, TextView> = pitchControlModeComponentMappings
        pitchCtrlModeComponentMapping.forEach(BiConsumer({ v: Boolean?, textView: TextView ->
            textView.setBackground(
                    ThemeHelper.resolveDrawable(requireContext(), R.attr.selectableItemBackground))
        }))

        // Mark the selected textview
        val textView: TextView? = pitchCtrlModeComponentMapping.get(semitones)
        if (textView != null) {
            textView.setBackground(LayerDrawable(arrayOf(
                    ThemeHelper.resolveDrawable(requireContext(), R.attr.dashed_border),
                    ThemeHelper.resolveDrawable(requireContext(), R.attr.selectableItemBackground)
            )))
        }

        // Show or hide component
        binding!!.pitchPercentControl.setVisibility(if (semitones) View.GONE else View.VISIBLE)
        binding!!.pitchSemitoneControl.setVisibility(if (semitones) View.VISIBLE else View.GONE)
        if (semitones) {
            // Recalculate pitch percent when changing to semitone
            // (as it could be an invalid semitone value)
            val newPitchPercent: Double = calcValidPitch(pitchPercent)

            // If the values differ set the new pitch
            if (pitchPercent != newPitchPercent) {
                if (Player.Companion.DEBUG) {
                    Log.d(TAG, ("Bringing pitchPercent to correct corresponding semitone: "
                            + "currentPitchPercent = " + pitchPercent + ", "
                            + "newPitchPercent = " + newPitchPercent)
                    )
                }
                onPitchPercentSliderUpdated(newPitchPercent)
                updateCallback()
            }
        } else if (!binding!!.unhookCheckbox.isChecked()) {
            // When changing to percent it's possible that tempo is != pitch
            ensureHookIsValidAndUpdateCallBack()
        }
    }

    private val isCurrentPitchControlModeSemitone: Boolean
        private get() {
            return PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean(
                            getString(R.string.playback_adjust_by_semitones_key),
                            PITCH_CTRL_MODE_PERCENT)
        }

    // -- Steps (Set) --
    private fun setupStepTextView(
            stepSizeValue: Double,
            textView: TextView
    ) {
        setText(textView, DoubleFunction({ percent: Double -> getPercentString(percent) }), stepSizeValue)
        textView.setOnClickListener(View.OnClickListener({ view: View? ->
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putFloat(getString(R.string.adjustment_step_key), stepSizeValue.toFloat())
                    .apply()
            setStepSizeToUI(stepSizeValue)
        }))
    }

    private val stepSizeComponentMappings: Map<Double, TextView>
        private get() {
            return java.util.Map.of<Double, TextView>(STEP_1_PERCENT_VALUE, binding!!.stepSizeOnePercent,
                    STEP_5_PERCENT_VALUE, binding!!.stepSizeFivePercent,
                    STEP_10_PERCENT_VALUE, binding!!.stepSizeTenPercent,
                    STEP_25_PERCENT_VALUE, binding!!.stepSizeTwentyFivePercent,
                    STEP_100_PERCENT_VALUE, binding!!.stepSizeOneHundredPercent)
        }

    private fun setStepSizeToUI(newStepSize: Double) {
        // Bring all textviews into a normal state
        val stepSiteComponentMapping: Map<Double, TextView> = stepSizeComponentMappings
        stepSiteComponentMapping.forEach(BiConsumer({ v: Double?, textView: TextView ->
            textView.setBackground(
                    ThemeHelper.resolveDrawable(requireContext(), R.attr.selectableItemBackground))
        }))

        // Mark the selected textview
        val textView: TextView? = stepSiteComponentMapping.get(newStepSize)
        if (textView != null) {
            textView.setBackground(LayerDrawable(arrayOf(
                    ThemeHelper.resolveDrawable(requireContext(), R.attr.dashed_border),
                    ThemeHelper.resolveDrawable(requireContext(), R.attr.selectableItemBackground)
            )))
        }

        // Bind to the corresponding control components
        binding!!.tempoStepUp.setText(getStepUpPercentString(newStepSize))
        binding!!.tempoStepDown.setText(getStepDownPercentString(newStepSize))
        binding!!.pitchPercentStepUp.setText(getStepUpPercentString(newStepSize))
        binding!!.pitchPercentStepDown.setText(getStepDownPercentString(newStepSize))
    }

    private val currentStepSize: Double
        private get() {
            return PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getFloat(getString(R.string.adjustment_step_key), DEFAULT_STEP.toFloat()).toDouble()
        }

    // -- Additional options --
    private fun setAndUpdateSkipSilence(newSkipSilence: Boolean) {
        skipSilence = newSkipSilence
        binding!!.skipSilenceCheckbox.setChecked(newSkipSilence)
    }

    // this method was written to be reusable
    private fun bindCheckboxWithBoolPref(
            checkBox: CheckBox,
            @StringRes resId: Int,
            defaultValue: Boolean,
            onInitialValueOrValueChange: Consumer<Boolean>
    ) {
        val prefValue: Boolean = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(resId), defaultValue)
        checkBox.setChecked(prefValue)
        onInitialValueOrValueChange.accept(prefValue)
        checkBox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener({ compoundButton: CompoundButton?, isChecked: Boolean ->
            // save whether pitch and tempo are unhooked or not
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putBoolean(getString(resId), isChecked)
                    .apply()
            onInitialValueOrValueChange.accept(isChecked)
        }))
    }

    /**
     * Ensures that the slider hook is valid and if not sets and updates the sliders accordingly.
     * <br></br>
     * You have to ensure by yourself that the hooking is active.
     */
    private fun ensureHookIsValidAndUpdateCallBack() {
        if (tempo != pitchPercent) {
            setSliders(min(tempo, pitchPercent))
            updateCallback()
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Sliders
    ////////////////////////////////////////////////////////////////////////// */
    private fun getTempoOrPitchSeekbarChangeListener(
            sliderStrategy: SliderStrategy,
            newValueConsumer: DoubleConsumer
    ): OnSeekBarChangeListener {
        return object : SimpleOnSeekBarChangeListener() {
            public override fun onProgressChanged(seekBar: SeekBar,
                                                  progress: Int,
                                                  fromUser: Boolean) {
                if (fromUser) { // ensure that the user triggered the change
                    newValueConsumer.accept(sliderStrategy.valueOf(progress))
                    updateCallback()
                }
            }
        }
    }

    private fun onTempoSliderUpdated(newTempo: Double) {
        if (!binding!!.unhookCheckbox.isChecked()) {
            setSliders(newTempo)
        } else {
            setAndUpdateTempo(newTempo)
        }
    }

    private fun onPitchPercentSliderUpdated(newPitch: Double) {
        if (!binding!!.unhookCheckbox.isChecked()) {
            setSliders(newPitch)
        } else {
            setAndUpdatePitch(newPitch)
        }
    }

    private fun setSliders(newValue: Double) {
        setAndUpdateTempo(newValue)
        setAndUpdatePitch(newValue)
    }

    private fun setAndUpdateTempo(newTempo: Double) {
        tempo = MathUtils.clamp(newTempo, MIN_PITCH_OR_SPEED, MAX_PITCH_OR_SPEED)
        binding!!.tempoSeekbar.setProgress(QUADRATIC_STRATEGY.progressOf(tempo))
        setText(binding!!.tempoCurrentText, DoubleFunction({ obj: Double -> PlayerHelper.formatSpeed() }), tempo)
    }

    private fun setAndUpdatePitch(newPitch: Double) {
        pitchPercent = calcValidPitch(newPitch)
        binding!!.pitchPercentSeekbar.setProgress(QUADRATIC_STRATEGY.progressOf(pitchPercent))
        binding!!.pitchSemitoneSeekbar.setProgress(SEMITONE_STRATEGY.progressOf(pitchPercent))
        setText(binding!!.pitchPercentCurrentText, DoubleFunction({ obj: Double -> PlayerHelper.formatPitch() }),
                pitchPercent)
        setText(binding!!.pitchSemitoneCurrentText, DoubleFunction({ obj: Double -> PlayerSemitoneHelper.formatPitchSemitones() }),
                pitchPercent)
    }

    private fun calcValidPitch(newPitch: Double): Double {
        val calcPitch: Double = MathUtils.clamp(newPitch, MIN_PITCH_OR_SPEED, MAX_PITCH_OR_SPEED)
        if (!isCurrentPitchControlModeSemitone) {
            return calcPitch
        }
        return PlayerSemitoneHelper.semitonesToPercent(
                PlayerSemitoneHelper.percentToSemitones(calcPitch))
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    ////////////////////////////////////////////////////////////////////////// */
    private fun updateCallback() {
        if (callback == null) {
            return
        }
        if (Player.Companion.DEBUG) {
            Log.d(TAG, ("Updating callback: "
                    + "tempo = " + tempo + ", "
                    + "pitchPercent = " + pitchPercent + ", "
                    + "skipSilence = " + skipSilence)
            )
        }
        callback!!.onPlaybackParameterChanged(tempo.toFloat(), pitchPercent.toFloat(), skipSilence)
    }

    open interface Callback {
        fun onPlaybackParameterChanged(playbackTempo: Float, playbackPitch: Float,
                                       playbackSkipSilence: Boolean)
    }

    companion object {
        private val TAG: String = "PlaybackParameterDialog"

        // Minimum allowable range in ExoPlayer
        private val MIN_PITCH_OR_SPEED: Double = 0.10
        private val MAX_PITCH_OR_SPEED: Double = 3.00
        private val PITCH_CTRL_MODE_PERCENT: Boolean = false
        private val PITCH_CTRL_MODE_SEMITONE: Boolean = true
        private val STEP_1_PERCENT_VALUE: Double = 0.01
        private val STEP_5_PERCENT_VALUE: Double = 0.05
        private val STEP_10_PERCENT_VALUE: Double = 0.10
        private val STEP_25_PERCENT_VALUE: Double = 0.25
        private val STEP_100_PERCENT_VALUE: Double = 1.00
        private val DEFAULT_TEMPO: Double = 1.00
        private val DEFAULT_PITCH_PERCENT: Double = 1.00
        private val DEFAULT_STEP: Double = STEP_25_PERCENT_VALUE
        private val DEFAULT_SKIP_SILENCE: Boolean = false
        private val QUADRATIC_STRATEGY: SliderStrategy = Quadratic(
                MIN_PITCH_OR_SPEED,
                MAX_PITCH_OR_SPEED,
                1.00,
                10000)
        private val SEMITONE_STRATEGY: SliderStrategy = object : SliderStrategy {
            public override fun progressOf(value: Double): Int {
                return PlayerSemitoneHelper.percentToSemitones(value) + 12
            }

            public override fun valueOf(progress: Int): Double {
                return PlayerSemitoneHelper.semitonesToPercent(progress - 12)
            }
        }

        fun newInstance(
                playbackTempo: Double,
                playbackPitch: Double,
                playbackSkipSilence: Boolean,
                callback: Callback?
        ): PlaybackParameterDialog {
            val dialog: PlaybackParameterDialog = PlaybackParameterDialog()
            dialog.callback = callback
            dialog.initialTempo = playbackTempo
            dialog.initialPitchPercent = playbackPitch
            dialog.initialSkipSilence = playbackSkipSilence
            dialog.tempo = dialog.initialTempo
            dialog.pitchPercent = dialog.initialPitchPercent
            dialog.skipSilence = dialog.initialSkipSilence
            return dialog
        }

        private fun getStepUpPercentString(percent: Double): String {
            return '+'.toString() + getPercentString(percent)
        }

        private fun getStepDownPercentString(percent: Double): String {
            return '-'.toString() + getPercentString(percent)
        }

        private fun getPercentString(percent: Double): String {
            return PlayerHelper.formatPitch(percent)
        }
    }
}
