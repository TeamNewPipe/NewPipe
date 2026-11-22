package us.shandian.giga.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

@RunWith(AndroidJUnit4::class)
class MissionItemLayoutsTest {
    @Test
    fun bothDownloadsLayoutsProvideRequiredCaptionBadgeAndControls() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (layout in intArrayOf(R.layout.mission_item, R.layout.mission_item_linear)) {
                    val root = LayoutInflater.from(activity).inflate(layout, FrameLayout(activity), false)
                    val badge = root.findViewById<View>(R.id.item_caption_badge)
                    assertNotNull("Missing caption badge in layout $layout", badge)
                    assertEquals(View.GONE, badge.visibility)
                    assertNotNull(root.findViewById<View>(R.id.item_bkg))
                    assertNotNull(root.findViewById<View>(R.id.item_icon))
                    assertNotNull(root.findViewById<View>(R.id.item_chat_badge))
                    assertNotNull(root.findViewById<View>(R.id.item_name))
                    assertNotNull(root.findViewById<View>(R.id.item_size))
                    assertNotNull(root.findViewById<View>(R.id.item_status))
                    assertNotNull(root.findViewById<View>(R.id.item_date))
                    assertNotNull(root.findViewById<View>(R.id.item_more))
                }
            }
        }
    }
}
