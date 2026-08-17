package us.shandian.giga.postprocessing

import okio.IOException
import org.schabi.newpipe.streams.Mp4FromDashWriter
import org.schabi.newpipe.streams.io.SharpStream

internal class Mp4FromDashMuxer : Postprocessing(true, true, ALGORITHM_MP4_FROM_DASH_MUXER) {
    override suspend fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        val muxer = Mp4FromDashWriter(*sources)
        muxer.parseSources()
        muxer.selectTracks(0, 0)
        muxer.build(out as SharpStream)

        return OK_RESULT.toInt()
    }
}
