package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import androidx.preference.Preference
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ReleaseVersionUtil

/**
 *  Provides main settings page, entry point to the NewPipe app settings.
 */
class MainSettingsV2Fragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        setHasOptionsMenu(true) // Otherwise onCreateOptionsMenu is not called

        // Check if the app is updatable
        if (!ReleaseVersionUtil.isReleaseApk()) {
            findPreference<Preference>(getString(R.string.update_pref_screen_key))?.let(
                preferenceScreen::removePreference,
            )
            defaultPreferences.edit().putBoolean(getString(R.string.update_app_key), false).apply()
        }

        // Hide debug preferences in RELEASE build variant
        if (!DEBUG) {
            findPreference<Preference>(getString(R.string.debug_pref_screen_key))?.let(
                preferenceScreen::removePreference,
            )
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        // -- Link settings activity and register menu --
        inflater.inflate(R.menu.menu_settings_main_fragment, menu)
        val menuSearchItem = menu.getItem(0)
        activity?.let { it as? SettingsActivity }?.apply {
            setMenuSearchItem(menuSearchItem)
            menuSearchItem.setOnMenuItemClickListener {
                setSearchActive(true)
                true
            }
        }
    }

    override fun onDestroy() {
        // Unlink activity so that we don't get memory problems
        activity?.let { it as? SettingsActivity }?.setMenuSearchItem(null)
        super.onDestroy()
    }

    companion object {
        private val DEBUG = MainActivity.DEBUG
    }
}
