package org.schabi.newpipe.fragments.detail

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.PlayerUiList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33], qualifiers = "w360dp-h800dp-port")
class FullscreenDetailScrollTest {
    @Test
    fun phoneFullscreenRemovesTheActualAppBarScrollRangeAndRestoresItOnExit() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.DarkTheme)
        val binding = FragmentVideoDetailBinding.inflate(LayoutInflater.from(context))
        binding.detailThumbnailImageView.layoutParams.height = 320
        val fragment = mock(VideoDetailFragment::class.java, CALLS_REAL_METHODS)
        val player = mock(Player::class.java)
        val ui = mock(MainPlayerUi::class.java)
        `when`(player.UIs()).thenReturn(PlayerUiList(ui))
        `when`(player.videoPlayerSelected()).thenReturn(true)
        doReturn(context).`when`(fragment).requireContext()
        doReturn(context.resources).`when`(fragment).resources
        doReturn(binding.root).`when`(fragment).view
        doReturn(Optional.empty<View>()).`when`(fragment).getRoot()
        field(fragment, "binding", binding)
        field(fragment, "player", player)
        val update = VideoDetailFragment::class.java.getDeclaredMethod("updatePinnedPlayerLayout")
            .apply { isAccessible = true }

        fun layout() {
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            )
            binding.root.layout(0, 0, 360, 800)
        }

        `when`(ui.isFullscreen).thenReturn(false)
        update.invoke(fragment)
        layout()
        assertTrue("Embedded details must still scroll", binding.appBarLayout.totalScrollRange > 0)
        repeat(3) {
            `when`(ui.isFullscreen).thenReturn(true)
            update.invoke(fragment)
            layout()
            assertEquals(0, binding.appBarLayout.totalScrollRange)
            val coordinator = binding.appBarLayout.parent as CoordinatorLayout
            val behavior = (binding.appBarLayout.layoutParams as CoordinatorLayout.LayoutParams)
                .behavior as AppBarLayout.Behavior
            // Material may accept a nested scroll solely to update lift/elevation. Verify the
            // actual invariant: neither scroll direction consumes distance or moves the video.
            behavior.onStartNestedScroll(coordinator, binding.appBarLayout, binding.viewPager, binding.viewPager, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            val fullscreenTop = binding.appBarLayout.top
            for (distance in listOf(300, -300)) {
                val consumed = IntArray(2)
                behavior.onNestedPreScroll(coordinator, binding.appBarLayout, binding.viewPager, 0, distance, consumed, ViewCompat.TYPE_TOUCH)
                behavior.onNestedScroll(coordinator, binding.appBarLayout, binding.viewPager, 0, 0, 0, distance, ViewCompat.TYPE_TOUCH, consumed)
                assertEquals(0, consumed[1])
                assertEquals(0, behavior.topAndBottomOffset)
                assertEquals(fullscreenTop, binding.appBarLayout.top)
            }
            behavior.onStopNestedScroll(coordinator, binding.appBarLayout, binding.viewPager, ViewCompat.TYPE_TOUCH)

            `when`(ui.isFullscreen).thenReturn(false)
            update.invoke(fragment)
            layout()
            assertTrue("Leaving fullscreen restores detail scrolling", binding.appBarLayout.totalScrollRange > 0)
            behavior.onStartNestedScroll(coordinator, binding.appBarLayout, binding.viewPager, binding.viewPager, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            val consumed = IntArray(2)
            behavior.onNestedPreScroll(coordinator, binding.appBarLayout, binding.viewPager, 0, 100, consumed, ViewCompat.TYPE_TOUCH)
            assertTrue("Embedded details must consume a scroll after leaving fullscreen", consumed[1] > 0)
            assertTrue("The app bar must move again outside fullscreen", behavior.topAndBottomOffset < 0)
            behavior.onStopNestedScroll(coordinator, binding.appBarLayout, binding.viewPager, ViewCompat.TYPE_TOUCH)
        }
    }

    private fun field(target: Any, name: String, value: Any) {
        VideoDetailFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
}
