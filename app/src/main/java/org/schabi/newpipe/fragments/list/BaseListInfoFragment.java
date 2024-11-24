package org.schabi.newpipe.fragments.list;

import static org.schabi.newpipe.extractor.ServiceList.SoundCloud;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.views.NewPipeRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class BaseListInfoFragment<I extends InfoItem, L extends ListInfo<I>>
        extends BaseListFragment<L, ListExtractor.InfoItemsPage<I>> {
    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private final UserAction errorUserAction;
    protected L currentInfo;
    protected Page currentNextPage;
    protected Disposable currentWorker;

    protected BaseListInfoFragment(final UserAction errorUserAction) {
        this.errorUserAction = errorUserAction;
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        setTitle(name);
        showListFooter(hasMoreItems());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            if (hasMoreItems() && !infoListAdapter.getItemsList().isEmpty()) {
                loadMoreItems();
            } else {
                doInitialLoadLogic();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (currentWorker != null) {
            currentWorker.dispose();
            currentWorker = null;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(currentInfo);
        objectsToSave.add(currentNextPage);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        currentInfo = (L) savedObjects.poll();
        currentNextPage = (Page) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (DEBUG) {
            Log.d(TAG, "doInitialLoadLogic() called");
        }
        if (currentInfo == null) {
            startLoading(false);
        } else {
            handleResult(currentInfo);
        }
    }

    /**
     * Implement the logic to load the info from the network.<br/>
     * You can use the default implementations from {@link org.schabi.newpipe.util.ExtractorHelper}.
     *
     * @param forceLoad allow or disallow the result to come from the cache
     * @return Rx {@link Single} containing the {@link ListInfo}
     */
    protected abstract Single<L> loadResult(boolean forceLoad);

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        showListFooter(false);
        infoListAdapter.clearStreamItemList();

        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        currentWorker = loadResult(forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@NonNull final L result) -> {
                    isLoading.set(false);
                    currentInfo = result;
                    currentNextPage = result.getNextPage();
                    handleResult(result);
                }, throwable ->
                        showError(new ErrorInfo(throwable, errorUserAction,
                                "Start loading: " + url, serviceId)));
    }

    /**
     * Implement the logic to load more items.
     * <p>You can use the default implementations
     * from {@link org.schabi.newpipe.util.ExtractorHelper}.</p>
     *
     * @return Rx {@link Single} containing the {@link ListExtractor.InfoItemsPage}
     */
    protected abstract Single<ListExtractor.InfoItemsPage<I>> loadMoreItemsLogic();

    @Override
    protected void loadMoreItems() {
        isLoading.set(true);

        if (currentWorker != null) {
            currentWorker.dispose();
        }

        forbidDownwardFocusScroll();

        currentWorker = loadMoreItemsLogic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(this::allowDownwardFocusScroll)
                .subscribe(infoItemsPage -> {
                    isLoading.set(false);
                    handleNextItems(infoItemsPage);
                }, (@NonNull Throwable throwable) ->
                        dynamicallyShowErrorPanelOrSnackbar(new ErrorInfo(throwable,
                                errorUserAction, "Loading more items: " + url, serviceId)));
    }

    private void forbidDownwardFocusScroll() {
        if (itemsList instanceof NewPipeRecyclerView) {
            ((NewPipeRecyclerView) itemsList).setFocusScrollAllowed(false);
        }
    }

    private void allowDownwardFocusScroll() {
        if (itemsList instanceof NewPipeRecyclerView) {
            ((NewPipeRecyclerView) itemsList).setFocusScrollAllowed(true);
        }
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage<I> result) {
        super.handleNextItems(result);

        currentNextPage = result.getNextPage();
        infoListAdapter.addInfoItemList(result.getItems());

        showListFooter(hasMoreItems());

        if (!result.getErrors().isEmpty()) {
            dynamicallyShowErrorPanelOrSnackbar(new ErrorInfo(result.getErrors(), errorUserAction,
                    "Get next items of: " + url, serviceId));
        }
    }

    @Override
    protected boolean hasMoreItems() {
        return Page.isValid(currentNextPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final L result) {
        super.handleResult(result);

        name = result.getName();
        setTitle(name);

        if (infoListAdapter.getItemsList().isEmpty()) {
            if (!result.getRelatedItems().isEmpty()) {
                infoListAdapter.addInfoItemList(result.getRelatedItems());
                showListFooter(hasMoreItems());
            } else if (hasMoreItems()) {
                loadMoreItems();
            } else {
                infoListAdapter.clearStreamItemList();
                showEmptyState();
            }
        }

        if (!result.getErrors().isEmpty()) {
            final List<Throwable> errors = new ArrayList<>(result.getErrors());
            // handling ContentNotSupportedException not to show the error but an appropriate string
            // so that crashes won't be sent uselessly and the user will understand what happened
            errors.removeIf(ContentNotSupportedException.class::isInstance);

            if (!errors.isEmpty()) {
                dynamicallyShowErrorPanelOrSnackbar(new ErrorInfo(result.getErrors(),
                        errorUserAction, "Start loading: " + url, serviceId));
            }
        }
    }

    @Override
    public void showEmptyState() {
        // show "no streams" for SoundCloud; otherwise "no videos"
        // showing "no live streams" is handled in KioskFragment
        if (emptyStateView != null) {
            if (currentInfo.getService() == SoundCloud) {
                setEmptyStateMessage(R.string.no_streams);
            } else {
                setEmptyStateMessage(R.string.no_videos);
            }
        }
        super.showEmptyState();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void setInitialData(final int sid, final String u, final String title) {
        this.serviceId = sid;
        this.url = u;
        this.name = !TextUtils.isEmpty(title) ? title : "";
    }

    private void dynamicallyShowErrorPanelOrSnackbar(final ErrorInfo errorInfo) {
        if (infoListAdapter.getItemCount() == 0) {
            // show error panel only if no items already visible
            showError(errorInfo);
        } else {
            isLoading.set(false);
            showSnackBarError(errorInfo);
        }
    }
}
