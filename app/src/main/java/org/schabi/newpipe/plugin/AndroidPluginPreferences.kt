/* SPDX-License-Identifier: GPL-3.0-or-later
 * Adapted from PipePipeClient 43ef494c1a1792a774c223e1982ff1e794c49017.
 */
package org.schabi.newpipe.plugin

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.schabi.newpipe.extractor.plugin.PluginPreferences
import org.schabi.newpipe.extractor.plugin.PluginSetting
import org.schabi.newpipe.profiles.ProfileManager

/** Preferences and credentials are bound to the profile that created this plugin service. */
class AndroidPluginPreferences @JvmOverloads constructor(
    context: Context,
    pluginId: String,
    settings: List<PluginSetting> = emptyList(),
    val profileId: String = ProfileManager.getActiveProfileId(context)
) : PluginPreferences {
    private val appContext = context.applicationContext
    private val defaults = settings.associate { it.key to it.defaultValue }
    val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        fileName(pluginId, profileId),
        Context.MODE_PRIVATE
    )
    private val listeners = CopyOnWriteArrayList<PluginPreferences.ChangeListener>()
    private val bridge = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) listeners.forEach { listener -> runCatching { listener.onChanged(key) } }
    }

    init {
        knownFiles.getOrPut(profileId) { Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>()) }
            .add(fileName(pluginId, profileId))
        sharedPreferences.registerOnSharedPreferenceChangeListener(bridge)
    }

    private fun profileExists() = ProfileManager.getProfile(appContext, profileId) != null

    override fun getString(key: String): String? = if (profileExists()) {
        runCatching { sharedPreferences.getString(key, defaults[key]) }.getOrDefault(defaults[key])
    } else {
        defaults[key]
    }

    override fun getBoolean(key: String): Boolean = if (profileExists()) {
        runCatching { sharedPreferences.getBoolean(key, defaults[key]?.toBoolean() ?: false) }
            .getOrDefault(defaults[key]?.toBoolean() ?: false)
    } else {
        defaults[key]?.toBoolean() ?: false
    }

    override fun putString(key: String, value: String?) {
        synchronized(profileWriteLock) {
            if (profileExists()) sharedPreferences.edit().putString(key, value).apply()
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        synchronized(profileWriteLock) {
            if (profileExists()) sharedPreferences.edit().putBoolean(key, value).apply()
        }
    }

    override fun addChangeListener(listener: PluginPreferences.ChangeListener) {
        listeners.addIfAbsent(listener)
    }

    companion object {
        private val profileWriteLock = Any()
        private val knownFiles = ConcurrentHashMap<String, MutableSet<String>>()

        @JvmStatic
        fun fileName(pluginId: String, profileId: String) = "plugin_profile_${profileId}_$pluginId"

        /** Called after profile deletion; stale plugin objects cannot recreate these values. */
        @JvmStatic
        fun forgetProfile(context: Context, profileId: String) = synchronized(profileWriteLock) {
            val prefix = "plugin_profile_${profileId}_"
            val persisted = File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
                ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".xml") }
                ?.map { it.name.removeSuffix(".xml") }.orEmpty()
            // apply() may not have created an XML file yet; include live preference objects.
            (persisted + knownFiles.remove(profileId).orEmpty()).distinct().forEach { name ->
                check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
    }
}
