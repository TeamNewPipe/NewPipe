package org.schabi.newpipe.search_fragment;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.SuggestionExtractor;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.R;

import java.io.IOException;
import java.util.List;

/**
 * Created by Christian Schabesberger on 02.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SuggestionSearchRunnable.java is part of NewPipe.
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

public class SuggestionSearchRunnable implements Runnable{

    /**
     * Runnable to update a {@link SuggestionListAdapter}
     */
    private class SuggestionResultRunnable implements Runnable{

        private final List<String> suggestions;

        private SuggestionResultRunnable(List<String> suggestions) {
            this.suggestions = suggestions;
        }

        @Override
        public void run() {
            adapter.updateAdapter(suggestions);
        }
    }

    private final int serviceId;
    private final String query;
    private final Handler h = new Handler();
    private final Activity a;
    private final SuggestionListAdapter adapter;
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
            SuggestionExtractor se =
                    NewPipe.getService(serviceId).getSuggestionExtractorInstance();
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(a);
            String searchLanguageKey = a.getString(R.string.search_language_key);
            String searchLanguage = sp.getString(searchLanguageKey,
                    a.getString(R.string.default_language_value));
            List<String> suggestions = se.suggestionList(query, searchLanguage);
            h.post(new SuggestionResultRunnable(suggestions));
        } catch (ExtractionException e) {
            ErrorActivity.reportError(h, a, e, null, a.findViewById(android.R.id.content),
                    ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                            NewPipe.getNameOfService(serviceId), query, R.string.parsing_error));
            e.printStackTrace();
        } catch (IOException e) {
            postNewErrorToast(h, R.string.network_error);
            e.printStackTrace();
        } catch (Exception e) {
            ErrorActivity.reportError(h, a, e, null, a.findViewById(android.R.id.content),
                    ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                            NewPipe.getNameOfService(serviceId), query, R.string.general_error));
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