package org.schabi.newpipe.error;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityRecaptchaBinding;
import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.util.ThemeHelper;

/*
 * Created by beneth <bmauduit@beneth.fr> on 06.12.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * ReCaptchaActivity.java is part of NewPipe.
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
public class ReCaptchaActivity extends AppCompatActivity {
    public static final int RECAPTCHA_REQUEST = 10;
    public static final String RECAPTCHA_URL_EXTRA = "recaptcha_url_extra";
    public static final String TAG = ReCaptchaActivity.class.toString();
    public static final String YT_URL = "https://www.youtube.com";
    public static final String RECAPTCHA_COOKIES_KEY = "recaptcha_cookies";

    public static String sanitizeRecaptchaUrl(@Nullable final String url) {
        if (url == null || url.trim().isEmpty()) {
            return YT_URL; // YouTube is the most likely service to have thrown a recaptcha
        } else {
            // remove "pbj=1" parameter from YouYube urls, as it makes the page JSON and not HTML
            return url.replace("&pbj=1", "").replace("pbj=1&", "").replace("?pbj=1", "");
        }
    }

    private ActivityRecaptchaBinding recaptchaBinding;
    private String foundCookies = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);

        recaptchaBinding = ActivityRecaptchaBinding.inflate(getLayoutInflater());
        setContentView(recaptchaBinding.getRoot());
        setSupportActionBar(recaptchaBinding.toolbar);

        final String url = sanitizeRecaptchaUrl(getIntent().getStringExtra(RECAPTCHA_URL_EXTRA));
        // set return to Cancel by default
        setResult(RESULT_CANCELED);

        // enable Javascript
        final WebSettings webSettings = recaptchaBinding.reCaptchaWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString(DownloaderImpl.USER_AGENT);

        recaptchaBinding.reCaptchaWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view,
                                                    final WebResourceRequest request) {
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "shouldOverrideUrlLoading: url=" + request.getUrl().toString());
                }

                handleCookiesFromUrl(request.getUrl().toString());
                return false;
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                handleCookiesFromUrl(url);
            }
        });

        // cleaning cache, history and cookies from webView
        recaptchaBinding.reCaptchaWebView.clearCache(true);
        recaptchaBinding.reCaptchaWebView.clearHistory();
        CookieManager.getInstance().removeAllCookies(null);

        recaptchaBinding.reCaptchaWebView.loadUrl(url);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_recaptcha, menu);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setTitle(R.string.title_activity_recaptcha);
            actionBar.setSubtitle(R.string.subtitle_activity_recaptcha);
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        saveCookiesAndFinish();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_done) {
            saveCookiesAndFinish();
            return true;
        }
        return false;
    }

    private void saveCookiesAndFinish() {
        // try to get cookies of unclosed page
        handleCookiesFromUrl(recaptchaBinding.reCaptchaWebView.getUrl());
        if (MainActivity.DEBUG) {
            Log.d(TAG, "saveCookiesAndFinish: foundCookies=" + foundCookies);
        }

        if (!foundCookies.isEmpty()) {
            // save cookies to preferences
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext());
            final String key = getApplicationContext().getString(R.string.recaptcha_cookies_key);
            prefs.edit().putString(key, foundCookies).apply();

            // give cookies to Downloader class
            DownloaderImpl.getInstance().setCookie(RECAPTCHA_COOKIES_KEY, foundCookies);
            setResult(RESULT_OK);
        }

        // Navigate to blank page (unloads youtube to prevent background playback)
        recaptchaBinding.reCaptchaWebView.loadUrl("about:blank");

        final Intent intent = new Intent(this, org.schabi.newpipe.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NavUtils.navigateUpTo(this, intent);
    }


    private void handleCookiesFromUrl(@Nullable final String url) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "handleCookiesFromUrl: url=" + (url == null ? "null" : url));
        }

        if (url == null) {
            return;
        }

        final String cookies = CookieManager.getInstance().getCookie(url);
        handleCookies(cookies);

        // sometimes cookies are inside the url
        final int abuseStart = url.indexOf("google_abuse=");
        if (abuseStart != -1) {
            final int abuseEnd = url.indexOf("+path");

            try {
                String abuseCookie = url.substring(abuseStart + 13, abuseEnd);
                abuseCookie = Utils.decodeUrlUtf8(abuseCookie);
                handleCookies(abuseCookie);
            } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
                if (MainActivity.DEBUG) {
                    Log.e(TAG, "handleCookiesFromUrl: invalid google abuse starting at "
                            + abuseStart + " and ending at " + abuseEnd + " for url " + url, e);
                }
            }
        }
    }

    private void handleCookies(@Nullable final String cookies) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "handleCookies: cookies=" + (cookies == null ? "null" : cookies));
        }

        if (cookies == null) {
            return;
        }

        addYoutubeCookies(cookies);
        // add here methods to extract cookies for other services
    }

    private void addYoutubeCookies(@NonNull final String cookies) {
        if (cookies.contains("s_gl=") || cookies.contains("goojf=")
                || cookies.contains("VISITOR_INFO1_LIVE=")
                || cookies.contains("GOOGLE_ABUSE_EXEMPTION=")) {
            // youtube seems to also need the other cookies:
            addCookie(cookies);
        }
    }

    private void addCookie(final String cookie) {
        if (foundCookies.contains(cookie)) {
            return;
        }

        if (foundCookies.isEmpty() || foundCookies.endsWith("; ")) {
            foundCookies += cookie;
        } else if (foundCookies.endsWith(";")) {
            foundCookies += " " + cookie;
        } else {
            foundCookies += "; " + cookie;
        }
    }
}
