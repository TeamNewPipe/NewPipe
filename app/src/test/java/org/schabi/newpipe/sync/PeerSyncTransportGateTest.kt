/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.net.SocketException
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerSyncTransportGateTest {
    @Test
    fun oneFailedStageDoesNotStopLaterCategories() {
        val gate = PeerSyncTransportGate()
        gate.record(TimeoutException("Timed out"))
        assertFalse(gate.isOpen)
    }

    @Test
    fun twoConsecutiveTransportFailuresStopRepeatedLongWaits() {
        val gate = PeerSyncTransportGate()
        gate.record(TimeoutException("Timed out"))
        gate.record(SocketException("Connection reset"))
        assertTrue(gate.isOpen)
    }

    @Test
    fun successfulCategoryResetsFailureStreak() {
        val gate = PeerSyncTransportGate()
        gate.record(TimeoutException("Timed out"))
        gate.record(null)
        gate.record(TimeoutException("Timed out"))
        assertFalse(gate.isOpen)
    }

    @Test
    fun applicationErrorDoesNotMarkPeerAsDisconnected() {
        val gate = PeerSyncTransportGate()
        gate.record(TimeoutException("Timed out"))
        gate.record(IllegalArgumentException("Invalid sync payload"))
        gate.record(TimeoutException("Timed out"))
        assertFalse(gate.isOpen)
    }

    @Test
    fun newAttemptStartsWithHealthyGate() {
        val expired = PeerSyncTransportGate()
        expired.record(TimeoutException("Timed out"))
        expired.record(TimeoutException("Timed out"))
        assertTrue(expired.isOpen)
        assertFalse(PeerSyncTransportGate().isOpen)
    }
}
