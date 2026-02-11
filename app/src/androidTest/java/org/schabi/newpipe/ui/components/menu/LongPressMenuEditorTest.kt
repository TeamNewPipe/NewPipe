package org.schabi.newpipe.ui.components.menu

import android.os.Build
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.espresso.device.DeviceInteraction.Companion.setDisplaySize
import androidx.test.espresso.device.EspressoDevice.Companion.onDevice
import androidx.test.espresso.device.rules.DisplaySizeRule
import androidx.test.espresso.device.sizeclass.HeightSizeClass
import androidx.test.espresso.device.sizeclass.WidthSizeClass
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlin.math.absoluteValue
import kotlin.math.sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.assertInRange
import org.schabi.newpipe.ctx
import org.schabi.newpipe.fetchPosOnScreen
import org.schabi.newpipe.inst
import org.schabi.newpipe.onNodeWithContentDescription
import org.schabi.newpipe.onNodeWithText
import org.schabi.newpipe.scrollVerticallyAndGetOriginalAndFinalY
import org.schabi.newpipe.tapAtAbsoluteXY
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Enqueue
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.EnqueueNext
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.PlayWithKodi
import org.schabi.newpipe.ui.theme.AppTheme

@RunWith(AndroidJUnit4::class)
class LongPressMenuEditorTest {
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Test rule for restoring device to its starting display size when a test case finishes.
    // See https://developer.android.com/training/testing/different-screens/tools#resize-displays.
    @get:Rule(order = 2)
    val displaySizeRule: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DisplaySizeRule()
        } else {
            RuleChain.emptyRuleChain()
        }

    /**
     * Sets up the [LongPressMenuEditorPage] in the [composeRule] Compose content for running tests.
     * Handles setting enabled header and actions via shared preferences, and closing the dialog
     * when it is dismissed.
     */
    private fun setEditor(
        onDismissRequest: () -> Unit = {},
        isHeaderEnabled: Boolean = true,
        actionArrangement: List<LongPressAction.Type> = LongPressAction.Type.entries
    ) {
        storeIsHeaderEnabledToSettings(ctx, isHeaderEnabled)
        storeLongPressActionArrangementToSettings(ctx, actionArrangement)
        composeRule.setContent {
            var isEditorVisible by rememberSaveable { mutableStateOf(true) }
            if (isEditorVisible) {
                AppTheme {
                    LongPressMenuEditorPage {
                        isEditorVisible = false
                        onDismissRequest()
                    }
                }
            }
        }
    }

    private fun closeEditorAndAssertNewSettings(
        isHeaderEnabled: Boolean,
        actionArrangement: List<LongPressAction.Type>
    ) {
        composeRule.onNodeWithContentDescription(R.string.back)
            .assertIsDisplayed()
        Espresso.pressBackUnconditionally()
        composeRule.waitUntil {
            composeRule.onNodeWithContentDescription(R.string.back)
                .runCatching { assertDoesNotExist() }
                .isSuccess
        }

        assertEquals(isHeaderEnabled, loadIsHeaderEnabledFromSettings(ctx))
        assertEquals(actionArrangement, loadLongPressActionArrangementFromSettings(ctx))
    }

    /**
     * Checks whether the action (or the header) found by text [label] is above or below the text
     * indicating that all actions below are disabled.
     */
    private fun assertActionEnabledStatus(
        @StringRes label: Int,
        expectedEnabled: Boolean
    ) {
        val buttonBounds = composeRule.onNodeWithText(label)
            .getUnclippedBoundsInRoot()
        val hiddenActionTextBounds = composeRule.onNodeWithText(R.string.long_press_menu_hidden_actions)
            .getUnclippedBoundsInRoot()
        assertEquals(expectedEnabled, buttonBounds.top < hiddenActionTextBounds.top)
    }

    /**
     * The editor should always have all actions visible. Works as expected only if the screen is
     * big enough to hold all items at once, otherwise LazyColumn will hide some lazily.
     */
    private fun assertHeaderAndAllActionsExist() {
        for (label in listOf(R.string.long_press_menu_header) + LongPressAction.Type.entries.map { it.label }) {
            composeRule.onNodeWithText(label)
                .assertExists()
        }
    }

    /**
     * Long-press-and-move is used to change the arrangement of items in the editor. If you pass
     * [longPressDurationMs]`=0` you can simulate the user just dragging across the screen,
     * because there was no long press.
     */
    private fun SemanticsNodeInteraction.longPressThenMove(
        dx: TouchInjectionScope.() -> Int = { 0 },
        dy: TouchInjectionScope.() -> Int = { 0 },
        longPressDurationMs: Long = 1000
    ): SemanticsNodeInteraction {
        return performTouchInput {
            down(center)
            advanceEventTime(longPressDurationMs) // perform long press
            val dy = dy()
            repeat(dy.absoluteValue) {
                moveBy(Offset(0f, dy.sign.toFloat()), 100)
            }
            val dx = dx()
            repeat(dx.absoluteValue) {
                moveBy(Offset(dx.sign.toFloat(), 0f), 100)
            }
            up()
        }
    }

    @Test
    fun pressingBackButtonCallsCallback() {
        var calledCount = 0
        setEditor(onDismissRequest = { calledCount += 1 })
        composeRule.onNodeWithContentDescription(R.string.back)
            .performClick()
        composeRule.waitUntil { calledCount == 1 }
    }

    /**
     * Opens the reset dialog by pressing on the corresponding button, either with DPAD or touch.
     */
    private fun openResetDialog(useDpad: Boolean) {
        if (useDpad) {
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        } else {
            composeRule.onNodeWithContentDescription(R.string.reset_to_defaults)
                .performClick()
        }
        composeRule.waitUntil {
            composeRule.onNodeWithText(R.string.long_press_menu_reset_to_defaults_confirm)
                .isDisplayed()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testEveryActionAndHeaderExists1() {
        // need a large screen to ensure all items are visible
        onDevice().setDisplaySize(WidthSizeClass.EXPANDED, HeightSizeClass.EXPANDED)
        setEditor(isHeaderEnabled = true, actionArrangement = LongPressAction.Type.entries.filter { it.id % 2 == 0 })
        assertHeaderAndAllActionsExist()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testEveryActionAndHeaderExists2() {
        // need a large screen to ensure all items are visible
        onDevice().setDisplaySize(WidthSizeClass.EXPANDED, HeightSizeClass.EXPANDED)
        setEditor(isHeaderEnabled = false, actionArrangement = listOf())
        assertHeaderAndAllActionsExist()
    }

    @Test
    fun testResetButtonCancel() {
        setEditor(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))
        openResetDialog(useDpad = false)

        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = EnqueueNext.label, expectedEnabled = false)
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = false)
        composeRule.onNodeWithText(R.string.cancel)
            .performClick()
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = EnqueueNext.label, expectedEnabled = false)
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = false)
        closeEditorAndAssertNewSettings(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))
    }

    @Test
    fun testResetButtonTapOutside() {
        setEditor(isHeaderEnabled = true, actionArrangement = listOf(Enqueue))
        openResetDialog(useDpad = true)

        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = EnqueueNext.label, expectedEnabled = false)
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = true)
        tapAtAbsoluteXY(200f, 200f)
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = EnqueueNext.label, expectedEnabled = false)
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = true)
        closeEditorAndAssertNewSettings(isHeaderEnabled = true, actionArrangement = listOf(Enqueue))
    }

    @Test
    fun testResetButtonPressBack() {
        setEditor(isHeaderEnabled = false, actionArrangement = listOf())
        openResetDialog(useDpad = false)

        assertActionEnabledStatus(label = EnqueueNext.label, expectedEnabled = false)
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = false)
        Espresso.pressBack()
        assertActionEnabledStatus(label = EnqueueNext.label, expectedEnabled = false)
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = false)
        closeEditorAndAssertNewSettings(isHeaderEnabled = false, actionArrangement = listOf())
    }

    @Test
    fun testResetButtonOk() {
        setEditor(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))
        openResetDialog(useDpad = true)

        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = EnqueueNext.label, expectedEnabled = false)
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = false)
        composeRule.onNodeWithText(R.string.ok)
            .performClick()
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = EnqueueNext.label, expectedEnabled = true)
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = true)
        closeEditorAndAssertNewSettings(isHeaderEnabled = true, actionArrangement = getDefaultLongPressActionArrangement(ctx))
    }

    @Test
    fun testDraggingItemToDisable() {
        setEditor(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = false)
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = PlayWithKodi.label, expectedEnabled = true)
        val kodiOriginalPos = composeRule.onNodeWithText(PlayWithKodi.label).fetchPosOnScreen()

        // long-press then move the Enqueue item down
        composeRule.onNodeWithText(Enqueue.label)
            .longPressThenMove(dy = { 3 * height })

        // assert that only Enqueue was disabled
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = false)
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = false)
        assertActionEnabledStatus(label = PlayWithKodi.label, expectedEnabled = true)

        // assert that the Kodi item moved horizontally but not vertically
        val kodiFinalPos = composeRule.onNodeWithText(PlayWithKodi.label).fetchPosOnScreen()
        assertEquals(kodiOriginalPos.y, kodiFinalPos.y)
        assertNotEquals(kodiOriginalPos.x, kodiFinalPos.x)

        // make sure the new setting is saved
        closeEditorAndAssertNewSettings(isHeaderEnabled = false, actionArrangement = listOf(PlayWithKodi))
    }

    @Test
    fun testDraggingHeaderToDisable() {
        setEditor(isHeaderEnabled = true, actionArrangement = listOf(Enqueue, PlayWithKodi))
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = true)
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = PlayWithKodi.label, expectedEnabled = true)

        // long-press then move the header down
        composeRule.onNodeWithText(R.string.long_press_menu_header)
            .longPressThenMove(dy = { 3 * height })

        // assert that only the header was disabled
        assertActionEnabledStatus(label = R.string.long_press_menu_header, expectedEnabled = false)
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        assertActionEnabledStatus(label = PlayWithKodi.label, expectedEnabled = true)

        // make sure the new setting is saved
        closeEditorAndAssertNewSettings(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testDraggingItemWithoutLongPressOnlyScrolls() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.COMPACT)
        setEditor(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))

        // scroll up by 3 pixels
        val (originalY, finalY) = composeRule.onNodeWithTag("LongPressMenuEditorGrid")
            .scrollVerticallyAndGetOriginalAndFinalY(
                itemInsideScrollingContainer = composeRule.onNodeWithText(Enqueue.label),
                startY = { bottom },
                endY = { bottom - 30 }
            )
        assertInRange(originalY - 40, originalY - 20, finalY)

        // scroll back down by dragging on an item (without long pressing, longPressDurationMs = 0!)
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)
        composeRule.onNodeWithText(Enqueue.label)
            .longPressThenMove(dy = { 3 * height }, longPressDurationMs = 0)
        assertActionEnabledStatus(label = Enqueue.label, expectedEnabled = true)

        // make sure that we are back to the original scroll state
        val posAfterScrollingBack = composeRule.onNodeWithText(Enqueue.label).fetchPosOnScreen()
        assertEquals(originalY, posAfterScrollingBack.y)

        // make sure that the item was not moved
        closeEditorAndAssertNewSettings(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testDraggingItemToBottomScrollsDown() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.COMPACT)
        setEditor(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))
        composeRule.onNodeWithText(PlayWithKodi.label)
            .assertExists()

        // drag the Enqueue item to the bottom of the screen
        val rootBottom = composeRule.onNodeWithTag("LongPressMenuEditorGrid")
            .fetchSemanticsNode()
            .boundsInWindow
            .bottom
        composeRule.onNodeWithText(Enqueue.label)
            .longPressThenMove(dy = { (rootBottom - center.y).toInt() })

        // the Kodi button does not exist anymore because the screen scrolled past it
        composeRule.onNodeWithText(PlayWithKodi.label)
            .assertDoesNotExist()
        composeRule.onNodeWithText(Enqueue.label)
            .assertExists()

        // make sure that Enqueue is now disabled
        closeEditorAndAssertNewSettings(isHeaderEnabled = false, actionArrangement = listOf(PlayWithKodi))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testDraggingItemToTopScrollsUp() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.COMPACT)
        setEditor(isHeaderEnabled = true, actionArrangement = listOf())
        composeRule.onNodeWithText(R.string.long_press_menu_enabled_actions_description)
            .assertExists()

        // scroll grid to the bottom (hacky way to achieve so is to swipe up 10 times)
        composeRule.onNodeWithTag("LongPressMenuEditorGrid")
            .performTouchInput { swipeUp() }
        // the enabled description does not exist anymore because the screen scrolled past it
        composeRule.onNodeWithText(R.string.long_press_menu_enabled_actions_description)
            .assertDoesNotExist()

        // find any action that is now visible on screen
        val actionToDrag = LongPressAction.Type.entries
            .first { composeRule.onNodeWithText(it.label).runCatching { isDisplayed() }.isSuccess }

        // drag it to the top of the screen (using a large dy since going out of the screen bounds
        // does not invalidate the touch gesture)
        composeRule.onNodeWithText(actionToDrag.label)
            .longPressThenMove(dy = { -2000 })

        // the enabled description now should exist again because the view scrolled back up
        composeRule.onNodeWithText(R.string.long_press_menu_enabled_actions_description)
            .assertExists()
        composeRule.onNodeWithText(actionToDrag.label)
            .assertExists()

        // make sure the actionToDrag is now enabled
        closeEditorAndAssertNewSettings(isHeaderEnabled = true, actionArrangement = listOf(actionToDrag))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testDpadScrolling() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.COMPACT)
        setEditor(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))

        composeRule.onNodeWithText(Enqueue.label).assertExists()
        repeat(20) { inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN) }
        composeRule.onNodeWithText(Enqueue.label).assertDoesNotExist() // scrolled down!
        repeat(20) { inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP) }
        composeRule.onNodeWithText(Enqueue.label).assertExists() // scrolled back up!
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testDpadScrollingWhileDraggingHeader() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.COMPACT)
        setEditor(isHeaderEnabled = true, actionArrangement = listOf(Enqueue, PlayWithKodi))
        composeRule.onNodeWithText(R.string.detail_drag_description)
            .assertDoesNotExist()

        // grab the header which is always in top left
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // ensure that the header was picked up by checking the presence of the placeholder
        composeRule.onNodeWithText(R.string.detail_drag_description)
            .assertExists()

        // same checks as in testDpadScrolling
        composeRule.onNodeWithText(Enqueue.label).assertExists()
        repeat(20) { inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN) }
        composeRule.onNodeWithText(Enqueue.label).assertDoesNotExist() // scrolled down!
        repeat(20) { inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP) }
        composeRule.onNodeWithText(Enqueue.label).assertExists() // scrolled back up!
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testDpadDraggingHeader() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.MEDIUM)
        setEditor(isHeaderEnabled = true, actionArrangement = listOf(Enqueue, PlayWithKodi))
        val originalHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()
        composeRule.onNodeWithText(R.string.detail_drag_description)
            .assertDoesNotExist()

        // grab the header which is always in top left
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // the header was grabbed and is thus in an offset position
        val dragHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()
        assertNotEquals(originalHeaderPos.x, dragHeaderPos.x)
        assertNotEquals(originalHeaderPos.y, dragHeaderPos.y)

        // ensure that the header was picked up by checking the presence of the placeholder
        composeRule.onNodeWithText(R.string.detail_drag_description)
            .assertExists()

        // move down a few times and release
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // the header was released in yet another position
        val endHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()
        assertEquals(originalHeaderPos.x, endHeaderPos.x) // always first item
        assertNotEquals(originalHeaderPos.y, endHeaderPos.y)
        assertNotEquals(dragHeaderPos.x, endHeaderPos.x)
        assertNotEquals(dragHeaderPos.y, endHeaderPos.y)

        // make sure the header is now disabled
        closeEditorAndAssertNewSettings(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testDpadDraggingItem() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.MEDIUM)
        setEditor(isHeaderEnabled = true, actionArrangement = listOf(Enqueue, PlayWithKodi))
        val originalHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()
        composeRule.onNodeWithText(R.string.detail_drag_description)
            .assertDoesNotExist()

        // grab the Enqueue item which is just right of the header which is always in top left
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // ensure the header was not picked up by checking that it is still in the same position
        // (though the y might have changed because of scrolling)
        val dragHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()
        assertEquals(originalHeaderPos.x, dragHeaderPos.x)

        // ensure that the Enqueue item was picked up by checking the presence of the placeholder
        composeRule.onNodeWithText(R.string.detail_drag_description)
            .assertExists()

        // move down a few times and release
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // make sure the Enqueue item is now disabled
        closeEditorAndAssertNewSettings(isHeaderEnabled = true, actionArrangement = listOf(PlayWithKodi))
    }

    @Test
    fun testNoneMarkerIsShownIfNoItemsEnabled() {
        setEditor(isHeaderEnabled = false, actionArrangement = listOf())
        assertActionEnabledStatus(R.string.none, true)
    }

    @Test
    fun testNoneMarkerIsShownIfNoItemsDisabled() {
        setEditor(isHeaderEnabled = true, actionArrangement = LongPressAction.Type.entries)
        // scroll grid to the bottom (hacky way to achieve so is to swipe up 10 times)
        composeRule.onNodeWithTag("LongPressMenuEditorGrid")
            .performTouchInput { repeat(10) { swipeUp() } }
        assertActionEnabledStatus(R.string.none, false)
    }

    @Test
    fun testDpadReordering() {
        setEditor(isHeaderEnabled = true, actionArrangement = listOf(Enqueue, PlayWithKodi))

        // grab the Enqueue item which is just right of the header which is always in top left
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // move the item right (past PlayWithKodi) and release it
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // the items now should have swapped
        closeEditorAndAssertNewSettings(isHeaderEnabled = true, actionArrangement = listOf(PlayWithKodi, Enqueue))
    }

    @Test
    fun testDpadHeaderIsAlwaysInFirstPosition() {
        setEditor(isHeaderEnabled = true, actionArrangement = LongPressAction.Type.entries)
        val originalHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()

        // grab the header which is always in top left
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // the header was grabbed and is thus in an offset position
        val dragHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()
        assertNotEquals(originalHeaderPos.x, dragHeaderPos.x)
        assertNotEquals(originalHeaderPos.y, dragHeaderPos.y)

        // even after moving the header around through the enabled actions ...
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        // ... after releasing it its position will still be the original
        val endHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()
        assertEquals(originalHeaderPos.x, endHeaderPos.x)
        assertEquals(originalHeaderPos.y, endHeaderPos.y)

        // nothing should have changed
        closeEditorAndAssertNewSettings(isHeaderEnabled = true, actionArrangement = LongPressAction.Type.entries)
    }

    @Test
    fun testTouchReordering() {
        setEditor(isHeaderEnabled = false, actionArrangement = listOf(Enqueue, PlayWithKodi))

        // move the Enqueue item to the right
        composeRule.onNodeWithText(Enqueue.label)
            .longPressThenMove(dx = { 200.dp.value.toInt() })

        // the items now should have swapped
        closeEditorAndAssertNewSettings(isHeaderEnabled = false, actionArrangement = listOf(PlayWithKodi, Enqueue))
    }

    @Test
    fun testTouchHeaderIsAlwaysInFirstPosition() {
        setEditor(isHeaderEnabled = true, actionArrangement = LongPressAction.Type.entries)
        val originalHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()

        // grab the header and move it around through the enabled actions
        composeRule.onNodeWithText(R.string.long_press_menu_header)
            .longPressThenMove(dx = { 2 * width }, dy = { 2 * height })

        // after releasing it its position will still be the original
        val endHeaderPos = composeRule.onNodeWithText(R.string.long_press_menu_header).fetchPosOnScreen()
        assertEquals(originalHeaderPos.x, endHeaderPos.x)
        assertEquals(originalHeaderPos.y, endHeaderPos.y)

        // nothing should have changed
        closeEditorAndAssertNewSettings(isHeaderEnabled = true, actionArrangement = LongPressAction.Type.entries)
    }
}
