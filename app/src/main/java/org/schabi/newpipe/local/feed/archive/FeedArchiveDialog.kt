package org.schabi.newpipe.local.feed.archive

import android.content.Context
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.NavigationHelper

/** Browse a disk-backed archive one page at a time; never materialize the full archive in UI. */
object FeedArchiveDialog {
    private const val PAGE_SIZE = 50

    @JvmStatic
    fun show(fragment: Fragment, page: Int = 0) {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        val profile = ProfileManager.getActiveProfileId(context)
        if (FeedArchiveSettings.mode(context, profile) == null) {
            settings(fragment, profile)
            return
        }
        val app = context.applicationContext
        Single.fromCallable { withFeedArchive(app) { it.page(profile, page, PAGE_SIZE) } }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ rows ->
                if (!fragment.isAdded ||
                    ProfileManager.getActiveProfileId(fragment.requireContext()) != profile
                ) {
                    return@subscribe
                }
                val labels = rows.map { row ->
                    row.title.ifBlank { row.url } + " • " + row.uploader
                }.toTypedArray()
                val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(fragment.getString(R.string.feed_archive_page, page + 1))
                    .setItems(labels) { _, which ->
                        val row = rows[which]
                        if (!fragment.parentFragmentManager.isStateSaved) {
                            NavigationHelper.openVideoDetailFragment(
                                fragment.requireContext(),
                                fragment.parentFragmentManager,
                                row.serviceId,
                                row.url,
                                row.title,
                                null,
                                false
                            )
                        }
                    }
                    .setPositiveButton(R.string.feed_archive_older) { _, _ -> show(fragment, page + 1) }
                    .setNegativeButton(
                        if (page == 0) R.string.close else R.string.feed_archive_newer
                    ) { _, _ -> if (page > 0) show(fragment, page - 1) }
                    .setNeutralButton(R.string.feed_archive_settings) { _, _ -> settings(fragment, profile) }
                    .create()
                dialog.setOnShowListener {
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                        .isEnabled = rows.size == PAGE_SIZE
                }
                dialog.show()
            }, { error ->
                if (fragment.isAdded) {
                    MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle(R.string.feed_archive_title)
                        .setMessage(error.message ?: fragment.getString(R.string.feed_archive_error))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            })
    }

    private fun settings(fragment: Fragment, profile: String) {
        if (!fragment.isAdded) return
        val choices = arrayOf(
            fragment.getString(R.string.feed_archive_off),
            fragment.getString(R.string.feed_archive_30),
            fragment.getString(R.string.feed_archive_90),
            fragment.getString(R.string.feed_archive_180)
        )
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.feed_archive_settings)
            .setSingleChoiceItems(choices, -1) { dialog, index ->
                val app = fragment.requireContext().applicationContext
                FeedArchiveSettings.set(app, profile, index)
                dialog.dismiss()
                Single.fromCallable { withFeedArchive(app) { it.prune(profile) } }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({}, {})
                if (index != 0) show(fragment)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
