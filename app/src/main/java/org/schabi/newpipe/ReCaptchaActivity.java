package org.schabi.newpipe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

    public static final String TAG = ReCaptchaActivity.class.toString();
    public static final String YT_URL = "https://www.youtube.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recaptcha);

        // Set return to Cancel by default
        setResult(RESULT_CANCELED);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.reCaptcha_title);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        WebView myWebView = findViewById(R.id.reCaptchaWebView);

        // Enable Javascript
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        ReCaptchaWebViewClient webClient = new ReCaptchaWebViewClient(this);
        myWebView.setWebViewClient(webClient);

        // Cleaning cache, history and cookies from webView
        myWebView.clearCache(true);
        myWebView.clearHistory();
        android.webkit.CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(new ValueCallback<Boolean>() {
                @Override
                public void onReceiveValue(Boolean aBoolean) {}
            });
        } else {
            cookieManager.removeAllCookie();
        }

        myWebView.loadUrl(YT_URL);
    }

    private class ReCaptchaWebViewClient extends WebViewClient {
        private final Activity context;
        private String mCookies;

        ReCaptchaWebViewClient(Activity ctx) {
            context = ctx;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            // TODO: Start Loader
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            String cookies = CookieManager.getInstance().getCookie(url);

            // TODO: Stop Loader

            // find cookies : s_gl & goojf and Add cookies to Downloader
            if (find_access_cookies(cookies)) {
                // Give cookies to Downloader class
                Downloader.getInstance().setCookies(mCookies);

                // Closing activity and return to parent
                setResult(RESULT_OK);
                finish();
            }
        }

        private boolean find_access_cookies(String cookies) {
            boolean ret = false;
            String c_s_gl = "";
            String c_goojf = "";

            String[] parts = cookies.split("; ");
            for (String part : parts) {
                if (part.trim().startsWith("s_gl")) {
                    c_s_gl = part.trim();
                }
                if (part.trim().startsWith("goojf")) {
                    c_goojf = part.trim();
                }
            }
            if (c_s_gl.length() > 0 && c_goojf.length() > 0) {
                ret = true;
                //mCookies = c_s_gl + "; " + c_goojf;
                // Youtube seems to also need the other cookies:
                mCookies = cookies;
            }

            return ret;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home: {
                Intent intent = new Intent(this, org.schabi.newpipe.MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                NavUtils.navigateUpTo(this, intent);
                return true;
            }
            default:
                return false;
        }
    }
}
