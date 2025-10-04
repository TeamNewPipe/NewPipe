package org.schabi.newpipe.ktx

import android.os.Bundle
import androidx.core.os.BundleCompat
import java.io.Serializable

inline fun <reified T : Serializable> Bundle.serializable(key: String?): T? {
    return BundleCompat.getSerializable(this, key, T::class.java)
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
