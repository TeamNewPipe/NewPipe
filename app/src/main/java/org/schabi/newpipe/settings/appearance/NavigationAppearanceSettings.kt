package org.schabi.newpipe.settings.appearance

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import org.schabi.newpipe.R
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.views.FloatingBottomNavigationView

/** Keeps the spacing controls relevant and provides an explicit return to the default gap. */
object NavigationAppearanceSettings {
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val style = requireNotNull(fragment.findPreference<ListPreference>(context.getString(R.string.bottom_navigation_style_key)))
        val spacing = requireNotNull(fragment.findPreference<SeekBarPreference>(context.getString(R.string.floating_navigation_spacing_key)))
        val reset = requireNotNull(fragment.findPreference<Preference>(context.getString(R.string.floating_navigation_spacing_reset_key)))
        val supported = !DeviceUtils.isTv(context)
        listOf(style, spacing, reset).forEach { it.isVisible = supported }
        fun updateEnabled(value: String?) {
            val enabled = value == "floating"
            spacing.isEnabled = enabled
            reset.isEnabled = enabled
        }
        updateEnabled(style.value)
        style.setOnPreferenceChangeListener { _, value ->
            updateEnabled(value as? String)
            true
        }
        reset.setOnPreferenceClickListener {
            spacing.value = FloatingBottomNavigationView.DEFAULT_SPACING_DP
            true
        }
    }
}
