/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LivePlaybackModeTest {
    @Test
    fun nonSeekableLiveWindowRejectsUserSeekingBeforeChangingPlayerState() {
        val player = mock(Player::class.java)
        val exoPlayer = mock(ExoPlayer::class.java)
        `when`(player.getExoPlayer()).thenReturn(exoPlayer)
        `when`(exoPlayer.isCurrentMediaItemDynamic).thenReturn(true)
        `when`(exoPlayer.isCurrentMediaItemSeekable).thenReturn(false)

        PlayerSeekController(player).seekTo(1_000)

        verify(player).getExoPlayer()
        verifyNoMoreInteractions(player)
        verify(exoPlayer).isCurrentMediaItemDynamic
        verify(exoPlayer).isCurrentMediaItemSeekable
        verifyNoMoreInteractions(exoPlayer)
    }

    @Test
    fun seekableLiveDvrWindowIsRecognizedEvenInNormalLiveMode() {
        val player = mock(Player::class.java)
        val exoPlayer = mock(ExoPlayer::class.java)
        `when`(player.getExoPlayer()).thenReturn(exoPlayer)
        `when`(exoPlayer.isCurrentMediaItemDynamic).thenReturn(true)
        `when`(exoPlayer.isCurrentMediaItemSeekable).thenReturn(true)

        assertTrue(PlayerSeekController(player).canSeekCurrentWindow())
    }

    @Test
    fun uninitializedPlayerRejectsSeekWithoutTouchingPlaybackState() {
        val player = mock(Player::class.java)
        PlayerSeekController(player).seekTo(1_000)
        verify(player).getExoPlayer()
        verifyNoMoreInteractions(player)
    }

    @Test
    fun overrideIsLimitedToOneStreamAndDoesNotChangeTheSavedDefault() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.default_live_mode_key)
        preferences.edit().remove(key).commit()
        val mode = LivePlaybackMode(context)
        assertTrue(mode.isNormal("first"))
        mode.setNormal("first", false)
        assertFalse(mode.isNormal("first"))
        assertTrue(mode.isNormal("next"))
        assertTrue(LivePlaybackMode(context).isNormal("first"))
        preferences.edit().putString(key, "full").commit()
        assertFalse(mode.isNormal("next"))
        mode.setNormal("next", true)
        assertTrue(mode.isNormal("next"))
        assertFalse(LivePlaybackMode(context).isNormal("next"))
        preferences.edit().remove(key).commit()
    }
}
