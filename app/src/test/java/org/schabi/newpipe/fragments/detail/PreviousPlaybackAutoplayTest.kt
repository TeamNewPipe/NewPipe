/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.detail

import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.download.DownloadedCopyRepository
import org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.history.PreviousPlaybackViewModel
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.recovery.QueueRecovery
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.SerializedCache

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreviousPlaybackAutoplayTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var fragment: VideoDetailFragment
    private lateinit var model: PreviousPlaybackViewModel
    private lateinit var info: StreamInfo

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java)
        activity = activityController.get()
        activity.setTheme(R.style.DarkTheme)
        activityController.setup()
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        preferences.edit().clear()
            .putString(activity.getString(R.string.autoplay_key), activity.getString(R.string.autoplay_never_key))
            .commit()
        assertFalse(PlayerHelper.isAutoplayAllowedByUser(activity))
        model = PreviousPlaybackViewModel(activity.application)
        fragment = mock(VideoDetailFragment::class.java, CALLS_REAL_METHODS)
        doReturn(activity).`when`(fragment).requireContext()
        doReturn(activity.resources).`when`(fragment).resources
        field(BaseFragment::class.java, "activity", activity)
        field(BaseStateFragment::class.java, "isLoading", AtomicBoolean(true))
        field(VideoDetailFragment::class.java, "binding", FragmentVideoDetailBinding.inflate(LayoutInflater.from(activity)))
        field(VideoDetailFragment::class.java, "previousPlayback", model)
        field(VideoDetailFragment::class.java, "playerService", mock(PlayerService::class.java))
        fragment.setAutoPlay(true)
        info = mock(StreamInfo::class.java)
        `when`(info.ageLimit).thenReturn(NO_AGE_LIMIT)
        doNothing().`when`(fragment).handleResult(info)
        // Keep the real completion branch and openMainPlayer/intent path, without rendering metadata.
        doAnswer {
            VideoDetailFragment::class.java.getDeclaredMethod("openMainPlayer", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(fragment, false)
            null
        }.`when`(fragment).openVideoPlayerAutoFullscreen()
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
    }

    @After
    fun tearDown() {
        try {
            RxJavaPlugins.reset()
            ViewModelStore().apply {
                put("previous", model)
                clear()
            }
            SerializedCache.getInstance().clear()
            activityController.pause().stop().destroy()
        } finally {
            // Release mock handlers that can retain the fragment's inflated views and Activity.
            framework().clearInlineMocks()
        }
    }

    @Test
    fun explicitReturnStartsTheSavedQueueEvenWhenAutoplayIsNeverAndKeepsItPaused() {
        val original = queue("first", "selected", "last").apply {
            index = 1
            setRecovery(1, 64_321)
        }
        val oldPlayer = mock(Player::class.java)
        `when`(oldPlayer.playQueue).thenReturn(original)
        `when`(oldPlayer.exoPlayerIsNull()).thenReturn(true)
        `when`(oldPlayer.playWhenReady).thenReturn(false)
        model.bindPlayer(oldPlayer)
        model.beforeSelection(oldPlayer, 0, "replacement")
        val previous = model.takePrevious()!!
        original.dispose()
        fragment.playQueue = previous.queue
        fragment.serviceId = 0
        fragment.url = "selected"
        fragment.title = "Selected"
        field(VideoDetailFragment::class.java, "currentInfo", info)

        // A reconnect from the replaced service must not overwrite the pending return queue.
        fragment.onQueueUpdate(queue("replacement"))
        assertEquals(1, fragment.playQueue!!.index)
        assertEquals("selected", fragment.playQueue!!.item!!.url)
        completeMetadata()

        verify(fragment).openVideoPlayerAutoFullscreen()
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertNotNull("Explicit return must reach the player service despite autoplay=never", intent)
        assertTrue(intent.getBooleanExtra(QueueRecovery.RESTORE_EXTRA, false))
        assertFalse(intent.getBooleanExtra(Player.PLAY_WHEN_READY, true))
        val delivered = SerializedCache.getInstance().take(intent.getStringExtra(Player.PLAY_QUEUE_KEY)!!, PlayQueue::class.java)!!
        assertEquals(listOf("first", "selected", "last"), delivered.streams.map { it.url })
        assertEquals(1, delivered.index)
        assertEquals(64_321L, delivered.item!!.recoveryPosition)
        assertFalse(delivered.isDisposed)
    }

    @Test
    fun ordinaryMetadataCompletionStillHonorsAutoplayNever() {
        fragment.playQueue = queue("ordinary")
        fragment.serviceId = 0
        fragment.url = "ordinary"
        fragment.title = "Ordinary"
        completeMetadata()
        verify(fragment, never()).openVideoPlayerAutoFullscreen()
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
    }

    private fun completeMetadata() {
        val network = Single.just(info)
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            extractor.`when`<Single<StreamInfo>> {
                ExtractorHelper.getStreamInfo(anyInt(), anyString(), anyBoolean())
            }.thenReturn(network)
            mockStatic(DownloadedCopyRepository::class.java).use { copies ->
                copies.`when`<Single<StreamInfo>> {
                    DownloadedCopyRepository.streamInfoOrNetwork(activity, fragment.serviceId, fragment.url!!, fragment.title, network)
                }.thenReturn(network)
                VideoDetailFragment::class.java.getDeclaredMethod("runWorker", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(fragment, false, false)
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    private fun queue(vararg urls: String) = LocalMediaPlayQueue(
        urls.map { PlayQueueItem(StreamInfoItem(0, it, it, StreamType.VIDEO_STREAM)) },
        0
    )

    private fun field(type: Class<*>, name: String, value: Any) {
        type.getDeclaredField(name).apply { isAccessible = true }.set(fragment, value)
    }
}
