/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.children
import androidx.media3.common.Player as Media3Player
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.download.OfflineChapter
import org.schabi.newpipe.download.OfflineDownloadFixture
import org.schabi.newpipe.download.OfflineDownloadMetadata
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Downloader.AsyncCallback
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.NavigationHelper

class OfflineTimestampPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun transcriptTimestampStartsColdLocalPlaybackAndAliasSeekRetainsTheQueueOffline() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val previousDownloader = NewPipe.getDownloader()
        val localization = NewPipe.getPreferredLocalization()
        val country = NewPipe.getPreferredContentCountry()
        val networkRequests = AtomicInteger()
        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response {
                    networkRequests.incrementAndGet()
                    throw IOException("Offline timestamp must not extract network metadata")
                }
                override fun executeAsync(request: Request, callback: AsyncCallback): Nothing {
                    networkRequests.incrementAndGet()
                    throw IOException("Offline timestamp must not extract network metadata")
                }
            },
            localization,
            country
        )
        InfoCache.getInstance().clearCache()
        var bound = false
        var service: PlayerService? = null
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as PlayerService.LocalBinder).service
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        try {
            OfflineDownloadFixture(context).use { fixture ->
                fixture.register()
                val identity = OfflinePlaybackMetadata.resolve(context, fixture.uri()).identity
                val profile = ProfileManager.getActiveProfileId(context)
                ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                    scenario.onActivity {
                        bound = context.bindService(Intent(context, PlayerService::class.java).setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION), connection, Context.BIND_AUTO_CREATE)
                    }
                    assertTrue(bound)
                    assertTrue(connected.await(10, TimeUnit.SECONDS))
                    instrumentation.runOnMainSync { service!!.destroyPlayerAndStopService() }
                    val cold = TimestampChangeData(PlayQueueItem.LOCAL_SERVICE_ID, fixture.uri(), 1, fixture.uri(), identity, profile, 1250)
                    for (invalid in listOf(cold.copy(localIdentity = "replaced-file"), cold.copy(profileId = "old-profile"))) {
                        val failedPlayerCreated = AtomicBoolean()
                        val failedPlayerStopped = CountDownLatch(1)
                        scenario.onActivity {
                            service!!.setPlayerListener { player ->
                                if (player != null) {
                                    failedPlayerCreated.set(true)
                                } else if (failedPlayerCreated.get()) {
                                    failedPlayerStopped.countDown()
                                }
                            }
                            ContextCompat.startForegroundService(context, NavigationHelper.getPlayerTimestampIntent(context, invalid).putExtra(Player.PLAY_WHEN_READY, false))
                        }
                        assertTrue("Failed cold timestamp left an empty player running", failedPlayerStopped.await(10, TimeUnit.SECONDS))
                    }
                    scenario.onActivity {
                        service!!.setPlayerListener(null)
                        ContextCompat.startForegroundService(context, NavigationHelper.getPlayerTimestampIntent(context, cold).putExtra(Player.PLAY_WHEN_READY, false))
                    }
                    waitFor("cold transcript timestamp") {
                        val player = service?.player ?: return@waitFor false
                        !player.exoPlayerIsNull() && player.exoPlayer.playbackState == Media3Player.STATE_READY && player.exoPlayer.currentPosition == 1250L
                    }
                    var queue: org.schabi.newpipe.player.playqueue.PlayQueue? = null
                    instrumentation.runOnMainSync {
                        val player = service!!.player!!
                        queue = player.playQueue
                        assertTrue(player.currentItem!!.isLocalMedia)
                        assertEquals(3, player.currentMetadata!!.streamSegments.size)
                        assertFalse(player.exoPlayer.playWhenReady)
                    }
                    val alias = FileProvider.getUriForFile(context, "${context.packageName}.provider", fixture.video).toString()
                    val second = TimestampChangeData(PlayQueueItem.LOCAL_SERVICE_ID, alias, 5, alias, identity, profile, 5000)
                    scenario.onActivity { context.startService(NavigationHelper.getPlayerTimestampIntent(context, second).putExtra(Player.PLAY_WHEN_READY, false)) }
                    waitFor("same-file alias seek") { service?.player?.exoPlayer?.currentPosition == 5000L }
                    instrumentation.runOnMainSync {
                        assertSame(queue, service!!.player!!.playQueue)
                        assertEquals(1, service!!.player!!.playQueue!!.streams.size)
                        assertFalse(service!!.player!!.exoPlayer.playWhenReady)
                    }
                    // Leave the chapter panel open while a paused queue selection changes metadata.
                    val nextVideo = File(fixture.directory, "next.mp4").apply { writeBytes(fixture.video.readBytes()) }
                    fixture.save(nextVideo, 'v', OfflineDownloadMetadata(0, fixture.source, "Next lesson", "Teacher", 8000, 'v', false, listOf(OfflineChapter("Start", 0), OfflineChapter("New boundary", 3500))))
                    val nextItem = PlayQueueItem.localMedia("Next lesson", fixture.uri(nextVideo), 8, null, null, null, "video/mp4", -1, true, null)
                    instrumentation.runOnMainSync {
                        val player = service!!.player!!
                        player.UIs().get(MainPlayerUi::class.java).get().binding.segmentsButton.performClick()
                        player.playQueue!!.append(listOf(nextItem))
                        player.playQueue!!.index = 1
                    }
                    waitFor("paused chapter metadata switch") {
                        val player = service!!.player!!
                        player.currentItem === nextItem && player.currentMetadata?.streamSegments?.size == 2 && player.exoPlayer.playbackState == Media3Player.STATE_READY
                    }
                    instrumentation.runOnMainSync {
                        val player = service!!.player!!
                        assertFalse(player.exoPlayer.playWhenReady)
                        val binding = player.UIs().get(MainPlayerUi::class.java).get().binding
                        val next = binding.itemsListControl.children.single { it.contentDescription == context.getString(R.string.next_chapter) }
                        assertTrue(next.isEnabled)
                        next.performClick()
                    }
                    waitFor("new paused chapter boundary") { service!!.player!!.exoPlayer.currentPosition == 3500L }
                    assertEquals(0, networkRequests.get())
                    instrumentation.runOnMainSync { service!!.destroyPlayerAndStopService() }
                }
            }
        } finally {
            instrumentation.runOnMainSync { service?.destroyPlayerAndStopService() }
            if (bound) context.unbindService(connection)
            NewPipe.init(previousDownloader, localization, country)
        }
    }

    private fun waitFor(label: String, condition: () -> Boolean) {
        repeat(200) {
            var complete = false
            instrumentation.runOnMainSync { complete = condition() }
            if (complete) return
            Thread.sleep(50)
        }
        error("Timed out waiting for $label")
    }
}
