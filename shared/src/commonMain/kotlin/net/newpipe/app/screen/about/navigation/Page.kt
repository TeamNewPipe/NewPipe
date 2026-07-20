/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.about.navigation

import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.tab_about
import newpipe.shared.generated.resources.tab_licenses
import org.jetbrains.compose.resources.StringResource

/**
 * Possible pages to show in about screen
 */
enum class Page(val localizedTitle: StringResource) {
    ABOUT(Res.string.tab_about),
    LICENSE(Res.string.tab_licenses)
}
