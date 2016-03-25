package org.schabi.newpipe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.SearchEngine;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

/**
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoItemListActivity.java is part of NewPipe.
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

public class VideoItemListActivity extends AppCompatActivity
        implements VideoItemListFragment.Callbacks {

    private static final String TAG = VideoItemListFragment.class.toString();

    // arguments to give to this activity
    public static final String VIDEO_INFO_ITEMS = "video_info_items";

    // savedInstanceBundle arguments
    private static final String QUERY = "query";
    private static final String STREAMING_SERVICE = "streaming_service";

    // activity modes
    private static final int SEARCH_MODE = 0;
    private static final int PRESENT_VIDEOS_MODE = 1;

    private int mode = SEARCH_MODE;
    private int currentStreamingServiceId = -1;
    private String searchQuery = "";

    private VideoItemListFragment listFragment;
    private VideoItemDetailFragment videoFragment = null;
    private Menu menu = null;

    private SuggestionListAdapter suggestionListAdapter;
    private SuggestionSearchRunnable suggestionSearchRunnable;
    private Thread searchThread;

    private class SearchVideoQueryListener implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            try {
                searchQuery = query;
                listFragment.search(query);

                // hide virtual keyboard
                InputMethodManager inputManager =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                try {
                    //noinspection ConstantConditions
                    inputManager.hideSoftInputFromWindow(
                            getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                } catch(NullPointerException e) {
                    Log.e(TAG, "Could not get widget with focus");
                    Toast.makeText(VideoItemListActivity.this, "Could not get widget with focus",
                            Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
                // clear focus
                // 1. to not open up the keyboard after switching back to this
                // 2. It's a workaround to a seeming bug by the Android OS it self, causing
                //    onQueryTextSubmit to trigger twice when focus is not cleared.
                // See: http://stackoverflow.com/questions/17874951/searchview-onquerytextsubmit-runs-twice-while-i-pressed-once
                getCurrentFocus().clearFocus();
            } catch(Exception e) {
                e.printStackTrace();
            }
            View bg = findViewById(R.id.mainBG);
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
    private class SearchSuggestionListener implements SearchView.OnSuggestionListener{

        private SearchView searchView;

        private SearchSuggestionListener(SearchView searchView) {
            this.searchView = searchView;
        }

        @Override
        public boolean onSuggestionSelect(int position) {
            String suggestion = suggestionListAdapter.getSuggestion(position);
            searchView.setQuery(suggestion,true);
            return false;
        }

        @Override
        public boolean onSuggestionClick(int position) {
            String suggestion = suggestionListAdapter.getSuggestion(position);
            searchView.setQuery(suggestion,true);
            return false;
        }
    }

    private class SuggestionResultRunnable implements Runnable{

        private ArrayList<String>suggestions;

        private SuggestionResultRunnable(ArrayList<String> suggestions) {
            this.suggestions = suggestions;
        }

        @Override
        public void run() {
            suggestionListAdapter.updateAdapter(suggestions);
        }
    }

    private class SuggestionSearchRunnable implements Runnable{
        private final int serviceId;
        private final String query;
        final Handler h = new Handler();
        private Context context;
        private SuggestionSearchRunnable(int serviceId, String query) {
            this.serviceId = serviceId;
            this.query = query;
            context = VideoItemListActivity.this;
        }

        @Override
        public void run() {
            try {
                SearchEngine engine =
                        ServiceList.getService(serviceId).getSearchEngineInstance(new Downloader());
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                String searchLanguageKey = context.getString(R.string.search_language_key);
                String searchLanguage = sp.getString(searchLanguageKey,
                        getString(R.string.default_language_value));
                ArrayList<String>suggestions = engine.suggestionList(query,searchLanguage,new Downloader());
                h.post(new SuggestionResultRunnable(suggestions));
            } catch (ExtractionException e) {
                ErrorActivity.reportError(h, VideoItemListActivity.this, e, null, findViewById(R.id.videoitem_list),
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                ServiceList.getNameOfService(serviceId), query, R.string.parsing_error));
                e.printStackTrace();
            } catch (IOException e) {
                postNewErrorToast(h, R.string.network_error);
                e.printStackTrace();
            } catch (Exception e) {
                ErrorActivity.reportError(h, VideoItemListActivity.this, e, null, findViewById(R.id.videoitem_list),
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                ServiceList.getNameOfService(serviceId), query, R.string.general_error));
            }
        }
    }
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_videoitem_list);
        StreamingService streamingService = null;

        View bg = findViewById(R.id.mainBG);
        bg.setVisibility(View.VISIBLE);

        try {
            //------ todo: remove this line when multiservice support is implemented ------
            currentStreamingServiceId = ServiceList.getIdOfService("Youtube");
            streamingService = ServiceList.getService(currentStreamingServiceId);
        } catch (Exception e) {
            e.printStackTrace();
            ErrorActivity.reportError(VideoItemListActivity.this, e, null, findViewById(R.id.videoitem_list),
                    ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                            ServiceList.getNameOfService(currentStreamingServiceId), "", R.string.general_error));
        }
        //-----------------------------------------------------------------------------
        //to solve issue 38
        listFragment = (VideoItemListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.videoitem_list);
        listFragment.setStreamingService(streamingService);

        if(savedInstanceState != null
                && mode != PRESENT_VIDEOS_MODE) {
            searchQuery = savedInstanceState.getString(QUERY);
            currentStreamingServiceId = savedInstanceState.getInt(STREAMING_SERVICE);
            if(!searchQuery.isEmpty()) {
                listFragment.search(searchQuery);
            }
        }

        if (findViewById(R.id.videoitem_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.

            ((VideoItemListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.videoitem_list))
                    .setActivateOnItemClick(true);

            SearchView searchView = (SearchView)findViewById(R.id.searchViewTablet);
            if(mode != PRESENT_VIDEOS_MODE) {
                // Somehow the seticonifiedbydefault property set by the layout xml is not working on
                // the support version on SearchView, so it needs to be set programmatically.
                searchView.setIconifiedByDefault(false);
                searchView.setIconified(false);
                if(!searchQuery.isEmpty()) {
                    searchView.setQuery(searchQuery,false);
                }
                searchView.setOnQueryTextListener(new SearchVideoQueryListener());
                suggestionListAdapter = new SuggestionListAdapter(this);
                searchView.setSuggestionsAdapter(suggestionListAdapter);
                searchView.setOnSuggestionListener(new SearchSuggestionListener(searchView));
            } else {
                searchView.setVisibility(View.GONE);
            }
        }

        PreferenceManager.setDefaultValues(this, R.xml.settings, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        App.checkStartTor(this);
    }

    /**
     * Callback method from {@link VideoItemListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(String id) {
        VideoListAdapter listAdapter = (VideoListAdapter) ((VideoItemListFragment)
                getSupportFragmentManager()
                        .findFragmentById(R.id.videoitem_list))
                .getListAdapter();
        String webpageUrl = listAdapter.getVideoList().get((int) Long.parseLong(id)).webpage_url;
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle arguments = new Bundle();
            //arguments.putString(VideoItemDetailFragment.ARG_ITEM_ID, id);
            arguments.putString(VideoItemDetailFragment.VIDEO_URL, webpageUrl);
            arguments.putInt(VideoItemDetailFragment.STREAMING_SERVICE, currentStreamingServiceId);
            videoFragment = new VideoItemDetailFragment();
            videoFragment.setArguments(arguments);
            videoFragment.setOnInvokeCreateOptionsMenuListener(new VideoItemDetailFragment.OnInvokeCreateOptionsMenuListener() {
                @Override
                public void createOptionsMenu() {
                    menu.clear();
                    onCreateOptionsMenu(menu);
                }
            });
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.videoitem_detail_container, videoFragment)
                    .commit();
        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, VideoItemDetailActivity.class);
            //detailIntent.putExtra(VideoItemDetailFragment.ARG_ITEM_ID, id);
            detailIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, webpageUrl);
            detailIntent.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, currentStreamingServiceId);
            startActivity(detailIntent);
        }
    }


    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        this.menu = menu;
        MenuInflater inflater = getMenuInflater();
        if(mode != PRESENT_VIDEOS_MODE &&
                findViewById(R.id.videoitem_detail_container) == null) {
            inflater.inflate(R.menu.videoitem_list, menu);
            MenuItem searchItem = menu.findItem(R.id.action_search);
            SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setFocusable(false);
            searchView.setOnQueryTextListener(
                    new SearchVideoQueryListener());
            suggestionListAdapter = new SuggestionListAdapter(this);
            searchView.setSuggestionsAdapter(suggestionListAdapter);
            searchView.setOnSuggestionListener(new SearchSuggestionListener(searchView));
            if(!searchQuery.isEmpty()) {
                searchView.setQuery(searchQuery,false);
                searchView.setIconifiedByDefault(false);
            }
        } else if (videoFragment != null){
            videoFragment.onCreateOptionsMenu(menu, inflater);
        } else {
            inflater.inflate(R.menu.videoitem_two_pannel, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id) {
            case android.R.id.home: {
                Intent intent = new Intent(this, VideoItemListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                NavUtils.navigateUpTo(this, intent);
                return true;
            }
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_report_error: {
                ErrorActivity.reportError(VideoItemListActivity.this, new Vector<Exception>(),
                        null, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.USER_REPORT,
                                ServiceList.getNameOfService(currentStreamingServiceId),
                                "user_report", R.string.user_report));
                return true;
            }
            default:
                return videoFragment.onOptionsItemSelected(item) ||
                    super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /*
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
        */
        outState.putString(QUERY, searchQuery);
        outState.putInt(STREAMING_SERVICE, currentStreamingServiceId);
    }

    private void searchSuggestions(String query) {
        suggestionSearchRunnable =
                new SuggestionSearchRunnable(currentStreamingServiceId, query);
        searchThread = new Thread(suggestionSearchRunnable);
        searchThread.start();

    }

    private void postNewErrorToast(Handler h, final int stringResource) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(VideoItemListActivity.this, getString(stringResource),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
