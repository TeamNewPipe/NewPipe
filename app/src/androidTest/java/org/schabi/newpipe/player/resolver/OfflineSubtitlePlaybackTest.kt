/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player.resolver

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.download.OfflineDownloadFixture
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.player.helper.CustomRenderersFactory
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor

class OfflineSubtitlePlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun coldSavedCaptionTracksReachThePlayerAndSeekWithoutRemoteDataSources() {
        OfflineDownloadFixture(context).use { fixture ->
            fixture.register()
            val offline = OfflinePlaybackMetadata.resolve(context, fixture.uri())
            val captionLanguage = PlayerHelper.captionLanguageOf(context, offline.subtitles.single())
            val ready = CountDownLatch(1)
            val first = CountDownLatch(1)
            val second = CountDownLatch(1)
            val firstCueTime = AtomicLong(C.TIME_UNSET)
            val secondCueTime = AtomicLong(C.TIME_UNSET)
            val failure = AtomicReference<PlaybackException>()
            val remoteOpens = AtomicInteger()
            var engine: ExoPlayer? = null
            try {
                instrumentation.runOnMainSync {
                    val localSources = DefaultDataSource.Factory(
                        context,
                        DataSource.Factory {
                            // DefaultDataSource creates its fallback eagerly, even for a local URI.
                            object : DataSource {
                                override fun addTransferListener(transferListener: TransferListener) = Unit
                                override fun open(dataSpec: DataSpec): Long {
                                    remoteOpens.incrementAndGet()
                                    error("Offline playback tried to open a remote DataSource: ${dataSpec.uri}")
                                }
                                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = error("Offline playback tried a remote read")
                                override fun getUri(): Uri? = null
                                override fun close() = Unit
                            }
                        }
                    )
                    val media = ProgressiveMediaSource.Factory(localSources).createMediaSource(MediaItem.fromUri(fixture.uri()))
                    engine = ExoPlayer.Builder(context, CustomRenderersFactory(context, false, VisualizerAudioProcessor())).build().apply {
                        trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                            .setPreferredTextLanguage(captionLanguage).build()
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY) ready.countDown()
                            }
                            override fun onCues(cueGroup: CueGroup) {
                                val text = cueGroup.cues.joinToString { it.text.toString() }
                                if (text.contains("First offline line") && first.count > 0) {
                                    firstCueTime.set(cueGroup.presentationTimeUs)
                                    engine!!.pause()
                                    first.countDown()
                                }
                                if (text.contains("Second offline line") && second.count > 0) {
                                    secondCueTime.set(cueGroup.presentationTimeUs)
                                    engine!!.pause()
                                    second.countDown()
                                }
                            }
                            override fun onPlayerError(error: PlaybackException) {
                                failure.set(error)
                                ready.countDown()
                                first.countDown()
                                second.countDown()
                            }
                        })
                        setMediaSource(OfflineSubtitleSources.merge(context, media, offline.subtitles, localSources))
                        seekTo(1250)
                        prepare()
                    }
                }
                assertTrue("Local media did not prepare", ready.await(20, TimeUnit.SECONDS))
                assertNull(failure.get())
                instrumentation.runOnMainSync {
                    assertEquals(1250L, engine!!.currentPosition)
                    assertTrue("Saved caption track was not selected", engine!!.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected })
                    // SingleSampleMediaSource uses legacy decoding; its renderer must be started
                    // to emit cues. Check the exact paused seek before starting each cue render.
                    engine!!.play()
                }
                assertTrue("First saved cue was not rendered", first.await(10, TimeUnit.SECONDS))
                assertNull(failure.get())
                assertEquals("First saved cue timestamp changed", 1_250_000L, firstCueTime.get())
                instrumentation.runOnMainSync {
                    assertTrue("First cue should pause playback", !engine!!.playWhenReady)
                    engine!!.seekTo(5000)
                    assertEquals(5000L, engine!!.currentPosition)
                    engine!!.play()
                }
                assertTrue("Caption seek did not render the second cue", second.await(10, TimeUnit.SECONDS))
                assertNull(failure.get())
                assertEquals("Second saved cue timestamp changed", 5_000_000L, secondCueTime.get())
                instrumentation.runOnMainSync { assertTrue("Second cue should pause playback", !engine!!.playWhenReady) }
                assertEquals("Offline playback opened a remote DataSource", 0, remoteOpens.get())
            } finally {
                instrumentation.runOnMainSync { engine?.release() }
            }
        }
    }
}
