package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBar
import androidx.core.text.util.LinkifyCompat
import icepick.State
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.ContentSource
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ServiceHelper

class SubscriptionsImportFragment() : BaseFragment() {
    @State
    var currentServiceId: Int = NO_SERVICE_ID
    private var supportedSources: List<ContentSource>? = null
    private var relatedUrl: String? = null

    @StringRes
    private var instructionsString: Int = 0

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */
    private var infoTextView: TextView? = null
    private var inputText: EditText? = null
    private var inputButton: Button? = null
    private val requestImportFileLauncher: ActivityResultLauncher<Intent> = registerForActivityResult<Intent, ActivityResult>(StartActivityForResult(), ActivityResultCallback<ActivityResult>({ result: ActivityResult -> requestImportFileResult(result) }))
    private fun setInitialData(serviceId: Int) {
        currentServiceId = serviceId
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    ///////////////////////////////////////////////////////////////////////////
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupServiceVariables()
        if (supportedSources!!.isEmpty() && currentServiceId != NO_SERVICE_ID) {
            showSnackbar((activity)!!,
                    ErrorInfo(arrayOf(), UserAction.SUBSCRIPTION_IMPORT_EXPORT,
                            ServiceHelper.getNameOfServiceById(currentServiceId),
                            "Service does not support importing subscriptions",
                            R.string.general_error))
            activity!!.finish()
        }
    }

    public override fun onResume() {
        super.onResume()
        setTitle(getString(R.string.import_title))
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_import, container, false)
    }

    /*/////////////////////////////////////////////////////////////////////////
    // Fragment Views
    ///////////////////////////////////////////////////////////////////////// */
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        inputButton = rootView.findViewById(R.id.input_button)
        inputText = rootView.findViewById(R.id.input_text)
        infoTextView = rootView.findViewById(R.id.info_text_view)

        // TODO: Support services that can import from more than one source
        //  (show the option to the user)
        if (supportedSources!!.contains(ContentSource.CHANNEL_URL)) {
            inputButton.setText(R.string.import_title)
            inputText.setVisibility(View.VISIBLE)
            inputText.setHint(ServiceHelper.getImportInstructionsHint(currentServiceId))
        } else {
            inputButton.setText(R.string.import_file_title)
        }
        if (instructionsString != 0) {
            if (TextUtils.isEmpty(relatedUrl)) {
                setInfoText(getString(instructionsString))
            } else {
                setInfoText(getString(instructionsString, relatedUrl))
            }
        } else {
            setInfoText("")
        }
        val supportActionBar: ActionBar? = activity!!.getSupportActionBar()
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true)
            setTitle(getString(R.string.import_title))
        }
    }

    override fun initListeners() {
        super.initListeners()
        inputButton!!.setOnClickListener(View.OnClickListener({ v: View? -> onImportClicked() }))
    }

    private fun onImportClicked() {
        if (inputText!!.getVisibility() == View.VISIBLE) {
            val value: String = inputText!!.getText().toString()
            if (!value.isEmpty()) {
                onImportUrl(value)
            }
        } else {
            onImportFile()
        }
    }

    fun onImportUrl(value: String?) {
        ImportConfirmationDialog.Companion.show(this, Intent(activity, SubscriptionsImportService::class.java)
                .putExtra(SubscriptionsImportService.Companion.KEY_MODE, SubscriptionsImportService.Companion.CHANNEL_URL_MODE)
                .putExtra(SubscriptionsImportService.Companion.KEY_VALUE, value)
                .putExtra(KEY_SERVICE_ID, currentServiceId))
    }

    fun onImportFile() {
        NoFileManagerSafeGuard.launchSafe<Intent>(
                requestImportFileLauncher,  // leave */* mime type to support all services
                // with different mime types and file extensions
                StoredFileHelper.Companion.getPicker((activity)!!, "*/*"),
                TAG,
                getContext()
        )
    }

    private fun requestImportFileResult(result: ActivityResult) {
        if (result.getData() == null) {
            return
        }
        if (result.getResultCode() == Activity.RESULT_OK && result.getData()!!.getData() != null) {
            ImportConfirmationDialog.Companion.show(this,
                    Intent(activity, SubscriptionsImportService::class.java)
                            .putExtra(SubscriptionsImportService.Companion.KEY_MODE, SubscriptionsImportService.Companion.INPUT_STREAM_MODE)
                            .putExtra(SubscriptionsImportService.Companion.KEY_VALUE, result.getData()!!.getData())
                            .putExtra(KEY_SERVICE_ID, currentServiceId))
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions
    ///////////////////////////////////////////////////////////////////////////
    private fun setupServiceVariables() {
        if (currentServiceId != NO_SERVICE_ID) {
            try {
                val extractor: SubscriptionExtractor = NewPipe.getService(currentServiceId)
                        .getSubscriptionExtractor()
                supportedSources = extractor.getSupportedSources()
                relatedUrl = extractor.getRelatedUrl()
                instructionsString = ServiceHelper.getImportInstructions(currentServiceId)
                return
            } catch (ignored: ExtractionException) {
            }
        }
        supportedSources = emptyList()
        relatedUrl = null
        instructionsString = 0
    }

    private fun setInfoText(infoString: String) {
        infoTextView!!.setText(infoString)
        LinkifyCompat.addLinks((infoTextView)!!, Linkify.WEB_URLS)
    }

    companion object {
        fun getInstance(serviceId: Int): SubscriptionsImportFragment {
            val instance: SubscriptionsImportFragment = SubscriptionsImportFragment()
            instance.setInitialData(serviceId)
            return instance
        }
    }
}
