package org.schabi.newpipe.player.playqueue

import java.io.Serializable
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.schabi.newpipe.player.playqueue.PlayQueueEvent.*

abstract class PlayQueue(index: Int, startWith: List<PlayQueueItem>) : Serializable {

    private val queueIndex = AtomicInteger(index)
    private val history = mutableListOf<PlayQueueItem>()

    private var backup: MutableList<PlayQueueItem>? = null
    protected var _streams = startWith.toMutableList()

    @Transient
    private var _eventBroadcast: MutableSharedFlow<PlayQueueEvent>? = null

    @Transient
    private var _broadcastReceiver: SharedFlow<PlayQueueEvent>? = null

    @Transient
    var isDisposed = false
        private set

    init {
        if (_streams.size > index) {
            history.add(_streams[index])
        }
    }

    open fun init() {
        val flow = MutableSharedFlow<PlayQueueEvent>(replay = 1)
        _eventBroadcast = flow
        _broadcastReceiver = flow.asSharedFlow()
        flow.tryEmit(InitEvent())
    }

    open fun dispose() {
        _eventBroadcast = null
        _broadcastReceiver = null
        isDisposed = true
    }

    abstract fun isComplete(): Boolean

    abstract fun fetch()

    var index: Int
        get() = queueIndex.get()

        @Synchronized
        set(index) {
            val oldIndex = this.index
            val newIndex: Int = when {
                index < 0 -> 0
                index < _streams.size -> index
                _streams.isEmpty() -> 0
                isComplete() -> index % _streams.size
                else -> _streams.size - 1
            }

            queueIndex.set(newIndex)

            if (oldIndex != newIndex) {
                history.add(_streams[newIndex])
            }

            broadcast(SelectEvent(oldIndex, newIndex))
        }

    val item: PlayQueueItem?
        get() = getItem(index)

    fun getItem(index: Int): PlayQueueItem? {
        if (index < 0 || index >= _streams.size) {
            return null
        }
        return _streams[index]
    }

    fun indexOf(item: PlayQueueItem): Int {
        return _streams.indexOf(item)
    }

    fun size(): Int = _streams.size

    val isEmpty: Boolean
        get() = _streams.isEmpty()

    val isShuffled: Boolean
        get() = backup != null

    fun getStreams(): List<PlayQueueItem> = _streams.toList()

    val broadcastReceiver: SharedFlow<PlayQueueEvent>?
        get() = _broadcastReceiver

    @Synchronized
    fun offsetIndex(offset: Int) {
        index += offset
    }

    @Synchronized
    fun notifyChange() {
        broadcast(AppendEvent(0))
    }

    @Synchronized
    fun append(items: List<PlayQueueItem>) {
        val itemList = items.toMutableList()

        if (isShuffled) {
            backup?.addAll(itemList)
            itemList.shuffle()
        }
        if (_streams.isNotEmpty() && _streams.last().isAutoQueued && !itemList[0].isAutoQueued) {
            _streams.removeAt(_streams.size - 1)
        }
        _streams.addAll(itemList)

        broadcast(AppendEvent(itemList.size))
    }

    fun enqueueNext(item: PlayQueueItem, skipIfSame: Boolean) {
        val currentIndex = index
        if (skipIfSame && item.isSameItem(getItem(currentIndex + 1))) {
            return
        }
        append(listOf(item))
        move(size() - 1, currentIndex + 1)
    }

    @Synchronized
    fun remove(index: Int) {
        if (index >= _streams.size || index < 0) {
            return
        }
        removeInternal(index)
        broadcast(RemoveEvent(index, this.index))
    }

    @Synchronized
    fun error() {
        val oldIndex = index
        queueIndex.incrementAndGet()
        if (_streams.size > index) {
            history.add(_streams[index])
        }
        broadcast(ErrorEvent(oldIndex, index))
    }

    private fun removeInternal(removeIndex: Int) {
        val currentIndex = queueIndex.get()
        val size = size()

        if (currentIndex > removeIndex) {
            queueIndex.decrementAndGet()
        } else if (currentIndex >= size) {
            queueIndex.set(currentIndex % (size - 1))
        } else if (currentIndex == removeIndex && currentIndex == size - 1) {
            queueIndex.set(0)
        }

        backup?.let { b ->
            getItem(removeIndex)?.let { b.remove(it) }
        }

        val removedItem = _streams.removeAt(removeIndex)
        history.remove(removedItem)
        if (_streams.size > index) {
            history.add(_streams[index])
        }
    }

    @Synchronized
    fun move(source: Int, target: Int) {
        if (source < 0 || target < 0) return
        if (source >= _streams.size || target >= _streams.size) return

        val current = index
        if (source == current) {
            queueIndex.set(target)
        } else if (source < current && target >= current) {
            queueIndex.decrementAndGet()
        } else if (source > current && target <= current) {
            queueIndex.incrementAndGet()
        }

        val playQueueItem = _streams.removeAt(source)
        playQueueItem.isAutoQueued = false
        _streams.add(target, playQueueItem)
        broadcast(MoveEvent(source, target))
    }

    @Synchronized
    fun setRecovery(index: Int, position: Long) {
        if (index < 0 || index >= _streams.size) return
        _streams[index].recoveryPosition = position
        broadcast(RecoveryEvent(index, position))
    }

    @Synchronized
    fun unsetRecovery(index: Int) {
        setRecovery(index, PlayQueueItem.RECOVERY_UNSET)
    }

    @Synchronized
    fun shuffle() {
        if (backup == null) {
            backup = _streams.toMutableList()
        }
        if (size() <= 2) return

        val originalIndex = index
        val currentItem = item ?: return

        _streams.shuffle()
        _streams.remove(currentItem)
        _streams.add(0, currentItem)
        queueIndex.set(0)

        history.add(currentItem)
        broadcast(ReorderEvent(originalIndex, 0))
    }

    @Synchronized
    fun unshuffle() {
        val currentBackup = backup ?: return
        val originIndex = index
        val current = item

        _streams = currentBackup.toMutableList()
        backup = null

        val newIndex = _streams.indexOf(current)
        index = if (newIndex != -1) newIndex else 0
        if (_streams.size > index) {
            history.add(_streams[index])
        }

        broadcast(ReorderEvent(originIndex, index))
    }

    @Synchronized
    fun previous(): Boolean {
        if (history.size <= 1) return false
        history.removeAt(history.size - 1)
        val last = history.removeAt(history.size - 1)
        index = indexOf(last)
        return true
    }

    fun equalStreams(other: PlayQueue?): Boolean {
        if (other == null || size() != other.size()) return false
        for (i in 0 until size()) {
            if (!_streams[i].isSameItem(other.getItem(i))) return false
        }
        return true
    }

    fun equalStreamsAndIndex(other: PlayQueue?): Boolean {
        return equalStreams(other) && other?.index == index
    }

    private fun broadcast(event: PlayQueueEvent) {
        _eventBroadcast?.tryEmit(event)
    }

    companion object {
        @JvmField
        val DEBUG = org.schabi.newpipe.DebugConstants.DEBUG
    }
}
