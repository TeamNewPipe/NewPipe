package org.schabi.newpipe.ktx

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import java.io.Serializable

inline fun <reified T : Parcelable> Bundle.parcelable(key: String?): T? {
    return BundleCompat.getParcelable(this, key, T::class.java)
}

inline fun <reified T : Serializable> Bundle.serializable(key: String?): T? {
    return BundleCompat.getSerializable(this, key, T::class.java)
}
