/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

/**
 * Class to hold data for links shown to users
 */
data class Link(
    val title: String,
    val description: String,
    val action: String,
    val url: String
)
