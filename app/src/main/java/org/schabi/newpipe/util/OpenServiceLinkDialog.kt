package org.schabi.newpipe.util

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService

/** Link entry for services that offer public browsing but no anonymous keyword search. */
class OpenServiceLinkDialog : DialogFragment() {
    private var input: EditText? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val serviceId = requireArguments().getInt(SERVICE_ID)
        val service = ServiceList.all().firstOrNull { it.serviceId == serviceId }
            ?: return MissingServiceHelper.createDialog(this)
        val content = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val editText = content.findViewById<EditText>(R.id.dialogEditText).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setHint(R.string.service_link_hint)
            setText(savedInstanceState?.getString(INPUT) ?: requireArguments().getString(INPUT, ""))
        }
        input = editText
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.open_link)
            .setMessage(
                if (serviceId == ServiceList.Vimeo.serviceId) {
                    getString(R.string.vimeo_link_entry_message)
                } else {
                    getString(R.string.service_keyword_search_unavailable, service.serviceInfo.name)
                }
            )
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.feed_preview_open, null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val url = editText.text.toString().trim()
                        if (!acceptsLink(serviceId, url)) {
                            editText.error = getString(R.string.unsupported_url)
                        } else {
                            NavigationHelper.openRouterActivity(requireContext(), url)
                            dismiss()
                        }
                    }
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(INPUT, input?.text?.toString().orEmpty())
    }

    override fun onDestroyView() {
        input = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "open_service_link"
        private const val SERVICE_ID = "service_id"
        private const val INPUT = "entered_link"

        @JvmStatic
        fun showIfSearchUnsupported(manager: FragmentManager, serviceId: Int, query: String): Boolean {
            if (MissingServiceHelper.showIfMissing(manager, serviceId)) return true
            if (VimeoSearchSetupDialog.showIfRequired(manager, serviceId, query)) return true
            if (ServiceSearchSupport.supportsSearch(serviceId)) return false
            showForService(manager, serviceId, query)
            return true
        }

        fun showForService(manager: FragmentManager, serviceId: Int, query: String) {
            if (MissingServiceHelper.showIfMissing(manager, serviceId)) return
            if (!manager.isStateSaved && manager.findFragmentByTag(TAG) == null) {
                OpenServiceLinkDialog().apply {
                    arguments = Bundle().apply {
                        putInt(SERVICE_ID, serviceId)
                        putString(INPUT, query.takeIf { acceptsLink(serviceId, it) }.orEmpty())
                    }
                }.show(manager, TAG)
            }
        }

        internal fun acceptsLink(serviceId: Int, url: String): Boolean = runCatching {
            NewPipe.getService(serviceId).getLinkTypeByUrl(url) != StreamingService.LinkType.NONE
        }.getOrDefault(false)
    }
}
