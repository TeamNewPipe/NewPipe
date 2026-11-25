package org.schabi.newpipe.local.subscription

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.local.feed.FeedScope

@RunWith(AndroidJUnit4::class)
class SubscriptionEditSelectionTest {
    @Test
    fun selectionUsesChannelKeysAcrossSearchAndDropsDeletedChannels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val binding = FragmentSubscriptionBinding.inflate(LayoutInflater.from(context))
            var notifications = 0
            val controller = SubscriptionSortController(context, "profile", binding, null) { notifications++ }
            try {
                assertTrue(controller.selectedChannels().isEmpty())
                val first = channel(1, "First")
                val second = channel(2, "Second")
                val separator = SubscriptionLayout.Entry("separator:test", "Test")
                controller.update(
                    listOf(first, second),
                    listOf(SubscriptionLayout.Entry(SubscriptionLayout.key(second)), separator, SubscriptionLayout.Entry(SubscriptionLayout.key(first))),
                    FeedScope(FeedScope.ALL_SERVICES, 0)
                )
                controller.setQuery("First")
                controller.controls.subscriptionSortSelect.performClick()
                controller.setQuery("Second")
                controller.controls.subscriptionSortSelect.performClick()
                assertEquals(listOf(2L, 1L), controller.selectedChannels().map { it.uid })
                controller.update(listOf(first), emptyList(), FeedScope(FeedScope.ALL_SERVICES, 0))
                assertEquals(listOf(1L), controller.selectedChannels().map { it.uid })
                controller.controls.subscriptionSortCount.performClick()
                assertTrue(controller.selectedChannels().isEmpty())
                assertTrue(notifications > 0)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun restoredSelectionDoesNotCrossProfilesAndDoneClearsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val binding = FragmentSubscriptionBinding.inflate(LayoutInflater.from(context))
            val state = Bundle()
            val first = channel(1, "First")
            SubscriptionSortController(context, "first-profile", binding, null).also { controller ->
                try {
                    controller.update(listOf(first), emptyList(), FeedScope(FeedScope.ALL_SERVICES, 0))
                    controller.controls.subscriptionSortSelect.performClick()
                    controller.saveState(state)
                    controller.setEnabled(false)
                    assertTrue(controller.selectedChannels().isEmpty())
                } finally {
                    controller.close()
                }
            }
            SubscriptionSortController(context, "second-profile", binding, state).also { controller ->
                try {
                    controller.update(listOf(first), emptyList(), FeedScope(FeedScope.ALL_SERVICES, 0))
                    assertTrue(controller.selectedChannels().isEmpty())
                } finally {
                    controller.close()
                }
            }
        }
    }

    private fun channel(id: Long, name: String) = SubscriptionEntity(
        uid = id,
        serviceId = 0,
        url = "https://example.com/$id",
        name = name
    )
}
