package org.schabi.newpipe.local.subscription

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.SubscriptionGroupsHeaderBinding
import org.schabi.newpipe.local.subscription.item.GroupsHeader

@RunWith(AndroidJUnit4::class)
class GroupsHeaderSearchTest {
    @Test
    fun channelGroupsHeaderProvidesAccessibleSearchAction() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val view = LayoutInflater.from(context)
                .inflate(R.layout.subscription_groups_header, FrameLayout(context), false)
            val binding = SubscriptionGroupsHeaderBinding.bind(view)
            var requested = 0
            GroupsHeader(
                title = "Channel groups",
                onSortClicked = {},
                onSearchClicked = { requested++ },
                onToggleListViewModeClicked = {},
                onToggleCollapsedClicked = {}
            ).bind(binding, 0)
            assertEquals(context.getString(R.string.subscription_groups_search), binding.headerSearch.contentDescription)
            binding.headerSearch.performClick()
            assertEquals(1, requested)
        }
    }
}
