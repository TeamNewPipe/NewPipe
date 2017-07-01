package org.schabi.newpipe.fragments;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.schabi.newpipe.R;

import static org.schabi.newpipe.util.AnimationUtils.animateView;


/**
 * The loading indicator is used to show the current state of the loading process.
 * <p>
 * The view indicator contains the following components:
 * <ul>
 * <li>A progress bar</li>
 * <li>A error message</li>
 * <li>A retry button</li>
 * </ul>
 */
public class LoadingIndicator extends LinearLayout {
    @NonNull
    private final TextView errorTextView;
    @NonNull
    private final Button retryButton;
    @NonNull
    private final ProgressBar progressBar;
    @NonNull
    private final Runnable setInvisibleRunnable;
    private final int showAnimationDuration;
    private final int hideAnimationDuration;


    public LoadingIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.loading_error_retry, this, true);
        errorTextView = (TextView) findViewById(R.id.error_message_view);
        retryButton = (Button) findViewById(R.id.error_button_retry);
        progressBar = (ProgressBar) findViewById(R.id.loading_progress_bar);
        setInvisibleRunnable = new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.INVISIBLE);
            }
        };
        // Previously 200 for hide and 300 for show
        showAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
        hideAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
    }

    private void showErrorViews(boolean showRetryButton) {
        if (showRetryButton) {
            animateView(retryButton, true, showAnimationDuration);
        } else {
            animateView(retryButton, false, 0);
        }

        animateView(errorTextView, true, showAnimationDuration);

        Runnable runnable = null;
        if (isErrorShown()) {
            // progress bar must be set invisible so the space is reserved for retries.
            runnable = setInvisibleRunnable;
        }
        animateView(progressBar, false, hideAnimationDuration, 0, runnable);
    }

    public boolean isErrorShown() {
        return errorTextView.getVisibility() == View.VISIBLE;
    }

    /**
     * Set the on click listener for the retry button
     *
     * @param onRetryClickedListener on click listener for the retry button or null
     * @see View#setOnClickListener(OnClickListener)
     */
    public void setOnRetryClickedListener(@Nullable OnClickListener onRetryClickedListener) {
        retryButton.setOnClickListener(onRetryClickedListener);
    }

    /**
     * Set the view state to successful.
     * <p>
     * This hides error views and loading indicator (all views).
     */
    public void setSuccessful() {
        animateView(errorTextView, false, hideAnimationDuration);
        animateView(retryButton, false, hideAnimationDuration);
        animateView(progressBar, false, hideAnimationDuration);
    }

    /**
     * Set the views state to loading.
     * <p>
     * Calls to this method should be followed later by calls to {@link #setSuccessful()} or
     * {@link #setErrorMessage(int, boolean)}.
     */
    public void setLoading() {
        animateView(progressBar, true, showAnimationDuration);
    }

    /**
     * Set the view state to failed providing
     *
     * @param message         the error message
     * @param showRetryButton if set to true the retry button is shown
     */
    public void setErrorMessage(@StringRes int message, boolean showRetryButton) {
        errorTextView.setText(message);
        showErrorViews(showRetryButton);
    }

    /**
     * Set the view state to failed providing
     *
     * @param message         the error message
     * @param showRetryButton if set to true the retry button is shown
     */
    public void setErrorMessage(CharSequence message, boolean showRetryButton) {
        errorTextView.setText(message);
        showErrorViews(showRetryButton);
    }
}
