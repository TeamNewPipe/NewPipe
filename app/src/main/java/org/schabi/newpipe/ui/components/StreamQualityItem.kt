package org.schabi.newpipe.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StreamQualityItem(
    formatName: String,
    quality: String,
    size: String?,
    isVideoOnly: Boolean,
    modifier: Modifier = Modifier,
    isIconVisible: Boolean = isVideoOnly
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isVideoOnly) {
            Icon(
                imageVector = Icons.Default.VolumeOff,
                contentDescription = null, // "No sound" icon
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .testTag("wo_sound_icon")
                    .padding(end = 12.dp)
                    .alpha(if (isIconVisible) 1f else 0f)
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = quality,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (size != null) {
            Text(
                text = size,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
