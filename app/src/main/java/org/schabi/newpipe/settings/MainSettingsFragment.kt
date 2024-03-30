package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ReleaseVersionUtil.isReleaseApk

class MainSettingsFragment() : BasePreferenceFragment() {
    private var settingsActivity: SettingsActivity? = null
    public override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        setHasOptionsMenu(true) // Otherwise onCreateOptionsMenu is not called

        // Check if the app is updatable
        if (!isReleaseApk) {
            getPreferenceScreen().removePreference(
                    (findPreference(getString(R.string.update_pref_screen_key)))!!)
            defaultPreferences!!.edit().putBoolean(getString(R.string.update_app_key), false).apply()
        }

        // Hide debug preferences in RELEASE build variant
        if (!DEBUG) {
            getPreferenceScreen().removePreference(
                    (findPreference(getString(R.string.debug_pref_screen_key)))!!)
        }
    }

    public override fun onCreateOptionsMenu(
            menu: Menu,
            inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        // -- Link settings activity and register menu --
        settingsActivity = getActivity() as SettingsActivity?
        inflater.inflate(R.menu.menu_settings_main_fragment, menu)
        val menuSearchItem: MenuItem = menu.getItem(0)
        settingsActivity!!.setMenuSearchItem(menuSearchItem)
        menuSearchItem.setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener({ ev: MenuItem? ->
            settingsActivity!!.setSearchActive(true)
            true
        }))
    }

    public override fun onDestroy() {
        // Unlink activity so that we don't get memory problems
        if (settingsActivity != null) {
            settingsActivity!!.setMenuSearchItem(null)
            settingsActivity = null
        }
        super.onDestroy()
    }

    companion object {
        val DEBUG: Boolean = MainActivity.Companion.DEBUG
    }
}
