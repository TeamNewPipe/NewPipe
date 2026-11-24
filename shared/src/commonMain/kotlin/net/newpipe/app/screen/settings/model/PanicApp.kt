/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.model

import kotlinx.serialization.Serializable

@Serializable
data class PanicApp(
    val packageName: String,
    val name: String,
    val signatures: List<String> = emptyList()
)
