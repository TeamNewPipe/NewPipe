package us.shandian.giga.postprocessing

import android.util.Log
import org.schabi.newpipe.streams.SrtFromTtmlWriter
import org.schabi.newpipe.streams.io.SharpStream
import java.io.IOException

/**
 * @author kapodamy
 */
internal class TtmlConverter() : Postprocessing(false, true, Postprocessing.Companion.ALGORITHM_TTML_CONVERTER) {
    @Throws(IOException::class)
    public override fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        // check if the subtitle is already in srt and copy, this should never happen
        val format: String? = getArgumentAt(0, null)
        val ignoreEmptyFrames: Boolean = (getArgumentAt(1, "true") == "true")
        if (format == null || (format == "ttml")) {
            val writer: SrtFromTtmlWriter = SrtFromTtmlWriter(out, ignoreEmptyFrames)
            try {
                writer.build(sources.get(0))
            } catch (err: Exception) {
                Log.e(TAG, "subtitle parse failed", err)
                return if (err is IOException) 1 else 8
            }
            return Postprocessing.Companion.OK_RESULT.toInt()
        } else if ((format == "srt")) {
            val buffer: ByteArray = ByteArray(8 * 1024)
            var read: Int
            while ((sources.get(0).read(buffer).also({ read = it })) > 0) {
                out!!.write(buffer, 0, read)
            }
            return Postprocessing.Companion.OK_RESULT.toInt()
        }
        throw UnsupportedOperationException("Can't convert this subtitle, unimplemented format: " + format)
    }

    companion object {
        private val TAG: String = "TtmlConverter"
    }
}
