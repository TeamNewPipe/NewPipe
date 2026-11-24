/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.settings.SettingsActivity

/** Opening a saved item never substitutes another provider or removes unavailable plugin data. */
object MissingServiceHelper {
    private const val DIALOG_TAG = "missing_streaming_service"

    @JvmStatic
    fun showIfMissing(fragmentManager: FragmentManager, serviceId: Int): Boolean {
        // Negative IDs belong to direct/local media and do not require an extractor plugin.
        if (serviceId < 0 || ServiceList.all().any { it.serviceId == serviceId }) return false
        if (!fragmentManager.isStateSaved && !fragmentManager.isDestroyed &&
            fragmentManager.findFragmentByTag(DIALOG_TAG) == null
        ) {
            MissingServiceDialog().show(fragmentManager, DIALOG_TAG)
        }
        return true
    }

    internal fun createDialog(fragment: DialogFragment): Dialog = MaterialAlertDialogBuilder(fragment.requireContext())
        .setTitle(R.string.missing_service_title)
        .setMessage(R.string.missing_service_message)
        .setPositiveButton(R.string.missing_service_open_plugins) { _, _ ->
            fragment.startActivity(
                Intent(fragment.requireContext(), SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_OPEN_PLUGIN_SETTINGS, true)
            )
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
}

/** Public no-argument fragment so Android can restore an open recovery dialog. */
class MissingServiceDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = MissingServiceHelper.createDialog(this)
}
