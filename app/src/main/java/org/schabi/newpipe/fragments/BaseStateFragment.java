package org.schabi.newpipe.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.rxbinding2.view.RxView;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.InfoCache;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import icepick.State;
import io.reactivex.android.schedulers.AndroidSchedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public abstract class BaseStateFragment<I> extends BaseFragment implements ViewContract<I> {

    @State
    protected AtomicBoolean wasLoading = new AtomicBoolean();
    protected AtomicBoolean isLoading = new AtomicBoolean();

    @Nullable
    protected View emptyStateView;
    @Nullable
    protected ProgressBar loadingProgressBar;

    protected View errorPanelRoot;
    protected Button errorButtonRetry;
    protected TextView errorTextView;

    @Override
    public void onViewCreated(View rootView, Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        doInitialLoadLogic();
    }

    @Override
    public void onPause() {
        super.onPause();
        wasLoading.set(isLoading.get());
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/


    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        emptyStateView = rootView.findViewById(R.id.empty_state_view);
        loadingProgressBar = rootView.findViewById(R.id.loading_progress_bar);

        errorPanelRoot = rootView.findViewById(R.id.error_panel);
        errorButtonRetry = rootView.findViewById(R.id.error_button_retry);
        errorTextView = rootView.findViewById(R.id.error_message_view);
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        RxView.clicks(errorButtonRetry)
                .debounce(300, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(o -> onRetryButtonClicked());
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

    protected void startLoading(boolean forceLoad) {
        if (DEBUG) Log.d(TAG, "startLoading() called with: forceLoad = [" + forceLoad + "]");
        showLoading();
        isLoading.set(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        if (emptyStateView != null) animateView(emptyStateView, false, 150);
        if (loadingProgressBar != null) animateView(loadingProgressBar, true, 400);
        animateView(errorPanelRoot, false, 150);
    }

    @Override
    public void hideLoading() {
        if (emptyStateView != null) animateView(emptyStateView, false, 150);
        if (loadingProgressBar != null) animateView(loadingProgressBar, false, 0);
        animateView(errorPanelRoot, false, 150);
    }

    @Override
    public void showEmptyState() {
        isLoading.set(false);
        if (emptyStateView != null) animateView(emptyStateView, true, 200);
        if (loadingProgressBar != null) animateView(loadingProgressBar, false, 0);
        animateView(errorPanelRoot, false, 150);
    }

    @Override
    public void showError(String message, boolean showRetryButton) {
        if (DEBUG) Log.d(TAG, "showError() called with: message = [" + message + "], showRetryButton = [" + showRetryButton + "]");
        isLoading.set(false);
        InfoCache.getInstance().clearCache();
        hideLoading();

        errorTextView.setText(message);
        if (showRetryButton) animateView(errorButtonRetry, true, 600);
        else animateView(errorButtonRetry, false, 0);
        animateView(errorPanelRoot, true, 300);
    }

    @Override
    public void handleResult(I result) {
        if (DEBUG) Log.d(TAG, "handleResult() called with: result = [" + result + "]");
        hideLoading();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error handling
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Default implementation handles some general exceptions
     *
     * @return if the exception was handled
     */
    protected boolean onError(Throwable exception) {
        if (DEBUG) Log.d(TAG, "onError() called with: exception = [" + exception + "]");
        isLoading.set(false);

        if (isDetached() || isRemoving()) {
            if (DEBUG) Log.w(TAG, "onError() is detached or removing = [" + exception + "]");
            return true;
        }

        if (ExtractorHelper.isInterruptedCaused(exception)) {
            if (DEBUG) Log.w(TAG, "onError() isInterruptedCaused! = [" + exception + "]");
            return true;
        }

        if (exception instanceof ReCaptchaException) {
            onReCaptchaException();
            return true;
        } else if (exception instanceof IOException) {
            showError(getString(R.string.network_error), true);
            return true;
        }

        return false;
    }

    public void onReCaptchaException() {
        if (DEBUG) Log.d(TAG, "onReCaptchaException() called");
        Toast.makeText(activity, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
        // Starting ReCaptcha Challenge Activity
        startActivityForResult(new Intent(activity, ReCaptchaActivity.class), ReCaptchaActivity.RECAPTCHA_REQUEST);

        showError(getString(R.string.recaptcha_request_toast), false);
    }

    public void onUnrecoverableError(Throwable exception, UserAction userAction, String serviceName, String request, @StringRes int errorId) {
        onUnrecoverableError(Collections.singletonList(exception), userAction, serviceName, request, errorId);
    }

    public void onUnrecoverableError(List<Throwable> exception, UserAction userAction, String serviceName, String request, @StringRes int errorId) {
        if (DEBUG) Log.d(TAG, "onUnrecoverableError() called with: exception = [" + exception + "]");

        if (serviceName == null) serviceName = "none";
        if (request == null) request = "none";

        ErrorActivity.reportError(getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(userAction, serviceName, request, errorId));
    }

    public void showSnackBarError(Throwable exception, UserAction userAction, String serviceName, String request, @StringRes int errorId) {
        showSnackBarError(Collections.singletonList(exception), userAction, serviceName, request, errorId);
    }

    /**
     * Show a SnackBar and only call ErrorActivity#reportError IF we a find a valid view (otherwise the error screen appears)
     */
    public void showSnackBarError(List<Throwable> exception, UserAction userAction, String serviceName, String request, @StringRes int errorId) {
        if (DEBUG) {
            Log.d(TAG, "showSnackBarError() called with: exception = [" + exception + "], userAction = [" + userAction + "], request = [" + request + "], errorId = [" + errorId + "]");
        }
        View rootView = activity != null ? activity.findViewById(android.R.id.content) : null;
        if (rootView == null && getView() != null) rootView = getView();
        if (rootView == null) return;

        ErrorActivity.reportError(getContext(), exception, MainActivity.class, rootView,
                ErrorActivity.ErrorInfo.make(userAction, serviceName, request, errorId));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void openUrlInBrowser(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(Intent.createChooser(intent, activity.getString(R.string.share_dialog_title)));
    }

    protected void shareUrl(String subject, String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)));
    }
}
