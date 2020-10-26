package org.schabi.newpipe.settings

import android.os.Bundle
import org.schabi.newpipe.R

class NotificationSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.notification_settings)
    }
}