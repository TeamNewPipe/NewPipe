package org.schabi.newpipe.report;

/**
 * The user actions that can cause an error.
 */
public enum UserAction {
    SEARCHED("searched"),
    REQUESTED_STREAM("requested stream"),
    GET_SUGGESTIONS("get suggestions"),
    SOMETHING_ELSE("something"),
    USER_REPORT("user report"),
    LOAD_IMAGE("load image"),
    UI_ERROR("ui error"),
    REQUESTED_CHANNEL("requested channel");


    private final String message;

    UserAction(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
