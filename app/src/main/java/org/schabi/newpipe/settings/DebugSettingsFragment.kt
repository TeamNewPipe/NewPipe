package org.schabi.newpipe.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.createNotification
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationWorker.Companion.runNow
import org.schabi.newpipe.util.image.PicassoHelper
import java.util.Optional

class DebugSettingsFragment() : BasePreferenceFragment() {
    public override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        val allowHeapDumpingPreference: Preference? = findPreference(getString(R.string.allow_heap_dumping_key))
        val showMemoryLeaksPreference: Preference? = findPreference(getString(R.string.show_memory_leaks_key))
        val showImageIndicatorsPreference: Preference? = findPreference(getString(R.string.show_image_indicators_key))
        val checkNewStreamsPreference: Preference? = findPreference(getString(R.string.check_new_streams_key))
        val crashTheAppPreference: Preference? = findPreference(getString(R.string.crash_the_app_key))
        val showErrorSnackbarPreference: Preference? = findPreference(getString(R.string.show_error_snackbar_key))
        val createErrorNotificationPreference: Preference? = findPreference(getString(R.string.create_error_notification_key))
        assert(allowHeapDumpingPreference != null)
        assert(showMemoryLeaksPreference != null)
        assert(showImageIndicatorsPreference != null)
        assert(checkNewStreamsPreference != null)
        assert(crashTheAppPreference != null)
        assert(showErrorSnackbarPreference != null)
        assert(createErrorNotificationPreference != null)
        val optBVLeakCanary: Optional<DebugSettingsBVDLeakCanaryAPI> = getBVDLeakCanary()
        allowHeapDumpingPreference!!.setEnabled(optBVLeakCanary.isPresent())
        showMemoryLeaksPreference!!.setEnabled(optBVLeakCanary.isPresent())
        if (optBVLeakCanary.isPresent()) {
            val pdLeakCanary: DebugSettingsBVDLeakCanaryAPI = optBVLeakCanary.get()
            showMemoryLeaksPreference.setOnPreferenceClickListener(Preference.OnPreferenceClickListener({ preference: Preference? ->
                startActivity((pdLeakCanary.getNewLeakDisplayActivityIntent())!!)
                true
            }))
        } else {
            allowHeapDumpingPreference.setSummary(R.string.leak_canary_not_available)
            showMemoryLeaksPreference.setSummary(R.string.leak_canary_not_available)
        }
        showImageIndicatorsPreference!!.setOnPreferenceChangeListener(Preference.OnPreferenceChangeListener({ preference: Preference?, newValue: Any ->
            PicassoHelper.setIndicatorsEnabled(newValue as Boolean)
            true
        }))
        checkNewStreamsPreference!!.setOnPreferenceClickListener(Preference.OnPreferenceClickListener({ preference: Preference ->
            runNow(preference.getContext())
            true
        }))
        crashTheAppPreference!!.setOnPreferenceClickListener(Preference.OnPreferenceClickListener({ preference: Preference? -> throw RuntimeException(DUMMY) }))
        showErrorSnackbarPreference!!.setOnPreferenceClickListener(Preference.OnPreferenceClickListener({ preference: Preference? ->
            showUiErrorSnackbar(this@DebugSettingsFragment,
                    DUMMY, RuntimeException(DUMMY))
            true
        }))
        createErrorNotificationPreference!!.setOnPreferenceClickListener(Preference.OnPreferenceClickListener({ preference: Preference? ->
            createNotification(requireContext(),
                    ErrorInfo(RuntimeException(DUMMY), UserAction.UI_ERROR, DUMMY))
            true
        }))
    }

    /**
     * Tries to find the [DebugSettingsBVDLeakCanaryAPI.IMPL_CLASS] and loads it if available.
     * @return An [Optional] which is empty if the implementation class couldn't be loaded.
     */
    private fun getBVDLeakCanary(): Optional<DebugSettingsBVDLeakCanaryAPI> {
        try {
            // Try to find the implementation of the LeakCanary API
            return Optional.of(Class.forName(DebugSettingsBVDLeakCanaryAPI.IMPL_CLASS)
                    .getDeclaredConstructor()
                    .newInstance() as DebugSettingsBVDLeakCanaryAPI)
        } catch (e: Exception) {
            return Optional.empty()
        }
    }

    /**
     * Build variant dependent (BVD) leak canary API for this fragment.
     * Why is LeakCanary not used directly? Because it can't be assured
     */
    open interface DebugSettingsBVDLeakCanaryAPI {
        fun getNewLeakDisplayActivityIntent(): Intent?

        companion object {
            val IMPL_CLASS: String = "org.schabi.newpipe.settings.DebugSettingsBVDLeakCanary"
        }
    }

    companion object {
        private val DUMMY: String = "Dummy"
    }
}
