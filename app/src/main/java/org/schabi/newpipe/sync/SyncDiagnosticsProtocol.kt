/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// jvm-libp2p's CompletableFuture API is backported by coreLibraryDesugaring on Android.
@file:Suppress("NewApi")

package org.schabi.newpipe.sync

import io.libp2p.core.PeerId
import io.libp2p.core.Stream
import io.libp2p.core.multistream.StrictProtocolBinding
import io.libp2p.etc.types.toByteBuf
import io.libp2p.protocol.ProtocolHandler
import io.libp2p.protocol.ProtocolMessageHandler
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal typealias SyncDiagnosticsRequestHandler =
    (PeerId, SyncDiagnosticsRequest, SyncDiagnosticsProtocolController) -> Unit

internal class SyncDiagnosticsProtocolBinding(
    requestHandler: SyncDiagnosticsRequestHandler
) : StrictProtocolBinding<SyncDiagnosticsProtocolController>(
    SYNC_DIAGNOSTICS_PROTOCOL_ID,
    SyncDiagnosticsProtocol(requestHandler)
)

internal class SyncDiagnosticsProtocol(
    private val requestHandler: SyncDiagnosticsRequestHandler
) : ProtocolHandler<SyncDiagnosticsProtocolController>(
    MAX_PROTOCOL_BYTES,
    MAX_PROTOCOL_BYTES
) {
    override fun onStartInitiator(
        stream: Stream
    ): CompletableFuture<SyncDiagnosticsProtocolController> {
        return start(stream, true)
    }

    override fun onStartResponder(
        stream: Stream
    ): CompletableFuture<SyncDiagnosticsProtocolController> {
        return start(stream, false)
    }

    private fun start(
        stream: Stream,
        initiator: Boolean
    ): CompletableFuture<SyncDiagnosticsProtocolController> {
        val ready = CompletableFuture<Void>()
        val controller = SyncDiagnosticsProtocolController(
            stream,
            initiator,
            requestHandler,
            ready
        )
        stream.pushHandler(controller)
        return ready.thenApply { controller }
    }

    companion object {
        private const val MAX_PROTOCOL_BYTES = 32 * 1024L
    }
}
internal class SyncDiagnosticsProtocolController(
    private val stream: Stream,
    private val initiator: Boolean,
    private val requestHandler: SyncDiagnosticsRequestHandler,
    private val ready: CompletableFuture<Void>
) : ProtocolMessageHandler<ByteBuf> {
    val response = CompletableFuture<SyncDiagnosticsResponse>()
    private var pendingBytes = ByteArray(0)
    private var handledFrame = false

    override fun onActivated(stream: Stream) {
        ready.complete(null)
    }

    override fun onMessage(stream: Stream, msg: ByteBuf) {
        try {
            val received = ByteArray(msg.readableBytes())
            msg.readBytes(received)
            pendingBytes += received
            while (pendingBytes.size >= FRAME_LENGTH_BYTES) {
                val frameLength = ByteBuffer.wrap(
                    pendingBytes,
                    0,
                    FRAME_LENGTH_BYTES
                ).int
                if (frameLength !in 1..MAX_FRAME_BYTES) {
                    throw SyncDiagnosticsException(
                        "The sync diagnostics message has an invalid length"
                    )
                }
                val totalFrameLength = FRAME_LENGTH_BYTES + frameLength
                if (pendingBytes.size < totalFrameLength) {
                    return
                }
                if (handledFrame) {
                    throw SyncDiagnosticsException(
                        "The sync diagnostics stream sent an unexpected extra message"
                    )
                }
                val frame = pendingBytes.copyOfRange(
                    FRAME_LENGTH_BYTES,
                    totalFrameLength
                )
                pendingBytes = pendingBytes.copyOfRange(
                    totalFrameLength,
                    pendingBytes.size
                )
                handledFrame = true
                handleFrame(stream.remotePeerId(), frame)
            }
        } catch (error: Exception) {
            handleFailure(error)
        }
    }

    fun sendRequest(request: SyncDiagnosticsRequest) {
        check(initiator) { "Only the stream initiator can send a sync diagnostics request" }
        send(SyncDiagnosticsCodec.encodeRequest(request))
    }

    fun sendResponse(response: SyncDiagnosticsResponse) {
        check(!initiator) { "Only the stream responder can send a sync diagnostics response" }
        send(SyncDiagnosticsCodec.encodeResponse(response))
    }

    fun close() {
        stream.close()
    }

    override fun onClosed(stream: Stream) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                SyncDiagnosticsException("The sync diagnostics connection closed")
            )
        }
    }

    override fun onException(cause: Throwable?) {
        if (initiator && !response.isDone) {
            response.completeExceptionally(
                cause ?: SyncDiagnosticsException("The sync diagnostics connection failed")
            )
        }
    }

    private fun handleFrame(remotePeerId: PeerId, frame: ByteArray) {
        val value = frame.toString(Charsets.UTF_8)
        if (initiator) {
            response.complete(SyncDiagnosticsCodec.decodeResponse(value))
        } else {
            requestHandler(
                remotePeerId,
                SyncDiagnosticsCodec.decodeRequest(value),
                this
            )
        }
    }

    private fun handleFailure(error: Exception) {
        if (initiator) {
            response.completeExceptionally(error)
            return
        }
        runCatching {
            sendResponse(
                SyncDiagnosticsResponse(
                    accepted = false,
                    error = "Malformed sync diagnostics request"
                )
            )
        }
    }

    private fun send(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_BYTES) {
            throw SyncDiagnosticsException("The sync diagnostics message is too large")
        }
        val frame = ByteBuffer.allocate(FRAME_LENGTH_BYTES + bytes.size)
            .putInt(bytes.size)
            .put(bytes)
            .array()
        stream.writeAndFlush(frame.toByteBuf())
    }

    companion object {
        private const val FRAME_LENGTH_BYTES = Int.SIZE_BYTES
        private const val MAX_FRAME_BYTES = 16 * 1024
    }
}

internal object SyncDiagnosticsCodec {
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encodeRequest(request: SyncDiagnosticsRequest): String {
        return json.encodeToString(request)
    }

    fun decodeRequest(value: String): SyncDiagnosticsRequest {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw SyncDiagnosticsException(
                "The sync diagnostics request is malformed",
                error
            )
        }
    }

    fun encodeResponse(response: SyncDiagnosticsResponse): String {
        return json.encodeToString(response)
    }

    fun decodeResponse(value: String): SyncDiagnosticsResponse {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw SyncDiagnosticsException(
                "The sync diagnostics response is malformed",
                error
            )
        }
    }
}
