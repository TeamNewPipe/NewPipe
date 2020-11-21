package org.schabi.newpipe.report

import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.android.parcel.Parcelize

@Parcelize
class ErrorInfo private constructor(
    val userAction: UserAction?,
    val serviceName: String,
    val request: String,
    @field:StringRes @param:StringRes val message: Int
) : Parcelable {
    companion object {
        @JvmStatic
        fun make(
            userAction: UserAction?,
            serviceName: String,
            request: String,
            @StringRes message: Int
        ) = ErrorInfo(userAction, serviceName, request, message)
    }
}
