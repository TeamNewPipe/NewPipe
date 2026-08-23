package org.schabi.newpipe.player.datasource;

import java.io.IOException;

final class SabrLogicException extends IOException {
    SabrLogicException(final String message) {
        super(message);
    }

    SabrLogicException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
