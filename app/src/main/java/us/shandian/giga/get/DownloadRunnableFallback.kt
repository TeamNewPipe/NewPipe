package us.shandian.giga.get

import android.util.Log
import org.schabi.newpipe.BuildConfig.DEBUG
import us.shandian.giga.util.Utility
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.nio.channels.ClosedByInterruptException

/**
 * Single-threaded fallback mode
 */
class DownloadRunnableFallback internal constructor(private val mMission: DownloadMission) : Thread() {
    private var mRetryCount: Int = 0
    private var mIs: InputStream? = null
    private var mF: SharpStream? = null
    private var mConn: HttpURLConnection? = null
    private fun dispose() {
        try {
            try {
                if (mIs != null) mIs!!.close()
            } finally {
                mConn!!.disconnect()
            }
        } catch (e: IOException) {
            // nothing to do
        }
        if (mF != null) mF.close()
    }

    public override fun run() {
        val done: Boolean
        var start: Long = mMission.fallbackResumeOffset
        if (DEBUG && !mMission.unknownLength && (start > 0)) {
            Log.i(TAG, "Resuming a single-thread download at " + start)
        }
        try {
            val rangeStart: Long = if ((mMission.unknownLength || start < 1)) -1 else start
            val mId: Int = 1
            mConn = mMission.openConnection(false, rangeStart, -1)
            if (mRetryCount == 0 && rangeStart == -1L) {
                // workaround: bypass android connection pool
                mConn!!.setRequestProperty("Range", "bytes=0-")
            }
            mMission.establishConnection(mId, mConn)

            // check if the download can be resumed
            if (mConn!!.getResponseCode() == 416 && start > 0) {
                mMission.notifyProgress(-start)
                start = 0
                mRetryCount--
                throw DownloadMission.HttpError(416)
            }

            // secondary check for the file length
            if (!mMission.unknownLength) mMission.unknownLength = Utility.getContentLength(mConn) == -1L
            if (mMission.unknownLength || mConn!!.getResponseCode() == 200) {
                // restart amount of bytes downloaded
                mMission.done = mMission.offsets.get(mMission.current) - mMission.offsets.get(0)
            }
            mF = mMission.storage!!.getStream()
            mF.seek(mMission.offsets.get(mMission.current) + start)
            mIs = mConn!!.getInputStream()
            val buf: ByteArray = ByteArray(DownloadMission.Companion.BUFFER_SIZE)
            var len: Int = 0
            while (mMission.running && (mIs.read(buf, 0, buf.size).also({ len = it })) != -1) {
                mF.write(buf, 0, len)
                start += len.toLong()
                mMission.notifyProgress(len.toLong())
            }
            dispose()

            // if thread goes interrupted check if the last part is written. This avoid re-download the whole file
            done = len == -1
        } catch (e: Exception) {
            dispose()
            mMission.fallbackResumeOffset = start
            if (!mMission.running || e is ClosedByInterruptException) return
            if (e is DownloadMission.HttpError && e.statusCode == DownloadMission.Companion.ERROR_HTTP_FORBIDDEN) {
                // for youtube streams. The url has expired, recover
                dispose()
                mMission.doRecover(DownloadMission.Companion.ERROR_HTTP_FORBIDDEN)
                return
            }
            if (mRetryCount++ >= mMission.maxRetry) {
                mMission.notifyError(e)
                return
            }
            if (DEBUG) {
                Log.e(TAG, "got exception, retrying...", e)
            }
            run() // try again
            return
        }
        if (done) {
            mMission.notifyFinished()
        } else {
            mMission.fallbackResumeOffset = start
        }
    }

    public override fun interrupt() {
        super.interrupt()
        if (mConn != null) {
            try {
                mConn!!.disconnect()
            } catch (e: Exception) {
                // nothing to do
            }
        }
    }

    companion object {
        private val TAG: String = "DownloadRunnableFallback"
    }
}
