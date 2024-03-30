package org.schabi.newpipe.error

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert
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
    @Test
    fun errorInfoTestParcelable() {
        val info = ErrorInfo(ParsingException("Hello"),
                UserAction.USER_REPORT, "request", ServiceList.YouTube.serviceId)
        // Obtain a Parcel object and write the parcelable object to it:
        val parcel = Parcel.obtain()
        info.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val infoFromParcel = ErrorInfo.CREATOR.createFromParcel(parcel) as ErrorInfo
        Assert.assertTrue(infoFromParcel.stackTraces.contentToString().contains(ErrorInfoTest::class.java.getSimpleName()))
        Assert.assertEquals(UserAction.USER_REPORT, infoFromParcel.userAction)
        Assert.assertEquals(ServiceList.YouTube.serviceInfo.name,
                infoFromParcel.serviceName)
        Assert.assertEquals("request", infoFromParcel.request)
        Assert.assertEquals(R.string.parsing_error.toLong(), infoFromParcel.messageStringId.toLong())
        parcel.recycle()
    }
}
