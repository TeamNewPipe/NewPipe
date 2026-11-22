/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.vimeo.VimeoPublicApi
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.settings.VimeoSettingsFragment

/** Keeps a pending Vimeo query while the user configures optional public API access. */
class VimeoSearchSetupDialog : DialogFragment() {
    private var openingSearch = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.search_with_service_name, ServiceList.Vimeo.serviceInfo.name))
        .setMessage(R.string.vimeo_search_setup_message)
        .setNegativeButton(R.string.cancel, null)
        .setNeutralButton(R.string.open_link) { _, _ ->
            OpenServiceLinkDialog.showForService(parentFragmentManager, ServiceList.Vimeo.serviceId, requireArguments().getString(QUERY).orEmpty())
        }
        .setPositiveButton(R.string.vimeo_search_setup_action, null)
        .create().apply {
            setOnShowListener {
                // Keep the dialog and its query on the back stack during the settings round trip.
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    startActivity(
                        Intent(requireContext(), SettingsActivity::class.java)
                            .putExtra(VimeoSettingsFragment.EXTRA_OPEN_SETTINGS, true)
                    )
                }
            }
        }

    override fun onResume() {
        super.onResume()
        if (openingSearch || !VimeoPublicApi.isConfigured()) return
        val manager = parentFragmentManager
        if (manager.isStateSaved) return
        openingSearch = true
        val query = requireArguments().getString(QUERY).orEmpty()
        dismiss()
        NavigationHelper.openSearchFragment(manager, ServiceList.Vimeo.serviceId, query)
    }

    companion object {
        private const val TAG = "vimeo_search_setup"
        private const val QUERY = "search_query"

        @JvmStatic
        fun showIfRequired(manager: FragmentManager, serviceId: Int, query: String): Boolean {
            if (serviceId != ServiceList.Vimeo.serviceId || VimeoPublicApi.isConfigured() ||
                !KidsMode.isSearchAllowed(App.instance, serviceId)
            ) {
                return false
            }
            if (!manager.isStateSaved && manager.findFragmentByTag(TAG) == null) {
                VimeoSearchSetupDialog().apply {
                    arguments = Bundle().apply { putString(QUERY, ContextualSearchHelper.normalizeQuery(query)) }
                }.show(manager, TAG)
            }
            return true
        }
    }
}
