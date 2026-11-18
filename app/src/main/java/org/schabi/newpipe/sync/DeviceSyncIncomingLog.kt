/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

data class IncomingHistoryBatch(
    val peerId: String,
    val category: HistorySyncCategory,
    val sentChanges: Int,
    val receivedChanges: Int,
    val error: String? = null
)

/** Updates a receiver-side diagnostic record without storing synced user content. */
internal object DeviceSyncIncomingLog {
    fun merge(entry: DeviceSyncLogEntry, batch: IncomingHistoryBatch): DeviceSyncLogEntry {
        val category = when (batch.category) {
            HistorySyncCategory.WATCH -> DeviceSyncLogCategory.WATCH_HISTORY
            HistorySyncCategory.SEARCH -> DeviceSyncLogCategory.SEARCH_HISTORY
            HistorySyncCategory.LEARNING_NOTES -> DeviceSyncLogCategory.LEARNING_NOTES
        }
        return entry.copy(
            attempts = entry.attempts.map { attempt ->
                if (attempt.peerId != batch.peerId) return@map attempt
                val previous = attempt.categories.firstOrNull { it.category == category }
                val result = DeviceSyncLogCategoryResult(
                    category = category,
                    status = if (batch.error != null ||
                        previous?.status == DeviceSyncLogStatus.FAILED
                    ) {
                        DeviceSyncLogStatus.FAILED
                    } else {
                        DeviceSyncLogStatus.SUCCEEDED
                    },
                    sentChanges = (previous?.sentChanges ?: 0) + batch.sentChanges,
                    receivedChanges = (previous?.receivedChanges ?: 0) + batch.receivedChanges,
                    error = batch.error?.take(512) ?: previous?.error
                )
                attempt.copy(
                    categories = attempt.categories.filterNot { it.category == category } + result
                )
            }
        )
    }
}
