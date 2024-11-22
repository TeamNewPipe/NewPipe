package org.schabi.newpipe.error.usecases

import android.content.Context
import android.content.Intent
import org.schabi.newpipe.error.ErrorActivity
import org.schabi.newpipe.error.ErrorInfo

class OpenErrorActivity(
    private val context: Context,
) {
    operator fun invoke(errorInfo: ErrorInfo) {
        val intent = Intent(context, ErrorActivity::class.java)
        intent.putExtra(ErrorActivity.ERROR_INFO, errorInfo)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }
}
