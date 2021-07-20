package org.schabi.newpipe.local.feed.notifications

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.assist.FailReason
import com.nostra13.universalimageloader.core.assist.ImageSize
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.core.SingleOnSubscribe

internal class NotificationIcon(
    context: Context,
    private val url: String
) : SingleOnSubscribe<Bitmap> {

    private val size = getIconSize(context)

    override fun subscribe(emitter: SingleEmitter<Bitmap>) {
        ImageLoader.getInstance().loadImage(
            url,
            ImageSize(size, size),
            object : SimpleImageLoadingListener() {
                override fun onLoadingFailed(imageUri: String?, view: View?, failReason: FailReason) {
                    emitter.onError(failReason.cause)
                }

                override fun onLoadingComplete(imageUri: String?, view: View?, loadedImage: Bitmap) {
                    emitter.onSuccess(loadedImage)
                }
            }
        )
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
