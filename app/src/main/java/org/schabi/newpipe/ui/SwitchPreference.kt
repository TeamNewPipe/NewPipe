package org.schabi.newpipe.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.schabi.newpipe.ui.theme.SizeTokens

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    @StringRes title: Int,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @StringRes summary: Int? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = stringResource(id = title),
                modifier = Modifier.padding(SizeTokens.SpacingExtraSmall),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Start,
            )
            summary?.let {
                Text(
                    text = stringResource(id = summary),
                    modifier = Modifier.padding(SizeTokens.SpacingExtraSmall),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                )
            }
        }
        Spacer(modifier = Modifier.width(SizeTokens.SpacingSmall))
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}
