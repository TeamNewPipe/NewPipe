package us.shandian.giga.get

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline
import java.io.File
import java.util.TreeMap

internal data class SabrDownloadTarget(
    val resourceIndex: Int,
    val recovery: MissionRecoveryInfo,
    val format: YoutubeSabrInfo.Format,
    val file: File,
    var nextWriteSequence: Int = 1,
    var initializationWritten: Boolean = false,
    var initializationData: ByteArray? = null,
    var timeline: YoutubeSabrFormatTimeline? = null,
    val pending: TreeMap<Int, ByteArray> = TreeMap(),
    var pendingBytes: Long = 0,
)
