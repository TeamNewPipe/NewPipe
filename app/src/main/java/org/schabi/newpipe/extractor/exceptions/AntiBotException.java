package org.schabi.newpipe.extractor.exceptions;

public class AntiBotException extends ParsingException {
    private final int serviceId;
    private final String url;

    public AntiBotException(final int serviceId, final String message,
                            final String url, final Throwable cause) {
        super(message, cause);
        this.serviceId = serviceId;
        this.url = url;
    }

    public AntiBotException(final int serviceId, final String message) {
        this(serviceId, message, null, null);
    }

    public AntiBotException(final int serviceId, final String message, final Throwable cause) {
        this(serviceId, message, null, cause);
    }

    public AntiBotException(final String message) {
        this(-1, message);
    }

    public AntiBotException(final String message, final Throwable cause) {
        this(-1, message, cause);
    }

    public int getServiceId() {
        return serviceId;
    }

    public String getUrl() {
        return url;
    }
}
