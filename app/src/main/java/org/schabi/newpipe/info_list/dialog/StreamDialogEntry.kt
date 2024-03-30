package org.schabi.newpipe.info_list.dialog

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class StreamDialogEntry(@field:StringRes @param:StringRes val resource: Int,
                        val action: StreamDialogEntryAction) {
    fun getString(context: Context): String {
        return context.getString(resource)
    }

    open interface StreamDialogEntryAction {
        fun onClick(fragment: Fragment?, infoItem: StreamInfoItem?)
    }
}
