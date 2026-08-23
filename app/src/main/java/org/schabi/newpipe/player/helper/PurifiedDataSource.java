package org.schabi.newpipe.player.helper;
/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.ContentDataSource;
import com.google.android.exoplayer2.upstream.DataSchemeDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.UdpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A {@link DataSource} that supports multiple URI schemes. The supported schemes are:
 *
 * <ul>
 *   <li>file: For fetching data from a local file (e.g. file:///path/to/media/media.mp4, or just
 *       /path/to/media/media.mp4 because the implementation assumes that a URI without a scheme is
 *       a local file URI).
 *   <li>asset: For fetching data from an asset in the application's apk (e.g. asset:///media.mp4).
 *   <li>rawresource: For fetching data from a raw resource in the application's apk (e.g.
 *       rawresource:///resourceId, where rawResourceId is the integer identifier of the raw
 *       resource).
 *   <li>android.resource: For fetching data in the application's apk (e.g.
 *       android.resource:///resourceId or android.resource://resourceType/resourceName). See {@link
 *       RawResourceDataSource} for more information about the URI form.
 *   <li>content: For fetching data from a content URI (e.g. content://authority/path/123).
 *   <li>rtmp: For fetching data over RTMP. Only supported if the project using ExoPlayer has an
 *       explicit dependency on ExoPlayer's RTMP extension.
 *   <li>data: For parsing data inlined in the URI as defined in RFC 2397.
 *   <li>udp: For fetching data over UDP (e.g. udp://something.com/media).
 *   <li>http(s): For fetching data over HTTP and HTTPS (e.g. https://www.something.com/media.mp4),
 *       if constructed using {@link #PurifiedDataSource(Context, String, boolean)}, or any other
 *       schemes supported by a base data source if constructed using {@link
 *       #PurifiedDataSource(Context, DataSource)}.
 * </ul>
 */
public final class PurifiedDataSource implements DataSource {

    /** {@link DataSource.Factory} for {@link PurifiedDataSource} instances. */
    public static final class Factory implements DataSource.Factory {

        private final Context context;
        private final DataSource.Factory baseDataSourceFactory;
        @Nullable private TransferListener transferListener;

        /**
         * Creates an instance.
         *
         * @param context A context.
         */
        public Factory(Context context) {
            this(context, new PurifiedHttpDataSource.Factory());
        }

        /**
         * Creates an instance.
         *
         * @param context A context.
         * @param baseDataSourceFactory The {@link DataSource.Factory} to be used to create base {@link
         *     DataSource DataSources} for {@link PurifiedDataSource} instances. The base {@link
         *     DataSource} is normally an {@link HttpDataSource}, and is responsible for fetching data
         *     over HTTP and HTTPS, as well as any other URI schemes not otherwise supported by {@link
         *     PurifiedDataSource}.
         */
        public Factory(Context context, DataSource.Factory baseDataSourceFactory) {
            this.context = context.getApplicationContext();
            this.baseDataSourceFactory = baseDataSourceFactory;
        }

        /**
         * Sets the {@link TransferListener} that will be used.
         *
         * <p>The default is {@code null}.
         *
         * <p>See {@link DataSource#addTransferListener(TransferListener)}.
         *
         * @param transferListener The listener that will be used.
         * @return This factory.
         */
        public Factory setTransferListener(@Nullable TransferListener transferListener) {
            this.transferListener = transferListener;
            return this;
        }

        @Override
        public PurifiedDataSource createDataSource() {
            PurifiedDataSource dataSource =
                    new PurifiedDataSource(context, baseDataSourceFactory.createDataSource());
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return dataSource;
        }
    }

    private static final String TAG = "PurifiedDataSource";

    private static final String SCHEME_ASSET = "asset";
    private static final String SCHEME_CONTENT = "content";
    private static final String SCHEME_RTMP = "rtmp";
    private static final String SCHEME_UDP = "udp";
    private static final String SCHEME_DATA = DataSchemeDataSource.SCHEME_DATA;
    private static final String SCHEME_RAW = RawResourceDataSource.RAW_RESOURCE_SCHEME;
    private static final String SCHEME_ANDROID_RESOURCE = ContentResolver.SCHEME_ANDROID_RESOURCE;

    private final Context context;
    private final List<TransferListener> transferListeners;
    private final DataSource baseDataSource;

    // Lazily initialized.
    @Nullable private DataSource fileDataSource;
    @Nullable private DataSource assetDataSource;
    @Nullable private DataSource contentDataSource;
    @Nullable private DataSource rtmpDataSource;
    @Nullable private DataSource udpDataSource;
    @Nullable private DataSource dataSchemeDataSource;
    @Nullable private DataSource rawResourceDataSource;

    @Nullable private DataSource dataSource;

    /**
     * Constructs a new instance, optionally configured to follow cross-protocol redirects.
     *
     * @param context A context.
     */
    public PurifiedDataSource(Context context, boolean allowCrossProtocolRedirects) {
        this(
                context,
                /* userAgent= */ null,
                PurifiedHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                PurifiedHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                allowCrossProtocolRedirects);
    }

    /**
     * Constructs a new instance, optionally configured to follow cross-protocol redirects.
     *
     * @param context A context.
     * @param userAgent The user agent that will be used when requesting remote data, or {@code null}
     *     to use the default user agent of the underlying platform.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *     to HTTPS and vice versa) are enabled when fetching remote data.
     */
    public PurifiedDataSource(
            Context context, @Nullable String userAgent, boolean allowCrossProtocolRedirects) {
        this(
                context,
                userAgent,
                PurifiedHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                PurifiedHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                allowCrossProtocolRedirects);
    }

    /**
     * Constructs a new instance, optionally configured to follow cross-protocol redirects.
     *
     * @param context A context.
     * @param userAgent The user agent that will be used when requesting remote data, or {@code null}
     *     to use the default user agent of the underlying platform.
     * @param connectTimeoutMillis The connection timeout that should be used when requesting remote
     *     data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param readTimeoutMillis The read timeout that should be used when requesting remote data, in
     *     milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *     to HTTPS and vice versa) are enabled when fetching remote data.
     */
    public PurifiedDataSource(
            Context context,
            @Nullable String userAgent,
            int connectTimeoutMillis,
            int readTimeoutMillis,
            boolean allowCrossProtocolRedirects) {
        this(
                context,
                new PurifiedHttpDataSource.Factory()
                        .setUserAgent(userAgent)
                        .setConnectTimeoutMs(connectTimeoutMillis)
                        .setReadTimeoutMs(readTimeoutMillis)
                        .setAllowCrossProtocolRedirects(allowCrossProtocolRedirects)
                        .createDataSource());
    }

    /**
     * Constructs a new instance that delegates to a provided {@link DataSource} for URI schemes other
     * than file, asset and content.
     *
     * @param context A context.
     * @param baseDataSource A {@link DataSource} to use for URI schemes other than file, asset and
     *     content. This {@link DataSource} should normally support at least http(s).
     */
    public PurifiedDataSource(Context context, DataSource baseDataSource) {
        this.context = context.getApplicationContext();
        this.baseDataSource = Assertions.checkNotNull(baseDataSource);
        transferListeners = new ArrayList<>();
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        Assertions.checkNotNull(transferListener);
        baseDataSource.addTransferListener(transferListener);
        transferListeners.add(transferListener);
        maybeAddListenerToDataSource(fileDataSource, transferListener);
        maybeAddListenerToDataSource(assetDataSource, transferListener);
        maybeAddListenerToDataSource(contentDataSource, transferListener);
        maybeAddListenerToDataSource(rtmpDataSource, transferListener);
        maybeAddListenerToDataSource(udpDataSource, transferListener);
        maybeAddListenerToDataSource(dataSchemeDataSource, transferListener);
        maybeAddListenerToDataSource(rawResourceDataSource, transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Assertions.checkState(dataSource == null);
        // Choose the correct source for the scheme.
        String scheme = dataSpec.uri.getScheme();
        if (Util.isLocalFileUri(dataSpec.uri)) {
            String uriPath = dataSpec.uri.getPath();
            if (uriPath != null && uriPath.startsWith("/android_asset/")) {
                dataSource = getAssetDataSource();
            } else {
                dataSource = getFileDataSource();
            }
        } else if (SCHEME_ASSET.equals(scheme)) {
            dataSource = getAssetDataSource();
        } else if (SCHEME_CONTENT.equals(scheme)) {
            dataSource = getContentDataSource();
        } else if (SCHEME_RTMP.equals(scheme)) {
            dataSource = getRtmpDataSource();
        } else if (SCHEME_UDP.equals(scheme)) {
            dataSource = getUdpDataSource();
        } else if (SCHEME_DATA.equals(scheme)) {
            dataSource = getDataSchemeDataSource();
        } else if (SCHEME_RAW.equals(scheme) || SCHEME_ANDROID_RESOURCE.equals(scheme)) {
            dataSource = getRawResourceDataSource();
        } else {
            dataSource = baseDataSource;
        }
        // Open the source and return.
        return dataSource.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return Assertions.checkNotNull(dataSource).read(buffer, offset, length);
    }

    @Override
    @Nullable
    public Uri getUri() {
        return dataSource == null ? null : dataSource.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return dataSource == null ? Collections.emptyMap() : dataSource.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            try {
                dataSource.close();
            } finally {
                dataSource = null;
            }
        }
    }

    private DataSource getUdpDataSource() {
        if (udpDataSource == null) {
            udpDataSource = new UdpDataSource();
            addListenersToDataSource(udpDataSource);
        }
        return udpDataSource;
    }

    private DataSource getFileDataSource() {
        if (fileDataSource == null) {
            fileDataSource = new FileDataSource();
            addListenersToDataSource(fileDataSource);
        }
        return fileDataSource;
    }

    private DataSource getAssetDataSource() {
        if (assetDataSource == null) {
            assetDataSource = new AssetDataSource(context);
            addListenersToDataSource(assetDataSource);
        }
        return assetDataSource;
    }

    private DataSource getContentDataSource() {
        if (contentDataSource == null) {
            contentDataSource = new ContentDataSource(context);
            addListenersToDataSource(contentDataSource);
        }
        return contentDataSource;
    }

    private DataSource getRtmpDataSource() {
        if (rtmpDataSource == null) {
            try {
                Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.rtmp.RtmpDataSource");
                rtmpDataSource = (DataSource) clazz.getConstructor().newInstance();
                addListenersToDataSource(rtmpDataSource);
            } catch (ClassNotFoundException e) {
                // Expected if the app was built without the RTMP extension.
                Log.w(TAG, "Attempting to play RTMP stream without depending on the RTMP extension");
            } catch (Exception e) {
                // The RTMP extension is present, but instantiation failed.
                throw new RuntimeException("Error instantiating RTMP extension", e);
            }
            if (rtmpDataSource == null) {
                rtmpDataSource = baseDataSource;
            }
        }
        return rtmpDataSource;
    }

    private DataSource getDataSchemeDataSource() {
        if (dataSchemeDataSource == null) {
            dataSchemeDataSource = new DataSchemeDataSource();
            addListenersToDataSource(dataSchemeDataSource);
        }
        return dataSchemeDataSource;
    }

    private DataSource getRawResourceDataSource() {
        if (rawResourceDataSource == null) {
            rawResourceDataSource = new RawResourceDataSource(context);
            addListenersToDataSource(rawResourceDataSource);
        }
        return rawResourceDataSource;
    }

    private void addListenersToDataSource(DataSource dataSource) {
        for (int i = 0; i < transferListeners.size(); i++) {
            dataSource.addTransferListener(transferListeners.get(i));
        }
    }

    private void maybeAddListenerToDataSource(
            @Nullable DataSource dataSource, TransferListener listener) {
        if (dataSource != null) {
            dataSource.addTransferListener(listener);
        }
    }
}
