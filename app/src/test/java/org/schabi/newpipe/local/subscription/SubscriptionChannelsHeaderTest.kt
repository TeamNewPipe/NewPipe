package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.SubscriptionChannelsHeaderBinding
import org.schabi.newpipe.local.subscription.item.SubscriptionChannelsHeader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], qualifiers = "mdpi")
class SubscriptionChannelsHeaderTest {
    @Test
    fun countStaysNumericAndActionsKeepTheirAccessibleMeaningInBrowseAndEditModes() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.LightTheme)
        controller.setup()
        try {
            val binding = SubscriptionChannelsHeaderBinding.inflate(LayoutInflater.from(activity))
            var edits = 0
            var searches = 0
            var collapses = 0
            val header = SubscriptionChannelsHeader({ edits++ }, { searches++ }, { collapses++ })
            header.count = 42
            header.collapsed = true
            header.bind(binding, 0)
            assertEquals("42", binding.channelsHeaderCount.text.toString())
            assertEquals(activity.resources.getQuantityString(R.plurals.subscription_total_count, 42, 42), binding.channelsHeaderCount.contentDescription)
            assertEquals(activity.getString(R.string.subscription_channels_expand), binding.channelsHeaderCollapse.contentDescription)
            binding.channelsHeaderEdit.performClick()
            binding.channelsHeaderSearch.performClick()
            binding.channelsHeaderCollapse.performClick()
            assertEquals(listOf(1, 1, 1), listOf(edits, searches, collapses))
            header.bindControls(binding, editing = true)
            assertEquals(View.GONE, binding.channelsHeaderCollapse.visibility)
            assertEquals(activity.getString(R.string.playlist_edit_done), binding.channelsHeaderEdit.contentDescription)
            assertTrue(header.collapsed)
            binding.channelsHeaderEdit.performClick()
            binding.channelsHeaderSearch.performClick()
            assertEquals(listOf(2, 2, 1), listOf(edits, searches, collapses))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
