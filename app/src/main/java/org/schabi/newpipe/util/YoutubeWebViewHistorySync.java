package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.fragments.YoutubeWebViewFragment;

/**
 * Best-effort YouTube watch-history sync using a short-lived hidden WebView.
 *
 * <p>This follows LiteTube's approach: when enabled, load the watched YouTube URL in a background
 * WebView so a signed-in YouTube Web session can register the view on YouTube's side. The WebView
 * is kept alive briefly after the page finishes, then stopped and destroyed.</p>
 */
public final class YoutubeWebViewHistorySync {
    private static final String TAG = "YoutubeHistorySync";
    private static final long KEEP_ALIVE_AFTER_PAGE_FINISHED_MILLIS = 5_000L;

    private YoutubeWebViewHistorySync() {
    }

    public static boolean isEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.enable_webview_history_sync_key), false);
    }

    public static void syncIfEnabled(@NonNull final Context context,
                                     final int serviceId,
                                     @Nullable final String url) {
        if (serviceId != ServiceList.YouTube.getServiceId() || url == null || !isEnabled(context)) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        final String syncUrl = YoutubeWebViewFragment.canonicalWatchUrl(url);
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        if (Looper.myLooper() == Looper.getMainLooper()) {
            startHiddenWebView(appContext, syncUrl);
        } else {
            mainHandler.post(() -> startHiddenWebView(appContext, syncUrl));
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void startHiddenWebView(@NonNull final Context context,
                                           @NonNull final String url) {
        try {
            final WebView backgroundWebView = new WebView(context);
            final WebSettings settings = backgroundWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);

            backgroundWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(final WebView view, final String pageUrl) {
                    super.onPageFinished(view, pageUrl);
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> destroy(backgroundWebView),
                            KEEP_ALIVE_AFTER_PAGE_FINISHED_MILLIS);
                }
            });
            backgroundWebView.loadUrl(url);
        } catch (final Exception e) {
            Log.e(TAG, "Could not sync YouTube watch history", e);
        }
    }

    private static void destroy(@NonNull final WebView webView) {
        try {
            webView.stopLoading();
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        } catch (final Exception e) {
            Log.e(TAG, "Could not destroy hidden history-sync WebView", e);
        }
    }
}
