package org.schabi.newpipe.search_fragment;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Created by Christian Schabesberger on 02.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SearchWorker.java is part of NewPipe.
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


public class SearchWorker {
    private static final String TAG = SearchWorker.class.toString();

    public interface SearchWorkerResultListener {
        void onResult(SearchResult result);
        void onNothingFound(final int stringResource);
        void onError(String message);
        void onReCaptchaChallenge();
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
                searchWorkerResultListener.onResult(result);
            }
        }
    }

    private class SearchRunnable implements Runnable {
        public static final String YOUTUBE = "Youtube";
        private final String query;
        private final int page;
        private final EnumSet<SearchEngine.Filter> filter;
        final Handler h = new Handler();
        private volatile boolean runs = true;
        private Activity a = null;
        private int serviceId = -1;
        public SearchRunnable(int serviceId,
                              String query,
                              int page,
                              EnumSet<SearchEngine.Filter> filter,
                              Activity activity,
                              int requestId) {
            this.serviceId = serviceId;
            this.query = query;
            this.page = page;
            this.filter = filter;
            this.a = activity;
        }
        void terminate() {
            runs = false;
        }
        @Override
        public void run() {
            final String serviceName = NewPipe.getNameOfService(serviceId);
            SearchResult result = null;
            SearchEngine engine = null;

            try {
                engine = NewPipe.getService(serviceId)
                        .getSearchEngineInstance();
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
                        .getSearchResult(engine, query, page, searchLanguage, filter);
                if(runs) {
                    h.post(new ResultRunnable(result, requestId));
                }

                // look for errors during extraction
                // soft errors:
                View rootView = a.findViewById(android.R.id.content);
                if(result != null &&
                        !result.errors.isEmpty()) {
                    Log.e(TAG, "OCCURRED ERRORS DURING SEARCH EXTRACTION:");
                    for(Throwable e : result.errors) {
                        e.printStackTrace();
                        Log.e(TAG, "------");
                    }

                    if(result.resultList.isEmpty()&& !result.errors.isEmpty()) {
                        // if it compleatly failes dont show snackbar, instead show error directlry
                        ErrorActivity.reportError(h, a, result.errors, null, null,
                                ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                        serviceName, query, R.string.parsing_error));
                    } else {
                        // if it partly show snackbar
                        ErrorActivity.reportError(h, a, result.errors, null, rootView,
                                ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                        serviceName, query, R.string.light_parsing_error));
                    }
                }
                // hard errors:
            } catch (ReCaptchaException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        searchWorkerResultListener.onReCaptchaChallenge();
                    }
                });
            } catch(IOException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        searchWorkerResultListener.onNothingFound(R.string.network_error);
                    }
                });
                e.printStackTrace();
            } catch(final SearchEngine.NothingFoundException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        searchWorkerResultListener.onError(e.getMessage());
                    }
                });
            } catch(ExtractionException e) {
                ErrorActivity.reportError(h, a, e, null, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                serviceName, query, R.string.parsing_error));
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
    private SearchWorkerResultListener searchWorkerResultListener = null;
    private SearchRunnable runnable = null;
    private int requestId = 0;     //prevents running requests that have already ben expired

    public static SearchWorker getInstance() {
        return searchWorker == null ? (searchWorker = new SearchWorker()) : searchWorker;
    }

    public void setSearchWorkerResultListener(SearchWorkerResultListener listener) {
        searchWorkerResultListener = listener;
    }

    private SearchWorker() {

    }

    public void search(int serviceId,
                       String query,
                       int page,
                       Activity a,
                       EnumSet<SearchEngine.Filter> filter) {
        if(runnable != null) {
            terminate();
        }
        runnable = new SearchRunnable(serviceId, query, page, filter, a, requestId);
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void terminate() {
        requestId++;
        runnable.terminate();
    }
}
