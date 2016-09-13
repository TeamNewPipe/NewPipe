package org.schabi.newpipe.search_fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.detail.VideoItemDetailActivity;
import org.schabi.newpipe.detail.VideoItemDetailFragment;
import org.schabi.newpipe.extractor.SearchResult;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.info_list.InfoListAdapter;

/**
 * Created by Christian Schabesberger on 02.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SearchInfoItemFragment.java is part of NewPipe.
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
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class SearchInfoItemFragment extends Fragment {

    private static final String TAG = SearchInfoItemFragment.class.toString();

    public class SearchQueryListener implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            Activity a = getActivity();
            try {
                searchQuery = query;
                search(query);

                // hide virtual keyboard
                InputMethodManager inputManager =
                        (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
                try {
                    //noinspection ConstantConditions
                    inputManager.hideSoftInputFromWindow(
                            a.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                } catch(NullPointerException e) {
                    e.printStackTrace();
                    ErrorActivity.reportError(a, e, null,
                            a.findViewById(android.R.id.content),
                            ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                    ServiceList.getNameOfService(streamingServiceId),
                                    "Could not get widget with focus", R.string.general_error));
                }
                // clear focus
                // 1. to not open up the keyboard after switching back to this
                // 2. It's a workaround to a seeming bug by the Android OS it self, causing
                //    onQueryTextSubmit to trigger twice when focus is not cleared.
                // See: http://stackoverflow.com/questions/17874951/searchview-onquerytextsubmit-runs-twice-while-i-pressed-once
                a.getCurrentFocus().clearFocus();
            } catch(Exception e) {
                e.printStackTrace();
            }
            View bg = a.findViewById(R.id.mainBG);
            bg.setVisibility(View.GONE);
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if(!newText.isEmpty()) {
                searchSuggestions(newText);
            }
            return true;
        }
    }

    private int streamingServiceId = -1;
    private String searchQuery = "";
    private boolean isLoading = false;

    private ProgressBar loadingIndicator = null;
    private SearchView searchView = null;
    private int pageNumber = 0;
    private SuggestionListAdapter suggestionListAdapter = null;
    private InfoListAdapter infoListAdapter = null;
    private LinearLayoutManager streamInfoListLayoutManager = null;
    private RecyclerView recyclerView = null;

    // savedInstanceBundle arguments
    private static final String QUERY = "query";
    private static final String STREAMING_SERVICE = "streaming_service";

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SearchInfoItemFragment() {
    }

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static SearchInfoItemFragment newInstance(int columnCount) {
        SearchInfoItemFragment fragment = new SearchInfoItemFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(savedInstanceState != null) {
            searchQuery = savedInstanceState.getString(QUERY);
            streamingServiceId = savedInstanceState.getInt(STREAMING_SERVICE);
        } else {
            try {
                streamingServiceId = ServiceList.getIdOfService("Youtube");
            } catch(Exception e) {
                e.printStackTrace();
                ErrorActivity.reportError(getActivity(), e, null,
                        getActivity().findViewById(android.R.id.content),
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                ServiceList.getNameOfService(streamingServiceId),
                                "", R.string.general_error));
            }
        }

        SearchWorker sw = SearchWorker.getInstance();
        sw.setSearchWorkerResultListner(new SearchWorker.SearchWorkerResultListner() {
            @Override
            public void onResult(SearchResult result) {
                infoListAdapter.addStreamItemList(result.resultList);
                isLoading = false;
                loadingIndicator.setVisibility(View.GONE);
            }

            @Override
            public void onNothingFound(int stringResource) {
                //setListShown(true);
                Toast.makeText(getActivity(), getString(stringResource),
                        Toast.LENGTH_SHORT).show();
                isLoading = false;
                loadingIndicator.setVisibility(View.GONE);
            }

            @Override
            public void onError(String message) {
                //setListShown(true);
                Toast.makeText(getActivity(), message,
                        Toast.LENGTH_LONG).show();
                isLoading = false;
                loadingIndicator.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_searchinfoitem, container, false);

        Context context = view.getContext();
        loadingIndicator = (ProgressBar) view.findViewById(R.id.progressBar);
        recyclerView = (RecyclerView) view.findViewById(R.id.list);
        streamInfoListLayoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(streamInfoListLayoutManager);

        infoListAdapter = new InfoListAdapter(getActivity(),
                getActivity().findViewById(android.R.id.content));
        infoListAdapter.setOnItemSelectedListener(new InfoListAdapter.OnItemSelectedListener() {
            @Override
            public void selected(String url) {
                Intent i = new Intent(getActivity(), VideoItemDetailActivity.class);
                i.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, streamingServiceId);
                i.putExtra(VideoItemDetailFragment.VIDEO_URL, url);
                getActivity().startActivity(i);
            }
        });
        recyclerView.setAdapter(infoListAdapter);

        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int pastVisiblesItems, visibleItemCount, totalItemCount;
                super.onScrolled(recyclerView, dx, dy);
                if(dy > 0) //check for scroll down
                {
                    visibleItemCount = streamInfoListLayoutManager.getChildCount();
                    totalItemCount = streamInfoListLayoutManager.getItemCount();
                    pastVisiblesItems = streamInfoListLayoutManager.findFirstVisibleItemPosition();

                    if ( (visibleItemCount + pastVisiblesItems) >= totalItemCount && !isLoading)
                    {
                        pageNumber++;
                        search(searchQuery, pageNumber);
                    }
                }
            }
        });

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.search_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();
        setupSearchView(searchView);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void setupSearchView(SearchView searchView) {
        suggestionListAdapter = new SuggestionListAdapter(getActivity());
        searchView.setSuggestionsAdapter(suggestionListAdapter);
        searchView.setOnSuggestionListener(new SearchSuggestionListener(searchView, suggestionListAdapter));
        searchView.setOnQueryTextListener(new SearchQueryListener());
        if(searchQuery != null && !searchQuery.isEmpty()) {
            searchView.setQuery(searchQuery, false);
            searchView.setIconifiedByDefault(false);
        }
    }

    private void search(String query) {
        infoListAdapter.clearSteamItemList();
        pageNumber = 0;
        search(query, pageNumber);
        loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void search(String query, int page) {
        isLoading = true;
        SearchWorker sw = SearchWorker.getInstance();
        sw.search(streamingServiceId, query, page, getActivity());
    }

    private void searchSuggestions(String query) {
        SuggestionSearchRunnable suggestionSearchRunnable =
                new SuggestionSearchRunnable(streamingServiceId, query, getActivity(), suggestionListAdapter);
        Thread suggestionThread = new Thread(suggestionSearchRunnable);
        suggestionThread.start();
    }
}
