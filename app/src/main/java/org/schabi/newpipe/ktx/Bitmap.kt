package org.schabi.newpipe.ktx

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.BitmapCompat

@Suppress("NOTHING_TO_INLINE")
inline fun Bitmap.scale(
    width: Int,
    height: Int,
    srcRect: Rect? = null,
    scaleInLinearSpace: Boolean = true,
) = BitmapCompat.createScaledBitmap(this, width, height, srcRect, scaleInLinearSpace)
