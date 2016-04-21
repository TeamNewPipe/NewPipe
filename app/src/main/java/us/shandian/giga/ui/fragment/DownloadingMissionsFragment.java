package us.shandian.giga.ui.fragment;

import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.FilteredDownloadManagerWrapper;
import us.shandian.giga.service.DownloadManagerService;

public class DownloadingMissionsFragment extends MissionsFragment
{
	@Override
	protected DownloadManager setupDownloadManager(DownloadManagerService.DMBinder binder) {
		return new FilteredDownloadManagerWrapper(binder.getDownloadManager(), false);
	}
}
