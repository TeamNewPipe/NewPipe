package org.schabi.newpipe.database;

public class RemoteDatabaseException extends RuntimeException {

    public RemoteDatabaseException(String message) {
        super(message);
    }

    public RemoteDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

}
