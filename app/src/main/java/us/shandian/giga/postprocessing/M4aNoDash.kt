package us.shandian.giga.postprocessing

import okio.IOException
import org.schabi.newpipe.streams.Mp4DashReader
import org.schabi.newpipe.streams.Mp4FromDashWriter
import org.schabi.newpipe.streams.io.SharpStream

internal class M4aNoDash : Postprocessing(false, true, ALGORITHM_M4A_NO_DASH) {
    override suspend fun test(vararg sources: SharpStream): Boolean {
        val reader = Mp4DashReader(sources[0])
        reader.parse()

        val brands = reader.getBrands()
        return when (if (brands.isNotEmpty()) brands[0] else 0) {
            0x64617368, // DASH
            0x69736F35 -> true

            // ISO5
            else -> false
        }
    }

    override suspend fun process(out: SharpStream?, vararg sources: SharpStream): Int {
        val muxer = Mp4FromDashWriter(sources[0])
        muxer.setMainBrand(0x4D344120) // binary string "M4A "
        muxer.parseSources()
        muxer.selectTracks(0)
        muxer.build(out as SharpStream)

        return OK_RESULT.toInt()
    }
}
