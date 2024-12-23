/* NewPlayer
 *
 * @author Christian Schabesberger
 *
 * Copyright (C) NewPipe e.V. 2024 <code(at)newpipe-ev.de>
 *
 * NewPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.newpipe.newplayer.testapp

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.newpipe.newplayer.NewPlayer
import net.newpipe.newplayer.NewPlayerImpl
import net.newpipe.newplayer.data.AudioStreamTrack
import net.newpipe.newplayer.data.Chapter
import net.newpipe.newplayer.data.Stream
import net.newpipe.newplayer.data.Subtitle
import net.newpipe.newplayer.data.VideoStreamTrack
import net.newpipe.newplayer.repository.CachingRepository
import net.newpipe.newplayer.repository.MediaRepository
import net.newpipe.newplayer.repository.PrefetchingRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.media_ccc.extractors.MediaCCCStreamExtractor
import org.schabi.newpipe.extractor.services.media_ccc.extractors.data.MediaCCCRecording
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NewPlayerComponent {
    @Provides
    @Singleton
    fun provideNewPlayer(app: Application): NewPlayer {
        val player = NewPlayerImpl(
            app = app,
            repository = PrefetchingRepository(CachingRepository(MediaCCCTestRepository())),
            notificationIcon = IconCompat.createWithResource(app, net.newpipe.newplayer.R.drawable.new_player_tiny_icon),
            playerActivityClass = MainActivity::class.java,
            // rescueStreamFault = …
        )
        if (app is App) {
            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    player.errorFlow.collect { e ->
                        Log.e("NewPlayerException", e.stackTraceToString())
                    }
                }
            }
        }
        return player
    }
}

class TestMediaRepository() : MediaRepository {
    private val client = OkHttpClient()

    override fun getRepoInfo() =
        MediaRepository.RepoMetaInfo(canHandleTimestampedLinks = true, pullsDataFromNetwork = true)

    @OptIn(UnstableApi::class)
    override suspend fun getMetaInfo(item: String): MediaMetadata =
        MediaMetadata.Builder()
            .setTitle("BGP and the rule of bla")
            .setArtist("mr BGP")
            .setArtworkUri(Uri.parse("https://static.media.ccc.de/media/congress/2017/9072-hd.jpg"))
            .setDurationMs(
                1871L * 1000L
            )
            .build()

    override suspend fun getStreams(item: String): List<Stream> {
        return listOf(
            Stream(
                item = "bgp",
                streamUri = Uri.parse("https://cdn.media.ccc.de/congress/2017/h264-hd/34c3-9072-eng-BGP_and_the_Rule_of_Custom.mp4"),
                mimeType = null,
                streamTracks = listOf(
                    AudioStreamTrack(
                        bitrate = 480000,
                        fileFormat = "MPEG4",
                        language = "en"
                    ),
                    VideoStreamTrack(
                        width = 1920,
                        height = 1080,
                        frameRate = 25,
                        fileFormat = "MPEG4"
                    )
                )
            )
        )
    }

    override suspend fun getSubtitles(item: String) =
        emptyList<Subtitle>()

    override suspend fun getPreviewThumbnail(item: String, timestampInMs: Long): Bitmap? {

        val templateUrl = "https://static.media.ccc.de/media/congress/2017/9072-hd.jpg"

        val thumbnailId = (timestampInMs / (10 * 1000)) + 1

        if (getPreviewThumbnailsInfo(item).count < thumbnailId) {
            return null
        }

        val thumbUrl = String.format(templateUrl, thumbnailId)

        val bitmap = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(thumbUrl).build()
            val response = client.newCall(request).execute()
            try {
                val responseBody = response.body
                val bitmap = BitmapFactory.decodeStream(responseBody?.byteStream())
                return@withContext bitmap
            } catch (e: Exception) {
                return@withContext null
            }
        }

        return bitmap
    }

    override suspend fun getPreviewThumbnailsInfo(item: String) =
        MediaRepository.PreviewThumbnailsInfo(0, 0)

    override suspend fun getChapters(item: String) =
        listOf<Chapter>()

    override suspend fun getTimestampLink(item: String, timestampInSeconds: Long) =
        ""
}

class MediaCCCTestRepository() : MediaRepository {
    private val client = OkHttpClient()

    private val service = ServiceList.MediaCCC

    suspend fun fetchPage(item: String): MediaCCCStreamExtractor {
        return withContext(Dispatchers.IO) {
            // TODO: handle MediaCCCLiveStreamExtractor as well
            val extractor = service.getStreamExtractor(item) as MediaCCCStreamExtractor
            extractor.fetchPage()
            extractor
        }
    }

    override fun getRepoInfo() =
        MediaRepository.RepoMetaInfo(canHandleTimestampedLinks = true, pullsDataFromNetwork = true)

    @OptIn(UnstableApi::class)
    override suspend fun getMetaInfo(item: String): MediaMetadata {
        val extractor = fetchPage(item)
        return MediaMetadata.Builder().apply {
            setTitle(extractor.name)
            setArtist(extractor.subChannelName)
            setDurationMs(
                extractor.length * 1000L
            )
            extractor.thumbnails.firstOrNull()?.url?.let {
                setArtworkUri(Uri.parse(it))
            }
        }.build()
    }

    override suspend fun getStreams(item: String): List<Stream> {
        val extractor = fetchPage(item)
        return extractor.recordings.filterIsInstance<MediaCCCRecording.Video>()
            .filter { it.recordingType == MediaCCCRecording.VideoType.MAIN }
            .map { track ->
                Stream(
                    item = item,
                    streamUri = Uri.parse(track.url),
                    streamTracks =
                    listOf(
                        VideoStreamTrack(
                            width = track.width,
                            height = track.height,
                            fileFormat = track.mimeType
                        ),
                    ) +
                        // one audio track per language
                        // (TODO: probably don’t need to attach the audio track here?)
                        track.languages.map { language ->
                            AudioStreamTrack(
                                // TODO: should we pass the Locale instead??
                                language = language.language,
                                fileFormat = track.mimeType,
                                // TODO: that’s something ExoPlayer should determine for us,
                                // we don’t know that from the metadata
                                bitrate = 44100,
                            )
                        }
                )
            }
    }

    override suspend fun getSubtitles(item: String) =
        emptyList<Subtitle>()

    override suspend fun getPreviewThumbnail(item: String, timestampInMs: Long): Bitmap? {
        val extractor = fetchPage(item)

        val templateUrl = extractor.thumbnails.firstOrNull()?.url ?: return null

        val thumbnailId = (timestampInMs / (10 * 1000)) + 1

        if (getPreviewThumbnailsInfo(item).count < thumbnailId) {
            return null
        }

        val thumbUrl = String.format(templateUrl, thumbnailId)

        val bitmap = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(thumbUrl).build()
            val response = client.newCall(request).execute()
            try {
                val responseBody = response.body
                val bitmap = BitmapFactory.decodeStream(responseBody?.byteStream())
                return@withContext bitmap
            } catch (e: Exception) {
                return@withContext null
            }
        }

        return bitmap
    }

    override suspend fun getPreviewThumbnailsInfo(item: String) =
        MediaRepository.PreviewThumbnailsInfo(1, 0)

    override suspend fun getChapters(item: String) =
        listOf<Chapter>()

    override suspend fun getTimestampLink(item: String, timestampInSeconds: Long) =
        ""
}
