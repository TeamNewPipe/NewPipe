/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.posix.exit

fun mainViewController() = ComposeUIViewController {
    App(
        // TODO: Remove this as Apple doesn't likes quitting app manually
        onCloseRequest = { exit(0) }
    )
}
