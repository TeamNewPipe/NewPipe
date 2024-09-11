package com.kt.apps.video.ui.popup

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog

fun Context.showDialogUpdateRequired(
    title: CharSequence,
    subTitle: CharSequence,
    action: CharSequence,
    onClick: () -> Unit
) {
    val dialog = AlertDialog.Builder(this)
        .setTitle(title)
        .apply {
            if (subTitle.isNotEmpty()) {
                setMessage(subTitle)
            }
        }
        .setPositiveButton(action) { _, _ -> }
        .setCancelable(false)
        .show()
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        Log.d("CommonDialog", "onClick")
        onClick()
    }
}
