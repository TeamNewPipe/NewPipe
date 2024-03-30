package us.shandian.giga.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.view.View
import androidx.core.os.HandlerCompat
import com.google.android.material.snackbar.Snackbar
import org.schabi.newpipe.R
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManager.MissionIterator
import us.shandian.giga.ui.adapter.MissionAdapter

class Deleter(private val mView: View, private val mContext: Context, private val mAdapter: MissionAdapter, private val mDownloadManager: DownloadManager, private val mIterator: MissionIterator, private val mHandler: Handler) {
    private var snackbar: Snackbar? = null
    private var items: ArrayList<Mission>?
    private var running: Boolean = true

    init {
        items = ArrayList(2)
    }

    fun append(item: Mission) {
        /* If a mission is removed from the list while the Snackbar for a previously
         * removed item is still showing, commit the action for the previous item
         * immediately. This prevents Snackbars from stacking up in reverse order.
         */
        mHandler.removeCallbacksAndMessages(COMMIT)
        commit()
        mIterator.hide(item)
        items!!.add(0, item)
        show()
    }

    private fun forget() {
        mIterator.unHide(items!!.removeAt(0))
        mAdapter.applyChanges()
        show()
    }

    private fun show() {
        if (items!!.size < 1) return
        pause()
        running = true
        HandlerCompat.postDelayed(mHandler, Runnable({ next() }), NEXT, DELAY.toLong())
    }

    private operator fun next() {
        if (items!!.size < 1) return
        val msg: String = mContext.getString(R.string.file_deleted) + ":\n" + items!!.get(0).storage!!.getName()
        snackbar = Snackbar.make(mView, msg, Snackbar.LENGTH_INDEFINITE)
        snackbar!!.setAction(R.string.undo, View.OnClickListener({ s: View? -> forget() }))
        snackbar!!.setActionTextColor(Color.YELLOW)
        snackbar!!.show()
        HandlerCompat.postDelayed(mHandler, Runnable({ commit() }), COMMIT, TIMEOUT.toLong())
    }

    private fun commit() {
        if (items!!.size < 1) return
        while (items!!.size > 0) {
            val mission: Mission = items!!.removeAt(0)
            if (mission.deleted) continue
            mIterator.unHide(mission)
            mDownloadManager.deleteMission(mission)
            if (mission is FinishedMission) {
                mContext.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mission.storage!!.getUri()))
            }
            break
        }
        if (items!!.size < 1) {
            pause()
            return
        }
        show()
    }

    fun pause() {
        running = false
        mHandler.removeCallbacksAndMessages(NEXT)
        mHandler.removeCallbacksAndMessages(SHOW)
        mHandler.removeCallbacksAndMessages(COMMIT)
        if (snackbar != null) snackbar!!.dismiss()
    }

    fun resume() {
        if (!running) {
            HandlerCompat.postDelayed(mHandler, Runnable({ show() }), SHOW, DELAY_RESUME.toLong())
        }
    }

    fun dispose() {
        if (items!!.size < 1) return
        pause()
        for (mission: Mission in items!!) mDownloadManager.deleteMission(mission)
        items = null
    }

    companion object {
        private val COMMIT: String = "commit"
        private val NEXT: String = "next"
        private val SHOW: String = "show"
        private val TIMEOUT: Int = 5000 // ms
        private val DELAY: Int = 350 // ms
        private val DELAY_RESUME: Int = 400 // ms
    }
}
