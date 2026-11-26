package org.schabi.newpipe.local.subscription

import android.widget.Toast
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.profiles.ProfileManager

internal class SubscriptionFeedMuteActions(
    private val fragment: Fragment,
    private val manager: SubscriptionManager,
    private val profileId: String = ProfileManager.getActiveProfileId(fragment.requireContext())
) {
    private val disposables = CompositeDisposable()
    private var busy = false

    fun set(channels: List<SubscriptionEntity>, muted: Boolean) {
        if (busy || fragment.view == null || channels.isEmpty()) return
        val context = fragment.requireContext().applicationContext
        if (ProfileManager.getActiveProfileId(context) != profileId) return
        val ids = channels.filter { it.profileId == profileId }.map { it.uid }
        if (ids.isEmpty()) return
        busy = true
        disposables.add(
            Completable.defer {
                if (ProfileManager.getActiveProfileId(context) == profileId) {
                    manager.setFeedMuted(ids, muted)
                } else {
                    Completable.complete()
                }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    busy = false
                    if (fragment.view != null && ProfileManager.getActiveProfileId(context) == profileId) {
                        Toast.makeText(context, if (muted) R.string.feed_mute_done else R.string.feed_unmute_done, Toast.LENGTH_SHORT).show()
                    }
                }, { error ->
                    busy = false
                    if (fragment.view != null && ProfileManager.getActiveProfileId(context) == profileId) {
                        ErrorUtil.showSnackbar(fragment, ErrorInfo(error, UserAction.SUBSCRIPTION_CHANGE, "Changing feed mute state"))
                    }
                })
        )
    }

    fun close() {
        disposables.clear()
        busy = false
    }
}
