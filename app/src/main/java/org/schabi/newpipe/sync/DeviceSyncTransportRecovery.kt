/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

internal class DeviceSyncTransportAttempt<T>(
    val peer: TrustedPeer,
    val result: Result<T>,
    val retried: Boolean,
    val rediscovered: Boolean
) {
    val retryDiagnostic: String?
        get() = when {
            !retried -> null

            result.isFailure &&
                DeviceSyncTransportRecovery.shouldRetryTransportFailure(
                    result.exceptionOrNull()
                ) -> {
                if (rediscovered) {
                    "Peer rediscovered; synchronization still timed out or lost its connection"
                } else {
                    "Synchronization still timed out or lost its connection after discovery and retry"
                }
            }

            rediscovered -> "Connection lost; rediscovered peer and retried once"

            else -> "Connection lost; retried once using saved peer addresses"
        }
}

internal object DeviceSyncTransportRecovery {
    fun <T> run(
        peer: TrustedPeer,
        refreshPeer: (TrustedPeer) -> TrustedPeer?,
        operation: (TrustedPeer) -> T
    ): DeviceSyncTransportAttempt<T> {
        var activePeer = peer
        var result = runCatching {
            operation(activePeer)
        }

        if (!shouldRetryTransportFailure(result.exceptionOrNull())) {
            return DeviceSyncTransportAttempt(
                peer = activePeer,
                result = result,
                retried = false,
                rediscovered = false
            )
        }

        val refreshedPeer = runCatching {
            refreshPeer(activePeer)
        }.getOrNull()
        if (refreshedPeer != null) {
            activePeer = refreshedPeer
        }

        result = runCatching {
            operation(activePeer)
        }
        return DeviceSyncTransportAttempt(
            peer = activePeer,
            result = result,
            retried = true,
            rediscovered = refreshedPeer != null
        )
    }

    fun shouldRetryTransportFailure(error: Throwable?): Boolean {
        if (error == null) {
            return false
        }

        return generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .any { cause ->
                cause.javaClass.name in TRANSIENT_EXCEPTION_CLASSES ||
                    cause.message.isTransientTransportMessage()
            }
    }

    private fun String?.isTransientTransportMessage(): Boolean {
        val message = this?.lowercase() ?: return false
        return message.startsWith("could not reach ") ||
            "connection closed" in message ||
            "channel closed" in message ||
            "closedchannelexception" in message ||
            "total timeout" in message ||
            "network is unreachable" in message ||
            "no route to host" in message ||
            "software caused connection abort" in message ||
            "connection reset" in message ||
            "broken pipe" in message ||
            "connection refused" in message
    }

    private const val MAX_CAUSE_DEPTH = 8

    private val TRANSIENT_EXCEPTION_CLASSES = setOf(
        "java.nio.channels.ClosedChannelException",
        "java.util.concurrent.TimeoutException",
        "io.libp2p.core.ConnectionClosedException",
        "io.libp2p.etc.util.netty.TotalTimeoutException"
    )
}
