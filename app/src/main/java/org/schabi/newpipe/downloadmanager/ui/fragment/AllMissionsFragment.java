package org.schabi.newpipe.downloadmanager.ui.fragment;

import org.schabi.newpipe.downloadmanager.get.DownloadManager;
import org.schabi.newpipe.downloadmanager.service.DownloadManagerService;

/**
 * Copyright (C) 2014 Peter Cai
 * Changes by Christian Schabesberger (C) 2018
 *
 * org.schabi.newpipe.downloadmanager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * org.schabi.newpipe.downloadmanager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with org.schabi.newpipe.downloadmanager.  If not, see <http://www.gnu.org/licenses/>.
 */

public class AllMissionsFragment extends MissionsFragment {

    @Override
    protected DownloadManager setupDownloadManager(DownloadManagerService.DMBinder binder) {
        return binder.getDownloadManager();
    }
}
