/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

import kotlinx.serialization.Serializable

/**
 * A PeerTube instance as stored in the settings. Field names match the legacy JSON.
 */
@Serializable
data class PeertubeInstance(
    val name: String,
    val url: String
)
