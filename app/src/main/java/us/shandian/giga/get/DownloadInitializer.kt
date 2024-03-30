package us.shandian.giga.get

import android.util.Log
import org.schabi.newpipe.BuildConfig.DEBUG
import us.shandian.giga.util.Utility
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.nio.channels.ClosedByInterruptException

class DownloadInitializer internal constructor(private val mMission: DownloadMission) : Thread() {
    private var mConn: HttpURLConnection? = null
    private fun dispose() {
        try {
            mConn!!.getInputStream().close()
        } catch (e: Exception) {
            // nothing to do
        }
    }

    public override fun run() {
        if (mMission.current > 0) mMission.resetState(false, true, DownloadMission.Companion.ERROR_NOTHING)
        var retryCount: Int = 0
        var httpCode: Int = 204
        while (true) {
            try {
                if (mMission.blocks == null && mMission.current == 0) {
                    // calculate the whole size of the mission
                    var finalLength: Long = 0
                    var lowestSize: Long = Long.MAX_VALUE
                    var i: Int = 0
                    while (i < mMission.urls.size && mMission.running) {
                        mConn = mMission.openConnection(mMission.urls.get(i), true, 0, 0)
                        mMission.establishConnection(mId, mConn)
                        dispose()
                        if (interrupted()) return
                        val length: Long = Utility.getTotalContentLength(mConn)
                        if (i == 0) {
                            httpCode = mConn!!.getResponseCode()
                            mMission.length = length
                        }
                        if (length > 0) finalLength += length
                        if (length < lowestSize) lowestSize = length
                        i++
                    }
                    mMission.nearLength = finalLength

                    // reserve space at the start of the file
                    if (mMission.psAlgorithm != null && mMission.psAlgorithm.reserveSpace) {
                        if (lowestSize < 1) {
                            // the length is unknown use the default size
                            mMission.offsets.get(0) = RESERVE_SPACE_DEFAULT.toLong()
                        } else {
                            // use the smallest resource size to download, otherwise, use the maximum
                            mMission.offsets.get(0) = if (lowestSize < RESERVE_SPACE_MAXIMUM) lowestSize else RESERVE_SPACE_MAXIMUM.toLong()
                        }
                    }
                } else {
                    // ask for the current resource length
                    mConn = mMission.openConnection(true, 0, 0)
                    mMission.establishConnection(mId, mConn)
                    dispose()
                    if (!mMission.running || interrupted()) return
                    httpCode = mConn!!.getResponseCode()
                    mMission.length = Utility.getTotalContentLength(mConn)
                }
                if (mMission.length == 0L || httpCode == 204) {
                    mMission.notifyError(DownloadMission.Companion.ERROR_HTTP_NO_CONTENT, null)
                    return
                }

                // check for dynamic generated content
                if (mMission.length == -1L && mConn!!.getResponseCode() == 200) {
                    mMission.blocks = IntArray(0)
                    mMission.length = 0
                    mMission.unknownLength = true
                    if (DEBUG) {
                        Log.d(TAG, "falling back (unknown length)")
                    }
                } else {
                    // Open again
                    mConn = mMission.openConnection(true, mMission.length - 10, mMission.length)
                    mMission.establishConnection(mId, mConn)
                    dispose()
                    if (!mMission.running || interrupted()) return
                    synchronized(mMission.LOCK, {
                        if (mConn!!.getResponseCode() == 206) {
                            if (mMission.threadCount > 1) {
                                var count: Int = (mMission.length / DownloadMission.Companion.BLOCK_SIZE).toInt()
                                if ((count * DownloadMission.Companion.BLOCK_SIZE) < mMission.length) count++
                                mMission.blocks = IntArray(count)
                            } else {
                                // if one thread is required don't calculate blocks, is useless
                                mMission.blocks = IntArray(0)
                                mMission.unknownLength = false
                            }
                            if (DEBUG) {
                                Log.d(TAG, "http response code = " + mConn!!.getResponseCode())
                            }
                        } else {
                            // Fallback to single thread
                            mMission.blocks = IntArray(0)
                            mMission.unknownLength = false
                            if (DEBUG) {
                                Log.d(TAG, "falling back due http response code = " + mConn!!.getResponseCode())
                            }
                        }
                    })
                    if (!mMission.running || interrupted()) return
                }
                mMission.storage!!.getStream().use({ fs ->
                    fs.setLength(mMission.offsets.get(mMission.current) + mMission.length)
                    fs.seek(mMission.offsets.get(mMission.current))
                })
                if (!mMission.running || interrupted()) return
                if (!mMission.unknownLength && mMission.recoveryInfo != null) {
                    val entityTag: String = mConn!!.getHeaderField("ETAG")
                    val lastModified: String = mConn!!.getHeaderField("Last-Modified")
                    val recovery: MissionRecoveryInfo? = mMission.recoveryInfo!!.get(mMission.current)
                    if (!TextUtils.isEmpty(entityTag)) {
                        recovery!!.validateCondition = entityTag
                    } else if (!TextUtils.isEmpty(lastModified)) {
                        recovery!!.validateCondition = lastModified // Note: this is less precise
                    } else {
                        recovery!!.validateCondition = null
                    }
                }
                mMission.running = false
                break
            } catch (e: InterruptedIOException) {
                return
            } catch (e: ClosedByInterruptException) {
                return
            } catch (e: Exception) {
                if (!mMission.running || super.isInterrupted()) return
                if (e is DownloadMission.HttpError && e.statusCode == DownloadMission.Companion.ERROR_HTTP_FORBIDDEN) {
                    // for youtube streams. The url has expired
                    interrupt()
                    mMission.doRecover(DownloadMission.Companion.ERROR_HTTP_FORBIDDEN)
                    return
                }
                if (e is IOException && e.message!!.contains("Permission denied")) {
                    mMission.notifyError(DownloadMission.Companion.ERROR_PERMISSION_DENIED, e)
                    return
                }
                if (retryCount++ > mMission.maxRetry) {
                    Log.e(TAG, "initializer failed", e)
                    mMission.notifyError(e)
                    return
                }
                Log.e(TAG, "initializer failed, retrying", e)
            }
        }
        mMission.start()
    }

    public override fun interrupt() {
        super.interrupt()
        if (mConn != null) dispose()
    }

    companion object {
        private val TAG: String = "DownloadInitializer"
        val mId: Int = 0
        private val RESERVE_SPACE_DEFAULT: Int = 5 * 1024 * 1024 // 5 MiB
        private val RESERVE_SPACE_MAXIMUM: Int = 150 * 1024 * 1024 // 150 MiB
    }
}
