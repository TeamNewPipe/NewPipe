/* SPDX-License-Identifier: GPL-3.0-or-later
 * Adapted from PipePipeClient 43ef494c1a1792a774c223e1982ff1e794c49017.
 */
package org.schabi.newpipe.settings

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.plugin.PluginLogin
import org.schabi.newpipe.extractor.plugin.PluginSetting
import org.schabi.newpipe.plugin.AndroidPluginPreferences
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.NavigationHelper

/** Manifest settings and optional login cookies, bound to the current profile. */
class PluginServiceSettingsFragment : BasePreferenceFragment() {
    private lateinit var pluginPreferences: AndroidPluginPreferences
    private var restartSnackbar: Snackbar? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        val screen = preferenceManager.createPreferenceScreen(context)
        screen.title = getString(R.string.plugins_title)
        preferenceScreen = screen
        val serviceId = requireArguments().getInt(ARG_SERVICE_ID)
        val manifest = PluginManager.getLoadedManifest(serviceId)
        if (manifest == null) {
            screen.addPreference(
                Preference(context).apply {
                    summary = getString(R.string.plugin_settings_unavailable)
                    isSelectable = false
                }
            )
            return
        }
        pluginPreferences = AndroidPluginPreferences(context, manifest.pluginId, manifest.settings)
        screen.title = getString(R.string.plugin_settings_title, manifest.name)
        screen.addPreference(
            Preference(context).apply {
                summary = getString(R.string.plugin_profile_settings)
                isSelectable = false
                isIconSpaceReserved = false
            }
        )
        for (setting in manifest.settings.filter { it.key != manifest.login?.key }) {
            val preference: Preference = when (setting.type) {
                PluginSetting.Type.BOOLEAN -> SwitchPreferenceCompat(context).apply {
                    isPersistent = false
                    isChecked = pluginPreferences.getBoolean(setting.key)
                    summary = setting.summary
                    setOnPreferenceChangeListener { _, value ->
                        if (!canChange()) return@setOnPreferenceChangeListener false
                        pluginPreferences.putBoolean(setting.key, value as Boolean)
                        showRestartHint()
                        true
                    }
                }

                PluginSetting.Type.STRING -> EditTextPreference(context).apply {
                    isPersistent = false
                    text = pluginPreferences.getString(setting.key)
                    dialogTitle = setting.title
                    setSummaryProvider { pref ->
                        (pref as EditTextPreference).text?.takeIf { it.isNotBlank() } ?: setting.summary
                    }
                    setOnPreferenceChangeListener { _, value ->
                        if (!canChange()) return@setOnPreferenceChangeListener false
                        pluginPreferences.putString(setting.key, value as String)
                        showRestartHint()
                        true
                    }
                }
            }
            preference.key = setting.key
            preference.title = setting.title
            preference.isIconSpaceReserved = false
            preference.isSingleLineTitle = false
            preference.isEnabled = PluginManager.canManage(context)
            screen.addPreference(preference)
        }
        manifest.login?.let(::addLoginPreferences)
    }

    private fun canChange(): Boolean = PluginManager.canManage(requireContext()) &&
        ProfileManager.getActiveProfileId(requireContext()) == pluginPreferences.profileId &&
        ProfileManager.getProfile(requireContext(), pluginPreferences.profileId) != null

    private fun addLoginPreferences(login: PluginLogin) {
        val cookies = EditTextPreference(requireContext()).apply {
            key = login.key
            title = getString(R.string.plugin_login_cookies)
            dialogTitle = title
            dialogMessage = getString(R.string.plugin_login_cookies_summary, login.url, login.cookie)
            isPersistent = false
            isIconSpaceReserved = false
            isEnabled = PluginManager.canManage(requireContext())
            text = pluginPreferences.getString(login.key)
            setSummaryProvider { pref ->
                if ((pref as EditTextPreference).text.isNullOrBlank()) {
                    getString(R.string.plugin_login_cookies_summary, login.url, login.cookie)
                } else {
                    getString(R.string.plugin_credentials_saved)
                }
            }
            setOnBindEditTextListener { edit ->
                edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { _, value ->
                if (!canChange()) return@setOnPreferenceChangeListener false
                val header = (value as String).trim()
                if (header.length > 65536 || header.contains('\n') || header.contains('\r') ||
                    (header.isNotEmpty() && header.split(';').none { it.trim().startsWith("${login.cookie}=") })
                ) {
                    Toast.makeText(context, getString(R.string.plugin_cookie_invalid, login.cookie), Toast.LENGTH_LONG).show()
                    return@setOnPreferenceChangeListener false
                }
                pluginPreferences.putString(login.key, header)
                showRestartHint()
                true
            }
        }
        preferenceScreen.addPreference(cookies)
        preferenceScreen.addPreference(
            Preference(requireContext()).apply {
                title = getString(R.string.plugin_clear_credentials)
                isIconSpaceReserved = false
                isEnabled = PluginManager.canManage(requireContext())
                setOnPreferenceClickListener {
                    if (canChange()) {
                        pluginPreferences.putString(login.key, "")
                        cookies.text = ""
                        showRestartHint()
                    }
                    true
                }
            }
        )
    }

    private fun showRestartHint() {
        val root = view ?: return
        if (restartSnackbar?.isShown == true) return
        restartSnackbar = Snackbar.make(root, R.string.plugin_restart_required, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.plugin_restart) {
                if (pluginPreferences.sharedPreferences.edit().commit()) {
                    NavigationHelper.restartApp(requireActivity())
                }
            }.also { it.show() }
    }

    override fun onDestroyView() {
        restartSnackbar?.dismiss()
        restartSnackbar = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_SERVICE_ID = "service_id"

        @JvmStatic
        fun newInstance(serviceId: Int) = PluginServiceSettingsFragment().apply {
            arguments = Bundle().apply { putInt(ARG_SERVICE_ID, serviceId) }
        }
    }
}
