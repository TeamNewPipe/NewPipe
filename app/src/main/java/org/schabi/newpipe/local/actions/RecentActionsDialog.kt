package org.schabi.newpipe.local.actions

import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.profiles.ProfileManager

/** Short-lived undo access, intentionally not a persistent activity/telemetry timeline. */
object RecentActionsDialog {
    @JvmStatic
    fun show(fragment: Fragment) {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        val profile = ProfileManager.getActiveProfileId(context)
        val entries = RecentUndoActions.snapshot(profile)
        if (entries.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.recent_actions_title)
                .setMessage(R.string.recent_actions_empty)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.recent_actions_title)
            .setItems(entries.map { fragment.getString(R.string.recent_action_undo, it.title) }.toTypedArray()) { _, which ->
                val action = entries[which]
                val undo = RecentUndoActions.claim(action.id, profile) ?: return@setItems
                Single.fromCallable { runCatching { undo() }.getOrDefault(false) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ success ->
                        RecentUndoActions.finish(action.id, success)
                        if (fragment.isAdded && ProfileManager.getActiveProfileId(fragment.requireContext()) == profile) {
                            Toast.makeText(
                                fragment.requireContext(),
                                if (success) R.string.recent_action_undone else R.string.recent_action_unavailable,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }, {
                        RecentUndoActions.finish(action.id, false)
                    })
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
