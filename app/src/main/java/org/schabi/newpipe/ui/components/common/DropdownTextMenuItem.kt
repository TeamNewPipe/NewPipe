package org.schabi.newpipe.ui.components.common

import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun DropdownTextMenuItem(
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
