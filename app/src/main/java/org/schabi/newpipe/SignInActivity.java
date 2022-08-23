package org.schabi.newpipe;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class SignInActivity extends AppCompatActivity {
    // TODO: figure out the bare-minimum URL parameters to use
    private static final String NAV_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true"
                    + "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin"
                    + "%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den%26next%3Dhttps"
                    + "%253A%252F%252Fwww.youtube.com%252F&hl=en&ec=65620";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        final WebView webView = findViewById(R.id.sign_in_web_view);
        webView.clearCache(true);
        webView.clearFormData();
        webView.clearHistory();
        webView.clearSslPreferences();

        final WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(final WebView view,
                                       final String url) {
                super.onPageFinished(view, url);

                if (view.getProgress() != 100) {
                    return;
                }

                final String cookies = CookieManager.getInstance().getCookie(url);

                if (!cookies.contains("SAPISID")) {
                    return;
                }

                final Intent resultIntent = new Intent();
                resultIntent.putExtra("site", "youtube");
                resultIntent.putExtra("cookies", cookies);

                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });

        webView.loadUrl(NAV_URL);
    }
}
