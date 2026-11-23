/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.history

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.ui.PlayerActionVisibility
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.player.ui.PreviousPlaybackUi

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], application = Application::class)
class PreviousPlaybackNavigationTest {
    private lateinit var context: ContextThemeWrapper
    private lateinit var model: PreviousPlaybackViewModel
    private lateinit var fragment: VideoDetailFragment
    private lateinit var player: Player
    private var returned: PreviousPlayback? = null

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        context = ContextThemeWrapper(application, R.style.DarkTheme)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        model = PreviousPlaybackViewModel(application)
        player = mock(Player::class.java)
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        `when`(player.UIs()).thenReturn(PlayerUiList())
        `when`(player.videoPlayerSelected()).thenReturn(true)
        fragment = mock(VideoDetailFragment::class.java, CALLS_REAL_METHODS)
        field("player", player)
        field("previousPlayback", model)
        `when`(player.fragmentListener).thenReturn(Optional.of(fragment))
        val queue = LocalMediaPlayQueue(
            listOf(PlayQueueItem.localMedia("First", "content://first", 180, null, null, null, "audio/mpeg", 1, false, null)),
            0
        )
        queue.setRecovery(0, 75_432)
        `when`(player.playQueue).thenReturn(queue)
        model.bindPlayer(player)
        model.beforeSelection(player, PlayQueueItem.LOCAL_SERVICE_ID, "content://second")
        doAnswer {
            returned = model.takePrevious()
            null
        }.`when`(fragment).onReturnToPreviousPlayback()
    }

    @After
    fun tearDown() {
        try {
            androidx.lifecycle.ViewModelStore().apply {
                put("previous", model)
                clear()
            }
        } finally {
            framework().clearInlineMocks()
        }
    }

    @Test
    fun androidBackStillCollapsesAndOnlyTheExplicitPlayerActionConsumesHistory() {
        assertTrue(fragment.canReturnToPreviousPlayback())
        assertFalse(fragment.onBackPressed())
        assertTrue(fragment.canReturnToPreviousPlayback())
        verify(fragment, never()).onReturnToPreviousPlayback()
        verify(player, never()).pause()

        val binding = PlayerBinding.inflate(LayoutInflater.from(context))
        PreviousPlaybackUi.bind(binding, player)
        val button = binding.previousPlayback
        assertEquals(View.VISIBLE, button.visibility)
        assertEquals(context.getString(R.string.return_to_previous_playback), button.contentDescription)
        assertTrue(button.isFocusable)
        assertTrue(button.isClickable)
        assertTrue(button.performClick())
        assertNotNull(returned)
        assertEquals("content://first", returned!!.queue.item!!.url)
        assertEquals(75_432L, returned!!.queue.item!!.recoveryPosition)
        assertEquals(View.GONE, button.visibility)
        PreviousPlaybackUi.clear(binding)
        assertFalse(button.hasOnClickListeners())
    }

    @Test
    fun detailActionWorksWithoutAPlayerServiceAndHonorsTheSharedToolPreference() {
        val binding = FragmentVideoDetailBinding.inflate(LayoutInflater.from(context))
        field("player", null)
        PreviousPlaybackUi.bindDetails(binding, fragment)
        val button = binding.detailPreviousPlayback
        assertEquals(View.VISIBLE, button.visibility)
        assertEquals(context.getString(R.string.return_to_previous_playback), button.contentDescription)
        assertTrue(button.isFocusable)
        PlayerActionVisibility.save(context, setOf("previous_playback"))
        PreviousPlaybackUi.updateDetails(binding, fragment)
        assertEquals(View.GONE, button.visibility)
        assertTrue(model.canReturn())
        PlayerActionVisibility.save(context, emptySet())
        PreviousPlaybackUi.updateDetails(binding, fragment)
        assertEquals(View.VISIBLE, button.visibility)
        assertTrue(button.performClick())
        assertEquals("content://first", returned!!.queue.item!!.url)
        PreviousPlaybackUi.updateDetails(binding, fragment)
        assertEquals(View.GONE, button.visibility)
    }

    private fun field(name: String, value: Any?) {
        VideoDetailFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.set(fragment, value)
    }
}
