package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.services.media_ccc.extractors.MediaCCCLiveStreamKiosk;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.Localization;

import icepick.State;
import io.reactivex.rxjava3.core.Single;

/**
 * Created by Christian Schabesberger on 23.09.17.
 * <p>
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * KioskFragment.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class KioskFragment extends BaseListInfoFragment<StreamInfoItem, KioskInfo> {
    @State
    String kioskId = "";
    String kioskTranslatedName;
    @State
    ContentCountry contentCountry;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    public static KioskFragment getInstance(final int serviceId) throws ExtractionException {
        return getInstance(serviceId, NewPipe.getService(serviceId)
                .getKioskList().getDefaultKioskId());
    }

    public static KioskFragment getInstance(final int serviceId, final String kioskId)
            throws ExtractionException {
        final KioskFragment instance = new KioskFragment();
        final StreamingService service = NewPipe.getService(serviceId);
        final ListLinkHandlerFactory kioskLinkHandlerFactory = service.getKioskList()
                .getListLinkHandlerFactoryByType(kioskId);
        instance.setInitialData(serviceId,
                kioskLinkHandlerFactory.fromId(kioskId).getUrl(), kioskId);
        instance.kioskId = kioskId;
        return instance;
    }

    public KioskFragment() {
        super(UserAction.REQUESTED_KIOSK);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, activity);
        name = kioskTranslatedName;
        contentCountry = Localization.getPreferredContentCountry(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!Localization.getPreferredContentCountry(requireContext()).equals(contentCountry)) {
            reloadContent();
        }
        if (useAsFrontPage && activity != null) {
            try {
                setTitle(kioskTranslatedName);
            } catch (final Exception e) {
                showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR, "Setting kiosk title"));
            }
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null && useAsFrontPage) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public Single<KioskInfo> loadResult(final boolean forceReload) {
        contentCountry = Localization.getPreferredContentCountry(requireContext());
        return ExtractorHelper.getKioskInfo(serviceId, url, forceReload);
    }

    @Override
    public Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreKioskItems(serviceId, url, currentNextPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final KioskInfo result) {
        super.handleResult(result);

        name = kioskTranslatedName;
        setTitle(kioskTranslatedName);
    }

    @Override
    public void showEmptyState() {
        // show "no live streams" for live stream kiosk
        super.showEmptyState();
        if (MediaCCCLiveStreamKiosk.KIOSK_ID.equals(currentInfo.getId())
                && ServiceList.MediaCCC.getServiceId() == currentInfo.getServiceId()) {
            setEmptyStateMessage(R.string.no_live_streams);
        }
    }
}
