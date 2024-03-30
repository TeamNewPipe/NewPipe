package us.shandian.giga.postprocessing

import org.schabi.newpipe.streams.Mp4FromDashWriter
import org.schabi.newpipe.streams.io.SharpStream
import java.io.IOException

/**
 * @author kapodamy
 */
internal class Mp4FromDashMuxer() : Postprocessing(true, true, Postprocessing.Companion.ALGORITHM_MP4_FROM_DASH_MUXER) {
    @Throws(IOException::class)
    public override fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        val muxer: Mp4FromDashWriter = Mp4FromDashWriter(*sources)
        muxer.parseSources()
        muxer.selectTracks(0, 0)
        muxer.build(out)
        return Postprocessing.Companion.OK_RESULT.toInt()
    }
}
