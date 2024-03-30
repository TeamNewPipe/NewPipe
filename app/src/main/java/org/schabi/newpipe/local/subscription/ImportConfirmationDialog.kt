package org.schabi.newpipe.local.subscription

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import icepick.Icepick
import icepick.State
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization

class ImportConfirmationDialog() : DialogFragment() {
    @State
    protected var resultServiceIntent: Intent? = null
    fun setResultServiceIntent(resultServiceIntent: Intent?) {
        this.resultServiceIntent = resultServiceIntent
    }

    public override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Localization.assureCorrectAppLanguage(getContext())
        return AlertDialog.Builder(requireContext())
                .setMessage(R.string.import_network_expensive_warning)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialogInterface: DialogInterface?, i: Int ->
                    if (resultServiceIntent != null && getContext() != null) {
                        getContext()!!.startService(resultServiceIntent)
                    }
                    dismiss()
                }))
                .create()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resultServiceIntent == null) {
            throw IllegalStateException("Result intent is null")
        }
        Icepick.restoreInstanceState(this, savedInstanceState)
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    companion object {
        fun show(fragment: Fragment,
                 resultServiceIntent: Intent) {
            val confirmationDialog: ImportConfirmationDialog = ImportConfirmationDialog()
            confirmationDialog.setResultServiceIntent(resultServiceIntent)
            confirmationDialog.show(fragment.getParentFragmentManager(), null)
        }
    }
}
