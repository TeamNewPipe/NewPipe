package org.schabi.newpipe.ui.components.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(AndroidJUnit4::class)
class CommentSectionErrorTest {
    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    // Test 1: Network error on initial load (Resource.Error)
    @Test
    fun testInitialCommentNetworkError() {
        val errorInfo = ErrorInfo(
            throwable = SocketTimeoutException("Connection timeout"),
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        Assert.assertEquals(context.getString(R.string.network_error), errorInfo.getMessage(context))
        Assert.assertTrue(errorInfo.isReportable)
        Assert.assertTrue(errorInfo.isRetryable)
        Assert.assertNull(errorInfo.recaptchaUrl)
    }

    // Test 2: Network error on paging (LoadState.Error)
    @Test
    fun testPagingNetworkError() {
        val errorInfo = ErrorInfo(
            throwable = IOException("Paging failed"),
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        Assert.assertEquals(context.getString(R.string.network_error), errorInfo.getMessage(context))
        Assert.assertTrue(errorInfo.isReportable)
        Assert.assertTrue(errorInfo.isRetryable)
        Assert.assertNull(errorInfo.recaptchaUrl)
    }

    // Test 3: ReCaptcha during comments load
    @Test
    fun testReCaptchaDuringComments() {
        val url = "https://www.google.com/recaptcha/api/fallback?k=test"
        val errorInfo = ErrorInfo(
            throwable = ReCaptchaException("ReCaptcha needed", url),
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        Assert.assertEquals(context.getString(R.string.recaptcha_request_toast), errorInfo.getMessage(context))
        Assert.assertEquals(url, errorInfo.recaptchaUrl)
        Assert.assertTrue(errorInfo.isReportable)
        Assert.assertTrue(errorInfo.isRetryable)
    }
}
