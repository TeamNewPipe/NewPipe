package us.shandian.giga.hls.state

import java.io.Serializable

data class HlsDownloadCheckpoint(
    val version: Int = VERSION,
    val resources: List<HlsResourceCheckpoint> = emptyList(),
    val remuxStarted: Boolean = false,
    val remuxOutputPath: String? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
        const val VERSION = 1
    }
}

data class HlsResourceCheckpoint(
    val resourceIndex: Int,
    val manifestUrl: String,
    val mediaPlaylistUrl: String?,
    val tempFilePath: String,
    val nextSegmentIndex: Int,
    val segmentByteOffset: Long,
    val bytesWritten: Long,
    val mediaSequence: Long,
    val playlistFingerprint: String?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
