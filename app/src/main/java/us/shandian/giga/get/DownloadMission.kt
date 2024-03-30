package us.shandian.giga.get

import android.os.Handler
import android.util.Log
import org.schabi.newpipe.BuildConfig.DEBUG
import us.shandian.giga.util.Utility
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.io.Serializable
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.channels.ClosedByInterruptException
import java.util.Objects
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.min

class DownloadMission(urls: Array<String?>, storage: StoredFileHelper?, kind: Char, psInstance: Postprocessing?) : Mission() {
    /**
     * The urls of the file to download
     */
    var urls: Array<String?>

    /**
     * Number of bytes downloaded and written
     */
    @Volatile
    var done: Long = 0

    /**
     * Indicates a file generated dynamically on the web server
     */
    var unknownLength: Boolean = false

    /**
     * offset in the file where the data should be written
     */
    var offsets: LongArray

    /**
     * Indicates if the post-processing state:
     * 0: ready
     * 1: running
     * 2: completed
     * 3: hold
     */
    @Volatile
    var psState: Int = 0

    /**
     * the post-processing algorithm instance
     */
    var psAlgorithm: Postprocessing?

    /**
     * The current resource to download, `urls[current]` and `offsets[current]`
     */
    var current: Int = 0

    /**
     * Metadata where the mission state is saved
     */
    @Transient
    var metadata: File? = null

    /**
     * maximum attempts
     */
    @Transient
    var maxRetry: Int

    /**
     * Approximated final length, this represent the sum of all resources sizes
     */
    var nearLength: Long = 0

    /**
     * Download blocks, the size is multiple of [DownloadMission.BLOCK_SIZE].
     * Every entry (block) in this array holds an offset, used to resume the download.
     * An block offset can be -1 if the block was downloaded successfully.
     */
    var blocks: IntArray?

    /**
     * Download/File resume offset in fallback mode (if applicable) [DownloadRunnableFallback]
     */
    @Volatile
    var fallbackResumeOffset: Long = 0

    /**
     * Maximum of download threads running, chosen by the user
     */
    var threadCount: Int = 3

    /**
     * information required to recover a download
     */
    var recoveryInfo: Array<MissionRecoveryInfo?>?

    @Transient
    private var finishCount: Int = 0

    @Volatile
    @Transient
    var running: Boolean = false
    var enqueued: Boolean
    var errCode: Int = ERROR_NOTHING
    var errObject: Exception? = null

    @Transient
    var mHandler: Handler? = null

    @Transient
    private var blockAcquired: BooleanArray?

    @Transient
    private var writingToFileNext: Long = 0

    @Volatile
    @Transient
    private var writingToFile: Boolean = false
    val LOCK: Any = Lock()

    @Transient
    var threads: Array<Thread?> = arrayOfNulls(0)

    @Transient
    var init: Thread? = null

    init {
        if (Objects.requireNonNull(urls).size < 1) throw IllegalArgumentException("urls array is empty")
        this.urls = urls
        this.kind = kind
        offsets = LongArray(urls.size)
        enqueued = true
        maxRetry = 3
        this.storage = storage
        psAlgorithm = psInstance
        if (DEBUG && (psInstance == null) && (urls.size > 1)) {
            Log.w(TAG, "mission created with multiple urls ¿missing post-processing algorithm?")
        }
    }

    /**
     * Acquire a block
     *
     * @return the block or `null` if no more blocks left
     */
    fun acquireBlock(): Block? {
        synchronized(LOCK, {
            for (i in blockAcquired!!.indices) {
                if (!blockAcquired!!.get(i) && blocks!!.get(i) >= 0) {
                    val block: Block = Block()
                    block.position = i
                    block.done = blocks!!.get(i)
                    blockAcquired!!.get(i) = true
                    return block
                }
            }
        })
        return null
    }

    /**
     * Release an block
     *
     * @param position the index of the block
     * @param done     amount of bytes downloaded
     */
    fun releaseBlock(position: Int, done: Int) {
        synchronized(LOCK, {
            blockAcquired!!.get(position) = false
            blocks!!.get(position) = done
        })
    }

    /**
     * Opens a connection
     *
     * @param headRequest `true` for use `HEAD` request method, otherwise, `GET` is used
     * @param rangeStart  range start
     * @param rangeEnd    range end
     * @return a [URLConnection][java.net.URLConnection] linking to the URL.
     * @throws IOException if an I/O exception occurs.
     */
    @Throws(IOException::class)
    fun openConnection(headRequest: Boolean, rangeStart: Long, rangeEnd: Long): HttpURLConnection {
        return openConnection(urls.get(current), headRequest, rangeStart, rangeEnd)
    }

    @Throws(IOException::class)
    fun openConnection(url: String?, headRequest: Boolean, rangeStart: Long, rangeEnd: Long): HttpURLConnection {
        val conn: HttpURLConnection = URL(url).openConnection() as HttpURLConnection
        conn.setInstanceFollowRedirects(true)
        conn.setRequestProperty("User-Agent", DownloaderImpl.Companion.USER_AGENT)
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Accept-Encoding", "*")
        if (headRequest) conn.setRequestMethod("HEAD")

        // BUG workaround: switching between networks can freeze the download forever
        conn.setConnectTimeout(30000)
        if (rangeStart >= 0) {
            var req: String? = "bytes=" + rangeStart + "-"
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
    fun establishConnection(threadId: Int, conn: HttpURLConnection?) {
        val statusCode: Int = conn!!.getResponseCode()
        if (DEBUG) {
            Log.d(TAG, threadId.toString() + ":[request]  Range=" + conn.getRequestProperty("Range"))
            Log.d(TAG, threadId.toString() + ":[response] Code=" + statusCode)
            Log.d(TAG, threadId.toString() + ":[response] Content-Length=" + conn.getContentLength())
            Log.d(TAG, threadId.toString() + ":[response] Content-Range=" + conn.getHeaderField("Content-Range"))
        }
        when (statusCode) {
            204, 205, 207 -> throw HttpError(statusCode)
            416 -> return  // let the download thread handle this error
            else -> if (statusCode < 200 || statusCode > 299) {
                throw HttpError(statusCode)
            }
        }
    }

    private fun notify(what: Int) {
        mHandler!!.obtainMessage(what, this).sendToTarget()
    }

    @Synchronized
    fun notifyProgress(deltaLen: Long) {
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
    fun notifyError(err: Exception?) {
        Log.e(TAG, "notifyError()", err)
        if (err is FileNotFoundException) {
            notifyError(ERROR_FILE_CREATION, null)
        } else if (err is SSLException) {
            notifyError(ERROR_SSL_EXCEPTION, null)
        } else if (err is HttpError) {
            notifyError(err.statusCode, null)
        } else if (err is ConnectException) {
            notifyError(ERROR_CONNECT_HOST, null)
        } else if (err is UnknownHostException) {
            notifyError(ERROR_UNKNOWN_HOST, null)
        } else if (err is SocketTimeoutException) {
            notifyError(ERROR_TIMEOUT, null)
        } else {
            notifyError(ERROR_UNKNOWN_EXCEPTION, err)
        }
    }

    @Synchronized
    fun notifyError(code: Int, err: Exception?) {
        var code: Int = code
        var err: Exception? = err
        Log.e(TAG, "notifyError() code = " + code, err)
        if (err != null && err.cause is ErrnoException) {
            val errno: Int = (err.cause as ErrnoException?).errno
            if (errno == OsConstants.ENOSPC) {
                code = ERROR_INSUFFICIENT_STORAGE
                err = null
            } else if (errno == OsConstants.EACCES) {
                code = ERROR_PERMISSION_DENIED
                err = null
            }
        }
        if (err is IOException) {
            if (err.message!!.contains("Permission denied")) {
                code = ERROR_PERMISSION_DENIED
                err = null
            } else if (err.message!!.contains("ENOSPC")) {
                code = ERROR_INSUFFICIENT_STORAGE
                err = null
            } else if (!storage!!.canWrite()) {
                code = ERROR_FILE_CREATION
                err = null
            }
        }
        errCode = code
        errObject = err
        when (code) {
            ERROR_SSL_EXCEPTION, ERROR_UNKNOWN_HOST, ERROR_CONNECT_HOST, ERROR_TIMEOUT -> {}
            else ->                 // also checks for server errors
                if (code < 500 || code > 599) enqueued = false
        }
        notify(DownloadManagerService.Companion.MESSAGE_ERROR)
        if (running) pauseThreads()
    }

    @Synchronized
    fun notifyFinished() {
        if (current < urls.size) {
            if (++finishCount < threads.size) return
            if (DEBUG) {
                Log.d(TAG, "onFinish: downloaded " + (current + 1) + "/" + urls.size)
            }
            current++
            if (current < urls.size) {
                // prepare next sub-mission
                offsets.get(current) = offsets.get(current - 1) + length
                initializer()
                return
            }
        }
        if (psAlgorithm != null && psState == 0) {
            threads = arrayOf(
                    runAsync(1, Runnable({ doPostprocessing() }))
            )
            return
        }


        // this mission is fully finished
        unknownLength = false
        enqueued = false
        running = false
        deleteThisFromFile()
        notify(DownloadManagerService.Companion.MESSAGE_FINISHED)
    }

    private fun notifyPostProcessing(state: Int) {
        val action: String
        when (state) {
            1 -> action = "Running"
            2 -> action = "Completed"
            else -> action = "Failed"
        }
        Log.d(TAG, action + " postprocessing on " + storage!!.getName())
        if (state == 2) {
            psState = state
            return
        }
        synchronized(LOCK, {

            // don't return without fully write the current state
            psState = state
            writeThisToFile()
        })
    }

    /**
     * Start downloading with multiple threads.
     */
    fun start() {
        if (running || isFinished() || (urls.size < 1)) return

        // ensure that the previous state is completely paused.
        joinForThreads(10000)
        running = true
        errCode = ERROR_NOTHING
        if (hasInvalidStorage()) {
            notifyError(ERROR_FILE_CREATION, null)
            return
        }
        if (current >= urls.size) {
            notifyFinished()
            return
        }
        notify(DownloadManagerService.Companion.MESSAGE_RUNNING)
        if (urls.get(current) == null) {
            doRecover(ERROR_RESOURCE_GONE)
            return
        }
        if (blocks == null) {
            initializer()
            return
        }
        init = null
        finishCount = 0
        blockAcquired = BooleanArray(blocks!!.size)
        if (blocks!!.size < 1) {
            threads = arrayOf(runAsync(1, DownloadRunnableFallback(this)))
        } else {
            var remainingBlocks: Int = 0
            for (block: Int in blocks!!) if (block >= 0) remainingBlocks++
            if (remainingBlocks < 1) {
                notifyFinished()
                return
            }
            threads = arrayOfNulls(min(threadCount.toDouble(), remainingBlocks.toDouble()).toInt())
            for (i in threads.indices) {
                threads.get(i) = runAsync(i + 1, DownloadRunnable(this, i))
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
        notify(DownloadManagerService.Companion.MESSAGE_PAUSED)
        if (init != null && init!!.isAlive()) {
            // NOTE: if start() method is running ¡will no have effect!
            init!!.interrupt()
            synchronized(LOCK, { resetState(false, true, ERROR_NOTHING) })
            return
        }
        if (DEBUG && unknownLength) {
            Log.w(TAG, "pausing a download that can not be resumed (range requests not allowed by the server).")
        }
        init = null
        pauseThreads()
    }

    private fun pauseThreads() {
        running = false
        joinForThreads(-1)
        writeThisToFile()
    }

    /**
     * Removes the downloaded file and the meta file
     */
    public override fun delete(): Boolean {
        if (psAlgorithm != null) psAlgorithm.cleanupTemporalDir()
        notify(DownloadManagerService.Companion.MESSAGE_DELETED)
        val res: Boolean = deleteThisFromFile()
        if (!super.delete()) return false
        return res
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
        threads = arrayOfNulls(0)
        fallbackResumeOffset = 0
        blocks = null
        blockAcquired = null
        if (rollback) current = 0
        if (persistChanges) writeThisToFile()
    }

    private fun initializer() {
        init = runAsync(DownloadInitializer.Companion.mId, DownloadInitializer(this))
    }

    private fun writeThisToFileAsync() {
        runAsync(-2, Runnable({ writeThisToFile() }))
    }

    /**
     * Write this [DownloadMission] to the meta file asynchronously
     * if no thread is already running.
     */
    fun writeThisToFile() {
        synchronized(LOCK, {
            if (metadata == null) return
            Utility.writeToFile(metadata!!, this)
            writingToFile = false
        })
    }

    /**
     * Indicates if the download if fully finished
     *
     * @return true, otherwise, false
     */
    fun isFinished(): Boolean {
        return current >= urls.size && (psAlgorithm == null || psState == 2)
    }

    /**
     * Indicates if the download file is corrupt due a failed post-processing
     *
     * @return `true` if this mission is unrecoverable
     */
    fun isPsFailed(): Boolean {
        when (errCode) {
            ERROR_POSTPROCESSING, ERROR_POSTPROCESSING_STOPPED -> return psAlgorithm.worksOnSameFile
        }
        return false
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
        var calculated: Long
        if (psState == 1 || psState == 3) {
            return length
        }
        calculated = offsets.get(if (current < offsets.size) current else (offsets.size - 1)) + length
        calculated -= offsets.get(0) // don't count reserved space
        return max(calculated.toDouble(), nearLength.toDouble()).toLong()
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
        threads.get(0)!!.interrupt()
    }

    /**
     * Indicates whatever the backed storage is invalid
     *
     * @return `true`, if storage is invalid and cannot be used
     */
    fun hasInvalidStorage(): Boolean {
        return (errCode == ERROR_PROGRESS_LOST) || (storage == null) || !storage!!.existsAsFile()
    }

    /**
     * Indicates whatever is possible to start the mission
     *
     * @return `true` is this mission its "healthy", otherwise, `false`
     */
    fun isCorrupt(): Boolean {
        if (urls.size < 1) return false
        return (isPsFailed() || errCode == ERROR_POSTPROCESSING_HOLD) || isFinished()
    }

    /**
     * Indicates if mission urls has expired and there an attempt to renovate them
     *
     * @return `true` if the mission is running a recovery procedure, otherwise, `false`
     */
    fun isRecovering(): Boolean {
        return (threads.size > 0) && threads.get(0) is DownloadMissionRecover && threads.get(0).isAlive()
    }

    private fun doPostprocessing() {
        errCode = ERROR_NOTHING
        errObject = null
        val thread: Thread = Thread.currentThread()
        notifyPostProcessing(1)
        if (DEBUG) {
            thread.setName("[" + TAG + "]  ps = " + psAlgorithm + "  filename = " + storage!!.getName())
        }
        var exception: Exception? = null
        try {
            psAlgorithm.run(this)
        } catch (err: Exception) {
            Log.e(TAG, "Post-processing failed. " + psAlgorithm.toString(), err)
            if (err is InterruptedIOException || err is ClosedByInterruptException || thread.isInterrupted()) {
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
    fun doRecover(errorCode: Int) {
        Log.i(TAG, "Attempting to recover the mission: " + storage!!.getName())
        if (recoveryInfo == null) {
            notifyError(errorCode, null)
            urls = arrayOfNulls(0) // mark this mission as dead
            return
        }
        joinForThreads(0)
        threads = arrayOf<Thread?>(
                runAsync(DownloadMissionRecover.Companion.mID, DownloadMissionRecover(this, errorCode))
        )
    }

    private fun deleteThisFromFile(): Boolean {
        synchronized(LOCK, {
            val res: Boolean = metadata!!.delete()
            metadata = null
            return res
        })
    }

    /**
     * run a new thread
     *
     * @param id  id of new thread (used for debugging only)
     * @param who the Runnable whose `run` method is invoked.
     */
    private fun runAsync(id: Int, who: Runnable): Thread {
        return runAsync(id, Thread(who))
    }

    /**
     * run a new thread
     *
     * @param id  id of new thread (used for debugging only)
     * @param who the Thread whose `run` method is invoked when this thread is started
     * @return the passed thread
     */
    private fun runAsync(id: Int, who: Thread): Thread {
        // known thread ids:
        //   -2:     state saving by  notifyProgress()  method
        //   -1:     wait for saving the state by  pause()  method
        //    0:     initializer
        //  >=1:     any download thread
        if (DEBUG) {
            who.setName(String.format("%s[%s] %s", TAG, id, storage!!.getName()))
        }
        who.start()
        return who
    }

    /**
     * Waits at most `millis` milliseconds for the thread to die
     *
     * @param millis the time to wait in milliseconds
     */
    private fun joinForThreads(millis: Int) {
        val currentThread: Thread = Thread.currentThread()
        if ((init != null) && (init !== currentThread) && init!!.isAlive()) {
            init!!.interrupt()
            if (millis > 0) {
                try {
                    init!!.join(millis.toLong())
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Initializer thread is still running", e)
                    return
                }
            }
        }

        // if a thread is still alive, possible reasons:
        //      slow device
        //      the user is spamming start/pause buttons
        //      start() method called quickly after pause()
        for (thread: Thread? in threads) {
            if (!thread!!.isAlive() || thread === Thread.currentThread()) continue
            thread.interrupt()
        }
        try {
            for (thread: Thread? in threads) {
                if (!thread!!.isAlive()) continue
                if (DEBUG) {
                    Log.w(TAG, "thread alive: " + thread.getName())
                }
                if (millis > 0) thread.join(millis.toLong())
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("A download thread is still running", e)
        }
    }

    internal class HttpError(val statusCode: Int) : Exception() {
        public override fun getMessage(): String {
            return "HTTP " + statusCode
        }
    }

    class Block() {
        var position: Int = 0
        var done: Int = 0
    }

    private class Lock() : Serializable { // java.lang.Object cannot be used because is not serializable
    }

    companion object {
        private val serialVersionUID: Long = 6L // last bump: 07 october 2019
        val BUFFER_SIZE: Int = 64 * 1024
        val BLOCK_SIZE: Int = 512 * 1024
        private val TAG: String = "DownloadMission"
        val ERROR_NOTHING: Int = -1
        val ERROR_PATH_CREATION: Int = 1000
        val ERROR_FILE_CREATION: Int = 1001
        val ERROR_UNKNOWN_EXCEPTION: Int = 1002
        val ERROR_PERMISSION_DENIED: Int = 1003
        val ERROR_SSL_EXCEPTION: Int = 1004
        val ERROR_UNKNOWN_HOST: Int = 1005
        val ERROR_CONNECT_HOST: Int = 1006
        val ERROR_POSTPROCESSING: Int = 1007
        val ERROR_POSTPROCESSING_STOPPED: Int = 1008
        val ERROR_POSTPROCESSING_HOLD: Int = 1009
        val ERROR_INSUFFICIENT_STORAGE: Int = 1010
        val ERROR_PROGRESS_LOST: Int = 1011
        val ERROR_TIMEOUT: Int = 1012
        val ERROR_RESOURCE_GONE: Int = 1013
        val ERROR_HTTP_NO_CONTENT: Int = 204
        val ERROR_HTTP_FORBIDDEN: Int = 403
    }
}
