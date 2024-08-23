package org.schabi.newpipe.ui.components.items.stream

import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.viewmodels.StreamViewModel

@Composable
fun StreamMenu(
    stream: StreamInfoItem,
    expanded: Boolean,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val streamViewModel = viewModel<StreamViewModel>()
    val playerHolder = PlayerHolder.getInstance()

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        if (playerHolder.isPlayQueueReady) {
            StreamMenuItem(
                text = R.string.enqueue_stream,
                onClick = {
                    onDismissRequest()
                    SparseItemUtil.fetchItemInfoIfSparse(context, stream) {
                        NavigationHelper.enqueueOnPlayer(context, it)
                    }
                }
            )

            if (playerHolder.queuePosition < playerHolder.queueSize - 1) {
                StreamMenuItem(
                    text = R.string.enqueue_next_stream,
                    onClick = {
                        onDismissRequest()
                        SparseItemUtil.fetchItemInfoIfSparse(context, stream) {
                            NavigationHelper.enqueueNextOnPlayer(context, it)
                        }
                    }
                )
            }
        }

        StreamMenuItem(
            text = R.string.start_here_on_background,
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchItemInfoIfSparse(context, stream) {
                    NavigationHelper.playOnBackgroundPlayer(context, it, true)
                }
            }
        )
        StreamMenuItem(
            text = R.string.start_here_on_popup,
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchItemInfoIfSparse(context, stream) {
                    NavigationHelper.playOnPopupPlayer(context, it, true)
                }
            }
        )
        StreamMenuItem(
            text = R.string.download,
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchStreamInfoAndSaveToDatabase(
                    context, stream.serviceId, stream.url
                ) { info ->
                    // TODO: Use an AlertDialog composable instead.
                    val downloadDialog = DownloadDialog(context, info)
                    val fragmentManager = (context as FragmentActivity).supportFragmentManager
                    downloadDialog.show(fragmentManager, "downloadDialog")
                }
            }
        )
        StreamMenuItem(
            text = R.string.add_to_playlist,
            onClick = {
                onDismissRequest()
                val list = listOf(StreamEntity(stream))
                PlaylistDialog.createCorrespondingDialog(context, list) { dialog ->
                    val tag = if (dialog is PlaylistAppendDialog) "append" else "create"
                    dialog.show(
                        (context as FragmentActivity).supportFragmentManager,
                        "StreamDialogEntry@${tag}_playlist"
                    )
                }
            }
        )
        StreamMenuItem(
            text = R.string.share,
            onClick = {
                onDismissRequest()
                ShareUtils.shareText(context, stream.name, stream.url, stream.thumbnails)
            }
        )
        StreamMenuItem(
            text = R.string.open_in_browser,
            onClick = {
                onDismissRequest()
                ShareUtils.openUrlInBrowser(context, stream.url)
            }
        )
        StreamMenuItem(
            text = R.string.mark_as_watched,
            onClick = {
                onDismissRequest()
                streamViewModel.markAsWatched(stream)
            }
        )
        StreamMenuItem(
            text = R.string.show_channel_details,
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchUploaderUrlIfSparse(
                    context, stream.serviceId, stream.url, stream.uploaderUrl
                ) { url ->
                    NavigationHelper.openChannelFragment(context as FragmentActivity, stream, url)
                }
            }
        )
    }
}

@Composable
private fun StreamMenuItem(
    @StringRes text: Int,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(text = stringResource(text), color = MaterialTheme.colorScheme.onBackground)
        },
        onClick = onClick
    )
}
