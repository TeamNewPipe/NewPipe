package org.schabi.newpipe.player.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService

/** Uses the real service owner while keeping each test's player and UI lifetime explicit. */
internal fun withCaptionPlayer(block: (ActivityScenario<AboutActivity>, Player) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val connected = CountDownLatch(1)
    lateinit var service: PlayerService
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as PlayerService.LocalBinder).service
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit
    }
    var bound = false
    var player: Player? = null
    try {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity {
                bound = context.bindService(
                    Intent(context, PlayerService::class.java).setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION),
                    connection,
                    Context.BIND_AUTO_CREATE
                )
            }
            assertTrue("Player service must bind", bound)
            assertTrue("Player service must connect", connected.await(10, TimeUnit.SECONDS))
            scenario.onActivity {
                player = Player(service, service.mediaSession, service.mediaSession.player)
            }
            try {
                block(scenario, player!!)
            } finally {
                instrumentation.runOnMainSync { player?.destroy() }
                player = null
            }
        }
    } finally {
        instrumentation.runOnMainSync { player?.destroy() }
        if (bound) context.unbindService(connection)
    }
}
