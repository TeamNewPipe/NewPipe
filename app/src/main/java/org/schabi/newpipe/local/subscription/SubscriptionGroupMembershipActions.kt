/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.profiles.ProfileManager

/** Edits memberships of existing subscriptions without changing their service modes or ordering. */
internal class SubscriptionGroupMembershipActions(
    private val fragment: Fragment,
    private val profileId: String = ProfileManager.getActiveProfileId(fragment.requireContext())
) {
    private val appContext = fragment.requireContext().applicationContext
    private val database get() = NewPipeDatabase.getInstance(appContext)
    private val disposables = CompositeDisposable()
    private var dialog: AlertDialog? = null
    private var generation = 0
    private var busy = false

    fun showSingle(channel: ChannelInfoItem) {
        load {
            database.runInTransaction<Selection> {
                val subscription = database.subscriptionDAO()
                    .getSubscriptionDirectForProfile(profileId, channel.serviceId, channel.url)
                Selection(
                    database.feedGroupDAO().getAllDirectForProfile(profileId),
                    listOfNotNull(subscription?.uid),
                    subscription?.let {
                        database.feedGroupDAO().getGroupIdsForSubscriptionDirectForProfile(profileId, it.uid).toSet()
                    }.orEmpty(),
                    Mode.EDIT
                )
            }
        }
    }

    fun showBulk(channels: List<SubscriptionEntity>, add: Boolean) {
        val ids = channels.filter { it.profileId == profileId }.map { it.uid }.distinct()
        if (ids.isEmpty()) return
        load {
            Selection(
                database.feedGroupDAO().getAllDirectForProfile(profileId),
                ids,
                emptySet(),
                if (add) Mode.ADD else Mode.REMOVE
            )
        }
    }

    private fun load(selection: () -> Selection) {
        if (busy || !canInteract()) return
        close()
        val request = generation
        val view = fragment.requireView()
        busy = true
        disposables.add(
            Single.fromCallable(selection)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ data ->
                    if (request != generation) return@subscribe
                    busy = false
                    if (fragment.view === view && canInteract()) showPicker(data)
                }, { error ->
                    if (request != generation) return@subscribe
                    busy = false
                    if (fragment.view === view) showError(error)
                })
        )
    }

    private fun showPicker(data: Selection) {
        if (data.subscriptionIds.isEmpty()) return
        if (data.groups.isEmpty()) {
            dialog = MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.channel_groups_dialog_title)
                .setMessage(R.string.subscription_groups_create_first)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        val selected = data.originalGroupIds.toMutableSet()
        val title = when (data.mode) {
            Mode.EDIT -> fragment.getString(R.string.channel_groups_dialog_title)

            Mode.ADD -> fragment.resources.getQuantityString(
                R.plurals.subscription_groups_add_title,
                data.subscriptionIds.size,
                data.subscriptionIds.size
            )

            Mode.REMOVE -> fragment.resources.getQuantityString(
                R.plurals.subscription_groups_remove_title,
                data.subscriptionIds.size,
                data.subscriptionIds.size
            )
        }
        dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(title)
            .setMultiChoiceItems(
                data.groups.map { it.name }.toTypedArray(),
                data.groups.map { it.uid in selected }.toBooleanArray()
            ) { picker, position, checked ->
                val id = data.groups[position].uid
                if (checked) selected.add(id) else selected.remove(id)
                (picker as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                    data.mode == Mode.EDIT || selected.isNotEmpty()
            }
            .setPositiveButton(R.string.save) { _, _ -> apply(data, selected) }
            .setNegativeButton(R.string.cancel, null)
            .show().also {
                it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                    data.mode == Mode.EDIT || selected.isNotEmpty()
            }
    }

    private fun apply(data: Selection, selected: Set<Long>) {
        if (busy || !canInteract()) return
        val added = if (data.mode == Mode.REMOVE) emptySet() else selected - data.originalGroupIds
        val removed = when (data.mode) {
            Mode.EDIT -> data.originalGroupIds - selected
            Mode.ADD -> emptySet()
            Mode.REMOVE -> selected
        }
        if (added.isEmpty() && removed.isEmpty()) return
        val request = generation
        val view = fragment.requireView()
        busy = true
        disposables.add(
            Single.fromCallable {
                database.feedGroupDAO().changeGroupsForSubscriptionsForProfile(
                    profileId,
                    data.subscriptionIds,
                    added.toList(),
                    removed.toList()
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (request != generation) return@subscribe
                    busy = false
                    if (fragment.view === view && canInteract()) {
                        Toast.makeText(appContext, R.string.channel_groups_updated, Toast.LENGTH_SHORT).show()
                    }
                }, { error ->
                    if (request != generation) return@subscribe
                    busy = false
                    if (fragment.view === view) showError(error)
                })
        )
    }

    private fun canInteract(): Boolean = fragment.view != null &&
        ProfileManager.getActiveProfileId(appContext) == profileId &&
        !fragment.parentFragmentManager.isStateSaved

    private fun showError(error: Throwable) {
        if (!canInteract()) return
        ErrorUtil.showSnackbar(fragment, ErrorInfo(error, UserAction.SUBSCRIPTION_CHANGE, "Updating subscription groups"))
    }

    fun close() {
        generation++
        dialog?.dismiss()
        dialog = null
        disposables.clear()
        busy = false
    }

    private enum class Mode { EDIT, ADD, REMOVE }

    private data class Selection(
        val groups: List<FeedGroupEntity>,
        val subscriptionIds: List<Long>,
        val originalGroupIds: Set<Long>,
        val mode: Mode
    )
}
