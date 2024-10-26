package org.schabi.newpipe.fragments;

import static org.schabi.newpipe.ktx.ViewUtils.animate;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.evernote.android.state.State;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorPanelHelper;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.util.InfoCache;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseStateFragment<I> extends BaseFragment implements ViewContract<I> {
    @State
    protected AtomicBoolean wasLoading = new AtomicBoolean();
    protected AtomicBoolean isLoading = new AtomicBoolean();

    @Nullable
    protected View emptyStateView;
    @Nullable
    protected TextView emptyStateMessageView;
    @Nullable
    private ProgressBar loadingProgressBar;

    private ErrorPanelHelper errorPanelHelper;
    @Nullable
    @State
    protected ErrorInfo lastPanelError = null;

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        doInitialLoadLogic();
    }

    @Override
    public void onPause() {
        super.onPause();
        wasLoading.set(isLoading.get());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lastPanelError != null) {
            showError(lastPanelError);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        emptyStateView = rootView.findViewById(R.id.empty_state_view);
        emptyStateMessageView = rootView.findViewById(R.id.empty_state_message);
        loadingProgressBar = rootView.findViewById(R.id.loading_progress_bar);
        errorPanelHelper = new ErrorPanelHelper(this, rootView, this::onRetryButtonClicked);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (errorPanelHelper != null) {
            errorPanelHelper.dispose();
        }
        emptyStateView = null;
        emptyStateMessageView = null;
    }

    protected void onRetryButtonClicked() {
        reloadContent();
    }

    public void reloadContent() {
        startLoading(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load
    //////////////////////////////////////////////////////////////////////////*/

    protected void doInitialLoadLogic() {
        startLoading(true);
    }

    protected void startLoading(final boolean forceLoad) {
        if (DEBUG) {
            Log.d(TAG, "startLoading() called with: forceLoad = [" + forceLoad + "]");
        }
        showLoading();
        isLoading.set(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        if (emptyStateView != null) {
            animate(emptyStateView, false, 150);
        }
        if (loadingProgressBar != null) {
            animate(loadingProgressBar, true, 400);
        }
        hideErrorPanel();
    }

    @Override
    public void hideLoading() {
        if (emptyStateView != null) {
            animate(emptyStateView, false, 150);
        }
        if (loadingProgressBar != null) {
            animate(loadingProgressBar, false, 0);
        }
        hideErrorPanel();
    }

    @Override
    public void showEmptyState() {
        isLoading.set(false);
        if (emptyStateView != null) {
            animate(emptyStateView, true, 200);
        }
        if (loadingProgressBar != null) {
            animate(loadingProgressBar, false, 0);
        }
        hideErrorPanel();
    }

    @Override
    public void handleResult(final I result) {
        if (DEBUG) {
            Log.d(TAG, "handleResult() called with: result = [" + result + "]");
        }
        hideLoading();
    }

    @Override
    public void handleError() {
        isLoading.set(false);
        InfoCache.getInstance().clearCache();
        if (emptyStateView != null) {
            animate(emptyStateView, false, 150);
        }
        if (loadingProgressBar != null) {
            animate(loadingProgressBar, false, 0);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error handling
    //////////////////////////////////////////////////////////////////////////*/

    public final void showError(final ErrorInfo errorInfo) {
        handleError();

        if (isDetached() || isRemoving()) {
            if (DEBUG) {
                Log.w(TAG, "showError() is detached or removing = [" + errorInfo + "]");
            }
            return;
        }

        errorPanelHelper.showError(errorInfo);
        lastPanelError = errorInfo;
    }

    public final void showTextError(@NonNull final String errorString) {
        handleError();

        if (isDetached() || isRemoving()) {
            if (DEBUG) {
                Log.w(TAG, "showTextError() is detached or removing = [" + errorString + "]");
            }
            return;
        }

        errorPanelHelper.showTextError(errorString);
    }

    protected void setEmptyStateMessage(@StringRes final int text) {
        if (emptyStateMessageView != null) {
            emptyStateMessageView.setText(text);
        }
    }

    public final void hideErrorPanel() {
        errorPanelHelper.hide();
        lastPanelError = null;
    }

    public final boolean isErrorPanelVisible() {
        return errorPanelHelper.isVisible();
    }

    /**
     * Directly calls {@link ErrorUtil#showSnackbar(Fragment, ErrorInfo)}, that shows a snackbar if
     * a valid view can be found, otherwise creates an error report notification.
     *
     * @param errorInfo The error information
     */
    public void showSnackBarError(final ErrorInfo errorInfo) {
        if (DEBUG) {
            Log.d(TAG, "showSnackBarError() called with: errorInfo = [" + errorInfo + "]");
        }
        ErrorUtil.showSnackbar(this, errorInfo);
    }
}
