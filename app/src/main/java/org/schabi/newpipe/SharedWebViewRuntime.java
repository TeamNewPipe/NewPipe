package org.schabi.newpipe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single headless WebView used by local JavaScript services.
 *
 * <p>The runtime loads one blank first-party page, keeps it alive for the app process, and exposes
 * a stable JavaScript bridge. Callers keep their own JS namespaces and serialize their public entry
 * points on the caller side.</p>
 */
public final class SharedWebViewRuntime {
    public interface InitializationFailureCallback {
        void onInitializationFailure(@NonNull Throwable throwable);
    }

    public interface SabrLocalDomCallbacks {
        void onJsInitializationError(@NonNull String error);

        void onRunBotguardResult(@NonNull String botguardResponse);

        void onMinterReady();

        void onObtainPoTokenResult(@NonNull String identifier, @NonNull String poTokenU8);

        void onObtainPoTokenError(@NonNull String identifier, @NonNull String error);
    }

    public static final String BRIDGE_NAME = "PipePlayWebViewBridge";

    private static final String TAG = "SharedWebViewRuntime";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    private static final long READY_CALLBACK_ATTEMPT_TIMEOUT_MS = 5_000L;
    private static final int MAX_READY_CALLBACK_ATTEMPTS = 2;
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3";
    private static volatile SharedWebViewRuntime instance;

    private final Context appContext;
    private final Context webViewContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object initLock = new Object();
    private final ConcurrentHashMap<String, SabrLocalDomCallbacks> sabrLocalDomCallbacks =
            new ConcurrentHashMap<>();

    @Nullable
    private CountDownLatch initLatch;
    @Nullable
    private AtomicReference<Throwable> initError;
    @Nullable
    private InitializationAttempt activeInitializationAttempt;
    private long nextInitializationAttemptId;
    @Nullable
    private InitializationFailureCallback initializationFailureCallback;
    @Nullable
    private WebView webView;
    private volatile boolean ready;

    private SharedWebViewRuntime(final Context context) {
        appContext = context.getApplicationContext();
        final Configuration configuration =
                new Configuration(appContext.getResources().getConfiguration());
        webViewContext = appContext.createConfigurationContext(configuration);
    }

    @NonNull
    public static SharedWebViewRuntime get(final Context context) {
        SharedWebViewRuntime runtime = instance;
        if (runtime == null) {
            synchronized (SharedWebViewRuntime.class) {
                runtime = instance;
                if (runtime == null) {
                    runtime = new SharedWebViewRuntime(context);
                    instance = runtime;
                }
            }
        }
        return runtime;
    }

    public static void warmUp(final Context context) {
        get(context).warmUp();
    }

    public void warmUp() {
        warmUp((InitializationFailureCallback) null);
    }

    public void warmUp(@Nullable final InitializationFailureCallback failureCallback) {
        final Throwable existingFailure;
        synchronized (initLock) {
            if (failureCallback != null) {
                initializationFailureCallback = failureCallback;
            }
            if (ready || initLatch != null) {
                existingFailure = initError == null ? null : initError.get();
                if (existingFailure == null) {
                    return;
                }
            } else {
                startInitializationLocked();
                return;
            }
        }
        if (failureCallback != null) {
            failureCallback.onInitializationFailure(existingFailure);
        }
    }

    public void ensureReady(final long timeoutMs, @NonNull final String operation)
            throws Exception {
        final CountDownLatch latch;
        final AtomicReference<Throwable> error;
        synchronized (initLock) {
            if (ready) {
                return;
            }
            if (initLatch == null) {
                startInitializationLocked();
            }
            latch = initLatch;
            error = initError;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(operation + " cannot wait on the main thread");
        }
        if (latch == null || error == null) {
            throw new IllegalStateException(operation + " did not start WebView initialization");
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(operation + " timed out waiting for WebView runtime");
        }
        final Throwable failure = error.get();
        if (failure != null) {
            throw new IllegalStateException(operation + " failed to initialize WebView runtime",
                    failure);
        }
    }

    @NonNull
    public String evaluateJavascriptBlocking(@NonNull final String script,
                                             final long timeoutMs,
                                             @NonNull final String operation) throws Exception {
        ensureReady(timeoutMs, operation);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(operation + " cannot wait on the main thread");
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        if (!mainHandler.post(() -> {
            try {
                final WebView view = webView;
                if (view == null) {
                    throw new IllegalStateException("WebView runtime is not initialized");
                }
                view.evaluateJavascript(script, value -> {
                    result.set(value);
                    latch.countDown();
                });
            } catch (final Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        })) {
            throw new IllegalStateException(operation + " could not post JavaScript evaluation");
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(operation + " timed out");
        }
        final Throwable failure = error.get();
        if (failure != null) {
            throw new IllegalStateException(operation + " failed", failure);
        }
        return result.get();
    }

    public boolean evaluateJavascript(@NonNull final String script,
                                      @Nullable final ValueCallback<String> callback,
                                      @Nullable final ValueCallback<Throwable> errorCallback) {
        try {
            ensureReady(DEFAULT_TIMEOUT_MS, "async JavaScript evaluation");
        } catch (final Throwable throwable) {
            if (errorCallback != null) {
                errorCallback.onReceiveValue(throwable);
            }
            return false;
        }
        return mainHandler.post(() -> {
            try {
                final WebView view = webView;
                if (view == null) {
                    throw new IllegalStateException("WebView runtime is not initialized");
                }
                view.evaluateJavascript(script, callback);
            } catch (final Throwable throwable) {
                if (errorCallback != null) {
                    errorCallback.onReceiveValue(throwable);
                }
            }
        });
    }

    @NonNull
    public String loadAsset(@NonNull final String path) {
        try (InputStream in = appContext.getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (final Exception e) {
            throw new IllegalStateException("Could not load asset " + path, e);
        }
    }

    @NonNull
    public String registerSabrLocalDomCallbacks(@NonNull final SabrLocalDomCallbacks callbacks) {
        final String id = UUID.randomUUID().toString();
        sabrLocalDomCallbacks.put(id, callbacks);
        return id;
    }

    public void unregisterSabrLocalDomCallbacks(@NonNull final String id) {
        sabrLocalDomCallbacks.remove(id);
    }

    private void startInitializationLocked() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final InitializationAttempt attempt = new InitializationAttempt(
                ++nextInitializationAttemptId, 1, latch, error);
        initLatch = latch;
        initError = error;
        activeInitializationAttempt = attempt;
        if (!mainHandler.post(() -> createWebView(attempt))) {
            final IllegalStateException exception =
                    new IllegalStateException("Could not post WebView creation");
            activeInitializationAttempt = null;
            error.set(exception);
            latch.countDown();
            notifyInitializationFailure(exception);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView(@NonNull final InitializationAttempt attempt) {
        if (!isActiveInitializationAttempt(attempt)) {
            return;
        }
        try {
            Log.i(TAG, "creating WebView attempt=" + attempt.number + " elapsedMs="
                    + attempt.elapsedMs());
            final WebView view = new WebView(webViewContext);
            attempt.view = view;
            Log.i(TAG, "created WebView attempt=" + attempt.number + " elapsedMs="
                    + attempt.elapsedMs());
            if (!isActiveInitializationAttempt(attempt)) {
                destroyWebView(view);
                return;
            }
            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
            final WebSettings settings = view.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setUserAgentString(USER_AGENT);
            settings.setBlockNetworkLoads(true);
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(settings, false);
            }
            view.addJavascriptInterface(new Bridge(), BRIDGE_NAME);
            view.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(final ConsoleMessage message) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "console " + message.messageLevel() + ' '
                                + message.message() + " @" + message.sourceId()
                                + ':' + message.lineNumber());
                    }
                    return true;
                }
            });
            view.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(final WebView view, final String url) {
                    // WebView 44/83 occasionally misses this callback for a headless local page.
                    // Readiness is therefore determined only by the local document's bridge call.
                    Log.i(TAG, "page finished url=" + url + " attempt=" + attempt.number
                            + " elapsedMs=" + attempt.elapsedMs());
                }

                @Override
                public void onReceivedError(final WebView view, final WebResourceRequest request,
                                            final WebResourceError webError) {
                    super.onReceivedError(view, request, webError);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame()) {
                        retryOrFail(attempt, new IllegalStateException(
                                "WebView runtime main frame error " + webError.getErrorCode()
                                        + ": " + webError.getDescription()));
                    }
                }
            });
            view.loadDataWithBaseURL("https://www.youtube.com/",
                    runtimeDocument(attempt.id),
                    "text/html", "UTF-8", null);
            Log.i(TAG, "load dispatched attempt=" + attempt.number + " elapsedMs="
                    + attempt.elapsedMs());
            mainHandler.postDelayed(() -> retryOrFail(attempt, new IllegalStateException(
                    "WebView runtime ready callback timed out after "
                            + READY_CALLBACK_ATTEMPT_TIMEOUT_MS + " ms")),
                    READY_CALLBACK_ATTEMPT_TIMEOUT_MS);
        } catch (final Throwable throwable) {
            retryOrFail(attempt, throwable);
        }
    }

    @NonNull
    private static String runtimeDocument(final long attemptId) {
        return "<!doctype html><html><head><script>"
                + "PipePlayWebViewBridge.onRuntimeDocumentReady('" + attemptId + "');"
                + "</script><title></title></head><body></body></html>";
    }

    private void completeInitialization(@NonNull final InitializationAttempt attempt) {
        if (!attempt.completed.compareAndSet(false, true)) {
            return;
        }
        final boolean stale;
        synchronized (initLock) {
            stale = activeInitializationAttempt != attempt || ready;
            if (!stale) {
                webView = attempt.view;
                ready = true;
                activeInitializationAttempt = null;
            }
        }
        if (stale) {
            destroyWebView(attempt.view);
            return;
        }
        Log.i(TAG, "ready source=bridge attempt=" + attempt.number + " elapsedMs="
                + attempt.elapsedMs() + " mainThread="
                + (Looper.myLooper() == Looper.getMainLooper()));
        attempt.latch.countDown();
    }

    private void retryOrFail(@NonNull final InitializationAttempt attempt,
                             @NonNull final Throwable throwable) {
        if (!attempt.completed.compareAndSet(false, true)) {
            return;
        }
        if (!isActiveInitializationAttempt(attempt)) {
            destroyWebView(attempt.view);
            return;
        }
        destroyWebView(attempt.view);
        if (attempt.number < MAX_READY_CALLBACK_ATTEMPTS) {
            final InitializationAttempt retry;
            synchronized (initLock) {
                if (activeInitializationAttempt != attempt || ready) {
                    return;
                }
                retry = new InitializationAttempt(++nextInitializationAttemptId,
                        attempt.number + 1, attempt.latch, attempt.error);
                activeInitializationAttempt = retry;
            }
            Log.w(TAG, "retrying WebView runtime ready callback after attempt " + attempt.number
                    + " elapsedMs=" + attempt.elapsedMs(), throwable);
            if (!mainHandler.post(() -> createWebView(retry))) {
                retryOrFail(retry, new IllegalStateException("Could not post WebView retry"));
            }
            return;
        }
        synchronized (initLock) {
            if (activeInitializationAttempt != attempt || ready) {
                return;
            }
            activeInitializationAttempt = null;
            attempt.error.compareAndSet(null, throwable);
        }
        Log.e(TAG, "WebView runtime ready callback failed attempt=" + attempt.number
                + " elapsedMs=" + attempt.elapsedMs(), throwable);
        attempt.latch.countDown();
        notifyInitializationFailure(throwable);
    }

    private boolean isActiveInitializationAttempt(@NonNull final InitializationAttempt attempt) {
        synchronized (initLock) {
            return activeInitializationAttempt == attempt && initLatch == attempt.latch
                    && initError == attempt.error && !ready;
        }
    }

    private static void destroyWebView(@Nullable final WebView view) {
        if (view == null) {
            return;
        }
        try {
            view.stopLoading();
            view.destroy();
        } catch (final Throwable throwable) {
            Log.w(TAG, "Could not destroy failed WebView initialization attempt", throwable);
        }
    }

    private void notifyInitializationFailure(@NonNull final Throwable throwable) {
        final InitializationFailureCallback callback;
        synchronized (initLock) {
            callback = initializationFailureCallback;
        }
        if (callback != null) {
            callback.onInitializationFailure(throwable);
        }
    }

    private static final class InitializationAttempt {
        private final long id;
        private final int number;
        private final CountDownLatch latch;
        private final AtomicReference<Throwable> error;
        private final AtomicBoolean completed = new AtomicBoolean();
        private final long startedAtMs = SystemClock.elapsedRealtime();
        @Nullable
        private WebView view;

        InitializationAttempt(final long id, final int number, @NonNull final CountDownLatch latch,
                              @NonNull final AtomicReference<Throwable> error) {
            this.id = id;
            this.number = number;
            this.latch = latch;
            this.error = error;
        }

        private long elapsedMs() {
            return SystemClock.elapsedRealtime() - startedAtMs;
        }
    }

    private final class Bridge {
        @JavascriptInterface
        public void onRuntimeDocumentReady(final String attemptId) {
            mainHandler.post(() -> {
                final InitializationAttempt attempt;
                synchronized (initLock) {
                    attempt = activeInitializationAttempt;
                    if (attempt == null || !Long.toString(attempt.id).equals(attemptId)) {
                        return;
                    }
                }
                completeInitialization(attempt);
            });
        }

        @JavascriptInterface
        public void onSabrLocalDomJsInitializationError(final String sessionId,
                                                        final String error) {
            final SabrLocalDomCallbacks callbacks = sabrLocalDomCallbacks.get(sessionId);
            if (callbacks != null) {
                callbacks.onJsInitializationError(error == null ? "" : error);
            }
        }

        @JavascriptInterface
        public void onSabrLocalDomRunBotguardResult(final String sessionId,
                                                    final String botguardResponse) {
            final SabrLocalDomCallbacks callbacks = sabrLocalDomCallbacks.get(sessionId);
            if (callbacks != null) {
                callbacks.onRunBotguardResult(botguardResponse == null ? "" : botguardResponse);
            }
        }

        @JavascriptInterface
        public void onSabrLocalDomMinterReady(final String sessionId) {
            final SabrLocalDomCallbacks callbacks = sabrLocalDomCallbacks.get(sessionId);
            if (callbacks != null) {
                callbacks.onMinterReady();
            }
        }

        @JavascriptInterface
        public void onSabrLocalDomObtainPoTokenResult(final String sessionId,
                                                      final String identifier,
                                                      final String poTokenU8) {
            final SabrLocalDomCallbacks callbacks = sabrLocalDomCallbacks.get(sessionId);
            if (callbacks != null) {
                callbacks.onObtainPoTokenResult(identifier == null ? "" : identifier,
                        poTokenU8 == null ? "" : poTokenU8);
            }
        }

        @JavascriptInterface
        public void onSabrLocalDomObtainPoTokenError(final String sessionId,
                                                     final String identifier,
                                                     final String error) {
            final SabrLocalDomCallbacks callbacks = sabrLocalDomCallbacks.get(sessionId);
            if (callbacks != null) {
                callbacks.onObtainPoTokenError(identifier == null ? "" : identifier,
                        error == null ? "" : error);
            }
        }
    }
}
