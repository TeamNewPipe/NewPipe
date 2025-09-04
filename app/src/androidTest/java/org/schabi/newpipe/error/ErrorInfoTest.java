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
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link ErrorInfo}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ErrorInfoTest {

    /**
     * @param errorInfo the error info to access
     * @return the private field errorInfo.message.stringRes using reflection
     */
    private int getMessageFromErrorInfo(final ErrorInfo errorInfo)
            throws NoSuchFieldException, IllegalAccessException {
        final var message = ErrorInfo.class.getDeclaredField("message");
        message.setAccessible(true);
        final var messageValue = (ErrorInfo.Companion.ErrorMessage) message.get(errorInfo);

        final var stringRes = ErrorInfo.Companion.ErrorMessage.class.getDeclaredField("stringRes");
        stringRes.setAccessible(true);
        return (int) Objects.requireNonNull(stringRes.get(messageValue));
    }

    @Test
    public void errorInfoTestParcelable() throws NoSuchFieldException, IllegalAccessException {
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
        assertEquals(R.string.parsing_error, getMessageFromErrorInfo(infoFromParcel));

        parcel.recycle();
    }
}
