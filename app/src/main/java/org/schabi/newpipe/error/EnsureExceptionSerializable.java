package org.schabi.newpipe.error;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ensures that a Exception is serializable.
 * This is
 */
public final class EnsureExceptionSerializable {
    private static final String TAG = "EnsureExSerializable";

    private EnsureExceptionSerializable() {
        // No instance
    }

    /**
     * Ensures that an exception is serializable.
     * <br/>
     * If that is not the case a {@link WorkaroundNotSerializableException} is created.
     *
     * @param exception
     * @return if an exception is not serializable a new {@link WorkaroundNotSerializableException}
     * otherwise the exception from the parameter
     */
    public static Exception ensureSerializable(@NonNull final Exception exception) {
        return checkIfSerializable(exception)
                ? exception
                : WorkaroundNotSerializableException.create(exception);
    }

    public static boolean checkIfSerializable(@NonNull final Exception exception) {
        try {
            // Check by creating a new ObjectOutputStream which does the serialization
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(bos)
            ) {
                oos.writeObject(exception);
                oos.flush();

                bos.toByteArray();
            }

            return true;
        } catch (final IOException ex) {
            Log.d(TAG, "Exception is not serializable", ex);
            return false;
        }
    }

    public static class WorkaroundNotSerializableException extends Exception {
        protected WorkaroundNotSerializableException(
                final Throwable notSerializableException,
                final Throwable cause) {
            super(notSerializableException.toString(), cause);
            setStackTrace(notSerializableException.getStackTrace());
        }

        protected WorkaroundNotSerializableException(final Throwable notSerializableException) {
            super(notSerializableException.toString());
            setStackTrace(notSerializableException.getStackTrace());
        }

        public static WorkaroundNotSerializableException create(
                @NonNull final Exception notSerializableException
        ) {
            // Build a list of the exception + all causes
            final List<Throwable> throwableList = new ArrayList<>();

            int pos = 0;
            Throwable throwableToProcess = notSerializableException;

            while (throwableToProcess != null) {
                throwableList.add(throwableToProcess);

                pos++;
                throwableToProcess = throwableToProcess.getCause();
            }

            // Reverse list so that it starts with the last one
            Collections.reverse(throwableList);

            // Build exception stack
            WorkaroundNotSerializableException cause = null;
            for (final Throwable t : throwableList) {
                cause = cause == null
                        ? new WorkaroundNotSerializableException(t)
                        : new WorkaroundNotSerializableException(t, cause);
            }

            return cause;
        }

    }
}
