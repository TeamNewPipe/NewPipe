package org.schabi.newpipe.cast.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.cast.session.CastSessionManager
import org.schabi.newpipe.util.ThemeHelper

object LinkWithTvCodeDialog {

    fun show(context: Context, onDeviceConnected: (() -> Unit)? = null) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_link_with_tv_code, null)
        val input = view.findViewById<EditText>(R.id.tvCodeInput)
        val progressBar = view.findViewById<ProgressBar>(R.id.linkingProgressBar)

        val dialog = AlertDialog.Builder(context, ThemeHelper.getDialogTheme(context))
            .setView(view)
            .setPositiveButton(R.string.cast_link, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val linkButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            linkButton.setOnClickListener {
                val code = input.text.toString().trim()
                if (code.isEmpty()) return@setOnClickListener

                linkButton.isEnabled = false
                input.isEnabled = false
                progressBar.visibility = View.VISIBLE

                val manager = CastSessionManager.getInstance(context)
                manager.pairWithTvCode(code) { result ->
                    progressBar.visibility = View.GONE
                    linkButton.isEnabled = true
                    input.isEnabled = true

                    result.onSuccess { device ->
                        manager.connect(device)
                        Toast.makeText(
                            context,
                            context.getString(R.string.casting_to, device.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        onDeviceConnected?.invoke()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            R.string.cast_pairing_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        dialog.show()
    }
}
