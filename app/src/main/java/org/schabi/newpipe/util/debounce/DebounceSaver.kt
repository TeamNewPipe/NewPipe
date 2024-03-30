package org.schabi.newpipe.util.debounce

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DebounceSaver(private val saveDebounceMillis: Long, private val debounceSavable: DebounceSavable) {
    val debouncedSaveSignal: PublishSubject<Long?>?

    // Has the object been modified
    private val isModified: AtomicBoolean?

    /**
     * Creates a new `DebounceSaver`.
     *
     * @param saveDebounceMillis    Save the object milliseconds later after the last change
     * occurred.
     * @param debounceSavable       The object containing data to be saved.
     */
    init {
        debouncedSaveSignal = PublishSubject.create()
        isModified = AtomicBoolean()
    }

    /**
     * Creates a new `DebounceSaver`. Save the object 10 seconds later after the last change
     * occurred.
     *
     * @param debounceSavable       The object containing data to be saved.
     */
    constructor(debounceSavable: DebounceSavable) : this(DEFAULT_SAVE_DEBOUNCE_MILLIS, debounceSavable)

    fun getIsModified(): Boolean {
        return isModified!!.get()
    }

    fun setNoChangesToSave() {
        isModified!!.set(false)
    }

    val debouncedSaver: Disposable
        get() = debouncedSaveSignal
                .debounce(saveDebounceMillis, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ ignored: Long? -> debounceSavable.saveImmediate() }) { throwable: Throwable? ->
                    debounceSavable.showError(ErrorInfo(throwable!!,
                            UserAction.SOMETHING_ELSE, "Debounced saver"))
                }

    fun setHasChangesToSave() {
        if (isModified == null || debouncedSaveSignal == null) {
            return
        }
        isModified.set(true)
        debouncedSaveSignal.onNext(System.currentTimeMillis())
    }

    companion object {
        // Default 10 seconds
        private const val DEFAULT_SAVE_DEBOUNCE_MILLIS: Long = 10000
    }
}
