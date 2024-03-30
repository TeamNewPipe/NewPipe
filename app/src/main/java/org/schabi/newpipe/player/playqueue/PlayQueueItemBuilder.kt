package org.schabi.newpipe.player.playqueue

import android.content.Context
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.PicassoHelper

class PlayQueueItemBuilder(context: Context?) {
    private var onItemClickListener: OnSelectedListener? = null
    fun setOnSelectedListener(listener: OnSelectedListener?) {
        onItemClickListener = listener
    }

    fun buildStreamInfoItem(holder: PlayQueueItemHolder, item: PlayQueueItem?) {
        if (!TextUtils.isEmpty(item.getTitle())) {
            holder.itemVideoTitleView.setText(item.getTitle())
        }
        holder.itemAdditionalDetailsView.setText(Localization.concatenateStrings(item.getUploader(),
                ServiceHelper.getNameOfServiceById(item.getServiceId())))
        if (item.getDuration() > 0) {
            holder.itemDurationView.setText(Localization.getDurationString(item.getDuration()))
        } else {
            holder.itemDurationView.setVisibility(View.GONE)
        }
        PicassoHelper.loadThumbnail(item.getThumbnails()).into(holder.itemThumbnailView)
        holder.itemRoot.setOnClickListener(View.OnClickListener({ view: View? ->
            if (onItemClickListener != null) {
                onItemClickListener!!.selected(item, view)
            }
        }))
        holder.itemRoot.setOnLongClickListener(OnLongClickListener({ view: View? ->
            if (onItemClickListener != null) {
                onItemClickListener!!.held((item)!!, view)
                return@setOnLongClickListener true
            }
            false
        }))
        holder.itemHandle.setOnTouchListener(getOnTouchListener(holder))
    }

    private fun getOnTouchListener(holder: PlayQueueItemHolder): OnTouchListener {
        return OnTouchListener({ view: View, motionEvent: MotionEvent ->
            view.performClick()
            if ((motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN
                            && onItemClickListener != null)) {
                onItemClickListener!!.onStartDrag(holder)
            }
            false
        })
    }

    open interface OnSelectedListener {
        fun selected(item: PlayQueueItem?, view: View?)
        fun held(item: PlayQueueItem, view: View?)
        fun onStartDrag(viewHolder: PlayQueueItemHolder?)
    }

    companion object {
        private val TAG: String = PlayQueueItemBuilder::class.java.toString()
    }
}
