package us.shandian.giga.postprocessing

import android.util.Log
import java.io.File
import okio.IOException
import java.io.Serializable
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.streams.io.SharpStream
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.DownloadMission.*
import us.shandian.giga.io.ChunkFileInputStream
import us.shandian.giga.io.CircularFileWriter
import us.shandian.giga.io.CircularFileWriter.OffsetChecker
import us.shandian.giga.io.ProgressReport

abstract class Postprocessing(
    @JvmField var reserveSpace: Boolean,
    @JvmField var worksOnSameFile: Boolean,
    algorithmName: String
) : Serializable {

    @get:JvmName("getName")
    private val name: String = algorithmName // for debugging only

    private var args: Array<String>? = null
    protected var streamInfo: StreamInfo? = null

    @Transient
    private var mission: DownloadMission? = null

    @Transient
    private var tempFile: File? = null

    fun setTemporalDir(directory: File) {
        val rnd = (Math.random() * 100000.0f).toInt()
        tempFile = File(directory, rnd.toString() + "_" + System.nanoTime() + ".tmp")
    }

    fun cleanupTemporalDir() {
        if (tempFile?.exists() == true) {
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                // nothing to do
            }
        }
    }

    @Throws(IOException::class)
    open suspend fun run(target: DownloadMission) {
        this.mission = target

        var result: Int
        var finalLength: Long = -1

        target.done = 0

        val length = target.storage!!.length() - target.offsets[0]
        target.length = Math.max(length, target.nearLength)

        val readProgress = ProgressReport { position: Long ->
            val pos = position - target.offsets[0]
            if (pos > target.done) target.done = pos
        }

        if (worksOnSameFile) {
            val sources = arrayOfNulls<ChunkFileInputStream>(target.urls?.size ?: 0)
            try {
                for (i in sources.indices) {
                    val source = target.storage!!.getStream()
                    val end = if (i + 1 < sources.size) target.offsets[i + 1] else source.length()

                    sources[i] = ChunkFileInputStream(source, target.offsets[i], end, readProgress)
                }

                @Suppress("UNCHECKED_CAST")
                if (test(*(sources as Array<SharpStream>))) {
                    for (source in sources) source?.rewind()

                    val checker = OffsetChecker {
                        for (source in sources) {
                            if (source == null || source.isClosed() || source.available() < 1) {
                                continue
                            }
                            return@OffsetChecker source.filePointer - 1
                        }
                        -1
                    }

                    CircularFileWriter(target.storage!!.getStream(), tempFile, checker).use { `out` ->
                        `out`.onProgress = ProgressReport { position: Long -> target.done = position }

                        `out`.onWriteError = CircularFileWriter.WriteError { err: Exception? ->
                            target.psState = 3
                            target.notifyError(DownloadMission.ERROR_POSTPROCESSING_HOLD, err)

                            try {
                                synchronized(this) {
                                    while (target.psState == 3) {
                                        (this as Object).wait()
                                    }
                                }
                            } catch (e: InterruptedException) {
                                Log.e(javaClass.simpleName, "got InterruptedException")
                            }

                            target.errCode == DownloadMission.ERROR_NOTHING
                        }

                        @Suppress("UNCHECKED_CAST")
                        result = process(`out`, *(sources as Array<SharpStream>))

                        if (result == OK_RESULT.toInt()) {
                            finalLength = `out`.finalizeFile()
                        }
                    }
                } else {
                    result = OK_RESULT.toInt()
                }
            } finally {
                for (source in sources) {
                    if (source != null && !source.isClosed()) {
                        source.close()
                    }
                }
                if (tempFile != null) {
                    tempFile?.delete()
                    tempFile = null
                }
            }
        } else {
            result = if (test()) process(null) else OK_RESULT.toInt()
        }

        if (result == OK_RESULT.toInt()) {
            if (finalLength != -1L) {
                target.length = finalLength
            }
        } else {
            target.errCode = DownloadMission.ERROR_POSTPROCESSING
            target.errObject = RuntimeException("post-processing algorithm returned $result")
        }

        if (result != OK_RESULT.toInt() && worksOnSameFile) {
            target.storage!!.delete()
        }

        this.mission = null
    }

    @Throws(IOException::class)
    internal open suspend fun test(vararg sources: SharpStream): Boolean {
        return true
    }

    @Throws(IOException::class)
    internal abstract suspend fun process(`out`: SharpStream?, vararg sources: SharpStream): Int

    protected fun getArgumentAt(index: Int, defaultValue: String): String {
        val currentArgs = args
        if (currentArgs == null || index >= currentArgs.size) {
            return defaultValue
        }
        return currentArgs[index]
    }

    override fun toString(): String {
        val argsStr = args?.joinToString(", ") ?: ""
        return "{ name=$name[$argsStr] }"
    }

    companion object {
        const val OK_RESULT = -1.toByte()

        const val ALGORITHM_TTML_CONVERTER = "ttml"
        const val ALGORITHM_WEBM_MUXER = "webm"
        const val ALGORITHM_MP4_FROM_DASH_MUXER = "mp4D-mp4"
        const val ALGORITHM_M4A_NO_DASH = "mp4D-m4a"
        const val ALGORITHM_OGG_FROM_WEBM_DEMUXER = "webm-ogg-d"

        @JvmStatic
        fun getAlgorithm(
            algorithmName: String,
            args: Array<String>?,
            streamInfo: StreamInfo?
        ): Postprocessing {
            val instance = when (algorithmName) {
                ALGORITHM_TTML_CONVERTER -> TtmlConverter()
                ALGORITHM_WEBM_MUXER -> WebMMuxer()
                ALGORITHM_MP4_FROM_DASH_MUXER -> Mp4FromDashMuxer()
                ALGORITHM_M4A_NO_DASH -> M4aNoDash()
                ALGORITHM_OGG_FROM_WEBM_DEMUXER -> OggFromWebmDemuxer()
                else -> throw UnsupportedOperationException("Unimplemented post-processing algorithm: $algorithmName")
            }

            instance.args = args
            instance.streamInfo = streamInfo
            return instance
        }
    }
}
