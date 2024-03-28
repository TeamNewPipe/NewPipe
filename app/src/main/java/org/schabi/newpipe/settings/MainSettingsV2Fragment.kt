package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.core.view.MenuProvider
import androidx.preference.Preference
import org.schabi.newpipe.R

/**
 *  Provides main settings page, entry point to the NewPipe app settings.
 */
class MainSettingsV2Fragment : BasePreferenceFragment(), MenuProvider {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        (activity as? ComponentActivity)?.addMenuProvider(this, this)

        // Hide debug preferences in RELEASE build variant
        if (!DEBUG) {
            findPreference<Preference>(getString(R.string.debug_pref_screen_key))?.let(
                preferenceScreen::removePreference,
            )
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        // -- Link settings activity and register menu --
        menuInflater.inflate(R.menu.menu_settings_main_fragment, menu)
        val menuSearchItem = menu.getItem(0)
        (activity as? SettingsActivity)?.setMenuSearchItem(menuSearchItem)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.action_search) {
            (activity as? SettingsActivity)?.setSearchActive(true)
            return true
        }
        return false
    }

    override fun onDestroy() {
        // Unlink activity so that we don't get memory problems
        (activity as? SettingsActivity)?.setMenuSearchItem(null)
        super.onDestroy()
    }
}
