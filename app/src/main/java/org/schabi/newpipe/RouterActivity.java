package org.schabi.newpipe;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.widget.Toast;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.Collection;
import java.util.HashSet;

import icepick.Icepick;
import icepick.State;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/*
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * RouterActivity.java is part of NewPipe.
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

/**
 * This Acitivty is designed to route share/open intents to the specified service, and
 * to the part of the service which can handle the url.
 */
public class RouterActivity extends AppCompatActivity {

    @State
    protected String currentUrl;
    protected CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);

        if (TextUtils.isEmpty(currentUrl)) {
            currentUrl = getUrl(getIntent());

            if (TextUtils.isEmpty(currentUrl)) {
                Toast.makeText(this, R.string.invalid_url_toast, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handleUrl(currentUrl);
    }

    protected void handleUrl(String url) {
        disposables.add(Observable
                .fromCallable(() -> NavigationHelper.getIntentByLink(this, url))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(intent -> {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);

                    finish();
                }, this::handleError)
        );
    }

    protected void handleError(Throwable error) {
        error.printStackTrace();

        if (error instanceof ExtractionException) {
            Toast.makeText(this, R.string.url_not_supported_toast, Toast.LENGTH_LONG).show();
        } else {
            ExtractorHelper.handleGeneralException(this, -1, null, error, UserAction.SOMETHING_ELSE, null);
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        disposables.clear();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Removes invisible separators (\p{Z}) and punctuation characters including
     * brackets (\p{P}). See http://www.regular-expressions.info/unicode.html for
     * more details.
     */
    protected final static String REGEX_REMOVE_FROM_URL = "[\\p{Z}\\p{P}]";

    protected String getUrl(Intent intent) {
        // first gather data and find service
        String videoUrl = null;
        if (intent.getData() != null) {
            // this means the video was called though another app
            videoUrl = intent.getData().toString();
        } else if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            //this means that vidoe was called through share menu
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            final String[] uris = getUris(extraText);
            videoUrl = uris.length > 0 ? uris[0] : null;
        }

        return videoUrl;
    }

    protected String removeHeadingGibberish(final String input) {
        int start = 0;
        for (int i = input.indexOf("://") - 1; i >= 0; i--) {
            if (!input.substring(i, i + 1).matches("\\p{L}")) {
                start = i + 1;
                break;
            }
        }
        return input.substring(start, input.length());
    }

    protected String trim(final String input) {
        if (input == null || input.length() < 1) {
            return input;
        } else {
            String output = input;
            while (output.length() > 0 && output.substring(0, 1).matches(REGEX_REMOVE_FROM_URL)) {
                output = output.substring(1);
            }
            while (output.length() > 0
                    && output.substring(output.length() - 1, output.length()).matches(REGEX_REMOVE_FROM_URL)) {
                output = output.substring(0, output.length() - 1);
            }
            return output;
        }
    }

    /**
     * Retrieves all Strings which look remotely like URLs from a text.
     * Used if NewPipe was called through share menu.
     *
     * @param sharedText text to scan for URLs.
     * @return potential URLs
     */
    protected String[] getUris(final String sharedText)  {
        final Collection<String> result = new HashSet<>();
        if (sharedText != null) {
            final String[] array = sharedText.split("\\p{Space}");
            for (String s : array) {
                s = trim(s);
                if (s.length() != 0) {
                    if (s.matches(".+://.+")) {
                        result.add(removeHeadingGibberish(s));
                    } else if (s.matches(".+\\..+")) {
                        result.add("http://" + s);
                    }
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

}
