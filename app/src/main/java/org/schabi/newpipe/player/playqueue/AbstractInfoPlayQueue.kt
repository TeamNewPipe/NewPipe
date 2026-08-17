package org.schabi.newpipe.player.playqueue

import android.util.Log
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem

abstract class AbstractInfoPlayQueue<T : ListInfo<out InfoItem>> : PlayQueue {
    protected var isInitial: Boolean = false
    private var isComplete: Boolean = false

    protected val serviceId: Int
    protected val baseUrl: String
    protected var nextPage: Page? = null

    @Transient
    private var fetchJob: Job? = null

    private val playQueueScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    constructor(info: T) : this(info, 0)

    constructor(info: T, index: Int) : this(
        info.serviceId,
        info.url,
        info.nextPage,
        info.relatedItems.filterIsInstance<StreamInfoItem>(),
        index
    )

    constructor(
        serviceId: Int,
        url: String,
        nextPage: Page?,
        streams: List<StreamInfoItem>,
        index: Int
    ) : super(index, streams.map { PlayQueueItem(it) }) {
        this.baseUrl = url
        this.nextPage = nextPage
        this.serviceId = serviceId

        this.isInitial = streams.isEmpty()
        this.isComplete = !isInitial && !Page.isValid(nextPage)
    }

    protected abstract fun getTag(): String

    override fun isComplete(): Boolean = isComplete

    protected fun handleResult(result: ListInfo<out InfoItem>) {
        isInitial = false
        if (!result.hasNextPage()) {
            isComplete = true
        }
        nextPage = result.nextPage

        append(result.relatedItems.filterIsInstance<StreamInfoItem>().map { PlayQueueItem(it) })
    }

    protected fun handleNextPageResult(result: ListExtractor.InfoItemsPage<out InfoItem>) {
        if (!result.hasNextPage()) {
            isComplete = true
        }
        nextPage = result.nextPage

        append(result.items.filterIsInstance<StreamInfoItem>().map { PlayQueueItem(it) })
    }

    protected fun handleError(e: Throwable) {
        Log.e(getTag(), "Error fetching more playlist, marking playlist as complete.", e)
        isComplete = true
        notifyChange()
    }

    override fun dispose() {
        super.dispose()
        fetchJob?.cancel()
        fetchJob = null
        playQueueScope.cancel()
    }

    protected fun startFetch(block: suspend CoroutineScope.() -> Unit) {
        if (isComplete || (fetchJob?.isActive == true)) {
            return
        }
        fetchJob = playQueueScope.launch(block = block)
    }
}
