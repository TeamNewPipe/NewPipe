/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Looper
import androidx.core.content.IntentCompat
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.net.UnknownHostException
import java.time.Duration
import java.util.Optional
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorActivity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.video.VideoAdjustmentController
import org.schabi.newpipe.util.ExtractorHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackNetworkErrorReportTest {
    @Test
    fun exhaustedLiveNetworkRecoveryKeepsItsBudgetAndIncludesBothDiagnosticSnapshots() {
        val context = RuntimeEnvironment.getApplication()
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(context.getString(R.string.error_report_channel_id), "Playback errors", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val queue = mock(PlayQueue::class.java)
        val item = mock(PlayQueueItem::class.java)
        val info = StreamInfo(ServiceList.YouTube.serviceId, "live", "https://www.youtube.com/watch?v=test", "Test").apply {
            streamType = StreamType.LIVE_STREAM
        }
        var playWhenReady = true
        `when`(player.context).thenReturn(context)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(player.videoAdjustments).thenReturn(mock(VideoAdjustmentController::class.java))
        `when`(player.playbackPresentationMode).thenReturn(PlaybackPresentationMode.AUDIO_BACKGROUND)
        `when`(player.playWhenReady).thenAnswer { playWhenReady }
        `when`(player.playQueue).thenReturn(queue)
        `when`(queue.item).thenReturn(item)
        `when`(item.serviceId).thenReturn(info.serviceId)
        `when`(item.url).thenReturn(info.url)
        `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
        doAnswer {
            playWhenReady = false
            null
        }.`when`(engine).pause()
        val immediate = Schedulers.trampoline()
        mockStatic(Schedulers::class.java).use { schedulers ->
            schedulers.`when`<io.reactivex.rxjava3.core.Scheduler> { Schedulers.io() }.thenReturn(immediate)
            mockStatic(ExtractorHelper::class.java).use { extractor ->
                extractor.`when`<Single<StreamInfo>> { ExtractorHelper.getStreamInfo(info.serviceId, info.url, true) }
                    .thenAnswer { Single.error<StreamInfo>(UnknownHostException("No address")) }
                PlaybackNetworkDiagnostics(context).use { diagnostics ->
                    val controller = PlayerErrorController(player, mock(PlayerEventDispatcher::class.java), mock(VideoPlaybackResolver::class.java), diagnostics)
                    val error = PlaybackException("Source error", UnknownHostException("No address"), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
                    controller.onPlayerError(error)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))

                    extractor.verify({ ExtractorHelper.getStreamInfo(info.serviceId, info.url, true) }, org.mockito.Mockito.times(3))
                    verify(engine).pause()
                    verify(player, never()).reloadPlayQueueManager()
                    val notification = shadowOf(notifications).allNotifications.single()
                    val intent = shadowOf(notification.contentIntent).savedIntent
                    val report = requireNotNull(IntentCompat.getParcelableExtra(intent, ErrorActivity.ERROR_INFO, ErrorInfo::class.java))
                    assertTrue(report.request.contains("livePreparation=EXHAUSTED"))
                    assertTrue(report.request.contains("networkAtError:"))
                    assertTrue(report.request.contains("networkAtReport:"))
                    assertTrue(report.request.contains("mode=AUDIO_BACKGROUND playWhenReady=true"))
                    assertTrue(report.request.contains("mode=AUDIO_BACKGROUND playWhenReady=false"))
                    controller.resetRecovery()
                }
            }
        }
    }
}
