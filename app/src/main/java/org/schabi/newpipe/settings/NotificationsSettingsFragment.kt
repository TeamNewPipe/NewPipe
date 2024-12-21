package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.local.feed.notifications.ScheduleOptions
import org.schabi.newpipe.local.subscription.SubscriptionManager

class NotificationsSettingsFragment : BasePreferenceFragment(), OnSharedPreferenceChangeListener {

    private var streamsNotificationsPreference: SwitchPreference? = null
    private var notificationWarningSnackbar: Snackbar? = null
    private var loader: Disposable? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.notifications_settings)
        streamsNotificationsPreference =
            findPreference(getString(R.string.enable_streams_notifications))

        // main check is done in onResume, but also do it here to prevent flickering
        updateEnabledState(NotificationHelper.areNotificationsEnabledOnDevice(requireContext()))
    }

    override fun onStart() {
        super.onStart()
        defaultPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        defaultPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val context = context ?: return
        if (key == getString(R.string.streams_notifications_interval_key) ||
            key == getString(R.string.streams_notifications_network_key)
        ) {
            // apply new configuration
            NotificationWorker.schedule(context, ScheduleOptions.from(context), true)
        } else if (key == getString(R.string.enable_streams_notifications)) {
            if (NotificationHelper.areNewStreamsNotificationsEnabled(context)) {
                // Start the worker, because notifications were disabled previously.
                NotificationWorker.schedule(context)
            } else {
                // The user disabled the notifications. Cancel the worker to save energy.
                // A new one will be created once the notifications are enabled again.
                NotificationWorker.cancel(context)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Check whether the notifications are disabled in the device's app settings.
        // If they are disabled, show a snackbar informing the user about that
        // while allowing them to open the device's app settings.
        val enabled = NotificationHelper.areNotificationsEnabledOnDevice(requireContext())
        updateEnabledState(enabled)
        if (!enabled) {
            if (notificationWarningSnackbar == null) {
                notificationWarningSnackbar = Snackbar.make(
                    listView,
                    R.string.notifications_disabled,
                    Snackbar.LENGTH_INDEFINITE
                ).apply {
                    setAction(R.string.settings) {
                        NotificationHelper.openNewPipeSystemNotificationSettings(it.context)
                    }
                    setActionTextColor(Color.YELLOW)
                    addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            notificationWarningSnackbar = null
                        }
                    })
                    show()
                }
            }
        }

        // (Re-)Create loader
        loader?.dispose()
        loader = SubscriptionManager(requireContext())
            .subscriptions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::updateSubscriptions, this::onError)
    }

    override fun onPause() {
        loader?.dispose()
        loader = null

        notificationWarningSnackbar?.dismiss()
        notificationWarningSnackbar = null

        super.onPause()
    }

    private fun updateEnabledState(enabled: Boolean) {
        // On Android 13 player notifications are exempt from notification settings
        // so the preferences in app should always be available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            streamsNotificationsPreference?.isEnabled = enabled
        } else {
            preferenceScreen.isEnabled = enabled
        }
    }

    private fun updateSubscriptions(subscriptions: List<SubscriptionEntity>) {
        val notified = subscriptions.count { it.notificationMode != NotificationMode.DISABLED }
        val preference = findPreference<Preference>(getString(R.string.streams_notifications_channels_key))
        preference?.apply { summary = "$notified/${subscriptions.size}" }
    }

    private fun onError(e: Throwable) {
        ErrorUtil.showSnackbar(
            this,
            ErrorInfo(e, UserAction.SUBSCRIPTION_GET, "Get subscriptions list")
        )
    }
}
