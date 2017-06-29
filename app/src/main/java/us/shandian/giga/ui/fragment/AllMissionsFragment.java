package us.shandian.giga.ui.fragment;

import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;

public class AllMissionsFragment extends MissionsFragment {

    @Override
    protected DownloadManager setupDownloadManager(DownloadManagerService.DMBinder binder) {
        return binder.getDownloadManager();
    }
}
