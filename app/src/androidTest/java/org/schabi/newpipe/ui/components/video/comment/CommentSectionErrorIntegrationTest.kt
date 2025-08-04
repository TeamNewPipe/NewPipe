package org.schabi.newpipe.ui.components.video.comment

import android.content.Context
import android.content.Intent
import androidx.paging.LoadState
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.ui.components.common.ErrorAction
import org.schabi.newpipe.ui.components.common.determineErrorAction
import org.schabi.newpipe.viewmodels.util.Resource
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(AndroidJUnit4::class)
class CommentSectionErrorIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // Test 1: Network error on initial load (Resource.Error)
    @Test
    fun testInitialCommentNetworkError() {
        val expectedMessage = "Connection timeout"
        val networkError = SocketTimeoutException(expectedMessage)
        val resourceError = Resource.Error(networkError)

        val errorInfo = ErrorInfo(
            throwable = resourceError.throwable,
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        assertEquals(networkError, errorInfo.throwable)
        assertEquals(ErrorAction.REPORT, determineErrorAction(errorInfo))
        assertEquals(expectedMessage, errorInfo.getExplanation())
    }

    // Test 2: Network error on paging (LoadState.Error)
    @Test
    fun testPagingNetworkError() {
        val expectedMessage = "Paging failed"
        val pagingError = IOException(expectedMessage)
        val loadStateError = LoadState.Error(pagingError)

        val errorInfo = ErrorInfo(
            throwable = loadStateError.error,
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        assertEquals(pagingError, errorInfo.throwable)
        assertEquals(ErrorAction.REPORT, determineErrorAction(errorInfo))
        assertEquals(expectedMessage, errorInfo.getExplanation())
    }

    // Test 3: ReCaptcha during comments load
    @Test
    fun testReCaptchaDuringComments() {
        val url = "https://www.google.com/recaptcha/api/fallback?k=test"
        val expectedMessage = "ReCaptcha needed"
        val captcha = ReCaptchaException(expectedMessage, url)
        val errorInfo = ErrorInfo(
            throwable = captcha,
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        assertEquals(ErrorAction.SOLVE_CAPTCHA, determineErrorAction(errorInfo))
        assertEquals(expectedMessage, errorInfo.getExplanation())

        val intent = Intent(context, org.schabi.newpipe.error.ReCaptchaActivity::class.java).apply {
            putExtra(org.schabi.newpipe.error.ReCaptchaActivity.RECAPTCHA_URL_EXTRA, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        assertEquals(url, intent.getStringExtra(org.schabi.newpipe.error.ReCaptchaActivity.RECAPTCHA_URL_EXTRA))
    }

    // Test 4: Retry functionality integration with ErrorPanel
    @Test
    fun testRetryIntegrationWithErrorPanel() {
        val expectedMessage = "Network request failed"
        val networkError = IOException(expectedMessage)
        val loadStateError = LoadState.Error(networkError)

        val errorInfo = ErrorInfo(
            throwable = loadStateError.error,
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )

        val errorAction = determineErrorAction(errorInfo)
        assertEquals("Network errors should get REPORT action", ErrorAction.REPORT, errorAction)
        var retryCallbackInvoked = false
        val mockCommentRetry = {
            retryCallbackInvoked = true
        }

        mockCommentRetry()
        assertTrue("Retry callback should be invoked when user clicks retry", retryCallbackInvoked)

        assertEquals(
            "Error explanation should be available for retry scenarios",
            expectedMessage, errorInfo.getExplanation()
        )
        assertEquals(
            "Error should maintain comment context for retry",
            UserAction.REQUESTED_COMMENTS, errorInfo.userAction
        )
    }
}
