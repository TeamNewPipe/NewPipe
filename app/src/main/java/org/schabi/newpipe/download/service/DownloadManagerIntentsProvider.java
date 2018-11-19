package org.schabi.newpipe.download.service;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import org.schabi.newpipe.download.ExtSDDownloadFailedActivity;
import org.schabi.newpipe.downloader.util.IntentsProvider;

class DownloadManagerIntentsProvider implements IntentsProvider {

    @NonNull
    private final Context context;

    DownloadManagerIntentsProvider(@NonNull Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Intent getSDCardPermissionDeniedIntent() {
        return new Intent(context, ExtSDDownloadFailedActivity.class);
    }
}
