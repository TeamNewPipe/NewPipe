package us.shandian.giga.ui.adapter

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.util.set
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.NavigationHelper
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission
import us.shandian.giga.io.StoredFileHelper
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManager.MissionItem
import us.shandian.giga.service.DownloadManager.MissionIterator
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.ui.common.Deleter
import us.shandian.giga.ui.common.ProgressDrawable
import us.shandian.giga.util.Utility

private suspend fun Context.calcChecksum(storedFileHelper: StoredFileHelper, algorithm: String) {
    // Create dialog
    val progressDialog = ProgressDialog(this)
    progressDialog.setCancelable(false)
    progressDialog.setMessage(getString(R.string.msg_wait))
    progressDialog.show()
    val checksum = withContext(Dispatchers.Default) { Utility.checksum(storedFileHelper, algorithm) }
    Utility.copyToClipboard(progressDialog.context, checksum)
    progressDialog.dismiss()
}

class MissionAdapter(
    private val mContext: Context,
    private val mDownloadManager: DownloadManager,
    emptyMessage: View,
    root: View
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Handler.Callback, CoroutineScope by CoroutineScope(Dispatchers.Main) {
    private val mInflater = mContext.getSystemService<LayoutInflater>()!!
    private val mDeleter: Deleter
    private var mLayout: Int
    private val mIterator: MissionIterator
    private val mPendingDownloadsItems = ArrayList<ViewHolderItem>()
    private val mHandler: Handler
    private var mClear: MenuItem? = null
    private var mStartButton: MenuItem? = null
    private var mPauseButton: MenuItem? = null
    private val mEmptyMessage: View
    private var mRecover: RecoverHelper? = null
    private val mView: View
    private val mHidden: ArrayList<Mission>
    private var mSnackbar: Snackbar? = null
    private val rUpdater = Runnable { updater() }
    private val rDelete = Runnable { deleteFinishedDownloads() }

    init {
        mLayout = R.layout.mission_item
        mHandler = Handler(mContext.mainLooper)
        mEmptyMessage = emptyMessage
        mIterator = mDownloadManager.iterator
        mDeleter = Deleter(root, mContext, this, mDownloadManager, mIterator, mHandler)
        mView = root
        mHidden = ArrayList()
        checkEmptyMessageVisibility()
        onResume()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            DownloadManager.SPECIAL_PENDING,
            DownloadManager.SPECIAL_FINISHED ->
                return ViewHolderHeader(mInflater.inflate(R.layout.missions_header, parent, false))
        }
        return ViewHolderItem(mInflater.inflate(mLayout, parent, false))
    }

    override fun onViewRecycled(view: RecyclerView.ViewHolder) {
        super.onViewRecycled(view)
        if (view is ViewHolderHeader) return
        val h = view as ViewHolderItem
        if (h.item!!.mission is DownloadMission) {
            mPendingDownloadsItems.remove(h)
            if (mPendingDownloadsItems.size < 1) {
                checkMasterButtonsVisibility()
            }
        }
        h.popupMenu.dismiss()
        h.item = null
        h.resetSpeedMeasure()
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(view: RecyclerView.ViewHolder, @SuppressLint("RecyclerView") pos: Int) {
        val item = mIterator.getItem(pos)
        if (view is ViewHolderHeader) {
            if (item.special == DownloadManager.SPECIAL_NOTHING) return
            val str: Int
            if (item.special == DownloadManager.SPECIAL_PENDING) {
                str = R.string.missions_header_pending
            } else {
                str = R.string.missions_header_finished
                if (mClear != null) mClear!!.isVisible = true
            }
            view.header.setText(str)
            return
        }
        val h = view as ViewHolderItem
        h.item = item
        val type = Utility.getFileType(item.mission.kind, item.mission.storage.name)
        h.icon.setImageResource(Utility.getIconForFileType(type))
        h.name.text = item.mission.storage.name
        h.progress.setColors(Utility.getBackgroundForFileType(mContext, type), Utility.getForegroundForFileType(mContext, type))
        if (h.item!!.mission is DownloadMission) {
            val mission = item.mission as DownloadMission
            var length = Utility.formatBytes(mission.getLength())
            if (mission.running && !mission.isPsRunning) length += " --.- kB/s"
            h.size.text = length
            h.pause.setTitle(if (mission.unknownLength) R.string.stop else R.string.pause)
            updateProgress(h)
            mPendingDownloadsItems.add(h)
        } else {
            h.progress.setMarquee(false)
            h.status.text = "100%"
            h.progress.setProgress(1.0)
            h.size.text = Utility.formatBytes(item.mission.length)
        }
    }

    override fun getItemCount(): Int {
        return mIterator.oldListSize
    }

    override fun getItemViewType(position: Int): Int {
        return mIterator.getSpecialAtItem(position)
    }

    @SuppressLint("DefaultLocale")
    private fun updateProgress(h: ViewHolderItem?) {
        if (h?.item == null || h.item!!.mission is FinishedMission) return
        val mission = h.item!!.mission as DownloadMission
        val done = mission.done.toDouble()
        val length = mission.getLength()
        val now = System.currentTimeMillis()
        val hasError = mission.errCode != DownloadMission.ERROR_NOTHING

        // hide on error
        // show if current resource length is not fetched
        // show if length is unknown
        h.progress.setMarquee(mission.isRecovering || !hasError && (!mission.isInitialized || mission.unknownLength))
        val progress: Double
        if (mission.unknownLength) {
            progress = Double.NaN
            h.progress.setProgress(0.0)
        } else {
            progress = done / length
        }
        when {
            hasError -> {
                h.progress.setProgress(if (isNotFinite(progress)) 1.0 else progress)
                h.status.setText(R.string.msg_error)
            }
            isNotFinite(progress) -> h.status.text = UNDEFINED_PROGRESS
            else -> {
                h.status.text = String.format("%.2f%%", progress * 100)
                h.progress.setProgress(progress)
            }
        }
        @StringRes val state: Int
        val sizeStr = Utility.formatBytes(length) + "  "
        if (mission.isPsFailed || mission.errCode == DownloadMission.ERROR_POSTPROCESSING_HOLD) {
            h.size.text = sizeStr
            return
        } else if (!mission.running) {
            state = if (mission.enqueued) R.string.queued else R.string.paused
        } else if (mission.isPsRunning) {
            state = R.string.post_processing
        } else if (mission.isRecovering) {
            state = R.string.recovering
        } else {
            state = 0
        }
        if (state != 0) {
            // update state without download speed
            h.size.text = sizeStr + "(" + mContext.getString(state) + ")"
            h.resetSpeedMeasure()
            return
        }
        if (h.lastTimestamp < 0) {
            h.size.text = sizeStr
            h.lastTimestamp = now
            h.lastDone = done
            return
        }
        val deltaTime = now - h.lastTimestamp
        val deltaDone = done - h.lastDone
        if (h.lastDone > done) {
            h.lastDone = done
            h.size.text = sizeStr
            return
        }
        if (deltaDone > 0 && deltaTime > 0) {
            val speed = (deltaDone * 1000.0 / deltaTime).toFloat()
            var averageSpeed = speed
            if (h.lastSpeedIdx < 0) {
                h.lastSpeed.fill(speed)
                h.lastSpeedIdx = 0
            } else {
                for (i in h.lastSpeed.indices) {
                    averageSpeed += h.lastSpeed[i]
                }
                averageSpeed /= h.lastSpeed.size + 1.0f
            }
            val speedStr = Utility.formatSpeed(averageSpeed.toDouble())
            val etaStr: String
            etaStr = if (mission.unknownLength) {
                ""
            } else {
                val eta = Math.ceil((length - done) / averageSpeed).toLong()
                Utility.formatBytes(done.toLong()) + "/" + Utility.stringifySeconds(eta.toDouble()) + "  "
            }
            h.size.text = sizeStr + etaStr + speedStr
            h.lastTimestamp = now
            h.lastDone = done
            h.lastSpeed[h.lastSpeedIdx++] = speed
            if (h.lastSpeedIdx >= h.lastSpeed.size) h.lastSpeedIdx = 0
        }
    }

    private fun viewWithFileProvider(mission: Mission) {
        if (checkInvalidFile(mission)) return
        val mimeType = resolveMimeType(mission)
        if (BuildConfig.DEBUG) Log.v(TAG, "Mime: " + mimeType + " package: " + BuildConfig.APPLICATION_ID + ".provider")
        val uri = resolveShareableUri(mission)
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.setDataAndType(uri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // mContext.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(mContext.packageManager) != null) {
            mContext.startActivity(intent)
        } else {
            Toast.makeText(mContext, R.string.toast_no_player, Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFile(mission: Mission) {
        if (checkInvalidFile(mission)) return
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = resolveMimeType(mission)
        intent.putExtra(Intent.EXTRA_STREAM, resolveShareableUri(mission))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        mContext.startActivity(Intent.createChooser(intent, null))
    }

    /**
     * Returns an Uri which can be shared to other applications.
     *
     * @see [
     * https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed](https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed)
     */
    private fun resolveShareableUri(mission: Mission): Uri {
        return if (mission.storage.isDirect) {
            FileProvider.getUriForFile(
                    mContext,
                    BuildConfig.APPLICATION_ID + ".provider",
                    File(URI.create(mission.storage.uri.toString()))
            )
        } else {
            mission.storage.uri
        }
    }

    private fun checkInvalidFile(mission: Mission): Boolean {
        if (mission.storage.existsAsFile()) return false
        Toast.makeText(mContext, R.string.missing_file, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun getViewHolder(mission: Any): ViewHolderItem? {
        for (h in mPendingDownloadsItems) {
            if (h.item!!.mission === mission) return h
        }
        return null
    }

    override fun handleMessage(msg: Message): Boolean {
        if (mStartButton != null && mPauseButton != null) {
            checkMasterButtonsVisibility()
        }
        when (msg.what) {
            DownloadManagerService.MESSAGE_ERROR,
            DownloadManagerService.MESSAGE_FINISHED,
            DownloadManagerService.MESSAGE_DELETED,
            DownloadManagerService.MESSAGE_PAUSED -> {
            }
            else -> return false
        }
        val h = getViewHolder(msg.obj) ?: return false
        when (msg.what) {
            DownloadManagerService.MESSAGE_FINISHED, DownloadManagerService.MESSAGE_DELETED -> {
                // DownloadManager should mark the download as finished
                applyChanges()
                return true
            }
        }
        updateProgress(h)
        return true
    }

    private fun showError(mission: DownloadMission) {
        @StringRes var msg = R.string.general_error
        var msgEx: String? = null
        when (mission.errCode) {
            416 -> msg = R.string.error_http_unsupported_range
            404 -> msg = R.string.error_http_not_found
            DownloadMission.ERROR_NOTHING -> return // this never should happen
            DownloadMission.ERROR_FILE_CREATION -> msg = R.string.error_file_creation
            DownloadMission.ERROR_HTTP_NO_CONTENT -> msg = R.string.error_http_no_content
            DownloadMission.ERROR_PATH_CREATION -> msg = R.string.error_path_creation
            DownloadMission.ERROR_PERMISSION_DENIED -> msg = R.string.permission_denied
            DownloadMission.ERROR_SSL_EXCEPTION -> msg = R.string.error_ssl_exception
            DownloadMission.ERROR_UNKNOWN_HOST -> msg = R.string.error_unknown_host
            DownloadMission.ERROR_CONNECT_HOST -> msg = R.string.error_connect_host
            DownloadMission.ERROR_POSTPROCESSING_STOPPED -> msg = R.string.error_postprocessing_stopped
            DownloadMission.ERROR_POSTPROCESSING, DownloadMission.ERROR_POSTPROCESSING_HOLD -> {
                showError(mission, UserAction.DOWNLOAD_POSTPROCESSING, R.string.error_postprocessing_failed)
                return
            }
            DownloadMission.ERROR_INSUFFICIENT_STORAGE -> msg = R.string.error_insufficient_storage
            DownloadMission.ERROR_UNKNOWN_EXCEPTION -> if (mission.errObject != null) {
                showError(mission, UserAction.DOWNLOAD_FAILED, R.string.general_error)
                return
            } else {
                msg = R.string.msg_error
                return
            }
            DownloadMission.ERROR_PROGRESS_LOST -> msg = R.string.error_progress_lost
            DownloadMission.ERROR_TIMEOUT -> msg = R.string.error_timeout
            DownloadMission.ERROR_RESOURCE_GONE -> msg = R.string.error_download_resource_gone
            else -> msgEx = when {
                mission.errCode in 100..599 -> "HTTP " + mission.errCode
                mission.errObject == null -> "(not_decelerated_error_code)"
                else -> {
                    showError(mission, UserAction.DOWNLOAD_FAILED, msg)
                    return
                }
            }
        }
        val builder = AlertDialog.Builder(mContext)
        if (msgEx != null) builder.setMessage(msgEx) else builder.setMessage(msg)

        // add report button for non-HTTP errors (range 100-599)
        if (mission.errObject != null && (mission.errCode < 100 || mission.errCode >= 600)) {
            @StringRes val mMsg = msg
            builder.setPositiveButton(R.string.error_report_title) {
                _, _ -> showError(mission, UserAction.DOWNLOAD_FAILED, mMsg)
            }
        }
        builder.setNegativeButton(R.string.finish) { dialog, _ -> dialog.cancel() }
                .setTitle(mission.storage.name)
                .create()
                .show()
    }

    private fun showError(mission: DownloadMission, action: UserAction, @StringRes reason: Int) {
        val request = StringBuilder(256)
        request.append(mission.source)
        request.append(" [")
        if (mission.recoveryInfo != null) {
            for (recovery in mission.recoveryInfo) request.append(' ')
                    .append(recovery.toString())
                    .append(' ')
        }
        request.append("]")
        val service = try {
            NewPipe.getServiceByUrl(mission.source).serviceInfo.name
        } catch (e: Exception) {
            "-"
        }
        ErrorActivity.reportError(
                mContext,
                mission.errObject,
                null,
                null,
                ErrorActivity.ErrorInfo.make(action, service, request.toString(), reason)
        )
    }

    fun clearFinishedDownloads(delete: Boolean) {
        if (delete && mIterator.hasFinishedMissions() && mHidden.isEmpty()) {
            for (i in 0 until mIterator.oldListSize) {
                val mission = if (mIterator.getItem(i).mission is FinishedMission) mIterator.getItem(i).mission as FinishedMission else null
                if (mission != null) {
                    mIterator.hide(mission)
                    mHidden.add(mission)
                }
            }
            applyChanges()
            val msg = String.format(mContext.getString(R.string.deleted_downloads), mHidden.size)
            mSnackbar = Snackbar.make(mView, msg, Snackbar.LENGTH_INDEFINITE)
            mSnackbar!!.setAction(R.string.undo) {
                val i = mHidden.iterator()
                while (i.hasNext()) {
                    mIterator.unHide(i.next())
                    i.remove()
                }
                applyChanges()
                mHandler.removeCallbacks(rDelete)
            }
            mSnackbar!!.setActionTextColor(Color.YELLOW)
            mSnackbar!!.show()
            mHandler.postDelayed(rDelete, 5000)
        } else if (!delete) {
            mDownloadManager.forgetFinishedDownloads()
            applyChanges()
        }
    }

    private fun deleteFinishedDownloads() {
        if (mSnackbar != null) mSnackbar!!.dismiss()
        val i = mHidden.iterator()
        while (i.hasNext()) {
            val mission = i.next()
            mDownloadManager.deleteMission(mission)
            mContext.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mission.storage.uri))
            i.remove()
        }
    }

    private fun handlePopupItem(h: ViewHolderItem, option: MenuItem): Boolean {
        if (h.item == null) return true
        val id = option.itemId
        val mission = if (h.item!!.mission is DownloadMission) h.item!!.mission as DownloadMission else null
        if (mission != null) {
            when (id) {
                R.id.start -> {
                    h.status.text = UNDEFINED_PROGRESS
                    mDownloadManager.resumeMission(mission)
                    return true
                }
                R.id.pause -> {
                    mDownloadManager.pauseMission(mission)
                    return true
                }
                R.id.error_message_view -> {
                    showError(mission)
                    return true
                }
                R.id.queue -> {
                    val flag = !h.queue.isChecked
                    h.queue.isChecked = flag
                    mission.setEnqueued(flag)
                    updateProgress(h)
                    return true
                }
                R.id.retry -> {
                    if (mission.isPsRunning) {
                        mission.psContinue(true)
                    } else {
                        mDownloadManager.tryRecover(mission)
                        if (mission.storage.isInvalid) mRecover!!.tryRecover(mission) else recoverMission(mission)
                    }
                    return true
                }
                R.id.cancel -> {
                    mission.psContinue(false)
                    return false
                }
            }
        }
        return when (id) {
            R.id.menu_item_share -> {
                shareFile(h.item!!.mission)
                true
            }
            R.id.delete -> {
                mDeleter.append(h.item!!.mission)
                applyChanges()
                checkMasterButtonsVisibility()
                true
            }
            R.id.md5, R.id.sha1 -> {
                launch { mContext.calcChecksum(h.item!!.mission.storage, ALGORITHMS[id]) }
                true
            }
            R.id.source -> {
                /*Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(h.item.mission.source));
                mContext.startActivity(intent);*/
                try {
                    val intent = NavigationHelper.getIntentByLink(mContext, h.item!!.mission.source)
                    intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
                    mContext.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Selected item has a invalid source", e)
                }
                true
            }
            else -> false
        }
    }

    fun applyChanges() {
        mIterator.start()
        DiffUtil.calculateDiff(mIterator, true).dispatchUpdatesTo(this)
        mIterator.end()
        checkEmptyMessageVisibility()
        if (mClear != null) mClear!!.isVisible = mIterator.hasFinishedMissions()
    }

    fun forceUpdate() {
        mIterator.start()
        mIterator.end()
        for (item in mPendingDownloadsItems) {
            item.resetSpeedMeasure()
        }
        notifyDataSetChanged()
    }

    fun setLinear(isLinear: Boolean) {
        mLayout = if (isLinear) R.layout.mission_item_linear else R.layout.mission_item
    }

    fun setClearButton(clearButton: MenuItem) {
        if (mClear == null) clearButton.isVisible = mIterator.hasFinishedMissions()
        mClear = clearButton
    }

    fun setMasterButtons(startButton: MenuItem?, pauseButton: MenuItem?) {
        val init = mStartButton == null || mPauseButton == null
        mStartButton = startButton
        mPauseButton = pauseButton
        if (init) checkMasterButtonsVisibility()
    }

    private fun checkEmptyMessageVisibility() {
        val flag = if (mIterator.oldListSize > 0) View.GONE else View.VISIBLE
        if (mEmptyMessage.visibility != flag) mEmptyMessage.visibility = flag
    }

    fun checkMasterButtonsVisibility() {
        val state = mIterator.hasValidPendingMissions()
        Log.d(TAG, "checkMasterButtonsVisibility() running=" + state[0] + " paused=" + state[1])
        setButtonVisible(mPauseButton, state[0])
        setButtonVisible(mStartButton, state[1])
    }

    fun refreshMissionItems() {
        for (h in mPendingDownloadsItems) {
            if ((h.item!!.mission as DownloadMission).running) continue
            updateProgress(h)
            h.resetSpeedMeasure()
        }
    }

    fun onDestroy() {
        mDeleter.dispose()
    }

    fun onResume() {
        mDeleter.resume()
        mHandler.post(rUpdater)
    }

    fun onPaused() {
        mDeleter.pause()
        mHandler.removeCallbacks(rUpdater)
    }

    fun recoverMission(mission: DownloadMission) {
        val h = getViewHolder(mission) ?: return
        mission.errObject = null
        mission.resetState(true, false, DownloadMission.ERROR_NOTHING)
        h.status.text = UNDEFINED_PROGRESS
        h.size.text = Utility.formatBytes(mission.getLength())
        h.progress.setMarquee(true)
        mDownloadManager.resumeMission(mission)
    }

    private fun updater() {
        for (h in mPendingDownloadsItems) {
            // check if the mission is running first
            if (!(h.item!!.mission as DownloadMission).running) continue
            updateProgress(h)
        }
        mHandler.postDelayed(rUpdater, 1000)
    }

    private fun isNotFinite(value: Double) = value.isNaN() || value.isInfinite()

    fun setRecover(callback: RecoverHelper) {
        mRecover = callback
    }

    internal inner class ViewHolderItem(view: View?) : RecyclerView.ViewHolder(view!!) {
        var item: MissionItem? = null
        var status: TextView
        var icon: ImageView
        var name: TextView
        var size: TextView
        var progress = ProgressDrawable()
        var popupMenu: PopupMenu
        var retry: MenuItem
        var cancel: MenuItem
        var start: MenuItem
        var pause: MenuItem
        var open: MenuItem
        var queue: MenuItem
        private var showError: MenuItem
        var delete: MenuItem
        var source: MenuItem
        var checksum: MenuItem
        var lastTimestamp: Long = -1
        var lastDone = 0.0
        var lastSpeedIdx = 0
        var lastSpeed = FloatArray(3)
        private var estimatedTimeArrival = UNDEFINED_ETA

        private fun showPopupMenu() {
            retry.isVisible = false
            cancel.isVisible = false
            start.isVisible = false
            pause.isVisible = false
            open.isVisible = false
            queue.isVisible = false
            showError.isVisible = false
            delete.isVisible = false
            source.isVisible = false
            checksum.isVisible = false
            val mission = if (item!!.mission is DownloadMission) item!!.mission as DownloadMission else null
            if (mission != null) {
                if (mission.hasInvalidStorage()) {
                    retry.isVisible = true
                    delete.isVisible = true
                    showError.isVisible = true
                } else if (mission.isPsRunning) {
                    when (mission.errCode) {
                        DownloadMission.ERROR_INSUFFICIENT_STORAGE, DownloadMission.ERROR_POSTPROCESSING_HOLD -> {
                            retry.isVisible = true
                            cancel.isVisible = true
                            showError.isVisible = true
                        }
                    }
                } else {
                    if (mission.running) {
                        pause.isVisible = true
                    } else {
                        if (mission.errCode != DownloadMission.ERROR_NOTHING) {
                            showError.isVisible = true
                        }
                        queue.isChecked = mission.enqueued
                        delete.isVisible = true
                        val flag = !mission.isPsFailed && mission.urls.isNotEmpty()
                        start.isVisible = flag
                        queue.isVisible = flag
                    }
                }
            } else {
                open.isVisible = true
                delete.isVisible = true
                checksum.isVisible = true
            }
            if (item!!.mission.source.isNullOrEmpty()) {
                source.isVisible = true
            }
            popupMenu.show()
        }

        private fun buildPopup(button: View): PopupMenu {
            val popup = PopupMenu(mContext, button)
            popup.inflate(R.menu.mission)
            popup.setOnMenuItemClickListener { option: MenuItem -> handlePopupItem(this, option) }
            return popup
        }

        fun resetSpeedMeasure() {
            estimatedTimeArrival = UNDEFINED_ETA
            lastTimestamp = -1
            lastSpeedIdx = -1
        }

        init {
            itemView.findViewById<View>(R.id.item_bkg).background = progress
            status = itemView.findViewById(R.id.item_status)
            name = itemView.findViewById(R.id.item_name)
            icon = itemView.findViewById(R.id.item_icon)
            size = itemView.findViewById(R.id.item_size)
            name.isSelected = true
            val button = itemView.findViewById<ImageView>(R.id.item_more)
            popupMenu = buildPopup(button)
            button.setOnClickListener { showPopupMenu() }
            val menu = popupMenu.menu
            retry = menu.findItem(R.id.retry)
            cancel = menu.findItem(R.id.cancel)
            start = menu.findItem(R.id.start)
            pause = menu.findItem(R.id.pause)
            open = menu.findItem(R.id.menu_item_share)
            queue = menu.findItem(R.id.queue)
            showError = menu.findItem(R.id.error_message_view)
            delete = menu.findItem(R.id.delete)
            source = menu.findItem(R.id.source)
            checksum = menu.findItem(R.id.checksum)
            itemView.isHapticFeedbackEnabled = true
            itemView.setOnClickListener { if (item!!.mission is FinishedMission) viewWithFileProvider(item!!.mission) }
            itemView.setOnLongClickListener { v: View ->
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showPopupMenu()
                true
            }
        }
    }

    internal inner class ViewHolderHeader(view: View?) : RecyclerView.ViewHolder(view!!) {
        var header: TextView = itemView.findViewById(R.id.item_name)
    }

    interface RecoverHelper {
        fun tryRecover(mission: DownloadMission?)
    }

    companion object {
        private val ALGORITHMS = SparseArray<String>()
        private const val TAG = "MissionAdapter"
        private const val UNDEFINED_PROGRESS = "--.-%"
        private const val DEFAULT_MIME_TYPE = "*/*"
        private const val UNDEFINED_ETA = "--:--"

        init {
            ALGORITHMS[R.id.md5] = "MD5"
            ALGORITHMS[R.id.sha1] = "SHA1"
        }

        private fun resolveMimeType(mission: Mission): String {
            var mimeType: String?
            if (!mission.storage.isInvalid) {
                mimeType = mission.storage.type
                if (!mimeType.isNullOrEmpty() && mimeType != StoredFileHelper.DEFAULT_MIME) return mimeType
            }
            val ext = Utility.getFileExt(mission.storage.name) ?: return DEFAULT_MIME_TYPE
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.substring(1))
            return mimeType ?: DEFAULT_MIME_TYPE
        }

        private fun setButtonVisible(button: MenuItem?, visible: Boolean) {
            if (button!!.isVisible != visible)
                button.isVisible = visible
        }
    }
}
