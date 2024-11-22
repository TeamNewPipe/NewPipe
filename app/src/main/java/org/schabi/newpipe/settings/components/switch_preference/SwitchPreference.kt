package org.schabi.newpipe.settings.components.switch_preference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingExtraSmall
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingMedium

@Composable
fun SwitchPreferenceComponent(
    title: String,
    summary: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(SpacingMedium)
        ) {
            Text(text = title)
            Spacer(modifier = Modifier.padding(SpacingExtraSmall))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.alpha(0.6f)
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(SpacingMedium)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SwitchPreferenceComponentPreview() {
    val title = "Watch history"
    val subtitle = "Keep track of watched videos"
    var isChecked = false
    AppTheme {
        SwitchPreferenceComponent(
            title = title,
            summary = subtitle,
            isChecked = isChecked,
            onCheckedChange = {
                isChecked = it
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
