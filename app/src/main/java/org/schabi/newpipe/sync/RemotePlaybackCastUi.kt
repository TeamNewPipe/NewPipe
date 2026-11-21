/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.sync

import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.ui.PlayerPopupMenu

/**
 * Sender UI runs in the player overlay (which can be hosted by a service or a desktop window).
 * The actual network requests run on IO, never on Media3's UI thread.
 */
object RemotePlaybackCastUi {
    private val scope = CoroutineScope(Dispatchers.IO)

    @JvmStatic
    fun show(player: Player, anchor: View) {
        val peers = DeviceSyncManager.get(anchor.context).trustedPeers
        if (peers.isEmpty()) {
            Toast.makeText(anchor.context, R.string.device_cast_no_peers, Toast.LENGTH_LONG).show()
            return
        }
        val dialog = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(anchor))
            .setTitle(R.string.device_cast_choose)
            .setItems(peers.map { it.deviceName }.toTypedArray()) { _, index ->
                showControls(player, anchor, peers[index])
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        showAttached(dialog, anchor)
    }

    private fun showControls(player: Player, anchor: View, peer: TrustedPeer) {
        if (!anchor.isAttachedToWindow) return
        val titles = intArrayOf(
            R.string.device_cast_send,
            R.string.device_cast_play,
            R.string.device_cast_pause,
            R.string.device_cast_seek_back,
            R.string.device_cast_seek_forward,
            R.string.device_cast_previous,
            R.string.device_cast_next,
            R.string.device_cast_stop,
            R.string.device_cast_status
        )
        val choices = titles.map(anchor.context::getString).toTypedArray()
        val dialog = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(anchor))
            .setTitle(anchor.context.getString(R.string.device_cast_commands, peer.deviceName))
            .setItems(choices) { _, index -> dispatch(player, anchor, peer, index) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        showAttached(dialog, anchor)
    }

    private fun dispatch(player: Player, anchor: View, peer: TrustedPeer, index: Int) {
        val currentItem = player.currentItem
        val castable = currentItem != null &&
            !currentItem.isLocalMedia &&
            currentItem.url.startsWith("https://", ignoreCase = true)
        if (index == 0 && !castable) {
            Toast.makeText(anchor.context, R.string.device_cast_no_video, Toast.LENGTH_LONG).show()
            return
        }
        val urlAtSend = currentItem?.url
        val request = when (index) {
            0 -> RemotePlaybackRequest(
                requestId = requestId(),
                action = RemotePlaybackAction.OPEN,
                serviceId = currentItem!!.serviceId,
                url = currentItem.url,
                title = currentItem.title.take(256),
                positionMillis = (player.getExoPlayer()?.currentPosition ?: 0).coerceAtLeast(0)
            )

            1 -> command(RemotePlaybackAction.PLAY)

            2 -> command(RemotePlaybackAction.PAUSE)

            5 -> command(RemotePlaybackAction.PREVIOUS)

            6 -> command(RemotePlaybackAction.NEXT)

            7 -> command(RemotePlaybackAction.STOP)

            else -> command(RemotePlaybackAction.STATUS)
        }
        val context = anchor.context.applicationContext
        scope.launch {
            val result = runCatching {
                val manager = DeviceSyncManager.get(context)
                if (index in 3..4) {
                    val previous = manager.remotePlayback(peer, command(RemotePlaybackAction.STATUS))
                        .status ?: error("The receiver has no playback position")
                    val delta = if (index == 3) -10_000L else 10_000L
                    val shifted = (previous.positionMillis + delta).coerceAtLeast(0)
                    val destination = if (previous.durationMillis > 0) {
                        shifted.coerceAtMost(previous.durationMillis)
                    } else {
                        shifted
                    }
                    manager.remotePlayback(
                        peer,
                        RemotePlaybackRequest(
                            requestId = requestId(),
                            action = RemotePlaybackAction.SEEK,
                            positionMillis = destination
                        )
                    )
                } else {
                    manager.remotePlayback(peer, request)
                }
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { response ->
                    if (index == 0 && player.currentItem?.url == urlAtSend) {
                        player.pause()
                    }
                    val message = if (index == 8 && response.status != null) {
                        val status = response.status
                        val stateLabel = if (status.playing) {
                            R.string.device_cast_playing
                        } else {
                            R.string.device_cast_paused
                        }
                        context.getString(
                            R.string.device_cast_status_summary,
                            status.title,
                            context.getString(stateLabel),
                            (status.positionMillis / 1_000).toInt()
                        )
                    } else {
                        context.getString(R.string.device_cast_sent)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.device_cast_error,
                            error.message ?: context.getString(R.string.general_error)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun command(action: RemotePlaybackAction): RemotePlaybackRequest {
        return RemotePlaybackRequest(requestId = requestId(), action = action)
    }

    private fun requestId(): String = UUID.randomUUID().toString()

    private fun showAttached(dialog: AlertDialog, anchor: View) {
        val attributes = dialog.window?.attributes
        if (attributes != null) {
            attributes.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            attributes.token = anchor.windowToken
            dialog.window?.attributes = attributes
        }
        runCatching { dialog.show() }
    }
}
