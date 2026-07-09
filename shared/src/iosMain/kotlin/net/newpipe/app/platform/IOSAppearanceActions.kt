/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

@Singleton(binds = [AppearanceActions::class])
class IOSAppearanceActions : AppearanceActions {
    override fun openCaptionSettings() = Unit
}
