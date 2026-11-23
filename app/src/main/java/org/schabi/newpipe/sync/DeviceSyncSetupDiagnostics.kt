/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.net.BindException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import kotlinx.serialization.Serializable

@Serializable
enum class DeviceSyncSetupOperation {
    CREATE_INVITATION,
    PAIR,
    START_LISTENER
}

/** Local diagnostic codes, never exception messages or values from a remote device. */
@Serializable
enum class DeviceSyncSetupFailure {
    INVALID_CODE,
    EXPIRED_OR_USED,
    CLOCK_MISMATCH,
    SAME_DEVICE,
    NO_LOCAL_ADDRESS,
    UNREACHABLE,
    TIMEOUT,
    LISTENER_UNAVAILABLE,
    REJECTED,
    UNKNOWN
}

@Serializable
data class DeviceSyncSetupDiagnostic(
    val operation: DeviceSyncSetupOperation,
    val succeeded: Boolean,
    val failure: DeviceSyncSetupFailure? = null,
    val localAppVersion: SyncAppVersion? = null
)

/** Records setup outcomes independently of a full synchronization attempt. */
internal class DeviceSyncSetupDiagnostics(
    private val localAppVersion: SyncAppVersion,
    private val record: (DeviceSyncSetupDiagnostic) -> Unit
) {
    fun <T> run(operation: DeviceSyncSetupOperation, action: () -> T): T {
        val result = try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recordSafely(
                DeviceSyncSetupDiagnostic(
                    operation = operation,
                    succeeded = false,
                    failure = classify(error),
                    localAppVersion = localAppVersion
                )
            )
            throw error
        }
        // Opening settings often starts an already running listener. Keep the useful failures.
        if (operation != DeviceSyncSetupOperation.START_LISTENER) {
            recordSafely(
                DeviceSyncSetupDiagnostic(
                    operation = operation,
                    succeeded = true,
                    localAppVersion = localAppVersion
                )
            )
        }
        return result
    }

    private fun recordSafely(diagnostic: DeviceSyncSetupDiagnostic) {
        try {
            record(diagnostic)
        } catch (_: Exception) {
            // A diagnostics storage failure must not change a completed pair or hide its cause.
        }
    }

    companion object {
        private const val MAX_CAUSE_DEPTH = 8

        fun classify(error: Throwable): DeviceSyncSetupFailure {
            // Bound traversal even for cyclic exception causes. Never retain or render messages.
            val causes = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
            if (causes.any { it is BindException }) {
                return DeviceSyncSetupFailure.LISTENER_UNAVAILABLE
            }
            if (causes.any { it is TimeoutException || it is SocketTimeoutException }) {
                return DeviceSyncSetupFailure.TIMEOUT
            }
            if (causes.any { it is ConnectException || it is NoRouteToHostException }) {
                return DeviceSyncSetupFailure.UNREACHABLE
            }
            for (cause in causes) {
                // Exact allowlisted messages may come from the existing verifier or peer.
                // Unexpected messages (including nested causes) become UNKNOWN, without text.
                val failure = when (cause.message) {
                    "The pairing invitation has expired",
                    "The pairing invitation is expired or was already used" ->
                        DeviceSyncSetupFailure.EXPIRED_OR_USED

                    "The pairing invitation is not valid yet",
                    "The pairing request is stale" -> DeviceSyncSetupFailure.CLOCK_MISMATCH

                    "This pairing code belongs to this device" -> DeviceSyncSetupFailure.SAME_DEVICE

                    "At least one network address is required",
                    "The synchronization listener has no usable TCP address" -> DeviceSyncSetupFailure.NO_LOCAL_ADDRESS

                    "Could not reach the other device" -> DeviceSyncSetupFailure.UNREACHABLE

                    "Device synchronization is not running" -> DeviceSyncSetupFailure.LISTENER_UNAVAILABLE

                    "The other device rejected pairing" -> DeviceSyncSetupFailure.REJECTED

                    "The pairing code is empty",
                    "The pairing code is too large",
                    "This is not a WizeStream pairing code",
                    "The pairing code has no invitation",
                    "This is not a valid pairing code",
                    "The pairing invitation is malformed",
                    "The pairing invitation has an invalid lifetime",
                    "The pairing invitation token has an invalid length",
                    "The invitation token is invalid",
                    "The pairing signature is invalid",
                    "The signature is invalid",
                    "The device name is invalid",
                    "The PeerID is invalid",
                    "The remote PeerID is invalid",
                    "The public key is invalid",
                    "The public key does not belong to the advertised PeerID",
                    "The connected device identity does not match its request",
                    "The network addresses are invalid",
                    "The remote network addresses are invalid",
                    "Too many network addresses",
                    "A network address is too long",
                    "Every network address must contain the advertised PeerID" ->
                        DeviceSyncSetupFailure.INVALID_CODE

                    else -> null
                }
                if (failure != null) return failure
            }
            return DeviceSyncSetupFailure.UNKNOWN
        }
    }
}
