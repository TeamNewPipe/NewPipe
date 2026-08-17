package org.schabi.newpipe.local.feed.service

import androidx.annotation.StringRes
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.IdleEvent

object FeedEventManager {
    private val stateFlow = MutableStateFlow<Event>(IdleEvent)
    private var ignoreUpstream = AtomicBoolean()

    fun postEvent(event: Event) {
        stateFlow.value = event
    }

    fun events(): Flow<Event> {
        return stateFlow.filter { !ignoreUpstream.get() }
    }

    fun reset() {
        ignoreUpstream.set(true)
        postEvent(IdleEvent)
        ignoreUpstream.set(false)
    }

    sealed class Event {
        data object IdleEvent : Event()
        data class ProgressEvent(val currentProgress: Int = -1, val maxProgress: Int = -1, @StringRes val progressMessage: Int = 0) : Event() {
            constructor(@StringRes progressMessage: Int) : this(-1, -1, progressMessage)
        }

        data class SuccessResultEvent(val itemsErrors: List<Throwable> = emptyList()) : Event()
        data class ErrorResultEvent(val error: Throwable) : Event()
    }
}
