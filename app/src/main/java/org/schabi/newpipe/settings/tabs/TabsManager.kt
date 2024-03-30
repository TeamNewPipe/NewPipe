package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.tabs.TabsJsonHelper.InvalidJsonException

class TabsManager private constructor(private val context: Context) {
    private val sharedPreferences: SharedPreferences
    private val savedTabsKey: String
    private var savedTabsChangeListener: SavedTabsChangeListener? = null
    private var preferenceChangeListener: OnSharedPreferenceChangeListener? = null

    init {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        savedTabsKey = context.getString(R.string.saved_tabs_key)
    }

    fun getTabs(): List<Tab?>? {
        val savedJson: String? = sharedPreferences.getString(savedTabsKey, null)
        try {
            return TabsJsonHelper.getTabsFromJson(savedJson)
        } catch (e: InvalidJsonException) {
            Toast.makeText(context, R.string.saved_tabs_invalid_json, Toast.LENGTH_SHORT).show()
            return getDefaultTabs()
        }
    }

    fun saveTabs(tabList: List<Tab?>?) {
        val jsonToSave: String? = TabsJsonHelper.getJsonToSave(tabList)
        sharedPreferences.edit().putString(savedTabsKey, jsonToSave).apply()
    }

    fun resetTabs() {
        sharedPreferences.edit().remove(savedTabsKey).apply()
    }

    fun getDefaultTabs(): List<Tab?>? {
        return TabsJsonHelper.getDefaultTabs()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Listener
    ////////////////////////////////////////////////////////////////////////// */
    fun setSavedTabsListener(listener: SavedTabsChangeListener?) {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
        savedTabsChangeListener = listener
        preferenceChangeListener = getPreferenceChangeListener()
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun unsetSavedTabsListener() {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
        preferenceChangeListener = null
        savedTabsChangeListener = null
    }

    private fun getPreferenceChangeListener(): OnSharedPreferenceChangeListener {
        return OnSharedPreferenceChangeListener({ sp: SharedPreferences?, key: String? ->
            if ((savedTabsKey == key) && savedTabsChangeListener != null) {
                savedTabsChangeListener!!.onTabsChanged()
            }
        })
    }

    open interface SavedTabsChangeListener {
        fun onTabsChanged()
    }

    companion object {
        fun getManager(context: Context): TabsManager {
            return TabsManager(context)
        }
    }
}
