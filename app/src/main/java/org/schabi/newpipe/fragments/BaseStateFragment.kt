package org.schabi.newpipe.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import icepick.State
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorPanelHelper
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.util.InfoCache
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseStateFragment<I>() : BaseFragment(), ViewContract<I> {
    @State
    protected var wasLoading: AtomicBoolean = AtomicBoolean()
    protected var isLoading: AtomicBoolean = AtomicBoolean()
    protected var emptyStateView: View? = null
    protected var emptyStateMessageView: TextView? = null
    private var loadingProgressBar: ProgressBar? = null
    private var errorPanelHelper: ErrorPanelHelper? = null

    @State
    protected var lastPanelError: ErrorInfo? = null
    public override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        doInitialLoadLogic()
    }

    public override fun onPause() {
        super.onPause()
        wasLoading.set(isLoading.get())
    }

    public override fun onResume() {
        super.onResume()
        if (lastPanelError != null) {
            showError(lastPanelError!!)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        emptyStateView = rootView.findViewById(R.id.empty_state_view)
        emptyStateMessageView = rootView.findViewById(R.id.empty_state_message)
        loadingProgressBar = rootView.findViewById(R.id.loading_progress_bar)
        errorPanelHelper = ErrorPanelHelper(this, rootView, Runnable({ onRetryButtonClicked() }))
    }

    public override fun onDestroyView() {
        super.onDestroyView()
        if (errorPanelHelper != null) {
            errorPanelHelper!!.dispose()
        }
        emptyStateView = null
        emptyStateMessageView = null
    }

    protected fun onRetryButtonClicked() {
        reloadContent()
    }

    open fun reloadContent() {
        startLoading(true)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load
    ////////////////////////////////////////////////////////////////////////// */
    protected open fun doInitialLoadLogic() {
        startLoading(true)
    }

    protected open fun startLoading(forceLoad: Boolean) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "startLoading() called with: forceLoad = [" + forceLoad + "]")
        }
        showLoading()
        isLoading.set(true)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    public override fun showLoading() {
        if (emptyStateView != null) {
            emptyStateView!!.animate(false, 150)
        }
        if (loadingProgressBar != null) {
            loadingProgressBar!!.animate(true, 400)
        }
        hideErrorPanel()
    }

    public override fun hideLoading() {
        if (emptyStateView != null) {
            emptyStateView!!.animate(false, 150)
        }
        if (loadingProgressBar != null) {
            loadingProgressBar!!.animate(false, 0)
        }
        hideErrorPanel()
    }

    public override fun showEmptyState() {
        isLoading.set(false)
        if (emptyStateView != null) {
            emptyStateView!!.animate(true, 200)
        }
        if (loadingProgressBar != null) {
            loadingProgressBar!!.animate(false, 0)
        }
        hideErrorPanel()
    }

    public override fun handleResult(result: I) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "handleResult() called with: result = [" + result + "]")
        }
        hideLoading()
    }

    public override fun handleError() {
        isLoading.set(false)
        InfoCache.Companion.getInstance().clearCache()
        if (emptyStateView != null) {
            emptyStateView!!.animate(false, 150)
        }
        if (loadingProgressBar != null) {
            loadingProgressBar!!.animate(false, 0)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error handling
    ////////////////////////////////////////////////////////////////////////// */
    fun showError(errorInfo: ErrorInfo) {
        handleError()
        if (isDetached() || isRemoving()) {
            if (BaseFragment.Companion.DEBUG) {
                Log.w(TAG, "showError() is detached or removing = [" + errorInfo + "]")
            }
            return
        }
        errorPanelHelper!!.showError(errorInfo)
        lastPanelError = errorInfo
    }

    fun showTextError(errorString: String) {
        handleError()
        if (isDetached() || isRemoving()) {
            if (BaseFragment.Companion.DEBUG) {
                Log.w(TAG, "showTextError() is detached or removing = [" + errorString + "]")
            }
            return
        }
        errorPanelHelper!!.showTextError(errorString)
    }

    protected fun setEmptyStateMessage(@StringRes text: Int) {
        if (emptyStateMessageView != null) {
            emptyStateMessageView!!.setText(text)
        }
    }

    fun hideErrorPanel() {
        errorPanelHelper!!.hide()
        lastPanelError = null
    }

    val isErrorPanelVisible: Boolean
        get() {
            return errorPanelHelper!!.isVisible()
        }

    /**
     * Directly calls [ErrorUtil.showSnackbar], that shows a snackbar if
     * a valid view can be found, otherwise creates an error report notification.
     *
     * @param errorInfo The error information
     */
    fun showSnackBarError(errorInfo: ErrorInfo) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "showSnackBarError() called with: errorInfo = [" + errorInfo + "]")
        }
        showSnackbar(this, errorInfo)
    }
}
