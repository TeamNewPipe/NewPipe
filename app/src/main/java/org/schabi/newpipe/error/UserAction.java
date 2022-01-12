package org.schabi.newpipe.error;

/**
 * The user actions that can cause an error.
 */
public enum UserAction {
    USER_REPORT("user report"),
    UI_ERROR("ui error"),
    SUBSCRIPTION_CHANGE("subscription change"),
    SUBSCRIPTION_UPDATE("subscription update"),
    SUBSCRIPTION_GET("get subscription"),
    SUBSCRIPTION_IMPORT_EXPORT("subscription import or export"),
    LOAD_IMAGE("load image"),
    SOMETHING_ELSE("something else"),
    SEARCHED("searched"),
    GET_SUGGESTIONS("get suggestions"),
    REQUESTED_STREAM("requested stream"),
    REQUESTED_CHANNEL("requested channel"),
    REQUESTED_PLAYLIST("requested playlist"),
    REQUESTED_KIOSK("requested kiosk"),
    REQUESTED_COMMENTS("requested comments"),
    REQUESTED_FEED("requested feed"),
    REQUESTED_BOOKMARK("bookmark"),
    DELETE_FROM_HISTORY("delete from history"),
    PLAY_STREAM("play stream"),
    DOWNLOAD_OPEN_DIALOG("download open dialog"),
    DOWNLOAD_POSTPROCESSING("download post-processing"),
    DOWNLOAD_FAILED("download failed"),
    PREFERENCES_MIGRATION("migration of preferences"),
    SHARE_TO_NEWPIPE("share to newpipe"),
    CHECK_FOR_NEW_APP_VERSION("check for new app version");


    private final String message;

    UserAction(final String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
