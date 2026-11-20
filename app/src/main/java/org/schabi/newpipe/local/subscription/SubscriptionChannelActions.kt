package org.schabi.newpipe.local.subscription

import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.DialogTitleBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.local.actions.RecentUndoActions
import org.schabi.newpipe.local.feed.FeedAllExclusions
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.profiles.ProfileRecord
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy

internal class SubscriptionChannelActions(
    private val fragment: Fragment,
    private val subscriptionManager: SubscriptionManager
) {
    private val disposables = CompositeDisposable()
    private var dialog: androidx.appcompat.app.AlertDialog? = null

    fun show(selectedItem: ChannelInfoItem) {
        if (fragment.view == null) return
        val commands = mutableListOf<Pair<String, () -> Unit>>(
            fragment.getString(R.string.share) to {
                ShareUtils.shareText(
                    fragment.requireContext(),
                    selectedItem.name,
                    selectedItem.url,
                    ExtractorImageCompat.thumbnailImages(selectedItem)
                )
            },
            fragment.getString(R.string.open_in_browser) to {
                ShareUtils.openUrlInBrowser(fragment.requireContext(), selectedItem.url)
            }
        )
        if (KidsMode.isSubscriptionAllowed(fragment.requireContext(), selectedItem.serviceId) &&
            ProfileManager.getProfiles(fragment.requireContext()).size > 1
        ) {
            commands += fragment.getString(R.string.channel_subscribe_to_profile) to {
                showSubscribeToProfileDialog(selectedItem)
            }
        }
        val activeProfileId = ProfileManager.getActiveProfileId(fragment.requireContext())
        val excluded = FeedAllExclusions.isExcluded(
            fragment.requireContext(),
            activeProfileId,
            selectedItem.serviceId,
            selectedItem.url
        )
        commands += fragment.getString(
            if (excluded) R.string.feed_include_in_all else R.string.feed_exclude_from_all
        ) to {
            val app = fragment.requireContext().applicationContext
            val nowExcluded = FeedAllExclusions.toggle(
                app,
                activeProfileId,
                selectedItem.serviceId,
                selectedItem.url
            )
            RecentUndoActions.record(activeProfileId, selectedItem.name) {
                if (FeedAllExclusions.isExcluded(
                        app,
                        activeProfileId,
                        selectedItem.serviceId,
                        selectedItem.url
                    ) != nowExcluded
                ) {
                    false
                } else {
                    FeedAllExclusions.toggle(
                        app,
                        activeProfileId,
                        selectedItem.serviceId,
                        selectedItem.url
                    )
                    true
                }
            }
            Toast.makeText(
                fragment.requireContext(),
                if (nowExcluded) R.string.feed_excluded_from_all else R.string.feed_included_in_all,
                Toast.LENGTH_SHORT
            ).show()
        }
        commands += fragment.getString(R.string.unsubscribe) to {
            deleteChannel(selectedItem)
        }

        val dialogTitleBinding = DialogTitleBinding.inflate(LayoutInflater.from(fragment.requireContext()))
        dialogTitleBinding.root.isSelected = true
        dialogTitleBinding.itemTitleView.text = selectedItem.name
        dialogTitleBinding.itemAdditionalDetails.visibility = View.GONE

        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setCustomTitle(dialogTitleBinding.root)
            .setItems(commands.map { it.first }.toTypedArray()) { _, which ->
                commands[which].second.invoke()
            }
            .show()
    }

    private fun showSubscribeToProfileDialog(selectedItem: ChannelInfoItem) {
        val activeProfileId = ProfileManager.getActiveProfileId(fragment.requireContext())
        val otherProfiles = ProfileManager.getProfiles(fragment.requireContext())
            .filter { it.id != activeProfileId }
        if (otherProfiles.isEmpty()) {
            return
        }

        val labels = otherProfiles
            .map { ProfileManager.getDisplayName(fragment.requireContext(), it) }
            .toTypedArray()
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.channel_subscribe_to_profile_title)
            .setItems(labels) { _, which ->
                subscribeToProfile(selectedItem, otherProfiles[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun subscribeToProfile(selectedItem: ChannelInfoItem, profile: ProfileRecord) {
        val appContext = fragment.requireContext().applicationContext
        val displayName = ProfileManager.getDisplayName(fragment.requireContext(), profile)
        val entity = SubscriptionEntity(
            serviceId = selectedItem.serviceId,
            url = selectedItem.url,
            name = selectedItem.name,
            avatarUrl = ImageStrategy.imageListToDbUrl(
                ExtractorImageCompat.thumbnailImages(selectedItem)
            ),
            subscriberCount = selectedItem.subscriberCount.takeIf { it >= 0 },
            description = selectedItem.description
        )

        disposables.add(
            Single.fromCallable {
                SubscriptionManager(appContext, profile.id)
                    .insertSubscriptionAndReturn(entity)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        if (fragment.view == null) return@subscribe
                        Toast.makeText(
                            fragment.requireContext(),
                            fragment.getString(R.string.channel_subscribed_to_profile, displayName),
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    { error ->
                        if (fragment.view == null) return@subscribe
                        ErrorUtil.showSnackbar(
                            fragment,
                            ErrorInfo(
                                error,
                                UserAction.SUBSCRIPTION_CHANGE,
                                "Subscribing ${selectedItem.url} to profile $displayName",
                                selectedItem.serviceId,
                                selectedItem.url
                            )
                        )
                    }
                )
        )
    }

    private fun deleteChannel(selectedItem: ChannelInfoItem) {
        disposables.add(
            subscriptionManager.deleteLibrarySubscription(selectedItem.serviceId, selectedItem.url)
                .subscribe(
                    {
                        if (fragment.view != null) {
                            Toast.makeText(fragment.requireContext(), R.string.channel_unsubscribed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    { error ->
                        if (fragment.view != null) {
                            ErrorUtil.showSnackbar(
                                fragment,
                                ErrorInfo(
                                    error,
                                    UserAction.SUBSCRIPTION_CHANGE,
                                    "Unsubscribing ${selectedItem.url}",
                                    selectedItem.serviceId,
                                    selectedItem.url
                                )
                            )
                        }
                    }
                )
        )
    }

    fun close() {
        dialog?.dismiss()
        dialog = null
        disposables.clear()
    }
}
