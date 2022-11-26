package org.schabi.newpipe.util;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class SerializedUtils {
    private SerializedUtils() {
    }

    @NonNull
    public static <T extends Serializable> T clone(@NonNull final T item,
                                                   @NonNull final Class<T> type
    ) throws IOException, SecurityException, ClassNotFoundException {
        final ByteArrayOutputStream bytesOutput = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(bytesOutput)) {
            objectOutput.writeObject(item);
            objectOutput.flush();
        }
        final Object clone = new ObjectInputStream(
                new ByteArrayInputStream(bytesOutput.toByteArray())).readObject();
        return type.cast(clone);
    }
}
