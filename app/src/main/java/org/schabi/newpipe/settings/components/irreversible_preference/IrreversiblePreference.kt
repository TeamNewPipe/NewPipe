package org.schabi.newpipe.settings.components.irreversible_preference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingExtraSmall
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingMedium

@Composable
fun IrreversiblePreferenceComponent(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val clickModifier = if (enabled) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }
    Row(
        modifier = clickModifier.then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha by remember {
            derivedStateOf {
                if (enabled) 1f else 0.38f
            }
        }
        Column(
            modifier = Modifier.padding(SpacingMedium)
        ) {
            Text(
                text = title,
                modifier = Modifier.alpha(alpha),
            )
            Spacer(modifier = Modifier.padding(SpacingExtraSmall))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.alpha(alpha * 0.6f),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun IrreversiblePreferenceComponentPreview() {
    val title = "Wipe cached metadata"
    val summary = "Remove all cached webpage data"
    AppTheme {
        Column {

            IrreversiblePreferenceComponent(
                title = title,
                summary = summary,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
            IrreversiblePreferenceComponent(
                title = title,
                summary = summary,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )
        }
    }
}
