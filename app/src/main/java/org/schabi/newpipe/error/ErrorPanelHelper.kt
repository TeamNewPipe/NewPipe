package org.schabi.newpipe.error

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.util.concurrent.TimeUnit

class ErrorPanelHelper(
    private val fragment: Fragment,
    rootView: View,
    onRetry: Runnable?,
) {
    private var _context: Context? = null
    private var _errorPanelRoot: View? = null
    private var _errorTextView: TextView? = null
    private var _errorServiceInfoTextView: TextView? = null
    private var _errorServiceExplanationTextView: TextView? = null
    private var _errorActionButton: Button? = null
    private var _errorRetryButton: Button? = null
    private var _errorOpenInBrowserButton: Button? = null

    // These all are valid till dispose()
    private val context: Context get() = _context!!
    private val errorPanelRoot: View get() = _errorPanelRoot!!
    private val errorTextView: TextView get() = _errorTextView!! // the only element that is visible by default
    private val errorServiceInfoTextView: TextView get() = _errorServiceInfoTextView!!
    private val errorServiceExplanationTextView: TextView get() = _errorServiceExplanationTextView!!
    private val errorActionButton: Button get() = _errorActionButton!!
    private val errorRetryButton: Button get() = _errorRetryButton!!
    private val errorOpenInBrowserButton: Button get() = _errorOpenInBrowserButton!!

    init {
        _context = rootView.context!!
        _errorPanelRoot = rootView.findViewById(R.id.error_panel)

        _errorTextView =
            errorPanelRoot.findViewById(R.id.error_message_view)
        _errorServiceInfoTextView =
            errorPanelRoot.findViewById(R.id.error_message_service_info_view)
        _errorServiceExplanationTextView =
            errorPanelRoot.findViewById(R.id.error_message_service_explanation_view)

        _errorActionButton = errorPanelRoot.findViewById(R.id.error_action_button)
        _errorRetryButton = errorPanelRoot.findViewById(R.id.error_retry_button)
        _errorOpenInBrowserButton = errorPanelRoot.findViewById(R.id.error_open_in_browser)
    }

    private var errorDisposable: Disposable? = null
    private var retryShouldBeShown: Boolean = (onRetry != null)

    init {
        if (onRetry != null) {
            errorDisposable = errorRetryButton.clicks()
                .debounce(300, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { onRetry.run() }
        }
    }

    private fun ensureDefaultVisibility() {
        errorTextView.isVisible = true

        errorServiceInfoTextView.isVisible = false
        errorServiceExplanationTextView.isVisible = false
        errorActionButton.isVisible = false
        errorRetryButton.isVisible = false
        errorOpenInBrowserButton.isVisible = false
    }

    fun showError(errorInfo: ErrorInfo) {
        ensureDefaultVisibility()
        errorTextView.text = errorInfo.getMessage(context)

        if (errorInfo.recaptchaUrl != null) {
            showAndSetErrorButtonAction(R.string.recaptcha_solve) {
                // Starting ReCaptcha Challenge Activity
                val intent = Intent(context, ReCaptchaActivity::class.java)
                intent.putExtra(ReCaptchaActivity.RECAPTCHA_URL_EXTRA, errorInfo.recaptchaUrl)
                fragment.startActivityForResult(intent, ReCaptchaActivity.RECAPTCHA_REQUEST)
                errorActionButton.setOnClickListener(null)
            }
        } else if (errorInfo.isReportable) {
            showAndSetErrorButtonAction(R.string.error_snackbar_action) {
                ErrorUtil.openActivity(context, errorInfo)
            }
        }

        if (errorInfo.isRetryable) {
            errorRetryButton.isVisible = retryShouldBeShown
        }

        if (errorInfo.openInBrowserUrl != null) {
            errorOpenInBrowserButton.isVisible = true
            errorOpenInBrowserButton.setOnClickListener {
                ShareUtils.openUrlInBrowser(context, errorInfo.openInBrowserUrl)
            }
        }

        setRootVisible()
    }

    /**
     * Shows the errorButtonAction, sets a text into it and sets the click listener.
     */
    private fun showAndSetErrorButtonAction(
        @StringRes resid: Int,
        listener: View.OnClickListener
    ) {
        errorActionButton.isVisible = true
        errorActionButton.setText(resid)
        errorActionButton.setOnClickListener(listener)
    }

    fun showTextError(errorString: String) {
        ensureDefaultVisibility()

        errorTextView.text = errorString

        setRootVisible()
    }

    private fun setRootVisible() {
        errorPanelRoot.animate(true, 300)
    }

    fun hide() {
        errorActionButton.setOnClickListener(null)
        errorPanelRoot.animate(false, 150)
    }

    fun isVisible(): Boolean {
        return errorPanelRoot.isVisible
    }

    fun dispose() {
        errorActionButton.setOnClickListener(null)
        errorRetryButton.setOnClickListener(null)
        errorDisposable?.dispose()

        _errorActionButton = null
        _errorRetryButton = null
        _errorOpenInBrowserButton = null

        _errorTextView = null
        _errorServiceInfoTextView = null
        _errorServiceExplanationTextView = null

        _context = null
        _errorPanelRoot = null
    }

    companion object {
        val TAG: String = ErrorPanelHelper::class.simpleName!!
        val DEBUG: Boolean = MainActivity.DEBUG
    }
}
