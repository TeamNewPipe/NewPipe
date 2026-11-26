/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.cast

import android.app.Application
import android.os.Looper
import java.io.IOException
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.NoHandle
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.ReceiverError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.R
import org.schabi.newpipe.cast.CastLoadDiagnostics.SourceKind
import org.schabi.newpipe.error.ErrorInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class CastLoadDiagnosticsTest {
    @Test
    fun reportsOnlyAllowlistedProtocolSourceAndFailureMetadata() {
        val reports = mutableListOf<ErrorInfo>()
        val diagnostics = CastLoadDiagnostics(ProtocolType.CHROMECAST, SourceKind.HLS, "application/vnd.apple.mpegurl; token=secret", 0) { report, _ -> reports += report }
        diagnostics.connectionFailed(IOException("https://media.test/watch?token=secret Authorization=secret 192.168.1.10"))

        val report = reports.single()
        val text = report.request + report.stackTraces.joinToString()
        assertTrue(report.isReportable)
        assertEquals(0, report.serviceId)
        assertTrue(text.contains("protocol=CHROMECAST source=HLS mime=HLS stage=CONNECT failure=IO_ERROR"))
        assertTrue(text.length < 512)
        listOf("https://", "secret", "Authorization", "192.168").forEach { assertFalse(text.contains(it)) }
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.cast_connection_report_failed), report.getMessage(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun deduplicatesAndBoundsFailuresUntilTheNextLoadAttempt() {
        val reports = mutableListOf<ErrorInfo>()
        val diagnostics = CastLoadDiagnostics(ProtocolType.F_CAST, SourceKind.AUDIO, "untrusted-mime-secret", 0) { report, _ -> reports += report }
        repeat(5) { diagnostics.commandFailed(ReceiverError.UNSUPPORTED_FORMAT) }
        repeat(5) { diagnostics.playbackFailed() }
        diagnostics.loadFailed(IOException("secret"))
        diagnostics.connectionFailed(IllegalStateException("secret"))
        assertEquals(3, reports.size)
        assertTrue(reports.first().request.contains("mime=OTHER"))
        assertTrue(reports.first().request.contains("stage=COMMAND failure=UNSUPPORTED_FORMAT"))

        diagnostics.beginLoad()
        diagnostics.commandFailed(ReceiverError.UNSUPPORTED_FORMAT)
        assertEquals(4, reports.size)
    }

    @Test
    fun actualLoadFailuresRemainReportableOnRetryAndNeverConfirmLocalHandoff() {
        Fixture().use { f ->
            f.device.failure = IOException("https://media.test/?token=secret")
            f.handler.connectionStateChanged(f.connected)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, f.device.loads.size)
            assertEquals(1, f.reports.size)
            assertTrue(f.reports.single().request.contains("stage=LOAD failure=IO_ERROR"))
            assertEquals(0, f.confirmations)

            f.handler.playbackStateChanged(PlaybackState.PLAYING)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, f.confirmations)
            f.handler.connectionStateChanged(f.connected)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, f.reports.size)
        }
    }

    @Test
    fun receiverErrorsAreVisibleWithoutCopyingReceiverTextOrPausingThePhone() {
        Fixture().use { f ->
            f.handler.commandError(ReceiverError.UNSUPPORTED_FORMAT)
            f.handler.commandError(ReceiverError.UNSUPPORTED_FORMAT)
            f.handler.playbackError("receiver-secret https://media.test/?token=secret")
            f.handler.playbackStateChanged(PlaybackState.PLAYING)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, f.reports.size)
            assertEquals(0, f.confirmations)
            assertTrue(f.reports.first().request.contains("failure=UNSUPPORTED_FORMAT"))
            assertTrue(f.reports.last().request.contains("failure=RECEIVER_PLAYBACK_ERROR"))
            f.reports.forEach { report ->
                assertFalse((report.request + report.stackTraces.joinToString()).contains("secret"))
            }
        }
    }

    @Test
    fun aSuccessfulLoadStillWaitsForPlayingBeforePausingLocalPlaybackOnce() {
        Fixture().use { f ->
            f.handler.connectionStateChanged(f.connected)
            shadowOf(Looper.getMainLooper()).idle()
            val request = f.device.loads.single() as LoadRequest.Video
            assertEquals("https://media.test/?token=secret", request.url)
            assertEquals(12.5, request.resumePosition!!, 0.0)
            assertEquals(0, f.confirmations)
            f.handler.playbackStateChanged(PlaybackState.BUFFERING)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, f.confirmations)

            repeat(2) { f.handler.playbackStateChanged(PlaybackState.PLAYING) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, f.confirmations)
            assertTrue(f.reports.isEmpty())
        }
    }

    @Test
    fun callbacksFromAnOldReceiverCannotReportOrConfirmHandoff() {
        Fixture().use { f ->
            f.setActiveDevice(null)
            f.handler.commandError(ReceiverError.RESOURCE_NOT_FOUND)
            f.handler.playbackError("stale error")
            f.handler.playbackStateChanged(PlaybackState.PLAYING)
            f.handler.connectionStateChanged(f.connected)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(f.reports.isEmpty())
            assertTrue(f.device.loads.isEmpty())
            assertEquals(0, f.confirmations)
        }
    }

    private class Fixture : AutoCloseable {
        val device = FakeDevice()
        val reports = mutableListOf<ErrorInfo>()
        var confirmations = 0
        private val activeDevice = FCastManager::class.java.getDeclaredField("activeDevice").apply { isAccessible = true }
        private val activePlaying = FCastManager::class.java.getDeclaredField("activePlaying").apply { isAccessible = true }
        private val previousDevice = activeDevice.get(null)
        private val previousPlaying = activePlaying.get(null)
        private val address = IpAddr.V4(127u, 0u, 0u, 1u)
        val connected = DeviceConnectionState.Connected(address, address, null)
        val handler = FCastManager.playbackHandler(
            RuntimeEnvironment.getApplication(),
            device,
            DeviceInfo("Private TV name", ProtocolType.CHROMECAST, listOf(address), 8009u, emptyMap()),
            FCastManager.CastSource("https://media.test/?token=secret", "application/vnd.apple.mpegurl", SourceKind.HLS, 0),
            PlaybackHandoff({ 12.5 }) { confirmations++ },
            CastLoadDiagnostics(ProtocolType.CHROMECAST, SourceKind.HLS, "application/vnd.apple.mpegurl", 0) { report, _ -> reports += report }
        )

        init {
            setActiveDevice(device)
        }

        fun setActiveDevice(device: CastingDevice?) = activeDevice.set(null, device)

        override fun close() {
            shadowOf(Looper.getMainLooper()).idle()
            activeDevice.set(null, previousDevice)
            activePlaying.set(null, previousPlaying)
            device.close()
        }
    }

    /** The SDK's explicit test constructor avoids loading or calling its native transport. */
    private class FakeDevice : CastingDevice(NoHandle) {
        val loads = mutableListOf<LoadRequest>()
        var failure: IOException? = null

        override fun load(request: LoadRequest, progressUpdateIntervalMillis: ULong?) {
            loads += request
            failure?.let { throw it }
        }
    }
}
