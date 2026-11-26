/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.detail

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.target.ViewTarget
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.LinkedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.error.ErrorPanelHelper
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.PlaybackPresentationMode
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.MinimizedVideoSurface
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality
import org.schabi.newpipe.views.ExpandableSurfaceView
import org.schabi.newpipe.views.VideoSurfaceContainer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h800dp-port")
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
class MiniPlayerArtworkTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var binding: FragmentVideoDetailBinding
    private lateinit var fragment: VideoDetailFragment
    private lateinit var player: Player
    private lateinit var imageLoader: ImageLoader
    private val requests = mutableListOf<PendingImage>()
    private val queues = mutableListOf<SinglePlayQueue>()
    private val stackField = VideoDetailFragment::class.java.getDeclaredField("stack").apply { isAccessible = true }
    private var previousStack: Any? = null
    private var viewDestroyed = false

    @Before
    fun setUp() {
        // Navigation history is static; keep other fragment fixtures out of this metadata path.
        previousStack = stackField.get(null)
        stackField.set(null, LinkedList<StackItem>())
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java)
        activity = activityController.get()
        activity.setTheme(R.style.DarkTheme)
        activityController.setup()
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        InfoCache.getInstance().clearCache()

        // Keep Coil's real ViewTarget, request replacement and cancellation. Only the image
        // response is controlled, so these tests never contact a thumbnail server.
        val detailBitmap = bitmap(Color.GRAY)
        imageLoader = ImageLoader.Builder(activity)
            .mainCoroutineContext(Dispatchers.Unconfined)
            .coroutineContext(Dispatchers.Unconfined)
            .components {
                add(
                    Interceptor { chain ->
                        val target = (chain.request.target as? ViewTarget<*>)?.view
                        val image = if (target?.id == R.id.overlay_thumbnail) {
                            val pending = PendingImage(chain.request.data.toString())
                            requests += pending
                            try {
                                pending.response.await()
                            } catch (cancelled: CancellationException) {
                                pending.cancelled = true
                                throw cancelled
                            }
                        } else {
                            detailBitmap
                        }
                        SuccessResult(image.asImage(), chain.request)
                    }
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)

        binding = FragmentVideoDetailBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        activityController.visible()
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        )
        binding.root.layout(0, 0, 360, 800)
        assertTrue(binding.overlayThumbnail.isAttachedToWindow)

        fragment = mock(VideoDetailFragment::class.java, CALLS_REAL_METHODS)
        player = mock(Player::class.java)
        doReturn(activity).`when`(fragment).requireContext()
        doReturn(activity.resources).`when`(fragment).resources
        field(fragment, "activity", activity)
        field(fragment, "binding", binding)
        field(fragment, "player", player)
        field(fragment, "errorPanelHelper", mock(ErrorPanelHelper::class.java))
        field(fragment, "detailNavigation", binding.root.findViewById<View>(R.id.detail_navigation))
        field(fragment, "bottomSheetState", BottomSheetBehavior.STATE_COLLAPSED)
        field(fragment, "minimizedVideoSurface", MinimizedVideoSurface())

        val surface = VideoSurfaceContainer(activity, null).apply {
            addView(ExpandableSurfaceView(activity, null).apply { id = R.id.surfaceView })
        }
        binding.playerPlaceholder.addView(surface)
        val playerBinding = mock(PlayerBinding::class.java)
        field(playerBinding, "videoSurfaceContainer", surface)
        val ui = mock(MainPlayerUi::class.java)
        `when`(ui.binding).thenReturn(playerBinding)
        `when`(player.UIs()).thenReturn(PlayerUiList(ui))
        `when`(player.videoPlayerSelected()).thenReturn(true)
        `when`(player.isPlaying).thenReturn(true)
        `when`(player.playbackPresentationMode).thenReturn(PlaybackPresentationMode.VIDEO)
    }

    @After
    fun tearDown() {
        try {
            destroyView()
            activityController.pause().stop().destroy()
            imageLoader.shutdown()
            SingletonImageLoader.reset()
            queues.forEach { it.dispose() }
            InfoCache.getInstance().clearCache()
            ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
            drainMainThread()
        } finally {
            stackField.set(null, previousStack)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun pendingPlaybackArtworkSurvivesDetailLoadingAndAppearsAfterSwitchingToAudio() {
        val media = media("playing")
        val pending = playbackMetadata(media)
        fragment.onPlaybackPresentationModeChanged(PlaybackPresentationMode.VIDEO)
        assertEquals(0f, binding.overlayThumbnail.alpha, 0f)

        fragment.showLoading()
        drainMainThread()
        assertFalse("Refreshing details must not cancel playback artwork", pending.cancelled)
        switchToAudio()
        assertEquals(View.GONE, binding.overlayVideo.visibility)
        assertEquals(1f, binding.overlayThumbnail.alpha, 0f)

        val artwork = bitmap(Color.BLUE)
        pending.response.complete(artwork)
        drainMainThread()
        assertSame(artwork, (binding.overlayThumbnail.drawable as BitmapDrawable).bitmap)
        assertEquals(1, requests.size)
        assertEquals(media.info.name, binding.overlayTitleTextView.text.toString())
    }

    @Test
    fun browsingAnotherDetailsPagePreservesThePlayingQueuesPendingArtwork() {
        val playing = media("playing")
        val pending = playbackMetadata(playing)
        val browsing = media("browsing")
        selectDetails(browsing)

        fragment.showLoading()
        drainMainThread()
        assertFalse(pending.cancelled)
        assertSame(playing.queue, player.playQueue)
        assertEquals(browsing.info.name, binding.detailVideoTitleView.text.toString())
        assertEquals(playing.info.name, binding.overlayTitleTextView.text.toString())
        assertEquals(playing.info.thumbnails.single().url, pending.url)

        switchToAudio()
        val artwork = bitmap(Color.GREEN)
        pending.response.complete(artwork)
        drainMainThread()
        assertSame(artwork, (binding.overlayThumbnail.drawable as BitmapDrawable).bitmap)
        assertEquals(1, requests.size)
    }

    @Test
    fun replacingPlaybackAndDestroyingTheViewStillCancelObsoleteImageRequests() {
        val first = playbackMetadata(media("first"))
        val secondMedia = media("second")
        val second = playbackMetadata(secondMedia)
        assertTrue("New playback metadata must replace the old request", first.cancelled)
        val artwork = bitmap(Color.YELLOW)
        second.response.complete(artwork)
        first.response.complete(bitmap(Color.RED))
        drainMainThread()
        assertSame(artwork, (binding.overlayThumbnail.drawable as BitmapDrawable).bitmap)
        assertEquals(secondMedia.info.name, binding.overlayTitleTextView.text.toString())

        val finalRequest = playbackMetadata(media("final"))
        val beforeTeardown = binding.overlayThumbnail.drawable
        destroyView()
        assertTrue("View teardown must cancel even while its old view is still attached", finalRequest.cancelled)
        finalRequest.response.complete(bitmap(Color.MAGENTA))
        drainMainThread()
        assertSame(beforeTeardown, binding.overlayThumbnail.drawable)
    }

    private fun playbackMetadata(media: Media): PendingImage {
        `when`(player.playQueue).thenReturn(media.queue)
        selectDetails(media)
        fragment.onMetadataUpdate(media.info, media.queue)
        drainMainThread()
        assertEquals(media.info.thumbnails.single().url, requests.last().url)
        return requests.last()
    }

    private fun selectDetails(media: Media) {
        fragment.playQueue = media.queue
        fragment.serviceId = media.info.serviceId
        fragment.url = media.info.url
        fragment.title = media.info.name
        field(fragment, "currentInfo", media.info)
    }

    private fun switchToAudio() {
        `when`(player.videoPlayerSelected()).thenReturn(false)
        `when`(player.isAudioOnly).thenReturn(true)
        `when`(player.UIs()).thenReturn(PlayerUiList())
        `when`(player.playbackPresentationMode).thenReturn(PlaybackPresentationMode.AUDIO_BACKGROUND)
        fragment.onPlaybackPresentationModeChanged(PlaybackPresentationMode.AUDIO_BACKGROUND)
    }

    private fun destroyView() {
        if (!viewDestroyed && ::fragment.isInitialized) {
            fragment.onDestroyView()
            viewDestroyed = true
            drainMainThread()
        }
    }

    private fun media(name: String): Media {
        val url = "https://example.invalid/watch/$name"
        val thumbnails = listOf(Image("https://example.invalid/$name.jpg", 90, 160, Image.ResolutionLevel.MEDIUM))
        val info = mock(StreamInfo::class.java)
        `when`(info.serviceId).thenReturn(0)
        `when`(info.url).thenReturn(url)
        `when`(info.name).thenReturn(name)
        `when`(info.uploaderName).thenReturn("Uploader $name")
        `when`(info.thumbnails).thenReturn(thumbnails)
        val item = StreamInfoItem(0, url, name, StreamType.VIDEO_STREAM).apply {
            ExtractorImageCompat.setThumbnailImages(this, thumbnails)
        }
        val queue = SinglePlayQueue(item).also { queues += it }
        return Media(info, queue)
    }

    private fun bitmap(color: Int): Bitmap = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun drainMainThread() = shadowOf(Looper.getMainLooper()).idle()

    private fun field(target: Any, name: String, value: Any?) {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                type.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        error("Missing field: $name")
    }

    private data class Media(val info: StreamInfo, val queue: SinglePlayQueue)

    private class PendingImage(val url: String) {
        val response = CompletableDeferred<Bitmap>()
        var cancelled = false
    }
}
