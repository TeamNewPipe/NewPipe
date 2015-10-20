package org.schabi.newpipe;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

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
    private static final String QUERY = "query";
    private static final String STREAMING_SERVICE = "streaming_service";

    private int currentStreamingServiceId = -1;
    private String searchQuery = "";

    private VideoItemListFragment listFragment;

    public class SearchVideoQueryListener implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            try {
                searchQuery = query;
                listFragment.search(query);

                // hide virtual keyboard
                InputMethodManager inputManager =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(
                        getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                // clear focus
                // 1. to not open up the keyboard after switching back to this
                // 2. It's a workaround to a seeming bug by the Android OS it self, causing
                //    onQueryTextSubmit to trigger twice when focus is not cleared.
                // See: http://stackoverflow.com/questions/17874951/searchview-onquerytextsubmit-runs-twice-while-i-pressed-once
                getCurrentFocus().clearFocus();
                hideWatermark();
            } catch(Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            return true;
        }

    }

    private void hideWatermark() {
        ImageView waterMark = (ImageView) findViewById(R.id.list_view_watermark);
        if(waterMark != null) {
            waterMark.setVisibility(View.GONE);
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

        listFragment = (VideoItemListFragment)
                getSupportFragmentManager().findFragmentById(R.id.videoitem_list);

        //-------- remove this line when multiservice support is implemented ----------
        currentStreamingServiceId = ServiceList.getIdOfService("Youtube");
        //-----------------------------------------------------------------------------
        VideoItemListFragment listFragment = (VideoItemListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.videoitem_list);
        listFragment.setStreamingService(ServiceList.getService(currentStreamingServiceId));

        if(savedInstanceState != null) {
            searchQuery = savedInstanceState.getString(QUERY);
            currentStreamingServiceId = savedInstanceState.getInt(STREAMING_SERVICE);
            if(!searchQuery.isEmpty()) {
                hideWatermark();
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
            // Somehow the seticonifiedbydefault property set by the layout xml is not working on
            // the support version on SearchView, so it needs to be set programmatically.
            searchView.setIconifiedByDefault(false);
            searchView.setIconified(false);
            searchView.setOnQueryTextListener(new SearchVideoQueryListener());

            ActionBarHandler.getHandler().setupNavMenu(this);

        }

        SettingsActivity.initSettings(this);

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
        String webpage_url = listAdapter.getVideoList().get((int) Long.parseLong(id)).webpage_url;
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle arguments = new Bundle();
            arguments.putString(VideoItemDetailFragment.ARG_ITEM_ID, id);
            arguments.putString(VideoItemDetailFragment.VIDEO_URL, webpage_url);
            arguments.putInt(VideoItemDetailFragment.STREAMING_SERVICE, currentStreamingServiceId);
            VideoItemDetailFragment fragment = new VideoItemDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.videoitem_detail_container, fragment)
                    .commit();
        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, VideoItemDetailActivity.class);
            detailIntent.putExtra(VideoItemDetailFragment.ARG_ITEM_ID, id);
            detailIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, webpage_url);
            detailIntent.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, currentStreamingServiceId);
            startActivity(detailIntent);
        }
    }


    public boolean onCreatePanelMenu(int featured, Menu menu) {
        super.onCreatePanelMenu(featured, menu);
        if(findViewById(R.id.videoitem_detail_container) == null) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.videoitem_list, menu);
            MenuItem searchItem = menu.findItem(R.id.action_search);
            SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setFocusable(false);
            searchView.setOnQueryTextListener(
                    new SearchVideoQueryListener());

        } else {
            MenuInflater inflater = getMenuInflater();
            ActionBarHandler.getHandler().setupMenu(menu, inflater, this);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else {
            ActionBarHandler.getHandler().onItemSelected(item, this);
            return super.onOptionsItemSelected(item);
        }
        return true;
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
}
