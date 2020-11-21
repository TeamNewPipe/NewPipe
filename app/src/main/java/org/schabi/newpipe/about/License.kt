package org.schabi.newpipe.about

import android.net.Uri
import android.os.Parcelable
import java.io.Serializable
import kotlinx.android.parcel.Parcelize

/**
 * Class for storing information about a software license.
 */
@Parcelize
class License(val name: String, val abbreviation: String, val filename: String) : Parcelable, Serializable {
    val contentUri: Uri
        get() = Uri.Builder()
                .scheme("file")
                .path("/android_asset")
                .appendPath(filename)
                .build()
}
