/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.sync

import io.libp2p.core.Host
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses real Noise-authenticated loopback streams, not a mocked request dispatcher. */
class RemotePlaybackNodeTest {
    @Test
    fun pairedDevicesCanSendRemotePlaybackWithoutSharingMediaBytes() {
        val receiverState = MemoryState()
        val senderState = MemoryState()
        var senderSeen: String? = null
        val receiver = node(receiverState, "Receiver") { peerId, request ->
            senderSeen = peerId
            RemotePlaybackResponse(
                accepted = true,
                requestId = request.requestId,
                status = RemotePlaybackStatus(
                    playing = true,
                    title = request.title.orEmpty(),
                    positionMillis = request.positionMillis ?: 0
                )
            )
        }
        val sender = node(senderState, "Sender") { _, request ->
            RemotePlaybackResponse(false, request.requestId, error = "Not receiving")
        }
        try {
            receiver.start()
            sender.start()
            val paired = sender.pair(receiver.createPairingCode())
            val request = RemotePlaybackRequest(
                requestId = UUID.randomUUID().toString(),
                action = RemotePlaybackAction.OPEN,
                serviceId = 0,
                url = "https://www.youtube.com/watch?v=abcdefghijk",
                title = "Test video",
                positionMillis = 24_000
            )
            val response = sender.sendRemotePlayback(paired, request)
            assertTrue(response.accepted)
            assertEquals(sender.peerId.toBase58(), senderSeen)
            assertEquals(24_000L, response.status?.positionMillis)
            assertEquals("Test video", response.status?.title)

            receiverState.clearTrustedPeers()
            assertThrows(RemotePlaybackException::class.java) {
                sender.sendRemotePlayback(
                    paired,
                    RemotePlaybackRequest(
                        requestId = UUID.randomUUID().toString(),
                        action = RemotePlaybackAction.PAUSE
                    )
                )
            }
        } finally {
            sender.stop()
            receiver.stop()
        }
    }

    private fun node(
        state: MemoryState,
        name: String,
        onPlayback: (String, RemotePlaybackRequest) -> RemotePlaybackResponse
    ) = Libp2pSyncNode(
        stateRepository = state,
        pairingSecurity = PairingSecurity(),
        deviceName = name,
        advertisedAddressProvider = { host: Host ->
            host.listenAddresses().map { address ->
                address.toString().replace("/ip4/0.0.0.0/", "/ip4/127.0.0.1/")
            }
        },
        listenAddress = "/ip4/127.0.0.1/tcp/0",
        onRemotePlaybackReceived = onPlayback
    )

    private class MemoryState : SyncStateRepository {
        private val identity = DeviceIdentity(generateKeyPair(KeyType.ED25519).first)
        private val peers = linkedMapOf<String, TrustedPeer>()

        override fun loadOrCreateIdentity(): DeviceIdentity = identity

        @Synchronized
        override fun getTrustedPeers(): List<TrustedPeer> = peers.values.toList()

        @Synchronized
        override fun saveTrustedPeer(peer: TrustedPeer) {
            peers[peer.peerId] = peer
        }

        @Synchronized
        override fun updateTrustedPeerSyncStatus(
            peerId: String,
            syncedAtEpochMillis: Long?,
            error: String?
        ) = Unit

        @Synchronized
        override fun clearTrustedPeers() {
            peers.clear()
        }
    }
}
