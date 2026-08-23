package org.schabi.newpipe.settings.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.settings.notifications.NotificationModeConfigAdapter.ModeToggleListener

/**
 * [NotificationModeConfigFragment] is a settings fragment
 * which allows changing the [NotificationMode] of all subscribed channels.
 * The [NotificationMode] can either be changed one by one or toggled for all channels.
 */
class NotificationModeConfigFragment : Fragment(), ModeToggleListener {

    private lateinit var updaters: CompositeDisposable
    private var loader: Disposable? = null
    private var adapter: NotificationModeConfigAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updaters = CompositeDisposable()
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_channels_notifications, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        adapter = NotificationModeConfigAdapter(this)
        recyclerView.adapter = adapter
        loader?.dispose()
        loader = SubscriptionManager(requireContext())
            .subscriptions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { newData -> adapter?.update(newData) }
    }

    override fun onDestroyView() {
        loader?.dispose()
        loader = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        updaters.dispose()
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

    override fun onModeChange(position: Int, @NotificationMode mode: Int) {
        // Notification mode has been changed via the UI.
        // Now change it in the database.
        val subscription = adapter?.getItem(position) ?: return
        updaters.add(
            SubscriptionManager(requireContext())
                .updateNotificationMode(
                    subscription.serviceId,
                    subscription.url,
                    mode
                )
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }

    private fun toggleAll() {
        val subscriptions = adapter?.getCurrentList() ?: return
        val mode = subscriptions.firstOrNull()?.notificationMode ?: return
        val newMode = when (mode) {
            NotificationMode.DISABLED -> NotificationMode.ENABLED
            else -> NotificationMode.DISABLED
        }
        val subscriptionManager = SubscriptionManager(requireContext())
        updaters.add(
            CompositeDisposable(
                subscriptions.map { item ->
                    subscriptionManager.updateNotificationMode(
                        serviceId = item.serviceId,
                        url = item.url,
                        mode = newMode
                    ).subscribeOn(Schedulers.io())
                        .subscribe()
                }
            )
        )
    }
}
