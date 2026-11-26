package org.schabi.newpipe.local.subscription

import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.profiles.ProfileManager

internal class SubscriptionGroupActions(
    private val fragment: Fragment,
    private val manager: SubscriptionManager
) {
    private val sourceProfileId = ProfileManager.getActiveProfileId(fragment.requireContext())
    private val disposables = CompositeDisposable()
    private var dialog: androidx.appcompat.app.AlertDialog? = null

    fun show(groupId: Long, name: String) {
        if (FeedGroupEntity.isVirtualGroup(groupId)) return
        val profiles = ProfileManager.getProfiles(fragment.requireContext())
            .filter { it.id != sourceProfileId }
        if (profiles.isEmpty()) {
            edit(groupId)
            return
        }
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(name)
            .setItems(
                arrayOf(
                    fragment.getString(R.string.feed_group_dialog_edit_title),
                    fragment.getString(R.string.group_subscribe_to_profile)
                )
            ) { _, which ->
                if (which == 0) {
                    edit(groupId)
                } else {
                    dialog = MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle(R.string.group_subscribe_to_profile)
                        .setItems(
                            profiles.map {
                                ProfileManager.getDisplayName(fragment.requireContext(), it)
                            }.toTypedArray()
                        ) { _, selected ->
                            val profile = profiles[selected]
                            val context = fragment.requireContext().applicationContext
                            val label = ProfileManager.getDisplayName(context, profile)
                            disposables.add(
                                Single.fromCallable {
                                    check(ProfileManager.getProfiles(context).any { it.id == profile.id })
                                    manager.copyGroupToProfile(groupId, profile.id)
                                }
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        if (fragment.view != null) {
                                            Toast.makeText(
                                                context,
                                                fragment.getString(
                                                    R.string.group_subscribed_to_profile,
                                                    name,
                                                    label
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }, { error ->
                                        if (fragment.view != null) {
                                            ErrorUtil.showSnackbar(
                                                fragment,
                                                ErrorInfo(
                                                    error,
                                                    UserAction.SUBSCRIPTION_CHANGE,
                                                    "Copying channel group to profile"
                                                )
                                            )
                                        }
                                    })
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private fun edit(groupId: Long) {
        FeedGroupDialog.newInstance(groupId).show(fragment.parentFragmentManager, null)
    }

    fun close() {
        dialog?.dismiss()
        dialog = null
        disposables.clear()
    }
}
