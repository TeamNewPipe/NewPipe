package us.shandian.giga.get

import android.util.Log
import okio.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.nio.channels.ClosedByInterruptException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.BuildConfig.DEBUG
import org.schabi.newpipe.streams.io.SharpStream
import us.shandian.giga.get.DownloadMission.Companion.ERROR_HTTP_FORBIDDEN
import us.shandian.giga.get.DownloadMission.HttpError
import us.shandian.giga.util.Utility

/**
 * Single-threaded fallback mode
 */
internal class DownloadRunnableFallback(private val mMission: DownloadMission) {
    private var mRetryCount = 0
    private var mIs: InputStream? = null
    private var mF: SharpStream? = null
    private var mConn: HttpURLConnection? = null

    private fun dispose() {
        try {
            try {
                mIs?.close()
            } finally {
                mConn?.disconnect()
            }
        } catch (e: IOException) {
            // nothing to do
        }

        mF?.close()
    }

    suspend fun run(): Unit = withContext(Dispatchers.IO) {
        var done: Boolean
        var start = mMission.fallbackResumeOffset

        if (DEBUG && !mMission.unknownLength && start > 0) {
            Log.i(TAG, "Resuming a single-thread download at $start")
        }

        try {
            val rangeStart = if (mMission.unknownLength || start < 1) -1 else start

            val mId = 1
            mConn = mMission.openConnection(false, rangeStart, -1)

            if (mRetryCount == 0 && rangeStart == -1L) {
                // workaround: bypass android connection pool
                mConn!!.setRequestProperty("Range", "bytes=0-")
            }

            mMission.establishConnection(mId, mConn!!)

            // check if the download can be resumed
            if (mConn!!.responseCode == 416 && start > 0) {
                mMission.notifyProgress(-start)
                start = 0
                mRetryCount--
                throw HttpError(416)
            }

            // secondary check for the file length
            if (!mMission.unknownLength) {
                mMission.unknownLength = Utility.getContentLength(mConn!!) == -1L
            }

            if (mMission.unknownLength || mConn!!.responseCode == 200) {
                // restart amount of bytes downloaded
                mMission.done = mMission.offsets[mMission.current] - mMission.offsets[0]
                start = 0 // reset position to avoid writing at wrong offset
            }

            mF = mMission.storage!!.getStream()
            mF!!.seek(mMission.offsets[mMission.current] + start)

            mIs = mConn!!.inputStream

            val buf = ByteArray(DownloadMission.BUFFER_SIZE)
            var len = 0

            while (mMission.running && mIs!!.read(buf, 0, buf.size).also { len = it } != -1) {
                mF!!.write(buf, 0, len)
                start += len.toLong()
                mMission.notifyProgress(len.toLong())
            }

            dispose()

            // if job goes cancelled check if the last part is written. This avoid re-download the whole file
            done = len == -1
        } catch (e: Exception) {
            dispose()

            mMission.fallbackResumeOffset = start

            if (!mMission.running || e is ClosedByInterruptException || e is kotlinx.coroutines.CancellationException) return@withContext

            if (e is HttpError && e.statusCode == ERROR_HTTP_FORBIDDEN) {
                // for youtube streams. The url has expired, recover
                dispose()
                mMission.doRecover(ERROR_HTTP_FORBIDDEN)
                return@withContext
            }

            if (mRetryCount++ >= mMission.maxRetry) {
                mMission.notifyError(e)
                return@withContext
            }

            if (DEBUG) {
                Log.e(TAG, "got exception, retrying...", e)
            }

            run() // try again
            return@withContext
        }

        if (done) {
            mMission.notifyFinished()
        } else {
            mMission.fallbackResumeOffset = start
        }
    }

    companion object {
        private const val TAG = "DLRunnableFallback"
    }
}
