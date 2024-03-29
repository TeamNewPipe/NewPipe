package org.schabi.newpipe.player.datasource;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.HlsDataSourceFactory;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSource;

import java.nio.charset.StandardCharsets;

/**
 * A {@link HlsDataSourceFactory} which allows playback of non-URI media HLS playlists for
 * {@link com.google.android.exoplayer2.source.hls.HlsMediaSource HlsMediaSource}s.
 *
 * <p>
 * If media requests are relative, the URI from which the manifest comes from (either the
 * manifest URI (preferred) or the master URI (if applicable)) must be returned, otherwise the
 * content will be not playable, as it will be an invalid URL, or it may be treat as something
 * unexpected, for instance as a file for
 * {@link com.google.android.exoplayer2.upstream.DefaultDataSource DefaultDataSource}s.
 * </p>
 *
 * <p>
 * See {@link #createDataSource(int)} for changes and implementation details.
 * </p>
 */
public final class NonUriHlsDataSourceFactory implements HlsDataSourceFactory {

    /**
     * Builder class of {@link NonUriHlsDataSourceFactory} instances.
     */
    public static final class Builder {
        private DataSource.Factory dataSourceFactory;
        private String playlistString;

        /**
         * Set the {@link DataSource.Factory} which will be used to create non manifest contents
         * {@link DataSource}s.
         *
         * @param dataSourceFactoryForNonManifestContents the {@link DataSource.Factory} which will
         *                                                be used to create non manifest contents
         *                                                {@link DataSource}s, which cannot be null
         */
        public void setDataSourceFactory(
                @NonNull final DataSource.Factory dataSourceFactoryForNonManifestContents) {
            this.dataSourceFactory = dataSourceFactoryForNonManifestContents;
        }

        /**
         * Set the HLS playlist which will be used for manifests requests.
         *
         * @param hlsPlaylistString the string which correspond to the response of the HLS
         *                          manifest, which cannot be null or empty
         */
        public void setPlaylistString(@NonNull final String hlsPlaylistString) {
            this.playlistString = hlsPlaylistString;
        }

        /**
         * Create a new {@link NonUriHlsDataSourceFactory} with the given data source factory and
         * the given HLS playlist.
         *
         * @return a {@link NonUriHlsDataSourceFactory}
         * @throws IllegalArgumentException if the data source factory is null or if the HLS
         * playlist string set is null or empty
         */
        @NonNull
        public NonUriHlsDataSourceFactory build() {
            if (dataSourceFactory == null) {
                throw new IllegalArgumentException(
                        "No DataSource.Factory valid instance has been specified.");
            }

            if (isNullOrEmpty(playlistString)) {
                throw new IllegalArgumentException("No HLS valid playlist has been specified.");
            }

            return new NonUriHlsDataSourceFactory(dataSourceFactory,
                    playlistString.getBytes(StandardCharsets.UTF_8));
        }
    }

    private final DataSource.Factory dataSourceFactory;
    private final byte[] playlistStringByteArray;

    /**
     * Create a {@link NonUriHlsDataSourceFactory} instance.
     *
     * @param dataSourceFactory       the {@link DataSource.Factory} which will be used to build
     *                                non manifests {@link DataSource}s, which must not be null
     * @param playlistStringByteArray a byte array of the HLS playlist, which must not be null
     */
    private NonUriHlsDataSourceFactory(@NonNull final DataSource.Factory dataSourceFactory,
                                       @NonNull final byte[] playlistStringByteArray) {
        this.dataSourceFactory = dataSourceFactory;
        this.playlistStringByteArray = playlistStringByteArray;
    }

    /**
     * Create a {@link DataSource} for the given data type.
     *
     * <p>
     * Contrary to {@link com.google.android.exoplayer2.source.hls.DefaultHlsDataSourceFactory
     * ExoPlayer's default implementation}, this implementation is not always using the
     * {@link DataSource.Factory} passed to the
     * {@link com.google.android.exoplayer2.source.hls.HlsMediaSource.Factory
     * HlsMediaSource.Factory} constructor, only when it's not
     * {@link C#DATA_TYPE_MANIFEST the manifest type}.
     * </p>
     *
     * <p>
     * This change allow playback of non-URI HLS contents, when the manifest is not a master
     * manifest/playlist (otherwise, endless loops should be encountered because the
     * {@link DataSource}s created for media playlists should use the master playlist response
     * instead).
     * </p>
     *
     * @param dataType the data type for which the {@link DataSource} will be used, which is one of
     *                 {@link C} {@code .DATA_TYPE_*} constants
     * @return a {@link DataSource} for the given data type
     */
    @NonNull
    @Override
    public DataSource createDataSource(final int dataType) {
        // The manifest is already downloaded and provided with playlistStringByteArray, so we
        // don't need to download it again and we can use a ByteArrayDataSource instead
        if (dataType == C.DATA_TYPE_MANIFEST) {
            return new ByteArrayDataSource(playlistStringByteArray);
        }

        return dataSourceFactory.createDataSource();
    }
}
