/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

data class ChannelNotification(
    val id: Long,
    val serviceId: Int,
    val url: String,
    val name: String,
    val enabled: Boolean
)
