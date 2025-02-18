package org.schabi.newpipe.ktx

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat

inline fun <reified T : Parcelable> Bundle.parcelableArrayList(key: String?): ArrayList<T>? {
    return BundleCompat.getParcelableArrayList(this, key, T::class.java)
}

fun Bundle?.toDebugString(): String {
    if (this == null) {
        return "null"
    }
    val string = StringBuilder("Bundle{")
    for (key in this.keySet()) {
        @Suppress("DEPRECATION") // we want this[key] to return items of any type
        string.append(" ").append(key).append(" => ").append(this[key]).append(";")
    }
    string.append(" }")
    return string.toString()
}
