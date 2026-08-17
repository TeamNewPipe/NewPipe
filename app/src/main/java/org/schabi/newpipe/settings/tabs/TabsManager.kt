package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

class TabsManager private constructor(private val context: Context) {
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val savedTabsKey: String = context.getString(R.string.saved_tabs_key)
    private var savedTabsChangeListener: SavedTabsChangeListener? = null
    private var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    val tabs: List<Tab>
        get() {
            val savedJson = sharedPreferences.getString(savedTabsKey, null)
            return try {
                TabsJsonHelper.getTabsFromJson(savedJson)
            } catch (e: TabsJsonHelper.InvalidJsonException) {
                Toast.makeText(context, R.string.saved_tabs_invalid_json, Toast.LENGTH_SHORT).show()
                defaultTabs
            }
        }

    fun saveTabs(tabList: List<Tab>) {
        val jsonToSave = TabsJsonHelper.getJsonToSave(tabList)
        sharedPreferences.edit().putString(savedTabsKey, jsonToSave).apply()
    }

    fun resetTabs() {
        sharedPreferences.edit().remove(savedTabsKey).apply()
    }

    val defaultTabs: List<Tab>
        get() = TabsJsonHelper.defaultTabs

    fun setSavedTabsListener(listener: SavedTabsChangeListener) {
        preferenceChangeListener?.let {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(it)
        }
        savedTabsChangeListener = listener
        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (savedTabsKey == key) {
                savedTabsChangeListener?.onTabsChanged()
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun unsetSavedTabsListener() {
        preferenceChangeListener?.let {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(it)
        }
        preferenceChangeListener = null
        savedTabsChangeListener = null
    }

    interface SavedTabsChangeListener {
        fun onTabsChanged()
    }

    companion object {
        @JvmStatic
        fun getManager(context: Context): TabsManager {
            return TabsManager(context)
        }
    }
}
