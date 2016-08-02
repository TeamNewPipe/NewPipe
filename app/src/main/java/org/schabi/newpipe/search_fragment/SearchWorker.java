package org.schabi.newpipe.search_fragment;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.ErrorActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.SearchEngine;
import org.schabi.newpipe.extractor.SearchResult;
import org.schabi.newpipe.extractor.ServiceList;

import java.io.IOException;

/**
 * Created by the-scrabi on 02.08.16.
 */


public class SearchWorker {
    private static final String TAG = SearchWorker.class.toString();

    public interface SearchWorkerResultListner {
        void onResult(SearchResult result);
        void onNothingFound(final int stringResource);
        void onError(String message);
    }

    private class ResultRunnable implements Runnable {
        private final SearchResult result;
        private int requestId = 0;
        public ResultRunnable(SearchResult result, int requestId) {
            this.result = result;
            this.requestId = requestId;
        }
        @Override
        public void run() {
            if(this.requestId == SearchWorker.this.requestId) {
                searchWorkerResultListner.onResult(result);
            }
        }
    }

    private class SearchRunnable implements Runnable {
        public static final String YOUTUBE = "Youtube";
        private final String query;
        private final int page;
        final Handler h = new Handler();
        private volatile boolean runs = true;
        private Activity a = null;
        private int serviceId = -1;
        public SearchRunnable(int serviceId, String query, int page, Activity activity, int requestId) {
            this.serviceId = serviceId;
            this.query = query;
            this.page = page;
            this.a = activity;
        }
        void terminate() {
            runs = false;
        }
        @Override
        public void run() {
            SearchResult result = null;
            SearchEngine engine = null;

            try {
                engine = ServiceList.getService(serviceId)
                        .getSearchEngineInstance(new Downloader());
            } catch(ExtractionException e) {
                ErrorActivity.reportError(h, a, e, null, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                Integer.toString(serviceId), query, R.string.general_error));
                return;
            }

            try {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(a);
                String searchLanguageKey = a.getString(R.string.search_language_key);
                String searchLanguage = sp.getString(searchLanguageKey,
                        a.getString(R.string.default_language_value));
                result = SearchResult
                        .getSearchResult(engine, query, page, searchLanguage, new Downloader());

                if(runs) {
                    h.post(new ResultRunnable(result, requestId));
                }

                // look for errors during extraction
                // soft errors:
                if(result != null &&
                        !result.errors.isEmpty()) {
                    Log.e(TAG, "OCCURRED ERRORS DURING SEARCH EXTRACTION:");
                    for(Throwable e : result.errors) {
                        e.printStackTrace();
                        Log.e(TAG, "------");
                    }

                    View rootView = a.findViewById(R.id.videoitem_list);
                    ErrorActivity.reportError(h, a, result.errors, null, rootView,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                        /* todo: this shoudl not be assigned static */  YOUTUBE, query, R.string.light_parsing_error));

                }
                // hard errors:
            } catch(IOException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        searchWorkerResultListner.onNothingFound(R.string.network_error);
                    }
                });
                e.printStackTrace();
            } catch(final SearchEngine.NothingFoundException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        searchWorkerResultListner.onError(e.getMessage());
                    }
                });
            } catch(ExtractionException e) {
                ErrorActivity.reportError(h, a, e, null, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                        /* todo: this shoudl not be assigned static */
                                YOUTUBE, query, R.string.parsing_error));
                //postNewErrorToast(h, R.string.parsing_error);
                e.printStackTrace();

            } catch(Exception e) {
                ErrorActivity.reportError(h, a, e, null, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                        /* todo: this shoudl not be assigned static */ YOUTUBE, query, R.string.general_error));

                e.printStackTrace();
            }
        }
    }

    private static SearchWorker searchWorker = null;
    private SearchWorkerResultListner searchWorkerResultListner = null;
    private SearchRunnable runnable = null;
    private int requestId = 0;     //prevents running requests that have already ben expired

    public static SearchWorker getInstance() {
        return searchWorker == null ? (searchWorker = new SearchWorker()) : searchWorker;
    }

    public void setSearchWorkerResultListner(SearchWorkerResultListner listener) {
        searchWorkerResultListner = listener;
    }

    private SearchWorker() {

    }

    public void search(int serviceId, String query, int page, Activity a) {
        if(runnable != null) {
            terminate();
        }
        runnable = new SearchRunnable(serviceId, query, page, a, requestId);
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void terminate() {
        requestId++;
        runnable.terminate();
    }
}
