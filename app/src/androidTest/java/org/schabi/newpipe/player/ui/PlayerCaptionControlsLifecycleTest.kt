package org.schabi.newpipe.player.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.download.OfflineDownloadFixture
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.player.helper.CustomRenderersFactory
import org.schabi.newpipe.player.resolver.OfflineSubtitleSources

class PlayerCaptionControlsLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun reattachedPortraitUiRestoresRealCaptionTracksAndHonorsHiddenActionPreferences() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hiddenKey = context.getString(R.string.visible_player_actions_key)
        val orderKey = context.getString(R.string.portrait_player_actions_key)
        val previousHidden = preferences.getStringSet(hiddenKey, null)?.toSet()
        val previousOrder = preferences.getString(orderKey, null)
        val previousSecondary = preferences.getString(SECONDARY_PREFERENCE, null)
        try {
            preferences.edit().remove(hiddenKey).remove(orderKey).remove(SECONDARY_PREFERENCE).commit()
            OfflineDownloadFixture(context).use { media ->
                media.register()
                val offline = OfflinePlaybackMetadata.resolve(context, media.uri())
                assertFalse("The fixture must expose a real local caption track", offline.subtitles.isEmpty())
                withCaptionPlayer { scenario, player ->
                    lateinit var binding: PlayerBinding
                    lateinit var host: FrameLayout
                    lateinit var engine: ExoPlayer
                    lateinit var selector: DefaultTrackSelector
                    lateinit var ui: MainPlayerUi
                    val prepared = CountDownLatch(1)
                    val failure = AtomicReference<PlaybackException>()
                    scenario.onActivity { activity ->
                        val configuration = Configuration(player.context.resources.configuration).apply {
                            orientation = Configuration.ORIENTATION_PORTRAIT
                            screenWidthDp = 320
                            screenHeightDp = 640
                        }
                        val themed = ContextThemeWrapper(player.context.createConfigurationContext(configuration), R.style.LightTheme)
                        binding = PlayerBinding.inflate(LayoutInflater.from(themed))
                        // The fixture controls playback directly, so it also dismisses loading.
                        // Caption visibility is only ever set by the real UI's track callbacks.
                        binding.loadingPanel.visibility = View.GONE
                        host = FrameLayout(activity)
                        host.addView(binding.root, FrameLayout.LayoutParams(dp(binding.root, 320), dp(binding.root, 180)))
                        activity.setContentView(host)
                        selector = DefaultTrackSelector(themed)
                        engine = ExoPlayer.Builder(
                            themed,
                            CustomRenderersFactory(themed, false, player.visualizerAudioProcessor)
                        ).setTrackSelector(selector).build().apply {
                            trackSelectionParameters = trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                            addListener(object : Media3Player.Listener {
                                override fun onTracksChanged(tracks: Tracks) {
                                    player.UIs().call { it.onTextTracksChanged(tracks) }
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Media3Player.STATE_READY) prepared.countDown()
                                }

                                override fun onPlayerError(error: PlaybackException) {
                                    failure.set(error)
                                    prepared.countDown()
                                }
                            })
                        }
                        player.setExoPlayerForLifecycle(engine)
                        player.setTrackSelectorForLifecycle(selector)
                        ui = MainPlayerUi(player, binding)
                        player.UIs().addAndPrepare(ui)
                        assertEquals("No mapped captions exist before preparation", View.GONE, binding.captionTextView.visibility)

                        // Main/popup transitions reuse this binding. Let tracks arrive in the
                        // interval with no video UI, so only initPlayer's replay can restore CC.
                        player.UIs().destroyAll(MainPlayerUi::class.java)
                        val data = DefaultDataSource.Factory(themed)
                        val source = ProgressiveMediaSource.Factory(data)
                            .createMediaSource(MediaItem.fromUri(media.uri()))
                        engine.setMediaSource(OfflineSubtitleSources.merge(themed, source, offline.subtitles, data))
                        engine.prepare()
                    }
                    assertTrue("Local caption media must prepare", prepared.await(20, TimeUnit.SECONDS))
                    assertNull(failure.get())
                    scenario.onActivity {
                        assertNotNull(selector.currentMappedTrackInfo)
                        assertTrue(engine.currentTracks.isTypeSupported(C.TRACK_TYPE_TEXT, false))
                        assertEquals("The detached binding retains its unavailable state", View.GONE, binding.captionTextView.visibility)
                        host.addView(binding.root, FrameLayout.LayoutParams(dp(binding.root, 320), dp(binding.root, 180)))
                        ui = MainPlayerUi(player, binding)
                        player.UIs().addAndPrepare(ui)
                        assertEquals("Attach must replay existing text tracks", View.VISIBLE, binding.captionTextView.visibility)
                        assertTrue("Attach must also rebuild the caption menu", captionMenuLanguages(ui).isNotEmpty())
                        binding.moreOptionsButton.performClick()
                    }
                    waitFor("Portrait secondary controls must open") {
                        binding.secondaryControls.isShown && binding.secondaryControls.alpha == 1f
                    }
                    scenario.onActivity {
                        val viewport = Rect(0, 0, binding.secondaryControls.width, binding.secondaryControls.height)
                        val caption = Rect(0, 0, binding.captionTextView.width, binding.captionTextView.height)
                        binding.secondaryControls.offsetDescendantRectToMyCoords(binding.captionTextView, caption)
                        assertTrue(binding.captionTextView.isShown)
                        assertEquals(0, binding.secondaryControls.scrollX)
                        assertTrue("CC must be inside the initial narrow portrait viewport", viewport.contains(caption))
                        assertTrue(binding.captionTextView.width >= dp(binding.root, 48))
                        assertTrue(binding.captionTextView.height >= dp(binding.root, 48))

                        PlayerActionVisibility.save(context, setOf("captions"))
                        ui.onTextTracksChanged(engine.currentTracks)
                        assertEquals("A track replay must respect the user's hidden action", View.GONE, binding.captionTextView.visibility)
                        PlayerActionVisibility.save(context, emptySet())
                    }
                    waitFor("Unhiding must recover the track-derived availability") {
                        binding.captionTextView.visibility == View.VISIBLE
                    }
                    scenario.onActivity {
                        // A subsequent no-caption item must clear availability again.
                        ui.onTextTracksChanged(Tracks.EMPTY)
                        assertEquals(View.GONE, binding.captionTextView.visibility)
                        ui.onTextTracksChanged(engine.currentTracks)
                        assertEquals(View.VISIBLE, binding.captionTextView.visibility)
                    }
                }
            }
        } finally {
            preferences.edit().apply {
                if (previousHidden == null) remove(hiddenKey) else putStringSet(hiddenKey, previousHidden)
                if (previousOrder == null) remove(orderKey) else putString(orderKey, previousOrder)
                if (previousSecondary == null) remove(SECONDARY_PREFERENCE) else putString(SECONDARY_PREFERENCE, previousSecondary)
            }.commit()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun captionMenuLanguages(ui: MainPlayerUi): List<String> = VideoPlayerUi::class.java
        .getDeclaredField("captionMenuLanguages").apply { isAccessible = true }.get(ui) as List<String>

    private fun waitFor(message: String, condition: () -> Boolean) {
        var satisfied = false
        for (attempt in 0 until 100) {
            instrumentation.runOnMainSync { satisfied = condition() }
            if (satisfied) return
            Thread.sleep(25)
        }
        assertTrue(message, satisfied)
    }

    private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()

    private companion object {
        const val SECONDARY_PREFERENCE = "dual_captions_secondary_language"
    }
}
