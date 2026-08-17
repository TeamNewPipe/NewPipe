package us.shandian.giga.get

import android.util.Log
import okio.IOException
import java.nio.channels.ClosedByInterruptException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.streams.io.SharpStream
import us.shandian.giga.get.DownloadMission.Companion.ERROR_HTTP_FORBIDDEN
import us.shandian.giga.get.DownloadMission.HttpError

/**
 * Runnable to download blocks of a file until the file is completely downloaded,
 * an error occurs or the process is stopped.
 */
internal class DownloadRunnable(private val mMission: DownloadMission, private val mId: Int) {
    private var mConn: java.net.HttpURLConnection? = null

    private fun releaseBlock(block: DownloadMission.Block, remain: Long) {
        // set the block offset to -1 if it is completed
        mMission.releaseBlock(block.position, if (remain < 0) -1 else block.done)
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        var retry = false
        var block: DownloadMission.Block? = null
        var retryCount = 0
        var f: SharpStream? = null

        try {
            f = mMission.storage!!.getStream()
        } catch (e: IOException) {
            mMission.notifyError(e) // this never should happen
            return@withContext
        }

        try {
            while (mMission.running && mMission.errCode == DownloadMission.ERROR_NOTHING) {
                if (!retry) {
                    block = mMission.acquireBlock()
                }

                if (block == null) {
                    if (DEBUG) Log.d(TAG, "$mId:no more blocks left, exiting")
                    break
                }

                if (DEBUG) {
                    if (retry) {
                        Log.d(TAG, "$mId:retry block at position=${block.position} from the start")
                    } else {
                        Log.d(TAG, "$mId:acquired block at position=${block.position} done=${block.done}")
                    }
                }

                var start = block.position.toLong() * DownloadMission.BLOCK_SIZE
                var end = start + DownloadMission.BLOCK_SIZE - 1

                start += block.done.toLong()

                if (end >= mMission.length) {
                    end = mMission.length - 1
                }

                try {
                    mConn = mMission.openConnection(false, start, end)
                    mMission.establishConnection(mId, mConn!!)

                    // check if the download can be resumed
                    if (mConn!!.responseCode == 416) {
                        if (block.done > 0) {
                            // try again from the start (of the block)
                            mMission.notifyProgress((-block.done).toLong())
                            block.done = 0
                            retry = true
                            mConn!!.disconnect()
                            continue
                        }

                        throw HttpError(416)
                    }

                    retry = false

                    // The server may be ignoring the range request
                    if (mConn!!.responseCode != 206) {
                        if (DEBUG) {
                            Log.e(TAG, "$mId:Unsupported ${mConn!!.responseCode}")
                        }
                        mMission.notifyError(HttpError(mConn!!.responseCode))
                        break
                    }

                    f.seek(mMission.offsets[mMission.current] + start)

                    mConn!!.inputStream.use { `is` ->
                        val buf = ByteArray(DownloadMission.BUFFER_SIZE)
                        var len = 0

                        // use always start <= end
                        // fixes a deadlock because in some videos, youtube is sending one byte alone
                        while (start <= end && mMission.running) {
                            len = `is`.read(buf, 0, buf.size)
                            if (len == -1) break
                            f.write(buf, 0, len)
                            start += len.toLong()
                            block.done += len
                            mMission.notifyProgress(len.toLong())
                        }
                    }

                    if (DEBUG && mMission.running) {
                        Log.d(TAG, "$mId:position ${block.position} stopped $start/$end")
                    }
                } catch (e: Exception) {
                    if (!mMission.running || e is ClosedByInterruptException || e is kotlinx.coroutines.CancellationException) break

                    if (e is HttpError && e.statusCode == ERROR_HTTP_FORBIDDEN) {
                        // for youtube streams. The url has expired, recover
                        f.close()

                        if (mId == 1) {
                            // only the first thread will execute the recovery procedure
                            mMission.doRecover(ERROR_HTTP_FORBIDDEN)
                        }
                        return@withContext
                    }

                    if (retryCount++ >= mMission.maxRetry) {
                        mMission.notifyError(e)
                        break
                    }

                    retry = true
                } finally {
                    if (!retry && block != null) releaseBlock(block, end - start)
                }
            }
        } finally {
            f.close()
        }

        if (DEBUG) {
            Log.d(TAG, "coroutine $mId exited from main download loop")
        }

        if (mMission.errCode == DownloadMission.ERROR_NOTHING && mMission.running) {
            if (DEBUG) {
                Log.d(TAG, "no error has happened, notifying")
            }
            mMission.notifyFinished()
        }

        if (DEBUG && !mMission.running) {
            Log.d(TAG, "The mission has been paused. Passing.")
        }
    }

    companion object {
        private const val TAG = "DownloadRunnable"
    }
}
