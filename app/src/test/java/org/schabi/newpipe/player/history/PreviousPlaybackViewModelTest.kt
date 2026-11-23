/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.history

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.recovery.QueueRecovery
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.SerializedCache

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreviousPlaybackViewModelTest {
    private lateinit var application: Application
    private lateinit var prefs: SharedPreferences
    private lateinit var store: ViewModelStore
    private lateinit var model: PreviousPlaybackViewModel
    private lateinit var player: Player
    private lateinit var engine: ExoPlayer

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        prefs = PreferenceManager.getDefaultSharedPreferences(application)
        prefs.edit().clear().commit()
        store = ViewModelStore()
        model = provider()[PreviousPlaybackViewModel::class.java]
        assertSame(application, model.getApplication<Application>())
        player = mock(Player::class.java)
        engine = mock(ExoPlayer::class.java)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(engine.currentTimeline).thenReturn(mock(Timeline::class.java))
    }

    @After
    fun tearDown() {
        try {
            store.clear()
            SerializedCache.getInstance().clear()
        } finally {
            framework().clearInlineMocks()
        }
    }

    @Test
    fun capturePrecedesServiceStopAndExactQueueRestoresAcrossViewRecreation() {
        val original = queue(1, 2, 3).apply { index = 1 }
        begin(original, 85_432, playing = false, repeat = REPEAT_MODE_ALL)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/9")
        assertTrue(model.available.value == true)
        original.setRecovery(1, 0)
        original.dispose()
        `when`(player.playQueue).thenReturn(null)
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        // The same fragment ViewModelStore survives recreation; the destroyed service does not.
        assertSame(model, provider()[PreviousPlaybackViewModel::class.java])

        val restored = model.takePrevious()!!
        assertEquals(listOf("content://media/1", "content://media/2", "content://media/3"), restored.queue.streams.map { it.url })
        assertEquals(1, restored.queue.index)
        assertEquals(85_432L, restored.queue.item!!.recoveryPosition)
        assertFalse(restored.playWhenReady)
        assertEquals(REPEAT_MODE_ALL, restored.repeatMode)
        assertNotSame(original.item, restored.queue.item)
        val intent = restored.playerIntent(application)!!
        assertTrue(intent.getBooleanExtra(QueueRecovery.RESTORE_EXTRA, false))
        assertFalse(intent.getBooleanExtra(Player.PLAY_WHEN_READY, true))
        assertFalse(intent.getBooleanExtra(Player.RESUME_PLAYBACK, true))
        val delivered = SerializedCache.getInstance().take(intent.getStringExtra(Player.PLAY_QUEUE_KEY)!!, PlayQueue::class.java)!!
        assertEquals(1, delivered.index)
        assertEquals(85_432L, delivered.item!!.recoveryPosition)
        assertFalse(delivered.isDisposed)

        val stale = queue(9)
        assertFalse(model.acceptsQueue(stale))
        model.bindQueue(stale, player)
        verify(engine, never()).repeatMode = REPEAT_MODE_ALL
        begin(delivered, 0)
        assertTrue(model.acceptsQueue(delivered))
        verify(engine).repeatMode = REPEAT_MODE_ALL
        assertFalse(model.canReturn())
    }

    @Test
    fun rapidSelectionsDoNotDuplicateTheStillBoundOldQueue() {
        begin(queue(1), 25_000)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/2")
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/3")
        val restored = model.takePrevious()!!
        assertEquals("content://media/1", restored.queue.item!!.url)
        begin(deliver(restored), 25_000)
        assertFalse(model.canReturn())
        assertNull(model.takePrevious())
    }

    @Test
    fun returningBeforeReplacementStartsCannotBeAcknowledgedByAnEqualOldQueue() {
        val original = queue(1, 2)
        begin(original, 56_789, playing = false, repeat = REPEAT_MODE_ALL)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/3")
        val previous = model.takePrevious()!!
        assertTrue(original.equalStreamsAndIndex(previous.queue))
        assertFalse(model.acceptsQueue(original))
        model.bindQueue(original, player)
        assertSame(previous, model.restorationFor(previous.queue))
        verify(engine, never()).repeatMode = REPEAT_MODE_ALL

        val delivered = deliver(previous)
        assertFalse(model.acceptsQueue(original))
        model.bindQueue(original, player)
        assertSame(previous, model.restorationFor(previous.queue))
        begin(delivered, 0)
        assertNull(model.restorationFor(previous.queue))
        assertEquals(56_789L, delivered.item!!.recoveryPosition)
        verify(engine).repeatMode = REPEAT_MODE_ALL
    }

    @Test
    fun repeatedReturnsWalkBackThroughIndependentQueuesWithoutCreatingAToggleLoop() {
        begin(queue(1, 11), 11_111)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/2")
        begin(queue(2, 22), 22_222)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/3")
        begin(queue(3, 33), 33_333)
        val second = model.takePrevious()!!
        assertEquals("content://media/2", second.queue.item!!.url)
        assertEquals(22_222L, second.queue.item!!.recoveryPosition)
        begin(deliver(second), 22_222)
        val first = model.takePrevious()!!
        assertEquals("content://media/1", first.queue.item!!.url)
        assertEquals(11_111L, first.queue.item!!.recoveryPosition)
        begin(deliver(first), 11_111)
        assertNull(model.takePrevious())
    }

    @Test
    fun missingTimelineKeepsTheExplicitPendingResumePosition() {
        val first = queue(1).apply { setRecovery(0, 63_000) }
        begin(first, 0)
        `when`(engine.currentTimeline).thenReturn(Timeline.EMPTY)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/2")
        assertEquals(63_000L, model.takePrevious()!!.queue.item!!.recoveryPosition)
    }

    @Test
    fun cancelledOrFailedRestoreCanBeRetriedWithoutLosingItsQueueOrPosition() {
        begin(queue(1, 2), 91_234)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/3")
        val first = model.takePrevious()!!
        assertFalse(model.canReturn())
        model.cancelRestoration()
        assertTrue(model.canReturn())
        val retry = model.takePrevious()!!
        assertEquals(first.queue.streams.map { it.url }, retry.queue.streams.map { it.url })
        assertEquals(91_234L, retry.queue.item!!.recoveryPosition)
        assertNotSame(first.queue, retry.queue)
    }

    @Test
    fun privacyAndProfileChangesEraseHistoryAndLatchTheOldQueue() {
        val changes = listOf<() -> Unit>(
            {
                IncognitoMode.setEnabled(application, true)
                IncognitoMode.setEnabled(application, false)
            },
            {
                val key = application.getString(R.string.enable_watch_history_key)
                prefs.edit().putBoolean(key, false).commit()
                prefs.edit().putBoolean(key, true).commit()
            },
            {
                val original = ProfileManager.getActiveProfileId(application)
                val other = ProfileManager.createProfile(application, "Other")!!
                assertTrue(ProfileManager.setActiveProfile(application, other.id))
                assertTrue(ProfileManager.setActiveProfile(application, original))
            },
            { QueueRecovery.clear(application, ProfileManager.getActiveProfileId(application)) }
        )
        changes.forEach { change ->
            model.clear()
            begin(queue(1), 10_000)
            model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/2")
            begin(queue(2), 20_000)
            assertTrue(model.canReturn())
            change()
            assertFalse(model.canReturn())
            // A surface/service copy of the old queue must not re-arm privacy eligibility.
            begin(queue(2), 20_000)
            model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/3")
            assertFalse(model.canReturn())
            begin(queue(3), 30_000)
            model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/4")
            assertEquals("content://media/3", model.takePrevious()!!.queue.item!!.url)
        }
    }

    @Test
    fun playbackStartedInIncognitoCannotBeAddedAfterLeavingIncognito() {
        IncognitoMode.setEnabled(application, true)
        begin(queue(1), 90_000)
        IncognitoMode.setEnabled(application, false)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/2")
        assertFalse(model.canReturn())
    }

    @Test
    fun privacyChangeDuringRestoreCannotRepopulateHistoryThroughCancellation() {
        begin(queue(1), 10_000)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/2")
        model.takePrevious()!!
        QueueRecovery.clear(application, ProfileManager.getActiveProfileId(application))
        model.cancelRestoration()
        assertFalse(model.canReturn())
    }

    @Test
    fun dismissalAndViewModelDestructionEndTheNavigationSession() {
        begin(queue(1), 10_000)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/2")
        assertTrue(model.canReturn())
        model.clear()
        assertFalse(model.canReturn())
        begin(queue(3), 30_000)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://media/4")
        assertTrue(model.canReturn())
        store.clear()
        assertFalse(model.canReturn())
        assertFalse(provider()[PreviousPlaybackViewModel::class.java].canReturn())
    }

    // Robolectric creates a new Application per test; the framework singleton retains the first.
    private fun provider() = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(application))

    private fun deliver(previous: PreviousPlayback): PlayQueue {
        val intent = previous.playerIntent(application)!!
        return SerializedCache.getInstance().take(intent.getStringExtra(Player.PLAY_QUEUE_KEY)!!, PlayQueue::class.java)!!
    }

    private fun begin(queue: PlayQueue, position: Long, playing: Boolean = true, repeat: Int = REPEAT_MODE_OFF) {
        `when`(player.playQueue).thenReturn(queue)
        `when`(player.currentItem).thenReturn(queue.item)
        `when`(player.exoPlayerIsNull()).thenReturn(false)
        `when`(player.playWhenReady).thenReturn(playing)
        `when`(player.repeatMode).thenReturn(repeat)
        `when`(engine.currentMediaItemIndex).thenReturn(queue.index)
        `when`(engine.contentPosition).thenReturn(position)
        model.bindPlayer(player)
    }

    private fun queue(vararg ids: Int) = LocalMediaPlayQueue(
        ids.map { PlayQueueItem.localMedia("Track $it", "content://media/$it", 300, "Artist", null, null, "audio/mpeg", it.toLong(), false, null) },
        0
    )
}
