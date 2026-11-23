package org.schabi.newpipe.local.subscription

import androidx.preference.PreferenceManager
import com.xwray.groupie.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.local.subscription.item.Header
import org.schabi.newpipe.local.subscription.item.SubscriptionChannelsHeader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SubscriptionChannelsSectionTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun collapsedListRetainsTheLatestRowsAndRestoresThemWhenExpanded() {
        val body = Section().apply {
            add(Header("First"))
            add(Header("Second"))
        }
        val header = SubscriptionChannelsHeader({}, {}, {})
        val section = SubscriptionChannelsSection(context, "first", header, body)
        assertEquals(3, section.itemCount)
        section.toggleCollapsed()
        assertTrue(header.collapsed)
        assertEquals(1, section.itemCount)
        val replacement = Header("Replacement")
        body.update(listOf(replacement))
        assertEquals(1, section.itemCount)
        section.toggleCollapsed()
        assertFalse(header.collapsed)
        assertEquals(2, section.itemCount)
        assertEquals(replacement, section.getItem(1))
    }

    @Test
    fun visibilitySurvivesRecreationWithoutChangingOtherProfilesOrChannelGroups() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().putBoolean(SubscriptionGroupsCollapse.key("first"), true).commit()
        section("first").toggleCollapsed()
        assertTrue(section("first").collapsed)
        assertFalse(section("second").collapsed)
        assertTrue(preferences.getBoolean(SubscriptionGroupsCollapse.key("first"), false))
        section("second").toggleCollapsed()
        section("first").revealSearchResults("Music")
        assertFalse(section("first").collapsed)
        assertTrue(section("second").collapsed)
        assertTrue(preferences.getBoolean(SubscriptionGroupsCollapse.key("first"), false))
    }

    @Test
    fun searchingRevealsChannelsButClearingSearchDoesNotUndoManualCollapse() {
        val section = section("first")
        section.toggleCollapsed()
        section.revealSearchResults("Music")
        assertFalse(section.collapsed)
        section.toggleCollapsed()
        section.revealSearchResults("")
        assertTrue(section.collapsed)
    }

    private fun section(profile: String) = SubscriptionChannelsSection(
        context,
        profile,
        SubscriptionChannelsHeader({}, {}, {}),
        Section()
    )
}
