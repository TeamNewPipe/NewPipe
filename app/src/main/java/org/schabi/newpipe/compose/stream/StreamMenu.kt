package org.schabi.newpipe.compose.stream

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.R

@Composable
fun StreamMenu(onDismissRequest: () -> Unit) {
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
            onClick = onDismissRequest
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.add_to_playlist)) },
            onClick = onDismissRequest
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.share)) },
            onClick = onDismissRequest
        )
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.open_in_browser)) },
            onClick = onDismissRequest
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
