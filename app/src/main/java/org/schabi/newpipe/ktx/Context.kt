package org.schabi.newpipe.ktx

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

tailrec fun Context.findFragmentActivity(): FragmentActivity {
    return when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> throw IllegalStateException("Unable to find FragmentActivity")
    }
}

fun Context.findFragmentManager(): FragmentManager {
    return findFragmentActivity().supportFragmentManager
}
