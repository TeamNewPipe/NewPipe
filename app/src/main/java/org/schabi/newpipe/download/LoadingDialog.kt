package org.schabi.newpipe.download

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DownloadLoadingDialogBinding

/**
 * This class contains a dialog which shows a loading indicator and has a customizable title.
 */
class LoadingDialog
/**
 * Create a new LoadingDialog.
 *
 *
 *
 * The dialog contains a loading indicator and has a customizable title.
 * <br></br>
 * Use `show()` to display the dialog to the user.
 *
 *
 * @param title an informative title shown in the dialog's toolbar
 */(@field:StringRes @param:StringRes private val title: Int) : DialogFragment() {
    private var dialogLoadingBinding: DownloadLoadingDialogBinding? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) {
            Log.d(TAG, ("onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]"))
        }
        setCancelable(false)
    }

    public override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? {
        if (DEBUG) {
            Log.d(TAG, ("onCreateView() called with: "
                    + "inflater = [" + inflater + "], container = [" + container + "], "
                    + "savedInstanceState = [" + savedInstanceState + "]"))
        }
        return inflater.inflate(R.layout.download_loading_dialog, container)
    }

    public override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialogLoadingBinding = DownloadLoadingDialogBinding.bind(view)
        initToolbar(dialogLoadingBinding!!.toolbarLayout.toolbar)
    }

    private fun initToolbar(toolbar: Toolbar) {
        if (DEBUG) {
            Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]")
        }
        toolbar.setTitle(requireContext().getString(title))
        toolbar.setNavigationOnClickListener(View.OnClickListener({ v: View? -> dismiss() }))
    }

    public override fun onDestroyView() {
        dialogLoadingBinding = null
        super.onDestroyView()
    }

    companion object {
        private val TAG: String = "LoadingDialog"
        private val DEBUG: Boolean = MainActivity.Companion.DEBUG
    }
}
