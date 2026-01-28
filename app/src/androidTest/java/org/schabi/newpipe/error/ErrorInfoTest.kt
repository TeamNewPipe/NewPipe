package org.schabi.newpipe.error

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * Instrumented tests for {@link ErrorInfo}.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ErrorInfoTest {
    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    /**
     * @param errorInfo the error info to access
     * @return the private field errorInfo.message.stringRes using reflection
     */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun getMessageFromErrorInfo(errorInfo: ErrorInfo): Int {
        val message = ErrorInfo::class.java.getDeclaredField("message")
        message.isAccessible = true
        val messageValue = message.get(errorInfo) as ErrorInfo.Companion.ErrorMessage

        val stringRes = ErrorInfo.Companion.ErrorMessage::class.java.getDeclaredField("stringRes")
        stringRes.isAccessible = true
        return stringRes.get(messageValue) as Int
    }

    @Test
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun errorInfoTestParcelable() {
        val info = ErrorInfo(
            ParsingException("Hello"),
            UserAction.USER_REPORT,
            "request",
            ServiceList.YouTube.serviceId
        )
        // Obtain a Parcel object and write the parcelable object to it:
        val parcel = Parcel.obtain()
        info.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val creatorField = ErrorInfo::class.java.getDeclaredField("CREATOR")
        val creator = creatorField.get(null)
        check(creator is Parcelable.Creator<*>)
        val infoFromParcel = requireNotNull(
            creator.createFromParcel(parcel) as? ErrorInfo
        )
        assertTrue(
            infoFromParcel.stackTraces.contentToString()
                .contains(ErrorInfoTest::class.java.simpleName)
        )
        assertEquals(UserAction.USER_REPORT, infoFromParcel.userAction)
        assertEquals(
            ServiceList.YouTube.serviceInfo.name,
            infoFromParcel.getServiceName()
        )
        assertEquals("request", infoFromParcel.request)
        assertEquals(R.string.parsing_error, getMessageFromErrorInfo(infoFromParcel))

        parcel.recycle()
    }

    /**
     * Test: Network error on initial load (Resource.Error)
     */

    @Test
    fun testInitialCommentNetworkError() {
        val errorInfo = ErrorInfo(
            throwable = SocketTimeoutException("Connection timeout"),
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        assertEquals(context.getString(R.string.network_error), errorInfo.getMessage(context))
        assertTrue(errorInfo.isReportable)
        assertTrue(errorInfo.isRetryable)
        assertNull(errorInfo.recaptchaUrl)
    }

    /**
     * Test: Network error on paging (LoadState.Error)
     */
    @Test
    fun testPagingNetworkError() {
        val errorInfo = ErrorInfo(
            throwable = IOException("Paging failed"),
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        assertEquals(context.getString(R.string.network_error), errorInfo.getMessage(context))
        assertTrue(errorInfo.isReportable)
        assertTrue(errorInfo.isRetryable)
        assertNull(errorInfo.recaptchaUrl)
    }

    /**
     * Test: ReCaptcha during comments load
     */
    @Test
    fun testReCaptchaDuringComments() {
        val url = "https://www.google.com/recaptcha/api/fallback?k=test"
        val errorInfo = ErrorInfo(
            throwable = ReCaptchaException("ReCaptcha needed", url),
            userAction = UserAction.REQUESTED_COMMENTS,
            request = "comments"
        )
        assertEquals(context.getString(R.string.recaptcha_request_toast), errorInfo.getMessage(context))
        assertEquals(url, errorInfo.recaptchaUrl)
        assertFalse(errorInfo.isReportable)
        assertTrue(errorInfo.isRetryable)
    }
}
