package org.schabi.newpipe.views;

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;

public class YouTubeLoginWebViewActivity extends BaseLoginWebViewActivity {

    private boolean hasLoaded = false;

    @Override
    protected String getLoginUrl() {
        return "https://www.youtube.com/signin";
    }

    @Override
    protected String getSuccessCookieIndicator() {
        return "SID=";
    }

    @Override
    protected void configureWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
    }

    @Override
    protected WebViewClient createWebViewClient() {
        return new YouTubeWebViewClient();
    }

    @Override
    protected void handleSuccessfulLogin(String cookies) {
        // YouTube handles success differently - through shouldInterceptRequest
    }

    private class YouTubeWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (!hasLoaded && cookies != null && cookies.contains(getSuccessCookieIndicator())) {
                hasLoaded = true;
                webView.loadUrl("https://www.youtube.com/watch?v=09839DpTctU");
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.contains("googlevideo.com/videoplayback")) {
                Uri uri = Uri.parse(url);
                String pot = uri.getQueryParameter("pot");
                Intent intent = new Intent();
                String cookies = CookieManager.getInstance().getCookie("https://music.youtube.com/watch");
                intent.putExtra("cookies", cookies);
                intent.putExtra("pot", pot);
                finishWithResult(intent);
            }
            return null;
        }
    }
}
