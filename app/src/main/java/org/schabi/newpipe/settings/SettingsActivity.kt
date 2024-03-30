package org.schabi.newpipe.settings

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.annotation.IdRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.jakewharton.rxbinding4.widget.textChanges
import icepick.Icepick
import icepick.State
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.SettingsLayoutBinding
import org.schabi.newpipe.settings.SettingsResourceRegistry.SettingRegistryEntry
import org.schabi.newpipe.settings.preferencesearch.PreferenceParser
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchConfiguration
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchFragment
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchItem
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchResultHighlighter
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchResultListener
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearcher
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.KeyboardUtil
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ReleaseVersionUtil.isReleaseApk
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.FocusOverlayView
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Predicate

/*
* Created by Christian Schabesberger on 31.08.15.
*
* Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
* SettingsActivity.java is part of NewPipe.
*
* NewPipe is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* NewPipe is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
*/
class SettingsActivity() : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, PreferenceSearchResultListener {
    private var searchFragment: PreferenceSearchFragment? = null
    private var menuSearchItem: MenuItem? = null
    private var searchContainer: View? = null
    private var searchEditText: EditText? = null

    // State
    @State
    var searchText: String? = null

    @State
    var wasSearchActive: Boolean = false
    override fun onCreate(savedInstanceBundle: Bundle?) {
        setTheme(ThemeHelper.getSettingsThemeStyle(this))
        Localization.assureCorrectAppLanguage(this)
        super.onCreate(savedInstanceBundle)
        Icepick.restoreInstanceState(this, savedInstanceBundle)
        val restored: Boolean = savedInstanceBundle != null
        val settingsLayoutBinding: SettingsLayoutBinding = SettingsLayoutBinding.inflate(getLayoutInflater())
        setContentView(settingsLayoutBinding.getRoot())
        initSearch(settingsLayoutBinding, restored)
        setSupportActionBar(settingsLayoutBinding.settingsToolbarLayout.toolbar)
        if (restored) {
            // Restore state
            if (wasSearchActive) {
                setSearchActive(true)
                if (!TextUtils.isEmpty(searchText)) {
                    searchEditText!!.setText(searchText)
                }
            }
        } else {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment_holder, MainSettingsFragment())
                    .commit()
        }
        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.Companion.setupFocusObserver(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    public override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val actionBar: ActionBar? = getSupportActionBar()
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowTitleEnabled(true)
        }
        return super.onCreateOptionsMenu(menu)
    }

    public override fun onBackPressed() {
        if (isSearchActive()) {
            setSearchActive(false)
            return
        }
        super.onBackPressed()
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id: Int = item.getItemId()
        if (id == android.R.id.home) {
            // Check if the search is active and if so: Close it
            if (isSearchActive()) {
                setSearchActive(false)
                return true
            }
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                finish()
            } else {
                getSupportFragmentManager().popBackStack()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    public override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat,
                                                  preference: Preference): Boolean {
        showSettingsFragment(instantiateFragment((preference.getFragment())!!))
        return true
    }

    private fun instantiateFragment(className: String): Fragment {
        return getSupportFragmentManager()
                .getFragmentFactory()
                .instantiate(getClassLoader(), className)
    }

    private fun showSettingsFragment(fragment: Fragment?) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out,
                        R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(FRAGMENT_HOLDER_ID, (fragment)!!)
                .addToBackStack(null)
                .commit()
    }

    override fun onDestroy() {
        setMenuSearchItem(null)
        searchFragment = null
        super.onDestroy()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    ////////////////////////////////////////////////////////////////////////// */
    //region Search
    private fun initSearch(
            settingsLayoutBinding: SettingsLayoutBinding,
            restored: Boolean
    ) {
        searchContainer = settingsLayoutBinding.settingsToolbarLayout.toolbar
                .findViewById(R.id.toolbar_search_container)

        // Configure input field for search
        searchEditText = searchContainer.findViewById(R.id.toolbar_search_edit_text)
        searchEditText.textChanges() // Wait some time after the last input before actually searching
                .debounce(200, TimeUnit.MILLISECONDS)
                .subscribe(io.reactivex.rxjava3.functions.Consumer({ v: CharSequence? -> runOnUiThread(Runnable({ onSearchChanged() })) }))

        // Configure clear button
        searchContainer.findViewById<View>(R.id.toolbar_search_clear)
                .setOnClickListener(View.OnClickListener({ ev: View? -> resetSearchText() }))
        ensureSearchRepresentsApplicationState()

        // Build search configuration using SettingsResourceRegistry
        val config: PreferenceSearchConfiguration = PreferenceSearchConfiguration()


        // Build search items
        val searchContext: Context = getApplicationContext()
        Localization.assureCorrectAppLanguage(searchContext)
        val parser: PreferenceParser = PreferenceParser(searchContext, config)
        val searcher: PreferenceSearcher = PreferenceSearcher(config)

        // Find all searchable SettingsResourceRegistry fragments
        SettingsResourceRegistry.Companion.getInstance().getAllEntries().stream()
                .filter(Predicate<SettingRegistryEntry?>({ isSearchable() })) // Get the resId
                .map<Int>(Function<SettingRegistryEntry?, Int>({ getPreferencesResId() })) // Parse
                .map<List<PreferenceSearchItem?>?>(Function<Int, List<PreferenceSearchItem?>?>({ resId: Int -> parser.parse(resId) })) // Add it to the searcher
                .forEach(java.util.function.Consumer<List<PreferenceSearchItem?>?>({ items: List<PreferenceSearchItem?>? -> searcher.add(items) }))
        if (restored) {
            searchFragment = getSupportFragmentManager()
                    .findFragmentByTag(PreferenceSearchFragment.Companion.NAME) as PreferenceSearchFragment?
            if (searchFragment != null) {
                // Hide/Remove the search fragment otherwise we get an exception
                // when adding it (because it's already present)
                hideSearchFragment()
            }
        }
        if (searchFragment == null) {
            searchFragment = PreferenceSearchFragment()
        }
        searchFragment!!.setSearcher(searcher)
    }

    /**
     * Ensures that the search shows the correct/available search results.
     * <br></br>
     * Some features are e.g. only available for debug builds, these should not
     * be found when searching inside a release.
     */
    private fun ensureSearchRepresentsApplicationState() {
        // Check if the update settings are available
        if (!isReleaseApk) {
            SettingsResourceRegistry.Companion.getInstance()
                    .getEntryByPreferencesResId(R.xml.update_settings)
                    .setSearchable(false)
        }

        // Hide debug preferences in RELEASE build variant
        if (DEBUG) {
            SettingsResourceRegistry.Companion.getInstance()
                    .getEntryByPreferencesResId(R.xml.debug_settings)
                    .setSearchable(true)
        }
    }

    fun setMenuSearchItem(menuSearchItem: MenuItem?) {
        this.menuSearchItem = menuSearchItem

        // Ensure that the item is in the correct state when adding it. This is due to
        // Android's lifecycle (the Activity is recreated before the Fragment that registers this)
        if (menuSearchItem != null) {
            menuSearchItem.setVisible(!isSearchActive())
        }
    }

    fun setSearchActive(active: Boolean) {
        if (DEBUG) {
            Log.d(TAG, "setSearchActive called active=" + active)
        }

        // Ignore if search is already in correct state
        if (isSearchActive() == active) {
            return
        }
        wasSearchActive = active
        searchContainer!!.setVisibility(if (active) View.VISIBLE else View.GONE)
        if (menuSearchItem != null) {
            menuSearchItem!!.setVisible(!active)
        }
        if (active) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(FRAGMENT_HOLDER_ID, (searchFragment)!!, PreferenceSearchFragment.Companion.NAME)
                    .addToBackStack(PreferenceSearchFragment.Companion.NAME)
                    .commit()
            KeyboardUtil.showKeyboard(this, searchEditText)
        } else if (searchFragment != null) {
            hideSearchFragment()
            getSupportFragmentManager()
                    .popBackStack(
                            PreferenceSearchFragment.Companion.NAME,
                            FragmentManager.POP_BACK_STACK_INCLUSIVE)
            KeyboardUtil.hideKeyboard(this, searchEditText)
        }
        resetSearchText()
    }

    private fun hideSearchFragment() {
        getSupportFragmentManager().beginTransaction().remove((searchFragment)!!).commit()
    }

    private fun resetSearchText() {
        searchEditText!!.setText("")
    }

    private fun isSearchActive(): Boolean {
        return searchContainer!!.getVisibility() == View.VISIBLE
    }

    private fun onSearchChanged() {
        if (!isSearchActive()) {
            return
        }
        if (searchFragment != null) {
            searchText = searchEditText!!.getText().toString()
            searchFragment!!.updateSearchResults(searchText!!)
        }
    }

    public override fun onSearchResultClicked(result: PreferenceSearchItem) {
        if (DEBUG) {
            Log.d(TAG, "onSearchResultClicked called result=" + result)
        }

        // Hide the search
        setSearchActive(false)

        // -- Highlight the result --
        // Find out which fragment class we need
        val targetedFragmentClass: Class<out Fragment>? = SettingsResourceRegistry.Companion.getInstance()
                .getFragmentClass(result.getSearchIndexItemResId())
        if (targetedFragmentClass == null) {
            // This should never happen
            Log.w(TAG, ("Unable to locate fragment class for resId="
                    + result.getSearchIndexItemResId()))
            return
        }

        // Check if the currentFragment is the one which contains the result
        var currentFragment: Fragment? = getSupportFragmentManager().findFragmentById(FRAGMENT_HOLDER_ID)
        if (!(targetedFragmentClass == currentFragment!!.javaClass)) {
            // If it's not the correct one display the correct one
            currentFragment = instantiateFragment(targetedFragmentClass.getName())
            showSettingsFragment(currentFragment)
        }

        // Run the highlighting
        if (currentFragment is PreferenceFragmentCompat) {
            PreferenceSearchResultHighlighter.highlight(result, currentFragment)
        }
    } //endregion

    companion object {
        private val TAG: String = "SettingsActivity"
        private val DEBUG: Boolean = MainActivity.Companion.DEBUG

        @IdRes
        private val FRAGMENT_HOLDER_ID: Int = R.id.settings_fragment_holder
    }
}
