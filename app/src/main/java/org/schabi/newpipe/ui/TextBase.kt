package org.schabi.newpipe.ui

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

@Composable
internal fun TextBase(
    @StringRes title: Int,
    @StringRes summary: Int?,
    enabled: Boolean
) {
    Text(
        text = stringResource(id = title),
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Start,
        color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    )
    summary?.let {
        Text(
            text = stringResource(id = summary),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
    }
}
