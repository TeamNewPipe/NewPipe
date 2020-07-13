package org.schabi.newpipe;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.util.ThemeHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

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

    private WebView webView;
    private String foundCookies = "";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recaptcha);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        String url = getIntent().getStringExtra(RECAPTCHA_URL_EXTRA);
        if (url == null || url.isEmpty()) {
            url = YT_URL;
        }

        // set return to Cancel by default
        setResult(RESULT_CANCELED);


        webView = findViewById(R.id.reCaptchaWebView);

        // enable Javascript
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view,
                                                    final WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "shouldOverrideUrlLoading: request.url=" + url);
                }

                handleCookiesFromUrl(url);
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "shouldOverrideUrlLoading: url=" + url);
                }

                handleCookiesFromUrl(url);
                return false;
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                handleCookiesFromUrl(url);
            }
        });

        // cleaning cache, history and cookies from webView
        webView.clearCache(true);
        webView.clearHistory();
        android.webkit.CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(aBoolean -> {
            });
        } else {
            cookieManager.removeAllCookie();
        }

        webView.loadUrl(url);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_recaptcha, menu);

        ActionBar actionBar = getSupportActionBar();
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
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_item_done:
                saveCookiesAndFinish();
                return true;
            default:
                return false;
        }
    }

    private void saveCookiesAndFinish() {
        handleCookiesFromUrl(webView.getUrl()); // try to get cookies of unclosed page
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

        Intent intent = new Intent(this, org.schabi.newpipe.MainActivity.class);
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

        String cookies = CookieManager.getInstance().getCookie(url);
        handleCookies(cookies);

        // sometimes cookies are inside the url
        int abuseStart = url.indexOf("google_abuse=");
        if (abuseStart != -1) {
            int abuseEnd = url.indexOf("+path");

            try {
                String abuseCookie = url.substring(abuseStart + 13, abuseEnd);
                abuseCookie = URLDecoder.decode(abuseCookie, "UTF-8");
                handleCookies(abuseCookie);
            } catch (UnsupportedEncodingException | StringIndexOutOfBoundsException e) {
                if (MainActivity.DEBUG) {
                    e.printStackTrace();
                    Log.d(TAG, "handleCookiesFromUrl: invalid google abuse starting at "
                            + abuseStart + " and ending at " + abuseEnd + " for url " + url);
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
