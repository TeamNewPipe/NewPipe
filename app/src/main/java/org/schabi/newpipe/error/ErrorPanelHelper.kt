package org.schabi.newpipe.error

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.Nullable
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.isInterruptedCaused
import org.schabi.newpipe.ktx.isNetworkRelated
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.util.concurrent.TimeUnit

class ErrorPanelHelper(
    private val fragment: Fragment,
    rootView: View,
    onRetry: Runnable
) {
    private val context: Context = rootView.context!!

    private val errorPanelRoot: View = rootView.findViewById(R.id.error_panel)

    // the only element that is visible by default
    private val errorTextView: TextView =
        errorPanelRoot.findViewById(R.id.error_message_view)
    private val errorServiceInfoTextView: TextView =
        errorPanelRoot.findViewById(R.id.error_message_service_info_view)
    private val errorServiceExplanationTextView: TextView =
        errorPanelRoot.findViewById(R.id.error_message_service_explanation_view)
    private val errorActionButton: Button =
        errorPanelRoot.findViewById(R.id.error_action_button)
    private val errorRetryButton: Button =
        errorPanelRoot.findViewById(R.id.error_retry_button)
    private val errorOpenInBrowserButton: Button =
        errorPanelRoot.findViewById(R.id.error_open_in_browser)

    private var errorDisposable: Disposable? = null

    init {
        errorDisposable = errorRetryButton.clicks()
            .debounce(300, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { onRetry.run() }
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

        if (errorInfo.throwable != null && errorInfo.throwable!!.isInterruptedCaused) {
            if (DEBUG) {
                Log.w(TAG, "onError() isInterruptedCaused! = [$errorInfo.throwable]")
            }
            return
        }

        ensureDefaultVisibility()

        if (errorInfo.throwable is ReCaptchaException) {
            errorTextView.setText(R.string.recaptcha_request_toast)

            showAndSetErrorButtonAction(
                R.string.recaptcha_solve
            ) {
                // Starting ReCaptcha Challenge Activity
                val intent = Intent(context, ReCaptchaActivity::class.java)
                intent.putExtra(
                    ReCaptchaActivity.RECAPTCHA_URL_EXTRA,
                    (errorInfo.throwable as ReCaptchaException).url
                )
                fragment.startActivityForResult(intent, ReCaptchaActivity.RECAPTCHA_REQUEST)
                errorActionButton.setOnClickListener(null)
            }

            errorRetryButton.isVisible = true
            showAndSetOpenInBrowserButtonAction(errorInfo)
        } else if (errorInfo.throwable is AccountTerminatedException) {
            errorTextView.setText(R.string.account_terminated)

            if (!isNullOrEmpty((errorInfo.throwable as AccountTerminatedException).message)) {
                errorServiceInfoTextView.text = context.resources.getString(
                    R.string.service_provides_reason,
                    ServiceHelper.getSelectedService(context)?.serviceInfo?.name ?: "<unknown>"
                )
                errorServiceInfoTextView.isVisible = true

                errorServiceExplanationTextView.text =
                    (errorInfo.throwable as AccountTerminatedException).message
                errorServiceExplanationTextView.isVisible = true
            }
        } else {
            showAndSetErrorButtonAction(
                R.string.error_snackbar_action
            ) {
                ErrorUtil.openActivity(context, errorInfo)
            }

            errorTextView.setText(getExceptionDescription(errorInfo.throwable))

            if (errorInfo.throwable !is ContentNotAvailableException &&
                errorInfo.throwable !is ContentNotSupportedException
            ) {
                // show retry button only for content which is not unavailable or unsupported
                errorRetryButton.isVisible = true
            }
            showAndSetOpenInBrowserButtonAction(errorInfo)
        }

        setRootVisible()
    }

    /**
     * Shows the errorButtonAction, sets a text into it and sets the click listener.
     */
    private fun showAndSetErrorButtonAction(
        @StringRes resid: Int,
        @Nullable listener: View.OnClickListener
    ) {
        errorActionButton.isVisible = true
        errorActionButton.setText(resid)
        errorActionButton.setOnClickListener(listener)
    }

    fun showAndSetOpenInBrowserButtonAction(
        errorInfo: ErrorInfo
    ) {
        errorOpenInBrowserButton.isVisible = true
        errorOpenInBrowserButton.setOnClickListener {
            ShareUtils.openUrlInBrowser(context, errorInfo.request)
        }
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
    }

    companion object {
        val TAG: String = ErrorPanelHelper::class.simpleName!!
        val DEBUG: Boolean = MainActivity.DEBUG

        @StringRes
        fun getExceptionDescription(throwable: Throwable?): Int {
            return when (throwable) {
                is AgeRestrictedContentException -> R.string.restricted_video_no_stream
                is GeographicRestrictionException -> R.string.georestricted_content
                is PaidContentException -> R.string.paid_content
                is PrivateContentException -> R.string.private_content
                is SoundCloudGoPlusContentException -> R.string.soundcloud_go_plus_content
                is YoutubeMusicPremiumContentException -> R.string.youtube_music_premium_content
                is ContentNotAvailableException -> R.string.content_not_available
                is ContentNotSupportedException -> R.string.content_not_supported
                else -> {
                    // show retry button only for content which is not unavailable or unsupported
                    if (throwable != null && throwable.isNetworkRelated) {
                        R.string.network_error
                    } else {
                        R.string.error_snackbar_message
                    }
                }
            }
        }
    }
}
