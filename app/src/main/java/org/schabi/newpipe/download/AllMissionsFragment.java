package org.schabi.newpipe.download;

import org.schabi.newpipe.download.service.DownloadManagerService;
import org.schabi.newpipe.downloader.get.DownloadManager;

public class AllMissionsFragment extends MissionsFragment {

    @Override
    protected DownloadManager setupDownloadManager(DownloadManagerService.DMBinder binder) {
        return binder.getDownloadManager();
    }
}
