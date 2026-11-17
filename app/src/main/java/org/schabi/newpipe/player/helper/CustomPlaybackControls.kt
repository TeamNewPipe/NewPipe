package org.schabi.newpipe.player.helper

import android.content.Context
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

object CustomPlaybackControls {
    @JvmStatic
    fun seekMillis(context: Context, forward: Boolean): Int {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val customKey = if (forward) R.string.playback_forward_custom_key else R.string.playback_rewind_custom_key
        val customSeconds = preferences.getString(context.getString(customKey), null).toSeekSeconds()
        val defaultMillis = context.getString(R.string.seek_duration_default_value).toInt()
        val presetMillis = preferences.getString(context.getString(R.string.seek_duration_key), null)
            ?.toIntOrNull()?.takeIf { it in 1_000..300_000 } ?: defaultMillis
        val seconds = customSeconds ?: (presetMillis / 1_000)
        val inexact = preferences.getBoolean(context.getString(R.string.use_inexact_seek_key), false)
        return (if (inexact && seconds % 10 == 5) seconds + 5 else seconds) * 1_000
    }

    @JvmStatic
    fun holdSpeed(context: Context): Float {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val value = preferences.getString(context.getString(R.string.playback_hold_custom_key), null)
        return value.toHoldSpeed() ?: context.getString(R.string.hold_to_speed_default_value).toFloat()
    }

    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        for (key in listOf(R.string.playback_rewind_custom_key, R.string.playback_forward_custom_key)) {
            val preference = requireNotNull(fragment.findPreference<EditTextPreference>(fragment.getString(key)))
            preference.setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER }
            preference.setOnPreferenceChangeListener { row, value ->
                val entered = value as? String
                val valid = entered.isNullOrBlank() || entered.toSeekSeconds() != null
                if (!valid) {
                    Toast.makeText(row.context, R.string.playback_custom_seek_invalid, Toast.LENGTH_SHORT).show()
                }
                valid
            }
            preference.summaryProvider = Preference.SummaryProvider<EditTextPreference> { row ->
                row.text.toSeekSeconds()?.let { seconds ->
                    row.context.resources.getQuantityString(R.plurals.seconds, seconds, seconds)
                } ?: row.context.getString(R.string.playback_use_seek_preset)
            }
        }

        val customSpeed = requireNotNull(
            fragment.findPreference<EditTextPreference>(fragment.getString(R.string.playback_hold_custom_key))
        )
        customSpeed.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        customSpeed.setOnPreferenceChangeListener { row, value ->
            val valid = (value as? String).toHoldSpeed() != null
            if (!valid) {
                Toast.makeText(row.context, R.string.playback_hold_custom_invalid, Toast.LENGTH_SHORT).show()
            }
            valid
        }
        customSpeed.summaryProvider = Preference.SummaryProvider<EditTextPreference> { row ->
            PlayerHelper.formatSpeed(holdSpeed(row.context).toDouble())
        }
    }

    private fun String?.toSeekSeconds(): Int? {
        return this?.trim()?.toIntOrNull()?.takeIf { it in 1..300 }
    }

    private fun String?.toHoldSpeed(): Float? {
        val decimalSeparator = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
        val normalized = this?.trim()?.map { character ->
            when {
                character == decimalSeparator || character == ',' -> '.'
                character.isDigit() -> character.digitToInt().digitToChar()
                else -> character
            }
        }?.joinToString("")
        return normalized?.toFloatOrNull()?.takeIf { it in 0.1f..10f }
    }
}
