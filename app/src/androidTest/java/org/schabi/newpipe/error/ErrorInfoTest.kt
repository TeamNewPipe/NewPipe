package org.schabi.newpipe.error

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import java.util.Arrays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ParsingException

/**
 * Instrumented tests for [ErrorInfo].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ErrorInfoTest {

    /**
     * @param errorInfo the error info to access
     * @return the private field errorInfo.message.stringRes using reflection
     */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun getMessageFromErrorInfo(errorInfo: ErrorInfo): Int {
        val messageField = ErrorInfo::class.java.getDeclaredField("message")
        messageField.isAccessible = true
        val messageValue = messageField.get(errorInfo) as ErrorInfo.Companion.ErrorMessage

        val stringResField = ErrorInfo.Companion.ErrorMessage::class.java.getDeclaredField("stringRes")
        stringResField.isAccessible = true
        return stringResField.get(messageValue) as Int
    }

    @Test
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun errorInfoTestParcelable() {
        val info = ErrorInfo(
            ParsingException("Hello"),
            UserAction.USER_REPORT,
            "request",
            ServiceList.YouTube.getServiceId()
        )
        // Obtain a Parcel object and write the parcelable object to it:
        val parcel = Parcel.obtain()
        info.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val infoFromParcel = ErrorInfo.CREATOR.createFromParcel(parcel)

        assertTrue(
            Arrays.toString(infoFromParcel.stackTraces)
                .contains(ErrorInfoTest::class.java.simpleName)
        )
        assertEquals(UserAction.USER_REPORT, infoFromParcel.userAction)
        assertEquals(
            ServiceList.YouTube.serviceInfo.name,
            infoFromParcel.serviceName
        )
        assertEquals("request", infoFromParcel.request)
        assertEquals(R.string.parsing_error, getMessageFromErrorInfo(infoFromParcel))

        parcel.recycle()
    }
}
