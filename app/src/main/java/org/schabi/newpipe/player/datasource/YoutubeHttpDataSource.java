/*
 * Based on ExoPlayer's DefaultHttpDataSource, version 2.18.1.
 *
 * Original source code copyright (C) 2016 The Android Open Source Project, licensed under the
 * Apache License, Version 2.0.
 */

package org.schabi.newpipe.player.datasource;

import static com.google.android.exoplayer2.upstream.DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS;
import static com.google.android.exoplayer2.upstream.DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS;
import static com.google.android.exoplayer2.upstream.HttpUtil.buildRangeRequestHeader;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getAndroidUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getIosUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTvHtml5UserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isAndroidStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isIosStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isTvHtml5StreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isWebStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isWebEmbeddedPlayerStreamingUrl;
import static java.lang.Math.min;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DataSpec.HttpMethod;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpUtil;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Predicate;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;

import org.schabi.newpipe.DownloaderImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * An {@link HttpDataSource} that uses Android's {@link HttpURLConnection}, based on
 * {@link com.google.android.exoplayer2.upstream.DefaultHttpDataSource}, for YouTube streams.
 *
 * <p>
 * It adds more headers to {@code videoplayback} URLs, such as {@code Origin}, {@code Referer}
 * (only where it's relevant) and also more parameters, such as {@code rn} and replaces the use of
 * the {@code Range} header by the corresponding parameter ({@code range}), if enabled.
 * </p>
 *
 * There are many unused methods in this class because everything was copied from {@link
 * com.google.android.exoplayer2.upstream.DefaultHttpDataSource} with as little changes as possible.
 * SonarQube warnings were also suppressed for the same reason.
 */
@SuppressWarnings({"squid:S3011", "squid:S4738"})
public final class YoutubeHttpDataSource extends BaseDataSource implements HttpDataSource {

    /**
     * {@link DataSource.Factory} for {@link YoutubeHttpDataSource} instances.
     */
    public static final class Factory implements HttpDataSource.Factory {

        private final RequestProperties defaultRequestProperties;

        @Nullable
        private TransferListener transferListener;
        @Nullable
        private Predicate<String> contentTypePredicate;
        private int connectTimeoutMs;
        private int readTimeoutMs;
        private boolean allowCrossProtocolRedirects;
        private boolean keepPostFor302Redirects;

        private boolean rangeParameterEnabled;
        private boolean rnParameterEnabled;

        /**
         * Creates an instance.
         */
        public Factory() {
            defaultRequestProperties = new RequestProperties();
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS;
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS;
        }

        @NonNull
        @Override
        public Factory setDefaultRequestProperties(
                @NonNull final Map<String, String> defaultRequestPropertiesMap) {
            defaultRequestProperties.clearAndSet(defaultRequestPropertiesMap);
            return this;
        }

        /**
         * Sets the connect timeout, in milliseconds.
         *
         * <p>
         * The default is {@link DefaultHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS}.
         * </p>
         *
         * @param connectTimeoutMsValue The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        public Factory setConnectTimeoutMs(final int connectTimeoutMsValue) {
            connectTimeoutMs = connectTimeoutMsValue;
            return this;
        }

        /**
         * Sets the read timeout, in milliseconds.
         *
         * <p>The default is {@link DefaultHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS}.
         *
         * @param readTimeoutMsValue The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        public Factory setReadTimeoutMs(final int readTimeoutMsValue) {
            readTimeoutMs = readTimeoutMsValue;
            return this;
        }

        /**
         * Sets whether to allow cross protocol redirects.
         *
         * <p>The default is {@code false}.
         *
         * @param allowCrossProtocolRedirectsValue Whether to allow cross protocol redirects.
         * @return This factory.
         */
        public Factory setAllowCrossProtocolRedirects(
                final boolean allowCrossProtocolRedirectsValue) {
            allowCrossProtocolRedirects = allowCrossProtocolRedirectsValue;
            return this;
        }

        /**
         * Sets whether the use of the {@code range} parameter instead of the {@code Range} header
         * to request ranges of streams is enabled.
         *
         * <p>
         * Note that it must be not enabled on streams which are using a {@link
         * com.google.android.exoplayer2.source.ProgressiveMediaSource}, as it will break playback
         * for them (some exceptions may be thrown).
         * </p>
         *
         * @param rangeParameterEnabledValue whether the use of the {@code range} parameter instead
         *                                   of the {@code Range} header (must be only enabled when
         *                                   non-{@code ProgressiveMediaSource}s)
         * @return This factory.
         */
        public Factory setRangeParameterEnabled(final boolean rangeParameterEnabledValue) {
            rangeParameterEnabled = rangeParameterEnabledValue;
            return this;
        }

        /**
         * Sets whether the use of the {@code rn}, which stands for request number, parameter is
         * enabled.
         *
         * <p>
         * Note that it should be not enabled on streams which are using {@code /} to delimit URLs
         * parameters, such as the streams of HLS manifests.
         * </p>
         *
         * @param rnParameterEnabledValue whether the appending the {@code rn} parameter to
         *                                {@code videoplayback} URLs
         * @return This factory.
         */
        public Factory setRnParameterEnabled(final boolean rnParameterEnabledValue) {
            rnParameterEnabled = rnParameterEnabledValue;
            return this;
        }

        /**
         * Sets a content type {@link Predicate}. If a content type is rejected by the predicate
         * then a {@link HttpDataSource.InvalidContentTypeException} is thrown from
         * {@link YoutubeHttpDataSource#open(DataSpec)}.
         *
         * <p>
         * The default is {@code null}.
         * </p>
         *
         * @param contentTypePredicateToSet The content type {@link Predicate}, or {@code null} to
         *                                  clear a predicate that was previously set.
         * @return This factory.
         */
        public Factory setContentTypePredicate(
                @Nullable final Predicate<String> contentTypePredicateToSet) {
            this.contentTypePredicate = contentTypePredicateToSet;
            return this;
        }

        /**
         * Sets the {@link TransferListener} that will be used.
         *
         * <p>The default is {@code null}.
         *
         * <p>See {@link DataSource#addTransferListener(TransferListener)}.
         *
         * @param transferListenerToUse The listener that will be used.
         * @return This factory.
         */
        public Factory setTransferListener(
                @Nullable final TransferListener transferListenerToUse) {
            this.transferListener = transferListenerToUse;
            return this;
        }

        /**
         * Sets whether we should keep the POST method and body when we have HTTP 302 redirects for
         * a POST request.
         *
         * @param keepPostFor302RedirectsValue Whether we should keep the POST method and body when
         *                                     we have HTTP 302 redirects for a POST request.
         * @return This factory.
         */
        public Factory setKeepPostFor302Redirects(final boolean keepPostFor302RedirectsValue) {
            this.keepPostFor302Redirects = keepPostFor302RedirectsValue;
            return this;
        }

        @NonNull
        @Override
        public YoutubeHttpDataSource createDataSource() {
            final YoutubeHttpDataSource dataSource = new YoutubeHttpDataSource(
                    connectTimeoutMs,
                    readTimeoutMs,
                    allowCrossProtocolRedirects,
                    rangeParameterEnabled,
                    rnParameterEnabled,
                    defaultRequestProperties,
                    contentTypePredicate,
                    keepPostFor302Redirects);
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return dataSource;
        }
    }

    private static final String TAG = YoutubeHttpDataSource.class.getSimpleName();
    private static final int MAX_REDIRECTS = 20; // Same limit as okhttp.
    private static final int HTTP_STATUS_TEMPORARY_REDIRECT = 307;
    private static final int HTTP_STATUS_PERMANENT_REDIRECT = 308;
    private static final long MAX_BYTES_TO_DRAIN = 2048;

    private static final String RN_PARAMETER = "&rn=";
    private static final String YOUTUBE_BASE_URL = "https://www.youtube.com";
    private static final byte[] POST_BODY = new byte[] {0x78, 0};

    private final boolean allowCrossProtocolRedirects;
    private final boolean rangeParameterEnabled;
    private final boolean rnParameterEnabled;

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    @Nullable
    private final RequestProperties defaultRequestProperties;
    private final RequestProperties requestProperties;
    private final boolean keepPostFor302Redirects;

    @Nullable
    private final Predicate<String> contentTypePredicate;
    @Nullable
    private DataSpec dataSpec;
    @Nullable
    private HttpURLConnection connection;
    @Nullable
    private InputStream inputStream;
    private boolean opened;
    private int responseCode;
    private long bytesToRead;
    private long bytesRead;

    private long requestNumber;

    @SuppressWarnings("checkstyle:ParameterNumber")
    private YoutubeHttpDataSource(final int connectTimeoutMillis,
                                  final int readTimeoutMillis,
                                  final boolean allowCrossProtocolRedirects,
                                  final boolean rangeParameterEnabled,
                                  final boolean rnParameterEnabled,
                                  @Nullable final RequestProperties defaultRequestProperties,
                                  @Nullable final Predicate<String> contentTypePredicate,
                                  final boolean keepPostFor302Redirects) {
        super(true);
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
        this.rangeParameterEnabled = rangeParameterEnabled;
        this.rnParameterEnabled = rnParameterEnabled;
        this.defaultRequestProperties = defaultRequestProperties;
        this.contentTypePredicate = contentTypePredicate;
        this.requestProperties = new RequestProperties();
        this.keepPostFor302Redirects = keepPostFor302Redirects;
        this.requestNumber = 0;
    }

    @Override
    @Nullable
    public Uri getUri() {
        return connection == null ? null : Uri.parse(connection.getURL().toString());
    }

    @Override
    public int getResponseCode() {
        return connection == null || responseCode <= 0 ? -1 : responseCode;
    }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        if (connection == null) {
            return ImmutableMap.of();
        }
        // connection.getHeaderFields() always contains a null key with a value like
        // ["HTTP/1.1 200 OK"]. The response code is available from
        // HttpURLConnection#getResponseCode() and the HTTP version is fixed when establishing the
        // connection.
        // DataSource#getResponseHeaders() doesn't allow null keys in the returned map, so we need
        // to remove it.
        // connection.getHeaderFields() returns a special unmodifiable case-insensitive Map
        // so we can't just remove the null key or make a copy without the null key. Instead we
        // wrap it in a ForwardingMap subclass that ignores and filters out null keys in the read
        // methods.
        return new NullFilteringHeadersMap(connection.getHeaderFields());
    }

    @Override
    public void setRequestProperty(@NonNull final String name, @NonNull final String value) {
        checkNotNull(name);
        checkNotNull(value);
        requestProperties.set(name, value);
    }

    @Override
    public void clearRequestProperty(@NonNull final String name) {
        checkNotNull(name);
        requestProperties.remove(name);
    }

    @Override
    public void clearAllRequestProperties() {
        requestProperties.clear();
    }

    /**
     * Opens the source to read the specified data.
     */
    @Override
    public long open(@NonNull final DataSpec dataSpecParameter) throws HttpDataSourceException {
        this.dataSpec = dataSpecParameter;
        bytesRead = 0;
        bytesToRead = 0;
        transferInitializing(dataSpecParameter);

        final HttpURLConnection httpURLConnection;
        final String responseMessage;
        try {
            this.connection = makeConnection(dataSpec);
            httpURLConnection = this.connection;
            responseCode = httpURLConnection.getResponseCode();
            responseMessage = httpURLConnection.getResponseMessage();
        } catch (final IOException e) {
            closeConnectionQuietly();
            throw HttpDataSourceException.createForIOException(e, dataSpec,
                    HttpDataSourceException.TYPE_OPEN);
        }

        // Check for a valid response code.
        if (responseCode < 200 || responseCode > 299) {
            final Map<String, List<String>> headers = httpURLConnection.getHeaderFields();
            if (responseCode == 416) {
                final long documentSize = HttpUtil.getDocumentSize(
                        httpURLConnection.getHeaderField(HttpHeaders.CONTENT_RANGE));
                if (dataSpecParameter.position == documentSize) {
                    opened = true;
                    transferStarted(dataSpecParameter);
                    return dataSpecParameter.length != C.LENGTH_UNSET
                            ? dataSpecParameter.length
                            : 0;
                }
            }

            final InputStream errorStream = httpURLConnection.getErrorStream();
            byte[] errorResponseBody;
            try {
                errorResponseBody = errorStream != null
                        ? Util.toByteArray(errorStream)
                        : Util.EMPTY_BYTE_ARRAY;
            } catch (final IOException e) {
                errorResponseBody = Util.EMPTY_BYTE_ARRAY;
            }

            closeConnectionQuietly();
            final IOException cause = responseCode == 416 ? new DataSourceException(
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
                    : null;
            throw new InvalidResponseCodeException(responseCode, responseMessage, cause, headers,
                    dataSpec, errorResponseBody);
        }

        // Check for a valid content type.
        final String contentType = httpURLConnection.getContentType();
        if (contentTypePredicate != null && !contentTypePredicate.apply(contentType)) {
            closeConnectionQuietly();
            throw new InvalidContentTypeException(contentType, dataSpecParameter);
        }

        final long bytesToSkip;
        if (!rangeParameterEnabled) {
            // If we requested a range starting from a non-zero position and received a 200 rather
            // than a 206, then the server does not support partial requests. We'll need to
            // manually skip to the requested position.
            bytesToSkip = responseCode == 200 && dataSpecParameter.position != 0
                    ? dataSpecParameter.position
                    : 0;
        } else {
            bytesToSkip = 0;
        }


        // Determine the length of the data to be read, after skipping.
        final boolean isCompressed = isCompressed(httpURLConnection);
        if (!isCompressed) {
            if (dataSpecParameter.length != C.LENGTH_UNSET) {
                bytesToRead = dataSpecParameter.length;
            } else {
                final long contentLength = HttpUtil.getContentLength(
                        httpURLConnection.getHeaderField(HttpHeaders.CONTENT_LENGTH),
                        httpURLConnection.getHeaderField(HttpHeaders.CONTENT_RANGE));
                bytesToRead = contentLength != C.LENGTH_UNSET
                        ? (contentLength - bytesToSkip)
                        : C.LENGTH_UNSET;
            }
        } else {
            // Gzip is enabled. If the server opts to use gzip then the content length in the
            // response will be that of the compressed data, which isn't what we want. Always use
            // the dataSpec length in this case.
            bytesToRead = dataSpecParameter.length;
        }

        try {
            inputStream = httpURLConnection.getInputStream();
            if (isCompressed) {
                inputStream = new GZIPInputStream(inputStream);
            }
        } catch (final IOException e) {
            closeConnectionQuietly();
            throw new HttpDataSourceException(e, dataSpec,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    HttpDataSourceException.TYPE_OPEN);
        }

        opened = true;
        transferStarted(dataSpecParameter);

        try {
            skipFully(bytesToSkip, dataSpec);
        } catch (final IOException e) {
            closeConnectionQuietly();
            if (e instanceof HttpDataSourceException) {
                throw (HttpDataSourceException) e;
            }
            throw new HttpDataSourceException(e, dataSpec,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    HttpDataSourceException.TYPE_OPEN);
        }

        return bytesToRead;
    }

    @Override
    public int read(@NonNull final byte[] buffer, final int offset, final int length)
            throws HttpDataSourceException {
        try {
            return readInternal(buffer, offset, length);
        } catch (final IOException e) {
            throw HttpDataSourceException.createForIOException(e, castNonNull(dataSpec),
                    HttpDataSourceException.TYPE_READ);
        }
    }

    @Override
    public void close() throws HttpDataSourceException {
        try {
            final InputStream connectionInputStream = this.inputStream;
            if (connectionInputStream != null) {
                final long bytesRemaining = bytesToRead == C.LENGTH_UNSET
                        ? C.LENGTH_UNSET
                        : bytesToRead - bytesRead;
                maybeTerminateInputStream(connection, bytesRemaining);

                try {
                    connectionInputStream.close();
                } catch (final IOException e) {
                    throw new HttpDataSourceException(e, castNonNull(dataSpec),
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                            HttpDataSourceException.TYPE_CLOSE);
                }
            }
        } finally {
            inputStream = null;
            closeConnectionQuietly();
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }

    @NonNull
    private HttpURLConnection makeConnection(@NonNull final DataSpec dataSpecToUse)
            throws IOException {
        URL url = new URL(dataSpecToUse.uri.toString());
        @HttpMethod int httpMethod = dataSpecToUse.httpMethod;
        @Nullable byte[] httpBody = dataSpecToUse.httpBody;
        final long position = dataSpecToUse.position;
        final long length = dataSpecToUse.length;
        final boolean allowGzip = dataSpecToUse.isFlagSet(DataSpec.FLAG_ALLOW_GZIP);

        if (!allowCrossProtocolRedirects && !keepPostFor302Redirects) {
            // HttpURLConnection disallows cross-protocol redirects, but otherwise performs
            // redirection automatically. This is the behavior we want, so use it.
            return makeConnection(url, httpMethod, httpBody, position, length, allowGzip, true,
                    dataSpecToUse.httpRequestHeaders);
        }

        // We need to handle redirects ourselves to allow cross-protocol redirects or to keep the
        // POST request method for 302.
        int redirectCount = 0;
        while (redirectCount++ <= MAX_REDIRECTS) {
            final HttpURLConnection httpURLConnection = makeConnection(url, httpMethod, httpBody,
                    position, length, allowGzip, false, dataSpecToUse.httpRequestHeaders);
            final int httpURLConnectionResponseCode = httpURLConnection.getResponseCode();
            final String location = httpURLConnection.getHeaderField("Location");
            if ((httpMethod == DataSpec.HTTP_METHOD_GET || httpMethod == DataSpec.HTTP_METHOD_HEAD)
                    && (httpURLConnectionResponseCode == HttpURLConnection.HTTP_MULT_CHOICE
                    || httpURLConnectionResponseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || httpURLConnectionResponseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || httpURLConnectionResponseCode == HttpURLConnection.HTTP_SEE_OTHER
                    || httpURLConnectionResponseCode == HTTP_STATUS_TEMPORARY_REDIRECT
                    || httpURLConnectionResponseCode == HTTP_STATUS_PERMANENT_REDIRECT)) {
                httpURLConnection.disconnect();
                url = handleRedirect(url, location, dataSpecToUse);
            } else if (httpMethod == DataSpec.HTTP_METHOD_POST
                    && (httpURLConnectionResponseCode == HttpURLConnection.HTTP_MULT_CHOICE
                    || httpURLConnectionResponseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || httpURLConnectionResponseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || httpURLConnectionResponseCode == HttpURLConnection.HTTP_SEE_OTHER)) {
                httpURLConnection.disconnect();
                final boolean shouldKeepPost = keepPostFor302Redirects
                        && responseCode == HttpURLConnection.HTTP_MOVED_TEMP;
                if (!shouldKeepPost) {
                    // POST request follows the redirect and is transformed into a GET request.
                    httpMethod = DataSpec.HTTP_METHOD_GET;
                    httpBody = null;
                }
                url = handleRedirect(url, location, dataSpecToUse);
            } else {
                return httpURLConnection;
            }
        }

        // If we get here we've been redirected more times than are permitted.
        throw new HttpDataSourceException(
                new NoRouteToHostException("Too many redirects: " + redirectCount),
                dataSpecToUse,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSourceException.TYPE_OPEN);
    }

    /**
     * Configures a connection and opens it.
     *
     * @param url               The url to connect to.
     * @param httpMethod        The http method.
     * @param httpBody          The body data, or {@code null} if not required.
     * @param position          The byte offset of the requested data.
     * @param length            The length of the requested data, or {@link C#LENGTH_UNSET}.
     * @param allowGzip         Whether to allow the use of gzip.
     * @param followRedirects   Whether to follow redirects.
     * @param requestParameters parameters (HTTP headers) to include in request.
     * @return the connection opened
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    @NonNull
    private HttpURLConnection makeConnection(
            @NonNull final URL url,
            @HttpMethod final int httpMethod,
            @Nullable final byte[] httpBody,
            final long position,
            final long length,
            final boolean allowGzip,
            final boolean followRedirects,
            final Map<String, String> requestParameters) throws IOException {
        // This is the method that contains breaking changes with respect to DefaultHttpDataSource!

        String requestUrl = url.toString();

        // Don't add the request number parameter if it has been already added (for instance in
        // DASH manifests) or if that's not a videoplayback URL
        final boolean isVideoPlaybackUrl = url.getPath().startsWith("/videoplayback");
        if (isVideoPlaybackUrl && rnParameterEnabled && !requestUrl.contains(RN_PARAMETER)) {
            requestUrl += RN_PARAMETER + requestNumber;
            ++requestNumber;
        }

        if (rangeParameterEnabled && isVideoPlaybackUrl) {
            final String rangeParameterBuilt = buildRangeParameter(position, length);
            if (rangeParameterBuilt != null) {
                requestUrl += rangeParameterBuilt;
            }
        }

        final HttpURLConnection httpURLConnection = openConnection(new URL(requestUrl));
        httpURLConnection.setConnectTimeout(connectTimeoutMillis);
        httpURLConnection.setReadTimeout(readTimeoutMillis);

        final Map<String, String> requestHeaders = new HashMap<>();
        if (defaultRequestProperties != null) {
            requestHeaders.putAll(defaultRequestProperties.getSnapshot());
        }
        requestHeaders.putAll(requestProperties.getSnapshot());
        requestHeaders.putAll(requestParameters);

        for (final Map.Entry<String, String> property : requestHeaders.entrySet()) {
            httpURLConnection.setRequestProperty(property.getKey(), property.getValue());
        }

        if (!rangeParameterEnabled) {
            final String rangeHeader = buildRangeRequestHeader(position, length);
            if (rangeHeader != null) {
                httpURLConnection.setRequestProperty(HttpHeaders.RANGE, rangeHeader);
            }
        }

        final boolean isTvHtml5StreamingUrl = isTvHtml5StreamingUrl(requestUrl);

        if (isWebStreamingUrl(requestUrl)
                || isTvHtml5StreamingUrl
                || isWebEmbeddedPlayerStreamingUrl(requestUrl)) {
            httpURLConnection.setRequestProperty(HttpHeaders.ORIGIN, YOUTUBE_BASE_URL);
            httpURLConnection.setRequestProperty(HttpHeaders.REFERER, YOUTUBE_BASE_URL);
            httpURLConnection.setRequestProperty(HttpHeaders.SEC_FETCH_DEST, "empty");
            httpURLConnection.setRequestProperty(HttpHeaders.SEC_FETCH_MODE, "cors");
            httpURLConnection.setRequestProperty(HttpHeaders.SEC_FETCH_SITE, "cross-site");
        }

        httpURLConnection.setRequestProperty(HttpHeaders.TE, "trailers");

        final boolean isAndroidStreamingUrl = isAndroidStreamingUrl(requestUrl);
        final boolean isIosStreamingUrl = isIosStreamingUrl(requestUrl);
        if (isAndroidStreamingUrl) {
            // Improvement which may be done: find the content country used to request YouTube
            // contents to add it in the user agent instead of using the default
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT,
                    getAndroidUserAgent(null));
        } else if (isIosStreamingUrl) {
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT,
                    getIosUserAgent(null));
        } else if (isTvHtml5StreamingUrl) {
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT,
                    getTvHtml5UserAgent());
        } else {
            // non-mobile user agent
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, DownloaderImpl.USER_AGENT);
        }

        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT_ENCODING,
                allowGzip ? "gzip" : "identity");
        httpURLConnection.setInstanceFollowRedirects(followRedirects);
        // Most clients use POST requests to fetch contents
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setDoOutput(true);
        httpURLConnection.setFixedLengthStreamingMode(POST_BODY.length);
        httpURLConnection.connect();

        final OutputStream os = httpURLConnection.getOutputStream();
        os.write(POST_BODY);
        os.close();

        return httpURLConnection;
    }

    /**
     * Creates an {@link HttpURLConnection} that is connected with the {@code url}.
     *
     * @param url the {@link URL} to create an {@link HttpURLConnection}
     * @return an {@link HttpURLConnection} created with the {@code url}
     */
    private HttpURLConnection openConnection(@NonNull final URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * Handles a redirect.
     *
     * @param originalUrl              The original URL.
     * @param location                 The Location header in the response. May be {@code null}.
     * @param dataSpecToHandleRedirect The {@link DataSpec}.
     * @return The next URL.
     * @throws HttpDataSourceException If redirection isn't possible.
     */
    @NonNull
    private URL handleRedirect(final URL originalUrl,
                               @Nullable final String location,
                               final DataSpec dataSpecToHandleRedirect)
            throws HttpDataSourceException {
        if (location == null) {
            throw new HttpDataSourceException("Null location redirect", dataSpecToHandleRedirect,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN);
        }

        // Form the new url.
        final URL url;
        try {
            url = new URL(originalUrl, location);
        } catch (final MalformedURLException e) {
            throw new HttpDataSourceException(e, dataSpecToHandleRedirect,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN);
        }

        // Check that the protocol of the new url is supported.
        final String protocol = url.getProtocol();
        if (!"https".equals(protocol) && !"http".equals(protocol)) {
            throw new HttpDataSourceException("Unsupported protocol redirect: " + protocol,
                    dataSpecToHandleRedirect,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN);
        }

        if (!allowCrossProtocolRedirects && !protocol.equals(originalUrl.getProtocol())) {
            throw new HttpDataSourceException(
                    "Disallowed cross-protocol redirect ("
                            + originalUrl.getProtocol()
                            + " to "
                            + protocol
                            + ")",
                    dataSpecToHandleRedirect,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN);
        }

        return url;
    }

    /**
     * Attempts to skip the specified number of bytes in full.
     *
     * @param bytesToSkip   The number of bytes to skip.
     * @param dataSpecToUse The {@link DataSpec}.
     * @throws IOException If the thread is interrupted during the operation, or if the data ended
     * before skipping the specified number of bytes.
     */
    @SuppressWarnings("checkstyle:FinalParameters")
    private void skipFully(long bytesToSkip, final DataSpec dataSpecToUse) throws IOException {
        if (bytesToSkip == 0) {
            return;
        }

        final byte[] skipBuffer = new byte[4096];
        while (bytesToSkip > 0) {
            final int readLength = (int) min(bytesToSkip, skipBuffer.length);
            final int read = castNonNull(inputStream).read(skipBuffer, 0, readLength);
            if (Thread.currentThread().isInterrupted()) {
                throw new HttpDataSourceException(
                        new InterruptedIOException(),
                        dataSpecToUse,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        HttpDataSourceException.TYPE_OPEN);
            }

            if (read == -1) {
                throw new HttpDataSourceException(
                        dataSpecToUse,
                        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                        HttpDataSourceException.TYPE_OPEN);
            }

            bytesToSkip -= read;
            bytesTransferred(read);
        }
    }

    /**
     * Reads up to {@code length} bytes of data and stores them into {@code buffer}, starting at
     * index {@code offset}.
     *
     * <p>
     * This method blocks until at least one byte of data can be read, the end of the opened range
     * is detected, or an exception is thrown.
     * </p>
     *
     * @param buffer     The buffer into which the read data should be stored.
     * @param offset     The start offset into {@code buffer} at which data should be written.
     * @param readLength The maximum number of bytes to read.
     * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the end of the opened
     * range is reached.
     * @throws IOException If an error occurs reading from the source.
     */
    @SuppressWarnings("checkstyle:FinalParameters")
    private int readInternal(final byte[] buffer, final int offset, int readLength)
            throws IOException {
        if (readLength == 0) {
            return 0;
        }
        if (bytesToRead != C.LENGTH_UNSET) {
            final long bytesRemaining = bytesToRead - bytesRead;
            if (bytesRemaining == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            readLength = (int) min(readLength, bytesRemaining);
        }

        final int read = castNonNull(inputStream).read(buffer, offset, readLength);
        if (read == -1) {
            return C.RESULT_END_OF_INPUT;
        }

        bytesRead += read;
        bytesTransferred(read);
        return read;
    }

    /**
     * On platform API levels 19 and 20, okhttp's implementation of {@link InputStream#close} can
     * block for a long time if the stream has a lot of data remaining. Call this method before
     * closing the input stream to make a best effort to cause the input stream to encounter an
     * unexpected end of input, working around this issue. On other platform API levels, the method
     * does nothing.
     *
     * @param connection     The connection whose {@link InputStream} should be terminated.
     * @param bytesRemaining The number of bytes remaining to be read from the input stream if its
     *                       length is known. {@link C#LENGTH_UNSET} otherwise.
     */
    private static void maybeTerminateInputStream(@Nullable final HttpURLConnection connection,
                                                  final long bytesRemaining) {
        if (connection == null || Util.SDK_INT < 19 || Util.SDK_INT > 20) {
            return;
        }

        try {
            final InputStream inputStream = connection.getInputStream();
            if (bytesRemaining == C.LENGTH_UNSET) {
                // If the input stream has already ended, do nothing. The socket may be re-used.
                if (inputStream.read() == -1) {
                    return;
                }
            } else if (bytesRemaining <= MAX_BYTES_TO_DRAIN) {
                // There isn't much data left. Prefer to allow it to drain, which may allow the
                // socket to be re-used.
                return;
            }
            final String className = inputStream.getClass().getName();
            if ("com.android.okhttp.internal.http.HttpTransport$ChunkedInputStream"
                    .equals(className)
                    || "com.android.okhttp.internal.http.HttpTransport$FixedLengthInputStream"
                        .equals(className)) {
                final Class<?> superclass = inputStream.getClass().getSuperclass();
                final Method unexpectedEndOfInput = checkNotNull(superclass).getDeclaredMethod(
                        "unexpectedEndOfInput");
                unexpectedEndOfInput.setAccessible(true);
                unexpectedEndOfInput.invoke(inputStream);
            }
        } catch (final Exception e) {
            // If an IOException then the connection didn't ever have an input stream, or it was
            // closed already. If another type of exception then something went wrong, most likely
            // the device isn't using okhttp.
        }
    }

    /**
     * Closes the current connection quietly, if there is one.
     */
    private void closeConnectionQuietly() {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (final Exception e) {
                Log.e(TAG, "Unexpected error while disconnecting", e);
            }
            connection = null;
        }
    }

    private static boolean isCompressed(@NonNull final HttpURLConnection connection) {
        final String contentEncoding = connection.getHeaderField("Content-Encoding");
        return "gzip".equalsIgnoreCase(contentEncoding);
    }

    /**
     * Builds a {@code range} parameter for the given position and length.
     *
     * <p>
     * To fetch its contents, YouTube use range requests which append a {@code range} parameter
     * to videoplayback URLs instead of the {@code Range} header (even if the server respond
     * correctly when requesting a range of a ressouce with it).
     * </p>
     *
     * <p>
     * The parameter works in the same way as the header.
     * </p>
     *
     * @param position The request position.
     * @param length The request length, or {@link C#LENGTH_UNSET} if the request is unbounded.
     * @return The corresponding {@code range} parameter, or {@code null} if this parameter is
     * unnecessary because the whole resource is being requested.
     */
    @Nullable
    private static String buildRangeParameter(final long position, final long length) {
        if (position == 0 && length == C.LENGTH_UNSET) {
            return null;
        }

        final StringBuilder rangeParameter = new StringBuilder();
        rangeParameter.append("&range=");
        rangeParameter.append(position);
        rangeParameter.append("-");
        if (length != C.LENGTH_UNSET) {
            rangeParameter.append(position + length - 1);
        }
        return rangeParameter.toString();
    }

    private static final class NullFilteringHeadersMap
            extends ForwardingMap<String, List<String>> {
        private final Map<String, List<String>> headers;

        NullFilteringHeadersMap(final Map<String, List<String>> headers) {
            this.headers = headers;
        }

        @NonNull
        @Override
        protected Map<String, List<String>> delegate() {
            return headers;
        }

        @Override
        public boolean containsKey(@Nullable final Object key) {
            return key != null && super.containsKey(key);
        }

        @Nullable
        @Override
        public List<String> get(@Nullable final Object key) {
            return key == null ? null : super.get(key);
        }

        @NonNull
        @Override
        public Set<String> keySet() {
            return Sets.filter(super.keySet(), Objects::nonNull);
        }

        @NonNull
        @Override
        public Set<Entry<String, List<String>>> entrySet() {
            return Sets.filter(super.entrySet(), entry -> entry.getKey() != null);
        }

        @Override
        public int size() {
            return super.size() - (super.containsKey(null) ? 1 : 0);
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() || (super.size() == 1 && super.containsKey(null));
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return super.standardContainsValue(value);
        }

        @Override
        public boolean equals(@Nullable final Object object) {
            return object != null && super.standardEquals(object);
        }

        @Override
        public int hashCode() {
            return super.standardHashCode();
        }
    }
}

