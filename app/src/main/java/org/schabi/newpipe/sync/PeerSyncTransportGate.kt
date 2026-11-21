/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

/**
 * Attempt-scoped circuit breaker: avoid a long series of per-category timeouts after a peer is
 * repeatedly unreachable. Each failed stage has already had its one bounded rediscovery/retry.
 * Next manual/background sync starts with a fresh gate and can recover immediately.
 */
internal class PeerSyncTransportGate {
    private var consecutiveTransportFailures = 0

    val isOpen: Boolean
        get() = consecutiveTransportFailures >= MAX_CONSECUTIVE_FAILURES

    fun record(error: Throwable?) {
        consecutiveTransportFailures = if (
            error != null && DeviceSyncTransportRecovery.shouldRetryTransportFailure(error)
        ) {
            (consecutiveTransportFailures + 1).coerceAtMost(MAX_CONSECUTIVE_FAILURES)
        } else {
            0
        }
    }

    companion object {
        private const val MAX_CONSECUTIVE_FAILURES = 2
        const val SKIPPED_DIAGNOSTIC =
            "Skipped: peer unreachable after repeated transport failures; try syncing again"
    }
}
