package org.schabi.newpipe.workers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.view.View;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;
import org.schabi.newpipe.report.ErrorActivity;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Return list of results based on a query
 *
 * @author mauriciocolli
 */
public class SearchWorker extends AbstractWorker {

    private EnumSet<SearchEngine.Filter> filter;
    private String query;
    private int page;
    private OnSearchResult callback;

    /**
     * Interface which will be called for result and errors
     */
    public interface OnSearchResult {
        void onSearchResult(SearchResult result);
        void onNothingFound(String message);
        void onSearchError(int messageId);
        void onReCaptchaChallenge();
    }

    public SearchWorker(Context context, int serviceId, String query, int page, EnumSet<SearchEngine.Filter> filter, OnSearchResult callback) {
        super(context, serviceId);
        this.callback = callback;
        this.query = query;
        this.page = page;
        this.filter = filter;
    }

    public static SearchWorker startForQuery(Context context, int serviceId, @NonNull String query, int page, EnumSet<SearchEngine.Filter> filter, OnSearchResult callback) {
        SearchWorker worker = new SearchWorker(context, serviceId, query, page, filter, callback);
        worker.start();
        return worker;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.callback = null;
    }

    @Override
    protected void doWork(int serviceId) throws Exception {
        SearchEngine searchEngine = getService().getSearchEngineInstance();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        String searchLanguageKey = getContext().getString(R.string.search_language_key);
        String searchLanguage = sharedPreferences.getString(searchLanguageKey, getContext().getString(R.string.default_language_value));

        final SearchResult searchResult = SearchResult.getSearchResult(searchEngine, query, page, searchLanguage, filter);
        if (callback != null && searchResult != null && !isInterrupted()) getHandler().post(new Runnable() {
            @Override
            public void run() {
                if (isInterrupted() || callback == null) return;

                callback.onSearchResult(searchResult);
                onDestroy();
            }
        });
    }

    @Override
    protected void handleException(final Exception exception, int serviceId) {
        if (callback == null || getHandler() == null || isInterrupted()) return;

        if (exception instanceof ReCaptchaException) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onReCaptchaChallenge();
                }
            });
        } else if (exception instanceof IOException) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onSearchError(R.string.network_error);
                }
            });
        } else if (exception instanceof SearchEngine.NothingFoundException) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onNothingFound(exception.getMessage());
                }
            });
        } else if (exception instanceof ExtractionException) {
            View rootView = getContext() instanceof Activity ? ((Activity) getContext()).findViewById(android.R.id.content) : null;
            ErrorActivity.reportError(getHandler(), getContext(), exception, null, rootView, ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED, getServiceName(), query, R.string.parsing_error));
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onSearchError(R.string.parsing_error);
                }
            });
        } else {
            View rootView = getContext() instanceof Activity ? ((Activity) getContext()).findViewById(android.R.id.content) : null;
            ErrorActivity.reportError(getHandler(), getContext(), exception, null, rootView, ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED, getServiceName(), query, R.string.general_error));
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onSearchError(R.string.general_error);
                }
            });
        }
    }
}
