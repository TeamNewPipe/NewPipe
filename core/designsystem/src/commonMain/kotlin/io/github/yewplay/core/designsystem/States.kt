/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.model.YpError

/** Labels of the error buttons, from the app's strings. */
data class YpErrorLabels(val retry: String, val openInBrowser: String)

/** An error in place of the content, with retry, report/solve and open-in-browser. */
@Composable
fun YpErrorState(
    error: YpError,
    labels: YpErrorLabels,
    onRetry: () -> Unit,
    onAction: () -> Unit,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    YpMessageState(title = error.message, detail = null, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (error.canRetry) {
                YpPillButton(labels.retry, primary = true, onClick = onRetry)
            }
            error.actionLabel?.let { YpPillButton(it, primary = !error.canRetry, onClick = onAction) }
        }
        if (error.canOpenInBrowser) {
            TextButton(onClick = onOpenInBrowser) {
                Text(
                    text = labels.openInBrowser,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A centered title, an optional detail and buttons (empty lists, errors). */
@Composable
fun YpMessageState(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))
        buttons()
    }
}

/** A 44 dp button: ink when primary, secondary surface otherwise (proposal, p. 7). */
@Composable
fun YpPillButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) colors.onPrimary else colors.onSurface
        )
    }
}

/** Blocks in the shape of compact video rows while a list loads (proposal, p. 17). */
@Composable
fun YpSkeletonList(modifier: Modifier = Modifier, rows: Int = 7) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) { YpSkeletonRow() }
    }
}

/** One skeleton block in the shape of a compact video row. */
@Composable
fun YpSkeletonRow() {
    val block = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(144.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(block)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1f, 0.7f, 0.45f).forEach { fraction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(block)
                )
            }
        }
    }
}

/** Small spinner at the end of a list while the next page loads. */
@Composable
fun YpLoadingMore() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}
