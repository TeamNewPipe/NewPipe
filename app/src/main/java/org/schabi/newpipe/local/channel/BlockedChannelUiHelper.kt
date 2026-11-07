/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.channel

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.ThemeHelper

object BlockedChannelUiHelper {
    @JvmStatic
    fun blockChannel(fragment: Fragment, streamInfoItem: StreamInfoItem) {
        blockChannel(fragment.requireContext(), fragment.requireActivity(), streamInfoItem)
    }

    @JvmStatic
    fun blockChannel(activity: FragmentActivity, streamInfoItem: StreamInfoItem) {
        blockChannel(activity, activity, streamInfoItem)
    }

    private fun blockChannel(context: Context, activity: Activity, streamInfoItem: StreamInfoItem) {
        SparseItemUtil.fetchUploaderUrlIfSparse(
            context,
            streamInfoItem.serviceId,
            streamInfoItem.url,
            streamInfoItem.uploaderUrl
        ) { uploaderUrl ->
            if (uploaderUrl.isNullOrBlank()) {
                return@fetchUploaderUrlIfSparse
            }

            val blockedChannelManager = BlockedChannelManager(context)
            blockedChannelManager.blockChannel(uploaderUrl, streamInfoItem.uploaderName ?: "")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        showUndoSnackbar(
                            context = context,
                            activity = activity,
                            blockedChannelManager = blockedChannelManager,
                            uploaderUrl = uploaderUrl,
                            channelName = streamInfoItem.uploaderName
                                ?: context.getString(R.string.unknown_content)
                        )
                    },
                    { }
                )
        }
    }

    private fun showUndoSnackbar(
        context: Context,
        activity: Activity,
        blockedChannelManager: BlockedChannelManager,
        uploaderUrl: String,
        channelName: String
    ) {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        Snackbar.make(
            rootView,
            context.getString(R.string.channel_blocked_message, channelName),
            Snackbar.LENGTH_LONG
        ).setAction(R.string.undo) {
            blockedChannelManager.unblockChannel(uploaderUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, { })
        }.setActionTextColor(ThemeHelper.resolveColorFromAttr(context, android.R.attr.colorAccent))
            .show()
    }
}
