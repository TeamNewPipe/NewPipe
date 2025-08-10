package org.schabi.newpipe.ui.components.common

import androidx.paging.LoadState
import org.junit.Assert
import org.junit.Test
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.SocketTimeoutException

class CommentSectionErrorTest {

    // Test 1: Network error on initial load (Resource.Error)
    @Test
    fun testInitialCommentNetworkError() {
        val expectedMessage = "Connection timeout"
        val networkError = SocketTimeoutException(expectedMessage)

        val errorInfo = ErrorInfo(
            throwable = networkError,
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        Assert.assertEquals(networkError, errorInfo.throwable)
        Assert.assertEquals(ErrorAction.REPORT, determineErrorAction(errorInfo))
        Assert.assertEquals(expectedMessage, errorInfo.getExplanation())
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
        Assert.assertEquals(pagingError, errorInfo.throwable)
        Assert.assertEquals(ErrorAction.REPORT, determineErrorAction(errorInfo))
        Assert.assertEquals(expectedMessage, errorInfo.getExplanation())
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
        Assert.assertEquals(ErrorAction.SOLVE_CAPTCHA, determineErrorAction(errorInfo))
        Assert.assertEquals(expectedMessage, errorInfo.getExplanation())
    }
}
