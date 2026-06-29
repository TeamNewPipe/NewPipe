/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

@Singleton(binds = [DownloadActions::class])
class AndroidDownloadActions(
    private val legacyHooks: AndroidLegacyHooks
) : DownloadActions {

    override fun pickDirectory(onPicked: (String) -> Unit) =
        legacyHooks.requestDirectoryPicker(onPicked)
}