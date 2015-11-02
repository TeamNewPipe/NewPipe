package org.schabi.newpipe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.Toast;

import java.net.URL;
import java.util.Arrays;
import java.util.Vector;


/**
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoItemListFragment.java is part of NewPipe.
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

public class VideoItemListFragment extends ListFragment {

    private static final String TAG = VideoItemListFragment.class.toString();

    private StreamingService streamingService = null;
    private VideoListAdapter videoListAdapter;

    // activity modes
    private static final int SEARCH_MODE = 0;
    private static final int PRESENT_VIDEOS_MODE = 1;

    private int mode = SEARCH_MODE;
    private String query = "";
    private int lastPage = 0;

    private Thread searchThread = null;
    private SearchRunnable searchRunnable = null;
    private Thread loadThumbsThread = null;
    private LoadThumbsRunnable loadThumbsRunnable = null;
    // used to track down if results posted by threads ar still valid
    private int currentRequestId = -1;
    private ListView list;

    private class ResultRunnable implements Runnable {
        private SearchEngine.Result result;
        private int requestId;
        public ResultRunnable(SearchEngine.Result result, int requestId) {
            this.result = result;
            this.requestId = requestId;
        }
        @Override
        public void run() {
            updateListOnResult(result, requestId);
        }
    }

    private class SearchRunnable implements Runnable {
        private Class engineClass = null;
        private String query;
        private int page;
        Handler h = new Handler();
        private volatile boolean run = true;
        private int requestId;
        public SearchRunnable(Class engineClass, String query, int page, int requestId) {
            this.engineClass = engineClass;
            this.query = query;
            this.page = page;
            this.requestId = requestId;
        }
        void terminate() {
            run = false;
        }
        @Override
        public void run() {
            SearchEngine engine = null;
            try {
                engine = (SearchEngine) engineClass.newInstance();
            } catch(Exception e) {
                e.printStackTrace();
                return;
            }
            try {
                SearchEngine.Result result = engine.search(query, page);
                if(run) {
                    h.post(new ResultRunnable(result, requestId));
                }
            } catch(Exception e) {
                e.printStackTrace();
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), "Network Error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private class LoadThumbsRunnable implements Runnable {
        private Vector<String> thumbnailUrlList = new Vector<>();
        private Vector<Boolean> downloadedList;
        Handler h = new Handler();
        private volatile boolean run = true;
        private int requestId;
        public LoadThumbsRunnable(Vector<VideoInfoItem> videoList,
                                  Vector<Boolean> downloadedList, int requestId) {
            for(VideoInfoItem item : videoList) {
                thumbnailUrlList.add(item.thumbnail_url);
            }
            this.downloadedList = downloadedList;
            this.requestId = requestId;
        }
        public void terminate() {
            run = false;
        }
        public boolean isRunning() {
            return run;
        }
        @Override
        public void run() {
            for(int i = 0; i < thumbnailUrlList.size() && run; i++) {
                if(!downloadedList.get(i)) {
                    Bitmap thumbnail = null;
                    try {
                        thumbnail = BitmapFactory.decodeStream(
                                new URL(thumbnailUrlList.get(i)).openConnection().getInputStream());
                        h.post(new SetThumbnailRunnable(i, thumbnail, requestId));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private class SetThumbnailRunnable implements Runnable {
        private int index;
        private Bitmap thumbnail;
        private int requestId;
        public SetThumbnailRunnable(int index, Bitmap thumbnail, int requestId) {
            this.index = index;
            this.thumbnail = thumbnail;
            this.requestId = requestId;
        }
        @Override
        public void run() {
            if(requestId == currentRequestId) {
                videoListAdapter.updateDownloadedThumbnailList(index, true);
                videoListAdapter.setThumbnail(index, thumbnail);
            }
        }
    }

    public void present(VideoInfoItem[] videoList) {
        mode = PRESENT_VIDEOS_MODE;
        setListShown(true);
        getListView().smoothScrollToPosition(0);

        // inefficient like hell i know (welcome to the world of java)
        //todo: make this more efficient
        updateList(new Vector<>(Arrays.asList(videoList)));
    }

    public void search(String query) {
        mode = SEARCH_MODE;
        this.query = query;
        this.lastPage = 1;
        videoListAdapter.clearVideoList();
        setListShown(false);
        startSearch(query, lastPage);
        getListView().smoothScrollToPosition(0);
    }

    public void nextPage() {
        lastPage++;
        Log.d(TAG, getString(R.string.searchPage) + Integer.toString(lastPage));
        startSearch(query, lastPage);
    }

    private void startSearch(String query, int page) {
        currentRequestId++;
        terminateThreads();
        searchRunnable = new SearchRunnable(streamingService.getSearchEngineClass(), query, page, currentRequestId);
        searchThread = new Thread(searchRunnable);
        searchThread.start();
    }

    public void setStreamingService(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    public void updateListOnResult(SearchEngine.Result result, int requestId) {
        if(requestId == currentRequestId) {
            setListShown(true);
            if (result.resultList.isEmpty()) {
                Toast.makeText(getActivity(), result.errorMessage, Toast.LENGTH_LONG).show();
            } else {
                if (!result.suggestion.isEmpty()) {
                    Toast.makeText(getActivity(), getString(R.string.didYouMean) + result.suggestion + " ?",
                            Toast.LENGTH_LONG).show();
                }
                updateList(result.resultList);
            }
        }
    }

    private void updateList(Vector<VideoInfoItem> list) {
        try {
            videoListAdapter.addVideoList(list);
            terminateThreads();
            loadThumbsRunnable = new LoadThumbsRunnable(videoListAdapter.getVideoList(),
                    videoListAdapter.getDownloadedThumbnailList(), currentRequestId);
            loadThumbsThread = new Thread(loadThumbsRunnable);
            loadThumbsThread.start();
        } catch(java.lang.IllegalStateException e) {
            Log.w(TAG, "Trying to set value while activity doesn't exist anymore.");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void terminateThreads() {
        if(loadThumbsRunnable != null && loadThumbsRunnable.isRunning()) {
            loadThumbsRunnable.terminate();
            try {
                loadThumbsThread.join();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        if(searchThread != null) {
            searchRunnable.terminate();
            // No need to join, since we don't really terminate the thread. We just demand
            // it to post its result runnable into the gui main loop.
        }
    }

    void displayList() {

    }

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         */
        void onItemSelected(String id);
    }

    Callbacks mCallbacks = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = getListView();
        videoListAdapter = new VideoListAdapter(getActivity(), this);
        setListAdapter(videoListAdapter);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null

                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }

        getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
            long lastScrollDate = 0;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (mode != PRESENT_VIDEOS_MODE
                        && list.getChildAt(0) != null
                        && list.getLastVisiblePosition() == list.getAdapter().getCount() - 1
                        && list.getChildAt(list.getChildCount() - 1).getBottom() <= list.getHeight()) {
                    long time = System.currentTimeMillis();
                    if ((time - lastScrollDate) > 200) {
                        lastScrollDate = time;
                        nextPage();
                    }
                }
            }

        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Activities containing this fragment must implement its callbacks.
        if (!(context instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        setActivatedPosition(position);
        mCallbacks.onItemSelected(Long.toString(id));
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
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        getListView().setChoiceMode(activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE);
    }

    private void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

}
