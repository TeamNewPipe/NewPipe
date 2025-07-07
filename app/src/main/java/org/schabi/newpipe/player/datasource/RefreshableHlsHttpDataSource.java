package org.schabi.newpipe.player.datasource;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.RefreshableStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RefreshableHlsHttpDataSource extends LoggingHttpDataSource {

    private final String TAG =
            RefreshableHlsHttpDataSource.class.getSimpleName() + "@" + hashCode();
    private final RefreshableStream refreshableStream;
    private final String originalPlaylistUrl;
    private final Map<String, String> chunkUrlMap = new LinkedHashMap<>();
    private int readsCalled;
    private boolean isError;

    public RefreshableHlsHttpDataSource(final RefreshableStream refreshableStream) {
        this.refreshableStream = refreshableStream;
        originalPlaylistUrl = refreshableStream.initialUrl();
    }

    @SuppressWarnings("checkstyle:LineLength")
    public RefreshableHlsHttpDataSource(final RefreshableStream refreshableStream,
                                        @Nullable final String userAgent,
                                        final int connectTimeoutMillis,
                                        final int readTimeoutMillis,
                                        final boolean allowCrossProtocolRedirects,
                                        @Nullable final RequestProperties defaultRequestProperties) {
        super(userAgent,
              connectTimeoutMillis,
              readTimeoutMillis,
              allowCrossProtocolRedirects,
              defaultRequestProperties);
        this.refreshableStream = refreshableStream;
        originalPlaylistUrl = refreshableStream.initialUrl();
    }

    @Override
    public int read(@NonNull final byte[] buffer, final int offset, final int length)
            throws HttpDataSourceException {
        return super.read(buffer, offset, length);
    }

    @Override
    public long open(final DataSpec dataSpec) throws HttpDataSourceException {
        final var url = dataSpec.uri.toString();
        if (DEBUG) {
            Log.d(TAG, "called open(" + url + ")");
        }

        if (!url.contains(refreshableStream.playlistId())) {
            // TODO: throw error or no?
            if (DEBUG) {
                Log.e(TAG, "Playlist id does not match");
            }
        }
        return chunkUrlMap.isEmpty()
                ? openInternal(dataSpec)
                : openInternal(getUpdatedDataSpec(dataSpec));
    }

    private long openInternal(final DataSpec dataSpec) throws HttpDataSourceException {
        try {
            final var bytesToRead = super.open(dataSpec);
            if (DEBUG) {
                Log.d(TAG, "Bytes to read: " + bytesToRead);
            }
            isError = false; // if we got to this line there was no error
            return bytesToRead;
        } catch (final InvalidResponseCodeException e) {
            // TODO: This assumes SoundCloud returning 403 when playlist expires
            //  If we need to refresh playlists for other services at a later date then
            //  need to generalize this class
            if (isError || e.responseCode != 403) {
                // Use isError to prevent infinite loop if playlist expires, we replace signature
                // but then that one gives an error, and then we replace signature again, and so on
                // The expectation is that no error will be thrown on the first recursion
                throw e;
            }

            try {
                refreshPlaylist();
            } catch (final ExtractionException | IOException ex) {
                // TODO: better error here
                // TODO: so what happens when we throw exception here
                //  and this method gets called again in this class?
                //  if we want to prevent open being called again we need to throw a runtime
                throw new HttpDataSourceException("Error refreshing Hls playlist: "
                                                  + originalPlaylistUrl,
                                                  new IOException(ex), dataSpec,
                                                  HttpDataSourceException.TYPE_OPEN);
            }
            isError = true;
            // Use recursion to reuse error handling without code duplication
            return openInternal(getUpdatedDataSpec(dataSpec));
        }
    }

    private void refreshPlaylist() throws ExtractionException, IOException {
        if (DEBUG) {
            Log.d(TAG, "refreshPlaylist() - originalPlaylistUrl " + originalPlaylistUrl);
        }

        final var newPlaylistUrl = refreshableStream.fetchLatestUrl();

        if (DEBUG) {
            Log.d(TAG, "New playlist url " + newPlaylistUrl);
            Log.d(TAG, "Extracting new playlist Chunks");
        }
        final var newChunks = extractChunksFromPlaylist(newPlaylistUrl);

        if (!chunkUrlMap.isEmpty()) {
            updateChunkMap(chunkUrlMap, newChunks);
        }
        initializeChunkMappings(chunkUrlMap, newChunks);
    }

    private static void initializeChunkMappings(final Map<String, String> chunkMap,
                                                final List<String> newChunks) {
        for (int i = 0; i < newChunks.size(); ++i) {
            final var newUrl = newChunks.get(i);
            chunkMap.put(getBaseUrl(newUrl), newUrl);
        }
    }

    private static void updateChunkMap(final Map<String, String> chunkMap,
                                       final List<String> newChunks) throws IOException {
        if (chunkMap.size() != newChunks.size()) {
            throw new IOException("Error extracting chunks: chunks are not same size\n"
                                  + "Expected " + chunkMap.size()
                                  + " and got " + newChunks.size());
        }

        final var baseUrlIt = chunkMap.keySet().iterator();
        final var newChunkUrlIt = newChunks.iterator();
        while (baseUrlIt.hasNext()) {
            chunkMap.put(baseUrlIt.next(), newChunkUrlIt.next());
        }
    }

    private static String getBaseUrl(final String url) {
        final int idx = url.indexOf('?');
        return idx == -1 ? url : url.substring(0, idx);
    }

    // TODO: better name
    private DataSpec getUpdatedDataSpec(final DataSpec dataSpec) {
        final var currentUrl = dataSpec.uri.toString();
        if (DEBUG) {
            Log.d(TAG, "getUpdatedDataSpec(" + currentUrl + ')');
        }
        // Playlist has expired, so get mapping for new url
        final var baseUrl = getBaseUrl(currentUrl);

        if (baseUrl.equals(currentUrl)) {
            if (DEBUG) {
                Log.e(TAG, "Url has no query parameters");
            }
        }

        final var updatedUrl = chunkUrlMap.get(baseUrl);
        if (updatedUrl == null) {
            throw new IllegalStateException("baseUrl not found in mappings: " + baseUrl);
            // TODO: problemo
        }
        if (DEBUG) {
            Log.d(TAG, "updated url:" + updatedUrl);
        }
        return dataSpec.buildUpon()
                       .setUri(Uri.parse(updatedUrl))
                       .build();
    }

    /**
     * Extracts the chunks/segments from an m3u8 playlist using
     * ExoPlayer's {@link HlsPlaylistParser}.
     * @param playlistUrl url of m3u8 playlist to extract
     * @return Urls for all the chunks/segments in the playlist
     * @throws IOException If error extracting the chunks
     */
    private List<String> extractChunksFromPlaylist(final String playlistUrl)
            throws IOException {
        if (DEBUG) {
            Log.d(TAG, "extractChunksFromPlaylist(" + playlistUrl + ')');
        }
        final var chunks = new ArrayList<String>();
        final var parser = new HlsPlaylistParser();
        final var dataSpec = new DataSpec(Uri.parse(playlistUrl));
        final var httpDataSource = new LoggingHttpDataSource.Factory().createDataSource();

        // Adapted from ParsingLoadable.load()
        // DataSourceInputStream opens the data source internally on open()
        // It passes dataSpec to data source
        // httpDataSource is a DefaultHttpDataSource, and getUri will return dataSpec's uri
        // which == playlistUrl
        try (@SuppressWarnings("LocalCanBeFinal")
             var inputStream = new DataSourceInputStream(httpDataSource, dataSpec)) {
            inputStream.open();

            final var playlist =
                    parser.parse(Objects.requireNonNull(httpDataSource.getUri()), inputStream);

            if (!(playlist instanceof final HlsMediaPlaylist hlsMediaPlaylist)) {
                throw new IOException("Expected Hls playlist to be an HlsMediaPlaylist, but was a "
                        + playlist.getClass().getSimpleName());
            }

            for (final var segment : hlsMediaPlaylist.segments) {
                chunks.add(segment.url);
            }

            if (DEBUG) {
                Log.d(TAG, "Extracted " + chunks.size() + " chunks");
                chunks.stream().forEach(m -> Log.d(TAG, "Chunk " + m));
            }

            return chunks;
        } finally {
            httpDataSource.close();
        }
    }

    public static class Factory extends LoggingHttpDataSource.Factory {
        private final RefreshableStream refreshableStream;

        public Factory(final RefreshableStream refreshableStream) {
            this.refreshableStream = refreshableStream;
        }

        @NonNull
        @Override
        public HttpDataSource createDataSource() {
            return new RefreshableHlsHttpDataSource(refreshableStream,
                                                    userAgent,
                                                    connectTimeoutMs,
                                                    readTimeoutMs,
                                                    allowCrossProtocolRedirects,
                                                    defaultRequestProperties);
        }
    }
}
