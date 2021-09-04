package org.schabi.newpipe.local.feed.notifications

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.core.SingleOnSubscribe
import org.schabi.newpipe.util.PicassoHelper

internal class NotificationIcon(
    context: Context,
    private val url: String,
) : SingleOnSubscribe<Bitmap> {

    private val size = getIconSize(context)

    override fun subscribe(emitter: SingleEmitter<Bitmap>) {
        val target = SingleEmitterTarget(emitter)
        PicassoHelper.loadThumbnail(url)
            .resize(size, size)
            .centerCrop()
            .into(target)
        emitter.setCancellable {
            PicassoHelper.cancelRequest(target)
        }
    }

    private class SingleEmitterTarget(private val emitter: SingleEmitter<Bitmap>) : Target {
        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
            if (!emitter.isDisposed) {
                emitter.onSuccess(bitmap)
            }
        }

        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
            emitter.tryOnError(e)
        }

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) = Unit
    }

    private companion object {

        fun getIconSize(context: Context): Int {
            val activityManager = context.getSystemService(
                Context.ACTIVITY_SERVICE
            ) as ActivityManager?
            val size1 = activityManager?.launcherLargeIconSize ?: 0
            val size2 = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            return maxOf(size2, size1)
        }
    }
}
