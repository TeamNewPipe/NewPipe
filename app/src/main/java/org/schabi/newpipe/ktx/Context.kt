package org.schabi.newpipe.ktx

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

tailrec fun Context.findFragmentActivity(): FragmentActivity {
    return when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> throw IllegalStateException("Unable to find FragmentActivity")
    }
}
