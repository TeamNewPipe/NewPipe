package org.schabi.newpipe.ui.components.stream

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.external_communication.ShareUtils

@Composable
fun StreamMenu(
    stream: StreamInfoItem,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    // TODO: Implement remaining click actions
    DropdownMenu(expanded = true, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.start_here_on_background)) },
            onClick = onDismissRequest
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.start_here_on_popup)) },
            onClick = onDismissRequest
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.download)) },
            onClick = {
                onDismissRequest()
                SparseItemUtil.fetchStreamInfoAndSaveToDatabase(
                    context, stream.serviceId, stream.url
                ) { info: StreamInfo ->
                    val downloadDialog = DownloadDialog(context, info)
                    val fragmentManager = (context as FragmentActivity).supportFragmentManager
                    downloadDialog.show(fragmentManager, "downloadDialog")
                }
            }
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.add_to_playlist)) },
            onClick = onDismissRequest
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.share)) },
            onClick = {
                onDismissRequest()
                ShareUtils.shareText(context, stream.name, stream.url, stream.thumbnails)
            }
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.open_in_browser)) },
            onClick = {
                onDismissRequest()
                ShareUtils.openUrlInBrowser(context, stream.url)
            }
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.mark_as_watched)) },
            onClick = onDismissRequest
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.show_channel_details)) },
            onClick = onDismissRequest
        )
    }
}
