package org.schabi.newpipe.util.external_communication

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import org.schabi.newpipe.R

fun ClipboardManager.setTextAndShowToast(context: Context, annotatedString: AnnotatedString) {
    setText(annotatedString)
    if (Build.VERSION.SDK_INT < 33) {
        // Android 13 has its own "copied to clipboard" dialog
        Toast.makeText(context, R.string.msg_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun copyToClipboardCallback(annotatedString: () -> AnnotatedString): (() -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    return {
        clipboardManager.setTextAndShowToast(context, annotatedString())
    }
}
