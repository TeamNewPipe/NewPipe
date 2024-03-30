package org.schabi.newpipe.player.playqueue

import android.util.Log
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.function.Function
import java.util.stream.Collectors

abstract class AbstractInfoPlayQueue<T : ListInfo<out InfoItem?>?> protected constructor(val serviceId: Int,
                                                                                         val baseUrl: String?,
                                                                                         var nextPage: Page?,
                                                                                         streams: List<StreamInfoItem>,
                                                                                         index: Int) : PlayQueue(index, extractListItems(streams)) {
    var isInitial: Boolean
    override var isComplete: Boolean
        private set

    @Transient
    private var fetchReactor: Disposable? = null

    protected constructor(info: T) : this(info!!.getServiceId(), info.getUrl(), info.getNextPage(),
            info.getRelatedItems()
                    .stream()
                    .filter({ obj: Any? -> StreamInfoItem::class.java.isInstance(obj) })
                    .map<StreamInfoItem>({ obj: Any? -> StreamInfoItem::class.java.cast(obj) })
                    .collect(Collectors.toList<StreamInfoItem>()),
            0)

    init {
        isInitial = streams.isEmpty()
        isComplete = !isInitial && !Page.isValid(nextPage)
    }

    protected abstract val tag: String
    val headListObserver: SingleObserver<T>
        get() {
            return object : SingleObserver<T> {
                public override fun onSubscribe(d: Disposable) {
                    if (isComplete || !isInitial || ((fetchReactor != null
                                    && !fetchReactor!!.isDisposed()))) {
                        d.dispose()
                    } else {
                        fetchReactor = d
                    }
                }

                public override fun onSuccess(result: T) {
                    isInitial = false
                    if (!result!!.hasNextPage()) {
                        isComplete = true
                    }
                    nextPage = result.getNextPage()
                    append(extractListItems(result.getRelatedItems()
                            .stream()
                            .filter({ obj: Any? -> StreamInfoItem::class.java.isInstance(obj) })
                            .map({ obj: Any? -> StreamInfoItem::class.java.cast(obj) })
                            .collect(Collectors.toList())))
                    fetchReactor!!.dispose()
                    fetchReactor = null
                }

                public override fun onError(e: Throwable) {
                    Log.e(tag, "Error fetching more playlist, marking playlist as complete.", e)
                    isComplete = true
                    notifyChange()
                }
            }
        }
    val nextPageObserver: SingleObserver<InfoItemsPage<out InfoItem>>
        get() {
            return object : SingleObserver<InfoItemsPage<out InfoItem?>> {
                public override fun onSubscribe(d: Disposable) {
                    if (isComplete || isInitial || ((fetchReactor != null
                                    && !fetchReactor!!.isDisposed()))) {
                        d.dispose()
                    } else {
                        fetchReactor = d
                    }
                }

                public override fun onSuccess(
                        result: InfoItemsPage<out InfoItem?>) {
                    if (!result.hasNextPage()) {
                        isComplete = true
                    }
                    nextPage = result.getNextPage()
                    append(extractListItems(result.getItems()
                            .stream()
                            .filter({ obj: Any? -> StreamInfoItem::class.java.isInstance(obj) })
                            .map({ obj: Any? -> StreamInfoItem::class.java.cast(obj) })
                            .collect(Collectors.toList())))
                    fetchReactor!!.dispose()
                    fetchReactor = null
                }

                public override fun onError(e: Throwable) {
                    Log.e(tag, "Error fetching more playlist, marking playlist as complete.", e)
                    isComplete = true
                    notifyChange()
                }
            }
        }

    public override fun dispose() {
        super.dispose()
        if (fetchReactor != null) {
            fetchReactor!!.dispose()
        }
        fetchReactor = null
    }

    companion object {
        private fun extractListItems(infoItems: List<StreamInfoItem>): List<PlayQueueItem?> {
            return infoItems.stream().map(Function({ item: StreamInfoItem -> PlayQueueItem(item) })).collect(Collectors.toList())
        }
    }
}
