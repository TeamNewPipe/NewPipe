/*
 * Based on ExoPlayer's DefaultHttpDataSource, version 2.18.1.
 *
 * Original source code copyright (C) 2016 The Android Open Source Project, licensed under the
 * Apache License, Version 2.0.
 */
package org.schabi.newpipe.player.datasource

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSourceException
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidContentTypeException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import com.google.android.exoplayer2.upstream.HttpDataSource.RequestProperties
import com.google.android.exoplayer2.upstream.HttpUtil
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import com.google.common.base.Predicate
import com.google.common.collect.ForwardingMap
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Sets
import com.google.common.net.HttpHeaders
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.URL
import java.util.Objects
import java.util.zip.GZIPInputStream
import kotlin.math.min

/**
 * An [HttpDataSource] that uses Android's [HttpURLConnection], based on
 * [com.google.android.exoplayer2.upstream.DefaultHttpDataSource], for YouTube streams.
 *
 *
 *
 * It adds more headers to `videoplayback` URLs, such as `Origin`, `Referer`
 * (only where it's relevant) and also more parameters, such as `rn` and replaces the use of
 * the `Range` header by the corresponding parameter (`range`), if enabled.
 *
 *
 * There are many unused methods in this class because everything was copied from [ ] with as little changes as possible.
 * SonarQube warnings were also suppressed for the same reason.
 */
class YoutubeHttpDataSource private constructor(private val connectTimeoutMillis: Int,
                                                private val readTimeoutMillis: Int,
                                                private val allowCrossProtocolRedirects: Boolean,
                                                private val rangeParameterEnabled: Boolean,
                                                private val rnParameterEnabled: Boolean,
                                                private val defaultRequestProperties: RequestProperties?,
                                                private val contentTypePredicate: Predicate<String>?,
                                                keepPostFor302Redirects: Boolean) : BaseDataSource(true), HttpDataSource {
    /**
     * [DataSource.Factory] for [YoutubeHttpDataSource] instances.
     */
    class Factory() : HttpDataSource.Factory {
        private val defaultRequestProperties: RequestProperties
        private var transferListener: TransferListener? = null
        private var contentTypePredicate: Predicate<String>? = null
        private var connectTimeoutMs: Int
        private var readTimeoutMs: Int
        private var allowCrossProtocolRedirects: Boolean = false
        private var keepPostFor302Redirects: Boolean = false
        private var rangeParameterEnabled: Boolean = false
        private var rnParameterEnabled: Boolean = false

        /**
         * Creates an instance.
         */
        init {
            defaultRequestProperties = RequestProperties()
            connectTimeoutMs = DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS
            readTimeoutMs = DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS
        }

        public override fun setDefaultRequestProperties(
                defaultRequestPropertiesMap: Map<String, String>): Factory {
            defaultRequestProperties.clearAndSet(defaultRequestPropertiesMap)
            return this
        }

        /**
         * Sets the connect timeout, in milliseconds.
         *
         *
         *
         * The default is [DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS].
         *
         *
         * @param connectTimeoutMsValue The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        fun setConnectTimeoutMs(connectTimeoutMsValue: Int): Factory {
            connectTimeoutMs = connectTimeoutMsValue
            return this
        }

        /**
         * Sets the read timeout, in milliseconds.
         *
         *
         * The default is [DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS].
         *
         * @param readTimeoutMsValue The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        fun setReadTimeoutMs(readTimeoutMsValue: Int): Factory {
            readTimeoutMs = readTimeoutMsValue
            return this
        }

        /**
         * Sets whether to allow cross protocol redirects.
         *
         *
         * The default is `false`.
         *
         * @param allowCrossProtocolRedirectsValue Whether to allow cross protocol redirects.
         * @return This factory.
         */
        fun setAllowCrossProtocolRedirects(
                allowCrossProtocolRedirectsValue: Boolean): Factory {
            allowCrossProtocolRedirects = allowCrossProtocolRedirectsValue
            return this
        }

        /**
         * Sets whether the use of the `range` parameter instead of the `Range` header
         * to request ranges of streams is enabled.
         *
         *
         *
         * Note that it must be not enabled on streams which are using a [ ], as it will break playback
         * for them (some exceptions may be thrown).
         *
         *
         * @param rangeParameterEnabledValue whether the use of the `range` parameter instead
         * of the `Range` header (must be only enabled when
         * non-`ProgressiveMediaSource`s)
         * @return This factory.
         */
        fun setRangeParameterEnabled(rangeParameterEnabledValue: Boolean): Factory {
            rangeParameterEnabled = rangeParameterEnabledValue
            return this
        }

        /**
         * Sets whether the use of the `rn`, which stands for request number, parameter is
         * enabled.
         *
         *
         *
         * Note that it should be not enabled on streams which are using `/` to delimit URLs
         * parameters, such as the streams of HLS manifests.
         *
         *
         * @param rnParameterEnabledValue whether the appending the `rn` parameter to
         * `videoplayback` URLs
         * @return This factory.
         */
        fun setRnParameterEnabled(rnParameterEnabledValue: Boolean): Factory {
            rnParameterEnabled = rnParameterEnabledValue
            return this
        }

        /**
         * Sets a content type [Predicate]. If a content type is rejected by the predicate
         * then a [HttpDataSource.InvalidContentTypeException] is thrown from
         * [YoutubeHttpDataSource.open].
         *
         *
         *
         * The default is `null`.
         *
         *
         * @param contentTypePredicateToSet The content type [Predicate], or `null` to
         * clear a predicate that was previously set.
         * @return This factory.
         */
        fun setContentTypePredicate(
                contentTypePredicateToSet: Predicate<String>?): Factory {
            contentTypePredicate = contentTypePredicateToSet
            return this
        }

        /**
         * Sets the [TransferListener] that will be used.
         *
         *
         * The default is `null`.
         *
         *
         * See [DataSource.addTransferListener].
         *
         * @param transferListenerToUse The listener that will be used.
         * @return This factory.
         */
        fun setTransferListener(
                transferListenerToUse: TransferListener?): Factory {
            transferListener = transferListenerToUse
            return this
        }

        /**
         * Sets whether we should keep the POST method and body when we have HTTP 302 redirects for
         * a POST request.
         *
         * @param keepPostFor302RedirectsValue Whether we should keep the POST method and body when
         * we have HTTP 302 redirects for a POST request.
         * @return This factory.
         */
        fun setKeepPostFor302Redirects(keepPostFor302RedirectsValue: Boolean): Factory {
            keepPostFor302Redirects = keepPostFor302RedirectsValue
            return this
        }

        public override fun createDataSource(): YoutubeHttpDataSource {
            val dataSource: YoutubeHttpDataSource = YoutubeHttpDataSource(
                    connectTimeoutMs,
                    readTimeoutMs,
                    allowCrossProtocolRedirects,
                    rangeParameterEnabled,
                    rnParameterEnabled,
                    defaultRequestProperties,
                    contentTypePredicate,
                    keepPostFor302Redirects)
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener!!)
            }
            return dataSource
        }
    }

    private val requestProperties: RequestProperties
    private val keepPostFor302Redirects: Boolean
    private var dataSpec: DataSpec? = null
    private var connection: HttpURLConnection? = null
    private var inputStream: InputStream? = null
    private var opened: Boolean = false
    private var responseCode: Int = 0
    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0
    private var requestNumber: Long

    init {
        requestProperties = RequestProperties()
        this.keepPostFor302Redirects = keepPostFor302Redirects
        requestNumber = 0
    }

    public override fun getUri(): Uri? {
        return if (connection == null) null else Uri.parse(connection!!.getURL().toString())
    }

    public override fun getResponseCode(): Int {
        return if (connection == null || responseCode <= 0) -1 else responseCode
    }

    public override fun getResponseHeaders(): Map<String, List<String>> {
        if (connection == null) {
            return ImmutableMap.of()
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
        return NullFilteringHeadersMap(connection!!.getHeaderFields())
    }

    public override fun setRequestProperty(name: String, value: String) {
        Assertions.checkNotNull(name)
        Assertions.checkNotNull(value)
        requestProperties.set(name, value)
    }

    public override fun clearRequestProperty(name: String) {
        Assertions.checkNotNull(name)
        requestProperties.remove(name)
    }

    public override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    /**
     * Opens the source to read the specified data.
     */
    @Throws(HttpDataSourceException::class)
    public override fun open(dataSpecParameter: DataSpec): Long {
        this.dataSpec = dataSpecParameter
        bytesRead = 0
        bytesToRead = 0
        transferInitializing(dataSpecParameter)
        val httpURLConnection: HttpURLConnection?
        val responseMessage: String
        try {
            connection = makeConnection(dataSpec!!)
            httpURLConnection = connection
            responseCode = httpURLConnection!!.getResponseCode()
            responseMessage = httpURLConnection.getResponseMessage()
        } catch (e: IOException) {
            closeConnectionQuietly()
            throw HttpDataSourceException.createForIOException(e, dataSpec!!,
                    HttpDataSourceException.TYPE_OPEN)
        }

        // Check for a valid response code.
        if (responseCode < 200 || responseCode > 299) {
            val headers: Map<String, List<String>> = httpURLConnection.getHeaderFields()
            if (responseCode == 416) {
                val documentSize: Long = HttpUtil.getDocumentSize(
                        httpURLConnection.getHeaderField(HttpHeaders.CONTENT_RANGE))
                if (dataSpecParameter.position == documentSize) {
                    opened = true
                    transferStarted(dataSpecParameter)
                    return if (dataSpecParameter.length != C.LENGTH_UNSET.toLong()) dataSpecParameter.length else 0
                }
            }
            val errorStream: InputStream? = httpURLConnection.getErrorStream()
            var errorResponseBody: ByteArray?
            try {
                errorResponseBody = if (errorStream != null) Util.toByteArray(errorStream) else Util.EMPTY_BYTE_ARRAY
            } catch (e: IOException) {
                errorResponseBody = Util.EMPTY_BYTE_ARRAY
            }
            closeConnectionQuietly()
            val cause: IOException? = if (responseCode == 416) DataSourceException(
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) else null
            throw InvalidResponseCodeException(responseCode, responseMessage, cause, headers,
                    dataSpec!!, (errorResponseBody)!!)
        }

        // Check for a valid content type.
        val contentType: String = httpURLConnection.getContentType()
        if (contentTypePredicate != null && !contentTypePredicate.apply(contentType)) {
            closeConnectionQuietly()
            throw InvalidContentTypeException(contentType, dataSpecParameter)
        }
        val bytesToSkip: Long
        if (!rangeParameterEnabled) {
            // If we requested a range starting from a non-zero position and received a 200 rather
            // than a 206, then the server does not support partial requests. We'll need to
            // manually skip to the requested position.
            bytesToSkip = if (responseCode == 200 && dataSpecParameter.position != 0L) dataSpecParameter.position else 0
        } else {
            bytesToSkip = 0
        }


        // Determine the length of the data to be read, after skipping.
        val isCompressed: Boolean = isCompressed((httpURLConnection))
        if (!isCompressed) {
            if (dataSpecParameter.length != C.LENGTH_UNSET.toLong()) {
                bytesToRead = dataSpecParameter.length
            } else {
                val contentLength: Long = HttpUtil.getContentLength(
                        httpURLConnection.getHeaderField(HttpHeaders.CONTENT_LENGTH),
                        httpURLConnection.getHeaderField(HttpHeaders.CONTENT_RANGE))
                bytesToRead = if (contentLength != C.LENGTH_UNSET.toLong()) (contentLength - bytesToSkip) else C.LENGTH_UNSET.toLong()
            }
        } else {
            // Gzip is enabled. If the server opts to use gzip then the content length in the
            // response will be that of the compressed data, which isn't what we want. Always use
            // the dataSpec length in this case.
            bytesToRead = dataSpecParameter.length
        }
        try {
            inputStream = httpURLConnection.getInputStream()
            if (isCompressed) {
                inputStream = GZIPInputStream(inputStream)
            }
        } catch (e: IOException) {
            closeConnectionQuietly()
            throw HttpDataSourceException(e, dataSpec!!,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    HttpDataSourceException.TYPE_OPEN)
        }
        opened = true
        transferStarted(dataSpecParameter)
        try {
            skipFully(bytesToSkip, dataSpec!!)
        } catch (e: IOException) {
            closeConnectionQuietly()
            if (e is HttpDataSourceException) {
                throw (e as HttpDataSourceException?)!!
            }
            throw HttpDataSourceException(e, dataSpec!!,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    HttpDataSourceException.TYPE_OPEN)
        }
        return bytesToRead
    }

    @Throws(HttpDataSourceException::class)
    public override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        try {
            return readInternal(buffer, offset, length)
        } catch (e: IOException) {
            throw HttpDataSourceException.createForIOException(e, Util.castNonNull(dataSpec),
                    HttpDataSourceException.TYPE_READ)
        }
    }

    @Throws(HttpDataSourceException::class)
    public override fun close() {
        try {
            val connectionInputStream: InputStream? = inputStream
            if (connectionInputStream != null) {
                val bytesRemaining: Long = if (bytesToRead == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else bytesToRead - bytesRead
                maybeTerminateInputStream(connection, bytesRemaining)
                try {
                    connectionInputStream.close()
                } catch (e: IOException) {
                    throw HttpDataSourceException(e, Util.castNonNull(dataSpec),
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                            HttpDataSourceException.TYPE_CLOSE)
                }
            }
        } finally {
            inputStream = null
            closeConnectionQuietly()
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    @Throws(IOException::class)
    private fun makeConnection(dataSpecToUse: DataSpec): HttpURLConnection {
        var url: URL = URL(dataSpecToUse.uri.toString())
        var httpMethod: @DataSpec.HttpMethod Int = dataSpecToUse.httpMethod
        var httpBody: ByteArray? = dataSpecToUse.httpBody
        val position: Long = dataSpecToUse.position
        val length: Long = dataSpecToUse.length
        val allowGzip: Boolean = dataSpecToUse.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)
        if (!allowCrossProtocolRedirects && !keepPostFor302Redirects) {
            // HttpURLConnection disallows cross-protocol redirects, but otherwise performs
            // redirection automatically. This is the behavior we want, so use it.
            return makeConnection(url, httpMethod, httpBody, position, length, allowGzip, true,
                    dataSpecToUse.httpRequestHeaders)
        }

        // We need to handle redirects ourselves to allow cross-protocol redirects or to keep the
        // POST request method for 302.
        var redirectCount: Int = 0
        while (redirectCount++ <= MAX_REDIRECTS) {
            val httpURLConnection: HttpURLConnection = makeConnection(url, httpMethod, httpBody,
                    position, length, allowGzip, false, dataSpecToUse.httpRequestHeaders)
            val httpURLConnectionResponseCode: Int = httpURLConnection.getResponseCode()
            val location: String = httpURLConnection.getHeaderField("Location")
            if (((httpMethod == DataSpec.HTTP_METHOD_GET || httpMethod == DataSpec.HTTP_METHOD_HEAD)
                            && ((httpURLConnectionResponseCode == HttpURLConnection.HTTP_MULT_CHOICE
                            ) || (httpURLConnectionResponseCode == HttpURLConnection.HTTP_MOVED_PERM
                            ) || (httpURLConnectionResponseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            ) || (httpURLConnectionResponseCode == HttpURLConnection.HTTP_SEE_OTHER
                            ) || (httpURLConnectionResponseCode == HTTP_STATUS_TEMPORARY_REDIRECT
                            ) || (httpURLConnectionResponseCode == HTTP_STATUS_PERMANENT_REDIRECT)))) {
                httpURLConnection.disconnect()
                url = handleRedirect(url, location, dataSpecToUse)
            } else if ((httpMethod == DataSpec.HTTP_METHOD_POST
                            && ((httpURLConnectionResponseCode == HttpURLConnection.HTTP_MULT_CHOICE
                            ) || (httpURLConnectionResponseCode == HttpURLConnection.HTTP_MOVED_PERM
                            ) || (httpURLConnectionResponseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            ) || (httpURLConnectionResponseCode == HttpURLConnection.HTTP_SEE_OTHER)))) {
                httpURLConnection.disconnect()
                val shouldKeepPost: Boolean = (keepPostFor302Redirects
                        && responseCode == HttpURLConnection.HTTP_MOVED_TEMP)
                if (!shouldKeepPost) {
                    // POST request follows the redirect and is transformed into a GET request.
                    httpMethod = DataSpec.HTTP_METHOD_GET
                    httpBody = null
                }
                url = handleRedirect(url, location, dataSpecToUse)
            } else {
                return httpURLConnection
            }
        }

        // If we get here we've been redirected more times than are permitted.
        throw HttpDataSourceException(
                NoRouteToHostException("Too many redirects: " + redirectCount),
                dataSpecToUse,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSourceException.TYPE_OPEN)
    }

    /**
     * Configures a connection and opens it.
     *
     * @param url               The url to connect to.
     * @param httpMethod        The http method.
     * @param httpBody          The body data, or `null` if not required.
     * @param position          The byte offset of the requested data.
     * @param length            The length of the requested data, or [C.LENGTH_UNSET].
     * @param allowGzip         Whether to allow the use of gzip.
     * @param followRedirects   Whether to follow redirects.
     * @param requestParameters parameters (HTTP headers) to include in request.
     * @return the connection opened
     */
    @Throws(IOException::class)
    private fun makeConnection(
            url: URL,
            httpMethod: @DataSpec.HttpMethod Int,
            httpBody: ByteArray?,
            position: Long,
            length: Long,
            allowGzip: Boolean,
            followRedirects: Boolean,
            requestParameters: Map<String, String>): HttpURLConnection {
        // This is the method that contains breaking changes with respect to DefaultHttpDataSource!
        var requestUrl: String = url.toString()

        // Don't add the request number parameter if it has been already added (for instance in
        // DASH manifests) or if that's not a videoplayback URL
        val isVideoPlaybackUrl: Boolean = url.getPath().startsWith("/videoplayback")
        if (isVideoPlaybackUrl && rnParameterEnabled && !requestUrl.contains(RN_PARAMETER)) {
            requestUrl += RN_PARAMETER + requestNumber
            ++requestNumber
        }
        if (rangeParameterEnabled && isVideoPlaybackUrl) {
            val rangeParameterBuilt: String? = buildRangeParameter(position, length)
            if (rangeParameterBuilt != null) {
                requestUrl += rangeParameterBuilt
            }
        }
        val httpURLConnection: HttpURLConnection = openConnection(URL(requestUrl))
        httpURLConnection.setConnectTimeout(connectTimeoutMillis)
        httpURLConnection.setReadTimeout(readTimeoutMillis)
        val requestHeaders: MutableMap<String, String> = HashMap()
        if (defaultRequestProperties != null) {
            requestHeaders.putAll(defaultRequestProperties.getSnapshot())
        }
        requestHeaders.putAll(requestProperties.getSnapshot())
        requestHeaders.putAll(requestParameters)
        for (property: Map.Entry<String, String> in requestHeaders.entries) {
            httpURLConnection.setRequestProperty(property.key, property.value)
        }
        if (!rangeParameterEnabled) {
            val rangeHeader: String? = HttpUtil.buildRangeRequestHeader(position, length)
            if (rangeHeader != null) {
                httpURLConnection.setRequestProperty(HttpHeaders.RANGE, rangeHeader)
            }
        }
        if ((YoutubeParsingHelper.isWebStreamingUrl(requestUrl)
                        || YoutubeParsingHelper.isTvHtml5SimplyEmbeddedPlayerStreamingUrl(requestUrl))) {
            httpURLConnection.setRequestProperty(HttpHeaders.ORIGIN, YOUTUBE_BASE_URL)
            httpURLConnection.setRequestProperty(HttpHeaders.REFERER, YOUTUBE_BASE_URL)
            httpURLConnection.setRequestProperty(HttpHeaders.SEC_FETCH_DEST, "empty")
            httpURLConnection.setRequestProperty(HttpHeaders.SEC_FETCH_MODE, "cors")
            httpURLConnection.setRequestProperty(HttpHeaders.SEC_FETCH_SITE, "cross-site")
        }
        httpURLConnection.setRequestProperty(HttpHeaders.TE, "trailers")
        val isAndroidStreamingUrl: Boolean = YoutubeParsingHelper.isAndroidStreamingUrl(requestUrl)
        val isIosStreamingUrl: Boolean = YoutubeParsingHelper.isIosStreamingUrl(requestUrl)
        if (isAndroidStreamingUrl) {
            // Improvement which may be done: find the content country used to request YouTube
            // contents to add it in the user agent instead of using the default
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT,
                    YoutubeParsingHelper.getAndroidUserAgent(null))
        } else if (isIosStreamingUrl) {
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT,
                    YoutubeParsingHelper.getIosUserAgent(null))
        } else {
            // non-mobile user agent
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, DownloaderImpl.Companion.USER_AGENT)
        }
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT_ENCODING,
                if (allowGzip) "gzip" else "identity")
        httpURLConnection.setInstanceFollowRedirects(followRedirects)
        httpURLConnection.setDoOutput(httpBody != null)

        // Mobile clients uses POST requests to fetch contents
        httpURLConnection.setRequestMethod(if (isAndroidStreamingUrl || isIosStreamingUrl) "POST" else DataSpec.getStringForHttpMethod(httpMethod))
        if (httpBody != null) {
            httpURLConnection.setFixedLengthStreamingMode(httpBody.size)
            httpURLConnection.connect()
            val os: OutputStream = httpURLConnection.getOutputStream()
            os.write(httpBody)
            os.close()
        } else {
            httpURLConnection.connect()
        }
        return httpURLConnection
    }

    /**
     * Creates an [HttpURLConnection] that is connected with the `url`.
     *
     * @param url the [URL] to create an [HttpURLConnection]
     * @return an [HttpURLConnection] created with the `url`
     */
    @Throws(IOException::class)
    private fun openConnection(url: URL): HttpURLConnection {
        return url.openConnection() as HttpURLConnection
    }

    /**
     * Handles a redirect.
     *
     * @param originalUrl              The original URL.
     * @param location                 The Location header in the response. May be `null`.
     * @param dataSpecToHandleRedirect The [DataSpec].
     * @return The next URL.
     * @throws HttpDataSourceException If redirection isn't possible.
     */
    @Throws(HttpDataSourceException::class)
    private fun handleRedirect(originalUrl: URL,
                               location: String?,
                               dataSpecToHandleRedirect: DataSpec): URL {
        if (location == null) {
            throw HttpDataSourceException("Null location redirect", dataSpecToHandleRedirect,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN)
        }

        // Form the new url.
        val url: URL
        try {
            url = URL(originalUrl, location)
        } catch (e: MalformedURLException) {
            throw HttpDataSourceException(e, dataSpecToHandleRedirect,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN)
        }

        // Check that the protocol of the new url is supported.
        val protocol: String = url.getProtocol()
        if (!("https" == protocol) && !("http" == protocol)) {
            throw HttpDataSourceException("Unsupported protocol redirect: " + protocol,
                    dataSpecToHandleRedirect,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN)
        }
        if (!allowCrossProtocolRedirects && !(protocol == originalUrl.getProtocol())) {
            throw HttpDataSourceException(
                    ("Disallowed cross-protocol redirect ("
                            + originalUrl.getProtocol()
                            + " to "
                            + protocol
                            + ")"),
                    dataSpecToHandleRedirect,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN)
        }
        return url
    }

    /**
     * Attempts to skip the specified number of bytes in full.
     *
     * @param bytesToSkip   The number of bytes to skip.
     * @param dataSpecToUse The [DataSpec].
     * @throws IOException If the thread is interrupted during the operation, or if the data ended
     * before skipping the specified number of bytes.
     */
    @Throws(IOException::class)
    private fun skipFully(bytesToSkip: Long, dataSpecToUse: DataSpec) {
        var bytesToSkip: Long = bytesToSkip
        if (bytesToSkip == 0L) {
            return
        }
        val skipBuffer: ByteArray = ByteArray(4096)
        while (bytesToSkip > 0) {
            val readLength: Int = min(bytesToSkip.toDouble(), skipBuffer.size.toDouble()).toInt()
            val read: Int = Util.castNonNull(inputStream).read(skipBuffer, 0, readLength)
            if (Thread.currentThread().isInterrupted()) {
                throw HttpDataSourceException(
                        InterruptedIOException(),
                        dataSpecToUse,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        HttpDataSourceException.TYPE_OPEN)
            }
            if (read == -1) {
                throw HttpDataSourceException(
                        dataSpecToUse,
                        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                        HttpDataSourceException.TYPE_OPEN)
            }
            bytesToSkip -= read.toLong()
            bytesTransferred(read)
        }
    }

    /**
     * Reads up to `length` bytes of data and stores them into `buffer`, starting at
     * index `offset`.
     *
     *
     *
     * This method blocks until at least one byte of data can be read, the end of the opened range
     * is detected, or an exception is thrown.
     *
     *
     * @param buffer     The buffer into which the read data should be stored.
     * @param offset     The start offset into `buffer` at which data should be written.
     * @param readLength The maximum number of bytes to read.
     * @return The number of bytes read, or [C.RESULT_END_OF_INPUT] if the end of the opened
     * range is reached.
     * @throws IOException If an error occurs reading from the source.
     */
    @Throws(IOException::class)
    private fun readInternal(buffer: ByteArray, offset: Int, readLength: Int): Int {
        var readLength: Int = readLength
        if (readLength == 0) {
            return 0
        }
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining: Long = bytesToRead - bytesRead
            if (bytesRemaining == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            readLength = min(readLength.toDouble(), bytesRemaining.toDouble()).toInt()
        }
        val read: Int = Util.castNonNull(inputStream).read(buffer, offset, readLength)
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += read.toLong()
        bytesTransferred(read)
        return read
    }

    /**
     * Closes the current connection quietly, if there is one.
     */
    private fun closeConnectionQuietly() {
        if (connection != null) {
            try {
                connection!!.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while disconnecting", e)
            }
            connection = null
        }
    }

    private class NullFilteringHeadersMap internal constructor(private val headers: Map<String?, List<String>?>) : ForwardingMap<String?, List<String>?>() {
        override fun delegate(): Map<String?, List<String>?> {
            return headers
        }

        public override fun containsKey(key: Any?): Boolean {
            return key != null && super.containsKey(key)
        }

        public override operator fun get(key: Any?): List<String>? {
            return if (key == null) null else super.get(key)
        }

        public override fun keySet(): Set<String> {
            return Sets.filter<String>(super.keys, Predicate({ obj: String? -> Objects.nonNull(obj) }))
        }

        public override fun entrySet(): Set<Map.Entry<String?, List<String?>?>> {
            return Sets.filter<Map.Entry<String?, List<String?>?>>(super.entries, Predicate({ entry: Map.Entry<String?, List<String?>?> -> entry.key != null }))
        }

        public override fun size(): Int {
            return super.size - (if (super.containsKey(null)) 1 else 0)
        }

        public override fun isEmpty(): Boolean {
            return super.isEmpty() || (super.size == 1 && super.containsKey(null))
        }

        public override fun containsValue(value: Any?): Boolean {
            return super.standardContainsValue(value)
        }

        public override fun equals(`object`: Any?): Boolean {
            return `object` != null && super.standardEquals(`object`)
        }

        public override fun hashCode(): Int {
            return super.standardHashCode()
        }
    }

    companion object {
        private val TAG: String = YoutubeHttpDataSource::class.java.getSimpleName()
        private val MAX_REDIRECTS: Int = 20 // Same limit as okhttp.
        private val HTTP_STATUS_TEMPORARY_REDIRECT: Int = 307
        private val HTTP_STATUS_PERMANENT_REDIRECT: Int = 308
        private val MAX_BYTES_TO_DRAIN: Long = 2048
        private val RN_PARAMETER: String = "&rn="
        private val YOUTUBE_BASE_URL: String = "https://www.youtube.com"

        /**
         * On platform API levels 19 and 20, okhttp's implementation of [InputStream.close] can
         * block for a long time if the stream has a lot of data remaining. Call this method before
         * closing the input stream to make a best effort to cause the input stream to encounter an
         * unexpected end of input, working around this issue. On other platform API levels, the method
         * does nothing.
         *
         * @param connection     The connection whose [InputStream] should be terminated.
         * @param bytesRemaining The number of bytes remaining to be read from the input stream if its
         * length is known. [C.LENGTH_UNSET] otherwise.
         */
        private fun maybeTerminateInputStream(connection: HttpURLConnection?,
                                              bytesRemaining: Long) {
            if ((connection == null) || (Util.SDK_INT < 19) || (Util.SDK_INT > 20)) {
                return
            }
            try {
                val inputStream: InputStream = connection.getInputStream()
                if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                    // If the input stream has already ended, do nothing. The socket may be re-used.
                    if (inputStream.read() == -1) {
                        return
                    }
                } else if (bytesRemaining <= MAX_BYTES_TO_DRAIN) {
                    // There isn't much data left. Prefer to allow it to drain, which may allow the
                    // socket to be re-used.
                    return
                }
                val className: String = inputStream.javaClass.getName()
                if ((("com.android.okhttp.internal.http.HttpTransport\$ChunkedInputStream"
                                == className) || ("com.android.okhttp.internal.http.HttpTransport\$FixedLengthInputStream"
                                == className))) {
                    val superclass: Class<*> = inputStream.javaClass.getSuperclass()
                    val unexpectedEndOfInput: Method = Assertions.checkNotNull(superclass).getDeclaredMethod(
                            "unexpectedEndOfInput")
                    unexpectedEndOfInput.setAccessible(true)
                    unexpectedEndOfInput.invoke(inputStream)
                }
            } catch (e: Exception) {
                // If an IOException then the connection didn't ever have an input stream, or it was
                // closed already. If another type of exception then something went wrong, most likely
                // the device isn't using okhttp.
            }
        }

        private fun isCompressed(connection: HttpURLConnection): Boolean {
            val contentEncoding: String = connection.getHeaderField("Content-Encoding")
            return "gzip".equals(contentEncoding, ignoreCase = true)
        }

        /**
         * Builds a `range` parameter for the given position and length.
         *
         *
         *
         * To fetch its contents, YouTube use range requests which append a `range` parameter
         * to videoplayback URLs instead of the `Range` header (even if the server respond
         * correctly when requesting a range of a ressouce with it).
         *
         *
         *
         *
         * The parameter works in the same way as the header.
         *
         *
         * @param position The request position.
         * @param length The request length, or [C.LENGTH_UNSET] if the request is unbounded.
         * @return The corresponding `range` parameter, or `null` if this parameter is
         * unnecessary because the whole resource is being requested.
         */
        private fun buildRangeParameter(position: Long, length: Long): String? {
            if (position == 0L && length == C.LENGTH_UNSET.toLong()) {
                return null
            }
            val rangeParameter: StringBuilder = StringBuilder()
            rangeParameter.append("&range=")
            rangeParameter.append(position)
            rangeParameter.append("-")
            if (length != C.LENGTH_UNSET.toLong()) {
                rangeParameter.append(position + length - 1)
            }
            return rangeParameter.toString()
        }
    }
}
