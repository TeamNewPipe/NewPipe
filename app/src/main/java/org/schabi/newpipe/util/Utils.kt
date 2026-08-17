package org.schabi.newpipe.util

import android.net.Uri
import java.io.File

object Utils {
    @JvmStatic
    fun getFileForUri(uri: Uri): File {
        return File(uri.path ?: "")
    }
}
