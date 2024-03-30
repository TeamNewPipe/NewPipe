package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.util.ThemeHelper
import java.util.Objects

abstract class BasePreferenceFragment() : PreferenceFragmentCompat() {
    protected val TAG: String = javaClass.getSimpleName() + "@" + Integer.toHexString(hashCode())
    var defaultPreferences: SharedPreferences? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        super.onCreate(savedInstanceState)
    }

    protected fun addPreferencesFromResourceRegistry() {
        addPreferencesFromResource(
                SettingsResourceRegistry.Companion.getInstance().getPreferencesResId(this.javaClass))
    }

    public override fun onViewCreated(rootView: View,
                                      savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        setDivider(null)
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle())
    }

    public override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle())
    }

    fun requirePreference(@StringRes resId: Int): Preference {
        val preference: Preference? = findPreference(getString(resId))
        Objects.requireNonNull(preference)
        return (preference)!!
    }

    companion object {
        protected val DEBUG: Boolean = MainActivity.Companion.DEBUG
    }
}
