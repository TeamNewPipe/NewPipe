/*
 * SPDX-FileCopyrightText: 2018-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import org.schabi.newpipe.R

class ImportConfirmationDialog : DialogFragment() {
    lateinit var resultServiceIntent: Intent
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setMessage(R.string.import_network_expensive_warning)
            .setCancelable(true)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                requireContext().startService(resultServiceIntent)
                dismiss()
            }
            .create()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultServiceIntent = requireArguments().getParcelable(EXTRA_RESULT_SERVICE_INTENT)!!
    }

    companion object {
        private const val EXTRA_RESULT_SERVICE_INTENT = "extra_result_service_intent"

        @JvmStatic
        fun show(fragment: Fragment, resultServiceIntent: Intent) {
            val dialog = ImportConfirmationDialog()
            val args = Bundle()
            args.putParcelable(EXTRA_RESULT_SERVICE_INTENT, resultServiceIntent)
            dialog.setArguments(args)
            dialog.show(fragment.getParentFragmentManager(), null)
        }
    }
}
