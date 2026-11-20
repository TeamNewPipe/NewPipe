package org.schabi.newpipe.sync

import java.io.IOException
import java.net.SocketException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncTransportRecoveryTest {
    @Test
    fun retriesReportedPeerTransportFailures() {
        listOf(
            "Network is unreachable",
            "No route to host",
            "Software caused connection abort",
            "Connection reset by peer",
            "Broken pipe",
            "Connection refused",
            "Total timeout"
        ).forEach { message ->
            val retriable = DeviceSyncTransportRecovery.shouldRetryTransportFailure(IOException(message))
            assertTrue(message, retriable)
        }
    }

    @Test
    fun inspectsNestedExceptionsAndKnownSocketFailures() {
        val nested = IllegalStateException(
            "Unable to open history stream",
            SocketException("Network is unreachable")
        )
        assertTrue(DeviceSyncTransportRecovery.shouldRetryTransportFailure(nested))
    }

    @Test
    fun doesNotRetryPermanentProtocolOrAuthenticationFailures() {
        assertFalse(DeviceSyncTransportRecovery.shouldRetryTransportFailure(null))
        val protocolFailure = IllegalStateException("Remote peer rejected an incompatible sync protocol")
        assertFalse(DeviceSyncTransportRecovery.shouldRetryTransportFailure(protocolFailure))
        val authenticationFailure = SecurityException("Pairing signature rejected")
        assertFalse(DeviceSyncTransportRecovery.shouldRetryTransportFailure(authenticationFailure))
    }
}
