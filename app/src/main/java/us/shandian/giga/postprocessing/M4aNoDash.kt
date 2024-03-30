package us.shandian.giga.postprocessing

import org.schabi.newpipe.streams.Mp4DashReader
import org.schabi.newpipe.streams.Mp4FromDashWriter
import org.schabi.newpipe.streams.io.SharpStream
import java.io.IOException

internal class M4aNoDash() : Postprocessing(false, true, Postprocessing.Companion.ALGORITHM_M4A_NO_DASH) {
    @Throws(IOException::class)
    public override fun test(vararg sources: SharpStream): Boolean {
        // check if the mp4 file is DASH (youtube)
        val reader: Mp4DashReader = Mp4DashReader(sources.get(0))
        reader.parse()
        when (reader.getBrands().get(0)) {
            0x64617368, 0x69736F35 -> return true
            else -> return false
        }
    }

    @Throws(IOException::class)
    public override fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        val muxer: Mp4FromDashWriter = Mp4FromDashWriter(sources.get(0))
        muxer.setMainBrand(0x4D344120) // binary string "M4A "
        muxer.parseSources()
        muxer.selectTracks(0)
        muxer.build(out)
        return Postprocessing.Companion.OK_RESULT.toInt()
    }
}
