package org.schabi.newpipe.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.PlayerHolder

/** One connection shared by the visible chat tab and fullscreen panel. */
@OptIn(ExperimentalCoroutinesApi::class)
object LiveChatRepository {
    private const val MAX_AUTOMATIC_RETRIES = 3
    private const val RETRY_DELAY_MILLIS = 1_000L

    private data class Feed(
        val scope: CoroutineScope,
        val retries: MutableStateFlow<Int>,
        val messages: SharedFlow<Result<List<BulletCommentsInfoItem>>>
    )
    private val feeds = linkedMapOf<String, Feed>()

    @JvmStatic
    fun supports(info: StreamInfo): Boolean {
        if (info.serviceId != ServiceList.YouTube.serviceId) return false
        return info.isUpcoming || info.streamType == StreamType.LIVE_STREAM ||
            info.streamType == StreamType.AUDIO_LIVE_STREAM ||
            info.streamType == StreamType.POST_LIVE_STREAM
    }

    @Synchronized
    fun messages(serviceId: Int, url: String): SharedFlow<Result<List<BulletCommentsInfoItem>>> {
        val key = "$serviceId|$url"
        return feeds.getOrPut(key) {
            if (feeds.size >= 4) feeds.remove(feeds.keys.first())?.scope?.cancel()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val retries = MutableStateFlow(0)
            Feed(
                scope,
                retries,
                retries.flatMapLatest {
                    observe({ currentPlaybackPosition(url) }) {
                        NewPipe.getService(serviceId).getBulletCommentsExtractor(url)
                    }
                }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), replay = 0)
            )
        }.messages
    }

    @Synchronized
    fun retry(serviceId: Int, url: String) {
        feeds["$serviceId|$url"]?.retries?.let { it.value += 1 }
    }

    internal fun observe(
        positionProvider: suspend () -> Long? = { null },
        retryDelayMillis: Long = RETRY_DELAY_MILLIS,
        factory: () -> BulletCommentsExtractor
    ): Flow<Result<List<BulletCommentsInfoItem>>> = flow {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            var extractor: BulletCommentsExtractor? = null
            var retry = false
            try {
                extractor = factory()
                extractor.fetchPage()
                if (extractor.isDisabled) {
                    emit(Result.failure(IllegalStateException("Live chat unavailable")))
                    return@flow
                }
                extractor.initialPage
                while (currentCoroutineContext().isActive) {
                    positionProvider()?.let(extractor::setCurrentPlayPosition)
                    emit(Result.success(extractor.liveMessages.orEmpty()))
                    delay(1000)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(Result.failure(error))
                failures++
                retry = failures <= MAX_AUTOMATIC_RETRIES
            } finally {
                extractor?.disconnect()
            }
            if (!retry) return@flow
            delay(retryDelayMillis * failures)
        }
    }

    /** Media3 player state is main-thread-confined, even while extraction runs on IO. */
    private suspend fun currentPlaybackPosition(url: String): Long? {
        return withContext(Dispatchers.Main.immediate) {
            val player = PlayerHolder.getInstance().player.orElse(null)
                ?: return@withContext null
            val currentUrl = player.currentStreamInfo.map(StreamInfo::getUrl).orElse(null)
            if (currentUrl != url || player.exoPlayerIsNull()) return@withContext null
            player.exoPlayer.currentPosition.coerceAtLeast(0L)
        }
    }
}
