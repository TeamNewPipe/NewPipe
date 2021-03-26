package org.schabi.newpipe.error;

import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link ErrorInfo}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ErrorInfoTest {

    @Test
    public void errorInfoTestParcelable() {
        final ErrorInfo info = new ErrorInfo(new ParsingException("Hello"),
                UserAction.USER_REPORT, "request", ServiceList.YouTube.getServiceId());
        // Obtain a Parcel object and write the parcelable object to it:
        final Parcel parcel = Parcel.obtain();
        info.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final ErrorInfo infoFromParcel = (ErrorInfo) ErrorInfo.CREATOR.createFromParcel(parcel);

        assertTrue(Arrays.toString(infoFromParcel.getStackTraces())
                .contains(ErrorInfoTest.class.getSimpleName()));
        assertEquals(UserAction.USER_REPORT, infoFromParcel.getUserAction());
        assertEquals(ServiceList.YouTube.getServiceInfo().getName(),
                infoFromParcel.getServiceName());
        assertEquals("request", infoFromParcel.getRequest());
        assertEquals(R.string.parsing_error, infoFromParcel.getMessageStringId());

        parcel.recycle();
    }
}
