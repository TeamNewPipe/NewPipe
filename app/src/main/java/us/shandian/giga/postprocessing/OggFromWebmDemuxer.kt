package us.shandian.giga.postprocessing

import org.schabi.newpipe.streams.OggFromWebMWriter
import org.schabi.newpipe.streams.io.SharpStream
import java.io.IOException
import java.nio.ByteBuffer

internal class OggFromWebmDemuxer() : Postprocessing(true, true, Postprocessing.Companion.ALGORITHM_OGG_FROM_WEBM_DEMUXER) {
    @Throws(IOException::class)
    public override fun test(vararg sources: SharpStream): Boolean {
        val buffer: ByteBuffer = ByteBuffer.allocate(4)
        sources.get(0).read(buffer.array())
        when (buffer.getInt()) {
            0x1a45dfa3 -> return true // webm/mkv
            0x4F676753 -> return false // ogg
        }
        throw UnsupportedOperationException("file not recognized, failed to demux the audio stream")
    }

    @Throws(IOException::class)
    public override fun process(out: SharpStream, vararg sources: SharpStream): Int {
        val demuxer: OggFromWebMWriter = OggFromWebMWriter(sources.get(0), out)
        demuxer.parseSource()
        demuxer.selectTrack(0)
        demuxer.build()
        return Postprocessing.Companion.OK_RESULT.toInt()
    }
}
