package org.schabi.newpipe.ui.components.menu

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.assertDidNotMove
import org.schabi.newpipe.assertInRange
import org.schabi.newpipe.assertMoved
import org.schabi.newpipe.assertNotInRange
import org.schabi.newpipe.ctx
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.onNodeWithContentDescription
import org.schabi.newpipe.onNodeWithText
import org.schabi.newpipe.scrollVerticallyAndGetOriginalAndFinalY
import org.schabi.newpipe.tapAtAbsoluteXY
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

    /**
     * Utility to build a [LongPressable] with dummy data for testing.
     */
    private fun getLongPressable(
        title: String = "title",
        url: String? = "https://example.com",
        thumbnailUrl: String? = "android.resource://${ctx.packageName}/${R.drawable.placeholder_thumbnail_video}",
        uploader: String? = "uploader",
        viewCount: Long? = 42,
        streamType: StreamType? = StreamType.VIDEO_STREAM,
        uploadDate: Either<String, OffsetDateTime>? = Either.left("2026"),
        decoration: LongPressable.Decoration? = LongPressable.Decoration.Duration(9478)
    ) = LongPressable(title, url, thumbnailUrl, uploader, viewCount, streamType, uploadDate, decoration)

    /**
     * Sets up the [LongPressMenu] in the [composeRule] Compose content for running tests. Handles
     * setting dialog settings via shared preferences, and closing the dialog when it is dismissed.
     */
    private fun setLongPressMenu(
        longPressable: LongPressable = getLongPressable(),
        longPressActions: List<LongPressAction> = LongPressAction.Type.entries.map { LongPressAction(it) { } },
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

    /**
     * The three tests below all call this function to ensure that the editor button is shown
     * independently of the long press menu contents.
     */
    private fun assertEditorIsEnteredAndExitedProperly() {
        composeRule.onNodeWithContentDescription(R.string.long_press_menu_enabled_actions_description)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(R.string.long_press_menu_actions_editor)
            .performClick()
        composeRule.waitUntil {
            composeRule.onNodeWithText(R.string.long_press_menu_enabled_actions_description)
                .isDisplayed()
        }

        composeRule.onNodeWithContentDescription(R.string.back)
            .performClick()
        composeRule.waitUntil {
            composeRule.onNodeWithText(R.string.long_press_menu_enabled_actions_description)
                .isNotDisplayed()
        }
        composeRule.onNodeWithContentDescription(R.string.long_press_menu_actions_editor)
            .assertIsDisplayed()
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
            longPressActions = listOf(LongPressAction(ShowChannelDetails) { pressedCount += 1 }),
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
            longPressActions = listOf(LongPressAction(ShowChannelDetails) { pressedCount += 1 }),
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
            longPressActions = listOf(LongPressAction(ShowChannelDetails) {}),
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
        // here the upload date is an unparsed String we have to use as-is
        // (e.g. the extractor could not parse it)
        setLongPressMenu(getLongPressable(uploadDate = Either.left("abcd")))
        composeRule.onNodeWithText("abcd", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun testHeaderUploadDate2() {
        // here the upload date is a proper OffsetDateTime that can be formatted properly
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

    private fun getFirstRowAndHeaderBounds(): Pair<Rect, Rect> {
        val row = composeRule
            .onAllNodesWithTag("LongPressMenuGridRow")
            .onFirst()
            .fetchSemanticsNode()
            .boundsInRoot
        val header = composeRule.onNodeWithTag("LongPressMenuHeader")
            .fetchSemanticsNode()
            .boundsInRoot
        return Pair(row, header)
    }

    private fun assertAllButtonsSameSize() {
        composeRule.onAllNodesWithTag("LongPressMenuButton")
            .fetchSemanticsNodes()
            .reduce { prev, curr ->
                assertInRange(prev.size.height - 1, prev.size.height + 1, curr.size.height)
                assertInRange(prev.size.width - 1, prev.size.width + 1, curr.size.width)
                return@reduce curr
            }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testHeaderSpansAllWidthIfSmallScreen() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.MEDIUM)
        setLongPressMenu()
        // checks that the header is roughly as large as the row that contains it
        val (row, header) = getFirstRowAndHeaderBounds()
        assertInRange(row.left, row.left + 24.dp.value, header.left)
        assertInRange(row.right - 24.dp.value, row.right, header.right)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testHeaderIsNotFullWidthIfLargeScreen() {
        onDevice().setDisplaySize(WidthSizeClass.EXPANDED, HeightSizeClass.MEDIUM)
        setLongPressMenu()

        // checks that the header is definitely smaller than the row that contains it
        val (row, header) = getFirstRowAndHeaderBounds()
        assertInRange(row.left, row.left + 24.dp.value, header.left)
        assertNotInRange(row.right - 24.dp.value, Float.MAX_VALUE, header.right)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testAllButtonsSameSizeSmallScreen() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.MEDIUM)
        setLongPressMenu()
        assertAllButtonsSameSize()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testAllButtonsSameSizeLargeScreen() {
        onDevice().setDisplaySize(WidthSizeClass.EXPANDED, HeightSizeClass.MEDIUM)
        setLongPressMenu()
        assertAllButtonsSameSize()
    }

    /**
     * The tests below all call this function to test, under different conditions, that the shown
     * actions are the intersection between the available and the enabled actions.
     */
    fun assertOnlyAndAllArrangedActionsDisplayed(
        availableActions: List<LongPressAction.Type>,
        actionArrangement: List<LongPressAction.Type>,
        expectedShownActions: List<LongPressAction.Type>,
        onDismissRequest: () -> Unit = {}
    ) {
        setLongPressMenu(
            longPressActions = availableActions.map { LongPressAction(it) {} },
            // whether the header is enabled or not shouldn't influence the result, so enable it
            // at random (but still deterministically)
            isHeaderEnabled = ((expectedShownActions + availableActions).sumOf { it.id } % 2) == 0,
            actionArrangement = actionArrangement,
            onDismissRequest = onDismissRequest
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
    fun testFewActionsOnNormalScreenAreNotScrollable() {
        var dismissedCount = 0
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = listOf(ShowDetails, ShowChannelDetails),
            actionArrangement = listOf(ShowDetails, ShowChannelDetails),
            expectedShownActions = listOf(ShowDetails, ShowChannelDetails),
            onDismissRequest = { dismissedCount += 1 }
        )

        // try to scroll and confirm that items don't move because the menu is not overflowing the
        // screen height
        composeRule.onNodeWithTag("LongPressMenuGrid")
            .assert(hasScrollAction())
            .scrollVerticallyAndGetOriginalAndFinalY(composeRule.onNodeWithText(ShowDetails.label))
            .assertDidNotMove()

        // also test that clicking on the top of the screen does not close the dialog because it
        // spans all of the screen
        tapAtAbsoluteXY(100f, 100f)
        composeRule.waitUntil { dismissedCount == 1 }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N) // setDisplaySize not available on API < 24
    fun testAllActionsOnSmallScreenAreScrollable() {
        onDevice().setDisplaySize(WidthSizeClass.COMPACT, HeightSizeClass.COMPACT)
        var dismissedCount = 0
        assertOnlyAndAllArrangedActionsDisplayed(
            availableActions = LongPressAction.Type.entries,
            actionArrangement = LongPressAction.Type.entries,
            expectedShownActions = LongPressAction.Type.entries,
            onDismissRequest = { dismissedCount += 1 }
        )

        val anItemIsNotVisible = LongPressAction.Type.entries.any {
            composeRule.onNodeWithText(it.label).isNotDisplayed()
        }
        assertTrue(anItemIsNotVisible)

        // try to scroll and confirm that items move
        composeRule.onNodeWithTag("LongPressMenuGrid")
            .assert(hasScrollAction())
            .scrollVerticallyAndGetOriginalAndFinalY(composeRule.onNodeWithText(Enqueue.label))
            .assertMoved()

        // also test that clicking on the top of the screen does not close the dialog because it
        // spans all of the screen (tap just above the grid bounds on the drag handle, to avoid
        // clicking on an action that would close the dialog)
        val gridBounds = composeRule.onNodeWithTag("LongPressMenuGrid")
            .fetchSemanticsNode()
            .boundsInWindow
        tapAtAbsoluteXY(gridBounds.center.x, gridBounds.top - 1)
        assertTrue(composeRule.runCatching { waitUntil { dismissedCount == 1 } }.isFailure)
    }

    @Test
    fun testEnabledDisabledActions() {
        setLongPressMenu(
            longPressActions = listOf(
                LongPressAction(ShowDetails, enabled = { true }) {},
                LongPressAction(Enqueue, enabled = { false }) {}
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
            longPressActions = listOf(LongPressAction(PlayWithKodi) { pressedCount += 1 })
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
            longPressActions = listOf(LongPressAction(BackgroundShuffled) { delay(500) })
        )

        // test that the loading circle appears while the action is being performed; note that there
        // is no way to test that the long press menu contents disappear, because in the current
        // implementation they just become hidden below the loading circle (with touches suppressed)
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
                LongPressAction(BackgroundShuffled) { throw Throwable("Whatever") }
            )
        )

        // make sure that a snackbar is shown after the dialog gets dismissed,
        // see https://stackoverflow.com/a/33245290
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(doesNotExist())
        composeRule.onNodeWithText(BackgroundShuffled.label)
            .performClick()
        composeRule.waitUntil { dismissedCount == 1 }
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText(R.string.error_snackbar_message)))
    }
}
