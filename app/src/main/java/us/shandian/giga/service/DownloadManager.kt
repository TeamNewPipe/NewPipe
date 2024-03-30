package us.shandian.giga.service

import android.content.Context
import android.os.Handler
import android.util.Log
import org.schabi.newpipe.BuildConfig.DEBUG
import us.shandian.giga.util.Utility
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.function.Predicate
import java.util.function.ToLongFunction

class DownloadManager internal constructor(context: Context, handler: Handler, storageVideo: StoredDirectoryHelper?, storageAudio: StoredDirectoryHelper?) {
    enum class NetworkState {
        Unavailable,
        Operating,
        MeteredOperating
    }

    private val mFinishedMissionStore: FinishedMissionStore
    private val mMissionsPending: ArrayList<DownloadMission?> = ArrayList<DownloadMission?>()
    private val mMissionsFinished: ArrayList<FinishedMission>
    private val mHandler: Handler
    private val mPendingMissionsDir: File?
    private var mLastNetworkStatus: NetworkState = NetworkState.Unavailable
    var mPrefMaxRetry: Int = 0
    var mPrefMeteredDownloads: Boolean = false
    var mPrefQueueLimit: Boolean = false
    private var mSelfMissionsControl: Boolean = false
    var mMainStorageAudio: StoredDirectoryHelper?
    var mMainStorageVideo: StoredDirectoryHelper?

    /**
     * Create a new instance
     *
     * @param context Context for the data source for finished downloads
     * @param handler Thread required for Messaging
     */
    init {
        if (DEBUG) {
            Log.d(TAG, "new DownloadManager instance. 0x" + Integer.toHexString(this.hashCode()))
        }
        mFinishedMissionStore = FinishedMissionStore(context)
        mHandler = handler
        mMainStorageAudio = storageAudio
        mMainStorageVideo = storageVideo
        mMissionsFinished = loadFinishedMissions()
        mPendingMissionsDir = getPendingDir(context)
        loadPendingMissions(context)
    }

    /**
     * Loads finished missions from the data source and forgets finished missions whose file does
     * not exist anymore.
     */
    private fun loadFinishedMissions(): ArrayList<FinishedMission> {
        val finishedMissions: ArrayList<FinishedMission> = mFinishedMissionStore.loadFinishedMissions()

        // check if the files exists, otherwise, forget the download
        for (i in finishedMissions.indices.reversed()) {
            val mission: FinishedMission = finishedMissions.get(i)
            if (!mission.storage.existsAsFile()) {
                if (DEBUG) Log.d(TAG, "downloaded file removed: " + mission.storage.getName())
                mFinishedMissionStore.deleteMission(mission)
                finishedMissions.removeAt(i)
            }
        }
        return finishedMissions
    }

    private fun loadPendingMissions(ctx: Context) {
        val subs: Array<File>? = mPendingMissionsDir!!.listFiles()
        if (subs == null) {
            Log.e(TAG, "listFiles() returned null")
            return
        }
        if (subs.size < 1) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, "Loading pending downloads from directory: " + mPendingMissionsDir.getAbsolutePath())
        }
        val tempDir: File? = pickAvailableTemporalDir(ctx)
        Log.i(TAG, "using '" + tempDir + "' as temporal directory")
        for (sub: File in subs) {
            if (!sub.isFile()) continue
            if ((sub.getName() == ".tmp")) continue
            val mis: DownloadMission? = Utility.readFromFile<DownloadMission>(sub)
            if ((mis == null) || mis.isFinished() || mis.hasInvalidStorage()) {
                sub.delete()
                continue
            }
            mis.threads = arrayOfNulls<Thread>(0)
            var exists: Boolean
            try {
                mis.storage = StoredFileHelper.Companion.deserialize(mis.storage, ctx)
                exists = !mis.storage.isInvalid() && mis.storage.existsAsFile()
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to load the file source of " + mis.storage.toString(), ex)
                mis.storage.invalidate()
                exists = false
            }
            if (mis.isPsRunning()) {
                if (mis.psAlgorithm.worksOnSameFile) {
                    // Incomplete post-processing results in a corrupted download file
                    // because the selected algorithm works on the same file to save space.
                    // the file will be deleted if the storage API
                    // is Java IO (avoid showing the "Save as..." dialog)
                    if (exists && mis.storage.isDirect() && !mis.storage.delete()) Log.w(TAG, "Unable to delete incomplete download file: " + sub.getPath())
                }
                mis.psState = 0
                mis.errCode = DownloadMission.Companion.ERROR_POSTPROCESSING_STOPPED
            } else if (!exists) {
                tryRecover(mis)

                // the progress is lost, reset mission state
                if (mis.isInitialized()) mis.resetState(true, true, DownloadMission.Companion.ERROR_PROGRESS_LOST)
            }
            if (mis.psAlgorithm != null) {
                mis.psAlgorithm.cleanupTemporalDir()
                mis.psAlgorithm.setTemporalDir(tempDir)
            }
            mis.metadata = sub
            mis.maxRetry = mPrefMaxRetry
            mis.mHandler = mHandler
            mMissionsPending.add(mis)
        }
        if (mMissionsPending.size > 1) Collections.sort<DownloadMission?>(mMissionsPending, Comparator.comparingLong<DownloadMission?>(ToLongFunction<DownloadMission?>({ obj: DownloadMission? -> obj.getTimestamp() })))
    }

    /**
     * Start a new download mission
     *
     * @param mission the new download mission to add and run (if possible)
     */
    fun startMission(mission: DownloadMission) {
        synchronized(this, {
            mission.timestamp = System.currentTimeMillis()
            mission.mHandler = mHandler
            mission.maxRetry = mPrefMaxRetry

            // create metadata file
            while (true) {
                mission.metadata = File(mPendingMissionsDir, mission.timestamp.toString())
                if (!mission.metadata.isFile() && !mission.metadata.exists()) {
                    try {
                        if (!mission.metadata.createNewFile()) throw RuntimeException("Cant create download metadata file")
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                    break
                }
                mission.timestamp = System.currentTimeMillis()
            }
            mSelfMissionsControl = true
            mMissionsPending.add(mission)

            // Before continue, save the metadata in case the internet connection is not available
            Utility.writeToFile(mission.metadata, mission)
            if (mission.storage == null) {
                // noting to do here
                mission.errCode = DownloadMission.Companion.ERROR_FILE_CREATION
                if (mission.errObject != null) mission.errObject = IOException("DownloadMission.storage == NULL")
                return
            }
            val start: Boolean = !mPrefQueueLimit || getRunningMissionsCount() < 1
            if (canDownloadInCurrentNetwork() && start) {
                mission.start()
            }
        })
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

    fun deleteMission(mission: Mission) {
        synchronized(this, {
            if (mission is DownloadMission) {
                mMissionsPending.remove(mission)
            } else if (mission is FinishedMission) {
                mMissionsFinished.remove(mission)
                mFinishedMissionStore.deleteMission(mission)
            }
            mission.delete()
        })
    }

    fun forgetMission(storage: StoredFileHelper) {
        synchronized(this, {
            val mission: Mission? = getAnyMission(storage)
            if (mission == null) return
            if (mission is DownloadMission) {
                mMissionsPending.remove(mission)
            } else if (mission is FinishedMission) {
                mMissionsFinished.remove(mission)
                mFinishedMissionStore.deleteMission(mission)
            }
            mission.storage = null
            mission.delete()
        })
    }

    fun tryRecover(mission: DownloadMission) {
        val mainStorage: StoredDirectoryHelper? = getMainStorage(mission.storage.getTag())
        if (!mission.storage.isInvalid() && mission.storage.create()) return

        // using javaIO cannot recreate the file
        // using SAF in older devices (no tree available)
        //
        // force the user to pick again the save path
        mission.storage.invalidate()
        if (mainStorage == null) return

        // if the user has changed the save path before this download, the original save path will be lost
        val newStorage: StoredFileHelper? = mainStorage.createFile(mission.storage.getName(), mission.storage.getType())
        if (newStorage != null) mission.storage = newStorage
    }

    /**
     * Get a pending mission by its path
     *
     * @param storage where the file possible is stored
     * @return the mission or null if no such mission exists
     */
    private fun getPendingMission(storage: StoredFileHelper): DownloadMission? {
        for (mission: DownloadMission in mMissionsPending) {
            if (mission.storage.equals(storage)) {
                return mission
            }
        }
        return null
    }

    /**
     * Get the index into [.mMissionsFinished] of a finished mission by its path, return
     * `-1` if there is no such mission. This function also checks if the matched mission's
     * file exists, and, if it does not, the related mission is forgotten about (like in [ ][.loadFinishedMissions]) and `-1` is returned.
     *
     * @param storage where the file would be stored
     * @return the mission index or -1 if no such mission exists
     */
    private fun getFinishedMissionIndex(storage: StoredFileHelper): Int {
        for (i in mMissionsFinished.indices) {
            if (mMissionsFinished.get(i).storage.equals(storage)) {
                // If the file does not exist the mission is not valid anymore. Also checking if
                // length == 0 since the file picker may create an empty file before yielding it,
                // but that does not mean the file really belonged to a previous mission.
                if (!storage.existsAsFile() || storage.length() == 0L) {
                    if (DEBUG) {
                        Log.d(TAG, "matched downloaded file removed: " + storage.getName())
                    }
                    mFinishedMissionStore.deleteMission(mMissionsFinished.get(i))
                    mMissionsFinished.removeAt(i)
                    return -1 // finished mission whose associated file was removed
                }
                return i
            }
        }
        return -1
    }

    private fun getAnyMission(storage: StoredFileHelper): Mission? {
        synchronized(this, {
            val mission: Mission? = getPendingMission(storage)
            if (mission != null) return mission
            val idx: Int = getFinishedMissionIndex(storage)
            if (idx >= 0) return mMissionsFinished.get(idx)
        })
        return null
    }

    fun getRunningMissionsCount(): Int {
        var count: Int = 0
        synchronized(this, {
            for (mission: DownloadMission in mMissionsPending) {
                if (mission.running && !mission.isPsFailed() && !mission.isFinished()) count++
            }
        })
        return count
    }

    fun pauseAllMissions(force: Boolean) {
        synchronized(this, {
            for (mission: DownloadMission in mMissionsPending) {
                if (!mission.running || mission.isPsRunning() || mission.isFinished()) continue
                if (force) {
                    // avoid waiting for threads
                    mission.init = null
                    mission.threads = arrayOfNulls<Thread>(0)
                }
                mission.pause()
            }
        })
    }

    fun startAllMissions() {
        synchronized(this, {
            for (mission: DownloadMission in mMissionsPending) {
                if (mission.running || mission.isCorrupt()) continue
                mission.start()
            }
        })
    }

    /**
     * Set a pending download as finished
     *
     * @param mission the desired mission
     */
    fun setFinished(mission: DownloadMission?) {
        synchronized(this, {
            mMissionsPending.remove(mission)
            mMissionsFinished.add(0, FinishedMission(mission))
            mFinishedMissionStore.addFinishedMission(mission)
        })
    }

    /**
     * runs one or multiple missions in from queue if possible
     *
     * @return true if one or multiple missions are running, otherwise, false
     */
    fun runMissions(): Boolean {
        synchronized(this, {
            if (mMissionsPending.size < 1) return false
            if (!canDownloadInCurrentNetwork()) return false
            if (mPrefQueueLimit) {
                for (mission: DownloadMission in mMissionsPending) if (!mission.isFinished() && mission.running) return true
            }
            var flag: Boolean = false
            for (mission: DownloadMission in mMissionsPending) {
                if (mission.running || !mission.enqueued || mission.isFinished()) continue
                resumeMission(mission)
                if (mission.errCode != DownloadMission.Companion.ERROR_NOTHING) continue
                if (mPrefQueueLimit) return true
                flag = true
            }
            return flag
        })
    }

    fun getIterator(): MissionIterator {
        mSelfMissionsControl = true
        return MissionIterator()
    }

    /**
     * Forget all finished downloads, but, doesn't delete any file
     */
    fun forgetFinishedDownloads() {
        synchronized(this, {
            for (mission: FinishedMission? in mMissionsFinished) {
                mFinishedMissionStore.deleteMission(mission)
            }
            mMissionsFinished.clear()
        })
    }

    private fun canDownloadInCurrentNetwork(): Boolean {
        if (mLastNetworkStatus == NetworkState.Unavailable) return false
        return !(mPrefMeteredDownloads && mLastNetworkStatus == NetworkState.MeteredOperating)
    }

    fun handleConnectivityState(currentStatus: NetworkState, updateOnly: Boolean) {
        if (currentStatus == mLastNetworkStatus) return
        mLastNetworkStatus = currentStatus
        if (currentStatus == NetworkState.Unavailable) return
        if (!mSelfMissionsControl || updateOnly) {
            return  // don't touch anything without the user interaction
        }
        val isMetered: Boolean = mPrefMeteredDownloads && mLastNetworkStatus == NetworkState.MeteredOperating
        synchronized(this, {
            for (mission: DownloadMission in mMissionsPending) {
                if (mission.isCorrupt() || mission.isPsRunning()) continue
                if (mission.running && isMetered) {
                    mission.pause()
                } else if (!mission.running && !isMetered && mission.enqueued) {
                    mission.start()
                    if (mPrefQueueLimit) break
                }
            }
        })
    }

    fun updateMaximumAttempts() {
        synchronized(this, { for (mission: DownloadMission in mMissionsPending) mission.maxRetry = mPrefMaxRetry })
    }

    fun checkForExistingMission(storage: StoredFileHelper): MissionState {
        synchronized(this, {
            val pending: DownloadMission? = getPendingMission(storage)
            if (pending == null) {
                if (getFinishedMissionIndex(storage) >= 0) return MissionState.Finished
            } else {
                if (pending.isFinished()) {
                    return MissionState.Finished // this never should happen (race-condition)
                } else {
                    return if (pending.running) MissionState.PendingRunning else MissionState.Pending
                }
            }
        })
        return MissionState.None
    }

    private fun getMainStorage(tag: String): StoredDirectoryHelper? {
        if ((tag == TAG_AUDIO)) return mMainStorageAudio
        if ((tag == TAG_VIDEO)) return mMainStorageVideo
        Log.w(TAG, "Unknown download category, not [audio video]: " + tag)
        return null // this never should happen
    }

    inner class MissionIterator() : DiffUtil.Callback() {
        val FINISHED: Any = Any()
        val PENDING: Any = Any()
        var snapshot: ArrayList<Any>?
        var current: ArrayList<Any>? = null
        var hidden: ArrayList<Mission>
        var hasFinished: Boolean = false

        init {
            hidden = ArrayList<Mission>(2)
            snapshot = getSpecialItems()
        }

        private fun getSpecialItems(): ArrayList<Any> {
            synchronized(this@DownloadManager, {
                val pending: ArrayList<Mission> = ArrayList<Mission>(mMissionsPending)
                val finished: ArrayList<Mission> = ArrayList<Mission>(mMissionsFinished)
                val remove: MutableList<Mission> = ArrayList<Mission>(hidden)

                // hide missions (if required)
                remove.removeIf(Predicate<Mission>({ mission: Mission -> pending.remove(mission) || finished.remove(mission) }))
                var fakeTotal: Int = pending.size
                if (fakeTotal > 0) fakeTotal++
                fakeTotal += finished.size
                if (finished.size > 0) fakeTotal++
                val list: ArrayList<Any> = ArrayList(fakeTotal)
                if (pending.size > 0) {
                    list.add(PENDING)
                    list.addAll(pending)
                }
                if (finished.size > 0) {
                    list.add(FINISHED)
                    list.addAll(finished)
                }
                hasFinished = finished.size > 0
                return list
            })
        }

        fun getItem(position: Int): MissionItem {
            val `object`: Any = snapshot!!.get(position)
            if (`object` === PENDING) return MissionItem(SPECIAL_PENDING)
            if (`object` === FINISHED) return MissionItem(SPECIAL_FINISHED)
            return MissionItem(SPECIAL_NOTHING, `object` as Mission?)
        }

        fun getSpecialAtItem(position: Int): Int {
            val `object`: Any = snapshot!!.get(position)
            if (`object` === PENDING) return SPECIAL_PENDING
            if (`object` === FINISHED) return SPECIAL_FINISHED
            return SPECIAL_NOTHING
        }

        fun start() {
            current = getSpecialItems()
        }

        fun end() {
            snapshot = current
            current = null
        }

        fun hide(mission: Mission) {
            hidden.add(mission)
        }

        fun unHide(mission: Mission) {
            hidden.remove(mission)
        }

        fun hasFinishedMissions(): Boolean {
            return hasFinished
        }

        /**
         * Check if exists missions running and paused. Corrupted and hidden missions are not counted
         *
         * @return two-dimensional array contains the current missions state.
         * 1° entry: true if has at least one mission running
         * 2° entry: true if has at least one mission paused
         */
        fun hasValidPendingMissions(): BooleanArray {
            var running: Boolean = false
            var paused: Boolean = false
            synchronized(this@DownloadManager, {
                for (mission: DownloadMission in mMissionsPending) {
                    if (hidden.contains(mission) || mission.isCorrupt()) continue
                    if (mission.running) running = true else paused = true
                }
            })
            return booleanArrayOf(running, paused)
        }

        public override fun getOldListSize(): Int {
            return snapshot!!.size
        }

        public override fun getNewListSize(): Int {
            return current!!.size
        }

        public override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return snapshot!!.get(oldItemPosition) === current!!.get(newItemPosition)
        }

        public override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val x: Any = snapshot!!.get(oldItemPosition)
            val y: Any = current!!.get(newItemPosition)
            if (x is Mission && y is Mission) {
                return (x as Mission).storage.equals((y as Mission).storage)
            }
            return false
        }
    }

    class MissionItem @JvmOverloads internal constructor(var special: Int, m: Mission? = null) {
        var mission: Mission?

        init {
            mission = m
        }
    }

    companion object {
        private val TAG: String = DownloadManager::class.java.getSimpleName()
        val SPECIAL_NOTHING: Int = 0
        val SPECIAL_PENDING: Int = 1
        val SPECIAL_FINISHED: Int = 2
        val TAG_AUDIO: String = "audio"
        val TAG_VIDEO: String = "video"
        private val DOWNLOADS_METADATA_FOLDER: String = "pending_downloads"
        private fun getPendingDir(context: Context): File? {
            var dir: File? = context.getExternalFilesDir(DOWNLOADS_METADATA_FOLDER)
            if (testDir(dir)) return dir
            dir = File(context.getFilesDir(), DOWNLOADS_METADATA_FOLDER)
            if (testDir(dir)) return dir
            throw RuntimeException("path to pending downloads are not accessible")
        }

        private fun testDir(dir: File?): Boolean {
            if (dir == null) return false
            try {
                if (!Utility.mkdir(dir, false)) {
                    Log.e(TAG, "testDir() cannot create the directory in path: " + dir.getAbsolutePath())
                    return false
                }
                val tmp: File = File(dir, ".tmp")
                if (!tmp.createNewFile()) return false
                return tmp.delete() // if the file was created, SHOULD BE deleted too
            } catch (e: Exception) {
                Log.e(TAG, "testDir() failed: " + dir.getAbsolutePath(), e)
                return false
            }
        }

        private fun isDirectoryAvailable(directory: File?): Boolean {
            return (directory != null) && directory.canWrite() && directory.exists()
        }

        fun pickAvailableTemporalDir(ctx: Context): File? {
            var dir: File? = ctx.getExternalFilesDir(null)
            if (isDirectoryAvailable(dir)) return dir
            dir = ctx.getFilesDir()
            if (isDirectoryAvailable(dir)) return dir

            // this never should happen
            dir = ctx.getDir("muxing_tmp", Context.MODE_PRIVATE)
            if (isDirectoryAvailable(dir)) return dir

            // fallback to cache dir
            dir = ctx.getCacheDir()
            if (isDirectoryAvailable(dir)) return dir
            throw RuntimeException("Not temporal directories are available")
        }
    }
}
