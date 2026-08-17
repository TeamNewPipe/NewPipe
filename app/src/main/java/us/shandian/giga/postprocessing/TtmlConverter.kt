package us.shandian.giga.postprocessing

import android.util.Log
import okio.IOException
import org.schabi.newpipe.streams.SrtFromTtmlWriter
import org.schabi.newpipe.streams.io.SharpStream

internal class TtmlConverter : Postprocessing(false, true, ALGORITHM_TTML_CONVERTER) {
    override suspend fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        val format = getArgumentAt(0, "")
        val ignoreEmptyFrames = getArgumentAt(1, "true") == "true"

        if (format.isEmpty() || format == "ttml") {
            val writer = SrtFromTtmlWriter(out as SharpStream, ignoreEmptyFrames)

            try {
                writer.build(sources[0])
            } catch (err: IOException) {
                Log.e(TAG, "subtitle conversion failed due to I/O error", err)
                throw err
            } catch (err: Exception) {
                Log.e(TAG, "subtitle conversion failed", err)
                throw IOException("TTML to SRT conversion failed", err)
            }

            return OK_RESULT.toInt()
        } else if (format == "srt") {
            val buffer = ByteArray(8 * 1024)
            var read: Int
            while (sources[0].read(buffer).also { read = it } > 0) {
                out?.write(buffer, 0, read)
            }
            return OK_RESULT.toInt()
        }

        throw UnsupportedOperationException("Can't convert this subtitle, unimplemented format: $format")
    }

    companion object {
        private const val TAG = "TtmlConverter"
    }
}
