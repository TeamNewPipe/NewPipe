/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import org.schabi.newpipe.local.media.chapters.EmbeddedChapterReader

/** Embedded chapters describe the current file even when its former download record was rejected. */
@WorkerThread
internal fun ResolvedOfflineMedia.withEmbeddedChapters(context: Context, uri: String): ResolvedOfflineMedia {
    if (chapters.isNotEmpty()) return this
    val embedded = EmbeddedChapterReader.read(context, Uri.parse(uri), durationMillis).map { PreciseStreamSegment(it.title, it.startMillis) }
    if (embedded.isEmpty()) return this
    sourceInfo?.streamSegments = embedded
    return copy(chapters = embedded)
}
