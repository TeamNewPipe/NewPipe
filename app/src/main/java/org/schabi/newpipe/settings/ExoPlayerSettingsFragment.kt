package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import org.schabi.newpipe.R

class ExoPlayerSettingsFragment() : BasePreferenceFragment() {
    public override fun onCreatePreferences(savedInstanceState: Bundle?,
                                            rootKey: String?) {
        addPreferencesFromResourceRegistry()
        val disabledMediaTunnelingAutomaticallyKey: String = getString(R.string.disabled_media_tunneling_automatically_key)
        val disableMediaTunnelingPref: SwitchPreferenceCompat = requirePreference(R.string.disable_media_tunneling_key) as SwitchPreferenceCompat
        val prefs: SharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
        val mediaTunnelingAutomaticallyDisabled: Boolean = prefs.getInt(disabledMediaTunnelingAutomaticallyKey, -1) == 1
        val summaryText: String = getString(R.string.disable_media_tunneling_summary)
        disableMediaTunnelingPref.setSummary(if (mediaTunnelingAutomaticallyDisabled) summaryText + " " + getString(R.string.disable_media_tunneling_automatic_info) else summaryText)
        disableMediaTunnelingPref.setOnPreferenceChangeListener(Preference.OnPreferenceChangeListener({ p: Preference, enabled: Any ->
            if ((java.lang.Boolean.FALSE == enabled)) {
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putInt(disabledMediaTunnelingAutomaticallyKey, 0)
                        .apply()
                // the info text might have been shown before
                p.setSummary(R.string.disable_media_tunneling_summary)
            }
            true
        }))
    }
}
