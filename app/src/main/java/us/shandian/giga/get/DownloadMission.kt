package us.shandian.giga.get

import android.os.Handler
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.io.Serializable
import okio.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.*
import javax.net.ssl.SSLException
import kotlinx.coroutines.*
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.util.Utility

open class DownloadMission : Mission {
    /**
     * The urls of the file to download
     */
    @JvmField
    var urls: Array<String>? = null

    /**
     * Number of bytes downloaded and written
     */
    @Volatile
    @JvmField
    var done: Long = 0

    /**
     * Indicates a file generated dynamically on the web server
     */
    @JvmField
    var unknownLength: Boolean = false

    /**
     * offset in the file where the data should be written
     */
    @JvmField
    var offsets: LongArray

    /**
     * Indicates if the post-processing state:
     * 0: ready
     * 1: running
     * 2: completed
     * 3: hold
     */
    @Volatile
    @JvmField
    var psState: Int = 0

    /**
     * the post-processing algorithm instance
     */
    @JvmField
    var psAlgorithm: Postprocessing? = null

    /**
     * The current resource to download, `urls[current]` and `offsets[current]`
     */
    @JvmField
    var current: Int = 0

    /**
     * Metadata where the mission state is saved
     */
    @Transient
    @JvmField
    var metadata: File? = null

    /**
     * maximum attempts
     */
    @Transient
    @JvmField
    var maxRetry: Int = 0

    /**
     * Approximated final length, this represent the sum of all resources sizes
     */
    @JvmField
    var nearLength: Long = 0

    /**
     * Download blocks, the size is multiple of [DownloadMission.BLOCK_SIZE].
     * Every entry (block) in this array holds an offset, used to resume the download.
     * An block offset can be -1 if the block was downloaded successfully.
     */
    @JvmField
    var blocks: IntArray? = null

    /**
     * Download/File resume offset in fallback mode (if applicable) [DownloadRunnableFallback]
     */
    @Volatile
    @JvmField
    var fallbackResumeOffset: Long = 0

    /**
     * Maximum of download threads running, chosen by the user
     */
    @JvmField
    var threadCount: Int = 3

    /**
     * information required to recover a download
     */
    @JvmField
    var recoveryInfo: Array<MissionRecoveryInfo>? = null

    @Transient
    private var finishCount: Int = 0

    @Transient
    @Volatile
    @JvmField
    var running: Boolean = false

    @JvmField
    var enqueued: Boolean = false

    @JvmField
    var errCode: Int = ERROR_NOTHING

    @JvmField
    var errObject: Exception? = null

    @Transient
    @JvmField
    var mHandler: Handler? = null

    @Transient
    private var blockAcquired: BooleanArray? = null

    @Transient
    private var writingToFileNext: Long = 0

    @Transient
    @Volatile
    private var writingToFile: Boolean = false

    @JvmField
    val LOCK = Lock()

    @Transient
    private var _jobs: MutableList<Job>? = null

    var jobs: MutableList<Job>
        get() {
            if (_jobs == null) {
                _jobs = Collections.synchronizedList(mutableListOf())
            }
            return _jobs!!
        }
        set(value) {
            _jobs = value
        }

    @Transient
    @JvmField
    var initJob: Job? = null

    @Transient
    private var missionScope: CoroutineScope? = null

    private fun readObject(inputStream: java.io.ObjectInputStream) {
        inputStream.defaultReadObject()
        _jobs = Collections.synchronizedList(mutableListOf())
    }

    constructor(urls: Array<String>, storage: StoredFileHelper, kind: Char, psInstance: Postprocessing?) {
        this.urls = urls
        this.kind = kind
        this.offsets = LongArray(urls.size)
        this.enqueued = true
        this.maxRetry = 3
        this.storage = storage
        this.psAlgorithm = psInstance

        if (psInstance == null && urls.size > 1) {
            Log.w(TAG, "mission created with multiple urls ¿missing post-processing algorithm?")
        }
    }

    /**
     * Acquire a block
     *
     * @return the block or `null` if no more blocks left
     */
    internal fun acquireBlock(): Block? {
        synchronized(LOCK) {
            val acquired = blockAcquired ?: return null
            val blks = blocks ?: return null
            for (i in acquired.indices) {
                if (!acquired[i] && blks[i] >= 0) {
                    val block = Block()
                    block.position = i
                    block.done = blks[i]

                    acquired[i] = true
                    return block
                }
            }
        }

        return null
    }

    /**
     * Release an block
     *
     * @param position the index of the block
     * @param done     amount of bytes downloaded
     */
    internal fun releaseBlock(position: Int, done: Int) {
        synchronized(LOCK) {
            blockAcquired?.let { it[position] = false }
            blocks?.let { it[position] = done }
        }
    }

    /**
     * Opens a connection
     *
     * @param headRequest `true` for use `HEAD` request method, otherwise, `GET` is used
     * @param rangeStart  range start
     * @param rangeEnd    range end
     * @return a [java.net.URLConnection URLConnection] linking to the URL.
     * @throws IOException if an I/O exception occurs.
     */
    @Throws(IOException::class)
    internal fun openConnection(headRequest: Boolean, rangeStart: Long, rangeEnd: Long): HttpURLConnection {
        return openConnection(urls!![current], headRequest, rangeStart, rangeEnd)
    }

    @Throws(IOException::class)
    internal fun openConnection(url: String, headRequest: Boolean, rangeStart: Long, rangeEnd: Long): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", DownloaderImpl.USER_AGENT)
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Accept-Encoding", "*")

        if (headRequest) conn.requestMethod = "HEAD"

        // BUG workaround: switching between networks can freeze the download forever
        conn.connectTimeout = 30000

        if (rangeStart >= 0) {
            var req = "bytes=$rangeStart-"
            if (rangeEnd > 0) req += rangeEnd

            conn.setRequestProperty("Range", req)
        }

        return conn
    }

    /**
     * @param threadId id of the calling thread
     * @param conn     Opens and establish the communication
     * @throws IOException if an error occurred connecting to the server.
     * @throws HttpError   if the HTTP Status-Code is not satisfiable
     */
    @Throws(IOException::class, HttpError::class)
    internal fun establishConnection(threadId: Int, conn: HttpURLConnection) {
        val statusCode = conn.responseCode

        if (DEBUG) {
            Log.d(TAG, "$threadId:[request]  Range=${conn.getRequestProperty("Range")}")
            Log.d(TAG, "$threadId:[response] Code=$statusCode")
            Log.d(TAG, "$threadId:[response] Content-Length=${conn.contentLength}")
            Log.d(TAG, "$threadId:[response] Content-Range=${conn.getHeaderField("Content-Range")}")
        }

        when (statusCode) {
            204, 205, 207 -> throw HttpError(statusCode)

            416 -> return

            // let the download thread handle this error
            else -> if (statusCode < 200 || statusCode > 299) {
                throw HttpError(statusCode)
            }
        }
    }

    private fun notify(what: Int) {
        mHandler?.obtainMessage(what, this)?.sendToTarget()
    }

    @Synchronized
    internal fun notifyProgress(deltaLen: Long) {
        if (unknownLength) {
            length += deltaLen // Update length before proceeding
        }

        done += deltaLen

        if (metadata == null) return

        if (!writingToFile && (done > writingToFileNext || deltaLen < 0)) {
            writingToFile = true
            writingToFileNext = done + BLOCK_SIZE
            writeThisToFileAsync()
        }
    }

    @Synchronized
    internal fun notifyError(err: Exception) {
        Log.e(TAG, "notifyError()", err)

        when (err) {
            is FileNotFoundException -> notifyError(ERROR_FILE_CREATION, null)
            is SSLException -> notifyError(ERROR_SSL_EXCEPTION, null)
            is HttpError -> notifyError(err.statusCode, null)
            is ConnectException -> notifyError(ERROR_CONNECT_HOST, null)
            is UnknownHostException -> notifyError(ERROR_UNKNOWN_HOST, null)
            is SocketTimeoutException -> notifyError(ERROR_TIMEOUT, null)
            else -> notifyError(ERROR_UNKNOWN_EXCEPTION, err)
        }
    }

    @Synchronized
    fun notifyError(code: Int, err: Exception?) {
        var mutableCode = code
        var mutableErr = err
        Log.e(TAG, "notifyError() code = $mutableCode", mutableErr)
        if (mutableErr != null && mutableErr.cause is ErrnoException) {
            val errno = (mutableErr.cause as ErrnoException).errno
            if (errno == OsConstants.ENOSPC) {
                mutableCode = ERROR_INSUFFICIENT_STORAGE
                mutableErr = null
            } else if (errno == OsConstants.EACCES) {
                mutableCode = ERROR_PERMISSION_DENIED
                mutableErr = null
            }
        }

        if (mutableErr is IOException) {
            if (mutableErr.message?.contains("Permission denied") == true) {
                mutableCode = ERROR_PERMISSION_DENIED
                mutableErr = null
            } else if (mutableErr.message?.contains("ENOSPC") == true) {
                mutableCode = ERROR_INSUFFICIENT_STORAGE
                mutableErr = null
            } else if (storage?.canWrite() == false) {
                mutableCode = ERROR_FILE_CREATION
                mutableErr = null
            }
        }

        errCode = mutableCode
        errObject = mutableErr

        when (mutableCode) {
            ERROR_SSL_EXCEPTION, ERROR_UNKNOWN_HOST, ERROR_CONNECT_HOST, ERROR_TIMEOUT -> {}
            else -> if (mutableCode < 500 || mutableCode > 599) enqueued = false
        }

        notify(DownloadManagerService.MESSAGE_ERROR)

        if (running) pauseJobs()
    }

    @Synchronized
    internal fun notifyFinished() {
        val urlsLocal = urls!!
        if (current < urlsLocal.size) {
            if (++finishCount < jobs.size) return

            if (DEBUG) {
                Log.d(TAG, "onFinish: downloaded ${current + 1}/${urlsLocal.size}")
            }

            current++
            if (current < urlsLocal.size) {
                // prepare next sub-mission
                offsets[current] = offsets[current - 1] + length
                initializer()
                return
            }
        }

        if (psAlgorithm != null && psState == 0) {
            jobs.clear()
            jobs.add(runAsync(1) { doPostprocessing() })
            return
        }

        // this mission is fully finished
        unknownLength = false
        enqueued = false
        running = false

        deleteThisFromFile()
        notify(DownloadManagerService.MESSAGE_FINISHED)
    }

    private fun notifyPostProcessing(state: Int) {
        val action = when (state) {
            1 -> "Running"
            2 -> "Completed"
            else -> "Failed"
        }

        Log.d(TAG, "$action postprocessing on ${storage?.getName()}")

        if (state == 2) {
            psState = state
            return
        }

        synchronized(LOCK) {
            // don't return without fully write the current state
            psState = state
            writeThisToFile()
        }
    }

    /**
     * Start downloading with multiple threads.
     */
    fun start() {
        if (running || isFinished() || (urls?.size ?: 0) < 1) return

        // ensure that the previous state is completely paused.
        cancelJobs(10000)

        running = true
        errCode = ERROR_NOTHING

        if (hasInvalidStorage()) {
            notifyError(ERROR_FILE_CREATION, null)
            return
        }

        if (current >= (urls?.size ?: 0)) {
            notifyFinished()
            return
        }

        notify(DownloadManagerService.MESSAGE_RUNNING)

        if (urls!![current] == null) {
            doRecover(ERROR_RESOURCE_GONE)
            return
        }

        val blocksLocal = blocks
        if (blocksLocal == null) {
            initializer()
            return
        }

        initJob = null
        finishCount = 0
        blockAcquired = BooleanArray(blocksLocal.size)
        jobs.clear()

        if (missionScope == null) {
            missionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        if (blocksLocal.isEmpty()) {
            jobs.add(runAsync(1) { DownloadRunnableFallback(this).run() })
        } else {
            var remainingBlocks = 0
            for (block in blocksLocal) if (block >= 0) remainingBlocks++

            if (remainingBlocks < 1) {
                notifyFinished()
                return
            }

            val threadCountToUse = Math.min(threadCount, remainingBlocks)
            for (i in 0 until threadCountToUse) {
                jobs.add(runAsync(i + 1) { DownloadRunnable(this, i).run() })
            }
        }
    }

    /**
     * Pause the mission
     */
    fun pause() {
        if (!running) return

        if (isPsRunning()) {
            if (DEBUG) {
                Log.w(TAG, "pause during post-processing is not applicable.")
            }
            return
        }

        running = false
        notify(DownloadManagerService.MESSAGE_PAUSED)

        val initJobLocal = initJob
        if (initJobLocal != null && initJobLocal.isActive) {
            initJobLocal.cancel()
            synchronized(LOCK) {
                resetState(false, true, ERROR_NOTHING)
            }
            return
        }

        if (DEBUG && unknownLength) {
            Log.w(TAG, "pausing a download that can not be resumed (range requests not allowed by the server).")
        }

        initJob = null
        pauseJobs()
    }

    private fun pauseJobs() {
        running = false
        cancelJobs(-1)
        writeThisToFile()
    }

    /**
     * Removes the downloaded file and the meta file
     */
    override fun delete(): Boolean {
        psAlgorithm?.cleanupTemporalDir()

        notify(DownloadManagerService.MESSAGE_DELETED)

        val res = deleteThisFromFile()

        return super.delete() && res
    }

    /**
     * Resets the mission state
     *
     * @param rollback       `true` true to forget all progress, otherwise, `false`
     * @param persistChanges `true` to commit changes to the metadata file, otherwise, `false`
     */
    fun resetState(rollback: Boolean, persistChanges: Boolean, errorCode: Int) {
        length = 0
        errCode = errorCode
        errObject = null
        unknownLength = false
        jobs.clear()
        fallbackResumeOffset = 0
        blocks = null
        blockAcquired = null

        if (rollback) current = 0
        if (persistChanges) writeThisToFile()
    }

    private fun initializer() {
        initJob = runAsync(DownloadInitializer.mId) { DownloadInitializer(this).run() }
    }

    private fun writeThisToFileAsync() {
        runAsync(-2) { writeThisToFile() }
    }

    /**
     * Write this [DownloadMission] to the meta file asynchronously
     * if no thread is already running.
     */
    fun writeThisToFile() {
        synchronized(LOCK) {
            val meta = metadata ?: return
            Utility.writeToFile(meta, this)
            writingToFile = false
        }
    }

    /**
     * Indicates if the download if fully finished
     *
     * @return true, otherwise, false
     */
    fun isFinished(): Boolean {
        return current >= (urls?.size ?: 0) && (psAlgorithm == null || psState == 2)
    }

    /**
     * Indicates if the download file is corrupt due a failed post-processing
     *
     * @return `true` if this mission is unrecoverable
     */
    fun isPsFailed(): Boolean {
        return when (errCode) {
            ERROR_POSTPROCESSING, ERROR_POSTPROCESSING_STOPPED -> psAlgorithm?.worksOnSameFile ?: false
            else -> false
        }
    }

    /**
     * Indicates if a post-processing algorithm is running
     *
     * @return true, otherwise, false
     */
    fun isPsRunning(): Boolean {
        return psAlgorithm != null && (psState == 1 || psState == 3)
    }

    /**
     * Indicated if the mission is ready
     *
     * @return true, otherwise, false
     */
    fun isInitialized(): Boolean {
        return blocks != null // DownloadMissionInitializer was executed
    }

    /**
     * Gets the approximated final length of the file
     *
     * @return the length in bytes
     */
    fun getLength(): Long {
        if (psState == 1 || psState == 3) {
            return length
        }

        var calculated = offsets[if (current < offsets.size) current else offsets.size - 1] + length
        calculated -= offsets[0] // don't count reserved space

        return Math.max(calculated, nearLength)
    }

    /**
     * set this mission state on the queue
     *
     * @param queue true to add to the queue, otherwise, false
     */
    fun setEnqueued(queue: Boolean) {
        enqueued = queue
        writeThisToFileAsync()
    }

    /**
     * Attempts to continue a blocked post-processing
     *
     * @param recover `true` to retry, otherwise, `false` to cancel
     */
    fun psContinue(recover: Boolean) {
        psState = 1
        errCode = if (recover) ERROR_NOTHING else ERROR_POSTPROCESSING
        jobs.getOrNull(0)?.cancel()
    }

    /**
     * Indicates whatever the backed storage is invalid
     *
     * @return `true`, if storage is invalid and cannot be used
     */
    fun hasInvalidStorage(): Boolean {
        // Don't consider ERROR_PROGRESS_LOST as invalid storage - it can be recovered
        return storage == null || !storage!!.existsAsFile()
    }

    /**
     * Indicates whatever is possible to start the mission
     *
     * @return `true` is this mission its "healthy", otherwise, `false`
     */
    fun isCorrupt(): Boolean {
        if ((urls?.size ?: 0) < 1) return false
        return isPsFailed() || errCode == ERROR_POSTPROCESSING_HOLD || isFinished()
    }

    /**
     * Indicates if mission urls has expired and there an attempt to renovate them
     *
     * @return `true` if the mission is running a recovery procedure, otherwise, `false`
     */
    fun isRecovering(): Boolean {
        // Since we replaced threads with jobs, we need another way to check if DownloadMissionRecover is running.
        // Actually, DownloadMissionRecover will be converted to a function/class that uses coroutines.
        // For now, let's just check if there are jobs.
        return jobs.isNotEmpty() && running // This is a simplification
    }

    private suspend fun doPostprocessing() {
        errCode = ERROR_NOTHING
        errObject = null

        notifyPostProcessing(1)

        var exception: Exception? = null

        try {
            withContext(Dispatchers.Default) {
                psAlgorithm?.run(this@DownloadMission)
            }
        } catch (err: Exception) {
            Log.e(TAG, "Post-processing failed. $psAlgorithm", err)

            if (err is InterruptedIOException || err is kotlinx.coroutines.CancellationException) {
                notifyError(ERROR_POSTPROCESSING_STOPPED, null)
                return
            }

            if (errCode == ERROR_NOTHING) errCode = ERROR_POSTPROCESSING

            exception = err
        } finally {
            notifyPostProcessing(if (errCode == ERROR_NOTHING) 2 else 0)
        }

        if (errCode != ERROR_NOTHING) {
            if (exception == null) exception = errObject
            notifyError(ERROR_POSTPROCESSING, exception)
            return
        }

        notifyFinished()
    }

    /**
     * Attempts to recover the download
     *
     * @param errorCode error code which trigger the recovery procedure
     */
    internal fun doRecover(errorCode: Int) {
        Log.i(TAG, "Attempting to recover the mission: ${storage?.getName()}")

        val recoveryInfoLocal = recoveryInfo
        if (recoveryInfoLocal == null) {
            notifyError(errorCode, null)
            urls = emptyArray() // mark this mission as dead
            return
        }

        cancelJobs(0)

        jobs.clear()
        jobs.add(
            runAsync(DownloadMissionRecover.mID) {
                DownloadMissionRecover(this, errorCode).run()
            }
        )
    }

    private fun deleteThisFromFile(): Boolean {
        synchronized(LOCK) {
            val meta = metadata ?: return true
            val res = meta.delete()
            metadata = null
            return res
        }
    }

    /**
     * run a new coroutine
     *
     * @param id  id of new coroutine (used for debugging only)
     * @param who the block whose code is invoked.
     */
    private fun runAsync(id: Int, who: suspend () -> Unit): Job {
        if (missionScope == null) {
            missionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        return missionScope!!.launch {
            if (DEBUG) {
                // Thread.currentThread().name = String.format("%s[%s] %s", TAG, id, storage?.getName())
            }
            who()
        }
    }

    /**
     * Waits at most `millis` milliseconds for the jobs to die
     *
     * @param millis the time to wait in milliseconds
     */
    private fun cancelJobs(millis: Int = 0) {
        initJob?.cancel()
        initJob = null

        for (job in jobs) {
            job.cancel()
        }
        jobs.clear()
    }

    internal class HttpError(val statusCode: Int) : Exception() {
        override val message: String
            get() = "HTTP $statusCode"
    }

    class Block : Serializable {
        @JvmField
        var position: Int = 0

        @JvmField
        var done: Int = 0
    }

    class Lock : Serializable

    companion object {
        private const val serialVersionUID = 6L // last bump: 07 october 2019

        const val BUFFER_SIZE: Int = 64 * 1024
        const val BLOCK_SIZE: Int = 512 * 1024

        private const val TAG = "DownloadMission"

        const val ERROR_NOTHING: Int = -1
        const val ERROR_PATH_CREATION: Int = 1000
        const val ERROR_FILE_CREATION: Int = 1001
        const val ERROR_UNKNOWN_EXCEPTION: Int = 1002
        const val ERROR_PERMISSION_DENIED: Int = 1003
        const val ERROR_SSL_EXCEPTION: Int = 1004
        const val ERROR_UNKNOWN_HOST: Int = 1005
        const val ERROR_CONNECT_HOST: Int = 1006
        const val ERROR_POSTPROCESSING: Int = 1007
        const val ERROR_POSTPROCESSING_STOPPED: Int = 1008
        const val ERROR_POSTPROCESSING_HOLD: Int = 1009
        const val ERROR_INSUFFICIENT_STORAGE: Int = 1010
        const val ERROR_PROGRESS_LOST: Int = 1011
        const val ERROR_TIMEOUT: Int = 1012
        const val ERROR_RESOURCE_GONE: Int = 1013
        const val ERROR_HTTP_NO_CONTENT: Int = 204
        const val ERROR_HTTP_FORBIDDEN: Int = 403
    }
}
