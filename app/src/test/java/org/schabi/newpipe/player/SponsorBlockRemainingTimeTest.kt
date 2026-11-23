/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import android.content.SharedPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import java.util.Optional
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockBehavior
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33], application = android.app.Application::class)
class SponsorBlockRemainingTimeTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var preferences: SharedPreferences
    private lateinit var exo: ExoPlayer
    private lateinit var controller: SponsorBlockPlaybackController

    @Before
    fun setUp() {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().putBoolean(context.getString(R.string.sponsor_block_enable_key), true)
            .putBoolean(context.getString(R.string.sponsor_block_notifications_key), false).commit()
        SponsorBlockCategoryRepository.setAllEnabled(context, true)
        val player = mock(Player::class.java)
        exo = mock(ExoPlayer::class.java)
        `when`(player.context).thenReturn(context)
        `when`(player.prefs).thenReturn(preferences)
        `when`(player.exoPlayer).thenReturn(exo)
        `when`(player.isPlaying).thenReturn(true)
        `when`(player.currentStreamInfo).thenReturn(Optional.empty())
        `when`(player.UIs()).thenReturn(PlayerUiList())
        `when`(exo.duration).thenReturn(120_000)
        `when`(exo.playbackState).thenReturn(androidx.media3.common.Player.STATE_READY)
        controller = SponsorBlockPlaybackController(player)
        behavior(SponsorBlockCategory.SPONSOR, SponsorBlockBehavior.SKIP)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun estimatesOnlyEnabledAutomaticSkipsAndReactsToPreferenceChanges() {
        behavior(SponsorBlockCategory.INTRO, SponsorBlockBehavior.MANUAL)
        SponsorBlockCategoryRepository.setEnabled(context, config(SponsorBlockCategory.OUTRO), false)
        controller.updateSegments(
            listOf(
                segment("sponsor", 10_000.0, 30_000.0),
                segment("manual", 40_000.0, 50_000.0, SponsorBlockCategory.INTRO),
                segment("disabled", 80_000.0, 90_000.0, SponsorBlockCategory.OUTRO),
                segment("highlight", 60_000.0, 60_000.0, SponsorBlockCategory.HIGHLIGHT, SponsorBlockAction.POI)
            )
        )
        assertEquals(100_000L, controller.remainingTimeMillis(120_000, 0, 1f))
        behavior(SponsorBlockCategory.SPONSOR, SponsorBlockBehavior.MANUAL)
        assertEquals(120_000L, controller.remainingTimeMillis(120_000, 0, 1f))
        behavior(SponsorBlockCategory.SPONSOR, SponsorBlockBehavior.SKIP)
        preferences.edit().putBoolean(context.getString(R.string.sponsor_block_enable_key), false).commit()
        assertEquals(60_000L, controller.remainingTimeMillis(120_000, 0, 2f))
        preferences.edit().putBoolean(context.getString(R.string.sponsor_block_enable_key), true).commit()
        assertEquals(50_000L, controller.remainingTimeMillis(120_000, 0, 2f))
    }

    @Test
    fun graceRewindAndAlreadyConsumedSegmentsFollowTheRealSkipController() {
        controller.updateSegments(listOf(segment("sponsor", 10_000.0, 30_000.0)))
        controller.onPositionDiscontinuity(true, 15_000)
        assertEquals(105_000L, controller.remainingTimeMillis(120_000, 15_000, 1f))

        controller.onPositionDiscontinuity(true, 0)
        assertEquals(100_000L, controller.remainingTimeMillis(120_000, 0, 1f))
        `when`(exo.currentPosition).thenReturn(15_000)
        controller.onProgress()
        verify(exo).seekTo(30_000)
        controller.onPositionDiscontinuity(false, 30_000)
        assertEquals(120_000L, controller.remainingTimeMillis(120_000, 0, 1f))

        preferences.edit().putBoolean(context.getString(R.string.sponsor_block_graced_rewind_key), false).commit()
        controller.onPositionDiscontinuity(true, 15_000)
        assertEquals(90_000L, controller.remainingTimeMillis(120_000, 15_000, 1f))
    }

    @Test
    fun malformedAndNonSkipSegmentsDoNotChangeTheEstimate() {
        controller.updateSegments(
            listOf(
                segment("nan", Double.NaN, 30_000.0),
                segment("infinite", 10_000.0, Double.POSITIVE_INFINITY),
                segment("negative", -10_000.0, 30_000.0),
                segment("point", 40_000.0, 50_000.0, action = SponsorBlockAction.POI)
            )
        )
        assertEquals(120_000L, controller.remainingTimeMillis(120_000, 0, 1f))
    }

    private fun config(category: SponsorBlockCategory) = checkNotNull(SponsorBlockCategoryConfig.fromApiCategory(category))
    private fun behavior(category: SponsorBlockCategory, value: SponsorBlockBehavior) = SponsorBlockCategoryRepository.setBehavior(context, config(category), value)
    private fun segment(id: String, start: Double, end: Double, category: SponsorBlockCategory = SponsorBlockCategory.SPONSOR, action: SponsorBlockAction = SponsorBlockAction.SKIP) = SponsorBlockSegment(id, start, end, category, action, 0)
}
