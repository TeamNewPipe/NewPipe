package org.schabi.newpipe.fragments;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

public class YoutubeWebViewFragment extends BaseFragment implements BackPressable {
    private static final String TAG = "YoutubeWebView";
    private static final String ARG_URL = "url";
    private static final String MOBILE_CHROME_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; "
            + "Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile "
            + "Safari/537.36";

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    public static YoutubeWebViewFragment newInstance(@Nullable final String url) {
        final YoutubeWebViewFragment fragment = new YoutubeWebViewFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_youtube_webview, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        webView = rootView.findViewById(R.id.youtube_webview);
        fullscreenContainer = rootView.findViewById(R.id.youtube_webview_fullscreen_container);
        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(getInitialUrl());
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureWebView() {
        final WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(MOBILE_CHROME_USER_AGENT);

        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new WebBridge(), "PipePlayWebView");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(final View view,
                                         final CustomViewCallback callback) {
                showFullscreenView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenView();
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                injectYouTubeLinkInterceptor();
            }

            @Override
            public boolean shouldOverrideUrlLoading(final WebView view,
                                                    final WebResourceRequest request) {
                final String target = request.getUrl().toString();
                if (target.startsWith("intent://")) {
                    return true;
                }
                final boolean youtube = target.contains("youtube.com")
                        || target.contains("youtu.be")
                        || target.contains("youtube-nocookie.com");
                if (!youtube && (target.startsWith("http://") || target.startsWith("https://"))) {
                    ShareUtils.openUrlInBrowser(requireContext(), target);
                    return true;
                }
                return false;
            }
        });
    }

    private String getInitialUrl() {
        final String requested = getArguments() == null ? null : getArguments().getString(ARG_URL);
        return requested == null || requested.isEmpty()
                ? "https://m.youtube.com/"
                : canonicalWatchUrl(requested);
    }

    public static String canonicalWatchUrl(final String url) {
        try {
            final Uri uri = Uri.parse(url);
            final String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String videoId = uri.getQueryParameter("v");
            final String path = uri.getPath() == null ? "" : uri.getPath();
            if ((host.equals("youtu.be") || host.endsWith(".youtu.be")) && path.length() > 1) {
                videoId = path.substring(1).split("/")[0];
            } else if (path.startsWith("/shorts/") || path.startsWith("/embed/")
                    || path.startsWith("/v/")) {
                final String[] parts = path.split("/");
                if (parts.length >= 3) {
                    videoId = parts[2];
                }
            }
            if (videoId != null && !videoId.isEmpty()) {
                return "https://www.youtube.com/watch?v=" + Uri.encode(videoId);
            }
        } catch (final Exception e) {
            Log.w(TAG, "Could not canonicalize URL: " + url, e);
        }
        return url;
    }

    private void injectYouTubeLinkInterceptor() {
        if (webView == null) {
            return;
        }
        final String script = "(function(){"
                + "if(window.pipePipeLinkHookInstalled)return;"
                + "window.pipePipeLinkHookInstalled=true;"
                + "document.addEventListener('click',function(event){"
                + "var node=event.target.closest&&event.target.closest('a');"
                + "if(!node||!node.href)return;"
                + "var href=node.href;"
                + "var video=href.indexOf('/watch?')!==-1||href.indexOf('/shorts/')!==-1"
                + "||href.indexOf('youtu.be/')!==-1;"
                + "var playlist=href.indexOf('/playlist?')!==-1;"
                + "if(video||playlist){event.preventDefault();event.stopPropagation();"
                + "window.PipePlayWebView.openNative(href,video);}},true);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void showFullscreenView(final View view,
                                    final WebChromeClient.CustomViewCallback callback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenView = view;
        fullscreenCallback = callback;
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void hideFullscreenView() {
        if (fullscreenView == null) {
            return;
        }
        fullscreenContainer.removeView(fullscreenView);
        fullscreenContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        fullscreenView = null;
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.youtube_webview_title));
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }


    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_youtube_webview, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_refresh) {
            if (webView != null) {
                webView.reload();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onDestroyView() {
        hideFullscreenView();
        if (webView != null) {
            final ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.stopLoading();
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        fullscreenContainer = null;
        super.onDestroyView();
    }

    @Override
    public boolean onBackPressed() {
        if (fullscreenView != null) {
            hideFullscreenView();
            return true;
        }
        return false;
    }

    private final class WebBridge {
        @JavascriptInterface
        public void openNative(final String url, final boolean video) {
            final FragmentActivity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                try {
                    if (webView != null) {
                        webView.evaluateJavascript("(function(){var v=document.querySelector('video');"
                                + "if(v){v.pause();}})();", null);
                    }
                    final String finalUrl = canonicalWatchUrl(url);
                    final StreamingService service = NewPipe.getServiceByUrl(finalUrl);
                    if (video) {
                        NavigationHelper.openVideoDetailFragment(requireContext(),
                                activity.getSupportFragmentManager(),
                                service.getServiceId(), finalUrl, "", null, false);
                    } else {
                        NavigationHelper.openPlaylistFragment(activity.getSupportFragmentManager(),
                                service.getServiceId(), finalUrl, "");
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Native navigation failed, staying in WebView: " + url, e);
                    if (webView != null) {
                        webView.loadUrl(url);
                    }
                }
            });
        }
    }
}
