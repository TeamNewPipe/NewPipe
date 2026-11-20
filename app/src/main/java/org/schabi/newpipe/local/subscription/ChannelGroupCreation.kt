package org.schabi.newpipe.local.subscription

import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog

/** Opens a new Channel Group with the channel already selected, respecting the active profile. */
object ChannelGroupCreation {
    @JvmStatic
    fun open(
        fragment: Fragment,
        channel: ChannelInfo?,
        profileId: String,
        existing: SubscriptionEntity?,
        subscribedInCurrentMode: Boolean,
        disposables: CompositeDisposable
    ) {
        val info = channel ?: return
        if (!fragment.isAdded || fragment.parentFragmentManager.isStateSaved) return

        fun show(subscriptionId: Long) {
            if (fragment.isAdded && !fragment.parentFragmentManager.isStateSaved) {
                FeedGroupDialog.forChannel(subscriptionId)
                    .show(fragment.parentFragmentManager, "channel_new_group")
            }
        }

        if (!needsSubscription(existing, subscribedInCurrentMode)) {
            show(requireNotNull(existing).uid)
            return
        }

        // An explicit confirmation avoids silently subscribing when the user merely opens a picker.
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.channel_groups_create_from_channel)
            .setMessage(R.string.channel_groups_subscribe_before_creation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.channel_groups_subscribe_and_continue) { _, _ ->
                val app = fragment.requireContext().applicationContext
                disposables.add(
                    Single.fromCallable {
                        SubscriptionManager(app, profileId)
                            .insertSubscriptionAndReturn(SubscriptionEntity.from(info)).uid
                    }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { subscriptionId -> show(subscriptionId) },
                            { error ->
                                Log.e("ChannelGroupCreation", "Unable to subscribe for group creation", error)
                                if (fragment.isAdded) {
                                    Toast.makeText(
                                        fragment.requireContext(),
                                        R.string.channel_groups_create_failed,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                )
            }
            .show()
    }

    internal fun needsSubscription(existing: SubscriptionEntity?, subscribedInMode: Boolean): Boolean {
        return existing == null || !subscribedInMode
    }
}
