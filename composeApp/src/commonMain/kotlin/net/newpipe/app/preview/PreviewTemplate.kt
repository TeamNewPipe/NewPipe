/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preview

import androidx.compose.runtime.Composable
import net.newpipe.app.theme.AppTheme

/**
 * Template for previewing composable with defaults
 */
@Composable
fun PreviewTemplate(content: @Composable () -> Unit) {
    AppTheme(content = content)
}
