package org.schabi.newpipe.local.subscription

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager

object StreamSubscriptionActions {
    /** Query only local profile data; never fetch channel metadata to label a context menu. */
    @JvmStatic
    fun isSubscribed(context: Context, serviceId: Int, channelUrl: String): Single<Boolean> {
        val profileId = ProfileManager.getActiveProfileId(context)
        val manager = SubscriptionManager(context.applicationContext, profileId)
        return Single.fromCallable {
            manager.subscriptionTable().getSubscriptionDirectForProfile(
                profileId,
                serviceId,
                channelUrl
            )?.let(manager::isSubscribedInCurrentMode) == true
        }.subscribeOn(Schedulers.io())
    }

    @JvmStatic
    fun actionLabel(subscribed: Boolean): Int {
        return if (subscribed) R.string.unsubscribe else R.string.subscribe_button_title
    }

    /** Remove only the current service/mode membership, preserving other profile memberships. */
    @JvmStatic
    fun unsubscribe(fragment: Fragment, serviceId: Int, channelUrl: String) {
        if (!fragment.isAdded || channelUrl.isBlank()) return
        val context = fragment.requireContext()
        val profileId = ProfileManager.getActiveProfileId(context)
        val manager = SubscriptionManager(context.applicationContext, profileId)
        val disposable = manager.deleteSubscription(serviceId, channelUrl)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (fragment.isAdded) {
                    Toast.makeText(context, R.string.channel_unsubscribed, Toast.LENGTH_SHORT).show()
                }
            }, {
                if (fragment.isAdded) {
                    Toast.makeText(context, R.string.subscription_change_failed, Toast.LENGTH_LONG).show()
                }
            })
        fragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = disposable.dispose()
        })
    }

    @JvmStatic
    fun show(fragment: Fragment, serviceId: Int, channelUrl: String, chooseGroups: Boolean) {
        if (!fragment.isAdded || fragment.view == null || channelUrl.isBlank()) return
        val context = fragment.requireContext()
        if (!KidsMode.isSubscriptionAllowed(context, serviceId)) return
        val profile = ProfileManager.getActiveProfileId(context)
        val manager = SubscriptionManager(context.applicationContext, profile)
        val feed = FeedDatabaseManager(context.applicationContext, profile)
        val disposables = CompositeDisposable()
        fragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = disposables.dispose()
        })
        fun error() {
            if (fragment.isAdded) Toast.makeText(context, R.string.subscription_change_failed, Toast.LENGTH_LONG).show()
        }
        disposables.add(
            Single.fromCallable { ChannelInfo.getInfo(NewPipe.getService(serviceId), channelUrl) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ info ->
                    fun save(groupIds: List<Long>?) {
                        disposables.add(
                            Single.fromCallable { manager.insertSubscriptionAndReturn(SubscriptionEntity.from(info)) }
                                .flatMapCompletable { subscription ->
                                    if (groupIds == null) {
                                        io.reactivex.rxjava3.core.Completable.complete()
                                    } else {
                                        feed.setGroupsForSubscription(subscription.uid, groupIds)
                                    }
                                }
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({ Toast.makeText(context, R.string.you_successfully_subscribed, Toast.LENGTH_SHORT).show() }, { error() })
                        )
                    }
                    if (!chooseGroups) {
                        save(null)
                        return@subscribe
                    }
                    disposables.add(
                        Single.fromCallable {
                            val groups = feed.groups().blockingFirst()
                            val existing = manager.subscriptionTable().getSubscriptionDirectForProfile(profile, serviceId, info.url)
                            val selected = existing?.let { feed.groupIdsForSubscription(it.uid).blockingFirst().toSet() } ?: emptySet()
                            groups to selected
                        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ (groups, selected) ->
                                if (!fragment.isAdded || fragment.isStateSaved) return@subscribe
                                if (groups.isEmpty()) {
                                    Toast.makeText(context, R.string.no_feed_group_created_yet, Toast.LENGTH_LONG).show()
                                    return@subscribe
                                }
                                val checked = selected.toMutableSet()
                                val dialog = MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.feed_groups_header_title)
                                    .setMultiChoiceItems(groups.map { it.name }.toTypedArray(), groups.map { it.uid in checked }.toBooleanArray()) { _, index, enabled ->
                                        if (enabled) checked.add(groups[index].uid) else checked.remove(groups[index].uid)
                                    }
                                    .setNegativeButton(R.string.cancel, null)
                                    .setPositiveButton(R.string.save) { _, _ -> save(checked.toList()) }
                                    .show()
                                fragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                                    override fun onDestroy(owner: LifecycleOwner) = dialog.dismiss()
                                })
                            }, { error() })
                    )
                }, { error() })
        )
    }
}
