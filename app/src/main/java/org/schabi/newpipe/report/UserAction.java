package org.schabi.newpipe.report;

/**
 * The user actions that can cause an error.
 */
public enum UserAction {
    USER_REPORT("user report"),
    UI_ERROR("ui error"),
    SUBSCRIPTION("subscription"),
    LOAD_IMAGE("load image"),
    SOMETHING_ELSE("something"),
    SEARCHED("searched"),
    GET_SUGGESTIONS("get suggestions"),
    REQUESTED_STREAM("requested stream"),
    REQUESTED_CHANNEL("requested channel"),
    REQUESTED_PLAYLIST("requested playlist"),
    REQUESTED_KIOSK("requested kiosk"),
    REQUESTED_COMMENTS("requested comments"),
    DELETE_FROM_HISTORY("delete from history"),
    PLAY_STREAM("Play stream");


    private final String message;

    UserAction(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
