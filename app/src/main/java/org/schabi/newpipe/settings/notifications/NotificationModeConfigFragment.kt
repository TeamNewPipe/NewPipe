package org.schabi.newpipe.settings.notifications

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.databinding.FragmentChannelsNotificationsBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.subscription.SubscriptionManager

/**
 * [NotificationModeConfigFragment] is a settings fragment
 * which allows changing the [NotificationMode] of all subscribed channels.
 * The [NotificationMode] can either be changed one by one or toggled for all channels.
 */
class NotificationModeConfigFragment : Fragment() {
    private var _binding: FragmentChannelsNotificationsBinding? = null
    private val binding get() = _binding!!

    private val disposables = CompositeDisposable()
    private var loader: Disposable? = null
    private var bulkUpdating = false
    private lateinit var adapter: NotificationModeConfigAdapter
    private lateinit var subscriptionManager: SubscriptionManager

    override fun onAttach(context: Context) {
        super.onAttach(context)
        subscriptionManager = SubscriptionManager(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChannelsNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = NotificationModeConfigAdapter(::showNotificationConfigDialog)
        binding.recyclerView.adapter = adapter
        loader?.dispose()
        loader = subscriptionManager.subscriptions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(adapter::update)
    }

    override fun onDestroyView() {
        loader?.dispose()
        loader = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_notifications_channels, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_all -> {
                toggleAll()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleAll() {
        if (bulkUpdating) return
        val newMode = NotificationModeBulkPolicy.nextMode(
            adapter.currentList.map { it.notificationMode }
        ) ?: return
        bulkUpdating = true
        disposables.add(
            subscriptionManager.updateAllNotificationModes(newMode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { bulkUpdating = false }
                .subscribe({}, { error ->
                    Log.e("NotificationModeConfig", "Unable to update channel notifications", error)
                    if (isAdded) {
                        ErrorUtil.showSnackbar(
                            this,
                            ErrorInfo(error, UserAction.SOMETHING_ELSE, "Bulk notification settings")
                        )
                    }
                })
        )
    }

    private fun updateNotificationMode(item: SubscriptionItem, @NotificationMode mode: Int) {
        disposables.add(
            subscriptionManager.updateNotificationMode(item.serviceId, item.url, mode)
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }

    private fun showNotificationConfigDialog(item: SubscriptionItem) {
        NotificationConfigDialog.show(
            this,
            item.title,
            item.notificationMode,
            item.notificationKeywords
        ) { mode, keywords ->
            updateNotificationSettings(item, mode, keywords)
        }
    }
    private fun updateNotificationSettings(
        item: SubscriptionItem,
        @NotificationMode mode: Int,
        keywords: String
    ) {
        disposables.add(
            subscriptionManager.updateNotificationSettings(
                item.serviceId,
                item.url,
                mode,
                keywords
            )
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }
}
