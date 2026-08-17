package us.shandian.giga.service

import android.content.Context
import android.os.Handler
import android.util.Log

import org.schabi.newpipe.BuildConfig.DEBUG
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.DownloadMission.Companion.ERROR_NOTHING
import us.shandian.giga.get.DownloadMission.Companion.ERROR_PROGRESS_LOST
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission
import us.shandian.giga.get.sqlite.FinishedMissionStore
import us.shandian.giga.util.Utility
import java.io.File
import okio.IOException
import java.util.*

class DownloadManager(
    context: Context,
    private val mHandler: Handler,
    var mMainStorageVideo: StoredDirectoryHelper?,
    var mMainStorageAudio: StoredDirectoryHelper?
) {
    enum class NetworkState { Unavailable, Operating, MeteredOperating }

    private val mFinishedMissionStore: FinishedMissionStore = FinishedMissionStore(context)
    private val mMissionsPending = ArrayList<DownloadMission>()
    private val mMissionsFinished: ArrayList<FinishedMission>
    private val mPendingMissionsDir: File

    private var mLastNetworkStatus = NetworkState.Unavailable

    @JvmField
    var mPrefMaxRetry: Int = 0
    @JvmField
    var mPrefMeteredDownloads: Boolean = false
    @JvmField
    var mPrefQueueLimit: Boolean = false
    private var mSelfMissionsControl: Boolean = false

    val missions: List<Mission>
        get() = synchronized(this) {
            mMissionsPending.toList() + mMissionsFinished.toList()
        }

    init {
        if (DEBUG) {
            Log.d(TAG, "new DownloadManager instance. 0x${Integer.toHexString(this.hashCode())}")
        }

        mMissionsFinished = loadFinishedMissions()
        mPendingMissionsDir = getPendingDir(context)

        loadPendingMissions(context)
    }

    private fun loadFinishedMissions(): ArrayList<FinishedMission> {
        val finishedMissions = mFinishedMissionStore.loadFinishedMissions()

        val iterator = finishedMissions.iterator()
        while (iterator.hasNext()) {
            val mission = iterator.next()
            if (!mission.storage!!.existsAsFile()) {
                if (DEBUG) Log.d(TAG, "downloaded file removed: ${mission.storage!!.getName()}")
                mFinishedMissionStore.deleteMission(mission)
                iterator.remove()
            }
        }

        return finishedMissions
    }

    private fun loadPendingMissions(ctx: Context) {
        val subs = mPendingMissionsDir.listFiles() ?: run {
            Log.e(TAG, "listFiles() returned null")
            return
        }
        if (subs.isEmpty()) return

        if (DEBUG) {
            Log.d(TAG, "Loading pending downloads from directory: ${mPendingMissionsDir.absolutePath}")
        }

        val tempDir = pickAvailableTemporalDir(ctx)
        Log.i(TAG, "using '$tempDir' as temporal directory")

        for (sub in subs) {
            if (!sub.isFile) continue
            if (sub.name == ".tmp") continue

            try {
                val mis = Utility.readFromFile<DownloadMission>(sub)
                if (mis == null) {
                    sub.delete()
                    continue
                }

                if (mis.isFinished()) {
                    setFinished(mis)
                    sub.delete()
                    continue
                }

                if (mis.hasInvalidStorage() && mis.errCode != ERROR_PROGRESS_LOST) {
                    if (mis.storage == null) {
                        sub.delete()
                        continue
                    }
                }

                try {
                    mis.jobs?.clear()
                } catch (e: Exception) {
                    mis.jobs = Collections.synchronizedList(mutableListOf())
                }

                var exists: Boolean
                try {
                    mis.storage = StoredFileHelper.deserialize(mis.storage!!, ctx)
                    exists = !mis.storage!!.isInvalid() && mis.storage!!.existsAsFile()
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to load the file source of ${mis.storage}", ex)
                    exists = false
                }

                if (mis.isPsRunning()) {
                    if (mis.psAlgorithm?.worksOnSameFile == true) {
                        if (exists && mis.storage!!.isDirect() && !mis.storage!!.delete())
                            Log.w(TAG, "Unable to delete incomplete download file: ${sub.path}")
                    }

                    mis.psState = 0
                    mis.errCode = DownloadMission.ERROR_POSTPROCESSING_STOPPED
                } else if (!exists) {
                    tryRecover(mis)
                    if (mis.isInitialized() && mis.errCode == ERROR_NOTHING) {
                        mis.resetState(rollback = true, persistChanges = true, errorCode = ERROR_PROGRESS_LOST)
                    }
                }

                mis.psAlgorithm?.let {
                    it.cleanupTemporalDir()
                    it.setTemporalDir(tempDir)
                }

                mis.metadata = sub
                mis.maxRetry = mPrefMaxRetry
                mis.mHandler = mHandler

                mMissionsPending.add(mis)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load pending mission from ${sub.path}", t)
                try { sub.delete() } catch (_: Exception) {}
            }
        }

        if (mMissionsPending.size > 1) {
            mMissionsPending.sortBy { it.timestamp }
        }
    }

    fun startMission(mission: DownloadMission) {
        synchronized(this) {
            mission.timestamp = System.currentTimeMillis()
            mission.mHandler = mHandler
            mission.maxRetry = mPrefMaxRetry

            while (true) {
                mission.metadata = File(mPendingMissionsDir, mission.timestamp.toString())
                if (!mission.metadata!!.isFile && !mission.metadata!!.exists()) {
                    try {
                        if (!mission.metadata!!.createNewFile())
                            throw RuntimeException("Cant create download metadata file")
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                    break
                }
                mission.timestamp = System.currentTimeMillis()
            }

            mSelfMissionsControl = true
            mMissionsPending.add(mission)

            Utility.writeToFile(mission.metadata!!, mission)

            if (mission.storage == null) {
                mission.errCode = DownloadMission.ERROR_FILE_CREATION
                if (mission.errObject != null)
                    mission.errObject = IOException("DownloadMission.storage == NULL")
                return
            }

            val start = !mPrefQueueLimit || getRunningMissionsCount() < 1

            if (canDownloadInCurrentNetwork() && start) {
                mission.start()
            }
        }
    }

    fun resumeMission(mission: DownloadMission) {
        if (!mission.running) {
            mission.start()
        }
    }

    fun pauseMission(mission: DownloadMission) {
        if (mission.running) {
            mission.setEnqueued(false)
            mission.pause()
        }
    }

    fun deleteMission(mission: Mission, alsoDeleteFile: Boolean) {
        synchronized(this) {
            if (mission is DownloadMission) {
                mMissionsPending.remove(mission)
            } else if (mission is FinishedMission) {
                mMissionsFinished.remove(mission)
                mFinishedMissionStore.deleteMission(mission)
            }

            if (alsoDeleteFile) {
                mission.delete()
            }
        }
    }

    fun forgetMission(storage: StoredFileHelper) {
        synchronized(this) {
            val mission = getAnyMission(storage) ?: return

            if (mission is DownloadMission) {
                mMissionsPending.remove(mission)
            } else if (mission is FinishedMission) {
                mMissionsFinished.remove(mission)
                mFinishedMissionStore.deleteMission(mission)
            }

            mission.storage = null
            mission.delete()
        }
    }

    fun tryRecover(mission: DownloadMission) {
        val mainStorage = getMainStorage(mission.storage!!.tag!!)

        if (!mission.storage!!.isInvalid() && mission.storage!!.create()) return

        mission.storage!!.invalidate()

        if (mainStorage == null) return

        val newStorage = mainStorage.createFile(mission.storage!!.getName()!!, mission.storage!!.getType())

        if (newStorage != null) mission.storage = newStorage
    }

    private fun getPendingMission(storage: StoredFileHelper): DownloadMission? {
        return mMissionsPending.find { it.storage == storage }
    }

    private fun getFinishedMissionIndex(storage: StoredFileHelper): Int {
        val iterator = mMissionsFinished.iterator()
        var i = 0
        while (iterator.hasNext()) {
            val mission = iterator.next()
            if (mission.storage == storage) {
                if (!storage.existsAsFile() || storage.length() == 0L) {
                    if (DEBUG) Log.d(TAG, "matched downloaded file removed: ${storage.getName()}")
                    mFinishedMissionStore.deleteMission(mission)
                    iterator.remove()
                    return -1
                }
                return i
            }
            i++
        }
        return -1
    }

    private fun getAnyMission(storage: StoredFileHelper): Mission? {
        synchronized(this) {
            val pending = getPendingMission(storage)
            if (pending != null) return pending

            val idx = getFinishedMissionIndex(storage)
            if (idx >= 0) return mMissionsFinished[idx]
        }
        return null
    }

    fun getRunningMissionsCount(): Int {
        synchronized(this) {
            return mMissionsPending.count { it.running && !it.isPsFailed() && !it.isFinished() }
        }
    }

    fun pauseAllMissions(force: Boolean) {
        synchronized(this) {
            for (mission in mMissionsPending) {
                if (!mission.running || mission.isPsRunning() || mission.isFinished()) continue

                if (force) {
                    mission.initJob = null
                    mission.jobs.clear()
                }

                mission.pause()
            }
        }
    }

    fun startAllMissions() {
        synchronized(this) {
            for (mission in mMissionsPending) {
                if (mission.running || mission.isCorrupt()) continue
                mission.start()
            }
        }
    }

    fun setFinished(mission: DownloadMission) {
        synchronized(this) {
            mMissionsPending.remove(mission)
            mMissionsFinished.add(0, FinishedMission(mission))
            mFinishedMissionStore.addFinishedMission(mission)
        }
    }

    fun runMissions(): Boolean {
        synchronized(this) {
            if (mMissionsPending.isEmpty()) return false
            if (!canDownloadInCurrentNetwork()) return false

            if (mPrefQueueLimit) {
                if (mMissionsPending.any { !it.isFinished() && it.running }) return true
            }

            var flag = false
            for (mission in mMissionsPending) {
                if (mission.running || !mission.enqueued || mission.isFinished())
                    continue

                resumeMission(mission)
                if (mission.errCode != ERROR_NOTHING) continue

                if (mPrefQueueLimit) return true
                flag = true
            }

            return flag
        }
    }



    fun forgetFinishedDownloads() {
        synchronized(this) {
            for (mission in mMissionsFinished) {
                mFinishedMissionStore.deleteMission(mission)
            }
            mMissionsFinished.clear()
        }
    }

    private fun canDownloadInCurrentNetwork(): Boolean {
        if (mLastNetworkStatus == NetworkState.Unavailable) return false
        return !(mPrefMeteredDownloads && mLastNetworkStatus == NetworkState.MeteredOperating)
    }

    fun handleConnectivityState(currentStatus: NetworkState, updateOnly: Boolean) {
        if (currentStatus == mLastNetworkStatus) return

        mLastNetworkStatus = currentStatus
        if (currentStatus == NetworkState.Unavailable) return

        if (!mSelfMissionsControl || updateOnly) return

        val isMetered = mPrefMeteredDownloads && mLastNetworkStatus == NetworkState.MeteredOperating

        synchronized(this) {
            for (mission in mMissionsPending) {
                if (mission.isCorrupt() || mission.isPsRunning()) continue

                if (mission.running && isMetered) {
                    mission.pause()
                } else if (!mission.running && !isMetered && mission.enqueued) {
                    mission.start()
                    if (mPrefQueueLimit) break
                }
            }
        }
    }

    fun updateMaximumAttempts() {
        synchronized(this) {
            for (mission in mMissionsPending) mission.maxRetry = mPrefMaxRetry
        }
    }

    fun canRecoverMission(mission: DownloadMission?): Boolean {
        if (mission == null) return false
        return mission.errCode == ERROR_PROGRESS_LOST || mission.storage == null || !mission.storage!!.existsAsFile()
    }

    fun checkForExistingMission(storage: StoredFileHelper): MissionState {
        synchronized(this) {
            val pending = getPendingMission(storage)

            if (pending == null) {
                if (getFinishedMissionIndex(storage) >= 0) return MissionState.Finished
            } else {
                return if (pending.isFinished()) {
                    MissionState.Finished
                } else {
                    if (pending.running) MissionState.PendingRunning else MissionState.Pending
                }
            }
        }

        return MissionState.None
    }

    private fun getMainStorage(tag: String): StoredDirectoryHelper? {
        return when (tag) {
            TAG_VIDEO -> mMainStorageVideo
            TAG_AUDIO -> mMainStorageAudio
            else -> {
                Log.w(TAG, "Unknown download category, not [audio video]: $tag")
                null
            }
        }
    }





    companion object {
        private val TAG = DownloadManager::class.java.simpleName



        const val TAG_AUDIO: String = "audio"
        const val TAG_VIDEO: String = "video"
        private const val DOWNLOADS_METADATA_FOLDER = "pending_downloads"

        private fun getPendingDir(context: Context): File {
            var dir = context.getExternalFilesDir(DOWNLOADS_METADATA_FOLDER)
            if (testDir(dir)) return dir!!

            dir = File(context.filesDir, DOWNLOADS_METADATA_FOLDER)
            if (testDir(dir)) return dir

            throw RuntimeException("path to pending downloads are not accessible")
        }

        private fun testDir(dir: File?): Boolean {
            if (dir == null) return false

            return try {
                if (!Utility.mkdir(dir, false)) {
                    Log.e(TAG, "testDir() cannot create the directory in path: ${dir.absolutePath}")
                    return false
                }

                val tmp = File(dir, ".tmp")
                if (!tmp.createNewFile()) return false
                tmp.delete()
            } catch (e: Exception) {
                Log.e(TAG, "testDir() failed: ${dir.absolutePath}", e)
                false
            }
        }

        fun pickAvailableTemporalDir(ctx: Context): File {
            var dir = ctx.getExternalFilesDir(null)
            if (isDirectoryAvailable(dir)) return dir!!

            dir = ctx.filesDir
            if (isDirectoryAvailable(dir)) return dir!!

            dir = ctx.getDir("muxing_tmp", Context.MODE_PRIVATE)
            if (isDirectoryAvailable(dir)) return dir!!

            dir = ctx.cacheDir
            if (isDirectoryAvailable(dir)) return dir!!

            throw RuntimeException("Not temporal directories are available")
        }

        private fun isDirectoryAvailable(directory: File?): Boolean {
            return directory != null && directory.canWrite() && directory.exists()
        }
    }
}
