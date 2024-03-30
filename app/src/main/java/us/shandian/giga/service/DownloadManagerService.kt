package us.shandian.giga.service

import android.R
import android.app.Notification
import android.app.Service
import android.content.Context
import android.graphics.Bitmap
import android.net.Network
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.BuildConfig.APPLICATION_ID
import org.schabi.newpipe.util.Localization
import java.io.File
import java.io.IOException
import java.util.Objects

class DownloadManagerService() : Service() {
    private var mBinder: DownloadManagerBinder? = null
    private var mManager: DownloadManager? = null
    private var mNotification: Notification? = null
    private var mHandler: Handler? = null
    private var mForeground: Boolean = false
    private var mNotificationManager: NotificationManager? = null
    private var mDownloadNotificationEnable: Boolean = true
    private var downloadDoneCount: Int = 0
    private var downloadDoneNotification: NotificationCompat.Builder? = null
    private var downloadDoneList: StringBuilder? = null
    private val mEchoObservers: MutableList<Handler.Callback> = ArrayList(1)
    private var mConnectivityManager: ConnectivityManager? = null
    private var mNetworkStateListenerL: ConnectivityManager.NetworkCallback? = null
    private var mPrefs: SharedPreferences? = null
    private val mPrefChangeListener: OnSharedPreferenceChangeListener = OnSharedPreferenceChangeListener({ prefs: SharedPreferences, key: String -> handlePreferenceChange(prefs, key) })
    private var mLockAcquired: Boolean = false
    private var mLock: LockManager? = null
    private var downloadFailedNotificationID: Int = DOWNLOADS_NOTIFICATION_ID + 1
    private var downloadFailedNotification: NotificationCompat.Builder? = null
    private val mFailedDownloads: SparseArrayCompat<DownloadMission> = SparseArrayCompat<DownloadMission>(5)
    private var icLauncher: Bitmap? = null
    private var icDownloadDone: Bitmap? = null
    private var icDownloadFailed: Bitmap? = null
    private var mOpenDownloadList: PendingIntent? = null

    /**
     * notify media scanner on downloaded media file ...
     *
     * @param file the downloaded file uri
     */
    private fun notifyMediaScanner(file: Uri) {
        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, file))
    }

    public override fun onCreate() {
        super.onCreate()
        if (DEBUG) {
            Log.d(TAG, "onCreate")
        }
        mBinder = DownloadManagerBinder()
        mHandler = Handler(Handler.Callback({ msg: Message -> handleMessage(msg) }))
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        mManager = DownloadManager(this, mHandler!!, loadMainVideoStorage(), loadMainAudioStorage())
        val openDownloadListIntent: Intent = Intent(this, DownloadActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
        mOpenDownloadList = PendingIntentCompat.getActivity(this, 0,
                openDownloadListIntent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)
        icLauncher = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setContentIntent(mOpenDownloadList)
                .setSmallIcon(R.drawable.stat_sys_download)
                .setLargeIcon(icLauncher)
                .setContentTitle(getString(R.string.msg_running))
                .setContentText(getString(R.string.msg_running_detail))
        mNotification = builder.build()
        mNotificationManager = ContextCompat.getSystemService<NotificationManager>(this,
                NotificationManager::class.java)
        mConnectivityManager = ContextCompat.getSystemService<ConnectivityManager>(this,
                ConnectivityManager::class.java)
        mNetworkStateListenerL = object : ConnectivityManager.NetworkCallback() {
            public override fun onAvailable(network: Network) {
                handleConnectivityState(false)
            }

            public override fun onLost(network: Network) {
                handleConnectivityState(false)
            }
        }
        mConnectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), mNetworkStateListenerL)
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefChangeListener)
        handlePreferenceChange(mPrefs, getString(R.string.downloads_cross_network))
        handlePreferenceChange(mPrefs, getString(R.string.downloads_maximum_retry))
        handlePreferenceChange(mPrefs, getString(R.string.downloads_queue_limit))
        mLock = LockManager(this)
    }

    public override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(TAG, if (intent == null) "Restarting" else "Starting")
        }
        if (intent == null) return START_NOT_STICKY
        Log.i(TAG, "Got intent: " + intent)
        val action: String? = intent.getAction()
        if (action != null) {
            if ((action == Intent.ACTION_RUN)) {
                mHandler!!.post(Runnable({ startMission(intent) }))
            } else if (downloadDoneNotification != null) {
                if ((action == ACTION_RESET_DOWNLOAD_FINISHED) || (action == ACTION_OPEN_DOWNLOADS_FINISHED)) {
                    downloadDoneCount = 0
                    downloadDoneList!!.setLength(0)
                }
                if ((action == ACTION_OPEN_DOWNLOADS_FINISHED)) {
                    startActivity(Intent(this, DownloadActivity::class.java)
                            .setAction(Intent.ACTION_MAIN)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (DEBUG) {
            Log.d(TAG, "Destroying")
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (mNotificationManager != null && downloadDoneNotification != null) {
            downloadDoneNotification!!.setDeleteIntent(null) // prevent NewPipe running when is killed, cleared from recent, etc
            mNotificationManager.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification!!.build())
        }
        manageLock(false)
        mConnectivityManager.unregisterNetworkCallback(mNetworkStateListenerL)
        mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener)
        if (icDownloadDone != null) icDownloadDone!!.recycle()
        if (icDownloadFailed != null) icDownloadFailed!!.recycle()
        if (icLauncher != null) icLauncher!!.recycle()
        mHandler = null
        mManager!!.pauseAllMissions(true)
    }

    public override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    private fun handleMessage(msg: Message): Boolean {
        if (mHandler == null) return true
        val mission: DownloadMission = msg.obj as DownloadMission
        when (msg.what) {
            MESSAGE_FINISHED -> {
                notifyMediaScanner(mission.storage.getUri())
                notifyFinishedDownload(mission.storage.getName())
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
        if (msg.what != MESSAGE_ERROR) mFailedDownloads.remove(mFailedDownloads.indexOfValue(mission))
        for (observer: Handler.Callback in mEchoObservers) observer.handleMessage(msg)
        return true
    }

    private fun handleConnectivityState(updateOnly: Boolean) {
        val info: NetworkInfo? = mConnectivityManager.getActiveNetworkInfo()
        val status: DownloadManager.NetworkState
        if (info == null) {
            status = DownloadManager.NetworkState.Unavailable
            Log.i(TAG, "Active network [connectivity is unavailable]")
        } else {
            val connected: Boolean = info.isConnected()
            val metered: Boolean = mConnectivityManager.isActiveNetworkMetered()
            if (connected) status = if (metered) DownloadManager.NetworkState.MeteredOperating else DownloadManager.NetworkState.Operating else status = DownloadManager.NetworkState.Unavailable
            Log.i(TAG, "Active network [connected=" + connected + " metered=" + metered + "] " + info.toString())
        }
        if (mManager == null) return  // avoid race-conditions while the service is starting
        mManager!!.handleConnectivityState(status, updateOnly)
    }

    private fun handlePreferenceChange(prefs: SharedPreferences, key: String) {
        if ((getString(R.string.downloads_maximum_retry) == key)) {
            try {
                val value: String? = prefs.getString(key, getString(R.string.downloads_maximum_retry_default))
                mManager!!.mPrefMaxRetry = if (value == null) 0 else value.toInt()
            } catch (e: Exception) {
                mManager!!.mPrefMaxRetry = 0
            }
            mManager!!.updateMaximumAttempts()
        } else if ((getString(R.string.downloads_cross_network) == key)) {
            mManager!!.mPrefMeteredDownloads = prefs.getBoolean(key, false)
        } else if ((getString(R.string.downloads_queue_limit) == key)) {
            mManager!!.mPrefQueueLimit = prefs.getBoolean(key, true)
        } else if ((getString(R.string.download_path_video_key) == key)) {
            mManager!!.mMainStorageVideo = loadMainVideoStorage()
        } else if ((getString(R.string.download_path_audio_key) == key)) {
            mManager!!.mMainStorageAudio = loadMainAudioStorage()
        }
    }

    fun updateForegroundState(state: Boolean) {
        if (state == mForeground) return
        if (state) {
            startForeground(FOREGROUND_NOTIFICATION_ID, mNotification)
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        manageLock(state)
        mForeground = state
    }

    private fun startMission(intent: Intent) {
        val urls: Array<String> = intent.getStringArrayExtra(EXTRA_URLS)
        val path: Uri = IntentCompat.getParcelableExtra<Uri>(intent, EXTRA_PATH, Uri::class.java)
        val parentPath: Uri = IntentCompat.getParcelableExtra<Uri>(intent, EXTRA_PARENT_PATH, Uri::class.java)
        val threads: Int = intent.getIntExtra(EXTRA_THREADS, 1)
        val kind: Char = intent.getCharExtra(EXTRA_KIND, '?')
        val psName: String? = intent.getStringExtra(EXTRA_POSTPROCESSING_NAME)
        val psArgs: Array<String> = intent.getStringArrayExtra(EXTRA_POSTPROCESSING_ARGS)
        val source: String = intent.getStringExtra(EXTRA_SOURCE)
        val nearLength: Long = intent.getLongExtra(EXTRA_NEAR_LENGTH, 0)
        val tag: String = intent.getStringExtra(EXTRA_STORAGE_TAG)
        val recovery: ArrayList<MissionRecoveryInfo> = IntentCompat.getParcelableArrayListExtra<MissionRecoveryInfo>(intent, EXTRA_RECOVERY_INFO,
                MissionRecoveryInfo::class.java)
        Objects.requireNonNull<ArrayList<MissionRecoveryInfo>>(recovery)
        val storage: StoredFileHelper
        try {
            storage = StoredFileHelper(this, parentPath, path, tag)
        } catch (e: IOException) {
            throw RuntimeException(e) // this never should happen
        }
        val ps: Postprocessing?
        if (psName == null) ps = null else ps = Postprocessing.Companion.getAlgorithm(psName, psArgs)
        val mission: DownloadMission = DownloadMission(urls, storage, kind, ps)
        mission.threadCount = threads
        mission.source = source
        mission.nearLength = nearLength
        mission.recoveryInfo = recovery.toTypedArray<MissionRecoveryInfo>()
        if (ps != null) ps.setTemporalDir(DownloadManager.Companion.pickAvailableTemporalDir(this))
        handleConnectivityState(true) // first check the actual network status
        mManager!!.startMission(mission)
    }

    fun notifyFinishedDownload(name: String) {
        if (!mDownloadNotificationEnable || mNotificationManager == null) {
            return
        }
        if (downloadDoneNotification == null) {
            downloadDoneList = StringBuilder(name.length)
            icDownloadDone = BitmapFactory.decodeResource(getResources(), R.drawable.stat_sys_download_done)
            downloadDoneNotification = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                    .setAutoCancel(true)
                    .setLargeIcon(icDownloadDone)
                    .setSmallIcon(R.drawable.stat_sys_download_done)
                    .setDeleteIntent(makePendingIntent(ACTION_RESET_DOWNLOAD_FINISHED))
                    .setContentIntent(makePendingIntent(ACTION_OPEN_DOWNLOADS_FINISHED))
        }
        downloadDoneCount++
        if (downloadDoneCount == 1) {
            downloadDoneList!!.append(name)
            downloadDoneNotification!!.setContentTitle(null)
            downloadDoneNotification!!.setContentText(Localization.downloadCount(this, downloadDoneCount))
            downloadDoneNotification!!.setStyle(NotificationCompat.BigTextStyle()
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
        mNotificationManager.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification!!.build())
    }

    fun notifyFailedDownload(mission: DownloadMission) {
        if (!mDownloadNotificationEnable || mFailedDownloads.containsValue(mission)) return
        val id: Int = downloadFailedNotificationID++
        mFailedDownloads.put(id, mission)
        if (downloadFailedNotification == null) {
            icDownloadFailed = BitmapFactory.decodeResource(getResources(), R.drawable.stat_sys_warning)
            downloadFailedNotification = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                    .setAutoCancel(true)
                    .setLargeIcon(icDownloadFailed)
                    .setSmallIcon(R.drawable.stat_sys_warning)
                    .setContentIntent(mOpenDownloadList)
        }
        downloadFailedNotification!!.setContentTitle(getString(R.string.download_failed))
        downloadFailedNotification!!.setContentText(mission.storage.getName())
        downloadFailedNotification!!.setStyle(NotificationCompat.BigTextStyle()
                .bigText(mission.storage.getName()))
        mNotificationManager.notify(id, downloadFailedNotification!!.build())
    }

    private fun makePendingIntent(action: String): PendingIntent {
        val intent: Intent = Intent(this, DownloadManagerService::class.java).setAction(action)
        return PendingIntentCompat.getService(this, intent.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false)
    }

    private fun manageLock(acquire: Boolean) {
        if (acquire == mLockAcquired) return
        if (acquire) mLock.acquireWifiAndCpu() else mLock.releaseWifiAndCpu()
        mLockAcquired = acquire
    }

    private fun loadMainVideoStorage(): StoredDirectoryHelper? {
        return loadMainStorage(R.string.download_path_video_key, DownloadManager.Companion.TAG_VIDEO)
    }

    private fun loadMainAudioStorage(): StoredDirectoryHelper? {
        return loadMainStorage(R.string.download_path_audio_key, DownloadManager.Companion.TAG_AUDIO)
    }

    private fun loadMainStorage(@StringRes prefKey: Int, tag: String): StoredDirectoryHelper? {
        var path: String? = mPrefs.getString(getString(prefKey), null)
        if (path == null || path.isEmpty()) return null
        if (path.get(0) == File.separatorChar) {
            Log.i(TAG, "Old save path style present: " + path)
            path = ""
            mPrefs.edit().putString(getString(prefKey), "").apply()
        }
        try {
            return StoredDirectoryHelper(this, Uri.parse(path), tag)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load the storage of " + tag + " from " + path, e)
            Toast.makeText(this, R.string.no_available_dir, Toast.LENGTH_LONG).show()
        }
        return null
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Wrappers for DownloadManager
    ////////////////////////////////////////////////////////////////////////////////////////////////
    inner class DownloadManagerBinder() : Binder() {
        fun getDownloadManager(): DownloadManager? {
            return mManager
        }

        fun getMainStorageVideo(): StoredDirectoryHelper? {
            return mManager!!.mMainStorageVideo
        }

        fun getMainStorageAudio(): StoredDirectoryHelper? {
            return mManager!!.mMainStorageAudio
        }

        fun askForSavePath(): Boolean {
            return mPrefs.getBoolean(
                    this@DownloadManagerService.getString(R.string.downloads_storage_ask),
                    false
            )
        }

        fun addMissionEventListener(handler: Handler.Callback) {
            mEchoObservers.add(handler)
        }

        fun removeMissionEventListener(handler: Handler.Callback) {
            mEchoObservers.remove(handler)
        }

        fun clearDownloadNotifications() {
            if (mNotificationManager == null) return
            if (downloadDoneNotification != null) {
                mNotificationManager.cancel(DOWNLOADS_NOTIFICATION_ID)
                downloadDoneList!!.setLength(0)
                downloadDoneCount = 0
            }
            if (downloadFailedNotification != null) {
                while (downloadFailedNotificationID > DOWNLOADS_NOTIFICATION_ID) {
                    mNotificationManager.cancel(downloadFailedNotificationID)
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
        private val TAG: String = "DownloadManagerService"
        val MESSAGE_RUNNING: Int = 0
        val MESSAGE_PAUSED: Int = 1
        val MESSAGE_FINISHED: Int = 2
        val MESSAGE_ERROR: Int = 3
        val MESSAGE_DELETED: Int = 4
        private val FOREGROUND_NOTIFICATION_ID: Int = 1000
        private val DOWNLOADS_NOTIFICATION_ID: Int = 1001
        private val EXTRA_URLS: String = "DownloadManagerService.extra.urls"
        private val EXTRA_KIND: String = "DownloadManagerService.extra.kind"
        private val EXTRA_THREADS: String = "DownloadManagerService.extra.threads"
        private val EXTRA_POSTPROCESSING_NAME: String = "DownloadManagerService.extra.postprocessingName"
        private val EXTRA_POSTPROCESSING_ARGS: String = "DownloadManagerService.extra.postprocessingArgs"
        private val EXTRA_SOURCE: String = "DownloadManagerService.extra.source"
        private val EXTRA_NEAR_LENGTH: String = "DownloadManagerService.extra.nearLength"
        private val EXTRA_PATH: String = "DownloadManagerService.extra.storagePath"
        private val EXTRA_PARENT_PATH: String = "DownloadManagerService.extra.storageParentPath"
        private val EXTRA_STORAGE_TAG: String = "DownloadManagerService.extra.storageTag"
        private val EXTRA_RECOVERY_INFO: String = "DownloadManagerService.extra.recoveryInfo"
        private val ACTION_RESET_DOWNLOAD_FINISHED: String = APPLICATION_ID + ".reset_download_finished"
        private val ACTION_OPEN_DOWNLOADS_FINISHED: String = APPLICATION_ID + ".open_downloads_finished"

        /**
         * Start a new download mission
         *
         * @param context      the activity context
         * @param urls         array of urls to download
         * @param storage      where the file is saved
         * @param kind         type of file (a: audio  v: video  s: subtitle ?: file-extension defined)
         * @param threads      the number of threads maximal used to download chunks of the file.
         * @param psName       the name of the required post-processing algorithm, or `null` to ignore.
         * @param source       source url of the resource
         * @param psArgs       the arguments for the post-processing algorithm.
         * @param nearLength   the approximated final length of the file
         * @param recoveryInfo array of MissionRecoveryInfo, in case is required recover the download
         */
        fun startMission(context: Context?, urls: Array<String>?, storage: StoredFileHelper,
                         kind: Char, threads: Int, source: String?, psName: String?,
                         psArgs: Array<String>?, nearLength: Long,
                         recoveryInfo: ArrayList<MissionRecoveryInfo>?) {
            val intent: Intent = Intent(context, DownloadManagerService::class.java)
                    .setAction(Intent.ACTION_RUN)
                    .putExtra(EXTRA_URLS, urls)
                    .putExtra(EXTRA_KIND, kind)
                    .putExtra(EXTRA_THREADS, threads)
                    .putExtra(EXTRA_SOURCE, source)
                    .putExtra(EXTRA_POSTPROCESSING_NAME, psName)
                    .putExtra(EXTRA_POSTPROCESSING_ARGS, psArgs)
                    .putExtra(EXTRA_NEAR_LENGTH, nearLength)
                    .putExtra(EXTRA_RECOVERY_INFO, recoveryInfo)
                    .putExtra(EXTRA_PARENT_PATH, storage.getParentUri())
                    .putExtra(EXTRA_PATH, storage.getUri())
                    .putExtra(EXTRA_STORAGE_TAG, storage.getTag())
            context!!.startService(intent)
        }
    }
}
