/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media.chapters

/** A local seek destination. Byte offsets and external URLs from metadata are never followed. */
data class EmbeddedChapter(val startMillis: Long, val title: String)
