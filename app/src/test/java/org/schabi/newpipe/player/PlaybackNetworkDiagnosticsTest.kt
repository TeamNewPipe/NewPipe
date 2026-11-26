/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.media3.common.PlaybackException
import androidx.preference.PreferenceManager
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.MainActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaybackNetworkDiagnosticsTest {
    @Test
    fun cellularFailureContextDistinguishesRestrictionFromObservedBlocking() {
        val f = Fixture()
        PlaybackNetworkDiagnostics(f.context).use { diagnostics ->
            val initial = diagnostics.snapshot(PlaybackPresentationMode.AUDIO_BACKGROUND, true)
            assertEquals(PlaybackNetworkDiagnostics.Transport.CELLULAR, initial.transport)
            assertEquals(PlaybackNetworkDiagnostics.DataSaver.RESTRICTED, initial.dataSaver)
            assertEquals(true, initial.metered)
            assertEquals(true, initial.activityBackground)
            assertEquals(false, initial.interactive)
            assertNull(initial.blocked)

            f.callback!!.onAvailable(f.network)
            assertNull(diagnostics.snapshot(PlaybackPresentationMode.VIDEO, true).blocked)
            f.callback!!.onBlockedStatusChanged(f.network, true)
            assertEquals(true, diagnostics.snapshot(PlaybackPresentationMode.VIDEO, true).blocked)
            f.callback!!.onBlockedStatusChanged(f.network, false)
            assertEquals(false, diagnostics.snapshot(PlaybackPresentationMode.VIDEO, true).blocked)
        }
    }

    @Test
    fun changedNetworksAndReleasedCallbacksCannotReuseStaleBlockedEvidence() {
        val f = Fixture()
        val diagnostics = PlaybackNetworkDiagnostics(f.context)
        val callback = requireNotNull(f.callback)
        callback.onAvailable(f.network)
        callback.onBlockedStatusChanged(f.network, true)
        val replacement = mock(Network::class.java)
        `when`(f.connectivity.activeNetwork).thenReturn(replacement)
        callback.onAvailable(replacement)
        callback.onBlockedStatusChanged(f.network, false)
        callback.onLost(f.network)
        assertNull(diagnostics.snapshot(PlaybackPresentationMode.VIDEO, true).blocked)
        callback.onBlockedStatusChanged(replacement, true)
        assertEquals(true, diagnostics.snapshot(PlaybackPresentationMode.VIDEO, true).blocked)

        diagnostics.close()
        diagnostics.close()
        callback.onAvailable(replacement)
        callback.onBlockedStatusChanged(replacement, true)
        assertNull(diagnostics.snapshot(PlaybackPresentationMode.VIDEO, true).blocked)
        verify(f.connectivity, times(1)).unregisterNetworkCallback(callback)
    }

    @Test
    @Config(sdk = [23])
    fun androidSixReportsUnsupportedAndUnknownStatesWithoutNewerApiCalls() {
        val f = Fixture()
        PlaybackNetworkDiagnostics(f.context).use { diagnostics ->
            val snapshot = diagnostics.snapshot(PlaybackPresentationMode.AUDIO_BACKGROUND, true)
            assertEquals(PlaybackNetworkDiagnostics.DataSaver.UNSUPPORTED, snapshot.dataSaver)
            assertNull(snapshot.backgroundRestricted)
            assertNull(snapshot.blocked)
            assertNull(f.callback)
        }
    }

    @Test
    fun missingPermissionsAndServiceFailuresProduceUnknownRatherThanOffline() {
        val f = Fixture()
        `when`(f.connectivity.activeNetwork).thenThrow(SecurityException("private network detail"))
        `when`(f.connectivity.isActiveNetworkMetered).thenThrow(SecurityException("private network detail"))
        `when`(f.connectivity.restrictBackgroundStatus).thenThrow(SecurityException("private network detail"))
        PlaybackNetworkDiagnostics(f.context).use { diagnostics ->
            val snapshot = diagnostics.snapshot(PlaybackPresentationMode.VIDEO, true)
            assertNull(snapshot.available)
            assertEquals(PlaybackNetworkDiagnostics.Transport.UNKNOWN, snapshot.transport)
            assertEquals(PlaybackNetworkDiagnostics.DataSaver.UNKNOWN, snapshot.dataSaver)
            assertFalse(snapshot.describe().contains("private"))
        }
    }

    @Test
    fun diagnosticsContainOnlyBoundedCoarseValuesAndNoNetworkOrExceptionText() {
        val f = Fixture()
        `when`(f.network.toString()).thenReturn("192.168.1.20 https://example.test/?token=secret")
        `when`(f.capabilities.toString()).thenReturn("SSID=private-name Authorization=secret")
        PlaybackNetworkDiagnostics(f.context).use { diagnostics ->
            val description = diagnostics.snapshot(PlaybackPresentationMode.AUDIO_BACKGROUND, true).describe()
            assertTrue(description.length < 512)
            listOf("192.168", "https://", "secret", "SSID", "Authorization", "private-name").forEach {
                assertFalse(description.contains(it))
            }
            assertTrue(description.contains("blocked=unknown"))
        }
    }

    @Test
    fun onlyNetworkOrManifestFailuresRequestNetworkContext() {
        assertTrue(PlaybackNetworkDiagnostics.isRelevant(PlaybackException("DNS", UnknownHostException("signed-url-secret"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
        assertTrue(PlaybackNetworkDiagnostics.isRelevant(PlaybackException("manifest", null, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED)))
        assertFalse(PlaybackNetworkDiagnostics.isRelevant(PlaybackException("decoder", null, PlaybackException.ERROR_CODE_DECODING_FAILED)))
    }

    private class Fixture {
        val connectivity = mock(ConnectivityManager::class.java)
        val network = mock(Network::class.java)
        val capabilities = mock(NetworkCapabilities::class.java)
        private val power = mock(PowerManager::class.java)
        private val activity = mock(ActivityManager::class.java)
        var callback: ConnectivityManager.NetworkCallback? = null
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = when (name) {
                Context.CONNECTIVITY_SERVICE -> connectivity
                Context.POWER_SERVICE -> power
                Context.ACTIVITY_SERVICE -> activity
                else -> super.getSystemService(name)
            }
        }

        init {
            `when`(connectivity.activeNetwork).thenReturn(network)
            `when`(connectivity.getNetworkCapabilities(network)).thenReturn(capabilities)
            `when`(connectivity.isActiveNetworkMetered).thenReturn(true)
            `when`(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true)
            `when`(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                `when`(connectivity.restrictBackgroundStatus).thenReturn(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)
                doAnswer {
                    callback = it.getArgument(0)
                    null
                }.`when`(connectivity).registerDefaultNetworkCallback(any(ConnectivityManager.NetworkCallback::class.java))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) `when`(activity.isBackgroundRestricted).thenReturn(true)
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(MainActivity.KEY_IS_IN_BACKGROUND, true).commit()
        }
    }
}
