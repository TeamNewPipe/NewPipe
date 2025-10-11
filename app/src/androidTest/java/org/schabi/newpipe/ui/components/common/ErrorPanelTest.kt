package org.schabi.newpipe.ui.components.common

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.ui.theme.AppTheme
import java.net.UnknownHostException

@RunWith(AndroidJUnit4::class)
class ErrorPanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setErrorPanel(errorInfo: ErrorInfo, onRetry: (() -> Unit)? = null) {
        composeRule.setContent {
            AppTheme {
                ErrorPanel(errorInfo = errorInfo, onRetry = onRetry)
            }
        }
    }
    private fun text(@StringRes id: Int) = composeRule.activity.getString(id)

    /**
     * Test Network Error
     */
    @Test
    fun testNetworkErrorShowsRetryWithoutReportButton() {
        val networkErrorInfo = ErrorInfo(
            throwable = UnknownHostException("offline"),
            userAction = UserAction.REQUESTED_STREAM,
            request = "https://example.com/watch?v=foo"
        )

        setErrorPanel(networkErrorInfo, onRetry = {})
        composeRule.onNodeWithText(text(R.string.network_error)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.retry), ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.error_snackbar_action), ignoreCase = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.recaptcha_solve), ignoreCase = true)
            .assertDoesNotExist()
    }

    /**
     * Test Unexpected Error, Shows Report and Retry buttons
     */
    @Test
    fun unexpectedErrorShowsReportAndRetryButtons() {
        val unexpectedErrorInfo = ErrorInfo(
            throwable = RuntimeException("Unexpected error"),
            userAction = UserAction.REQUESTED_STREAM,
            request = "https://example.com/watch?v=bar"
        )

        setErrorPanel(unexpectedErrorInfo, onRetry = {})
        composeRule.onNodeWithText(text(R.string.error_snackbar_message)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.retry), ignoreCase = true).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.error_snackbar_action), ignoreCase = true)
            .assertIsDisplayed()
    }

    /**
     * Test Recaptcha Error shows solve, retry and open in browser buttons
     */
    @Test
    fun recaptchaErrorShowsSolveAndRetryOpenInBrowserButtons() {
        var retryClicked = false
        val recaptchaErrorInfo = ErrorInfo(
            throwable = ReCaptchaException(
                "Recaptcha required",
                "https://example.com/captcha"
            ),
            userAction = UserAction.REQUESTED_STREAM,
            request = "https://example.com/watch?v=baz",
            openInBrowserUrl = "https://example.com/watch?v=baz"
        )

        setErrorPanel(
            errorInfo = recaptchaErrorInfo,
            onRetry = { retryClicked = true }

        )
        composeRule.onNodeWithText(text(R.string.recaptcha_solve), ignoreCase = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.retry), ignoreCase = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(text(R.string.open_in_browser), ignoreCase = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.error_snackbar_action), ignoreCase = true)
            .assertDoesNotExist()
        assert(retryClicked) { "onRetry callback should have been invoked" }
    }

    /**
     * Test Content Not Available Error hides retry button
     */
    @Test
    fun testNonRetryableErrorHidesRetryAndReportButtons() {
        val contentNotAvailable = ErrorInfo(
            throwable = ContentNotAvailableException("Video has been removed"),
            userAction = UserAction.REQUESTED_STREAM,
            request = "https://example.com/watch?v=qux"
        )

        setErrorPanel(contentNotAvailable)

        composeRule.onNodeWithText(text(R.string.content_not_available))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.retry), ignoreCase = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.error_snackbar_action), ignoreCase = true)
            .assertDoesNotExist()
    }
}
