package org.schabi.newpipe.player.helper;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.common.base.Predicate;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NiconicoLiveHttpDataSource extends PurifiedHttpDataSource {
    private static final long FETCH_INTERVAL = 50000000; //disable
    private final String liveUrl;
    private static final Map<String, Long> fetchHistory = new HashMap<>();
    private static String currentKey;
    private boolean isFetching = false;

    public static class Factory implements HttpDataSource.Factory {

        private final RequestProperties defaultRequestProperties;
        private final String url;

        @Nullable
        private TransferListener transferListener;
        @Nullable private Predicate<String> contentTypePredicate;
        @Nullable private String userAgent;
        private int connectTimeoutMs;
        private int readTimeoutMs;
        private boolean allowCrossProtocolRedirects;
        private boolean keepPostFor302Redirects;

        /** Creates an instance. */
        public Factory(String url) {
            if(url.equals("")){
                throw(new RuntimeException("Build NicoNico live source failed. This should never happen."));
            }
            defaultRequestProperties = new RequestProperties();
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS;
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS;
            this.url = url;
        }

        @Override
        public final NiconicoLiveHttpDataSource.Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties);
            return this;
        }

        /**
         * Sets the user agent that will be used.
         *
         * <p>The default is {@code null}, which causes the default user agent of the underlying
         * platform to be used.
         *
         * @param userAgent The user agent that will be used, or {@code null} to use the default user
         *     agent of the underlying platform.
         * @return This factory.
         */
        public NiconicoLiveHttpDataSource.Factory setUserAgent(@Nullable String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Sets the connect timeout, in milliseconds.
         *
         * <p>The default is {@link PurifiedHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS}.
         *
         * @param connectTimeoutMs The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        public NiconicoLiveHttpDataSource.Factory setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        /**
         * Sets the read timeout, in milliseconds.
         *
         * <p>The default is {@link PurifiedHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS}.
         *
         * @param readTimeoutMs The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        public NiconicoLiveHttpDataSource.Factory setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        /**
         * Sets whether to allow cross protocol redirects.
         *
         * <p>The default is {@code false}.
         *
         * @param allowCrossProtocolRedirects Whether to allow cross protocol redirects.
         * @return This factory.
         */
        public NiconicoLiveHttpDataSource.Factory setAllowCrossProtocolRedirects(boolean allowCrossProtocolRedirects) {
            this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
            return this;
        }

        /**
         * Sets a content type {@link Predicate}. If a content type is rejected by the predicate then a
         * {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
         * PurifiedHttpDataSource#open(DataSpec)}.
         *
         * <p>The default is {@code null}.
         *
         * @param contentTypePredicate The content type {@link Predicate}, or {@code null} to clear a
         *     predicate that was previously set.
         * @return This factory.
         */
        public NiconicoLiveHttpDataSource.Factory setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
            this.contentTypePredicate = contentTypePredicate;
            return this;
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
        public NiconicoLiveHttpDataSource.Factory setTransferListener(@Nullable TransferListener transferListener) {
            this.transferListener = transferListener;
            return this;
        }

        /**
         * Sets whether we should keep the POST method and body when we have HTTP 302 redirects for a
         * POST request.
         */
        public NiconicoLiveHttpDataSource.Factory setKeepPostFor302Redirects(boolean keepPostFor302Redirects) {
            this.keepPostFor302Redirects = keepPostFor302Redirects;
            return this;
        }

        @Override
        public NiconicoLiveHttpDataSource createDataSource() {
            NiconicoLiveHttpDataSource dataSource =
                    new NiconicoLiveHttpDataSource(
                            userAgent,
                            connectTimeoutMs,
                            readTimeoutMs,
                            allowCrossProtocolRedirects,
                            defaultRequestProperties,
                            contentTypePredicate,
                            keepPostFor302Redirects,
                            url);
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return dataSource;
        }
    }
    NiconicoLiveHttpDataSource(@Nullable String userAgent, int connectTimeoutMillis, int readTimeoutMillis, boolean allowCrossProtocolRedirects, @Nullable RequestProperties defaultRequestProperties
            , @Nullable Predicate<String> contentTypePredicate, boolean keepPostFor302Redirects, String liveUrl) {
        super(userAgent, connectTimeoutMillis, readTimeoutMillis, allowCrossProtocolRedirects, defaultRequestProperties, contentTypePredicate, keepPostFor302Redirects);
        this.liveUrl = liveUrl;
    }
    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException
    {
//        String fetchUrl = dataSpec.uri.toString();
//        int type = 0;
//        List<String> anonStrings = Arrays.asList("anonymous-user-", "anonymous_user_", "ht2_nicolive=");
////        System.out.println("Start fetching: " + new Date().toString() + " " + fetchUrl + " isFetching: " + isFetching);
////        System.out.println("Current key: " + currentKey + " " + fetchHistory.entrySet().toString());
//        String fetchKey;
//        if (fetchUrl.contains(anonStrings.get(0))){
//            fetchKey = fetchUrl.split(anonStrings.get(0))[1].split("&")[0];
//        } else if (fetchUrl.contains(anonStrings.get(1))){
//            fetchKey = fetchUrl.split(anonStrings.get(1))[1].split("&")[0];
//            type = 1;
//        } else{
//            fetchKey = fetchUrl.split(anonStrings.get(2))[1].split("&")[0];
//            type = 2;
//        }
//
//        if(currentKey == null){
//            currentKey = fetchKey;
//        }
//        Long currentTime = new Date().getTime();
//        if(!fetchHistory.containsKey(currentKey)){
//            fetchHistory.put(currentKey, currentTime);
//        } else if (!isFetching && currentTime - fetchHistory.get(currentKey) > FETCH_INTERVAL && !fetchUrl.contains("playlist.m3u8")) {
//            // start a new thread and fetch the new key
////            int finalType = type;
////            new Thread(() -> {
////                try {
//////                    System.out.println("Start fetching new key: " + new Date().toString());
////                    isFetching = true;
////                    currentKey = PlayerDataSource.getNicoLiveUrl(liveUrl).split(anonStrings.get(finalType))[1].split("&")[0];
////                    fetchHistory.put(String.valueOf(currentKey), currentTime);
////                    isFetching = false;
//////                    System.out.println("End fetching new key: " + new Date().toString());
////                } catch (ParsingException | IOException | ReCaptchaException | JsonParserException e) {
////                    throw new RuntimeException(e);
////                }
////            }).start();
//        }
//        String newUrl = fetchUrl.replace(fetchKey, currentKey);
//        System.out.println("End fetching: " + new Date().toString() + " " + newUrl);
        return super.open(dataSpec);
    }
}
