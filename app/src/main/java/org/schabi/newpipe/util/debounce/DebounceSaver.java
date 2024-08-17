package org.schabi.newpipe.util.debounce;

import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class DebounceSaver {

    private final long saveDebounceMillis;

    private final PublishSubject<Long> debouncedSaveSignal;

    private final DebounceSavable debounceSavable;

    // Has the object been modified
    private final AtomicBoolean isModified;

    // Default 10 seconds
    private static final long DEFAULT_SAVE_DEBOUNCE_MILLIS = 10000;


    /**
     * Creates a new {@code DebounceSaver}.
     *
     * @param saveDebounceMillis    Save the object milliseconds later after the last change
     *                              occurred.
     * @param debounceSavable       The object containing data to be saved.
     */
    public DebounceSaver(final long saveDebounceMillis, final DebounceSavable debounceSavable) {
        this.saveDebounceMillis = saveDebounceMillis;
        debouncedSaveSignal = PublishSubject.create();
        this.debounceSavable = debounceSavable;
        this.isModified = new AtomicBoolean();
    }

    /**
     * Creates a new {@code DebounceSaver}. Save the object 10 seconds later after the last change
     * occurred.
     *
     * @param debounceSavable       The object containing data to be saved.
     */
    public DebounceSaver(final DebounceSavable debounceSavable) {
        this(DEFAULT_SAVE_DEBOUNCE_MILLIS, debounceSavable);
    }

    public boolean getIsModified() {
        return isModified.get();
    }

    public void setNoChangesToSave() {
        isModified.set(false);
    }

    public PublishSubject<Long> getDebouncedSaveSignal() {
        return debouncedSaveSignal;
    }

    public Disposable getDebouncedSaver() {
        return debouncedSaveSignal
                .debounce(saveDebounceMillis, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> debounceSavable.saveImmediate(), throwable ->
                        debounceSavable.showError(new ErrorInfo(throwable,
                                UserAction.SOMETHING_ELSE, "Debounced saver")));
    }

    public void setHasChangesToSave() {
        if (isModified == null || debouncedSaveSignal == null) {
            return;
        }

        isModified.set(true);
        debouncedSaveSignal.onNext(System.currentTimeMillis());
    }
}
