package us.shandian.giga.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.collection.SparseArrayCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.preference.PreferenceManager
import java.io.File
import java.util.Objects
import okio.IOException
import java.util.*
import org.schabi.newpipe.BuildConfig.APPLICATION_ID
import org.schabi.newpipe.BuildConfig.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.download.DownloadActivity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.helper.LockManager
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.Localization
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager.NetworkState
import us.shandian.giga.util.Utility

class DownloadManagerService : Service() {

    private var mBinder: DownloadManagerBinder? = null
    private var mManager: DownloadManager? = null
    private var mNotification: Notification? = null
    private var mHandler: Handler? = null
    private var mForeground = false
    private var mNotificationManager: NotificationManager? = null
    private var mDownloadNotificationEnable = true

    private var downloadDoneCount = 0
    private var downloadDoneNotification: Builder? = null
    private var downloadDoneList: StringBuilder? = null

    private var mNotificationBuilder: Builder? = null
    private var mProgressUpdater: Runnable? = null

    private val mEchoObservers = ArrayList<Handler.Callback>(1)

    private var mConnectivityManager: ConnectivityManager? = null
    private var mNetworkStateListenerL: ConnectivityManager.NetworkCallback? = null

    private var mPrefs: SharedPreferences? = null
    private val mPrefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key -> handlePreferenceChange(prefs, key!!) }

    private var mLockAcquired = false
    private var mLock: LockManager? = null

    private var downloadFailedNotificationID = DOWNLOADS_NOTIFICATION_ID + 1
    private var downloadFailedNotification: Builder? = null
    private val mFailedDownloads = SparseArrayCompat<DownloadMission>(5)

    private var icLauncher: Bitmap? = null
    private var icDownloadDone: Bitmap? = null
    private var icDownloadFailed: Bitmap? = null

    private var mOpenDownloadList: PendingIntent? = null

    private fun notifyMediaScanner(file: Uri) {
        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, file))
    }

    override fun onCreate() {
        super.onCreate()

        if (DEBUG) {
            Log.d(TAG, "onCreate")
        }

        mBinder = DownloadManagerBinder()
        mHandler = Handler { msg -> handleMessage(msg) }

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        mManager = DownloadManager(this, mHandler!!, loadMainVideoStorage(), loadMainAudioStorage())

        val openDownloadListIntent = Intent(this, org.schabi.newpipe.MainActivity::class.java).apply {
            action = org.schabi.newpipe.MainActivity.ACTION_OPEN_DOWNLOADS
            putExtra(org.schabi.newpipe.MainActivity.EXTRA_DESTINATION, org.schabi.newpipe.MainActivity.DESTINATION_DOWNLOADS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        mOpenDownloadList = PendingIntentCompat.getActivity(
            this,
            0,
            openDownloadListIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        icLauncher = BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher)

        mNotificationBuilder = Builder(this, getString(R.string.notification_channel_id))
            .setContentIntent(mOpenDownloadList)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(icLauncher)
            .setContentTitle(getString(R.string.msg_running))
            .setContentText(getString(R.string.msg_running_detail))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        mNotification = mNotificationBuilder!!.build()

        mProgressUpdater = object : Runnable {
            override fun run() {
                if (mForeground) {
                    updateProgressNotification()
                    mHandler?.postDelayed(this, 500)
                }
            }
        }

        mNotificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        mConnectivityManager = ContextCompat.getSystemService(this, ConnectivityManager::class.java)

        mNetworkStateListenerL = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handleConnectivityState(false)
            }

            override fun onLost(network: Network) {
                handleConnectivityState(false)
            }
        }
        mConnectivityManager!!.registerNetworkCallback(NetworkRequest.Builder().build(), mNetworkStateListenerL!!)

        mPrefs!!.registerOnSharedPreferenceChangeListener(mPrefChangeListener)

        handlePreferenceChange(mPrefs!!, getString(R.string.downloads_cross_network))
        handlePreferenceChange(mPrefs!!, getString(R.string.downloads_maximum_retry))
        handlePreferenceChange(mPrefs!!, getString(R.string.downloads_queue_limit))

        mLock = LockManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(TAG, if (intent == null) "Restarting" else "Starting")
        }

        if (intent == null) return START_NOT_STICKY

        Log.i(TAG, "Got intent: $intent")
        val action = intent.action
        if (action != null) {
            if (action == Intent.ACTION_RUN) {
                mHandler!!.post { startMission(intent) }
            } else if (downloadDoneNotification != null) {
                if (action == ACTION_RESET_DOWNLOAD_FINISHED || action == ACTION_OPEN_DOWNLOADS_FINISHED) {
                    downloadDoneCount = 0
                    downloadDoneList!!.setLength(0)
                }
                if (action == ACTION_OPEN_DOWNLOADS_FINISHED) {
                    val openDownloadsIntent = Intent(this, org.schabi.newpipe.MainActivity::class.java).apply {
                        this.action = org.schabi.newpipe.MainActivity.ACTION_OPEN_DOWNLOADS
                        putExtra(org.schabi.newpipe.MainActivity.EXTRA_DESTINATION, org.schabi.newpipe.MainActivity.DESTINATION_DOWNLOADS)
                        this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(openDownloadsIntent)
                }
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        if (DEBUG) {
            Log.d(TAG, "Destroying")
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        if (mNotificationManager != null && downloadDoneNotification != null) {
            downloadDoneNotification!!.setDeleteIntent(null) // prevent NewPipe running when is killed, cleared from recent, etc
            mNotificationManager!!.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification!!.build())
        }

        manageLock(false)

        mConnectivityManager!!.unregisterNetworkCallback(mNetworkStateListenerL!!)

        mPrefs!!.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener)

        icDownloadDone?.recycle()
        icDownloadFailed?.recycle()
        icLauncher?.recycle()

        mHandler?.removeCallbacks(mProgressUpdater!!)
        mHandler = null
        mManager!!.pauseAllMissions(true)
    }

    override fun onBind(intent: Intent?): IBinder? = mBinder

    private fun handleMessage(msg: Message): Boolean {
        if (mHandler == null) return true

        val mission = msg.obj as DownloadMission

        when (msg.what) {
            MESSAGE_FINISHED -> {
                notifyMediaScanner(mission.storage!!.getUri())
                notifyFinishedDownload(mission.storage!!.getName()!!)
                mManager!!.setFinished(mission)
                handleConnectivityState(false)
                updateForegroundState(mManager!!.runMissions())
            }

            MESSAGE_RUNNING -> updateForegroundState(true)

            MESSAGE_ERROR -> {
                notifyFailedDownload(mission)
                handleConnectivityState(false)
                updateForegroundState(mManager!!.runMissions())
            }

            MESSAGE_PAUSED -> updateForegroundState(mManager!!.getRunningMissionsCount() > 0)
        }

        if (msg.what != MESSAGE_ERROR) {
            val idx = mFailedDownloads.indexOfValue(mission)
            if (idx >= 0) mFailedDownloads.removeAt(idx)
        }

        for (observer in mEchoObservers) {
            observer.handleMessage(msg)
        }

        return true
    }

    private fun handleConnectivityState(updateOnly: Boolean) {
        val info = mConnectivityManager!!.activeNetworkInfo
        val status: NetworkState

        if (info == null) {
            status = NetworkState.Unavailable
            Log.i(TAG, "Active network [connectivity is unavailable]")
        } else {
            val connected = info.isConnected
            val metered = mConnectivityManager!!.isActiveNetworkMetered

            status = if (connected) {
                if (metered) NetworkState.MeteredOperating else NetworkState.Operating
            } else {
                NetworkState.Unavailable
            }

            Log.i(TAG, "Active network [connected=$connected metered=$metered] $info")
        }

        if (mManager == null) return // avoid race-conditions while the service is starting
        mManager!!.handleConnectivityState(status, updateOnly)
    }

    private fun handlePreferenceChange(prefs: SharedPreferences, key: String) {
        when (key) {
            getString(R.string.downloads_maximum_retry) -> {
                try {
                    val value = prefs.getString(key, getString(R.string.downloads_maximum_retry_default))
                    mManager!!.mPrefMaxRetry = value?.toInt() ?: 0
                } catch (e: Exception) {
                    mManager!!.mPrefMaxRetry = 0
                }
                mManager!!.updateMaximumAttempts()
            }

            getString(R.string.downloads_cross_network) -> {
                mManager!!.mPrefMeteredDownloads = prefs.getBoolean(key, false)
            }

            getString(R.string.downloads_queue_limit) -> {
                mManager!!.mPrefQueueLimit = prefs.getBoolean(key, true)
            }

            getString(R.string.download_path_video_key) -> {
                mManager!!.mMainStorageVideo = loadMainVideoStorage()
            }

            getString(R.string.download_path_audio_key) -> {
                mManager!!.mMainStorageAudio = loadMainAudioStorage()
            }
        }
    }

    fun updateForegroundState(state: Boolean) {
        if (state == mForeground) {
            if (state) {
                mHandler?.removeCallbacks(mProgressUpdater!!)
                mHandler?.post(mProgressUpdater!!)
            }
            return
        }

        if (state) {
            startForeground(FOREGROUND_NOTIFICATION_ID, mNotification!!)
            mHandler?.removeCallbacks(mProgressUpdater!!)
            mHandler?.post(mProgressUpdater!!)
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            mHandler?.removeCallbacks(mProgressUpdater!!)
        }

        manageLock(state)

        mForeground = state
    }

    private fun updateProgressNotification() {
        if (!mForeground || mManager == null || mNotificationBuilder == null || mNotificationManager == null) return

        val missions = mManager!!.missions
        var totalDone: Long = 0
        var totalLength: Long = 0
        var allLengthsKnown = true
        var runningCount = 0
        var singleTitle: String? = null

        for (mission in missions) {
            if (mission is us.shandian.giga.get.DownloadMission && mission.running) {
                runningCount++
                totalDone += mission.done
                val effectiveLength = if (mission.length > 0) mission.length else mission.nearLength
                if (effectiveLength > 0) {
                    totalLength += effectiveLength
                } else {
                    allLengthsKnown = false
                }
                if (singleTitle == null) {
                    singleTitle = mission.title ?: mission.storage?.getName()
                }
            }
        }

        if (runningCount == 0) return

        if (totalLength > 0 && (allLengthsKnown || totalDone > 0)) {
            val progress = (totalDone * 100 / totalLength).toInt().coerceIn(0, 100)
            mNotificationBuilder!!.setProgress(100, progress, false)
            val sizeProgress = "${Utility.formatBytes(totalDone)} / ${Utility.formatBytes(totalLength)} ($progress%)"
            mNotificationBuilder!!.setContentText(sizeProgress)
            mNotificationBuilder!!.setContentTitle(
                if (runningCount == 1) (singleTitle ?: getString(R.string.msg_running))
                else "Downloading $runningCount items"
            )
        } else {
            mNotificationBuilder!!.setProgress(0, 0, true)
            mNotificationBuilder!!.setContentText(getString(R.string.msg_running_detail))
            mNotificationBuilder!!.setContentTitle(singleTitle ?: getString(R.string.msg_running))
        }

        mNotification = mNotificationBuilder!!.build()
        mNotificationManager!!.notify(FOREGROUND_NOTIFICATION_ID, mNotification)
    }

    private fun startMission(intent: Intent) {
        val urls = intent.getStringArrayExtra(EXTRA_URLS)
        val path = IntentCompat.getParcelableExtra(intent, EXTRA_PATH, Uri::class.java)
        val parentPath = IntentCompat.getParcelableExtra(intent, EXTRA_PARENT_PATH, Uri::class.java)
        val threads = intent.getIntExtra(EXTRA_THREADS, 1)
        val kind = intent.getCharExtra(EXTRA_KIND, '?')
        val psName = intent.getStringExtra(EXTRA_POSTPROCESSING_NAME)
        val psArgs = intent.getStringArrayExtra(EXTRA_POSTPROCESSING_ARGS)
        val nearLength = intent.getLongExtra(EXTRA_NEAR_LENGTH, 0)
        val tag = intent.getStringExtra(EXTRA_STORAGE_TAG)
        val streamInfo = intent.getSerializableExtra(EXTRA_STREAM_INFO) as StreamInfo
        val recovery = IntentCompat.getParcelableArrayListExtra(
            intent,
            EXTRA_RECOVERY_INFO,
            MissionRecoveryInfo::class.java
        )
        Objects.requireNonNull(recovery)

        val storage: StoredFileHelper = try {
            StoredFileHelper(this, parentPath, path!!, tag!!)
        } catch (e: IOException) {
            throw RuntimeException(e) // this never should happen
        }

        val ps = if (psName == null) null else Postprocessing.getAlgorithm(psName, psArgs, streamInfo)

        val mission = DownloadMission(urls!!, storage, kind, ps)
        mission.threadCount = threads
        mission.source = streamInfo.url
        mission.title = streamInfo.name
        mission.uploader = streamInfo.uploaderName
        mission.thumbnailUrl = org.schabi.newpipe.util.image.ImageStrategy.choosePreferredImage(streamInfo.thumbnails, org.schabi.newpipe.util.image.PreferredImageQuality.HIGH)
        mission.duration = streamInfo.duration
        mission.nearLength = nearLength
        mission.recoveryInfo = recovery!!.toTypedArray()

        ps?.setTemporalDir(DownloadManager.pickAvailableTemporalDir(this))

        handleConnectivityState(true) // first check the actual network status

        mManager!!.startMission(mission)
    }

    fun notifyFinishedDownload(name: String) {
        if (!mDownloadNotificationEnable || mNotificationManager == null) {
            return
        }

        if (downloadDoneNotification == null) {
            downloadDoneList = StringBuilder(name.length)

            icDownloadDone = BitmapFactory.decodeResource(this.resources, android.R.drawable.stat_sys_download_done)
            downloadDoneNotification = Builder(this, getString(R.string.notification_channel_id))
                .setAutoCancel(true)
                .setLargeIcon(icDownloadDone)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setDeleteIntent(makePendingIntent(ACTION_RESET_DOWNLOAD_FINISHED))
                .setContentIntent(makePendingIntent(ACTION_OPEN_DOWNLOADS_FINISHED))
        }

        downloadDoneCount++
        if (downloadDoneCount == 1) {
            downloadDoneList!!.append(name)

            downloadDoneNotification!!.setContentTitle(null)
            downloadDoneNotification!!.setContentText(Localization.downloadCount(this, downloadDoneCount))
            downloadDoneNotification!!.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(Localization.downloadCount(this, downloadDoneCount))
                    .bigText(name)
            )
        } else {
            downloadDoneList!!.append('\n')
            downloadDoneList!!.append(name)

            downloadDoneNotification!!.setStyle(NotificationCompat.BigTextStyle().bigText(downloadDoneList))
            downloadDoneNotification!!.setContentTitle(Localization.downloadCount(this, downloadDoneCount))
            downloadDoneNotification!!.setContentText(downloadDoneList)
        }

        mNotificationManager!!.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification!!.build())
    }

    fun notifyFailedDownload(mission: DownloadMission) {
        if (!mDownloadNotificationEnable || mFailedDownloads.containsValue(mission)) return

        val id = downloadFailedNotificationID++
        mFailedDownloads.put(id, mission)

        if (downloadFailedNotification == null) {
            icDownloadFailed = BitmapFactory.decodeResource(this.resources, android.R.drawable.stat_sys_warning)
            downloadFailedNotification = Builder(this, getString(R.string.notification_channel_id))
                .setAutoCancel(true)
                .setLargeIcon(icDownloadFailed)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentIntent(mOpenDownloadList)
        }

        downloadFailedNotification!!.setContentTitle(getString(R.string.download_failed))
        downloadFailedNotification!!.setContentText(mission.storage!!.getName())
        downloadFailedNotification!!.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(mission.storage!!.getName())
        )

        mNotificationManager!!.notify(id, downloadFailedNotification!!.build())
    }

    private fun makePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, DownloadManagerService::class.java).setAction(action)
        return PendingIntentCompat.getService(
            this,
            intent.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )!!
    }

    private fun manageLock(acquire: Boolean) {
        if (acquire == mLockAcquired) return

        if (acquire) {
            mLock!!.acquireWifiAndCpu()
        } else {
            mLock!!.releaseWifiAndCpu()
        }

        mLockAcquired = acquire
    }

    private fun loadMainVideoStorage(): StoredDirectoryHelper? {
        return loadMainStorage(R.string.download_path_video_key, DownloadManager.TAG_VIDEO)
    }

    private fun loadMainAudioStorage(): StoredDirectoryHelper? {
        return loadMainStorage(R.string.download_path_audio_key, DownloadManager.TAG_AUDIO)
    }

    private fun loadMainStorage(@StringRes prefKey: Int, tag: String): StoredDirectoryHelper? {
        var path = mPrefs!!.getString(getString(prefKey), null)

        if (path.isNullOrEmpty()) {
            val fallbackDir = if (tag == DownloadManager.TAG_VIDEO) {
                val movies = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                if (movies != null && movies.exists()) movies else File(getExternalFilesDir(null), "Movies").apply { mkdirs() }
            } else {
                val music = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                if (music != null && music.exists()) music else File(getExternalFilesDir(null), "Music").apply { mkdirs() }
            }
            return try {
                StoredDirectoryHelper(this, Uri.fromFile(fallbackDir), tag)
            } catch (e: Exception) {
                null
            }
        }

        if (path[0] == File.separatorChar) {
            Log.i(TAG, "Old save path style present: $path")
            path = ""
            mPrefs!!.edit().putString(getString(prefKey), "").apply()
        }

        return try {
            StoredDirectoryHelper(this, Uri.parse(path), tag)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load the storage of $tag from $path", e)
            val fallbackDir = if (tag == DownloadManager.TAG_VIDEO) {
                File(getExternalFilesDir(null), "Movies").apply { mkdirs() }
            } else {
                File(getExternalFilesDir(null), "Music").apply { mkdirs() }
            }
            try {
                StoredDirectoryHelper(this, Uri.fromFile(fallbackDir), tag)
            } catch (e2: Exception) {
                Toast.makeText(this, R.string.no_available_dir, Toast.LENGTH_LONG).show()
                null
            }
        }
    }

    inner class DownloadManagerBinder : Binder() {
        val downloadManager: DownloadManager?
            get() = mManager

        val mainStorageVideo: StoredDirectoryHelper?
            get() = mManager!!.mMainStorageVideo

        val mainStorageAudio: StoredDirectoryHelper?
            get() = mManager!!.mMainStorageAudio

        fun askForSavePath(): Boolean {
            return this@DownloadManagerService.mPrefs!!.getBoolean(
                this@DownloadManagerService.getString(R.string.downloads_storage_ask),
                false
            )
        }

        fun addMissionEventListener(callback: Handler.Callback) {
            mEchoObservers.add(callback)
        }

        fun removeMissionEventListener(callback: Handler.Callback) {
            mEchoObservers.remove(callback)
        }

        fun clearDownloadNotifications() {
            if (mNotificationManager == null) return
            if (downloadDoneNotification != null) {
                mNotificationManager!!.cancel(DOWNLOADS_NOTIFICATION_ID)
                downloadDoneList!!.setLength(0)
                downloadDoneCount = 0
            }
            if (downloadFailedNotification != null) {
                while (downloadFailedNotificationID > DOWNLOADS_NOTIFICATION_ID) {
                    mNotificationManager!!.cancel(downloadFailedNotificationID)
                    downloadFailedNotificationID--
                }
                mFailedDownloads.clear()
                downloadFailedNotificationID++
            }
        }

        fun enableNotifications(enable: Boolean) {
            mDownloadNotificationEnable = enable
        }
    }

    companion object {
        private const val TAG = "DownloadManagerService"

        const val MESSAGE_RUNNING: Int = 0
        const val MESSAGE_PAUSED: Int = 1
        const val MESSAGE_FINISHED: Int = 2
        const val MESSAGE_ERROR: Int = 3
        const val MESSAGE_DELETED: Int = 4

        private const val FOREGROUND_NOTIFICATION_ID = 1000
        private const val DOWNLOADS_NOTIFICATION_ID = 1001

        private const val EXTRA_URLS = "DownloadManagerService.extra.urls"
        private const val EXTRA_KIND = "DownloadManagerService.extra.kind"
        private const val EXTRA_THREADS = "DownloadManagerService.extra.threads"
        private const val EXTRA_POSTPROCESSING_NAME = "DownloadManagerService.extra.postprocessingName"
        private const val EXTRA_POSTPROCESSING_ARGS = "DownloadManagerService.extra.postprocessingArgs"
        private const val EXTRA_NEAR_LENGTH = "DownloadManagerService.extra.nearLength"
        private const val EXTRA_PATH = "DownloadManagerService.extra.storagePath"
        private const val EXTRA_PARENT_PATH = "DownloadManagerService.extra.storageParentPath"
        private const val EXTRA_STORAGE_TAG = "DownloadManagerService.extra.storageTag"
        private const val EXTRA_RECOVERY_INFO = "DownloadManagerService.extra.recoveryInfo"
        private const val EXTRA_STREAM_INFO = "DownloadManagerService.extra.streamInfo"

        private val ACTION_RESET_DOWNLOAD_FINISHED = "$APPLICATION_ID.reset_download_finished"
        private val ACTION_OPEN_DOWNLOADS_FINISHED = "$APPLICATION_ID.open_downloads_finished"

        @JvmStatic
        fun startMission(
            context: Context,
            urls: Array<String>,
            storage: StoredFileHelper,
            kind: Char,
            threads: Int,
            streamInfo: StreamInfo,
            psName: String?,
            psArgs: Array<String>?,
            nearLength: Long,
            recoveryInfo: ArrayList<MissionRecoveryInfo>
        ) {
            val intent = Intent(context, DownloadManagerService::class.java)
                .setAction(Intent.ACTION_RUN)
                .putExtra(EXTRA_URLS, urls)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_THREADS, threads)
                .putExtra(EXTRA_POSTPROCESSING_NAME, psName)
                .putExtra(EXTRA_POSTPROCESSING_ARGS, psArgs)
                .putExtra(EXTRA_NEAR_LENGTH, nearLength)
                .putExtra(EXTRA_RECOVERY_INFO, recoveryInfo)
                .putExtra(EXTRA_PARENT_PATH, storage.getParentUri())
                .putExtra(EXTRA_PATH, storage.getUri())
                .putExtra(EXTRA_STORAGE_TAG, storage.tag)
                .putExtra(EXTRA_STREAM_INFO, streamInfo)

            context.startService(intent)
        }
    }
}
