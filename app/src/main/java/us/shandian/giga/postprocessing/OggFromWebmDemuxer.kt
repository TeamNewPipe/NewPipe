package us.shandian.giga.postprocessing

import okio.IOException
import java.nio.ByteBuffer
import org.schabi.newpipe.streams.OggFromWebMWriter
import org.schabi.newpipe.streams.io.SharpStream

internal class OggFromWebmDemuxer : Postprocessing(true, true, ALGORITHM_OGG_FROM_WEBM_DEMUXER) {
    override suspend fun test(vararg sources: SharpStream): Boolean {
        val buffer = ByteBuffer.allocate(4)
        sources[0].read(buffer.array())

        return when (buffer.int) {
            0x1a45dfa3 -> true

            // webm/mkv
            0x4F676753 -> false

            // ogg
            else -> throw UnsupportedOperationException("file not recognized, failed to demux the audio stream")
        }
    }

    override suspend fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        val demuxer = OggFromWebMWriter(sources[0], out!!, streamInfo)
        demuxer.parseSource()
        demuxer.selectTrack(0)
        demuxer.build()

        return OK_RESULT.toInt()
    }
}
