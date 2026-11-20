/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.recovery

import android.app.Application
import android.content.Context
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QueueRecoveryControllerTest {
    private lateinit var context: Context
    private lateinit var player: Player
    private lateinit var controller: QueueRecoveryController
    private val saves = mutableListOf<QueueSnapshot>()
    private val eligibility = mutableListOf<AtomicBoolean>()

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        player = mock(Player::class.java)
        `when`(player.context).thenReturn(context)
        `when`(player.prefs).thenReturn(prefs)
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        controller = QueueRecoveryController(player) { _, snapshot, eligible, _ ->
            saves.add(snapshot)
            eligibility.add(eligible)
        }
    }

    private fun beginQueue() {
        val item = PlayQueueItem.localMedia("Track", "content://media/external/audio/media/1", 100, "Artist", null, null, "audio/mpeg", 1, false, null)
        val queue = LocalMediaPlayQueue(listOf(item), 0)
        queue.setRecovery(0, 9_000)
        `when`(player.playQueue).thenReturn(queue)
        controller.bind(queue)
    }

    @Test fun incognitoInvalidatesPendingWritesAndRemainsLatchedUntilNewQueue() {
        beginQueue()
        controller.checkpoint(true)
        assertEquals(9_000L, saves.single().position)
        IncognitoMode.setEnabled(context, true)
        assertFalse(eligibility.single().get())
        controller.checkpoint(true)
        IncognitoMode.setEnabled(context, false)
        controller.checkpoint(true)
        assertEquals(1, saves.size)
        beginQueue()
        controller.checkpoint(true)
        assertEquals(2, saves.size)
        controller.close()
    }

    @Test fun queueStartedInIncognitoIsNotSavedAfterLeavingIncognito() {
        IncognitoMode.setEnabled(context, true)
        beginQueue()
        IncognitoMode.setEnabled(context, false)
        controller.checkpoint(true)
        assertTrue(saves.isEmpty())
        controller.close()
    }

    @Test fun disablingHistoryPreventsExistingQueueFromBeingSavedAgain() {
        beginQueue()
        val key = context.getString(R.string.enable_watch_history_key)
        player.prefs.edit().putBoolean(key, false).commit()
        player.prefs.edit().putBoolean(key, true).commit()
        controller.checkpoint(true)
        assertTrue(saves.isEmpty())
        controller.close()
    }

    @Test fun profileSwitchInvalidatesTheExistingQueueEvenAfterSwitchingBack() {
        beginQueue()
        val key = ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY
        player.prefs.edit().putString(key, "different-profile").commit()
        player.prefs.edit().putString(key, ProfileManager.DEFAULT_PROFILE_ID).commit()
        controller.checkpoint(true)
        assertTrue(saves.isEmpty())
        controller.close()
    }

    @Test fun clearingHistoryDoesNotAllowTheActiveQueueToRepopulateRecovery() {
        beginQueue()
        QueueRecovery.clear(context, ProfileManager.getActiveProfileId(context))
        controller.checkpoint(true)
        assertTrue(saves.isEmpty())
        beginQueue()
        controller.checkpoint(true)
        assertEquals(1, saves.size)
        controller.close()
    }

    @Test fun temporaryTimelineLossCannotReplaceTheLastKnownPosition() {
        beginQueue()
        val engine = mock(ExoPlayer::class.java)
        val currentTimeline = mock(Timeline::class.java)
        `when`(player.exoPlayerIsNull()).thenReturn(false)
        `when`(player.exoPlayer).thenReturn(engine)
        val item = player.playQueue!!.item
        `when`(player.currentItem).thenReturn(item)
        `when`(engine.currentTimeline).thenReturn(currentTimeline)
        `when`(engine.currentMediaItemIndex).thenReturn(0)
        `when`(engine.contentPosition).thenReturn(75_000)
        controller.checkpoint(true)

        `when`(engine.currentTimeline).thenReturn(Timeline.EMPTY)
        controller.checkpoint(true)
        assertEquals(listOf(75_000L), saves.map { it.position })

        `when`(engine.currentTimeline).thenReturn(currentTimeline)
        `when`(engine.contentPosition).thenReturn(45_000)
        controller.checkpoint(true)
        assertEquals(listOf(75_000L, 45_000L), saves.map { it.position })
        controller.close()
    }

    @Test fun savesConfirmedPlaybackPositionEveryThreeSecondsWhenActive() {
        val item = PlayQueueItem.localMedia(
            "Track", "content://media/external/audio/media/1",
            100, "Artist", null, null, "audio/mpeg", 1, false, null
        )
        val queue = LocalMediaPlayQueue(listOf(item), 0)
        `when`(player.playQueue).thenReturn(queue)
        val engine = mock(ExoPlayer::class.java)
        val timeline = mock(Timeline::class.java)
        `when`(player.exoPlayerIsNull()).thenReturn(false)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(player.currentItem).thenReturn(queue.item)
        `when`(engine.currentTimeline).thenReturn(timeline)
        `when`(engine.currentMediaItemIndex).thenReturn(0)
        var elapsed = 10_000L
        controller.close()
        controller = QueueRecoveryController(player, elapsedRealtime = { elapsed }) { _, snapshot, _, _ ->
            saves.add(snapshot)
        }
        controller.bind(queue)

        `when`(engine.contentPosition).thenReturn(50_000L)
        controller.checkpoint()
        elapsed += 2_500
        `when`(engine.contentPosition).thenReturn(52_500L)
        controller.checkpoint()
        assertEquals(listOf(50_000L), saves.map { it.position })

        elapsed += 500
        `when`(engine.contentPosition).thenReturn(53_000L)
        controller.checkpoint()
        assertEquals(listOf(50_000L, 53_000L), saves.map { it.position })

        elapsed += 500
        `when`(engine.contentPosition).thenReturn(53_500L)
        controller.checkpoint(true)
        assertEquals(listOf(50_000L, 53_000L, 53_500L), saves.map { it.position })
        controller.close()
    }

    @Test fun replacementStartsAnotherSession() {
        beginQueue()
        controller.checkpoint(true)
        beginQueue()
        controller.checkpoint(true)
        assertNotEquals(saves[0].session, saves[1].session)
        controller.close()
    }
}
