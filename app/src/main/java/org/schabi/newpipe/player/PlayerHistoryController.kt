/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.learning.LearningSessionTracker
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.recommendations.RecommendationWatchTracker

/** Owns playback history persistence and active learning-session accounting. */
internal class PlayerHistoryController(private val player: Player) {
    private val recommendations = RecommendationWatchTracker(player.context)
    private val learningSessions = LearningSessionTracker(player.context)
    private val updates = CompositeDisposable()
    private val radioHistory = RadioSongHistoryController(player.context, updates)

    fun onRadioMetadata(metadata: androidx.media3.common.Metadata) {
        radioHistory.onMetadata(currentRadioItem(), metadata, player.isPlaying)
    }

    fun onPlaybackEvents() {
        radioHistory.onPlayback(currentRadioItem(), player.isPlaying)
    }

    private fun currentRadioItem(): PlayQueueItem? {
        if (player.exoPlayerIsNull()) return null
        val tag = org.schabi.newpipe.player.mediaitem.MediaItemTag.from(player.exoPlayer.currentMediaItem).orElse(null)
        return (tag as? org.schabi.newpipe.player.mediaitem.LocalMediaItemTag)?.item?.takeIf { it.isRadio }
    }

    fun restoreStreamState(
        item: PlayQueueItem,
        onSuccess: Consumer<in StreamStateEntity>,
        onError: Consumer<in Throwable>,
        onComplete: Action
    ) {
        updates.add(
            records().loadStreamState(item)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess, onError, onComplete)
        )
    }

    fun registerViewed() {
        val item = player.currentItem
        if (item?.isLocalMedia == true) {
            registerViewed(item)
        } else {
            player.currentStreamInfo.ifPresent { info ->
                recommendations.start(info)
                updates.add(records().onViewed(info, item).onErrorComplete().subscribe())
            }
        }
    }

    fun registerViewed(item: PlayQueueItem) {
        recommendations.stop()
        updates.add(records().onViewed(item).onErrorComplete().subscribe())
    }

    fun saveProgress(progressMillis: Long) {
        if (!isWatchHistoryEnabled()) {
            return
        }
        val item = player.currentItem
        if (item?.isLocalMedia == true) {
            updates.add(
                records().saveStreamState(item, progressMillis)
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorComplete()
                    .subscribe()
            )
            return
        }

        player.currentStreamInfo.ifPresent { info ->
            if (Player.DEBUG) {
                Log.d(
                    Player.TAG,
                    "saveStreamProgressState() called with: progressMillis=$progressMillis, " +
                        "currentMetadata=[${info.name}]"
                )
            }
            updates.add(
                records().saveStreamState(info, progressMillis)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError { error ->
                        if (Player.DEBUG) {
                            error.printStackTrace()
                        }
                    }
                    .onErrorComplete()
                    .subscribe()
            )
        }
    }

    fun updateLearningSession() {
        recommendations.update(player.currentState == Player.STATE_PLAYING)
        learningSessions.update(
            player.currentItem,
            player.currentState == Player.STATE_PLAYING,
            player.audioPlayerSelected()
        )
    }

    fun stopLearningSession() {
        recommendations.stop()
        learningSessions.stop()
    }

    fun clear() {
        recommendations.close()
        learningSessions.close()
        updates.clear()
    }

    // The player service can outlive the activity that switched profiles.
    private fun records(): HistoryRecordManager = HistoryRecordManager(player.context)

    private fun isWatchHistoryEnabled(): Boolean = player.prefs.getBoolean(
        player.context.getString(R.string.enable_watch_history_key),
        true
    )
}
