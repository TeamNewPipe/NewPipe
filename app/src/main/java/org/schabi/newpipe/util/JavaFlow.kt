package org.schabi.newpipe.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class JavaFlow<T>(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    interface OperatorCallback <T> {
        fun onStart() = Unit
        fun onCompletion(thr: Throwable?) = Unit
        fun onResult(result: T)
    }

    fun collect(
        flow: Flow<T>,
        operatorCallback: OperatorCallback<T>,
    ) {
        coroutineScope.launch {
            flow
                .onStart { operatorCallback.onStart() }
                .onCompletion { operatorCallback.onCompletion(it) }
                .collect { operatorCallback.onResult(it) }
        }
    }

    fun close() {
        coroutineScope.cancel()
    }
}
