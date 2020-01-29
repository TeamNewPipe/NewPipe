package org.schabi.newpipe;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    private String foundCookies = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recaptcha);

        String url = getIntent().getStringExtra(RECAPTCHA_URL_EXTRA);
        if (url == null || url.isEmpty()) {
            url = YT_URL;
        }


        // Set return to Cancel by default
        setResult(RESULT_CANCELED);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        WebView myWebView = findViewById(R.id.reCaptchaWebView);

        // Enable Javascript
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleCookies(CookieManager.getInstance().getCookie(url));
            }
        });

        // Cleaning cache, history and cookies from webView
        myWebView.clearCache(true);
        myWebView.clearHistory();
        android.webkit.CookieManager cookieManager = CookieManager .getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(aBoolean -> {});
        } else {
            cookieManager.removeAllCookie();
        }

        myWebView.loadUrl(url);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ret = super.onCreateOptionsMenu(menu);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(getResources().getDrawable(R.drawable.ic_done_white_24dp));
            actionBar.setTitle(R.string.title_activity_recaptcha);
            actionBar.setSubtitle(R.string.subtitle_activity_recaptcha);
        }

        return ret;
    }

    @Override
    public void onBackPressed() {
        saveCookiesAndFinish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                saveCookiesAndFinish();
                return true;
            default:
                return false;
        }
    }

    private void saveCookiesAndFinish() {
        if (!foundCookies.isEmpty()) {
            // Give cookies to Downloader class
            DownloaderImpl.getInstance().setCookies(foundCookies);
            setResult(RESULT_OK);
        }

        Intent intent = new Intent(this, org.schabi.newpipe.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NavUtils.navigateUpTo(this, intent);
    }



    private void handleCookies(@Nullable String cookies) {
        if (MainActivity.DEBUG) Log.d(TAG, "handleCookies: cookies=" + (cookies == null ? "null" : cookies));
        if (cookies == null) return;

        addYoutubeCookies(cookies);
        // add other methods to extract cookies here
    }

    private void addYoutubeCookies(@Nonnull String cookies) {
        String c_s_gl = "";
        String c_goojf = "";

        String[] parts = cookies.split(";");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith("s_gl")) {
                c_s_gl = trimmedPart;
            }
            if (trimmedPart.startsWith("goojf")) {
                c_goojf = trimmedPart;
            }
        }
        if (c_s_gl.length() > 0 && c_goojf.length() > 0) {
            // addCookie(c_s_gl);
            // addCookie(c_goojf);
            // Youtube seems to also need the other cookies:
            addCookie(cookies);
        }
    }

    private void addCookie(String cookie) {
        if (foundCookies.isEmpty() || foundCookies.endsWith("; ")) {
            foundCookies += cookie;
        } else if (foundCookies.endsWith(";")) {
            foundCookies += " " + cookie;
        } else {
            foundCookies += "; " + cookie;
        }
    }
}
