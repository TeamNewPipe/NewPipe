package org.schabi.newpipe.search_fragment;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.ErrorActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.SearchEngine;
import org.schabi.newpipe.extractor.ServiceList;

import java.io.IOException;
import java.util.List;

/**
 * Created by the-scrabi on 02.08.16.
 */

public class SuggestionSearchRunnable implements Runnable{

    private class SuggestionResultRunnable implements Runnable{

        private List<String> suggestions;
        private SuggestionListAdapter adapter;

        private SuggestionResultRunnable(List<String> suggestions, SuggestionListAdapter adapter) {
            this.suggestions = suggestions;
            this.adapter = adapter;
        }

        @Override
        public void run() {
            adapter.updateAdapter(suggestions);
        }
    }

    private final int serviceId;
    private final String query;
    final Handler h = new Handler();
    private Activity a = null;
    private SuggestionListAdapter adapter;
    public SuggestionSearchRunnable(int serviceId, String query,
                                     Activity activity, SuggestionListAdapter adapter) {
        this.serviceId = serviceId;
        this.query = query;
        this.a = activity;
        this.adapter = adapter;
    }

    @Override
    public void run() {
        try {
            SearchEngine engine =
                    ServiceList.getService(serviceId).getSearchEngineInstance(new Downloader());
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(a);
            String searchLanguageKey = a.getString(R.string.search_language_key);
            String searchLanguage = sp.getString(searchLanguageKey,
                    a.getString(R.string.default_language_value));
            List<String> suggestions = engine.suggestionList(query,searchLanguage,new Downloader());
            h.post(new SuggestionResultRunnable(suggestions, adapter));
        } catch (ExtractionException e) {
            ErrorActivity.reportError(h, a, e, null, a.findViewById(android.R.id.content),
                    ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                            ServiceList.getNameOfService(serviceId), query, R.string.parsing_error));
            e.printStackTrace();
        } catch (IOException e) {
            postNewErrorToast(h, R.string.network_error);
            e.printStackTrace();
        } catch (Exception e) {
            ErrorActivity.reportError(h, a, e, null, a.findViewById(android.R.id.content),
                    ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                            ServiceList.getNameOfService(serviceId), query, R.string.general_error));
        }
    }

    private void postNewErrorToast(Handler h, final int stringResource) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(a, a.getString(stringResource),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}