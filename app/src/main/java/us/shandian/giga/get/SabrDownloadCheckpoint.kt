package us.shandian.giga.get

import java.io.Serializable

data class SabrDownloadCheckpoint(
    val version: Int = VERSION,
    val resources: List<SabrResourceCheckpoint> = emptyList(),
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
        const val VERSION = 2
    }
}

data class SabrResourceCheckpoint(
    val resourceIndex: Int,
    val itag: Int,
    val xtags: String?,
    val tempFilePath: String,
    val nextWriteSequence: Int,
    val bytesWritten: Long,
    val initializationBytes: Int,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
