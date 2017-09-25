package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;

import io.reactivex.Single;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Created by Christian Schabesberger on 23.09.17.
 *
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * KioskFragment.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenHitboxStreams.  If not, see <http://www.gnu.org/licenses/>.
 */

public class KioskFragment extends BaseListInfoFragment<KioskInfo> {


    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View headerRootLayout;
    private TextView headerTitleView;

    public static KioskFragment getInstance(int serviceId)
            throws ExtractionException {
        KioskFragment instance = new KioskFragment();
        StreamingService service = NewPipe.getService(serviceId);
        String defaultKioskId = service.getKioskList().getDefaultKioskId();
        UrlIdHandler defaultKioskTypeUrlIdHandler = service.getKioskList()
                .getUrlIdHandlerByType(defaultKioskId);
        instance.setInitialData(serviceId,
                defaultKioskTypeUrlIdHandler.getUrl(defaultKioskId),
                defaultKioskId);
        return instance;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    protected View getListHeader() {
        headerRootLayout = activity.getLayoutInflater().inflate(R.layout.kiosk_header, itemsList, false);
        headerTitleView = headerRootLayout.findViewById(R.id.kiosk_title_view);

        return headerRootLayout;
    }

    @Override
    public void initListeners() {
        // We have to override this because the default implementation of this function calls
        // openVideoDetailFragment on getFragmentManager() but what we want here is
        // getParentFragment().getFragmentManager()
        infoListAdapter.setOnStreamSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener<StreamInfoItem>() {
            @Override
            public void selected(StreamInfoItem selectedItem) {
                onItemSelected(selectedItem);
                NavigationHelper.openVideoDetailFragment(getParentFragment().getFragmentManager(),
                        selectedItem.service_id, selectedItem.url, selectedItem.name);
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public Single<KioskInfo> loadResult(boolean forceReload) {
        String contentCountry = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString(getString(R.string.search_language_key),
                        getString(R.string.default_language_value));
        return ExtractorHelper.getKioskInfo(serviceId, url, contentCountry, forceReload);
    }

    @Override
    public Single<ListExtractor.NextItemsResult> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreKioskItems(serviceId, url, currentNextItemsUrl);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
        animateView(headerRootLayout, false, 200);
        animateView(itemsList, false, 100);
    }

    @Override
    public void handleResult(@NonNull final KioskInfo result) {
        super.handleResult(result);

        animateView(headerRootLayout, true, 100);
        headerTitleView.setText("★★ " +result.name+ " ★★");

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors,
                    UserAction.REQUESTED_PLAYLIST,
                    NewPipe.getNameOfService(result.service_id), result.url, 0);
        }
    }

    @Override
    public void handleNextItems(ListExtractor.NextItemsResult result) {
        super.handleNextItems(result);

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors,
                    UserAction.REQUESTED_PLAYLIST, NewPipe.getNameOfService(serviceId)
                    , "Get next page of: " + url, 0);
        }
    }
}
