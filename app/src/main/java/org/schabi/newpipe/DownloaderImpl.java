package org.schabi.newpipe;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.WWW_REFERER;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;
import org.schabi.newpipe.network.InvidiousInterceptor;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.plugin.PluginDownloaderAdapters;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.util.InfoCache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.brotli.BrotliInterceptor;

public final class DownloaderImpl extends Downloader {
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE_KEY =
            "youtube_restricted_mode_key";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000";
    public static final String YOUTUBE_DOMAIN = "youtube.com";
    static final int PARSED_URL_CACHE_SIZE = 64;

    private static final String YOUTUBE_INNERTUBE_URL =
            "https://www.youtube.com/youtubei/v1/";
    private static final String[] COMMON_YOUTUBE_API_URLS = {
            YOUTUBE_INNERTUBE_URL + "browse?prettyPrint=false",
            YOUTUBE_INNERTUBE_URL + "next?prettyPrint=false",
            YOUTUBE_INNERTUBE_URL + "player?prettyPrint=false",
            YOUTUBE_INNERTUBE_URL + "search?prettyPrint=false"
    };

    private static DownloaderImpl instance;
    private final Map<String, String> mCookies;
    private final Map<String, HttpUrl> parsedUrls = new LinkedHashMap<>(
            PARSED_URL_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, HttpUrl> eldest) {
            return size() > PARSED_URL_CACHE_SIZE;
        }
    };
    private final OkHttpClient client;

    private DownloaderImpl(final OkHttpClient.Builder builder) {
        for (final String url : COMMON_YOUTUBE_API_URLS) {
            parseUrl(url);
        }
        this.client = builder
                .followRedirects(!InvidiousBackend.isEnabled())
                .followSslRedirects(!InvidiousBackend.isEnabled())
                .addInterceptor(new InvidiousInterceptor())
                .addInterceptor(new YoutubeRequestTimingInterceptor())
                .addInterceptor(BrotliInterceptor.INSTANCE)
                .addInterceptor(chain -> {
                    final okhttp3.Request originalRequest = chain.request();
                    if (originalRequest.header("User-Agent") != null) {
                        return chain.proceed(originalRequest);
                    }

                    return chain.proceed(originalRequest.newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .build());
                })
                // Network interceptors run again for every redirect. Select stored cookies
                // using the actual destination, without carrying them onto another host.
                .addNetworkInterceptor(chain -> {
                    final okhttp3.Request request = chain.request();
                    InvidiousBackend.checkRequest(request.url().uri());
                    final String cookies = getCookies(request.url().toString());
                    if (request.header("Cookie") != null || cookies.isEmpty()) {
                        return chain.proceed(request);
                    }
                    return chain.proceed(request.newBuilder().header("Cookie", cookies).build());
                })
                .addNetworkInterceptor(new org.schabi.newpipe.network.VimeoApiRedirectInterceptor())
                .readTimeout(30, TimeUnit.SECONDS)
//                .cache(new Cache(new File(context.getExternalCacheDir(), "okhttp"),
//                        16 * 1024 * 1024))
                .build();
        this.mCookies = new HashMap<>();
    }

    @NonNull
    public OkHttpClient getClient() {
        return client;
    }

    /**
     * It's recommended to call exactly once in the entire lifetime of the application.
     *
     * @param builder if null, default builder will be used
     * @return a new instance of {@link DownloaderImpl}
     */
    public static DownloaderImpl init(@Nullable final OkHttpClient.Builder builder) {
        instance = new DownloaderImpl(
                builder != null ? builder : new OkHttpClient.Builder());
        return instance;
    }

    public static DownloaderImpl getInstance() {
        return instance;
    }

    public String getCookies(final String url) {
        final HttpUrl destination;
        try {
            destination = HttpUrl.get(url);
        } catch (final IllegalArgumentException error) {
            return "";
        }
        final String host = destination.host();
        if (!destination.isHttps()
                || !(host.equals(YOUTUBE_DOMAIN) || host.endsWith("." + YOUTUBE_DOMAIN))) {
            return "";
        }
        return Stream.of(getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY),
                getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY))
                .filter(Objects::nonNull)
                .flatMap(cookies -> Arrays.stream(cookies.split("; *")))
                .distinct()
                .collect(Collectors.joining("; "));
    }

    public String getCookie(final String key) {
        return mCookies.get(key);
    }

    public void setCookie(final String key, final String cookie) {
        mCookies.put(key, cookie);
    }

    public void removeCookie(final String key) {
        mCookies.remove(key);
    }

    public void updateYoutubeRestrictedModeCookies(final Context context) {
        final String restrictedModeEnabledKey =
                context.getString(R.string.youtube_restricted_mode_enabled);
        final boolean restrictedModeEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(restrictedModeEnabledKey, false);
        updateYoutubeRestrictedModeCookies(restrictedModeEnabled);
    }

    public void updateYoutubeRestrictedModeCookies(final boolean youtubeRestrictedModeEnabled) {
        if (youtubeRestrictedModeEnabled) {
            setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY,
                    YOUTUBE_RESTRICTED_MODE_COOKIE);
        } else {
            removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY);
        }
        InfoCache.getInstance().clearCache();
    }

    /**
     * Get the size of the content that the url is pointing by firing a HEAD request.
     *
     * @param url an url pointing to the content
     * @return the size of the content, in bytes
     */
    public long getContentLength(final String url) throws IOException {
        try {
            final Map<String, List<String>> headers = BilibiliService.isBiliBiliDownloadUrl(url)
                    ? BilibiliService.getUserAgentHeaders(WWW_REFERER) : null;
            final Response response = head(url, headers);
            return Long.parseLong(response.getHeader("Content-Length"));
        } catch (final NumberFormatException e) {
            throw new IOException("Invalid content length", e);
        } catch (final ReCaptchaException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Response execute(@NonNull final Request request)
            throws IOException, ReCaptchaException {
        final okhttp3.Request okHttpRequest = buildRequest(request);
        try (okhttp3.Response response = client.newCall(okHttpRequest).execute()) {
            return buildExtractorResponse(response, request.url());
        }
    }

    @Override
    public StreamingResponse getStreaming(final String url,
                                           @Nullable final Map<String, List<String>> headers,
                                           @Nullable final Localization localization)
            throws IOException, ReCaptchaException {
        return executeStreaming(Request.newBuilder().get(url).headers(headers)
                .localization(localization).build(), client);
    }

    @Override
    public StreamingResponse getStreaming(final String url,
                                           @Nullable final Map<String, List<String>> headers,
                                           @Nullable final Localization localization,
                                           final long timeoutMs)
            throws IOException, ReCaptchaException {
        final long boundedTimeoutMs = Math.max(1, timeoutMs);
        final OkHttpClient requestClient = client.newBuilder()
                .callTimeout(boundedTimeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(boundedTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(boundedTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        return executeStreaming(Request.newBuilder().get(url).headers(headers)
                .localization(localization).build(), requestClient);
    }

    @Override
    public StreamingResponse postStreaming(final String url,
                                            @Nullable final Map<String, List<String>> headers,
                                            @Nullable final byte[] dataToSend,
                                            @Nullable final Localization localization)
            throws IOException, ReCaptchaException {
        return executeStreaming(Request.newBuilder().post(url, dataToSend).headers(headers)
                .localization(localization).build(), client);
    }

    /**
     * Shares the normal request/cookie policy while leaving body consumption to the caller.
     *
     * @param request the request to execute
     * @param requestClient the client supplying timeouts and connection policy
     * @return a response whose stream must be closed by the caller
     */
    private StreamingResponse executeStreaming(@NonNull final Request request,
                                                @NonNull final OkHttpClient requestClient)
            throws IOException, ReCaptchaException {
        final okhttp3.Response response = requestClient.newCall(buildRequest(request)).execute();
        try {
            checkResponseCode(response, request.url());
        } catch (final ReCaptchaException error) {
            response.close();
            throw error;
        }
        final ResponseBody body = response.body();
        // Closing the returned stream closes OkHttp's body/source and releases its connection.
        return new StreamingResponse(response.code(), response.headers().toMultimap(),
                body == null ? new ByteArrayInputStream(new byte[0]) : body.byteStream());
    }

    @Override
    public CancellableCall executeAsync(@NonNull final Request request,
                                        final AsyncCallback input)
            throws IOException, ReCaptchaException {
        final AsyncCallback callback = PluginDownloaderAdapters.asyncCallback(input);
        final Call call = client.newCall(buildRequest(request));
        final CancellableCall cancellableCall = new CancellableCall(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull final Call call, @NonNull final IOException e) {
                try {
                    callback.onError(e);
                } finally {
                    cancellableCall.setFinished();
                }
            }

            @Override
            public void onResponse(@NonNull final Call call,
                                   @NonNull final okhttp3.Response response) {
                try (response) {
                    final Response extractorResponse = buildExtractorResponse(
                            response, request.url());
                    callback.onSuccess(extractorResponse);
                } catch (final IOException | ExtractionException e) {
                    callback.onError(e);
                } finally {
                    cancellableCall.setFinished();
                }
            }
        });
        return cancellableCall;
    }

    @NonNull
    private okhttp3.Request buildRequest(@NonNull final Request request) {
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        final RequestBody requestBody = buildRequestBody(httpMethod, dataToSend);

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(parseUrl(url))
                .addHeader("User-Agent", USER_AGENT);

        headers.forEach((headerName, headerValueList) -> {
            requestBuilder.removeHeader(headerName);
            headerValueList.forEach(headerValue ->
                    requestBuilder.addHeader(headerName, headerValue));
        });
        return requestBuilder.build();
    }

    /**
     * Reuses parsed URLs so repeated extractor requests do not repeatedly enter OkHttp's hostname
     * canonicalizer. Apart from avoiding duplicate work, this works around an Android Runtime JIT
     * crash observed on some Android 16 custom-ROM builds while that Kotlin method becomes hot.
     *
     * @param url the absolute request URL
     * @return the cached or newly parsed OkHttp URL
     */
    @NonNull
    HttpUrl parseUrl(@NonNull final String url) {
        synchronized (parsedUrls) {
            final HttpUrl cachedUrl = parsedUrls.get(url);
            if (cachedUrl != null) {
                return cachedUrl;
            }

            final HttpUrl parsedUrl = HttpUrl.get(url);
            parsedUrls.put(url, parsedUrl);
            return parsedUrl;
        }
    }

    @Nullable
    static RequestBody buildRequestBody(@NonNull final String httpMethod,
                                        @Nullable final byte[] dataToSend) {
        if (dataToSend != null) {
            return RequestBody.create(dataToSend);
        }

        // OkHttp requires POST requests to have a body, while the extractor downloader contract
        // permits callers to represent an empty body with null.
        return "POST".equals(httpMethod) ? RequestBody.create(new byte[0]) : null;
    }

    @NonNull
    private static Response buildExtractorResponse(@NonNull final okhttp3.Response response,
                                                   @NonNull final String originalUrl)
            throws IOException, ReCaptchaException {
        checkResponseCode(response, originalUrl);

        byte[] responseBodyBytes = new byte[0];
        String responseBodyToReturn = null;
        try (ResponseBody body = response.body()) {
            if (body != null) {
                responseBodyBytes = body.bytes();
                responseBodyToReturn = new String(responseBodyBytes, responseCharset(body));
            }
        }

        final String latestUrl = response.request().url().toString();
        return new Response(
                response.code(),
                response.message(),
                response.headers().toMultimap(),
                responseBodyToReturn,
                responseBodyBytes,
                latestUrl);
    }

    private static void checkResponseCode(@NonNull final okhttp3.Response response,
                                           @NonNull final String originalUrl)
            throws ReCaptchaException {
        if (response.code() == 429) {
            throw new ReCaptchaException("reCaptcha Challenge requested", originalUrl);
        }
    }

    @NonNull
    private static Charset responseCharset(@NonNull final ResponseBody body) {
        final MediaType contentType = body.contentType();
        return contentType == null
                ? StandardCharsets.UTF_8 : contentType.charset(StandardCharsets.UTF_8);
    }

}
