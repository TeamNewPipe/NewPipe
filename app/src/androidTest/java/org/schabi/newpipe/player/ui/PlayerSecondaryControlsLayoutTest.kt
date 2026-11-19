package org.schabi.newpipe.player.ui

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class PlayerSecondaryControlsLayoutTest {
    @Test
    fun metadataExpandsBeforeSecondaryControlsAtLargeTextSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val root = LayoutInflater.from(context)
                .inflate(R.layout.player, FrameLayout(context), false)

            val playbackControlRoot = root.findViewById<View>(R.id.playbackControlRoot)
            val primaryControls = root.findViewById<View>(R.id.primaryControls)
            val metadataControls = root.findViewById<View>(R.id.metadataControls)
            val metadata = root.findViewById<View>(R.id.metadataView)
            val secondaryControls = root.findViewById<View>(R.id.secondaryControls)
            val title = root.findViewById<TextView>(R.id.titleTextView)
            val channel = root.findViewById<TextView>(R.id.channelTextView)

            playbackControlRoot.visibility = View.VISIBLE
            metadata.visibility = View.VISIBLE
            secondaryControls.visibility = View.VISIBLE
            title.text = "A long fullscreen title that needs the complete metadata row"
            channel.text = "A channel name that must remain fully visible"
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            channel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)

            measureAndLayout(root, dp(context, 640), dp(context, 288))

            assertTrue(channel.bottom <= metadata.height)
            assertTrue(metadataControls.top + metadata.bottom <= primaryControls.height)
            assertTrue(primaryControls.bottom <= secondaryControls.top)
        }
    }

    @Test
    fun captionAndAbControlsStayVisibleAtNarrowPhoneWidth() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val root = LayoutInflater.from(context)
                .inflate(R.layout.player, FrameLayout(context), false)

            val playbackControlRoot = root.findViewById<View>(R.id.playbackControlRoot)
            val secondaryControls =
                root.findViewById<HorizontalScrollView>(R.id.secondaryControls)
            val caption = root.findViewById<View>(R.id.captionTextView)
            val abRepeat = root.findViewById<View>(R.id.abRepeatButton)

            playbackControlRoot.visibility = View.VISIBLE
            secondaryControls.visibility = View.VISIBLE
            caption.visibility = View.VISIBLE
            abRepeat.visibility = View.VISIBLE

            // Stress the row with every optional secondary action visible. Captions must remain in
            // the initial viewport while later actions are still reachable by horizontal scrolling.
            listOf(
                R.id.playWithKodi,
                R.id.openInBrowser,
                R.id.share,
                R.id.learningNoteButton,
                R.id.sleepTimerButton,
                R.id.equalizerButton,
                R.id.listenModeButton,
                R.id.switchMute,
                R.id.fullScreenButton
            ).forEach { root.findViewById<View>(it).visibility = View.VISIBLE }

            measureAndLayout(root, dp(context, 320), dp(context, 180))

            val content = secondaryControls.getChildAt(0)
            val captionContainer = caption.parent as View
            val captionRightInContent = captionContainer.left + caption.right

            assertEquals(0, secondaryControls.scrollX)
            assertTrue(caption.width >= dp(context, 48))
            assertTrue(caption.height >= dp(context, 48))
            assertTrue(captionRightInContent <= secondaryControls.width)
            assertTrue(abRepeat.right <= secondaryControls.width)
            assertTrue(content.width > secondaryControls.width)
            // Portrait actions beyond the initial viewport must be discoverable and reachable.
            assertTrue(secondaryControls.isHorizontalScrollBarEnabled)
            assertFalse(secondaryControls.isScrollbarFadingEnabled)
            secondaryControls.scrollTo(content.width - secondaryControls.width, 0)
            val mute = root.findViewById<View>(R.id.switchMute)
            assertTrue(mute.right - secondaryControls.scrollX <= secondaryControls.width)
        }
    }

    @Test
    fun longAudioTrackLabelFitsInNarrowPortraitControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val root = LayoutInflater.from(context)
                .inflate(R.layout.player, FrameLayout(context), false)

            val playbackControlRoot = root.findViewById<View>(R.id.playbackControlRoot)
            val primaryControls = root.findViewById<View>(R.id.primaryControls)
            val metadataControls = root.findViewById<View>(R.id.metadataControls)
            val audioContainer = root.findViewById<View>(R.id.audioTrackContainer)
            val audioTrack = root.findViewById<TextView>(R.id.audioTrackTextView)

            playbackControlRoot.visibility = View.VISIBLE
            audioTrack.visibility = View.VISIBLE
            audioTrack.text = "English (United States) original"

            measureAndLayout(root, dp(context, 320), dp(context, 640))

            val audioRightInPrimary =
                metadataControls.left + audioContainer.left + audioTrack.right
            assertTrue(audioTrack.width >= dp(context, 48))
            assertTrue(audioTrack.width <= dp(context, 112))
            assertTrue(audioRightInPrimary <= primaryControls.width)
        }
    }

    private fun measureAndLayout(root: View, widthPixels: Int, heightPixels: Int) {
        val width = View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(heightPixels, View.MeasureSpec.EXACTLY)
        root.measure(width, height)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
