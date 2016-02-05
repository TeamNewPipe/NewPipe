package org.schabi.newpipe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Vector;

import org.schabi.newpipe.crawler.CrawlingException;
import org.schabi.newpipe.crawler.VideoPreviewInfo;
import org.schabi.newpipe.crawler.SearchEngine;
import org.schabi.newpipe.crawler.StreamingService;


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
    // used to track down if results posted by threads ar still valid
    private int currentRequestId = -1;
    private ListView list;

    private View footer;

    // used to suppress request for loading a new page while another page is already loading.
    private boolean loadingNextPage = true;

    private class ResultRunnable implements Runnable {
        private final SearchEngine.Result result;
        private final int requestId;
        public ResultRunnable(SearchEngine.Result result, int requestId) {
            this.result = result;
            this.requestId = requestId;
        }
        @Override
        public void run() {
            updateListOnResult(result, requestId);
            if (android.os.Build.VERSION.SDK_INT >= 19) {
                getListView().removeFooterView(footer);
            }
        }
    }

    private class SearchRunnable implements Runnable {
        private final SearchEngine engine;
        private final String query;
        private final int page;
        final Handler h = new Handler();
        private volatile boolean runs = true;
        private final int requestId;
        public SearchRunnable(SearchEngine engine, String query, int page, int requestId) {
            this.engine = engine;
            this.query = query;
            this.page = page;
            this.requestId = requestId;
        }
        void terminate() {
            runs = false;
        }
        @Override
        public void run() {
            try {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
                String searchLanguageKey = getContext().getString(R.string.search_language_key);
                String searchLanguage = sp.getString(searchLanguageKey,
                        getString(R.string.default_language_value));
                SearchEngine.Result result = engine.search(query, page, searchLanguage,
                        new Downloader());

                Log.i(TAG, "language code passed:\""+searchLanguage+"\"");
                if(runs) {
                    h.post(new ResultRunnable(result, requestId));
                }
            } catch(IOException e) {
                postNewErrorToast(h, R.string.network_error);
                e.printStackTrace();
            } catch(CrawlingException ce) {
                postNewErrorToast(h, R.string.parsing_error);
                ce.printStackTrace();
            } catch(Exception e) {
                postNewErrorToast(h, R.string.general_error);
                e.printStackTrace();
            }
        }
    }
/*
<<<
    private class LoadThumbsRunnable implements Runnable {
        private final Vector<String> thumbnailUrlList = new Vector<>();
        private final Vector<Boolean> downloadedList;
        final Handler h = new Handler();
        private volatile boolean run = true;
        private final int requestId;
        public LoadThumbsRunnable(Vector<VideoPreviewInfo> videoList,
                                  Vector<Boolean> downloadedList, int requestId) {
            for(VideoPreviewInfo item : videoList) {
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
                    Bitmap thumbnail;
                    try {
                        //todo: make bitmaps not bypass tor
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
        private final int index;
        private final Bitmap thumbnail;
        private final int requestId;
        public SetThumbnailRunnable(int index, Bitmap thumbnail, int requestId) {
            this.index = index;
            this.thumbnail = thumbnail;
            this.requestId = requestId;
        }
        @Override
        public void run() {
            if(requestId == currentRequestId) {
                videoListAdapter.updateDownloadedThumbnailList(index);
                videoListAdapter.setThumbnail(index, thumbnail);
            }
        }
    }

=======
>>>>>>> 6d1b4652fc98e5c2d5e19b0f98ba38a731137a70
*/
    public void present(List<VideoPreviewInfo> videoList) {
        mode = PRESENT_VIDEOS_MODE;
        setListShown(true);
        getListView().smoothScrollToPosition(0);

        updateList(videoList);
    }

    public void search(String query) {
        mode = SEARCH_MODE;
        this.query = query;
        this.lastPage = 1;
        videoListAdapter.clearVideoList();
        setListShown(false);
        startSearch(query, lastPage);
        //todo: Somehow this command is not working on older devices,
        // although it was introduced with API level 8. Test this and find a solution.
        getListView().smoothScrollToPosition(0);
    }

    private void nextPage() {
        loadingNextPage = true;
        lastPage++;
        Log.d(TAG, getString(R.string.search_page) + Integer.toString(lastPage));
        startSearch(query, lastPage);
    }

    private void startSearch(String query, int page) {
        currentRequestId++;
        terminateThreads();
        searchRunnable = new SearchRunnable(streamingService.getSearchEngineInstance(),
                                            query, page, currentRequestId);
        searchThread = new Thread(searchRunnable);
        searchThread.start();
    }

    public void setStreamingService(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    private void updateListOnResult(SearchEngine.Result result, int requestId) {
        if(requestId == currentRequestId) {
            setListShown(true);
            if (result.resultList.isEmpty()) {
                Toast.makeText(getActivity(), result.errorMessage, Toast.LENGTH_LONG).show();
            } else {
                if (!result.suggestion.isEmpty()) {
                    Toast.makeText(getActivity(), getString(R.string.did_you_mean) + result.suggestion + " ?",
                            Toast.LENGTH_LONG).show();
                }
                updateList(result.resultList);
            }
        }
    }

    private void updateList(List<VideoPreviewInfo> list) {
        try {
            videoListAdapter.addVideoList(list);
            terminateThreads();
        } catch(java.lang.IllegalStateException e) {
            Log.w(TAG, "Trying to set value while activity doesn't exist anymore.");
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            loadingNextPage = false;
        }
    }

    private void terminateThreads() {
        if(searchThread != null) {
            searchRunnable.terminate();
            // No need to join, since we don't really terminate the thread. We just demand
            // it to post its result runnable into the gui main loop.
        }
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

    private Callbacks mCallbacks = null;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = getListView();
        videoListAdapter = new VideoListAdapter(getActivity(), this);
        footer = ((LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.paginate_footer, null, false);


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
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                if (mode != PRESENT_VIDEOS_MODE
                        && list.getChildAt(0) != null
                        && list.getLastVisiblePosition() == list.getAdapter().getCount() - 1
                        && list.getChildAt(list.getChildCount() - 1).getBottom() <= list.getHeight()) {
                    long time = System.currentTimeMillis();
                    if ((time - lastScrollDate) > 200
                            && !loadingNextPage) {
                        lastScrollDate = time;
                        getListView().addFooterView(footer);
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
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        setActivatedPosition(position);
        mCallbacks.onItemSelected(Long.toString(id));
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(@SuppressWarnings("SameParameterValue") boolean activateOnItemClick) {
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

    private void postNewErrorToast(Handler h, final int stringResource) {
        h.post(new Runnable() {
            @Override
            public void run() {
                setListShown(true);
                Toast.makeText(getActivity(), getString(R.string.network_error),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
