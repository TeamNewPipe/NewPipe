package us.shandian.giga.get

import android.text.TextUtils
import android.util.Log
import java.io.InterruptedIOException
import okio.IOException
import java.nio.channels.ClosedByInterruptException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.BuildConfig.DEBUG
import us.shandian.giga.get.DownloadMission.Companion.ERROR_HTTP_FORBIDDEN
import us.shandian.giga.util.Utility

internal class DownloadInitializer(private val mMission: DownloadMission) {
    private var mConn: java.net.HttpURLConnection? = null

    private fun dispose() {
        try {
            mConn?.inputStream?.close()
        } catch (e: Exception) {
            // nothing to do
        }
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        if (mMission.current > 0) mMission.resetState(false, true, DownloadMission.ERROR_NOTHING)

        var retryCount = 0
        var httpCode = 204

        while (true) {
            try {
                if (mMission.blocks == null && mMission.current == 0) {
                    // calculate the whole size of the mission
                    var finalLength: Long = 0
                    var lowestSize = Long.MAX_VALUE

                    for (i in 0 until (mMission.urls?.size ?: 0)) {
                        if (!mMission.running) return@withContext

                        mConn = mMission.openConnection(mMission.urls!![i], true, 0, 0)
                        mMission.establishConnection(mId, mConn!!)
                        dispose()

                        val length = Utility.getTotalContentLength(mConn!!)

                        if (i == 0) {
                            httpCode = mConn!!.responseCode
                            mMission.length = length
                        }

                        if (length > 0) finalLength += length
                        if (length < lowestSize) lowestSize = length
                    }

                    mMission.nearLength = finalLength

                    // reserve space at the start of the file
                    if (mMission.psAlgorithm != null && mMission.psAlgorithm!!.reserveSpace) {
                        if (lowestSize < 1) {
                            // the length is unknown use the default size
                            mMission.offsets[0] = RESERVE_SPACE_DEFAULT.toLong()
                        } else {
                            // use the smallest resource size to download, otherwise, use the maximum
                            mMission.offsets[0] = if (lowestSize < RESERVE_SPACE_MAXIMUM) lowestSize else RESERVE_SPACE_MAXIMUM.toLong()
                        }
                    }
                } else {
                    // ask for the current resource length
                    mConn = mMission.openConnection(true, 0, 0)
                    mMission.establishConnection(mId, mConn!!)
                    dispose()

                    if (!mMission.running) return@withContext

                    httpCode = mConn!!.responseCode
                    mMission.length = Utility.getTotalContentLength(mConn!!)
                }

                if (mMission.length == 0L || httpCode == 204) {
                    mMission.notifyError(DownloadMission.ERROR_HTTP_NO_CONTENT, null)
                    return@withContext
                }

                // check for dynamic generated content
                if (mMission.length == -1L && mConn!!.responseCode == 200) {
                    mMission.blocks = IntArray(0)
                    mMission.length = 0
                    mMission.unknownLength = true

                    if (DEBUG) {
                        Log.d(TAG, "falling back (unknown length)")
                    }
                } else {
                    // Open again
                    mConn = mMission.openConnection(true, mMission.length - 10, mMission.length)
                    mMission.establishConnection(mId, mConn!!)
                    dispose()

                    if (!mMission.running) return@withContext

                    synchronized(mMission.LOCK) {
                        if (mConn!!.responseCode == 206) {
                            if (mMission.threadCount > 1) {
                                var count = (mMission.length / DownloadMission.BLOCK_SIZE).toInt()
                                if (count.toLong() * DownloadMission.BLOCK_SIZE < mMission.length) count++

                                mMission.blocks = IntArray(count)
                            } else {
                                // if one thread is required don't calculate blocks, is useless
                                mMission.blocks = IntArray(0)
                                mMission.unknownLength = false
                            }

                            if (DEBUG) {
                                Log.d(TAG, "http response code = ${mConn!!.responseCode}")
                            }
                        } else {
                            // Fallback to single thread
                            mMission.blocks = IntArray(0)
                            mMission.unknownLength = false

                            if (DEBUG) {
                                Log.d(TAG, "falling back due http response code = ${mConn!!.responseCode}")
                            }
                        }
                    }

                    if (!mMission.running) return@withContext
                }

                mMission.storage!!.getStream().use { fs ->
                    fs.setLength(mMission.offsets[mMission.current] + mMission.length)
                    fs.seek(mMission.offsets[mMission.current])
                }

                if (!mMission.running) return@withContext

                if (!mMission.unknownLength && mMission.recoveryInfo != null) {
                    val entityTag = mConn!!.getHeaderField("ETAG")
                    val lastModified = mConn!!.getHeaderField("Last-Modified")
                    val recovery = mMission.recoveryInfo!![mMission.current]

                    if (!TextUtils.isEmpty(entityTag)) {
                        recovery.validateCondition = entityTag
                    } else if (!TextUtils.isEmpty(lastModified)) {
                        recovery.validateCondition = lastModified // Note: this is less precise
                    } else {
                        recovery.validateCondition = null
                    }
                }

                mMission.running = false
                break
            } catch (e: InterruptedIOException) {
                return@withContext
            } catch (e: ClosedByInterruptException) {
                return@withContext
            } catch (e: kotlinx.coroutines.CancellationException) {
                return@withContext
            } catch (e: Exception) {
                if (!mMission.running) return@withContext

                if (e is DownloadMission.HttpError && e.statusCode == ERROR_HTTP_FORBIDDEN) {
                    // for youtube streams. The url has expired
                    mMission.doRecover(ERROR_HTTP_FORBIDDEN)
                    return@withContext
                }

                if (e is IOException && e.message?.contains("Permission denied") == true) {
                    mMission.notifyError(DownloadMission.ERROR_PERMISSION_DENIED, e)
                    return@withContext
                }

                if (retryCount++ > mMission.maxRetry) {
                    Log.e(TAG, "initializer failed", e)
                    mMission.notifyError(e)
                    return@withContext
                }

                Log.e(TAG, "initializer failed, retrying", e)
            }
        }

        mMission.start()
    }

    companion object {
        private const val TAG = "DownloadInitializer"
        const val mId = 0
        private const val RESERVE_SPACE_DEFAULT = 5 * 1024 * 1024 // 5 MiB
        private const val RESERVE_SPACE_MAXIMUM = 150 * 1024 * 1024 // 150 MiB
    }
}
