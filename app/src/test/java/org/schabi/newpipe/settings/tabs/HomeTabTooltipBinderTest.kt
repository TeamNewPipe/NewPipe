package org.schabi.newpipe.settings.tabs

import com.google.android.material.tabs.TabLayout
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.schabi.newpipe.R

class HomeTabTooltipBinderTest {
    @Test
    fun pinnedChannelTabUsesChannelNameForLongPressWithoutShowingText() {
        val tab = mock(TabLayout.Tab::class.java)
        HomeTabTooltipBinder.bind(tab, "My channel", R.drawable.ic_tv)

        verify(tab).setText("My channel")
        verify(tab).setTabLabelVisibility(TabLayout.TAB_LABEL_VISIBILITY_UNLABELED)
        verify(tab).setContentDescription("My channel")
        verify(tab).setIcon(R.drawable.ic_tv)
    }

    @Test
    fun updatedTitleReplacesStaleFeedTitle() {
        val tab = mock(TabLayout.Tab::class.java)
        HomeTabTooltipBinder.bind(tab, "Channel videos", R.drawable.ic_tv)

        verify(tab).setText("Channel videos")
        verify(tab).setContentDescription("Channel videos")
    }
}
