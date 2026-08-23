package org.schabi.newpipe.views;

import android.content.Intent;
import android.webkit.WebViewClient;

public class NicoNicoLoginWebViewActivity extends BaseLoginWebViewActivity {

    @Override
    protected String getLoginUrl() {
        return "https://account.nicovideo.jp/login?site=niconico";
    }

    @Override
    protected String getSuccessCookieIndicator() {
        return "user_session";
    }

    @Override
    protected void configureWebView() {
        // NicoNico uses default WebView configuration
    }

    @Override
    protected WebViewClient createWebViewClient() {
        return new StandardWebViewClient();
    }

    @Override
    protected void handleSuccessfulLogin(String cookies) {
        Intent intent = new Intent();
        intent.putExtra("cookies", cookies);
        finishWithResult(intent);
    }
}
