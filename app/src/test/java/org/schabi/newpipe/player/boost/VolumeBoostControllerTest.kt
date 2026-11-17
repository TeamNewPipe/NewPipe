/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.boost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeBoostControllerTest {
    @Test
    fun enablingCreatesOneSessionEngineAndPersistsGain() {
        val store = FakeStore()
        val factory = FakeFactory()
        val controller = VolumeBoostController(store, factory)
        controller.attachAudioSession(41)

        assertEquals(0, factory.createCount)

        controller.updateGainMb(600)

        assertEquals(1, factory.createCount)
        assertEquals(41, factory.lastSessionId)
        assertEquals(600, factory.lastEngine?.gainMb)
        assertTrue(factory.lastEngine?.enabledState == true)
        assertEquals(600, store.savedGainMb)
        assertTrue(controller.isOperational)
    }

    @Test
    fun audioSessionChangeReleasesOldEngineAndReappliesGain() {
        val store = FakeStore(900)
        val factory = FakeFactory()
        val controller = VolumeBoostController(store, factory)

        controller.attachAudioSession(10)
        val first = factory.lastEngine
        controller.attachAudioSession(11)

        assertTrue(first?.released == true)
        assertFalse(first?.enabledState == true)
        assertEquals(2, factory.createCount)
        assertEquals(11, factory.lastSessionId)
        assertEquals(900, factory.lastEngine?.gainMb)
    }

    @Test
    fun disablingReleasesEngineAndKeepsSessionReadyForReenable() {
        val store = FakeStore(300)
        val factory = FakeFactory()
        val controller = VolumeBoostController(store, factory)
        controller.attachAudioSession(7)
        val first = factory.lastEngine

        controller.updateGainMb(0)
        assertTrue(first?.released == true)
        assertFalse(controller.isOperational)

        controller.updateGainMb(1200)
        assertEquals(2, factory.createCount)
        assertEquals(1200, factory.lastEngine?.gainMb)
    }

    @Test
    fun unsupportedValuesUseNearestSafePreset() {
        val store = FakeStore(500)
        val controller = VolumeBoostController(store, FakeFactory())

        assertEquals(600, controller.gainMb)
        controller.updateGainMb(10_000)

        assertEquals(1200, controller.gainMb)
        assertEquals(1200, store.savedGainMb)
    }

    @Test
    fun unavailableBackendKeepsBoostNonOperational() {
        val controller = VolumeBoostController(FakeStore(600), FakeFactory(false))

        controller.attachAudioSession(9)

        assertTrue(controller.isEnabled)
        assertTrue(!controller.isAvailable)
        assertFalse(controller.isOperational)
    }

    private class FakeStore(initialGainMb: Int = 0) : VolumeBoostStateStore {
        private val initial = initialGainMb
        var savedGainMb = initialGainMb

        override fun loadGainMb(): Int = initial

        override fun saveGainMb(gainMb: Int) {
            savedGainMb = gainMb
        }
    }

    private class FakeFactory(
        override val isAvailable: Boolean = true
    ) : VolumeBoostEngineFactory {
        var createCount = 0
        var lastSessionId = -1
        var lastEngine: FakeEngine? = null

        override fun create(audioSessionId: Int): VolumeBoostEngine {
            createCount++
            lastSessionId = audioSessionId
            return FakeEngine().also { lastEngine = it }
        }
    }

    private class FakeEngine : VolumeBoostEngine {
        var gainMb = 0
        var enabledState = false
        var released = false

        override fun setTargetGainMb(gainMb: Int) {
            this.gainMb = gainMb
        }

        override fun setEnabled(enabled: Boolean) {
            enabledState = enabled
        }

        override fun release() {
            released = true
        }
    }
}
