/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player

import android.app.Application
import android.content.Intent
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfflineTimestampControllerTest {
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val engine = mock(ExoPlayer::class.java)
    private val requests = CompositeDisposable()
    private val pending = ArrayDeque<SingleSubject<OfflineTimestampTarget>>()
    private val first = item("first")
    private var queue: PlayQueue = LocalMediaPlayQueue(listOf(first), 0)
    private var activeItem: PlayQueueItem? = first
    private var noQueue = false
    private lateinit var controller: PlayerIntentController

    @Before fun setup() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        `when`(player.playQueue).thenAnswer { if (noQueue) null else queue }
        `when`(player.currentItem).thenAnswer { activeItem }
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(engine.playbackState).thenReturn(Media3Player.STATE_READY)
        controller = PlayerIntentController(
            player,
            RuntimeEnvironment.getApplication(),
            mock(PlayerHistoryController::class.java),
            mock(PlayerPresentationController::class.java),
            mock(PopupPlayerReturnState::class.java),
            mock(VideoPlaybackResolver::class.java),
            requests
        ) { _, _ -> pending.removeFirst() }
    }

    @After fun cleanup() {
        requests.dispose()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
        framework().clearInlineMocks()
    }

    @Test fun selectingAnotherQueueWhileLocalMetadataLoadsDiscardsTheOldTarget() {
        val result = SingleSubject.create<OfflineTimestampTarget>()
        pending += result
        controller.handle(intent(1250))
        queue = LocalMediaPlayQueue(listOf(item("new-selection")), 0)
        result.onSuccess(OfflineTimestampTarget(first, true))
        verify(engine, never()).seekTo(anyInt(), anyLong())
        verify(player, never()).initPlayback(any(PlayQueue::class.java) ?: queue, anyBoolean())
        assertEquals("new-selection", queue.item!!.title)
    }

    @Test fun aNewerTimestampCancelsTheOldRequestAndOnlyAppliesItsOwnPosition() {
        val old = SingleSubject.create<OfflineTimestampTarget>()
        val latest = SingleSubject.create<OfflineTimestampTarget>()
        pending += old
        pending += latest
        controller.handle(intent(1250))
        controller.handle(intent(5000))
        assertFalse(old.hasObservers())
        old.onSuccess(OfflineTimestampTarget(first, true))
        latest.onSuccess(OfflineTimestampTarget(first, true))
        verify(engine, never()).seekTo(0, 1250)
        verify(engine).seekTo(0, 5000)
    }

    @Test fun nextWithinTheSameQueueDiscardsTheOldTargetBeforeCurrentMetadataCatchesUp() {
        queue = LocalMediaPlayQueue(listOf(first, item("pending-next")), 0)
        val result = SingleSubject.create<OfflineTimestampTarget>()
        pending += result
        controller.handle(intent(1250))
        queue.index = 1
        result.onSuccess(OfflineTimestampTarget(first, true))
        verify(engine, never()).seekTo(anyInt(), anyLong())
        verify(player, never()).initPlayback(any(PlayQueue::class.java) ?: queue, anyBoolean())
        assertEquals("pending-next", queue.item!!.title)
    }

    @Test fun newerPlaybackIntentCancelsPendingTimestampEvenBeforeItHasAQueue() {
        val old = SingleSubject.create<OfflineTimestampTarget>()
        pending += old
        controller.handle(intent(1250))
        controller.handle(Intent().putExtra(Player.PLAYER_INTENT_TYPE, PlayerIntentType.AllOthers).putExtra(Player.PLAYER_TYPE, PlayerType.MAIN))
        assertFalse(old.hasObservers())
        old.onSuccess(OfflineTimestampTarget(first, true))
        verify(engine, never()).seekTo(anyInt(), anyLong())
        verify(player, never()).initPlayback(any(PlayQueue::class.java) ?: queue, anyBoolean())
    }

    @Test fun sameItemTimestampRebuildsAnAbsentEngineWithItsRecoveryPosition() {
        val result = SingleSubject.create<OfflineTimestampTarget>()
        pending += result
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        controller.handle(intent(1250))
        result.onSuccess(OfflineTimestampTarget(first, true))
        assertEquals(1250L, queue.item!!.recoveryPosition)
        verify(player).initPlayback(queue, false)
        verify(engine, never()).seekTo(anyInt(), anyLong())
    }

    @Test fun aColdTimestampFromAnOldProfileStopsItsOtherwiseEmptyService() {
        noQueue = true
        activeItem = null
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        val stale = TimestampChangeData(PlayQueueItem.LOCAL_SERVICE_ID, first.url, 1, first.url, "identity", "old-profile", 1250)
        controller.handle(Intent().putExtra(Player.PLAYER_INTENT_TYPE, PlayerIntentType.TimestampChange).putExtra(Player.PLAYER_INTENT_DATA, stale))
        verify(player.service).destroyPlayerAndStopService()
        verify(player, never()).initPlayback(any(PlayQueue::class.java) ?: queue, anyBoolean())
    }

    private fun intent(position: Long) = Intent().putExtra(Player.PLAYER_INTENT_TYPE, PlayerIntentType.TimestampChange)
        .putExtra(Player.PLAYER_INTENT_DATA, TimestampChangeData(PlayQueueItem.LOCAL_SERVICE_ID, first.url, (position / 1000).toInt(), first.url, "identity", positionMillis = position))
        .putExtra(Player.PLAY_WHEN_READY, false)

    private fun item(title: String) = PlayQueueItem.localMedia(title, "file:///$title.mp4", 8, null, null, null, "video/mp4", -1, true, null)
}
