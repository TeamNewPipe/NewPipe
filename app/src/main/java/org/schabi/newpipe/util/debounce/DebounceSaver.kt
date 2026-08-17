package org.schabi.newpipe.util.debounce

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import java.util.concurrent.atomic.AtomicBoolean

class DebounceSaver(
    private val saveDebounceMillis: Long = DEFAULT_SAVE_DEBOUNCE_MILLIS,
    private val debounceSavable: DebounceSavable
) {
    private val _debouncedSaveSignal = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private val isModified = AtomicBoolean(false)

    fun getIsModified() = isModified.get()

    fun setNoChangesToSave() {
        isModified.set(false)
    }

    fun start(scope: CoroutineScope): Job {
        return _debouncedSaveSignal
            .debounce(saveDebounceMillis)
            .onEach {
                debounceSavable.saveImmediate()
            }
            .catch { throwable ->
                debounceSavable.showError(
                    ErrorInfo(throwable, UserAction.SOMETHING_ELSE, "Debounced saver")
                )
            }
            .launchIn(scope)
    }

    fun setHasChangesToSave() {
        isModified.set(true)
        _debouncedSaveSignal.tryEmit(System.currentTimeMillis())
    }

    companion object {
        private const val DEFAULT_SAVE_DEBOUNCE_MILLIS = 10000L
    }
}
