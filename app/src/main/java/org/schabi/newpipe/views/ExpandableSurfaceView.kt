package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM

class ExpandableSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs) {

    var resizeMode: Int = RESIZE_MODE_FIT
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private var baseHeight = 0
    private var maxHeight = 0
    private var videoAspectRatio = 0.0f
    private var sX = 1.0f
    private var sY = 1.0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (videoAspectRatio == 0.0f) return

        var width = MeasureSpec.getSize(widthMeasureSpec)
        val verticalVideo = videoAspectRatio < 1
        var height = if (maxHeight != 0 && resizeMode != RESIZE_MODE_FIT && verticalVideo) maxHeight else baseHeight

        if (width == 0 || height == 0) return

        val viewAspectRatio = width / height.toFloat()
        val aspectDeformation = (videoAspectRatio / viewAspectRatio) - 1
        sX = 1.0f
        sY = 1.0f

        if (resizeMode == RESIZE_MODE_FIT) {
            if (aspectDeformation > 0) {
                height = (width / videoAspectRatio).toInt()
            } else {
                width = (height * videoAspectRatio).toInt()
            }
        } else if (resizeMode == RESIZE_MODE_ZOOM) {
            if (aspectDeformation < 0) {
                sY = viewAspectRatio / videoAspectRatio
            } else {
                sX = videoAspectRatio / viewAspectRatio
            }
        }

        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        scaleX = sX
        scaleY = sY
    }

    fun setHeights(base: Int, max: Int) {
        if (baseHeight == base && maxHeight == max) return
        baseHeight = base
        maxHeight = max
        requestLayout()
    }

    fun setAspectRatio(aspectRatio: Float) {
        if (videoAspectRatio == aspectRatio || aspectRatio == 0f || !aspectRatio.isFinite()) return
        videoAspectRatio = aspectRatio
        requestLayout()
    }
}
