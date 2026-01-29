package org.schabi.newpipe.ui.components.items.stream

import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.ui.components.common.DropdownTextMenuItem
import org.schabi.newpipe.ui.components.items.Stream
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.viewmodels.StreamViewModel

@Composable
fun StreamMenu(
    stream: Stream,
    expanded: Boolean,
    onDismissRequest: () -> Unit
) {
    val info = stream.toStreamInfoItem()
    val context = LocalContext.current
    val streamViewModel = viewModel<StreamViewModel>()

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        if (PlayerHolder.isPlayQueueReady) {
            DropdownTextMenuItem(
                text = R.string.enqueue_stream,
                onClick = {
                    onDismissRequest()
                    SparseItemUtil.fetchItemInfoIfSparse(context, info) {
                        NavigationHelper.enqueueOnPlayer(context, it)
                    }
                },
            )

            if (PlayerHolder.queuePosition < PlayerHolder.queueSize - 1) {
                DropdownTextMenuItem(
                    text = R.string.enqueue_stream,
                    onClick = {
                        onDismissRequest()
                        SparseItemUtil.fetchItemInfoIfSparse(context, info) {
                            NavigationHelper.enqueueOnPlayer(context, it)
                        }
                    },
                )
            }
        }

        DropdownTextMenuItem(
            text = R.string.start_here_on_background,
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchItemInfoIfSparse(context, info) {
                    NavigationHelper.playOnBackgroundPlayer(context, it, true)
                }
            },
        )
        DropdownTextMenuItem(
            text = R.string.start_here_on_popup,
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchItemInfoIfSparse(context, info) {
                    NavigationHelper.playOnPopupPlayer(context, it, true)
                }
            },
        )

        if (stream.streamId != -1L) {
            DropdownTextMenuItem(
                text = R.string.delete,
                onClick = {
                    onDismissRequest()
                    streamViewModel.deleteStreamHistory(stream.streamId)
                },
            )
        }

        DropdownTextMenuItem(
            text = R.string.download,
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchStreamInfoAndSaveToDatabase(
                    context,
                    stream.serviceId,
                    stream.url
                ) { info ->
                    // TODO: Use an AlertDialog composable instead.
                    val downloadDialog = DownloadDialog(context, info)
                    val fragmentManager = context.findFragmentActivity().supportFragmentManager
                    downloadDialog.show(fragmentManager, "downloadDialog")
                }
            },
        )
        DropdownTextMenuItem(
            text = R.string.add_to_playlist,
            onClick = {
                onDismissRequest()
                val list = listOf(StreamEntity(info))
                PlaylistDialog.createCorrespondingDialog(context, list) { dialog ->
                    val tag = if (dialog is PlaylistAppendDialog) "append" else "create"
                    dialog.show(
                        context.findFragmentActivity().supportFragmentManager,
                        "StreamDialogEntry@${tag}_playlist",
                    )
                }
            },
        )
        DropdownTextMenuItem(
            text = R.string.share,
            onClick = {
                onDismissRequest()
                ShareUtils.shareText(context, stream.name, stream.url, stream.thumbnails)
            },
        )
        DropdownTextMenuItem(
            text = R.string.open_in_browser,
            onClick = {
                onDismissRequest()
                ShareUtils.openUrlInBrowser(context, stream.url)
            },
        )
        DropdownTextMenuItem(
            text = R.string.mark_as_watched,
            onClick = {
                onDismissRequest()
                streamViewModel.markAsWatched(info)
            }
        )
        DropdownTextMenuItem(
            text = R.string.show_channel_details,
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchUploaderUrlIfSparse(
                    context,
                    stream.serviceId,
                    stream.url,
                    stream.uploaderUrl
                ) { url ->
                    val activity = context.findFragmentActivity()
                    NavigationHelper.openChannelFragment(activity, info, url)
                }
            }
        )
    }
}
