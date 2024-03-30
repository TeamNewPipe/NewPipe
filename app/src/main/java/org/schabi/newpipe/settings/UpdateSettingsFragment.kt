package org.schabi.newpipe.settings

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import org.schabi.newpipe.NewVersionWorker.Companion.enqueueNewVersionCheckingWork
import org.schabi.newpipe.R

class UpdateSettingsFragment() : BasePreferenceFragment() {
    private val updatePreferenceChange: Preference.OnPreferenceChangeListener = Preference.OnPreferenceChangeListener({ p: Preference?, nVal: Any ->
        val checkForUpdates: Boolean = nVal as Boolean
        defaultPreferences!!.edit()
                .putBoolean(getString(R.string.update_app_key), checkForUpdates)
                .apply()
        if (checkForUpdates) {
            enqueueNewVersionCheckingWork(requireContext(), true)
        }
        true
    })
    private val manualUpdateClick: Preference.OnPreferenceClickListener = Preference.OnPreferenceClickListener({ preference: Preference? ->
        Toast.makeText(getContext(), R.string.checking_updates_toast, Toast.LENGTH_SHORT).show()
        enqueueNewVersionCheckingWork(requireContext(), true)
        true
    })

    public override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        findPreference<Preference>(getString(R.string.update_app_key))
                .setOnPreferenceChangeListener(updatePreferenceChange)
        findPreference<Preference>(getString(R.string.manual_update_key))
                .setOnPreferenceClickListener(manualUpdateClick)
    }

    companion object {
        fun askForConsentToUpdateChecks(context: Context) {
            AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.check_for_updates))
                    .setMessage(context.getString(R.string.auto_update_check_description))
                    .setPositiveButton(context.getString(R.string.yes), DialogInterface.OnClickListener({ d: DialogInterface, w: Int ->
                        d.dismiss()
                        setAutoUpdateCheckEnabled(context, true)
                    }))
                    .setNegativeButton(R.string.no, DialogInterface.OnClickListener({ d: DialogInterface, w: Int ->
                        d.dismiss()
                        // set explicitly to false, since the default is true on previous versions
                        setAutoUpdateCheckEnabled(context, false)
                    }))
                    .show()
        }

        private fun setAutoUpdateCheckEnabled(context: Context, enabled: Boolean) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putBoolean(context.getString(R.string.update_app_key), enabled)
                    .putBoolean(context.getString(R.string.update_check_consent_key), true)
                    .apply()
        }

        /**
         * Whether the user was asked for consent to automatically check for app updates.
         * @param context
         * @return true if the user was asked for consent, false otherwise
         */
        fun wasUserAskedForConsent(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(context.getString(R.string.update_check_consent_key), false)
        }
    }
}
