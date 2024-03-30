package us.shandian.giga.postprocessing

import android.util.Log
import org.schabi.newpipe.streams.io.SharpStream
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.io.ChunkFileInputStream
import us.shandian.giga.io.CircularFileWriter
import us.shandian.giga.io.CircularFileWriter.OffsetChecker
import us.shandian.giga.io.CircularFileWriter.WriteErrorHandle
import us.shandian.giga.io.ProgressReport
import java.io.File
import java.io.IOException
import java.io.Serializable
import kotlin.math.max

abstract class Postprocessing // for debugging only
internal constructor(
        /**
         * Indicates whether the selected algorithm needs space reserved at the beginning of the file
         */
        var reserveSpace: Boolean,
        /**
         * Get a boolean value that indicate if the given algorithm work on the same
         * file
         */
        var worksOnSameFile: Boolean,
        /**
         * Gets the given algorithm short name
         */
        private val name: String) : Serializable {
    private var args: Array<String>?

    @Transient
    private var mission: DownloadMission? = null

    @Transient
    private var tempFile: File? = null
    fun setTemporalDir(directory: File) {
        val rnd: Long = ((Math.random() * 100000.0f).toInt()).toLong()
        tempFile = File(directory, rnd.toString() + "_" + System.nanoTime() + ".tmp")
    }

    fun cleanupTemporalDir() {
        if (tempFile != null && tempFile!!.exists()) {
            try {
                tempFile!!.delete()
            } catch (e: Exception) {
                // nothing to do
            }
        }
    }

    @Throws(IOException::class)
    fun run(target: DownloadMission?) {
        mission = target
        var result: Int
        var finalLength: Long = -1
        mission!!.done = 0
        val length: Long = mission!!.storage!!.length() - mission!!.offsets.get(0)
        mission!!.length = max(length.toDouble(), mission!!.nearLength.toDouble()).toLong()
        val readProgress: ProgressReport = ProgressReport({ position: Long ->
            var position: Long = position
            position -= mission!!.offsets.get(0)
            if (position > mission!!.done) mission!!.done = position
        })
        if (worksOnSameFile) {
            val sources: Array<ChunkFileInputStream?> = arrayOfNulls(mission!!.urls.size)
            try {
                var i: Int = 0
                var j: Int = 1
                while (i < sources.size) {
                    val source: SharpStream? = mission!!.storage!!.getStream()
                    val end: Long = if (j < sources.size) mission!!.offsets.get(j) else source!!.length()
                    sources.get(i) = ChunkFileInputStream(source, mission!!.offsets.get(i), end, readProgress)
                    i++
                    j++
                }
                if (test(*sources)) {
                    for (source: SharpStream? in sources) source!!.rewind()
                    val checker: OffsetChecker = OffsetChecker({
                        for (source: ChunkFileInputStream? in sources) {
                            /*
                             * WARNING: never use rewind() in any chunk after any writing (especially on first chunks)
                             *          or the CircularFileWriter can lead to unexpected results
                             */
                            if (source!!.isClosed() || source.available() < 1) {
                                continue  // the selected source is not used anymore
                            }
                            return@OffsetChecker source.getFilePointer() - 1
                        }
                        -1
                    })
                    CircularFileWriter(
                            mission!!.storage!!.getStream(), tempFile, checker).use({ out ->
                        out.onProgress = ProgressReport({ position: Long -> mission!!.done = position })
                        out.onWriteError = WriteErrorHandle({ err: Exception? ->
                            mission!!.psState = 3
                            mission!!.notifyError(DownloadMission.Companion.ERROR_POSTPROCESSING_HOLD, err)
                            try {
                                synchronized(this, { while (mission!!.psState == 3) (this as Object).wait() })
                            } catch (e: InterruptedException) {
                                // nothing to do
                                Log.e(javaClass.getSimpleName(), "got InterruptedException")
                            }
                            mission!!.errCode == DownloadMission.Companion.ERROR_NOTHING
                        })
                        result = process(out, *sources)
                        if (result == OK_RESULT.toInt()) finalLength = out.finalizeFile()
                    })
                } else {
                    result = OK_RESULT.toInt()
                }
            } finally {
                for (source: SharpStream? in sources) {
                    if (source != null && !source.isClosed()) {
                        source.close()
                    }
                }
                if (tempFile != null) {
                    tempFile!!.delete()
                    tempFile = null
                }
            }
        } else {
            result = if (test()) process(null) else OK_RESULT.toInt()
        }
        if (result == OK_RESULT.toInt()) {
            if (finalLength != -1L) {
                mission!!.length = finalLength
            }
        } else {
            mission!!.errCode = DownloadMission.Companion.ERROR_POSTPROCESSING
            mission!!.errObject = RuntimeException("post-processing algorithm returned " + result)
        }
        if (result != OK_RESULT.toInt() && worksOnSameFile) mission!!.storage!!.delete()
        mission = null
    }

    /**
     * Test if the post-processing algorithm can be skipped
     *
     * @param sources files to be processed
     * @return `true` if the post-processing is required, otherwise, `false`
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    open fun test(vararg sources: SharpStream): Boolean {
        return true
    }

    /**
     * Abstract method to execute the post-processing algorithm
     *
     * @param out     output stream
     * @param sources files to be processed
     * @return an error code, `OK_RESULT` means the operation was successful
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    abstract fun process(out: SharpStream?, vararg sources: SharpStream): Int
    fun getArgumentAt(index: Int, defaultValue: String): String {
        if (args == null || index >= args!!.size) {
            return defaultValue
        }
        return args!!.get(index)
    }

    public override fun toString(): String {
        val str: StringBuilder = StringBuilder()
        str.append("{ name=").append(name).append('[')
        if (args != null) {
            for (arg: String? in args!!) {
                str.append(", ")
                str.append(arg)
            }
            str.delete(0, 1)
        }
        return str.append("] }").toString()
    }

    companion object {
        @Transient
        val OK_RESULT: Byte = DownloadMission.Companion.ERROR_NOTHING.toByte()

        @Transient
        val ALGORITHM_TTML_CONVERTER: String = "ttml"

        @Transient
        val ALGORITHM_WEBM_MUXER: String = "webm"

        @Transient
        val ALGORITHM_MP4_FROM_DASH_MUXER: String = "mp4D-mp4"

        @Transient
        val ALGORITHM_M4A_NO_DASH: String = "mp4D-m4a"

        @Transient
        val ALGORITHM_OGG_FROM_WEBM_DEMUXER: String = "webm-ogg-d"
        fun getAlgorithm(algorithmName: String, args: Array<String>?): Postprocessing {
            val instance: Postprocessing
            when (algorithmName) {
                ALGORITHM_TTML_CONVERTER -> instance = TtmlConverter()
                ALGORITHM_WEBM_MUXER -> instance = WebMMuxer()
                ALGORITHM_MP4_FROM_DASH_MUXER -> instance = Mp4FromDashMuxer()
                ALGORITHM_M4A_NO_DASH -> instance = M4aNoDash()
                ALGORITHM_OGG_FROM_WEBM_DEMUXER -> instance = OggFromWebmDemuxer()
                else -> throw UnsupportedOperationException("Unimplemented post-processing algorithm: " + algorithmName)
            }
            instance.args = args
            return instance
        }
    }
}
