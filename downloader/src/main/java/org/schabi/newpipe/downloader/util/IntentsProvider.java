package org.schabi.newpipe.downloader.util;

import android.content.Intent;
import android.support.annotation.NonNull;

/**
 * Provides intents for the {@link org.schabi.newpipe.downloader.get.DownloadManager}
 * to react on certain events (like download failure).
 */
public interface IntentsProvider {

    /**
     * Retrieves the intent that will used to start an Activity when the download fails because of
     * a read/write permission not gived by a user.
     *
     * @return an Intent that will be used to start the Activity.
     */
    @NonNull
    Intent getSDCardPermissionDeniedIntent();

}
