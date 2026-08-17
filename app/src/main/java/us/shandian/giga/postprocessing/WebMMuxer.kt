package us.shandian.giga.postprocessing

import okio.IOException
import org.schabi.newpipe.streams.WebMReader.TrackKind
import org.schabi.newpipe.streams.WebMWriter
import org.schabi.newpipe.streams.io.SharpStream

internal class WebMMuxer : Postprocessing(true, true, ALGORITHM_WEBM_MUXER) {
    override suspend fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        val muxer = WebMWriter(*sources)
        muxer.parseSources()

        val tracksList = Array(sources.size) { i -> muxer.getTracksFromSource(i) }
        val indexes = IntArray(sources.size)

        outer@for (i in sources.indices) {
            val tracks = tracksList[i] ?: continue
            for (j in tracks.indices) {
                if (tracks[j].kind == TrackKind.Audio) {
                    indexes[i] = j
                    break@outer
                }
            }
        }

        muxer.selectTracks(*indexes)
        muxer.build(out as SharpStream)

        return OK_RESULT.toInt()
    }
}
