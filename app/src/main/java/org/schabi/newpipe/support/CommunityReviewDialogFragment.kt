/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.support

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R

class CommunityReviewDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.community_review_prompt_title)
        .setMessage(R.string.community_review_prompt_message)
        .setPositiveButton(R.string.community_reviews_action) { _, _ -> CommunityReviews.openPage(requireContext()) }
        .setNegativeButton(R.string.community_review_prompt_later) { _, _ -> remindLater() }
        .setNeutralButton(R.string.github_star_prompt_never) { _, _ -> CommunityReviews.state(requireContext()).stopAsking() }
        .create()

    override fun onCancel(dialog: DialogInterface) {
        remindLater()
        super.onCancel(dialog)
    }

    private fun remindLater() = CommunityReviews.state(requireContext()).remindLater(System.currentTimeMillis())

    companion object {
        const val TAG = "CommunityReviewPrompt"
    }
}
