package org.schabi.newpipe.info_list.holder

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.util.OnClickGesture

@RunWith(AndroidJUnit4::class)
class StreamCardUploaderTouchTargetsTest {
    @Test
    fun blankUploaderSpaceOpensVideoButAvatarAndNameOpenChannel() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var videoClicks = 0
                var channelClicks = 0
                val builder = InfoItemBuilder(activity)
                builder.onStreamSelectedListener = OnClickGesture { videoClicks++ }
                builder.onChannelSelectedListener = OnClickGesture { channelClicks++ }
                val parent = FrameLayout(activity)
                val holder = StreamInfoItemHolder(builder, parent)
                val stream = StreamInfoItem(
                    0,
                    "https://example.com/video",
                    "Video",
                    StreamType.VIDEO_STREAM
                ).apply {
                    uploaderName = "Test channel"
                    uploaderUrl = "https://www.youtube.com/channel/UCtest"
                }
                holder.updateFromItem(stream, null)

                val root = holder.itemView
                val uploaderRoot = root.findViewById<android.view.View>(R.id.itemUploaderRoot)
                val uploaderName = root.findViewById<android.view.View>(R.id.itemUploaderView)
                val uploaderAvatar = root.findViewById<android.view.View>(R.id.itemUploaderAvatarView)
                assertTrue(root.isClickable)
                assertFalse(uploaderRoot.isClickable)
                assertTrue(uploaderName.isClickable)
                assertTrue(uploaderAvatar.isClickable)
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, uploaderName.layoutParams.width)

                root.performClick()
                assertEquals(1, videoClicks)
                assertEquals(0, channelClicks)
                uploaderName.performClick()
                assertEquals(1, videoClicks)
                assertEquals(1, channelClicks)
                uploaderAvatar.performClick()
                assertEquals(2, channelClicks)

                builder.onChannelSelectedListener = null
                holder.updateFromItem(stream, null)
                assertFalse(uploaderRoot.isClickable)
                assertFalse(uploaderName.isClickable)
                assertFalse(uploaderAvatar.isClickable)
                root.performClick()
                assertEquals(2, videoClicks)
            }
        }
    }
}
