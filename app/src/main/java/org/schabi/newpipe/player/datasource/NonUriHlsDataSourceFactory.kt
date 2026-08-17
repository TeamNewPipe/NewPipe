@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.datasource

import androidx.media3.common.C
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty

/**
 * A [HlsDataSourceFactory] which allows playback of non-URI media HLS playlists for
 * [androidx.media3.exoplayer.hls.HlsMediaSource]s.
 *
 *
 * If media requests are relative, the URI from which the manifest comes from (either the
 * manifest URI (preferred) or the master URI (if applicable)) must be returned, otherwise the
 * content will be not playable, as it will be an invalid URL, or it may be treat as something
 * unexpected, for instance as a file for
 * [androidx.media3.datasource.DefaultDataSource]s.
 *
 *
 * See [createDataSource] for changes and implementation details.
 */
class NonUriHlsDataSourceFactory private constructor(
    private val dataSourceFactory: DataSource.Factory,
    private val playlistStringByteArray: ByteArray
) : HlsDataSourceFactory {

    /**
     * Builder class of [NonUriHlsDataSourceFactory] instances.
     */
    class Builder {
        private var dataSourceFactory: DataSource.Factory? = null
        private var playlistString: String? = null

        /**
         * Set the [DataSource.Factory] which will be used to create non manifest contents
         * [DataSource]s.
         *
         * @param dataSourceFactoryForNonManifestContents the [DataSource.Factory] which will
         * be used to create non manifest contents
         * [DataSource]s, which cannot be null
         */
        fun setDataSourceFactory(
            dataSourceFactoryForNonManifestContents: DataSource.Factory
        ): Builder {
            this.dataSourceFactory = dataSourceFactoryForNonManifestContents
            return this
        }

        /**
         * Set the HLS playlist which will be used for manifests requests.
         *
         * @param hlsPlaylistString the string which correspond to the response of the HLS
         * manifest, which cannot be null or empty
         */
        fun setPlaylistString(hlsPlaylistString: String): Builder {
            this.playlistString = hlsPlaylistString
            return this
        }

        /**
         * Create a new [NonUriHlsDataSourceFactory] with the given data source factory and
         * the given HLS playlist.
         *
         * @return a [NonUriHlsDataSourceFactory]
         * @throws IllegalArgumentException if the data source factory is null or if the HLS
         * playlist string set is null or empty
         */
        fun build(): NonUriHlsDataSourceFactory {
            val factory = dataSourceFactory
                ?: throw IllegalArgumentException("No DataSource.Factory valid instance has been specified.")

            if (isNullOrEmpty(playlistString)) {
                throw IllegalArgumentException("No HLS valid playlist has been specified.")
            }

            return NonUriHlsDataSourceFactory(
                factory,
                playlistString!!.toByteArray(StandardCharsets.UTF_8)
            )
        }
    }

    /**
     * Create a [DataSource] for the given data type.
     *
     *
     * Contrary to [androidx.media3.exoplayer.hls.DefaultHlsDataSourceFactory],
     * this implementation is not always using the
     * [DataSource.Factory] passed to the
     * [androidx.media3.exoplayer.hls.HlsMediaSource.Factory] constructor,
     * only when it's not [C.DATA_TYPE_MANIFEST].
     *
     *
     * This change allow playback of non-URI HLS contents, when the manifest is not a master
     * manifest/playlist (otherwise, endless loops should be encountered because the
     * [DataSource]s created for media playlists should use the master playlist response
     * instead).
     *
     * @param dataType the data type for which the [DataSource] will be used, which is one of
     * [C] `.DATA_TYPE_*` constants
     * @return a [DataSource] for the given data type
     */
    override fun createDataSource(dataType: Int): DataSource {
        // The manifest is already downloaded and provided with playlistStringByteArray, so we
        // don't need to download it again and we can use a ByteArrayDataSource instead
        return if (dataType == C.DATA_TYPE_MANIFEST) {
            ByteArrayDataSource(playlistStringByteArray)
        } else {
            dataSourceFactory.createDataSource()
        }
    }
}
