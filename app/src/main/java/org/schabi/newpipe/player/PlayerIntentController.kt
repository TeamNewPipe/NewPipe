/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.media3.common.Player as Media3Player
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.SerialDisposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.download.DownloadedCopyRepository
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.PlayerUi
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SerializedCache

/** Owns playback intent interpretation and queue/session selection. */
internal class PlayerIntentController(
    private val player: Player,
    private val context: Context,
    private val historyController: PlayerHistoryController,
    private val presentationController: PlayerPresentationController,
    private val popupPlayerReturnState: PopupPlayerReturnState,
    private val videoResolver: VideoPlaybackResolver,
    private val streamItemDisposable: CompositeDisposable,
    private val localTimestamp: (TimestampChangeData, PlayQueueItem?) -> Single<OfflineTimestampTarget> = { data, item ->
        Single.fromCallable { OfflineTimestampTargetResolver.resolve(context, data, item) }
    }
) {
    private var openPlayQueueAfterIntent = false
    private var timestampGeneration = 0L
    private val timestampRequest = SerialDisposable().also { streamItemDisposable.add(it) }

    fun handle(intent: Intent) {
        openPlayQueueAfterIntent = false
        val intentType = IntentCompat.getSerializableExtra(
            intent,
            Player.PLAYER_INTENT_TYPE,
            PlayerIntentType::class.java
        ) ?: return
        timestampGeneration++
        timestampRequest.set(null)

        if (intentType != PlayerIntentType.TimestampChange) {
            historyController.updateLearningSession()
            val requestedType = checkNotNull(
                IntentCompat.getSerializableExtra(
                    intent,
                    Player.PLAYER_TYPE,
                    PlayerType::class.java
                )
            )
            if (player.playerType == PlayerType.MAIN &&
                requestedType == PlayerType.POPUP &&
                !popupPlayerReturnState.isRemembered
            ) {
                popupPlayerReturnState.remember(
                    player.UIs().get(MainPlayerUi::class.java)
                        .map(MainPlayerUi::isFullscreen)
                        .orElse(false)
                )
            }
            player.setPlayerTypeForIntent(requestedType)
            historyController.updateLearningSession()
        }
        player.initUIsForCurrentPlayerType()

        val requestedMode = IntentCompat.getSerializableExtra(
            intent,
            Player.PLAYBACK_PRESENTATION_MODE,
            PlaybackPresentationMode::class.java
        )
        presentationController.updateFromIntent(
            player.audioPlayerSelected(),
            requestedMode,
            intentType != PlayerIntentType.TimestampChange
        )
        if (intent.hasExtra(Player.PLAYBACK_QUALITY)) {
            videoResolver.setPlaybackQuality(intent.getStringExtra(Player.PLAYBACK_QUALITY))
        }
        val playWhenReady = intent.getBooleanExtra(Player.PLAY_WHEN_READY, true)

        when (intentType) {
            PlayerIntentType.Enqueue -> {
                player.playQueue?.let { queue ->
                    val newQueue = playQueueFromCache(intent) ?: return
                    queue.append(newQueue.streams)
                    return
                }
            }

            PlayerIntentType.EnqueueNext -> {
                player.playQueue?.let { queue ->
                    val newQueue = playQueueFromCache(intent) ?: return
                    queue.enqueueNext(newQueue.streams[0], false)
                    return
                }
            }

            PlayerIntentType.TimestampChange -> {
                handleTimestampChange(intent, playWhenReady)
                return
            }

            PlayerIntentType.AllOthers -> Unit
        }

        val newQueue = playQueueFromCache(intent) ?: return
        openPlayQueueAfterIntent = player.playerType == PlayerType.MAIN &&
            (newQueue as? LocalMediaPlayQueue)?.consumeOpenQueueOnStart() == true
        val currentQueue = player.playQueue
        val samePlayQueue =
            currentQueue != null && currentQueue.equalStreamsAndIndex(newQueue)

        if (intent.getBooleanExtra(org.schabi.newpipe.player.recovery.QueueRecovery.RESTORE_EXTRA, false)) {
            player.initPlayback(newQueue, playWhenReady)
        } else if (!player.exoPlayerIsNull() &&
            newQueue.size() == 1 &&
            newQueue.item != null &&
            currentQueue != null &&
            currentQueue.size() == 1 &&
            currentQueue.item != null &&
            newQueue.item!!.isSameItem(currentQueue.item) &&
            newQueue.item!!.recoveryPosition != PlayQueueItem.RECOVERY_UNSET
        ) {
            prepareIfIdle()
            player.channelSkipController.manualSeek(newQueue.item!!.recoveryPosition)
            player.exoPlayer.seekTo(currentQueue.index, newQueue.item!!.recoveryPosition)
            player.exoPlayer.playWhenReady = playWhenReady
        } else if (!player.exoPlayerIsNull() &&
            samePlayQueue &&
            currentQueue != null &&
            !currentQueue.isDisposed
        ) {
            prepareIfIdle()
            player.exoPlayer.playWhenReady = playWhenReady
        } else if (intent.getBooleanExtra(Player.RESUME_PLAYBACK, false) &&
            DependentPreferenceHelper.getResumePlaybackEnabled(context) &&
            (currentQueue == null || !currentQueue.equalStreamsAndIndex(newQueue)) &&
            !newQueue.isEmpty &&
            newQueue.item != null &&
            newQueue.item!!.recoveryPosition == PlayQueueItem.RECOVERY_UNSET
        ) {
            val item = newQueue.item!!
            historyController.restoreStreamState(
                item,
                Consumer { state ->
                    if (!state.isFinished(item.duration)) {
                        newQueue.setRecovery(newQueue.index, state.progressMillis)
                    }
                    player.initPlayback(newQueue, playWhenReady)
                },
                Consumer { error ->
                    if (Player.DEBUG) {
                        Log.w(Player.TAG, "Failed to start playback", error)
                    }
                    player.initPlayback(newQueue, playWhenReady)
                },
                Action { player.initPlayback(newQueue, playWhenReady) }
            )
        } else {
            player.initPlayback(if (samePlayQueue) currentQueue!! else newQueue, playWhenReady)
        }
    }

    fun handlePost(oldPlayerType: PlayerType) {
        if (oldPlayerType != player.playerType && player.playQueue != null) {
            player.reloadPlayQueueManager()
        }
        player.UIs().call(PlayerUi::setupAfterIntent)
        NavigationHelper.sendPlayerStartedEvent(context)
        if (openPlayQueueAfterIntent) {
            openPlayQueueAfterIntent = false
            context.startActivity(
                Intent(context, PlayQueueActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun handleTimestampChange(intent: Intent, playWhenReady: Boolean) {
        val data = requireNotNull(
            IntentCompat.getParcelableExtra(
                intent,
                Player.PLAYER_INTENT_DATA,
                TimestampChangeData::class.java
            )
        )
        if (!timestampProfileMatches(data)) {
            stopEmptyTimestampPlayer()
            return
        }
        val current = player.currentItem
        val queue = player.playQueue
        val selected = queue?.item
        val selectedIndex = queue?.index
        val generation = timestampGeneration
        fun stillCurrent() = generation == timestampGeneration && player.playQueue === queue && player.currentItem === current && queue?.item === selected && queue?.index == selectedIndex
        if (data.localUri != null || data.serviceId == PlayQueueItem.LOCAL_SERVICE_ID) {
            timestampRequest.set(
                localTimestamp(data, current)
                    .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ target ->
                        if (!stillCurrent()) return@subscribe
                        if (!timestampProfileMatches(data)) {
                            stopEmptyTimestampPlayer()
                            return@subscribe
                        }
                        if (target.matchesCurrent && queue != null && selected === current) {
                            seekCurrentTimestamp(data, playWhenReady)
                        } else {
                            applyTimestampItem(target.item, data, playWhenReady)
                        }
                    }, { error -> if (stillCurrent()) timestampError(data, error) })
            )
            return
        }
        if (current != null && selected === current && current.serviceId == data.serviceId &&
            DownloadedCopyRepository.sameSource(data.serviceId, current.url, data.url)
        ) {
            seekCurrentTimestamp(data, playWhenReady)
            return
        }
        val single = DownloadedCopyRepository.streamInfoOrNetwork(
            context,
            data.serviceId,
            data.url,
            null,
            Single.defer { ExtractorHelper.getStreamInfo(data.serviceId, data.url, false) }
        )
        timestampRequest.set(
            single.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { info ->
                        if (stillCurrent()) {
                            if (timestampProfileMatches(data)) applyTimestamp(info, data, playWhenReady) else stopEmptyTimestampPlayer()
                        }
                    },
                    { error -> if (stillCurrent()) timestampError(data, error) }
                )
        )
    }

    private fun applyTimestamp(
        info: StreamInfo,
        data: TimestampChangeData,
        playWhenReady: Boolean
    ) {
        info.startPosition = data.seconds.toLong()
        applyTimestampItem(PlayQueueItem(info), data, playWhenReady)
    }

    private fun applyTimestampItem(item: PlayQueueItem, data: TimestampChangeData, playWhenReady: Boolean) {
        val oldQueue = player.playQueue
        if (oldQueue != null && item.isSameItem(oldQueue.item)) {
            seekCurrentTimestamp(data, playWhenReady)
            return
        }
        val newQueue = if (oldQueue == null) {
            if (item.isLocalMedia) LocalMediaPlayQueue(listOf(item), 0) else SinglePlayQueue(item)
        } else {
            oldQueue.enqueueNext(item, true)
            oldQueue.offsetIndex(1)
            oldQueue
        }
        newQueue.setRecovery(newQueue.index, timestampMillis(data))
        player.initPlayback(newQueue, playWhenReady)
    }

    private fun seekCurrentTimestamp(data: TimestampChangeData, playWhenReady: Boolean) {
        val queue = player.playQueue ?: return
        val position = timestampMillis(data)
        if (player.exoPlayerIsNull() || player.currentItem !== queue.item) {
            queue.setRecovery(queue.index, position)
            player.initPlayback(queue, playWhenReady)
            return
        }
        prepareIfIdle()
        player.channelSkipController.manualSeek(position)
        player.exoPlayer.seekTo(queue.index, position)
        player.exoPlayer.playWhenReady = playWhenReady
    }

    private fun timestampMillis(data: TimestampChangeData) = (data.positionMillis ?: data.seconds * 1000L).coerceAtLeast(0)

    private fun timestampProfileMatches(data: TimestampChangeData) = data.profileId == null || data.profileId == ProfileManager.getActiveProfileId(context)

    private fun timestampError(data: TimestampChangeData, error: Throwable) {
        if (timestampProfileMatches(data)) {
            ErrorUtil.createNotification(context, ErrorInfo(error, UserAction.PLAY_ON_POPUP, data.url, null, data.url))
        }
        stopEmptyTimestampPlayer()
    }

    private fun stopEmptyTimestampPlayer() {
        if (player.playQueue == null && player.exoPlayerIsNull()) player.service.destroyPlayerAndStopService()
    }

    private fun prepareIfIdle() {
        if (player.exoPlayer.playbackState == Media3Player.STATE_IDLE) {
            player.exoPlayer.prepare()
        }
    }

    private fun playQueueFromCache(intent: Intent): PlayQueue? {
        val key = intent.getStringExtra(Player.PLAY_QUEUE_KEY) ?: return null
        return SerializedCache.getInstance().take(key, PlayQueue::class.java)
    }
}
