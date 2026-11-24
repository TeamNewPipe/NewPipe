package org.schabi.newpipe.player.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.NavigationHelper

class FullscreenCommentsSidebarIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun portraitCommentsLeavePlaybackTouchableAndRestoreMargins() = withPanel(false, false, false) { scenario, binding, clicks ->
        onActivity(scenario) { activity ->
            val panel = sidebar(activity)!!
            assertEquals(binding.root.width - 7, binding.playerVideoContent.width)
            assertTrue(binding.playerVideoContent.height < binding.root.height - 11)
            assertTrue(panel.requireDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        }
        tapPlayback(scenario, binding, clicks)
        savePreview("portrait-comments-panel")
        val originalHeight = AtomicInteger()
        val handle = AtomicReference<Rect>()
        onActivity(scenario) { activity ->
            originalHeight.set(sidebar(activity)!!.requireDialog().window!!.attributes.height)
            handle.set(screenBounds(sidebar(activity)!!.requireView().findViewById(R.id.fullscreenCommentsResizeHandle)))
        }
        val bounds = handle.get()
        val distance = 32 * context.resources.displayMetrics.density
        pointerGesture(bounds.exactCenterX(), bounds.exactCenterY(), bounds.exactCenterX(), bounds.exactCenterY() - distance)
        waitFor(scenario) { activity -> sidebar(activity)!!.requireDialog().window!!.attributes.height > originalHeight.get() }
        tapPlayback(scenario, binding, clicks)
        onActivity(scenario) { activity -> sidebar(activity)!!.dismiss() }
        waitFor(scenario) { activity -> sidebar(activity) == null }
        onActivity(scenario) {
            val margins = binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams
            assertEquals(7, margins.marginEnd)
            assertEquals(11, margins.bottomMargin)
        }
    }

    @Test
    fun landscapeLiveChatOverlayKeepsVideoSizeAndPlaybackTouches() = withPanel(true, false, true) { scenario, binding, clicks ->
        onActivity(scenario) { activity ->
            val margins = binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams
            assertEquals(7, margins.marginEnd)
            assertEquals(11, margins.bottomMargin)
            assertTrue(sidebar(activity)!!.childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) is org.schabi.newpipe.fragments.list.chat.LiveChatFragment)
        }
        tapPlayback(scenario, binding, clicks)
        savePreview("landscape-live-chat-panel")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitFor(scenario) { activity -> sidebar(activity) == null }
        onActivity(scenario) { activity ->
            assertFalse(activity.isFinishing)
            assertEquals(1, clicks.get())
        }
    }

    @Test
    fun tvSidebarRetainsRemoteFocusAndOneBackClosesOnlyThePanel() = withPanel(true, true, false) { scenario, binding, clicks ->
        onActivity(scenario) { activity ->
            val panel = sidebar(activity)!!
            val flags = panel.requireDialog().window!!.attributes.flags
            assertEquals(0, flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            assertEquals(0, flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            assertTrue(panel.requireDialog().window!!.decorView.hasWindowFocus())
            assertTrue(binding.playerVideoContent.width < binding.root.width - 7)
        }
        val target = AtomicReference<Rect>()
        onActivity(scenario) { target.set(screenBounds(binding.playPauseButton)) }
        pointerGesture(target.get().exactCenterX(), target.get().exactCenterY())
        onActivity(scenario) { activity ->
            assertEquals("TV focus stays in the comments panel", 0, clicks.get())
            assertTrue(sidebar(activity)!!.requireDialog().window!!.decorView.hasWindowFocus())
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitFor(scenario) { activity -> sidebar(activity) == null }
        onActivity(scenario) { activity -> assertFalse(activity.isFinishing) }
    }

    @Test
    fun commentsAndRepliesStayOutOfNavigationAndOneRemoteBackRestoresTheSameVideo() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldSort = prefs.all["comment_sort_newest"] as? Boolean
        prefs.edit().putBoolean("comment_sort_newest", false).commit()
        val comment = cacheComments()
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        try {
            lateinit var binding: PlayerBinding
            var navigationChanges = 0
            var navigationCount = 0
            onActivity(scenario) { activity ->
                binding = attachPlayer(activity)
                navigationCount = activity.supportFragmentManager.backStackEntryCount
                activity.supportFragmentManager.addOnBackStackChangedListener { navigationChanges++ }
                FullscreenCommentsSidebar.show(activity, 0, URL, "Sidebar test")
                activity.supportFragmentManager.executePendingTransactions()
            }
            waitFor(scenario) { activity ->
                sidebar(activity)?.dialog?.window?.decorView?.hasWindowFocus() == true &&
                    hasReservedPanelSpace(binding)
            }
            onActivity(scenario) { activity ->
                val sidebar = requireNotNull(sidebar(activity))
                assertTrue(sidebar.childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) is CommentsFragment)
                assertEquals(navigationCount, activity.supportFragmentManager.backStackEntryCount)
                assertEquals(0, navigationChanges)
                assertAdaptiveViewport(binding)
                assertTrue(binding.surfaceView.width <= binding.playerVideoContent.width)
                assertTrue(binding.surfaceView.height <= binding.playerVideoContent.height)
                NavigationHelper.openCommentRepliesFragment(activity, comment)
                sidebar.childFragmentManager.executePendingTransactions()
                assertTrue(sidebar.childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) is CommentRepliesFragment)
                assertEquals(navigationCount, activity.supportFragmentManager.backStackEntryCount)
                sidebar.requireView().findViewById<View>(R.id.fullscreenCommentsBack).performClick()
                sidebar.childFragmentManager.executePendingTransactions()
                assertTrue(sidebar.childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) is CommentsFragment)
            }
            savePreview()
            val surface = binding.surfaceView
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor(scenario) { activity -> sidebar(activity) == null }
            onActivity(scenario) { activity ->
                assertFalse(activity.isFinishing)
                assertEquals(0, (binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams).marginEnd)
                assertEquals(0, (binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                assertSame(surface, binding.surfaceView)
                assertEquals(navigationCount, activity.supportFragmentManager.backStackEntryCount)
                assertEquals(0, navigationChanges)
                FullscreenCommentsSidebar.show(activity, 0, URL, "Sidebar test")
                activity.supportFragmentManager.executePendingTransactions()
                assertNotNull(sidebar(activity)?.childFragmentManager?.findFragmentById(R.id.fullscreenCommentsContent)?.view)
            }
            scenario.recreate()
            onActivity(scenario) { activity -> binding = attachPlayer(activity) }
            waitFor(scenario) { activity ->
                sidebar(activity)?.dialog?.window?.decorView?.hasWindowFocus() == true &&
                    hasReservedPanelSpace(binding)
            }
            onActivity(scenario) { activity ->
                assertNotNull(sidebar(activity)?.childFragmentManager?.findFragmentById(R.id.fullscreenCommentsContent)?.view)
                assertAdaptiveViewport(binding)
                NavigationHelper.openCommentRepliesFragment(activity, comment)
                sidebar(activity)!!.childFragmentManager.executePendingTransactions()
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor(scenario) { activity -> sidebar(activity) == null }
            onActivity(scenario) { activity ->
                assertFalse(activity.isFinishing)
                assertEquals(0, (binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams).marginEnd)
                assertEquals(0, (binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
            }
        } finally {
            onActivity(scenario) { it.finish() }
            scenario.close()
            InfoCache.getInstance().removeInfo(0, URL, InfoCache.Type.COMMENTS)
            prefs.edit().apply { oldSort?.let { putBoolean("comment_sort_newest", it) } ?: remove("comment_sort_newest") }.commit()
        }
    }

    private fun attachPlayer(activity: AboutActivity): PlayerBinding = PlayerBinding.inflate(activity.layoutInflater).also {
        it.loadingPanel.visibility = View.GONE
        it.surfaceForeground.setBackgroundColor(0xff204050.toInt())
        it.playbackControlRoot.visibility = View.VISIBLE
        it.titleTextView.text = "Fullscreen video keeps its place"
        it.channelTextView.text = "Comments stay with the video"
        activity.setContentView(it.root)
    }

    private fun sidebar(activity: AboutActivity): FullscreenCommentsDialog? = activity.supportFragmentManager.findFragmentByTag(FullscreenCommentsSidebar.TAG) as? FullscreenCommentsDialog

    private fun withPanel(landscape: Boolean, tv: Boolean, chat: Boolean, block: (ActivityScenario<AboutActivity>, PlayerBinding, AtomicInteger) -> Unit) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val keys = listOf("fullscreen_comments_width_ratio", "fullscreen_comments_height_ratio", "comment_sort_newest", context.getString(R.string.live_chat_landscape_overlay_key), context.getString(R.string.danmaku_enabled_key))
        val previous = keys.associateWith { prefs.all[it] }
        val television = DeviceUtils::class.java.getDeclaredField("isTV").apply { isAccessible = true }
        val originalTv = television.get(null)
        television.set(null, tv)
        prefs.edit().remove(keys[0]).remove(keys[1]).putBoolean(keys[2], false).putBoolean(keys[3], true).commit()
        cacheComments()
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        try {
            val orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            onActivity(scenario) { it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            waitFor(scenario) { it.resources.configuration.orientation == orientation }
            lateinit var binding: PlayerBinding
            val clicks = AtomicInteger()
            onActivity(scenario) { activity ->
                binding = attachPlayer(activity)
                binding.playPauseButton.setOnClickListener { clicks.incrementAndGet() }
                (binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    marginEnd = 7
                    bottomMargin = 11
                }
                if (chat) {
                    // Unsupported fixture service returns unavailable without a network call;
                    // this exercises the real live-chat fragment/window and its controls.
                    FullscreenCommentsSidebar.showLiveChat(activity, -1, "fixture://panel-chat", "Live chat test")
                } else {
                    FullscreenCommentsSidebar.show(activity, 0, URL, "Sidebar test")
                }
                activity.supportFragmentManager.executePendingTransactions()
            }
            waitFor(scenario) { activity ->
                val window = sidebar(activity)?.dialog?.window
                val margins = binding.playerVideoContent.layoutParams as ViewGroup.MarginLayoutParams
                window?.decorView?.hasWindowFocus() == true && window.attributes.width > 0 &&
                    binding.playPauseButton.width > 0 && binding.playerVideoContent.height > 0 &&
                    binding.playerVideoContent.width == binding.root.width - margins.marginEnd &&
                    binding.playerVideoContent.height == binding.root.height - margins.bottomMargin &&
                    (chat || margins.marginEnd > 7 || margins.bottomMargin > 11)
            }
            block(scenario, binding, clicks)
        } finally {
            onActivity(scenario) { activity ->
                sidebar(activity)?.dismissAllowingStateLoss()
                activity.finish()
            }
            scenario.close()
            television.set(null, originalTv)
            InfoCache.getInstance().removeInfo(0, URL, InfoCache.Type.COMMENTS)
            prefs.edit().apply {
                previous.forEach { (key, value) ->
                    when (value) {
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
        }
    }

    private fun tapPlayback(scenario: ActivityScenario<AboutActivity>, binding: PlayerBinding, clicks: AtomicInteger) {
        val target = AtomicReference<Rect>()
        val before = clicks.get()
        onActivity(scenario) { activity ->
            val bounds = screenBounds(binding.playPauseButton)
            val panel = screenBounds(sidebar(activity)!!.requireView())
            assertFalse("Playback target must remain outside the panel", panel.contains(bounds.centerX(), bounds.centerY()))
            target.set(bounds)
        }
        pointerGesture(target.get().exactCenterX(), target.get().exactCenterY())
        waitFor(scenario) { clicks.get() == before + 1 }
    }

    private fun screenBounds(view: View): Rect {
        assertTrue("Target must be visible", view.getGlobalVisibleRect(Rect()))
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun pointerGesture(x: Float, y: Float, endX: Float = x, endY: Float = y) {
        val down = SystemClock.uptimeMillis()
        val events = if (x == endX && y == endY) listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP) else listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)
        events.forEachIndexed { index, action ->
            val event = MotionEvent.obtain(down, down + index * 20L, action, if (index == 0) x else endX, if (index == 0) y else endY, 0)
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun hasReservedPanelSpace(binding: PlayerBinding): Boolean = binding.playerVideoContent.width > 0 && binding.playerVideoContent.height > 0 &&
        (binding.playerVideoContent.width < binding.root.width || binding.playerVideoContent.height < binding.root.height)

    private fun assertAdaptiveViewport(binding: PlayerBinding) {
        val portraitPhone = binding.root.height > binding.root.width &&
            binding.root.width < 600 * binding.root.resources.displayMetrics.density
        if (portraitPhone && !org.schabi.newpipe.util.DeviceUtils.isTv(context)) {
            assertEquals(binding.root.width, binding.playerVideoContent.width)
            assertTrue(binding.playerVideoContent.height in 1 until binding.root.height)
        } else {
            assertTrue(binding.playerVideoContent.width in 1 until binding.root.width)
            assertEquals(binding.root.height, binding.playerVideoContent.height)
        }
    }

    private fun cacheComments(): CommentsInfoItem {
        val comment = CommentsInfoItem(0, URL, "Test comment").apply {
            commentId = "sidebar-comment"
            setCommentText("The video should remain fullscreen while these comments are open.")
            uploaderName = "WizeStream tester"
            uploaderUrl = ""
            uploaderAvatarUrl = ""
            textualUploadDate = "today"
            likeCount = 2
        }
        val constructor = CommentsInfo::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, ListLinkHandler::class.java, String::class.java)
        constructor.isAccessible = true
        val info = constructor.newInstance(0, ServiceList.YouTube.commentsLHFactory.fromUrl(URL), "Sidebar test").apply {
            relatedItems = listOf(comment)
        }
        InfoCache.getInstance().putInfo(0, URL, info, InfoCache.Type.COMMENTS)
        return comment
    }

    private fun savePreview(name: String = "fullscreen-comments-sidebar") {
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val target = "/sdcard/Download/wizestream-player-layout-previews"
        listOf("mkdir -p $target", "cp ${file.absolutePath} $target/").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    private fun waitFor(scenario: ActivityScenario<AboutActivity>, condition: (AboutActivity) -> Boolean) {
        repeat(100) {
            var ready = false
            onActivity(scenario) { ready = condition(it) }
            if (ready) return
            Thread.sleep(50)
        }
        throw AssertionError("Comments panel did not reach its expected state")
    }

    private fun onActivity(scenario: ActivityScenario<AboutActivity>, action: (AboutActivity) -> Unit) {
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        Handler(Looper.getMainLooper()).post {
            try {
                scenario.onActivity(action)
            } catch (failure: Throwable) {
                error.set(failure)
            } finally {
                done.countDown()
            }
        }
        assertTrue("Main-thread action timed out", done.await(10, TimeUnit.SECONDS))
        error.get()?.let { throw it }
    }

    companion object {
        private const val URL = "https://www.youtube.com/watch?v=sidebar0001"
    }
}
