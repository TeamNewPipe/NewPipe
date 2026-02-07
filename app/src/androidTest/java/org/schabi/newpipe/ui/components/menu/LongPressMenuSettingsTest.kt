package org.schabi.newpipe.ui.components.menu

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.clearPrefs
import org.schabi.newpipe.ctx
import org.schabi.newpipe.putBooleanInPrefs
import org.schabi.newpipe.putStringInPrefs
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Background
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Enqueue
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.EnqueueNext
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.MarkAsWatched
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.PlayWithKodi
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.ShowChannelDetails
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.ShowDetails

@RunWith(AndroidJUnit4::class)
class LongPressMenuSettingsTest {

    @Test
    fun testStoringAndLoadingPreservesIsHeaderEnabled() {
        for (enabled in arrayOf(false, true)) {
            storeIsHeaderEnabledToSettings(ctx, enabled)
            assertEquals(enabled, loadIsHeaderEnabledFromSettings(ctx))
        }
    }

    @Test
    fun testStoringAndLoadingPreservesActionArrangement() {
        for (actions in listOf(
            listOf(),
            LongPressAction.Type.entries.toList(),
            listOf(Enqueue, EnqueueNext, MarkAsWatched, ShowChannelDetails),
            listOf(PlayWithKodi)
        )) {
            storeLongPressActionArrangementToSettings(ctx, actions)
            assertEquals(actions, loadLongPressActionArrangementFromSettings(ctx))
        }
    }

    @Test
    fun testLoadingActionArrangementUnset() {
        clearPrefs()
        assertEquals(getDefaultEnabledLongPressActions(ctx), loadLongPressActionArrangementFromSettings(ctx))
    }

    @Test
    fun testLoadingActionArrangementInvalid() {
        putStringInPrefs(R.string.long_press_menu_action_arrangement_key, "0,1,whatever,3")
        assertEquals(getDefaultEnabledLongPressActions(ctx), loadLongPressActionArrangementFromSettings(ctx))
    }

    @Test
    fun testLoadingActionArrangementEmpty() {
        putStringInPrefs(R.string.long_press_menu_action_arrangement_key, "")
        assertEquals(listOf<LongPressAction.Type>(), loadLongPressActionArrangementFromSettings(ctx))
    }

    @Test
    fun testLoadingActionArrangementDuplicates() {
        putStringInPrefs(R.string.long_press_menu_action_arrangement_key, "0,1,0,3,2,3,3,3,0")
        assertEquals(
            // deduplicates items but retains order
            listOf(ShowDetails, Enqueue, Background, EnqueueNext),
            loadLongPressActionArrangementFromSettings(ctx)
        )
    }

    @Test
    fun testDefaultActionsIncludeKodiIffShowKodiEnabled() {
        for (enabled in arrayOf(false, true)) {
            putBooleanInPrefs(R.string.show_play_with_kodi_key, enabled)
            val actions = getDefaultEnabledLongPressActions(ctx)
            assertEquals(enabled, actions.contains(PlayWithKodi))
        }
    }

    @Test
    fun testAddOrRemoveKodiLongPressAction() {
        for (enabled in arrayOf(false, true)) {
            putBooleanInPrefs(R.string.show_play_with_kodi_key, enabled)
            for (actions in listOf(listOf(Enqueue), listOf(Enqueue, PlayWithKodi))) {
                storeLongPressActionArrangementToSettings(ctx, actions)
                addOrRemoveKodiLongPressAction(ctx)
                val newActions = getDefaultEnabledLongPressActions(ctx)
                assertEquals(enabled, newActions.contains(PlayWithKodi))
            }
        }
    }
}
