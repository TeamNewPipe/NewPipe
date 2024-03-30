package org.schabi.newpipe.fragments.list

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import icepick.State
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.views.NewPipeRecyclerView
import java.util.Queue
import java.util.function.Predicate

abstract class BaseListInfoFragment<I : InfoItem?, L : ListInfo<I>?> protected constructor(private val errorUserAction: UserAction) : BaseListFragment<L, InfoItemsPage<I?>?>() {
    @State
    protected var serviceId: Int = NO_SERVICE_ID

    @State
    protected var name: String? = null

    @State
    protected var url: String? = null
    protected var currentInfo: L? = null
    protected var currentNextPage: Page? = null
    protected var currentWorker: Disposable? = null
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        setTitle(name)
        showListFooter(hasMoreItems())
    }

    public override fun onPause() {
        super.onPause()
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }
    }

    public override fun onResume() {
        super.onResume()
        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            if (hasMoreItems() && !infoListAdapter!!.getItemsList().isEmpty()) {
                loadMoreItems()
            } else {
                doInitialLoadLogic()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (currentWorker != null) {
            currentWorker!!.dispose()
            currentWorker = null
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    ////////////////////////////////////////////////////////////////////////// */
    public override fun writeTo(objectsToSave: Queue<Any?>) {
        super.writeTo(objectsToSave)
        objectsToSave.add(currentInfo)
        objectsToSave.add(currentNextPage)
    }

    @Throws(Exception::class)
    public override fun readFrom(savedObjects: Queue<Any>) {
        super.readFrom(savedObjects)
        currentInfo = savedObjects.poll() as L
        currentNextPage = savedObjects.poll() as Page?
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    ////////////////////////////////////////////////////////////////////////// */
    override fun doInitialLoadLogic() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "doInitialLoadLogic() called")
        }
        if (currentInfo == null) {
            startLoading(false)
        } else {
            handleResult(currentInfo!!)
        }
    }

    /**
     * Implement the logic to load the info from the network.<br></br>
     * You can use the default implementations from [org.schabi.newpipe.util.ExtractorHelper].
     *
     * @param forceLoad allow or disallow the result to come from the cache
     * @return Rx [Single] containing the [ListInfo]
     */
    protected abstract fun loadResult(forceLoad: Boolean): Single<L>?
    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        showListFooter(false)
        infoListAdapter!!.clearStreamItemList()
        currentInfo = null
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }
        currentWorker = loadResult(forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer({ result: L ->
                    isLoading.set(false)
                    currentInfo = result
                    currentNextPage = result!!.getNextPage()
                    handleResult(result)
                }), Consumer({ throwable: Throwable? ->
                    showError(ErrorInfo((throwable)!!, errorUserAction,
                            "Start loading: " + url, serviceId))
                }))
    }

    /**
     * Implement the logic to load more items.
     *
     * You can use the default implementations
     * from [org.schabi.newpipe.util.ExtractorHelper].
     *
     * @return Rx [Single] containing the [ListExtractor.InfoItemsPage]
     */
    protected abstract fun loadMoreItemsLogic(): Single<InfoItemsPage<I?>?>?
    override fun loadMoreItems() {
        isLoading.set(true)
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }
        forbidDownwardFocusScroll()
        currentWorker = loadMoreItemsLogic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(Action({ allowDownwardFocusScroll() }))
                .subscribe(Consumer({ infoItemsPage: InfoItemsPage<I?>? ->
                    isLoading.set(false)
                    handleNextItems(infoItemsPage)
                }), Consumer({ throwable: Throwable ->
                    dynamicallyShowErrorPanelOrSnackbar(ErrorInfo(throwable,
                            errorUserAction, "Loading more items: " + url, serviceId))
                }))
    }

    private fun forbidDownwardFocusScroll() {
        if (itemsList is NewPipeRecyclerView) {
            (itemsList as NewPipeRecyclerView).setFocusScrollAllowed(false)
        }
    }

    private fun allowDownwardFocusScroll() {
        if (itemsList is NewPipeRecyclerView) {
            (itemsList as NewPipeRecyclerView).setFocusScrollAllowed(true)
        }
    }

    public override fun handleNextItems(result: InfoItemsPage<I?>?) {
        super.handleNextItems(result)
        currentNextPage = result!!.getNextPage()
        infoListAdapter!!.addInfoItemList(result.getItems())
        showListFooter(hasMoreItems())
        if (!result.getErrors().isEmpty()) {
            dynamicallyShowErrorPanelOrSnackbar(ErrorInfo(result.getErrors(), errorUserAction,
                    "Get next items of: " + url, serviceId))
        }
    }

    override fun hasMoreItems(): Boolean {
        return Page.isValid(currentNextPage)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    public override fun handleResult(result: L) {
        super.handleResult(result)
        name = result!!.getName()
        setTitle(name)
        if (infoListAdapter!!.getItemsList().isEmpty()) {
            if (!result.getRelatedItems().isEmpty()) {
                infoListAdapter!!.addInfoItemList(result.getRelatedItems())
                showListFooter(hasMoreItems())
            } else if (hasMoreItems()) {
                loadMoreItems()
            } else {
                infoListAdapter!!.clearStreamItemList()
                showEmptyState()
            }
        }
        if (!result.getErrors().isEmpty()) {
            val errors: MutableList<Throwable> = ArrayList(result.getErrors())
            // handling ContentNotSupportedException not to show the error but an appropriate string
            // so that crashes won't be sent uselessly and the user will understand what happened
            errors.removeIf(Predicate({ obj: Throwable? -> ContentNotSupportedException::class.java.isInstance(obj) }))
            if (!errors.isEmpty()) {
                dynamicallyShowErrorPanelOrSnackbar(ErrorInfo(result.getErrors(),
                        errorUserAction, "Start loading: " + url, serviceId))
            }
        }
    }

    public override fun showEmptyState() {
        // show "no streams" for SoundCloud; otherwise "no videos"
        // showing "no live streams" is handled in KioskFragment
        if (emptyStateView != null) {
            if (currentInfo!!.getService() === ServiceList.SoundCloud) {
                setEmptyStateMessage(R.string.no_streams)
            } else {
                setEmptyStateMessage(R.string.no_videos)
            }
        }
        super.showEmptyState()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    protected fun setInitialData(sid: Int, u: String?, title: String?) {
        serviceId = sid
        url = u
        name = if (!TextUtils.isEmpty(title)) title else ""
    }

    private fun dynamicallyShowErrorPanelOrSnackbar(errorInfo: ErrorInfo) {
        if (infoListAdapter!!.getItemCount() == 0) {
            // show error panel only if no items already visible
            showError(errorInfo)
        } else {
            isLoading.set(false)
            showSnackBarError(errorInfo)
        }
    }
}
