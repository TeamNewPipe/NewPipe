/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerIntentType
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.NavigationHelper

/**
 * The listener worker never touches Media3 directly. Every command rechecks consent on the main
 * thread, after the authenticated libp2p peer has been checked in Libp2pSyncNode.
 */
internal object RemotePlaybackReceiver {
    fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            context.getString(R.string.device_sync_remote_playback_key),
            false
        )
    }

    fun handle(context: Context, request: RemotePlaybackRequest): RemotePlaybackResponse {
        if (!isEnabled(context)) {
            return reject(request, "Remote playback is disabled on this device")
        }
        val task = FutureTask {
            runCatching { execute(context, request) }
                .getOrElse { reject(request, "Could not carry out the playback command") }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run()
        } else {
            Handler(Looper.getMainLooper()).post(task)
        }
        return runCatching { task.get(6, TimeUnit.SECONDS) }
            .getOrElse { reject(request, "The remote player did not respond") }
    }

    private fun execute(context: Context, request: RemotePlaybackRequest): RemotePlaybackResponse {
        if (!isEnabled(context)) return reject(request, "Remote playback is disabled")
        RemotePlaybackValidation.validateRequest(request)
        val player = PlayerHolder.getInstance().player.orElse(null)
        when (request.action) {
            RemotePlaybackAction.OPEN -> {
                val url = requireNotNull(request.url)
                val service = requireNotNull(request.serviceId)
                check(NewPipe.getServiceByUrl(url).serviceId == service) {
                    "The receiver does not support this service link"
                }
                val video = StreamInfo(service, url, url, requireNotNull(request.title)).apply {
                    streamType = StreamType.VIDEO_STREAM
                    startPosition = (request.positionMillis ?: 0) / 1_000
                }
                val queue = SinglePlayQueue(video)
                val intent = NavigationHelper.getPlayerIntent(
                    context,
                    PlayerService::class.java,
                    queue,
                    PlayerIntentType.AllOthers
                ).putExtra(Player.RESUME_PLAYBACK, true)
                ContextCompat.startForegroundService(context, intent)
            }

            RemotePlaybackAction.PLAY -> (player ?: error("No player active")).play()

            RemotePlaybackAction.PAUSE -> (player ?: error("No player active")).pause()

            RemotePlaybackAction.SEEK -> {
                val current = player ?: error("No player active")
                check(current.getExoPlayer()?.isCurrentMediaItemSeekable == true) {
                    "The current video is not seekable"
                }
                current.seekTo(requireNotNull(request.positionMillis))
            }

            RemotePlaybackAction.NEXT -> (player ?: error("No player active")).playNext()

            RemotePlaybackAction.PREVIOUS -> (player ?: error("No player active")).playPrevious()

            RemotePlaybackAction.STOP -> PlayerHolder.getInstance().stopService()

            RemotePlaybackAction.STATUS -> Unit
        }
        val status = if (request.action == RemotePlaybackAction.OPEN) {
            RemotePlaybackStatus(
                playing = false,
                title = request.title.orEmpty(),
                positionMillis = request.positionMillis ?: 0
            )
        } else {
            playerStatus(PlayerHolder.getInstance().player.orElse(null))
        }
        return RemotePlaybackResponse(
            accepted = true,
            requestId = request.requestId,
            status = status
        )
    }

    private fun playerStatus(player: Player?): RemotePlaybackStatus {
        val engine = player?.getExoPlayer()
        return RemotePlaybackStatus(
            playing = player?.isPlaying == true,
            title = player?.currentItem?.title.orEmpty().take(256),
            positionMillis = (engine?.currentPosition ?: 0).coerceAtLeast(0),
            durationMillis = (engine?.duration ?: 0).coerceAtLeast(0)
        )
    }

    private fun reject(request: RemotePlaybackRequest, reason: String): RemotePlaybackResponse {
        return RemotePlaybackResponse(accepted = false, requestId = request.requestId, error = reason)
    }
}
