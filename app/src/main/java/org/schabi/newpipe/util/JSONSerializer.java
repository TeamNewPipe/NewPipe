package org.schabi.newpipe.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * Centralized class for JSON serialization.
 */
public final class JSONSerializer {
    private JSONSerializer() {

    }

    private static ObjectMapper newMapper() {
        return new ObjectMapper();
    }

    public static void toJson(final Object objToConvert, final OutputStream target) {
        try {
            newMapper().writeValue(target, objToConvert);
        } catch (final IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public static <T> T fromJson(final InputStream stream, final Class<T> target) {
        try {
            return newMapper().readValue(stream, target);
        } catch (final IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
}
