package org.schabi.newpipe.ui.components.menu

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.device.DeviceInteraction.Companion.setDisplaySize
import androidx.test.espresso.device.EspressoDevice.Companion.onDevice
import androidx.test.espresso.device.rules.DisplaySizeRule
import androidx.test.espresso.device.sizeclass.HeightSizeClass
import androidx.test.espresso.device.sizeclass.WidthSizeClass
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.assertInRange
import org.schabi.newpipe.assertNotInRange
import org.schabi.newpipe.ctx
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.onNodeWithContentDescription
import org.schabi.newpipe.onNodeWithText
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.BackgroundShuffled
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Enqueue
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.PlayWithKodi
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.ShowChannelDetails
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.ShowDetails
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Either
import org.schabi.newpipe.util.Localization

@RunWith(AndroidJUnit4::class)
class LongPressMenuTest {
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

    private fun getLongPressable(
        title: String = "title",
        url: String? = "https://example.com",
        thumbnailUrl: String? = "android.resource://${ctx.packageName}/${R.drawable.placeholder_thumbnail_video}",
        uploader: String? = "uploader",
        uploaderUrl: String? = "https://example.com",
        viewCount: Long? = 42,
        streamType: StreamType? = StreamType.VIDEO_STREAM,
        uploadDate: Either<String, OffsetDateTime>? = Either.left("2026"),
        decoration: LongPressable.Decoration? = LongPressable.Decoration.Duration(9478)
    ) = LongPressable(title, url, thumbnailUrl, uploader, uploaderUrl, viewCount, streamType, uploadDate, decoration)

    private fun setLongPressMenu(
        longPressable: LongPressable = getLongPressable(),
        longPressActions: List<LongPressAction> = LongPressAction.Type.entries.map { it.buildAction { } },
        onDismissRequest: () -> Unit = {},
        isHeaderEnabled: Boolean = true,
        actionArrangement: List<LongPressAction.Type> = LongPressAction.Type.entries
    ) {
        storeIsHeaderEnabledToSettings(ctx, isHeaderEnabled)
        storeLongPressActionArrangementToSettings(ctx, actionArrangement)
        composeRule.setContent {
            var isMenuVisible by rememberSaveable { mutableStateOf(true) }
            if (isMenuVisible) {
                AppTheme {
                    LongPressMenu(longPressable, longPressActions, {
                        isMenuVisible = false
                        onDismissRequest()
                    })
                }
            }
        }
    }

    // the three tests below all call this function to ensure that the editor button is shown
    // independently of the long press menu contents
    private fun assertEditorIsEnteredAndExitedProperly() {
        composeRule.onNodeWithContentDescription(R.string.long_press_menu_enabled_actions_description)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(R.string.edit)
            .performClick()
        composeRule.waitUntil {
            composeRule.onNodeWithText(R.string.long_press_menu_enabled_actions)
                .isDisplayed()
        }

        composeRule.onNodeWithContentDescription(R.string.edit)
            .assertDoesNotExist()
        Espresso.pressBack()
        composeRule.waitUntil {
            composeRule.onNodeWithContentDescription(R.string.edit)
                .isDisplayed()
        }

        composeRule.onNodeWithText(R.string.long_press_menu_enabled_actions)
            .assertDoesNotExist()
    }

    @Test
    fun testEditorButton1() {
        setLongPressMenu(isHeaderEnabled = false, actionArrangement = listOf())
        assertEditorIsEnteredAndExitedProperly()
    }

    @Test
    fun testEditorButton2() {
        setLongPressMenu(isHeaderEnabled = true, actionArrangement = listOf(PlayWithKodi))
        assertEditorIsEnteredAndExitedProperly()
    }

    @Test
    fun testEditorButton3() {
        setLongPressMenu(isHeaderEnabled = true, longPressActions = listOf(), actionArrangement = LongPressAction.Type.entries)
        assertEditorIsEnteredAndExitedProperly()
    }

    @Test
    fun testShowChannelDetails1() {
        var pressedCount = 0
        var dismissedCount = 0
        setLongPressMenu(
            onDismissRequest = { dismissedCount += 1 },
            longPressable = getLongPressable(uploader = "UpLoAdEr"),
            longPressActions = listOf(ShowChannelDetails.buildAction { pressedCount += 1 }),
            actionArrangement = listOf()
        )

        // although ShowChannelDetails is not in the actionArrangement set in user settings (and
        // thus the action will not appear in the menu), the LongPressMenu "knows" how to open a
        // channel because the longPressActions that can be performed contain ShowChannelDetails,
        // therefore the channel name is made clickable in the header
        composeRule.onNodeWithText(R.string.show_channel_details, substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("UpLoAdEr", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("ShowChannelDetails")
            .performClick()
        composeRule.waitUntil { dismissedCount == 1 }
        assertEquals(1, pressedCount)
    }

    @Test
    fun testShowChannelDetails2() {
        var pressedCount = 0
        var dismissedCount = 0
        setLongPressMenu(
            onDismissRequest = { dismissedCount += 1 },
            longPressable = getLongPressable(uploader = null),
            longPressActions = listOf(ShowChannelDetails.buildAction { pressedCount += 1 }),
            actionArrangement = listOf()
        )

        // if the uploader name is not present, we use "Show channel details" as the text for the
        // channel opening link in the header
        composeRule.onNodeWithText(R.string.show_channel_details, substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("ShowChannelDetails")
            .performClick()
        composeRule.waitUntil { dismissedCount == 1 }
        assertEquals(1, pressedCount)
    }

    @Test
    fun testShowChannelDetails3() {
        setLongPressMenu(
            longPressable = getLongPressable(uploader = "UpLoAdEr"),
            longPressActions = listOf(),
            actionArrangement = listOf()
        )
        // the longPressActions that can be performed do not contain ShowChannelDetails, so the
        // LongPressMenu cannot "know" how to open channel details
        composeRule.onNodeWithTag("ShowChannelDetails")
            .assertHasNoClickAction()
    }

    @Test
    fun testShowChannelDetails4() {
        setLongPressMenu(
            longPressable = getLongPressable(uploader = "UpLoAdEr"),
            longPressActions = listOf(ShowChannelDetails.buildAction {}),
            actionArrangement = listOf(ShowChannelDetails)
        )
        // a ShowChannelDetails button is already present among the actions,
        // so the channel name isn't clickable in the header
        composeRule.onNodeWithTag("ShowChannelDetails")
            .assertHasNoClickAction()
    }

    @Test
    fun testHeaderContents() {
        val longPressable = getLongPressable()
        setLongPressMenu(longPressable = longPressable)
        composeRule.onNodeWithText(longPressable.title)
            .assertIsDisplayed()
        composeRule.onNodeWithText(longPressable.uploader!!, substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(longPressable.uploadDate!!.value.toString(), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderViewCount1() {
        setLongPressMenu(getLongPressable(viewCount = 0, streamType = StreamType.VIDEO_STREAM))
        composeRule.onNodeWithText(ctx.getString(R.string.no_views), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderViewCount2() {
        setLongPressMenu(getLongPressable(viewCount = 0, streamType = StreamType.LIVE_STREAM))
        composeRule.onNodeWithText(ctx.getString(R.string.no_one_watching), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderUploadDate1() {
        setLongPressMenu(getLongPressable(uploadDate = Either.left("abcd")))
        composeRule.onNodeWithText("abcd", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderUploadDate2() {
        val date = OffsetDateTime.now()
            .minus(2, ChronoUnit.HOURS)
            .minus(50, ChronoUnit.MILLIS)
        setLongPressMenu(getLongPressable(uploadDate = Either.right(date)))
        composeRule.onNodeWithText("2 hours ago", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(date.toString(), substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun testHeaderDuration() {
        setLongPressMenu(
            longPressable = getLongPressable(decoration = LongPressable.Decoration.Duration(123)),
            isHeaderEnabled = true
        )
        composeRule.onNodeWithTag("LongPressMenuHeader")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("LongPressMenuHeaderThumbnail")
            .assertIsDisplayed()
        composeRule.onNodeWithText(Localization.getDurationString(123))
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderLive() {
        setLongPressMenu(
            longPressable = getLongPressable(decoration = LongPressable.Decoration.Duration(123)),
            isHeaderEnabled = true
        )
        composeRule.onNodeWithTag("LongPressMenuHeader")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("LongPressMenuHeaderThumbnail")
            .assertIsDisplayed()
        composeRule.onNodeWithText(Localization.getDurationString(123))
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderPlaylist() {
        setLongPressMenu(
            longPressable = getLongPressable(decoration = LongPressable.Decoration.Duration(123)),
            isHeaderEnabled = true
        )
        composeRule.onNodeWithTag("LongPressMenuHeader")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("LongPressMenuHeaderThumbnail")
            .assertIsDisplayed()
        composeRule.onNodeWithText(Localization.getDurationString(123))
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderNoDecoration() {
        setLongPressMenu(
            longPressable = getLongPressable(decoration = null),
            isHeaderEnabled = true
        )
        composeRule.onNodeWithTag("LongPressMenuHeader")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("LongPressMenuHeaderThumbnail")
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderHidden() {
        setLongPressMenu(
            longPressable = getLongPressable(decoration = LongPressable.Decoration.Duration(123)),
            isHeaderEnabled = false
        )
        composeRule.onNodeWithTag("LongPressMenuHeader")
            .assertDoesNotExist()
        composeRule.onNodeWithText(Localization.getDurationString(123))
            .assertDoesNotExist()
    }

    @Test
    fun testDurationNotShownIfNoThumbnailInHeader() {
        setLongPressMenu(
            longPressable = getLongPressable(
                thumbnailUrl = null,
                decoration = LongPressable.Decoration.Duration(123)
            )
        )
        composeRule.onNodeWithTag("LongPressMenuHeaderThumbnail")
            .assertDoesNotExist()
        composeRule.onNodeWithText(Localization.getDurationString(123))
            .assertDoesNotExist()
    }

    @Test
    fun testLiveNotShownIfNoThumbnailInHeader() {
        setLongPressMenu(
            longPressable = getLongPressable(
                thumbnailUrl = null,
                decoration = LongPressable.Decoration.Live
            )
        )
        composeRule.onNodeWithTag("LongPressMenuHeaderThumbnail")
            .assertDoesNotExist()
        composeRule.onNodeWithText(R.string.duration_live, ignoreCase = true)
            .assertDoesNotExist()
    }

    @Test
    fun testPlaylistStillShownIfNoThumbnailInHeader() {
        setLongPressMenu(
            longPressable = getLongPressable(
                thumbnailUrl = null,
                decoration = LongPressable.Decoration.Playlist(573)
            )
        )
        composeRule.onNodeWithTag("LongPressMenuHeaderThumbnail")
            .assertDoesNotExist()
        composeRule.onNodeWithText("573")
            .assertIsDisplayed()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    fun testHeaderSpansAllWidthIfSmallScreen() {
        onDevice().setDisplaySize(
            widthSizeClass = WidthSizeClass.COMPACT,
            heightSizeClass = HeightSizeClass.MEDIUM
        )
        setLongPressMenu()
        val row = composeRule
            .onAllNodesWithTag("LongPressMenuGridRow")
            .onFirst()
            .fetchSemanticsNode()
            .boundsInRoot
        val header = composeRule.onNodeWithTag("LongPressMenuHeader")
            .fetchSemanticsNode()
            .boundsInRoot
        assertInRange(row.left, row.left + 24.dp.value, header.left)
        assertInRange(row.right - 24.dp.value, row.right, header.right)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    fun testHeaderIsNotFullWidthIfLargeScreen() {
        onDevice().setDisplaySize(
            widthSizeClass = WidthSizeClass.EXPANDED,
            heightSizeClass = HeightSizeClass.MEDIUM
        )
        setLongPressMenu()
        val row = composeRule
            .onAllNodesWithTag("LongPressMenuGridRow")
            .onFirst()
            .fetchSemanticsNode()
            .boundsInRoot
        val header = composeRule.onNodeWithTag("LongPressMenuHeader")
            .fetchSemanticsNode()
            .boundsInRoot
        assertInRange(row.left, row.left + 24.dp.value, header.left)
        assertNotInRange(row.right - 24.dp.value, row.right, header.right)
    }

    // the tests below all call this function to test, under different conditions, that the shown
    // actions are the intersection between the available and the enabled actions
    fun assertOnlyAndAllArrangedActionsDisplayed(
        availableActions: List<LongPressAction.Type>,
        actionArrangement: List<LongPressAction.Type>,
        expectedShownActions: List<LongPressAction.Type>
    ) {
        setLongPressMenu(
            longPressActions = availableActions.map { it.buildAction {} },
            isHeaderEnabled = ((availableActions.size + actionArrangement.size) % 2) == 0,
            actionArrangement = actionArrangement
        )
        for (type in LongPressAction.Type.entries) {
            composeRule.onNodeWithText(type.label)
                .apply {
                    if (type in expectedShownActions) {
                        assertExists()
                        assertHasClickAction()
                    } else {
                        assertDoesNotExist()
                    }
                }
        }
    }

    @Test
    fun testOnlyAndAllArrangedActionsDisplayed1() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = LongPressAction.Type.entries,
            actionArrangement = listOf(),
            expectedShownActions = listOf()
        )
    }

    @Test
    fun testOnlyAndAllArrangedActionsDisplayed2() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = LongPressAction.Type.entries,
            actionArrangement = listOf(PlayWithKodi, ShowChannelDetails),
            expectedShownActions = listOf(PlayWithKodi, ShowChannelDetails)
        )
    }

    @Test
    fun testOnlyAndAllArrangedActionsDisplayed3() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = LongPressAction.Type.entries,
            actionArrangement = getDefaultEnabledLongPressActions(ctx),
            expectedShownActions = getDefaultEnabledLongPressActions(ctx)
        )
    }

    @Test
    fun testOnlyAndAllAvailableActionsDisplayed1() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = listOf(),
            actionArrangement = LongPressAction.Type.entries,
            expectedShownActions = listOf()
        )
    }

    @Test
    fun testOnlyAndAllAvailableActionsDisplayed2() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = listOf(PlayWithKodi, ShowChannelDetails),
            actionArrangement = LongPressAction.Type.entries,
            expectedShownActions = listOf(PlayWithKodi, ShowChannelDetails)
        )
    }

    @Test
    fun testOnlyAndAllAvailableActionsDisplayed3() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = getDefaultEnabledLongPressActions(ctx),
            actionArrangement = LongPressAction.Type.entries,
            expectedShownActions = getDefaultEnabledLongPressActions(ctx)
        )
    }

    @Test
    fun testOnlyAndAllArrangedAndAvailableActionsDisplayed1() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = listOf(),
            actionArrangement = listOf(),
            expectedShownActions = listOf()
        )
    }

    @Test
    fun testOnlyAndAllArrangedAndAvailableActionsDisplayed2() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = listOf(ShowDetails, ShowChannelDetails),
            actionArrangement = listOf(ShowDetails, Enqueue),
            expectedShownActions = listOf(ShowDetails)
        )
    }

    @Test
    fun testOnlyAndAllArrangedAndAvailableActionsDisplayed3() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = LongPressAction.Type.entries,
            actionArrangement = LongPressAction.Type.entries,
            expectedShownActions = LongPressAction.Type.entries
        )
    }

    @Test
    fun testFewActionsOnLargeScreenAreNotScrollable() {
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = listOf(ShowDetails, ShowChannelDetails),
            actionArrangement = listOf(ShowDetails, ShowChannelDetails),
            expectedShownActions = listOf(ShowDetails, ShowChannelDetails)
        )

        // try to scroll and confirm that items don't move because the menu is not overflowing the
        // screen height
        composeRule.onNodeWithTag("LongPressMenuGrid")
            .assert(hasScrollAction())
        val originalPosition = composeRule.onNodeWithText(ShowDetails.label)
            .fetchSemanticsNode()
            .positionOnScreen
        composeRule.onNodeWithTag("LongPressMenuGrid")
            .performTouchInput { swipeUp() }
        val finalPosition = composeRule.onNodeWithText(ShowDetails.label)
            .fetchSemanticsNode()
            .positionOnScreen
        assertEquals(originalPosition, finalPosition)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    fun testAllActionsOnSmallScreenAreScrollable() {
        onDevice().setDisplaySize(
            widthSizeClass = WidthSizeClass.COMPACT,
            heightSizeClass = HeightSizeClass.COMPACT
        )
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = LongPressAction.Type.entries,
            actionArrangement = LongPressAction.Type.entries,
            expectedShownActions = LongPressAction.Type.entries
        )

        val anItemIsNotVisible = LongPressAction.Type.entries.any {
            composeRule.onNodeWithText(it.label).isNotDisplayed()
        }
        assertEquals(true, anItemIsNotVisible)

        // try to scroll and confirm that items move
        composeRule.onNodeWithTag("LongPressMenuGrid")
            .assert(hasScrollAction())
        val originalPosition = composeRule.onNodeWithText(Enqueue.label)
            .fetchSemanticsNode()
            .positionOnScreen
        composeRule.onNodeWithTag("LongPressMenuGrid")
            .performTouchInput { swipeUp() }
        val finalPosition = composeRule.onNodeWithText(Enqueue.label)
            .fetchSemanticsNode()
            .positionOnScreen
        assertNotEquals(originalPosition, finalPosition)
    }

    @Test
    fun testEnabledDisabledActions() {
        setLongPressMenu(
            longPressActions = listOf(
                ShowDetails.buildAction(enabled = { true }) {},
                Enqueue.buildAction(enabled = { false }) {}
            )
        )
        composeRule.onNodeWithText(ShowDetails.label)
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithText(Enqueue.label)
            .assertIsNotEnabled()
    }

    @Test
    fun testClickingActionDismissesDialog() {
        var pressedCount = 0
        var dismissedCount = 0
        setLongPressMenu(
            onDismissRequest = { dismissedCount += 1 },
            longPressActions = listOf(PlayWithKodi.buildAction { pressedCount += 1 })
        )

        composeRule.onNodeWithText(PlayWithKodi.label)
            .performClick()
        composeRule.waitUntil { dismissedCount == 1 }
        assertEquals(1, pressedCount)
    }

    @Test
    fun testActionLoading() {
        var dismissedCount = 0
        setLongPressMenu(
            onDismissRequest = { dismissedCount += 1 },
            longPressActions = listOf(BackgroundShuffled.buildAction { delay(500) })
        )

        composeRule.onNode(SemanticsMatcher.keyIsDefined(ProgressBarRangeInfo))
            .assertDoesNotExist()
        composeRule.onNodeWithText(BackgroundShuffled.label)
            .performClick()
        composeRule.waitUntil {
            composeRule.onNode(SemanticsMatcher.keyIsDefined(ProgressBarRangeInfo))
                .isDisplayed()
        }
        assertEquals(0, dismissedCount)
        composeRule.waitUntil { dismissedCount == 1 }
    }

    @Test
    fun testActionError() {
        var dismissedCount = 0
        composeRule.activity.setTheme(R.style.DarkTheme)
        setLongPressMenu(
            onDismissRequest = { dismissedCount += 1 },
            longPressActions = listOf(
                BackgroundShuffled.buildAction { throw Throwable("Whatever") }
            )
        )

        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(doesNotExist())
        composeRule.onNodeWithText(BackgroundShuffled.label)
            .performClick()
        composeRule.waitUntil { dismissedCount == 1 }
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText(R.string.error_snackbar_message)))
    }
}
