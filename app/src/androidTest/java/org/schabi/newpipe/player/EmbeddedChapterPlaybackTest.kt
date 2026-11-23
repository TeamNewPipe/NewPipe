/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.media3.common.Player as Media3Player
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.download.ChapterTimes
import org.schabi.newpipe.download.EmbeddedChapterFixture
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Downloader.AsyncCallback
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.NavigationHelper

class EmbeddedChapterPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun localPlaybackExposesAndSeeksPreciseEmbeddedChaptersWithoutOnlineMetadata() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val previousDownloader = NewPipe.getDownloader()
        val localization = NewPipe.getPreferredLocalization()
        val country = NewPipe.getPreferredContentCountry()
        val networkRequests = AtomicInteger()
        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response {
                    networkRequests.incrementAndGet()
                    throw IOException("Embedded chapters must remain offline")
                }
                override fun executeAsync(request: Request, callback: AsyncCallback): Nothing {
                    networkRequests.incrementAndGet()
                    throw IOException("Embedded chapters must remain offline")
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
            EmbeddedChapterFixture(context).use { fixture ->
                val identity = OfflinePlaybackMetadata.resolve(context, fixture.uri).identity
                val profile = ProfileManager.getActiveProfileId(context)
                ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                    scenario.onActivity {
                        bound = context.bindService(Intent(context, PlayerService::class.java).setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION), connection, Context.BIND_AUTO_CREATE)
                    }
                    assertTrue(bound)
                    assertTrue(connected.await(10, TimeUnit.SECONDS))
                    instrumentation.runOnMainSync { service!!.destroyPlayerAndStopService() }
                    val timestamp = TimestampChangeData(PlayQueueItem.LOCAL_SERVICE_ID, fixture.uri, 1, fixture.uri, identity, profile, 1500)
                    scenario.onActivity {
                        ContextCompat.startForegroundService(context, NavigationHelper.getPlayerTimestampIntent(context, timestamp).putExtra(Player.PLAY_WHEN_READY, false))
                    }
                    var ready = false
                    for (attempt in 0 until 200) {
                        instrumentation.runOnMainSync {
                            val player = service?.player
                            ready = player != null && !player.exoPlayerIsNull() && player.exoPlayer.playbackState == Media3Player.STATE_READY &&
                                player.exoPlayer.currentPosition == 1500L && player.currentMetadata?.streamSegments?.size == 3
                        }
                        if (ready) break
                        Thread.sleep(50)
                    }
                    assertTrue("Local media and its embedded chapters must become ready", ready)
                    instrumentation.runOnMainSync {
                        val player = service!!.player!!
                        assertTrue(player.currentItem!!.isLocalMedia)
                        assertEquals(listOf(0L, 1500L, 3500L), player.currentMetadata!!.streamSegments.map(ChapterTimes::startMillis))
                        assertFalse(player.currentMetadata!!.maybeStreamInfo.isPresent)
                        assertFalse(player.exoPlayer.playWhenReady)
                    }
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
}
