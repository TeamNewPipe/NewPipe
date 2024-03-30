package us.shandian.giga.postprocessing

import org.schabi.newpipe.streams.WebMReader
import org.schabi.newpipe.streams.WebMReader.WebMTrack
import org.schabi.newpipe.streams.WebMWriter
import org.schabi.newpipe.streams.io.SharpStream
import java.io.IOException

/**
 * @author kapodamy
 */
internal class WebMMuxer() : Postprocessing(true, true, Postprocessing.Companion.ALGORITHM_WEBM_MUXER) {
    @Throws(IOException::class)
    public override fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        val muxer: WebMWriter = WebMWriter(*sources)
        muxer.parseSources()

        // youtube uses a webm with a fake video track that acts as a "cover image"
        val indexes: IntArray = IntArray(sources.size)
        var i: Int = 0
        while (i < sources.size) {
            val tracks: Array<WebMTrack?>? = muxer.getTracksFromSource(i)
            for (j in tracks!!.indices) {
                if (tracks.get(j)!!.kind == WebMReader.TrackKind.Audio) {
                    indexes.get(i) = j
                    i = sources.size
                    break
                }
            }
            i++
        }
        muxer.selectTracks(*indexes)
        muxer.build(out)
        return Postprocessing.Companion.OK_RESULT.toInt()
    }
}
