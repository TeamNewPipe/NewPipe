package org.schabi.newpipe.local.feed

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

class FeedHelpFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val usingDedicatedMethod = sharedPreferences.getBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false)
        val enableDisableButtonText = when {
            usingDedicatedMethod -> R.string.feed_use_dedicated_fetch_method_disable_button
            else -> R.string.feed_use_dedicated_fetch_method_enable_button
        }
        return AlertDialog.Builder(requireContext())
                .setMessage(R.string.feed_use_dedicated_fetch_method_help_text)
                .setNeutralButton(enableDisableButtonText) { _, _ ->
                    sharedPreferences.edit()
                            .putBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), !usingDedicatedMethod)
                            .apply()
                }
                .setPositiveButton(resources.getString(R.string.finish), null)
                .create()
    }
}
