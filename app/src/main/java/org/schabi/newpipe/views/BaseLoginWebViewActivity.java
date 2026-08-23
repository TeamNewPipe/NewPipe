package org.schabi.newpipe.views;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import org.schabi.newpipe.R;

public abstract class BaseLoginWebViewActivity extends AppCompatActivity {

    protected WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_webview);

        webView = findViewById(R.id.login_webview);
        configureWebView();
        webView.setWebViewClient(createWebViewClient());
        loadLoginUrl();
    }

    protected abstract String getLoginUrl();
    protected abstract String getSuccessCookieIndicator();
    protected abstract void configureWebView();
    protected abstract WebViewClient createWebViewClient();
    protected abstract void handleSuccessfulLogin(String cookies);

    protected void loadLoginUrl() {
        webView.loadUrl(getLoginUrl());
    }

    protected void finishWithResult(Intent intent) {
        setResult(RESULT_OK, intent);
        runOnUiThread(() -> {
            if (!isFinishing()) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.onPause();
                webView.removeAllViews();
                webView.destroy();
                finish();
            }
        });
    }

    protected class StandardWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && cookies.contains(getSuccessCookieIndicator())) {
                handleSuccessfulLogin(cookies);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
    }
}
